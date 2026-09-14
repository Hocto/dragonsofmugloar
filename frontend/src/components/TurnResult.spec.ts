import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import TurnResult from './TurnResult.vue'
import { turnEvent } from '@/test/fixtures'

describe('TurnResult', () => {
  it('reports a solved quest with Mugloar\'s own words and the gold', () => {
    const result = mount(TurnResult, { props: { event: turnEvent() } })

    expect(result.text()).toContain('You successfully solved the mission!')
    expect(result.text()).toContain('+82 gold')
  })

  it('does not call a pass a success, and does not claim nothing changed', () => {
    // A turn passed. "That went well" and "Nothing changed" are both wrong for this move.
    const result = mount(TurnResult, {
      props: {
        event: turnEvent({
          action: 'IDLED',
          apiMessage: null,
          delta: { lives: 0, gold: 0, score: 0, level: 0, turn: 1 },
        }),
      },
    })

    expect(result.text()).toContain('You let the turn pass.')
    expect(result.text()).toContain('+1 turn, nothing risked')
    expect(result.text()).not.toContain('That went well')
    expect(result.text()).not.toContain('Nothing changed')
    expect(result.get('.result').classes()).toContain('result--neutral')
  })

  it('marks a failed attempt as bad', () => {
    const result = mount(TurnResult, {
      props: {
        event: turnEvent({
          success: false,
          apiMessage: 'You failed to solve the mission!',
          delta: { lives: -1, gold: 0, score: 0, level: 0, turn: 1 },
        }),
      },
    })

    expect(result.get('.result').classes()).toContain('result--bad')
    expect(result.text()).toContain('-1 life')
  })
})
