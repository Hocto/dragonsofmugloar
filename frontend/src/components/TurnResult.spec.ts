import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import TurnResult from './TurnResult.vue'
import { turnEvent } from '@/test/fixtures'
import type { BoardChange } from '@/stores/run'

const passed = () =>
  turnEvent({
    action: 'IDLED',
    apiMessage: null,
    delta: { lives: 0, gold: 0, score: 0, level: 0, turn: 1 },
  })

function mountResult(props: {
  event?: ReturnType<typeof turnEvent>
  boardChange?: BoardChange | null
  turnsUntilBoardChanges?: number | null
} = {}) {
  return mount(TurnResult, {
    props: {
      event: props.event ?? turnEvent(),
      boardChange: props.boardChange ?? null,
      turnsUntilBoardChanges: props.turnsUntilBoardChanges ?? null,
    },
  })
}

describe('TurnResult', () => {
  it("reports a solved quest with Mugloar's own words and the gold", () => {
    const result = mountResult()

    expect(result.text()).toContain('You successfully solved the mission!')
    expect(result.text()).toContain('+82 gold')
  })

  it('does not call a pass a success, and does not claim nothing changed', () => {
    const result = mountResult({ event: passed() })

    expect(result.text()).toContain('You let the turn pass.')
    expect(result.text()).toContain('+1 turn, nothing risked')
    expect(result.text()).not.toContain('That went well')
    expect(result.text()).not.toContain('Nothing changed')
    expect(result.get('.result').classes()).toContain('result--neutral')
  })

  it('says when a pass changed nothing on the board, and when the board will change', () => {
    // A pass only ages the board. Without this line it looks like a no-op, because visibly it
    // very nearly is.
    const result = mountResult({
      event: passed(),
      boardChange: { expired: 0, arrived: 0 },
      turnsUntilBoardChanges: 3,
    })

    expect(result.text()).toContain('Nothing expired. The board changes in 3 turns.')
  })

  it('says how many notices a pass removed and replaced when something did expire', () => {
    const result = mountResult({
      event: passed(),
      boardChange: { expired: 7, arrived: 7 },
      turnsUntilBoardChanges: 2,
    })

    expect(result.text()).toContain('7 notices left the board, 7 new.')
  })

  it('gets the singular right', () => {
    const result = mountResult({
      event: passed(),
      boardChange: { expired: 1, arrived: 1 },
      turnsUntilBoardChanges: 1,
    })

    expect(result.text()).toContain('1 notice left the board, 1 new.')
  })

  it('says the same for a solve, which replaces the solved notice', () => {
    const result = mountResult({ boardChange: { expired: 1, arrived: 1 }, turnsUntilBoardChanges: 5 })

    expect(result.text()).toContain('1 notice left the board, 1 new.')
  })

  it('marks a failed attempt as bad', () => {
    const result = mountResult({
      event: turnEvent({
        success: false,
        apiMessage: 'You failed to solve the mission!',
        delta: { lives: -1, gold: 0, score: 0, level: 0, turn: 1 },
      }),
    })

    expect(result.get('.result').classes()).toContain('result--bad')
    expect(result.text()).toContain('-1 life')
  })
})
