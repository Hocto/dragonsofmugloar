import { onBeforeUnmount, onMounted, watch } from 'vue'
import { useRunStore } from '@/stores/run'

/**
 * Keeps the run id in the URL hash, and the URL hash in the store.
 *
 * This is what makes a refresh survivable and the back button mean something. The server already
 * holds the run and can replay it; without the id in the URL the browser had no way to ask for it,
 * which made the server's reattach support unreachable from the one client that exists.
 *
 * A hash rather than a path so the static build needs no server-side rewrite, and a composable
 * rather than a router because one pattern, `#run/<id>`, is not enough routing to justify the
 * dependency. If a second page ever appears, that is the moment to swap this for Vue Router.
 */
const RUN_HASH = /^#run\/([A-Za-z0-9_-]+)$/

export function runIdFromHash(hash: string = window.location.hash): string | null {
  const match = RUN_HASH.exec(hash)
  return match?.[1] ?? null
}

export function useRunLocation() {
  const store = useRunStore()

  // URL -> store. Fires on load, on back/forward, and on a pasted link.
  async function followHash(): Promise<void> {
    const id = runIdFromHash()
    if (id) {
      await store.resume(id)
    } else if (store.run) {
      store.reset()
    }
  }

  // store -> URL. Starting a run pushes a history entry, so Back returns to the start screen;
  // leaving a run clears the hash without pushing, so Back does not bounce you into it again.
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
