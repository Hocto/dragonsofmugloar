import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import WaitTurnPanel from './WaitTurnPanel.vue'

/**
 * The move exists so a player is never forced to gamble a life on a hopeless board. These pin down
 * that it stays available even when the bot disagrees, and that it explains itself in words.
 */
function mountPanel(props: Partial<InstanceType<typeof WaitTurnPanel>['$props']> = {}) {
  return mount(WaitTurnPanel, {
    props: { recommended: false, pending: false, disabled: false, ...props },
  })
}

describe('WaitTurnPanel', () => {
  it('says what the move costs and what it risks', () => {
    expect(mountPanel().text()).toContain('Costs a turn, risks nothing')
  })

  it('says so when the strategy would also wait', () => {
    expect(mountPanel({ recommended: true }).text()).toContain('Nothing on the board is worth the risk')
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
