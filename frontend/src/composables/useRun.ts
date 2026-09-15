import { computed } from 'vue'
import { useRunStore } from '@/stores/run'
import type { RunMode } from '@/api/types'

/** Run lifecycle for the screens: start, retry a failed call, abandon. The store owns the state. */
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
