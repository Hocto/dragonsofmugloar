import { http, HttpResponse } from 'msw'
import { runView, turnEvent } from './fixtures'
import type { ApiErrorBody, RunView, TurnResultView } from '@/api/types'

/**
 * The backend, stubbed. MSW intercepts at the network layer, so the store and the api client under
 * test are the real ones - only the server is fake.
 */
export const handlers = [
  http.post('/api/runs', async ({ request }) => {
    const body = (await request.json()) as { mode: RunView['mode'] }
    return HttpResponse.json(runView({ mode: body.mode }), { status: 201 })
  }),

  http.get('/api/runs/:runId', () => HttpResponse.json(runView())),

  http.post('/api/runs/:runId/solve', () =>
    HttpResponse.json<TurnResultView>({
      event: turnEvent(),
      run: runView({ state: turnEvent().state }),
    }),
  ),

  http.post('/api/runs/:runId/buy', () =>
    HttpResponse.json<TurnResultView>({
      event: turnEvent({ action: 'BOUGHT', target: 'hpot', description: 'Bought by hand' }),
      run: runView(),
    }),
  ),
]

/** Handy shorthand for the failure paths, which are half the point of the store tests. */
export const failing = {
  startRun: (status: number, body: ApiErrorBody) =>
    http.post('/api/runs', () => HttpResponse.json(body, { status })),
  solve: (status: number, body: ApiErrorBody) =>
    http.post('/api/runs/:runId/solve', () => HttpResponse.json(body, { status })),
}
