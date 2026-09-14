<script setup lang="ts">
import { computed } from 'vue'
import type { TurnEvent } from '@/api/types'

/** Feedback after a manual attempt, with the state change spelled out rather than implied. */
const props = defineProps<{ event: TurnEvent }>()

/** Passing is neither a win nor a loss, so it gets its own tone rather than the success one. */
const tone = computed(() => {
  if (props.event.action === 'IDLED') return 'neutral'
  return props.event.success ? 'good' : 'bad'
})

const headline = computed(() => {
  if (props.event.action === 'IDLED') return 'You let the turn pass.'
  return props.event.apiMessage ?? (props.event.success ? 'That went well.' : 'That went badly.')
})

const changes = computed(() => {
  const d = props.event.delta
  const parts: string[] = []
  if (d.gold) parts.push(`${d.gold > 0 ? '+' : ''}${d.gold} gold`)
  if (d.lives) parts.push(`${d.lives > 0 ? '+' : ''}${d.lives} ${Math.abs(d.lives) === 1 ? 'life' : 'lives'}`)
  if (d.score) parts.push(`+${d.score} score`)
  if (d.level) parts.push(`+${d.level} level`)
  // A turn is only worth stating when it is the whole of what happened - which is the case for a
  // pass, and would otherwise read as noise next to the gold and the score.
  if (parts.length === 0 && d.turn) parts.push(`+${d.turn} turn, nothing risked`)
  return parts
})
</script>

<template>
  <div class="result" :class="`result--${tone}`" role="status">
    <p class="result__headline">{{ headline }}</p>
    <p v-if="changes.length" class="result__changes numeral">{{ changes.join(' · ') }}</p>
    <p v-else class="result__changes">Nothing changed.</p>
  </div>
</template>

<style scoped>
.result {
  padding: var(--gap-2) var(--gap-3);
  border-left: 3px solid currentColor;
  background: var(--paper-raised);
  border-radius: var(--radius);
}

.result--good {
  color: var(--moss);
  background: var(--moss-wash);
}

.result--bad {
  color: var(--wax);
  background: var(--wax-wash);
}

.result--neutral {
  color: var(--ink-soft);
  background: var(--paper-sunken);
}

.result__headline {
  font-family: var(--display);
  font-size: var(--step-1);
}

.result__changes {
  color: var(--ink-soft);
  font-size: 0.8125rem;
}
</style>
