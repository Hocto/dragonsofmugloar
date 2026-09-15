import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import * as api from '@/api/client'
import { ApiError } from '@/api/client'
import type { AdView, RunMode, RunView, TurnEvent } from '@/api/types'

/**
 * The run as an explicit state machine. Every async action has a pending phase, so the UI never
 * has to infer in-flight state from booleans.
 *
 *   idle      nothing started
 *   starting  waiting for POST /api/runs
 *   playing   a board is on screen
 *   resolving a solve is in flight
 *   shopping  a purchase is in flight
 *   gameOver  the run ended, out of lives or by upstream failure
 *
 * `error` is separate from the phase: a failed request leaves the run in place and offers a retry.
 */
export type RunPhase = 'idle' | 'starting' | 'playing' | 'resolving' | 'shopping' | 'gameOver'

/** What the last move did to the board: how many notices left it, how many arrived. */
export interface BoardChange {
  expired: number
  arrived: number
}

export interface RunErrorState {
  message: string
  retryable: boolean
  /** Re-runs whatever failed. */
  retry: () => Promise<void>
}

export const useRunStore = defineStore('run', () => {
  const phase = ref<RunPhase>('idle')
  const run = ref<RunView | null>(null)
  const error = ref<RunErrorState | null>(null)
  const lastEvent = ref<TurnEvent | null>(null)
  const lastBoardChange = ref<BoardChange | null>(null)
  const pendingAdId = ref<string | null>(null)
  const pendingItemId = ref<string | null>(null)
  /** Turns arriving on the SSE stream, newest first. */
  const feed = ref<TurnEvent[]>([])
  /**
   * Highest event sequence applied. Replayed and live events arrive from two server threads and
   * can interleave; anything at or below this number is stale and is discarded.
   */
  let highestSequence = -1

  const state = computed(() => run.value?.state ?? null)
  const ads = computed<AdView[]>(() => run.value?.ads ?? [])
  const shop = computed(() => run.value?.shop ?? [])
  const isAuto = computed(() => run.value?.mode === 'AUTO')
  const isBusy = computed(() => phase.value === 'resolving' || phase.value === 'shopping')
  const canAct = computed(() => phase.value === 'playing' && !isAuto.value)

  function reset() {
    phase.value = 'idle'
    run.value = null
    error.value = null
    lastEvent.value = null
    lastBoardChange.value = null
    pendingAdId.value = null
    pendingItemId.value = null
    feed.value = []
    highestSequence = -1
  }

  function boardChange(before: AdView[], after: AdView[]): BoardChange {
    const beforeIds = new Set(before.map((ad) => ad.adId))
    const afterIds = new Set(after.map((ad) => ad.adId))
    return {
      expired: before.filter((ad) => !afterIds.has(ad.adId)).length,
      arrived: after.filter((ad) => !beforeIds.has(ad.adId)).length,
    }
  }

  /** Every network call goes through here, so error handling exists in one place. */
  async function attempt<T>(
    work: () => Promise<T>,
    onSuccess: (value: T) => void,
    retry: () => Promise<void>,
    settledPhase: RunPhase,
  ): Promise<void> {
    error.value = null
    try {
      onSuccess(await work())
      phase.value = settledPhase
    } catch (thrown) {
      const failure = thrown instanceof ApiError
        ? thrown
        : new ApiError('Something went wrong.', 0, true, 'UNKNOWN')
      error.value = { message: failure.message, retryable: failure.retryable, retry }
      // Keep the run in place; a failed request does not blank the screen.
      phase.value = run.value ? syncPhase() : 'idle'
    } finally {
      pendingAdId.value = null
      pendingItemId.value = null
    }
  }

  function syncPhase(): RunPhase {
    return run.value && run.value.status !== 'RUNNING' ? 'gameOver' : 'playing'
  }

  async function start(mode: RunMode): Promise<void> {
    phase.value = 'starting'
    feed.value = []
    lastEvent.value = null
    await attempt(
      () => api.startRun(mode),
      (view) => {
        run.value = view
        feed.value = [...view.events].reverse()
        highestSequence = view.events.at(-1)?.sequence ?? -1
      },
      () => start(mode),
      'playing',
    )
    if (run.value) {
      phase.value = syncPhase()
    }
  }

  /** Loads a run that already exists on the server. For an auto run, the stream reattaches once the run is in the store. */
  async function resume(runId: string): Promise<void> {
    if (run.value?.runId === runId) return
    phase.value = 'starting'
    feed.value = []
    lastEvent.value = null
    await attempt(
      () => api.getRun(runId),
      (view) => {
        run.value = view
        feed.value = [...view.events].reverse()
        lastEvent.value = view.events.at(-1) ?? null
        highestSequence = view.events.at(-1)?.sequence ?? -1
      },
      () => resume(runId),
      'playing',
    )
    if (run.value) {
      phase.value = syncPhase()
    }
  }

  async function refresh(): Promise<void> {
    const current = run.value
    if (!current) return
    await attempt(
      () => api.getRun(current.runId),
      (view) => {
        run.value = view
      },
      refresh,
      syncPhase(),
    )
    if (run.value) {
      phase.value = syncPhase()
    }
  }

  async function solve(adId: string): Promise<void> {
    const current = run.value
    if (!current || phase.value !== 'playing') return
    phase.value = 'resolving'
    pendingAdId.value = adId
    const before = current.ads
    await attempt(
      () => api.solveAd(current.runId, adId),
      (result) => {
        run.value = result.run
        lastBoardChange.value = boardChange(before, result.run.ads)
        lastEvent.value = result.event
        feed.value = [result.event, ...feed.value]
        highestSequence = Math.max(highestSequence, result.event.sequence)
      },
      () => solve(adId),
      'playing',
    )
    if (run.value) {
      phase.value = syncPhase()
    }
  }

  async function buy(itemId: string): Promise<void> {
    const current = run.value
    if (!current || phase.value !== 'playing') return
    phase.value = 'shopping'
    pendingItemId.value = itemId
    await attempt(
      () => api.buyItem(current.runId, itemId),
      (result) => {
        run.value = result.run
        lastEvent.value = result.event
        feed.value = [result.event, ...feed.value]
        highestSequence = Math.max(highestSequence, result.event.sequence)
      },
      () => buy(itemId),
      'playing',
    )
    if (run.value) {
      phase.value = syncPhase()
    }
  }

  /** Gives up the turn. Uses the solve phase; it is the same kind of move. */
  async function waitOutTurn(): Promise<void> {
    const current = run.value
    if (!current || phase.value !== 'playing') return
    phase.value = 'resolving'
    const before = current.ads
    await attempt(
      () => api.waitOutTurn(current.runId),
      (result) => {
        run.value = result.run
        lastBoardChange.value = boardChange(before, result.run.ads)
        lastEvent.value = result.event
        feed.value = [result.event, ...feed.value]
        highestSequence = Math.max(highestSequence, result.event.sequence)
      },
      waitOutTurn,
      'playing',
    )
    if (run.value) {
      phase.value = syncPhase()
    }
  }

  /** A turn arrived on the stream. The server is the source of truth for state; the client only appends. */
  function applyStreamedTurn(event: TurnEvent): void {
    if (!run.value) return
    // Anything older than the newest applied event is stale, duplicate or not.
    if (event.sequence <= highestSequence) return
    highestSequence = event.sequence

    feed.value = [event, ...feed.value].slice(0, 200)
    lastEvent.value = event
    run.value = { ...run.value, state: event.state }

    if (event.action === 'FINISHED' || event.action === 'FAILED') {
      run.value = {
        ...run.value,
        status: event.action === 'FAILED' ? 'FAILED' : 'FINISHED',
        failure: event.action === 'FAILED' ? event.description : null,
      }
      phase.value = 'gameOver'
    }
  }

  /**
   * Refreshes the board for an auto run; the stream carries state, not ads. Coalesced: at most one
   * request is in flight, and events arriving meanwhile trigger one follow-up rather than one
   * request each.
   */
  let boardRefreshInFlight = false
  let boardRefreshStale = false

  async function refreshBoardQuietly(): Promise<void> {
    if (boardRefreshInFlight) {
      boardRefreshStale = true
      return
    }
    boardRefreshInFlight = true
    try {
      do {
        boardRefreshStale = false
        const current = run.value
        if (!current || current.status !== 'RUNNING') return
        try {
          const view = await api.getRun(current.runId)
          if (run.value && run.value.runId === view.runId) {
            run.value = { ...view, state: run.value.state }
          }
        } catch {
          // A failed board refresh is cosmetic while the stream is alive.
        }
      } while (boardRefreshStale)
    } finally {
      boardRefreshInFlight = false
    }
  }

  function reportStreamError(retry: () => Promise<void>): void {
    if (phase.value === 'gameOver') return
    error.value = {
      message: 'Lost the live feed from the server.',
      retryable: true,
      retry,
    }
  }

  function clearError(): void {
    error.value = null
  }

  return {
    phase,
    run,
    error,
    lastEvent,
    lastBoardChange,
    pendingAdId,
    pendingItemId,
    feed,
    state,
    ads,
    shop,
    isAuto,
    isBusy,
    canAct,
    start,
    resume,
    refresh,
    solve,
    buy,
    waitOutTurn,
    reset,
    applyStreamedTurn,
    refreshBoardQuietly,
    reportStreamError,
    clearError,
  }
})
