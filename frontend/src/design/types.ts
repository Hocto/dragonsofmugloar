/**
 * The contract every direction renders against.
 *
 * Deliberately nothing but what the game actually has. There is no mana, no per-quest health, no
 * stamina, and no difficulty number - difficulty is a word, and `difficultyRank` exists only so the
 * scale can be drawn without relying on colour.
 */

export interface PlayerState {
  lives: number
  gold: number
  score: number
  /** Number of upgrades bought. Treated as the dragon's power. */
  level: number
  turn: number
}

export interface Quest {
  id: string
  text: string
  reward: number
  turnsRemaining: number
  /** The word, exactly as the game says it: "Piece of cake" ... "Suicide mission". */
  difficulty: string
  /** 1 = safest. Present so difficulty reads without colour, never shown as a difficulty score. */
  difficultyRank: number
  difficultyOf: number
  /** What the selection algorithm scored it. Drives sort order, never displayed as gold. */
  selectionScore: number
  /** The dragon power at which this difficulty stops being a bad idea. */
  minimumPower: number
  /** 0..1 - the share of `reward` a player can realistically expect to keep. */
  expectedShare: number
  /** True when the algorithm would pass. Dimmed rather than hidden, so the reason stays visible. */
  wouldSkip: boolean
}

/** The only cumulative total in the game, and the only thing that gets a part-to-whole chart. */
export interface GoldBreakdown {
  upgrades: number
  healing: number
  inHand: number
}

/** Sorted by selection score, highest first. The order is the point: it shows how the bot thinks. */
export function bySelectionScore(quests: readonly Quest[]): Quest[] {
  return [...quests].sort((a, b) => b.selectionScore - a.selectionScore)
}

/** A quest is "high reward" relative to what is on the board, not against a fixed number. */
export function highRewardThreshold(quests: readonly Quest[]): number {
  if (quests.length === 0) return Infinity
  const max = Math.max(...quests.map((q) => q.reward))
  return max * 0.6
}

export function powerVerdict(quest: Quest, level: number): { covered: boolean; label: string } {
  return quest.minimumPower <= level
    ? { covered: true, label: 'Your power covers this' }
    : { covered: false, label: `Needs power ${quest.minimumPower}` }
}
