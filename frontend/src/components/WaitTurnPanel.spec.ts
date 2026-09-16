import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import WaitTurnPanel from './WaitTurnPanel.vue'

/**
 * The move exists so a player is never forced to gamble a life on a hopeless board. These pin down
 * that it stays available even when the bot disagrees, and that it explains itself in words.
 */
function mountPanel(props: Partial<InstanceType<typeof WaitTurnPanel>['$props']> = {}) {
  return mount(WaitTurnPanel, {
    props: {
      recommended: false,
      pending: false,
      disabled: false,
      turnsUntilBoardChanges: 3,
      turnsRemaining: 10,
      ...props,
    },
  })
}

describe('WaitTurnPanel', () => {
  it('puts the cost in turns on the button itself', () => {
    expect(mountPanel({ turnsUntilBoardChanges: 3 }).get('button').text()).toBe('Wait 3 turns for a new board')
    expect(mountPanel({ turnsUntilBoardChanges: 1 }).get('button').text()).toBe('Wait 1 turn for a new board')
  })

  it('says what the move risks', () => {
    expect(mountPanel().text()).toContain('Risks nothing')
  })

  it('says so when the strategy would also wait', () => {
    expect(mountPanel({ recommended: true }).text()).toContain('Nothing here is worth the risk')
  })

  it('explains why the board changes at all', () => {
    expect(mountPanel().text()).toContain('the board changes when a notice expires')
  })

  it('offers a single turn when the board is empty', () => {
    expect(mountPanel({ turnsUntilBoardChanges: null }).get('button').text()).toBe('Wait 1 turn for a new board')
  })

  it('shows how many waiting turns the game has left', () => {
    expect(mountPanel({ turnsRemaining: 4 }).text()).toContain('4 waiting turns left this game')
    expect(mountPanel({ turnsRemaining: 1, turnsUntilBoardChanges: 1 }).text()).toContain('1 waiting turn left this game')
  })

  it('disables the button and says why when the budget is spent', () => {
    const panel = mountPanel({ turnsRemaining: 0 })

    expect(panel.get('button').attributes('disabled')).toBeDefined()
    expect(panel.text()).toContain('No waiting turns left this game')
    expect(panel.get('button').attributes('aria-label')).toContain('Cannot wait')
  })

  it('disables the button when the budget cannot see the board through', () => {
    // Three turns left, board needs five. Spending the three would leave the same board.
    const panel = mountPanel({ turnsRemaining: 3, turnsUntilBoardChanges: 5 })

    expect(panel.get('button').attributes('disabled')).toBeDefined()
    expect(panel.text()).toContain('Only 3 waiting turns left; this board needs 5')
  })

  it('does not urge a wait it would then refuse', () => {
    const panel = mountPanel({ recommended: true, turnsRemaining: 0 })

    expect(panel.classes()).not.toContain('wait--urged')
    expect(panel.text()).not.toContain('Nothing here is worth the risk')
  })

  it('keeps to one row in both states so it does not resize as the board changes', () => {
    // The panel sits above the board now. If it grew a line when the last viable quest expired,
    // the whole board would jump down at exactly the wrong moment.
    const quiet = mountPanel({ recommended: false })
    const urged = mountPanel({ recommended: true })

    expect(quiet.findAll('p')).toHaveLength(1)
    expect(urged.findAll('p')).toHaveLength(1)
  })

  it('stays available even when the strategy would not wait', () => {
    // The hint is a hint. The player decides.
    const panel = mountPanel({ recommended: false })

    expect(panel.get('button').attributes('disabled')).toBeUndefined()
  })

  it('emits once when used', async () => {
    const panel = mountPanel()
    await panel.get('button').trigger('click')

    expect(panel.emitted('wait')).toHaveLength(1)
  })

  it('shows a pending state and cannot be fired twice', () => {
    const panel = mountPanel({ pending: true })

    expect(panel.text()).toContain('Waiting...')
    expect(panel.get('button').attributes('disabled')).toBeDefined()
  })

  it('is disabled while another action holds the turn', () => {
    expect(mountPanel({ disabled: true }).get('button').attributes('disabled')).toBeDefined()
  })

  it('has an accessible name that states the cost and the consequence', () => {
    const label = mountPanel({ turnsUntilBoardChanges: 3 }).get('button').attributes('aria-label') ?? ''

    expect(label).toContain('Wait 3 turns')
    expect(label).toContain('without attempting')
    expect(label).toContain('risks no lives')
  })
})
