import { test, expect, type Page } from '@playwright/test'

/**
 * Start a manual game, take a quest, and play it into the ground.
 *
 * The backend is stubbed at the network boundary. A real run against Mugloar takes dozens of turns
 * and depends on dice and on a rate-limited third party, none of which belongs in a test that has
 * to pass on every push.
 */

const state = (over: Record<string, number> = {}) => ({
  gameId: 'e2e-1',
  lives: 3,
  gold: 60,
  level: 0,
  score: 0,
  highScore: 0,
  turn: 1,
  ...over,
})

const ad = (over: Record<string, unknown> = {}) => ({
  adId: 'ad-goat',
  message: 'Escort the goat to market',
  reward: 82,
  expiresIn: 4,
  risk: 'Piece of cake',
  difficultyRank: 1,
  difficultyOf: 11,
  wasEncoded: false,
  encoding: 'NONE',
  successChance: 0.91,
  score: 96,
  recommended: true,
  skippedByStrategy: false,
  ...over,
})

const shop = [
  { id: 'hpot', name: 'Healing potion', cost: 50, affordable: true, healing: true, recommended: false },
]

const run = (over: Record<string, unknown> = {}) => ({
  runId: 'e2e-1',
  mode: 'MANUAL',
  status: 'RUNNING',
  strategy: 'expected-value',
  state: state(),
  reputation: null,
  failure: null,
  ads: [ad(), ad({ adId: 'ad-dragon', message: 'Slay the neighbours dragon', expiresIn: 1, reward: 210, risk: 'Risky', difficultyRank: 7 })],
  shop,
  shopAdvice: { action: 'SKIP', itemId: null, reason: 'nothing worth buying at 60 gold' },
  events: [],
  summary: { solved: 0, failed: 0, bought: 0, idled: 0 },
  ...over,
})

/** A scripted three-turn game: win, lose, lose, dead. */
async function stubBackend(page: Page): Promise<void> {
  let solves = 0

  await page.route('**/api/runs', async (route) => {
    await route.fulfill({ status: 201, json: run() })
  })

  await page.route('**/api/runs/*/solve', async (route) => {
    solves += 1
    const won = solves === 1
    const next = state({ lives: 3 - solves, gold: 142, score: 82, turn: 1 + solves })
    await route.fulfill({
      status: 200,
      json: {
        event: {
          sequence: solves,
          action: 'SOLVED',
          target: 'ad-goat',
          description: 'Escort the goat to market',
          success: won,
          apiMessage: won ? 'You successfully solved the mission!' : 'You failed to solve the mission!',
          successChance: 0.91,
          risk: 'PIECE_OF_CAKE',
          reward: 82,
          state: next,
          delta: { lives: won ? 0 : -1, gold: won ? 82 : 0, score: won ? 82 : 0, level: 0, turn: 1 },
          at: new Date().toISOString(),
        },
        run: run({
          state: next,
          status: next.lives > 0 ? 'RUNNING' : 'FINISHED',
          summary: { solved: won ? 1 : 1, failed: solves - 1, bought: 0, idled: 0 },
        }),
      },
    })
  })

  await page.route('**/api/runs/*', async (route) => {
    await route.fulfill({ status: 200, json: run() })
  })
}

test('start a game, take a quest, and reach the end of the run', async ({ page }) => {
  await stubBackend(page)
  await page.goto('/')

  await expect(page.getByRole('heading', { name: 'Dragons of Mugloar' })).toBeVisible()

  await page.getByRole('button', { name: /Take the quests yourself/ }).click()

  // The HUD and the board are up.
  await expect(page.getByRole('heading', { name: 'The message board' })).toBeVisible()
  await expect(page.getByText('Lives')).toBeVisible()
  await expect(page.getByRole('heading', { name: 'The shop' })).toBeVisible()

  // The urgent ad is sorted to the front and says so in words, not only in colour.
  await expect(page.getByText('Last chance')).toBeVisible()

  // First quest succeeds, and the result shows the delta.
  await page.getByRole('button', { name: /Escort the goat to market/ }).click()
  await expect(page.getByRole('status')).toContainText('successfully solved')
  await expect(page.getByRole('status')).toContainText('+82 gold')

  // Two more failures take the last lives.
  await page.getByRole('button', { name: /Escort the goat to market/ }).click()
  await expect(page.getByRole('status')).toContainText('failed')

  await page.getByRole('button', { name: /Escort the goat to market/ }).click()

  // Game over, with a summary and a way back.
  await expect(page.getByRole('heading', { name: 'Out of lives' })).toBeVisible()
  await expect(page.getByText('final score')).toBeVisible()
  // The counts come from the server's summary, not from whatever the client happened to hold.
  await expect(page.locator('dt:has-text("Quests solved") + dd')).toHaveText('1')
  await expect(page.locator('dt:has-text("Quests failed") + dd')).toHaveText('2')

  await page.getByRole('button', { name: 'Back to the den' }).click()
  await expect(page.getByRole('heading', { name: 'Dragons of Mugloar' })).toBeVisible()
})

test('an error offers a retry instead of a blank screen', async ({ page }) => {
  let attempts = 0
  await page.route('**/api/runs', async (route) => {
    attempts += 1
    if (attempts === 1) {
      await route.fulfill({
        status: 502,
        json: { error: 'UPSTREAM_ERROR', message: 'Mugloar did not cooperate', retryable: true, at: '' },
      })
      return
    }
    await route.fulfill({ status: 201, json: run() })
  })
  await page.route('**/api/runs/*', async (route) => route.fulfill({ status: 200, json: run() }))

  await page.goto('/')
  // Manual mode, so the failure under test is the start call and not the SSE stream.
  await page.getByRole('button', { name: /Take the quests yourself/ }).click()

  const alert = page.getByRole('alert')
  await expect(alert).toContainText('Mugloar did not cooperate')

  await alert.getByRole('button', { name: 'Try again' }).click()
  await expect(page.getByRole('heading', { name: 'The message board' })).toBeVisible()
})

test('the run survives a refresh and the back button leaves it', async ({ page }) => {
  await stubBackend(page)
  await page.goto('/')
  await page.getByRole('button', { name: /Take the quests yourself/ }).click()
  await expect(page.getByRole('heading', { name: 'The message board' })).toBeVisible()

  // Starting a run puts its id in the address bar.
  await expect(page).toHaveURL(/#run\/e2e-1$/)

  // A refresh comes back to the same run, not to the start screen.
  await page.reload()
  await expect(page.getByRole('heading', { name: 'The message board' })).toBeVisible()
  await expect(page.getByText('playing by hand')).toBeVisible()

  // Back returns to the start screen, and the run id leaves the address bar with it.
  await page.goBack()
  await expect(page.getByRole('heading', { name: 'Dragons of Mugloar' })).toBeVisible()
  await expect(page).not.toHaveURL(/#run/)
})

test('leave is reachable mid-game and returns to the start', async ({ page }) => {
  await stubBackend(page)
  await page.goto('/')
  await page.getByRole('button', { name: /Take the quests yourself/ }).click()
  await expect(page.getByRole('heading', { name: 'The message board' })).toBeVisible()

  await page.getByRole('button', { name: /^Leave this run/ }).click()

  await expect(page.getByRole('heading', { name: 'Dragons of Mugloar' })).toBeVisible()
  await expect(page).not.toHaveURL(/#run/)
})

test('a stale link lands on the start screen with a reason, not a blank page', async ({ page }) => {
  await page.route('**/api/runs/*', async (route) => {
    await route.fulfill({
      status: 404,
      json: { error: 'RUN_NOT_FOUND', message: 'No run with id gone', retryable: false, at: '' },
    })
  })

  await page.goto('/#run/gone')

  await expect(page.getByRole('heading', { name: 'Dragons of Mugloar' })).toBeVisible()
  await expect(page.getByRole('alert')).toContainText('No run with id gone')
})
