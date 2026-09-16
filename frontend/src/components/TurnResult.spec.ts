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

  it('does not call a wait a success and does not claim nothing changed', () => {
    const result = mountResult({
      event: passed(),
      boardChange: { turns: 3, expired: 7, arrived: 7 },
      turnsUntilBoardChanges: 4,
    })

    expect(result.text()).toContain('You sat the board out.')
    expect(result.text()).not.toContain('That went well')
    expect(result.text()).not.toContain('Nothing changed')
    expect(result.get('.result').classes()).toContain('result--neutral')
  })

  it('says how many turns the wait cost and what it changed on the board', () => {
    const result = mountResult({
      event: passed(),
      boardChange: { turns: 3, expired: 7, arrived: 7 },
      turnsUntilBoardChanges: 4,
    })

    expect(result.text()).toContain('Waited 3 turns. 7 notices left the board, 7 new.')
  })

  it('says so when the wait hit the safety cap without the board changing', () => {
    const result = mountResult({
      event: passed(),
      boardChange: { turns: 10, expired: 0, arrived: 0 },
      turnsUntilBoardChanges: 2,
    })

    expect(result.text()).toContain('Waited 10 turns. Nothing expired. The board changes in 2 turns.')
  })

  it('gets the singulars right', () => {
    const result = mountResult({
      event: passed(),
      boardChange: { turns: 1, expired: 1, arrived: 1 },
      turnsUntilBoardChanges: 1,
    })

    expect(result.text()).toContain('Waited 1 turn. 1 notice left the board, 1 new.')
  })

  it('reports the replaced notice on a solve without a waited line', () => {
    const result = mountResult({ boardChange: { turns: 1, expired: 1, arrived: 1 }, turnsUntilBoardChanges: 5 })

    expect(result.text()).toContain('1 notice left the board, 1 new.')
    expect(result.text()).not.toContain('Waited')
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
