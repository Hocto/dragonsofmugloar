import type { GoldBreakdown, PlayerState, Quest } from './types'

/**
 * A board built to exercise the cases that matter, not a pretty one: a high-reward gamble the
 * player has no power for, a safe little job, a quest on its last turn, one the algorithm would
 * skip, and an out-of-order selection score so the sort is visibly doing something.
 */
export const samplePlayer: PlayerState = {
  lives: 3,
  gold: 227,
  score: 1077,
  level: 7,
  turn: 25,
}

export const sampleGold: GoldBreakdown = {
  upgrades: 900,
  healing: 150,
  inHand: 227,
}

export const sampleQuests: Quest[] = [
  {
    id: 'q-ridgepine',
    text: 'Help defend the church in Redseed from the intruders',
    reward: 226,
    turnsRemaining: 2,
    difficulty: 'Quite likely',
    difficultyRank: 5,
    difficultyOf: 11,
    selectionScore: 214,
    minimumPower: 4,
    expectedShare: 0.66,
    wouldSkip: false,
  },
  {
    id: 'q-towergill',
    text: 'Help defend the hill in Towergill from the intruders',
    reward: 192,
    turnsRemaining: 1,
    difficulty: 'Suicide mission',
    difficultyRank: 10,
    difficultyOf: 11,
    selectionScore: 9,
    minimumPower: 24,
    expectedShare: 0.02,
    wouldSkip: true,
  },
  {
    id: 'q-burnscaster',
    text: 'Help defend the foggy riverside in Burnscaster from the intruders',
    reward: 195,
    turnsRemaining: 5,
    difficulty: 'Hmmm....',
    difficultyRank: 6,
    difficultyOf: 11,
    selectionScore: 141,
    minimumPower: 9,
    expectedShare: 0.51,
    wouldSkip: false,
  },
  {
    id: 'q-darkfair',
    text: 'Help defend the rocky plains in Darkfair from the intruders',
    reward: 189,
    turnsRemaining: 3,
    difficulty: 'Rather detrimental',
    difficultyRank: 8,
    difficultyOf: 11,
    selectionScore: 71,
    minimumPower: 14,
    expectedShare: 0.33,
    wouldSkip: true,
  },
  {
    id: 'q-snowdean',
    text: 'Escort Beverly Granville to the meadow in Snowdean to meet their long lost dog',
    reward: 64,
    turnsRemaining: 6,
    difficulty: 'Piece of cake',
    difficultyRank: 1,
    difficultyOf: 11,
    selectionScore: 78,
    minimumPower: 0,
    expectedShare: 0.91,
    wouldSkip: false,
  },
  {
    id: 'q-bleakfair',
    text: 'Help Lleu Sherburne reach an agreement with Lettie Lindsey over a disputed dog',
    reward: 57,
    turnsRemaining: 1,
    difficulty: 'Piece of cake',
    difficultyRank: 1,
    difficultyOf: 11,
    selectionScore: 104,
    minimumPower: 0,
    expectedShare: 0.91,
    wouldSkip: false,
  },
  {
    id: 'q-maonchester',
    text: 'Rescue Devaraj Immers from thrift shopping in Maonchester, where angry water awaits',
    reward: 47,
    turnsRemaining: 2,
    difficulty: 'Walk in the park',
    difficultyRank: 2,
    difficultyOf: 11,
    selectionScore: 66,
    minimumPower: 0,
    expectedShare: 0.84,
    wouldSkip: false,
  },
]
