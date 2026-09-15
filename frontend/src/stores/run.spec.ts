import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useRunStore } from './run'
import { server } from '@/test/setup'
import { failing } from '@/test/handlers'
import { gameState, turnEvent } from '@/test/fixtures'

/**
 * These are about the state machine, not about rendering. The phases are the contract every
 * component depends on, so the transitions are what is worth pinning down.
 */
describe('run store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('starts idle with nothing loaded', () => {
    const store = useRunStore()

    expect(store.phase).toBe('idle')
    expect(store.run).toBeNull()
    expect(store.ads).toEqual([])
  })

  it('goes idle -> starting -> playing', async () => {
    const store = useRunStore()
    const seen: string[] = []

    const pending = store.start('MANUAL')
    seen.push(store.phase)
    await pending
    seen.push(store.phase)

    expect(seen).toEqual(['starting', 'playing'])
    expect(store.run?.runId).toBe('g1')
  })

  it('goes through resolving and back to playing on a solve', async () => {
    const store = useRunStore()
    await store.start('MANUAL')

    const pending = store.solve('ad1')
    expect(store.phase).toBe('resolving')
    expect(store.pendingAdId).toBe('ad1')
    await pending

    expect(store.phase).toBe('playing')
    expect(store.pendingAdId).toBeNull()
    expect(store.lastEvent?.delta.gold).toBe(82)
    expect(store.state?.score).toBe(82)
  })

  it('goes through shopping and back to playing on a purchase', async () => {
    const store = useRunStore()
    await store.start('MANUAL')

    const pending = store.buy('hpot')
    expect(store.phase).toBe('shopping')
    expect(store.pendingItemId).toBe('hpot')
    await pending

    expect(store.phase).toBe('playing')
    expect(store.pendingItemId).toBeNull()
  })

  it('spends a turn and no lives when the player lets the turn pass', async () => {
    const store = useRunStore()
    await store.start('MANUAL')

    const pending = store.waitOutTurn()
    expect(store.phase).toBe('resolving')
    await pending

    expect(store.phase).toBe('playing')
    expect(store.lastEvent?.action).toBe('IDLED')
    expect(store.lastEvent?.delta.turn).toBe(1)
    expect(store.lastEvent?.delta.lives).toBe(0)
    expect(store.feed).toHaveLength(1)
  })

  it('will not let the turn pass when it is not the player\'s move', async () => {
    const store = useRunStore()
    await store.start('AUTO')
    store.phase = 'gameOver'

    await store.waitOutTurn()

    expect(store.feed).toHaveLength(0)
  })

  it('will not act while another action is in flight', async () => {
    const store = useRunStore()
    await store.start('MANUAL')

    const first = store.solve('ad1')
    await store.solve('ad1')
    await first

    // The second call is a no-op because the phase was not 'playing'.
    expect(store.feed).toHaveLength(1)
  })

  it('keeps the run and offers a retry when a solve fails', async () => {
    const store = useRunStore()
    await store.start('MANUAL')
    server.use(
      failing.solve(502, {
        error: 'UPSTREAM_ERROR',
        message: 'Mugloar did not cooperate',
        retryable: true,
        at: '',
      }),
    )

    await store.solve('ad1')

    expect(store.phase).toBe('playing')
    expect(store.run).not.toBeNull()
    expect(store.error?.message).toBe('Mugloar did not cooperate')
    expect(store.error?.retryable).toBe(true)
  })

  it('does not offer a retry for a failure that will fail again', async () => {
    const store = useRunStore()
    server.use(
      failing.startRun(400, {
        error: 'INVALID_REQUEST',
        message: 'mode must not be null',
        retryable: false,
        at: '',
      }),
    )

    await store.start('MANUAL')

    expect(store.phase).toBe('idle')
    expect(store.error?.retryable).toBe(false)
  })

  it('retries the exact call that failed', async () => {
    const store = useRunStore()
    server.use(
      failing.startRun(502, { error: 'UPSTREAM_ERROR', message: 'nope', retryable: true, at: '' }),
    )
    await store.start('AUTO')
    expect(store.run).toBeNull()

    server.resetHandlers()
    await store.error?.retry()

    expect(store.run?.mode).toBe('AUTO')
    expect(store.error).toBeNull()
  })

  it('ignores repeated sequence numbers so a stream replay is harmless', async () => {
    const store = useRunStore()
    await store.start('AUTO')

    store.applyStreamedTurn(turnEvent({ sequence: 1 }))
    store.applyStreamedTurn(turnEvent({ sequence: 1 }))

    expect(store.feed).toHaveLength(1)
    expect(store.state?.score).toBe(82)
  })

  it('refuses a replayed event that arrives after a newer live one', async () => {
    // Replay and live events come from two server threads and can interleave. A late replay of
    // turn 3 landing after turn 5 must not wind the HUD back to turn 3.
    const store = useRunStore()
    await store.start('AUTO')

    store.applyStreamedTurn(turnEvent({ sequence: 5, state: gameState({ turn: 5, score: 500 }) }))
    store.applyStreamedTurn(turnEvent({ sequence: 3, state: gameState({ turn: 3, score: 300 }) }))

    expect(store.state?.turn).toBe(5)
    expect(store.state?.score).toBe(500)
    expect(store.feed).toHaveLength(1)
  })

  it('does not let a late replay reopen a finished run', async () => {
    const store = useRunStore()
    await store.start('AUTO')

    store.applyStreamedTurn(turnEvent({ sequence: 9, action: 'FINISHED', description: 'Out of lives' }))
    store.applyStreamedTurn(turnEvent({ sequence: 4, action: 'SOLVED' }))

    expect(store.phase).toBe('gameOver')
    expect(store.run?.status).toBe('FINISHED')
  })

  it('coalesces board refreshes so a replay of many events is not many concurrent requests', async () => {
    const store = useRunStore()
    await store.start('AUTO')
    let gets = 0
    const original = window.fetch
    window.fetch = (...args) => {
      if (String(args[0]).includes('/api/runs/g1') && (args[1]?.method ?? 'GET') === 'GET') gets++
      return original(...args)
    }
    try {
      // Fifty events land while the first refresh is still in flight.
      const all = Array.from({ length: 50 }, () => store.refreshBoardQuietly())
      await Promise.all(all)
      // One in flight, one more for the events that arrived meanwhile. Not fifty.
      expect(gets).toBeLessThanOrEqual(2)
      expect(gets).toBeGreaterThanOrEqual(1)
    } finally {
      window.fetch = original
    }
  })

  it('ends the run when the stream says it finished', async () => {
    const store = useRunStore()
    await store.start('AUTO')

    store.applyStreamedTurn(
      turnEvent({ sequence: 2, action: 'FINISHED', description: 'Out of lives' }),
    )

    expect(store.phase).toBe('gameOver')
    expect(store.run?.status).toBe('FINISHED')
  })

  it('marks an upstream break as failed rather than a normal finish', async () => {
    const store = useRunStore()
    await store.start('AUTO')

    store.applyStreamedTurn(
      turnEvent({ sequence: 9, action: 'FAILED', description: 'Mugloar returned 503' }),
    )

    expect(store.phase).toBe('gameOver')
    expect(store.run?.status).toBe('FAILED')
    expect(store.run?.failure).toBe('Mugloar returned 503')
  })

  it('resumes a run the server still has, straight into playing', async () => {
    // After a refresh, from a link, or via the back button: the server is the source of truth.
    const store = useRunStore()

    await store.resume('g1')

    expect(store.phase).toBe('playing')
    expect(store.run?.runId).toBe('g1')
    expect(store.error).toBeNull()
  })

  it('lands on the start screen with an error when the run is gone', async () => {
    // A restart drops every run. The link is stale, and the UI should say so rather than spin.
    const store = useRunStore()

    await store.resume('gone')

    expect(store.phase).toBe('idle')
    expect(store.run).toBeNull()
    expect(store.error?.retryable).toBe(false)
  })

  it('does not refetch a run it already holds', async () => {
    const store = useRunStore()
    await store.start('MANUAL')
    const before = store.run

    await store.resume('g1')

    expect(store.run).toBe(before)
  })

  it('only lets a human act in manual mode', async () => {
    const store = useRunStore()
    await store.start('AUTO')
    expect(store.canAct).toBe(false)

    store.reset()
    await store.start('MANUAL')
    expect(store.canAct).toBe(true)
  })

  it('clears everything on reset', async () => {
    const store = useRunStore()
    await store.start('MANUAL')
    await store.solve('ad1')

    store.reset()

    expect(store.phase).toBe('idle')
    expect(store.run).toBeNull()
    expect(store.feed).toEqual([])
    expect(store.lastEvent).toBeNull()
  })
})
