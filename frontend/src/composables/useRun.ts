import { computed } from 'vue'
import { useRunStore } from '@/stores/run'
import type { RunMode } from '@/api/types'

/**
 * Run lifecycle for the screens: start one, retry a failed call, throw it away and go home.
 *
 * The store owns the state; this exists so components never import the store's action list
 * directly and so "start a run" is one call rather than three.
 */
export function useRun() {
  const store = useRunStore()

  const screen = computed(() => {
    switch (store.phase) {
      case 'idle':
        return 'start' as const
      case 'starting':
        return 'starting' as const
      case 'gameOver':
        return 'over' as const
      default:
        return 'game' as const
    }
  })

  async function begin(mode: RunMode): Promise<void> {
    await store.start(mode)
  }

  async function retry(): Promise<void> {
    const failure = store.error
    if (!failure) return
    await failure.retry()
  }

  function abandon(): void {
    store.reset()
  }

  return {
    store,
    screen,
    begin,
    retry,
    abandon,
  }
}
