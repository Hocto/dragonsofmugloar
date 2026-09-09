import type { RunMode, RunView, TurnResultView, ApiErrorBody } from './types'

/**
 * One function per backend endpoint, and the only place in the app that knows about fetch.
 *
 * Everything is relative to /api. In development Vite proxies that to :8080 and in Docker nginx
 * does the same, so the browser is always talking to its own origin and there is no base URL to
 * configure at build time.
 */

const BASE = '/api/runs'

/** A failure the UI can act on: it knows whether offering "try again" makes any sense. */
export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly retryable: boolean,
    readonly code: string,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  let response: Response
  try {
    response = await fetch(url, {
      headers: { 'Content-Type': 'application/json' },
      ...init,
    })
  } catch {
    // Network-level failure: no response at all, and retrying is exactly the right suggestion.
    throw new ApiError('Could not reach the server.', 0, true, 'NETWORK')
  }

  if (!response.ok) {
    throw await toApiError(response)
  }
  return (await response.json()) as T
}

async function toApiError(response: Response): Promise<ApiError> {
  try {
    const body = (await response.json()) as ApiErrorBody
    return new ApiError(body.message, response.status, body.retryable, body.error)
  } catch {
    // The backend always sends JSON errors, but a proxy in front of it might not.
    return new ApiError(
      `The server replied ${response.status}.`,
      response.status,
      response.status >= 500,
      'UNKNOWN',
    )
  }
}

export function startRun(mode: RunMode): Promise<RunView> {
  return request<RunView>(BASE, { method: 'POST', body: JSON.stringify({ mode }) })
}

export function getRun(runId: string): Promise<RunView> {
  return request<RunView>(`${BASE}/${encodeURIComponent(runId)}`)
}

export function solveAd(runId: string, adId: string): Promise<TurnResultView> {
  return request<TurnResultView>(`${BASE}/${encodeURIComponent(runId)}/solve`, {
    method: 'POST',
    body: JSON.stringify({ adId }),
  })
}

export function buyItem(runId: string, itemId: string): Promise<TurnResultView> {
  return request<TurnResultView>(`${BASE}/${encodeURIComponent(runId)}/buy`, {
    method: 'POST',
    body: JSON.stringify({ itemId }),
  })
}

/** The SSE endpoint. Subscribing is the composable's job; this just names the URL. */
export function streamUrl(runId: string): string {
  return `${BASE}/${encodeURIComponent(runId)}/stream`
}
