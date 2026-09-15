import type { RunView, TurnEvent } from '@/api/types'

export function gameState(over: Partial<RunView['state']> = {}): RunView['state'] {
  return { gameId: 'g1', lives: 3, gold: 40, level: 0, score: 0, highScore: 0, turn: 1, ...over }
}

export function ad(over: Partial<RunView['ads'][number]> = {}): RunView['ads'][number] {
  return {
    adId: 'ad1',
    message: 'Help someone with their goat',
    reward: 82,
    expiresIn: 5,
    risk: 'Piece of cake',
    difficultyRank: 1,
    difficultyOf: 11,
    wasEncoded: false,
    encoding: 'NONE',
    successChance: 0.91,
    score: 96,
    recommended: false,
    skippedByStrategy: false,
    ...over,
  }
}

export function runView(over: Partial<RunView> = {}): RunView {
  return {
    runId: 'g1',
    mode: 'MANUAL',
    status: 'RUNNING',
    strategy: 'expected-value',
    state: gameState(),
    reputation: null,
    failure: null,
    ads: [ad()],
    shop: [
      { id: 'hpot', name: 'Healing potion', cost: 50, affordable: false, healing: true, recommended: false },
      { id: 'cs', name: 'Claw Sharpening', cost: 100, affordable: false, healing: false, recommended: false },
    ],
    shopAdvice: { action: 'SKIP', itemId: null, reason: 'nothing worth buying at 40 gold' },
    events: [],
    summary: { solved: 0, failed: 0, bought: 0, idled: 0 },
    ...over,
  }
}

export function turnEvent(over: Partial<TurnEvent> = {}): TurnEvent {
  return {
    sequence: 1,
    action: 'SOLVED',
    target: 'ad1',
    description: 'Help someone with their goat',
    success: true,
    apiMessage: 'You successfully solved the mission!',
    successChance: 0.91,
    risk: 'PIECE_OF_CAKE',
    reward: 82,
    state: gameState({ gold: 122, score: 82, turn: 2 }),
    delta: { lives: 0, gold: 82, score: 82, level: 0, turn: 1 },
    at: '2026-01-01T00:00:00Z',
    ...over,
  }
}
