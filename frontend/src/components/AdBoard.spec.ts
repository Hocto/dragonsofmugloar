import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import AdBoard from './AdBoard.vue'
import { ad } from '@/test/fixtures'

/**
 * The board is where the accessibility requirements actually live, so this leans on them: the
 * difficulty has to be readable without colour, and every action needs a label that says what it
 * will do.
 */
function mountBoard(props: Partial<InstanceType<typeof AdBoard>['$props']> = {}) {
  return mount(AdBoard, {
    props: {
      ads: [ad()],
      actionable: true,
      pendingAdId: null,
      busy: false,
      ...props,
    },
  })
}

describe('AdBoard', () => {
  it('says so instead of rendering an empty grid', () => {
    const board = mountBoard({ ads: [] })

    expect(board.text()).toContain('Nothing pinned up right now')
    expect(board.findAll('li')).toHaveLength(0)
  })

  it('puts the ads that expire soonest first', () => {
    const board = mountBoard({
      ads: [
        ad({ adId: 'patient', message: 'Patient', expiresIn: 6 }),
        ad({ adId: 'urgent', message: 'Urgent', expiresIn: 1 }),
        ad({ adId: 'middling', message: 'Middling', expiresIn: 3 }),
      ],
    })

    const order = board.findAll('li').map((row) => row.text())
    expect(order[0]).toContain('Urgent')
    expect(order[1]).toContain('Middling')
    expect(order[2]).toContain('Patient')
  })

  it('breaks ties on reward', () => {
    const board = mountBoard({
      ads: [
        ad({ adId: 'small', message: 'Small', expiresIn: 3, reward: 10 }),
        ad({ adId: 'big', message: 'Big', expiresIn: 3, reward: 300 }),
      ],
    })

    expect(board.findAll('li')[0]?.text()).toContain('Big')
  })

  it('spells out the difficulty in words and in a rank, not only in colour', () => {
    const board = mountBoard({
      ads: [ad({ risk: 'Rather detrimental', difficultyRank: 8, difficultyOf: 11 })],
    })

    expect(board.text()).toContain('Rather detrimental')
    expect(board.text()).toContain('difficulty 8 of 11')
  })

  it('marks an ad on its last turn as urgent in text, not just style', () => {
    const board = mountBoard({ ads: [ad({ expiresIn: 1 })] })

    expect(board.text()).toContain('Last chance')
    expect(board.text()).not.toContain('turns left')
  })

  it('gives every action a label that describes what it does', () => {
    const board = mountBoard({
      ads: [ad({ message: 'Escort the goat', reward: 82, risk: 'Piece of cake' })],
    })

    const label = board.get('button').attributes('aria-label')
    expect(label).toContain('Escort the goat')
    expect(label).toContain('82 gold')
    expect(label).toContain('Piece of cake')
  })

  it('emits the ad id when a quest is taken', async () => {
    const board = mountBoard({ ads: [ad({ adId: 'chosen' })] })

    await board.get('button').trigger('click')

    expect(board.emitted('solve')).toEqual([['chosen']])
  })

  it('shows an inline pending state on the ad being solved and disables the rest', () => {
    const board = mountBoard({
      ads: [ad({ adId: 'a' }), ad({ adId: 'b' })],
      pendingAdId: 'a',
      busy: true,
    })

    const buttons = board.findAll('button')
    expect(board.text()).toContain('Riding out...')
    expect(buttons.every((button) => button.attributes('disabled') !== undefined)).toBe(true)
  })

  it('hides the actions entirely when the bot is playing', () => {
    const board = mountBoard({ actionable: false })

    expect(board.findAll('button')).toHaveLength(0)
  })

  it('drops expired cards straight out of the DOM when the bot is playing', async () => {
    // The auto board is replaced two or three times a second. Animating each card in and out is
    // churn, and a backgrounded tab pauses animation frames, which strands Vue's leave transition
    // and leaves the elements in the DOM for as long as the run lasts.
    const board = mountBoard({
      actionable: false,
      ads: [ad({ adId: 'a' }), ad({ adId: 'b' })],
    })
    expect(board.findAll('li')).toHaveLength(2)

    await board.setProps({ ads: [ad({ adId: 'b' })] })

    expect(board.findAll('li')).toHaveLength(1)
    expect(board.html()).not.toContain('leave-active')
  })

  it('says when the strategy would not touch an ad', () => {
    const board = mountBoard({
      ads: [ad({ successChance: null, skippedByStrategy: true })],
    })

    expect(board.text()).toContain('The bot would not risk this')
  })

  it('flags an ad that arrived encoded, since the backend decoded it', () => {
    const board = mountBoard({ ads: [ad({ wasEncoded: true, encoding: 'ROT13' })] })

    expect(board.text()).toContain('decoded')
  })
})
