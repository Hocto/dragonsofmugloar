import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import * as api from '@/api/client'
import { ApiError } from '@/api/client'
import type { AdView, RunMode, RunView, TurnEvent } from '@/api/types'

/**
 * The run, modelled as an explicit state machine.
 *
 * The phases are not decoration. Every async action has a pending phase, so the UI never has to
 * guess whether something is in flight, and there is no combination of booleans that can describe
 * a screen that should not exist.
 *
 *   idle      nothing started; the start screen is showing
 *   starting  waiting for POST /api/runs
 *   playing   a board is on screen and it is the player's move (or the bot's)
 *   resolving a solve is in flight
 *   shopping  a purchase is in flight
 *   gameOver  the run ended, either out of lives or upstream failure
 *
 * `error` is deliberately separate from the phase. A failed request leaves the run where it was and
 * offers a retry; it does not throw the player back to a blank screen.
 */
export type RunPhase = 'idle' | 'starting' | 'playing' | 'resolving' | 'shopping' | 'gameOver'

export interface RunErrorState {
  message: string
  retryable: boolean
  /** Re-runs whatever failed, so the retry button does not need to know what that was. */
  retry: () => Promise<void>
}

export const useRunStore = defineStore('run', () => {
  const phase = ref<RunPhase>('idle')
  const run = ref<RunView | null>(null)
  const error = ref<RunErrorState | null>(null)
  const lastEvent = ref<TurnEvent | null>(null)
  const pendingAdId = ref<string | null>(null)
  const pendingItemId = ref<string | null>(null)
  /** Turns arriving on the SSE stream, newest first. */
  const feed = ref<TurnEvent[]>([])

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
    pendingAdId.value = null
    pendingItemId.value = null
    feed.value = []
  }

  /** Every network call in the store goes through here, so error handling exists in one place. */
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
      // Stay where we were rather than blanking the screen: a failed solve is not a lost run.
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
      },
      () => start(mode),
      'playing',
    )
    if (run.value) {
      phase.value = syncPhase()
    }
  }

  /**
   * Pick up a run that already exists on the server: after a refresh, from a shared link, or on
   * the browser's back/forward. The server is the source of truth, so this is a read, and for an
   * auto run the stream reattaches on its own once the run is in the store.
   */
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
    await attempt(
      () => api.solveAd(current.runId, adId),
      (result) => {
        run.value = result.run
        lastEvent.value = result.event
        feed.value = [result.event, ...feed.value]
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
      },
      () => buy(itemId),
      'playing',
    )
    if (run.value) {
      phase.value = syncPhase()
    }
  }

  /**
   * Give up the turn. Same phase as a solve, because it is the same kind of move: one turn spent,
   * board comes back changed.
   */
  async function waitOutTurn(): Promise<void> {
    const current = run.value
    if (!current || phase.value !== 'playing') return
    phase.value = 'resolving'
    await attempt(
      () => api.waitOutTurn(current.runId),
      (result) => {
        run.value = result.run
        lastEvent.value = result.event
        feed.value = [result.event, ...feed.value]
      },
      waitOutTurn,
      'playing',
    )
    if (run.value) {
      phase.value = syncPhase()
    }
  }

  /**
   * A turn arrived on the stream. Auto runs are driven entirely by this: the server is the source
   * of truth for state, and the client only appends.
   */
  function applyStreamedTurn(event: TurnEvent): void {
    if (!run.value) return
    if (feed.value.some((seen) => seen.sequence === event.sequence)) return

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

  /** Auto runs need the board refreshed alongside the stream; the stream carries state, not ads. */
  async function refreshBoardQuietly(): Promise<void> {
    const current = run.value
    if (!current || current.status !== 'RUNNING') return
    try {
      const view = await api.getRun(current.runId)
      if (run.value && run.value.runId === view.runId) {
        run.value = { ...view, state: run.value.state }
      }
    } catch {
      // A board refresh failing is cosmetic while the stream is alive, so it stays quiet.
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
