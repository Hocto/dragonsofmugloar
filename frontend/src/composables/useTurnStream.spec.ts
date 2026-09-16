import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick } from 'vue'
import { mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { useRunStore } from '@/stores/run'
import { useTurnStream } from './useTurnStream'

/**
 * A hand-rolled EventSource so the two ways a stream can die can be told apart. The real one
 * reconnects by itself after a dropped connection (readyState CONNECTING) but gives up for good on
 * a 404 or a 500 (readyState CLOSED), and the composable has to treat those differently.
 */
class FakeEventSource extends EventTarget {
  static CONNECTING = 0
  static OPEN = 1
  static CLOSED = 2
  static instances: FakeEventSource[] = []
  readyState = FakeEventSource.CONNECTING
  closed = false

  constructor(readonly url: string) {
    super()
    FakeEventSource.instances.push(this)
  }

  close() {
    this.closed = true
    this.readyState = FakeEventSource.CLOSED
  }

  /** The server answered with something EventSource will not retry from. */
  dieForGood() {
    this.readyState = FakeEventSource.CLOSED
    this.dispatchEvent(new Event('error'))
  }

  /** A blip. EventSource would retry this on its own. */
  drop() {
    this.readyState = FakeEventSource.CONNECTING
    this.dispatchEvent(new Event('error'))
  }
}

const Host = defineComponent({
  setup() {
    useTurnStream()
    return () => h('div')
  },
})

describe('useTurnStream', () => {
  let host: VueWrapper | null = null

  beforeEach(() => {
    setActivePinia(createPinia())
    FakeEventSource.instances = []
    vi.stubGlobal('EventSource', FakeEventSource)
  })

  afterEach(() => {
    host?.unmount()
    host = null
    vi.unstubAllGlobals()
  })

  async function startAutoRun() {
    host = mount(Host)
    const store = useRunStore()
    await store.start('AUTO')
    await nextTick()
    return store
  }

  it('subscribes to an auto run and not to a manual one', async () => {
    host = mount(Host)
    const store = useRunStore()

    await store.start('MANUAL')
    await nextTick()
    expect(FakeEventSource.instances).toHaveLength(0)

    store.reset()
    await store.start('AUTO')
    await nextTick()
    expect(FakeEventSource.instances).toHaveLength(1)
    expect(FakeEventSource.instances[0]!.url).toContain('/api/runs/g1/stream')
  })

  it('reports at once when the server refuses the stream, instead of waiting for retries', async () => {
    // A run evicted from the registry answers the stream with a 404. EventSource does not retry
    // that; it sits at CLOSED. Left to the retry counter, the chronicle said "reconnecting"
    // forever.
    const store = await startAutoRun()

    FakeEventSource.instances[0]!.dieForGood()

    expect(store.error?.message).toContain('Lost the live feed')
    expect(store.error?.retryable).toBe(true)
  })

  it('stays quiet through a few dropped connections, which EventSource retries itself', async () => {
    const store = await startAutoRun()
    const source = FakeEventSource.instances[0]!

    source.drop()
    source.drop()
    source.drop()

    expect(store.error).toBeNull()
  })

  it('gives up and reports after too many drops in a row', async () => {
    const store = await startAutoRun()
    const source = FakeEventSource.instances[0]!

    source.drop()
    source.drop()
    source.drop()
    source.drop()

    expect(store.error?.message).toContain('Lost the live feed')
  })

  it('does not report a stream that closed because the run finished', async () => {
    const store = await startAutoRun()
    store.phase = 'gameOver'

    FakeEventSource.instances[0]!.dieForGood()

    expect(store.error).toBeNull()
  })

  it('reconnects when the reported error is retried', async () => {
    const store = await startAutoRun()
    FakeEventSource.instances[0]!.dieForGood()

    await store.error!.retry()

    expect(FakeEventSource.instances).toHaveLength(2)
    expect(FakeEventSource.instances[1]!.url).toContain('/api/runs/g1/stream')
  })
})
