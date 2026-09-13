import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import QuestBoardEditorial from './editorial/QuestBoardEditorial.vue'
import QuestBoardQuiet from './quiet/QuestBoardQuiet.vue'
import QuestBoardBold from './bold/QuestBoardBold.vue'
import HudEditorial from './editorial/HudEditorial.vue'
import HudQuiet from './quiet/HudQuiet.vue'
import HudBold from './bold/HudBold.vue'
import { sampleQuests, samplePlayer } from './sample'
import type { Quest } from './types'

/**
 * The rules that hold across all three directions, run against all three.
 *
 * A direction is free to look however it likes, but it is not free to sort by anything other than
 * the selection score, to drop a quest the algorithm skipped, or to say something in colour that it
 * does not also say in words. Parameterising means a fourth direction inherits the same bar.
 */
const boards = [
  ['editorial', QuestBoardEditorial],
  ['quiet', QuestBoardQuiet],
  ['bold', QuestBoardBold],
] as const

const huds = [
  ['editorial', HudEditorial],
  ['quiet', HudQuiet],
  ['bold', HudBold],
] as const

function mountBoard(component: (typeof boards)[number][1], quests: Quest[] = sampleQuests) {
  return mount(component, { props: { quests, level: samplePlayer.level } })
}

describe.each(boards)('%s quest board', (_name, component) => {
  it('orders by selection score, never by reward and never by the order it was given', () => {
    const board = mountBoard(component)
    const rendered = board.findAll('h3').map((h) => h.text())

    const expected = [...sampleQuests]
      .sort((a, b) => b.selectionScore - a.selectionScore)
      .map((q) => q.text)

    expect(rendered).toEqual(expected)
    // Guard against a reward sort happening to look right on this fixture.
    const byReward = [...sampleQuests].sort((a, b) => b.reward - a.reward).map((q) => q.text)
    expect(rendered).not.toEqual(byReward)
    expect(rendered).not.toEqual(sampleQuests.map((q) => q.text))
  })

  it('keeps quests the algorithm would skip on the board rather than removing them', () => {
    const board = mountBoard(component)
    const skipped = sampleQuests.filter((q) => q.wouldSkip)

    expect(skipped.length).toBeGreaterThan(0)
    for (const quest of skipped) {
      expect(board.text()).toContain(quest.text)
    }
    expect(board.findAll('li')).toHaveLength(sampleQuests.length)
  })

  it('says the power verdict in words, both when covered and when short', () => {
    const board = mountBoard(component)

    expect(board.text()).toContain('Your power covers this')
    expect(board.text()).toMatch(/Needs power \d+/)
  })

  it('names the difficulty as a word on every quest', () => {
    const board = mountBoard(component)

    for (const quest of sampleQuests) {
      expect(board.text()).toContain(quest.difficulty)
    }
  })

  it('gives every action an accessible name that identifies the quest', () => {
    const board = mountBoard(component)
    const labels = board.findAll('button').map((b) => b.attributes('aria-label') ?? '')

    expect(labels).toHaveLength(sampleQuests.length)
    for (const label of labels) {
      expect(label).toMatch(/Take the quest:/)
      expect(label).toMatch(/gold/)
      expect(label).toMatch(/difficulty/)
    }
  })

  it('emits the quest id and nothing else when an action is used', async () => {
    const board = mountBoard(component)
    await board.findAll('button')[0]!.trigger('click')

    const best = [...sampleQuests].sort((a, b) => b.selectionScore - a.selectionScore)[0]!
    expect(board.emitted('attempt')).toEqual([[best.id]])
  })

  it('marks a quest on its last turn in words, not only with colour', () => {
    const board = mountBoard(component, [
      { ...sampleQuests[0]!, id: 'expiring', turnsRemaining: 1 },
    ])

    expect(board.text().toLowerCase()).toContain('last turn')
  })

  it('drops a lapsed quest cleanly when it is no longer in the data', async () => {
    const board = mountBoard(component)
    const survivor = sampleQuests[0]!

    await board.setProps({ quests: [survivor] })

    expect(board.findAll('li')).toHaveLength(1)
    expect(board.text()).toContain(survivor.text)
  })

  it('does not invent a stat the game does not have', () => {
    const text = mountBoard(component).text().toLowerCase()

    // Whole words only - "xp" and "hp" are both substrings of "expected".
    for (const invented of ['mana', 'stamina', 'health', 'hp', 'xp', 'energy']) {
      expect(text).not.toMatch(new RegExp(`\\b${invented}\\b`))
    }
  })

  it('survives an empty board without throwing', () => {
    expect(() => mountBoard(component, [])).not.toThrow()
  })
})

describe.each(huds)('%s hud', (_name, component) => {
  it('shows all five stats', () => {
    const hud = mount(component, { props: { player: samplePlayer } })
    const text = hud.text()

    expect(text).toContain('Lives')
    expect(text).toContain('Gold')
    expect(text).toContain('Score')
    expect(text).toContain('Power')
    expect(text).toContain('Turn')
    expect(text).toContain(String(samplePlayer.lives))
    expect(text).toContain(String(samplePlayer.gold))
    expect(text).toContain(String(samplePlayer.score))
  })

  it('is labelled as a region so it is reachable and announced', () => {
    const hud = mount(component, { props: { player: samplePlayer } })

    expect(hud.attributes('aria-label')).toBe('Player state')
  })
})
