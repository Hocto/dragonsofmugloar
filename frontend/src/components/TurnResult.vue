<script setup lang="ts">
import { computed } from 'vue'
import type { TurnEvent } from '@/api/types'
import type { BoardChange } from '@/stores/run'

/** Feedback after a manual move, with the state change spelled out. */
const props = defineProps<{
  event: TurnEvent
  boardChange: BoardChange | null
  /** Turns until the soonest remaining notice expires. */
  turnsUntilBoardChanges: number | null
}>()

/** Passing is neither a win nor a loss and gets a neutral tone. */
const tone = computed(() => {
  if (props.event.action === 'IDLED') return 'neutral'
  return props.event.success ? 'good' : 'bad'
})

const headline = computed(() => {
  if (props.event.action === 'IDLED') return 'You sat the board out.'
  return props.event.apiMessage ?? (props.event.success ? 'That went well.' : 'That went badly.')
})

/** What the move did to the board: turns spent waiting, and notices that left and arrived. */
const boardNote = computed(() => {
  const change = props.boardChange
  if (!change) return null
  const waited =
    props.event.action === 'IDLED'
      ? `Waited ${change.turns} ${change.turns === 1 ? 'turn' : 'turns'}. `
      : ''
  if (change.expired > 0 || change.arrived > 0) {
    const gone = `${change.expired} ${change.expired === 1 ? 'notice' : 'notices'} left the board`
    const came = change.arrived > 0 ? `, ${change.arrived} new` : ''
    return `${waited}${gone}${came}.`
  }
  if (props.event.action !== 'IDLED') return null
  if (props.turnsUntilBoardChanges === null) return `${waited}The board is empty.`
  const t = props.turnsUntilBoardChanges
  return `${waited}Nothing expired. The board changes in ${t} ${t === 1 ? 'turn' : 'turns'}.`
})

const changes = computed(() => {
  const d = props.event.delta
  const parts: string[] = []
  if (d.gold) parts.push(`${d.gold > 0 ? '+' : ''}${d.gold} gold`)
  if (d.lives) parts.push(`${d.lives > 0 ? '+' : ''}${d.lives} ${Math.abs(d.lives) === 1 ? 'life' : 'lives'}`)
  if (d.score) parts.push(`+${d.score} score`)
  if (d.level) parts.push(`+${d.level} level`)
  // For a wait the board note carries the turn count; the delta here is only the last turn's.
  if (parts.length === 0 && d.turn && props.event.action !== 'IDLED') parts.push(`+${d.turn} turn`)
  return parts
})
</script>

<template>
  <div class="result" :class="`result--${tone}`" role="status">
    <p class="result__headline">{{ headline }}</p>
    <p v-if="changes.length" class="result__changes numeral">{{ changes.join(' · ') }}</p>
    <!-- A wait's outcome is the board note below, so the generic fallback would contradict it. -->
    <p v-else-if="event.action !== 'IDLED'" class="result__changes">Nothing changed.</p>
    <p v-if="boardNote" class="result__board">{{ boardNote }}</p>
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

.result__board {
  margin-top: 0.15rem;
  color: var(--ink-soft);
  font-size: 0.8125rem;
}
</style>
