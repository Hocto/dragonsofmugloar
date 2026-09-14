import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import GameScreen from './GameScreen.vue'
import { runView, ad, turnEvent } from '@/test/fixtures'
import type { RunView } from '@/api/types'

function mountScreen(run: Partial<RunView> = {}) {
  return mount(GameScreen, {
    props: {
      run: runView(run),
      feed: [],
      lastEvent: null,
      pendingAdId: null,
      pendingItemId: null,
      canAct: true,
      busy: false,
      streamConnected: false,
    },
  })
}

describe('GameScreen', () => {
  it('puts the pass action before the board, not after it', () => {
    // The moment you need it is the moment the whole board is a bad bet. Behind ten quest cards
    // means scrolling past every tempting mistake to reach it, and tabbing through ten buttons.
    const screen = mountScreen()
    const html = screen.html()

    expect(html.indexOf('wait-heading')).toBeGreaterThan(-1)
    expect(html.indexOf('wait-heading')).toBeLessThan(html.indexOf('board-heading'))
  })

  it('reaches the pass button before any quest button in tab order', () => {
    const screen = mountScreen({
      ads: [ad({ adId: 'a' }), ad({ adId: 'b' }), ad({ adId: 'c' })],
    })
    const labels = screen.findAll('button').map((b) => b.attributes('aria-label') ?? b.text())

    expect(labels[0]).toContain('Let the turn pass')
  })

  it('urges the pass only when the strategy has refused the whole board', () => {
    const allSkipped = mountScreen({
      ads: [ad({ adId: 'a', skippedByStrategy: true }), ad({ adId: 'b', skippedByStrategy: true })],
    })
    expect(allSkipped.get('.wait').classes()).toContain('wait--urged')

    const someViable = mountScreen({
      ads: [ad({ adId: 'a', skippedByStrategy: true }), ad({ adId: 'b', skippedByStrategy: false })],
    })
    expect(someViable.get('.wait').classes()).not.toContain('wait--urged')
  })

  it('does not urge the pass on an empty board, where there is nothing to refuse', () => {
    expect(mountScreen({ ads: [] }).get('.wait').classes()).not.toContain('wait--urged')
  })

  it('hides the pass entirely while the bot is playing', () => {
    const screen = mountScreen({ mode: 'AUTO' })

    expect(screen.find('.wait').exists()).toBe(false)
  })

  it('forwards the pass as an event and fetches nothing itself', async () => {
    const screen = mountScreen()
    await screen.get('.wait button').trigger('click')

    expect(screen.emitted('wait')).toHaveLength(1)
  })

  it('shows the result of the last turn above the board', () => {
    const screen = mount(GameScreen, {
      props: {
        run: runView(),
        feed: [],
        lastEvent: turnEvent({ action: 'IDLED', apiMessage: null }),
        pendingAdId: null,
        pendingItemId: null,
        canAct: true,
        busy: false,
        streamConnected: false,
      },
    })

    expect(screen.text()).toContain('You let the turn pass.')
  })
})
