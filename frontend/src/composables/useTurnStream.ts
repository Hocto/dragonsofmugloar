import { onBeforeUnmount, ref, watch } from 'vue'
import { streamUrl } from '@/api/client'
import { useRunStore } from '@/stores/run'
import type { TurnEvent } from '@/api/types'

/**
 * Subscribes to the per-turn SSE stream for an auto run. The server replays history before live
 * turns and the store discards events by sequence number, so reconnecting needs no cursor. After
 * a few silent reconnects this stops and offers an explicit retry.
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
      // The stream carries state, not the board; the ads are refreshed alongside it.
      void store.refreshBoardQuietly()
    })

    stream.addEventListener('error', () => {
      connected.value = false
      // A completed stream also lands here; a finished run needs no reconnect.
      if (store.phase === 'gameOver') {
        close()
        return
      }
      // EventSource retries a dropped connection on its own but gives up for good on a 404 or
      // 500, leaving readyState at CLOSED. That is reported immediately.
      if (stream.readyState === EventSource.CLOSED) {
        close()
        store.reportStreamError(async () => {
          failures = 0
          connect(runId)
        })
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
