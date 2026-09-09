import { onBeforeUnmount, ref, watch } from 'vue'
import { streamUrl } from '@/api/client'
import { useRunStore } from '@/stores/run'
import type { TurnEvent } from '@/api/types'

/**
 * Subscribes to the per-turn SSE stream for an auto run.
 *
 * The server replays a run's history before sending live turns, so a reconnect is not a special
 * case - it just replays, and the store drops sequence numbers it has already seen. That is why
 * there is no cursor to track here.
 *
 * EventSource reconnects on its own, but it does so silently and forever. After a few failed
 * attempts this stops and hands the user an explicit retry instead of spinning quietly.
 */
const MAX_SILENT_RECONNECTS = 3

export function useTurnStream() {
  const store = useRunStore()
  const connected = ref(false)
  let source: EventSource | null = null
  let failures = 0

  function close(): void {
    source?.close()
    source = null
    connected.value = false
  }

  function connect(runId: string): void {
    close()
    const stream = new EventSource(streamUrl(runId))
    source = stream

    stream.addEventListener('open', () => {
      connected.value = true
      failures = 0
      store.clearError()
    })

    stream.addEventListener('turn', (message) => {
      const event = JSON.parse((message as MessageEvent<string>).data) as TurnEvent
      store.applyStreamedTurn(event)
      // The stream carries state, not the board, so the ads are refreshed alongside it.
      void store.refreshBoardQuietly()
    })

    stream.addEventListener('error', () => {
      connected.value = false
      // A completed stream also lands here, and a finished run needs no reconnect.
      if (store.phase === 'gameOver') {
        close()
        return
      }
      failures += 1
      if (failures > MAX_SILENT_RECONNECTS) {
        close()
        store.reportStreamError(async () => {
          failures = 0
          connect(runId)
        })
      }
    })
  }

  // Follows the current auto run, and stops as soon as there is not one.
  watch(
    () => (store.isAuto && store.run?.status === 'RUNNING' ? store.run.runId : null),
    (runId) => {
      if (runId) {
        connect(runId)
      } else {
        close()
      }
    },
    { immediate: true },
  )

  onBeforeUnmount(close)

  return { connected }
}
