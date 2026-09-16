import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { defineComponent, h, nextTick } from 'vue'
import { mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { useRunStore } from '@/stores/run'
import { runIdFromHash, useRunLocation } from './useRunLocation'

/**
 * The URL and the store have to agree in both directions, and neither may push the other into a
 * loop. These drive each direction and check the other followed.
 */
const Host = defineComponent({
  setup() {
    useRunLocation()
    return () => h('div')
  },
})

const flush = async () => {
  await nextTick()
  await new Promise((r) => setTimeout(r, 0))
  await nextTick()
}

describe('runIdFromHash', () => {
  it('reads the run id and nothing else', () => {
    expect(runIdFromHash('#run/abc123')).toBe('abc123')
    expect(runIdFromHash('#run/')).toBeNull()
    expect(runIdFromHash('#something-else')).toBeNull()
    expect(runIdFromHash('')).toBeNull()
    expect(runIdFromHash('#run/../etc')).toBeNull()
  })
})

describe('useRunLocation', () => {
  // Every mount registers a hashchange listener. Leave one behind and the next test's hash change
  // wakes a stale store that resumes the run on its own, which reads as a loop that is not there.
  let host: VueWrapper | null = null
  const mountHost = () => (host = mount(Host))

  beforeEach(() => {
    setActivePinia(createPinia())
    window.history.replaceState(null, '', '/')
  })

  afterEach(() => {
    host?.unmount()
    host = null
    window.history.replaceState(null, '', '/')
  })

  it('puts the run id in the hash when a run starts', async () => {
    mountHost()
    const store = useRunStore()

    await store.start('MANUAL')
    await flush()

    expect(window.location.hash).toBe('#run/g1')
  })

  it('resumes the run named in the hash on load', async () => {
    window.location.hash = '#run/g1'

    mountHost()
    await flush()

    expect(useRunStore().run?.runId).toBe('g1')
    expect(useRunStore().phase).toBe('playing')
  })

  it('goes back to the start screen when the hash is cleared, as the back button does', async () => {
    mountHost()
    const store = useRunStore()
    await store.start('MANUAL')
    await flush()

    window.location.hash = ''
    window.dispatchEvent(new HashChangeEvent('hashchange'))
    await flush()

    expect(store.run).toBeNull()
    expect(store.phase).toBe('idle')
  })

  it('clears the hash when the player leaves', async () => {
    mountHost()
    const store = useRunStore()
    await store.start('MANUAL')
    await flush()
    expect(window.location.hash).toBe('#run/g1')

    store.reset()
    await flush()

    expect(window.location.hash).toBe('')
  })

  it('does not bounce between the two directions', async () => {
    // start -> hash set -> hashchange -> resume (no-op, same id) -> watch (no-op, same hash).
    // If that ever became a loop the run would refetch forever; count the fetches instead.
    let fetches = 0
    const original = window.fetch
    window.fetch = (...args) => {
      if (String(args[0]).includes('/api/runs/g1') && (args[1]?.method ?? 'GET') === 'GET') fetches++
      return original(...args)
    }
    try {
      mountHost()
      await useRunStore().start('MANUAL')
      await flush()
      await flush()
      expect(fetches).toBe(0)
    } finally {
      window.fetch = original
    }
  })
})
