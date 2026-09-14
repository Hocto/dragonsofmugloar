/**
 * The backend's contract, hand-written rather than generated.
 *
 * There are eight types here and generating them would mean a build step, a schema endpoint and a
 * dependency. At this size, typing them by hand and letting the store tests catch drift is the
 * cheaper trade.
 */

export type RunMode = 'AUTO' | 'MANUAL'
export type RunStatus = 'RUNNING' | 'FINISHED' | 'FAILED'
export type TurnAction = 'STARTED' | 'SOLVED' | 'BOUGHT' | 'IDLED' | 'FINISHED' | 'FAILED'
export type AdEncoding = 'NONE' | 'BASE64' | 'ROT13'

export interface GameState {
  gameId: string
  lives: number
  gold: number
  level: number
  score: number
  highScore: number
  turn: number
}

export interface Reputation {
  people: number
  state: number
  underworld: number
}

export interface AdView {
  adId: string
  message: string
  reward: number
  expiresIn: number
  /** The label exactly as Mugloar spells it, e.g. "Piece of cake". */
  risk: string
  /** 1 is safest. Sent as a number so the UI never has to encode difficulty as colour alone. */
  difficultyRank: number
  difficultyOf: number
  wasEncoded: boolean
  encoding: AdEncoding
  successChance: number | null
  score: number | null
  recommended: boolean
  /** True when the strategy refuses to attempt it, e.g. a gamble on the last life. */
  skippedByStrategy: boolean
}

export interface ShopItemView {
  id: string
  name: string
  cost: number
  affordable: boolean
  healing: boolean
  recommended: boolean
}

export interface ShopAdviceView {
  action: 'BUY' | 'SKIP'
  itemId: string | null
  reason: string
}

export interface StateDelta {
  lives: number
  gold: number
  score: number
  level: number
  turn: number
}

export interface TurnEvent {
  sequence: number
  action: TurnAction
  target: string | null
  description: string | null
  success: boolean
  apiMessage: string | null
  successChance: number | null
  risk: string | null
  reward: number | null
  state: GameState
  delta: StateDelta
  at: string
}

export interface RunView {
  runId: string
  mode: RunMode
  status: RunStatus
  strategy: string
  state: GameState
  reputation: Reputation | null
  failure: string | null
  ads: AdView[]
  shop: ShopItemView[]
  shopAdvice: ShopAdviceView
  events: TurnEvent[]
}

export interface TurnResultView {
  event: TurnEvent
  run: RunView
}

export interface ApiErrorBody {
  error: string
  message: string
  retryable: boolean
  at: string
}
