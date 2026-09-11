<script setup lang="ts">
import { computed } from 'vue'
import AdCard from './AdCard.vue'
import type { AdView } from '@/api/types'

const props = defineProps<{
  ads: AdView[]
  actionable: boolean
  pendingAdId: string | null
  busy: boolean
}>()

/**
 * Animate the board only when a person is reading it.
 *
 * In manual mode the board changes when the player acts, and an expiring notice lifting off is
 * worth seeing. In auto mode it is replaced two or three times a second, so per-card enter and
 * leave animations are just churn - and they never finish in a backgrounded tab, because a paused
 * animation frame leaves Vue's leave transition stranded and the elements pile up in the DOM.
 */
const animated = computed(() => props.actionable)

const emit = defineEmits<{ solve: [adId: string] }>()

/** Most urgent first, then most valuable. The same order a person would read the board in. */
const ordered = computed(() =>
  [...props.ads].sort((a, b) => a.expiresIn - b.expiresIn || b.reward - a.reward),
)
</script>

<template>
  <section class="board" aria-labelledby="board-heading">
    <h2 id="board-heading" class="board__heading">The message board</h2>

    <p v-if="ads.length === 0" class="board__empty">
      Nothing pinned up right now.
    </p>

    <!--
      Keyed by adId so Vue moves cards rather than rebuilding them, which is what makes an
      expiring notice leave cleanly instead of the whole board flickering.
    -->
    <TransitionGroup v-else tag="ul" name="notice" class="board__list" :css="animated">
      <AdCard
        v-for="ad in ordered"
        :key="ad.adId"
        :ad="ad"
        :actionable="actionable"
        :pending="pendingAdId === ad.adId"
        :disabled="busy"
        @solve="emit('solve', $event)"
      />
    </TransitionGroup>
  </section>
</template>

<style scoped>
.board__heading {
  font-size: var(--step-2);
  margin-bottom: var(--gap-3);
  padding-bottom: var(--gap-1);
  border-bottom: 1px solid var(--rule);
}

.board__empty {
  color: var(--ink-faint);
  font-style: italic;
}

.board__list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(15rem, 1fr));
  gap: var(--gap-3);
  list-style: none;
  margin: 0;
  padding: 0;
  /* Leaving cards are absolutely positioned, so they need this to have something to sit inside. */
  position: relative;
}

/* Give each card a slightly different tilt so the board is not a spreadsheet. */
.board__list > :nth-child(3n + 1) {
  --tilt: -0.5deg;
}
.board__list > :nth-child(3n + 2) {
  --tilt: 0.4deg;
}
.board__list > :nth-child(3n) {
  --tilt: -0.2deg;
}

.notice-enter-active,
.notice-leave-active {
  transition: opacity 260ms ease, transform 260ms ease;
}

.notice-enter-from {
  opacity: 0;
  transform: translateY(-6px) rotate(var(--tilt, 0deg));
}

/* Expiring notices lift off the board rather than blinking out. */
.notice-leave-to {
  opacity: 0;
  transform: translateY(-14px) rotate(calc(var(--tilt, 0deg) - 2deg));
}

.notice-leave-active {
  position: absolute;
}
</style>
