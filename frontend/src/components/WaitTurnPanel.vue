<script setup lang="ts">
import { computed } from 'vue'

/**
 * Waits until the board changes, for manual play. A single pass replaces nothing on the board,
 * only ages every notice by one turn, so one click passes as many turns as the soonest expiry
 * needs and the label states that cost up front. Sits above the board so it is reachable without
 * scrolling past every quest, and keeps one row in both states so the board does not shift.
 */
const props = defineProps<{
  recommended: boolean
  pending: boolean
  disabled: boolean
  /** Turns until the soonest notice expires and is replaced. Null when the board is empty. */
  turnsUntilBoardChanges: number | null
  /** Turns this game may still spend waiting. */
  turnsRemaining: number
}>()

const emit = defineEmits<{ wait: [] }>()

const turns = computed(() => props.turnsUntilBoardChanges ?? 1)
const plural = (n: number) => (n === 1 ? 'turn' : 'turns')

/** Refused for the same reason the strategy refuses: a wait the budget cannot see through. */
const unaffordable = computed(() => props.turnsRemaining <= 0 || turns.value > props.turnsRemaining)

const budgetNote = computed(() => {
  if (props.turnsRemaining <= 0) return 'No waiting turns left this game.'
  if (unaffordable.value)
    return `Only ${props.turnsRemaining} waiting ${plural(props.turnsRemaining)} left; this board needs ${turns.value}.`
  return `${props.turnsRemaining} waiting ${plural(props.turnsRemaining)} left this game.`
})

const label = computed(() =>
  props.pending ? 'Waiting...' : `Wait ${turns.value} ${plural(turns.value)} for a new board`,
)
const ariaLabel = computed(() =>
  unaffordable.value
    ? `Cannot wait: ${budgetNote.value}`
    : `Wait ${turns.value} ${plural(turns.value)} until the board changes, without attempting a quest. Costs ${turns.value} ${plural(turns.value)} and risks no lives. ${budgetNote.value}`,
)
</script>

<template>
  <section class="wait" :class="{ 'wait--urged': recommended && !unaffordable }" aria-labelledby="wait-heading">
    <p class="wait__copy">
      <span id="wait-heading" class="wait__heading">Sit this board out</span>
      <span class="wait__body">
        <template v-if="recommended && !unaffordable">Nothing here is worth the risk.</template>
        <template v-else-if="!unaffordable">Risks nothing; the board changes when a notice expires.</template>
        <span :class="{ 'wait__budget--out': unaffordable }">{{ budgetNote }}</span>
      </span>
    </p>

    <button
      type="button"
      class="seal wait__action"
      :class="{ 'seal--primary': recommended && !unaffordable }"
      :disabled="disabled || pending || unaffordable"
      :aria-label="ariaLabel"
      @click="emit('wait')"
    >
      {{ label }}
    </button>
  </section>
</template>

<style scoped>
.wait {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--gap-2);
  padding: var(--gap-2) var(--gap-3);
  border: 1px dashed var(--rule-strong);
  border-radius: var(--radius);
}

/* Only a hint. The button stays available whether or not the bot would agree. */
.wait--urged {
  border-style: solid;
  border-color: var(--wax);
  background: var(--wax-wash);
}

.wait__copy {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 0 var(--gap-2);
  margin: 0;
  min-width: 0;
}

.wait__heading {
  font-family: var(--display);
  font-size: var(--step-1);
}

.wait__body {
  font-size: 0.8125rem;
  color: var(--ink-soft);
}

.wait--urged .wait__body {
  color: var(--wax);
  font-weight: 600;
}

.wait__budget--out {
  color: var(--wax);
  font-weight: 600;
}

.wait__countdown {
  font-weight: 700;
}

.wait__action {
  flex: none;
  min-height: 2.5rem;
  padding: 0.45rem 1.1rem;
  font-size: var(--step-0);
}

@media (max-width: 30rem) {
  .wait {
    gap: var(--gap-2);
  }

  .wait__action {
    width: 100%;
    min-height: 2.75rem;
  }
}
</style>
