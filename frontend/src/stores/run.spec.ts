import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useRunStore } from './run'
import { server } from '@/test/setup'
import { failing } from '@/test/handlers'
import { turnEvent } from '@/test/fixtures'

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
