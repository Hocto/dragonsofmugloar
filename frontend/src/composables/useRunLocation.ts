import { onBeforeUnmount, onMounted, watch } from 'vue'
import { useRunStore } from '@/stores/run'

/**
 * Keeps the run id in the URL hash and the hash in the store, so a refresh, a shared link and the
 * back button all resolve to the run the server holds. A hash rather than a path so the static
 * build needs no server-side rewrite.
 */
const RUN_HASH = /^#run\/([A-Za-z0-9_-]+)$/

export function runIdFromHash(hash: string = window.location.hash): string | null {
  const match = RUN_HASH.exec(hash)
  return match?.[1] ?? null
}

export function useRunLocation() {
  const store = useRunStore()

  // URL -> store: on load, on back/forward, and on a pasted link.
  async function followHash(): Promise<void> {
    const id = runIdFromHash()
    if (id) {
      await store.resume(id)
    } else if (store.run) {
      store.reset()
    }
  }

  // store -> URL. Starting pushes a history entry so Back returns to the start screen; leaving
  // replaces it so Back does not return to the run just left.
  watch(
    () => store.run?.runId ?? null,
    (id) => {
      const wanted = id ? `#run/${id}` : ''
      if (window.location.hash === wanted) return
      if (id) {
        window.location.hash = wanted
      } else {
        window.history.replaceState(null, '', window.location.pathname + window.location.search)
      }
    },
  )

  onMounted(() => {
    window.addEventListener('hashchange', followHash)
    void followHash()
  })
  onBeforeUnmount(() => window.removeEventListener('hashchange', followHash))
}
