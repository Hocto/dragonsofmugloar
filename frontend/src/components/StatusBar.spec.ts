import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import StatusBar from './StatusBar.vue'
import { gameState } from '@/test/fixtures'
import { TARGET_SCORE } from '@/game'

function mountHud(score: number) {
  return mount(StatusBar, {
    props: { state: gameState({ score }), delta: null, strategy: 'expected-value', mode: 'MANUAL' },
  })
}

describe('StatusBar', () => {
  it('says nothing about the target below it', () => {
    expect(mountHud(TARGET_SCORE - 1).text()).not.toContain('cleared')
  })

  it('marks the score once it reaches the target, in words', () => {
    // Reaching the target does not end the run; the game only ends on lives. This is a marker.
    const hud = mountHud(TARGET_SCORE)

    expect(hud.text()).toContain('cleared 1,000')
    expect(hud.get('.hud__cleared').text()).toBe('cleared 1,000')
  })

  it('keeps the marker well above the target', () => {
    expect(mountHud(4289).text()).toContain('cleared 1,000')
  })
})
