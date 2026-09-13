<script setup lang="ts">
import { ref } from 'vue'
import QuestBoardEditorial from './editorial/QuestBoardEditorial.vue'
import HudEditorial from './editorial/HudEditorial.vue'
import QuestBoardQuiet from './quiet/QuestBoardQuiet.vue'
import HudQuiet from './quiet/HudQuiet.vue'
import QuestBoardBold from './bold/QuestBoardBold.vue'
import HudBold from './bold/HudBold.vue'
import GoldDonut from './GoldDonut.vue'
import { samplePlayer, sampleQuests, sampleGold } from './sample'
import type { Quest } from './types'

/**
 * A harness, not a screen. It exists so the three directions can be compared with the same data
 * rather than described, and so expiry and removal can be poked at by hand.
 */
const quests = ref<Quest[]>([...sampleQuests])
const lastTaken = ref<string | null>(null)

function onAttempt(id: string) {
  lastTaken.value = id
}

/** Mimics a turn passing, so the last-turn treatment and clean removal can be seen. */
function advanceTurn() {
  quests.value = quests.value
    .map((q) => ({ ...q, turnsRemaining: q.turnsRemaining - 1 }))
    .filter((q) => q.turnsRemaining > 0)
}

function reset() {
  quests.value = [...sampleQuests]
  lastTaken.value = null
}

const directions = [
  {
    key: 'editorial',
    name: 'One — Editorial',
    note: 'Prioritises atmosphere and the sense that the board is a place, with the ranking and the reasoning set like a printed page. Gives up density: about four quests fit a screen, so scanning a full board means scrolling.',
  },
  {
    key: 'quiet',
    name: 'Two — Quiet',
    note: 'Prioritises reading and comparison, with aligned figures and the whole board visible at once. Gives up atmosphere and some of the visceral sense of reward, since a 226 gold job and a 47 gold one look structurally alike.',
  },
  {
    key: 'bold',
    name: 'Three — Bold',
    note: 'Prioritises making value pre-attentive: the money is legible from across the room before any word is read. Gives up calm and some comparability, because a board of mostly high rewards turns into a wall of amber.',
  },
] as const
</script>

<template>
  <div class="dom-scope gallery">
    <header class="gallery__head">
      <h1>Quest board — three directions</h1>
      <p class="gallery__lede">
        Same data, same contract, three takes. Sorted by selection score in all three.
      </p>
      <div class="gallery__controls">
        <button type="button" @click="advanceTurn">Advance a turn</button>
        <button type="button" @click="reset">Reset board</button>
        <p v-if="lastTaken" class="gallery__event" role="status">
          Emitted <code>attempt</code> for {{ lastTaken }}
        </p>
      </div>
    </header>

    <section v-for="direction in directions" :key="direction.key" class="gallery__panel">
      <div class="gallery__meta">
        <h2>{{ direction.name }}</h2>
        <p>{{ direction.note }}</p>
      </div>

      <div class="gallery__demo">
        <template v-if="direction.key === 'editorial'">
          <HudEditorial :player="samplePlayer" />
          <QuestBoardEditorial :quests="quests" :level="samplePlayer.level" @attempt="onAttempt" />
          <GoldDonut :gold="sampleGold" />
        </template>
        <template v-else-if="direction.key === 'quiet'">
          <HudQuiet :player="samplePlayer" />
          <QuestBoardQuiet :quests="quests" :level="samplePlayer.level" @attempt="onAttempt" />
          <GoldDonut :gold="sampleGold" />
        </template>
        <template v-else>
          <HudBold :player="samplePlayer" />
          <QuestBoardBold :quests="quests" :level="samplePlayer.level" @attempt="onAttempt" />
          <GoldDonut :gold="sampleGold" />
        </template>
      </div>
    </section>
  </div>
</template>

<style scoped>
.gallery {
  min-height: 100vh;
  padding: 2rem 1rem 5rem;
}

.gallery__head {
  max-width: 70rem;
  margin: 0 auto 2.5rem;
}

.gallery__head h1 {
  font-family: var(--serif);
  font-size: clamp(1.5rem, 5vw, 2.25rem);
  margin: 0;
}

.gallery__lede {
  margin: 0.4rem 0 1rem;
  font-family: var(--sans);
  color: var(--ink-muted);
}

.gallery__controls {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.75rem;
}

.gallery__controls button {
  padding: 0.5rem 1rem;
  background: transparent;
  border: 1px solid var(--ink);
  border-radius: 0;
  font-family: var(--sans);
  font-size: 0.875rem;
}

.gallery__controls button:hover {
  background: var(--ink);
  color: var(--paper);
}

.gallery__event {
  margin: 0;
  font-family: var(--sans);
  font-size: 0.8125rem;
  color: var(--ink-muted);
}

.gallery__panel {
  max-width: 70rem;
  margin: 0 auto 4rem;
  padding-top: 2rem;
  border-top: 1px solid var(--edge);
}

.gallery__meta {
  margin-bottom: 1.5rem;
}

.gallery__meta h2 {
  font-family: var(--sans);
  font-size: 0.75rem;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  margin: 0 0 0.4rem;
  color: var(--ink-muted);
}

.gallery__meta p {
  margin: 0;
  max-width: 44rem;
  font-family: var(--serif);
  font-size: 1rem;
  line-height: 1.5;
}

.gallery__demo {
  display: flex;
  flex-direction: column;
  gap: 2rem;
  max-width: 44rem;
}
</style>
