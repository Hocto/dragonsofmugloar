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
      ...props,
    },
  })
}

describe('WaitTurnPanel', () => {
  it('says what the move costs and what it risks', () => {
    expect(mountPanel().text()).toContain('Costs a turn, risks nothing')
  })

  it('says so when the strategy would also wait', () => {
    expect(mountPanel({ recommended: true }).text()).toContain('Nothing here is worth the risk')
  })

  it('says how long until the board actually changes', () => {
    // Waiting does not deal a new hand - it ages the board, and the board only changes when
    // something expires. Without this the move looks like it does nothing.
    expect(mountPanel({ turnsUntilBoardChanges: 3 }).text()).toContain('Board changes in 3 turns')
  })

  it('gets the singular right on the last turn before the board moves', () => {
    const text = mountPanel({ turnsUntilBoardChanges: 1 }).text()

    expect(text).toContain('Board changes in 1 turn')
    expect(text).not.toContain('1 turns')
  })

  it('says nothing about a countdown when the board is empty', () => {
    expect(mountPanel({ turnsUntilBoardChanges: null }).text()).not.toContain('Board changes')
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

  it('has an accessible name that explains the consequence', () => {
    const label = mountPanel().get('button').attributes('aria-label') ?? ''

    expect(label).toContain('without attempting')
    expect(label).toContain('risks no lives')
  })
})
