<script setup lang="ts">
import { computed } from 'vue'

/**
 * One thin bar per quest: how much of the reward the player can expect to keep, against the share
 * risk eats.
 *
 * The bar's *length* is the reward relative to the biggest one on the board, and the *split* is the
 * odds. That is what makes a high-reward gamble read as a long bar that is half red, and a safe
 * little job as a short bar that is entirely green - the two dimensions stay separable.
 *
 * Geometry is shared across all three directions; only thickness and colour change, through CSS
 * custom properties. The numbers are also written out underneath, because a bar on its own is a
 * picture of a fact rather than the fact.
 */
const props = defineProps<{
  reward: number
  /** 0..1 */
  expectedShare: number
  /** Largest reward on the board, so bars are comparable row to row. */
  boardMaxReward: number
  /** Hides the written figures where the direction already states them nearby. */
  compact?: boolean
}>()

const lengthPct = computed(() =>
  props.boardMaxReward > 0 ? Math.max(6, (props.reward / props.boardMaxReward) * 100) : 0,
)
const expectedPct = computed(() => Math.round(props.expectedShare * 100))
const expectedGold = computed(() => Math.round(props.reward * props.expectedShare))
const riskGold = computed(() => props.reward - expectedGold.value)
</script>

<template>
  <div class="bar">
    <div class="bar__track" :style="{ width: `${lengthPct}%` }" aria-hidden="true">
      <span class="bar__expected" :style="{ width: `${expectedPct}%` }" />
      <span class="bar__risk" />
    </div>
    <p v-if="!compact" class="bar__legend">
      <span class="bar__figure bar__figure--expected u-num">{{ expectedGold }}g expected</span>
      <span class="bar__figure bar__figure--risk u-num">{{ riskGold }}g at risk</span>
    </p>
    <span class="u-hidden">
      Of {{ reward }} gold, about {{ expectedGold }} expected and {{ riskGold }} at risk.
    </span>
  </div>
</template>

<style scoped>
.bar {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
}

.bar__track {
  display: flex;
  height: var(--bar-height, 3px);
  min-width: 2rem;
  background: transparent;
}

.bar__expected {
  background: var(--expected);
}

.bar__risk {
  flex: 1;
  background: var(--risk);
}

.bar__legend {
  display: flex;
  flex-wrap: wrap;
  gap: 0 0.75rem;
  margin: 0;
  font-family: var(--sans);
  font-size: 0.6875rem;
  letter-spacing: 0.01em;
}

.bar__figure--expected {
  color: var(--expected);
}

.bar__figure--risk {
  color: var(--risk);
}
</style>
