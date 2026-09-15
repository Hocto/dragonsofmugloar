import type { RunMode, RunView, TurnResultView, ApiErrorBody } from './types'

/**
 * One function per backend endpoint; the only place that calls fetch. Paths are relative to /api,
 * which the dev server and nginx both proxy same-origin.
 */

const BASE = '/api/runs'

/** A failure the UI can act on; `retryable` says whether a second attempt could succeed. */
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
    // No response at all; a retry may succeed.
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
    // The backend sends JSON errors; a proxy in front of it might not.
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

/** Gives up the turn. Takes no body. */
export function waitOutTurn(runId: string): Promise<TurnResultView> {
  return request<TurnResultView>(`${BASE}/${encodeURIComponent(runId)}/wait`, { method: 'POST' })
}

/** The SSE endpoint URL; subscribing is the composable's job. */
export function streamUrl(runId: string): string {
  return `${BASE}/${encodeURIComponent(runId)}/stream`
}
