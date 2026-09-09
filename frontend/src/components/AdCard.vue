<script setup lang="ts">
import { computed } from 'vue'
import DifficultyMeter from './DifficultyMeter.vue'
import type { AdView } from '@/api/types'

/**
 * One notice pinned to the board.
 *
 * Presentational: it takes an ad and emits an intent. It has never heard of fetch.
 */
const props = defineProps<{
  ad: AdView
  actionable: boolean
  pending: boolean
  disabled: boolean
}>()

const emit = defineEmits<{ solve: [adId: string] }>()

/** One turn left means take it now or lose it, and the card says so loudly. */
const urgent = computed(() => props.ad.expiresIn <= 1)
const fading = computed(() => props.ad.expiresIn <= 2)

const chance = computed(() =>
  props.ad.successChance === null ? null : Math.round(props.ad.successChance * 100),
)
</script>

<template>
  <li class="notice" :class="{ 'notice--urgent': urgent, 'notice--fading': fading }">
    <div class="notice__head">
      <p class="notice__reward numeral">
        <span class="visually-hidden">Reward </span>{{ ad.reward }}<span class="notice__unit">g</span>
      </p>
      <p class="notice__expiry" :class="{ 'notice__expiry--urgent': urgent }">
        <template v-if="urgent">Last chance</template>
        <template v-else>{{ ad.expiresIn }} turns left</template>
      </p>
    </div>

    <p class="notice__text">{{ ad.message }}</p>

    <DifficultyMeter :label="ad.risk" :rank="ad.difficultyRank" :of="ad.difficultyOf" />

    <p class="notice__meta">
      <span v-if="chance !== null" class="numeral">{{ chance }}% by the bot's reckoning</span>
      <span v-else-if="ad.skippedByStrategy" class="notice__skip">The bot would not risk this</span>
      <span v-if="ad.wasEncoded" class="notice__seal" :title="`Arrived ${ad.encoding}-encoded`">
        decoded
      </span>
      <span v-if="ad.recommended" class="notice__pick">the bot's pick</span>
    </p>

    <button
      v-if="actionable"
      type="button"
      class="seal notice__action"
      :disabled="disabled || pending"
      :aria-label="`Take the quest: ${ad.message}, reward ${ad.reward} gold, difficulty ${ad.risk}`"
      @click="emit('solve', ad.adId)"
    >
      <span v-if="pending" class="notice__spinner" aria-hidden="true" />
      {{ pending ? 'Riding out...' : 'Take the quest' }}
    </button>
  </li>
</template>

<style scoped>
.notice {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: var(--gap-2);
  padding: var(--gap-3);
  background: var(--paper-raised);
  border: 1px solid var(--paper-edge);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  /* Pinned by hand, so nothing sits perfectly straight. */
  transform: rotate(var(--tilt, 0deg));
}

/* The paper darkens and curls as the notice ages. */
.notice--fading {
  background: linear-gradient(160deg, var(--paper-raised), var(--paper-sunken));
}

.notice--urgent {
  border-color: var(--wax);
  box-shadow: var(--shadow), 0 0 0 1px var(--wax-wash);
}

.notice__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--gap-2);
}

.notice__reward {
  margin: 0;
  font-size: var(--step-3);
  font-weight: 600;
  color: var(--gilt);
  line-height: 1;
}

.notice__unit {
  font-size: 0.5em;
  margin-left: 0.15em;
}

.notice__expiry {
  font-size: 0.8125rem;
  color: var(--ink-faint);
  text-transform: lowercase;
}

.notice__expiry--urgent {
  color: var(--wax);
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  animation: pulse 1.4s ease-in-out infinite;
}

.notice__text {
  font-family: var(--display);
  font-size: var(--step-1);
  line-height: 1.4;
}

.notice__meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--gap-2);
  font-size: 0.8125rem;
  color: var(--ink-faint);
  min-height: 1.2em;
}

.notice__skip {
  color: var(--wax);
}

.notice__seal,
.notice__pick {
  padding: 0 0.4rem;
  border: 1px solid currentColor;
  border-radius: 999px;
  font-size: 0.6875rem;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.notice__pick {
  color: var(--moss);
}

.notice__action {
  margin-top: auto;
  width: 100%;
}

.notice__spinner {
  width: 0.75em;
  height: 0.75em;
  border: 2px solid currentColor;
  border-top-color: transparent;
  border-radius: 50%;
  animation: spin 700ms linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@keyframes pulse {
  50% {
    opacity: 0.45;
  }
}
</style>
