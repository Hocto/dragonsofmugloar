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
}>()

const emit = defineEmits<{ wait: [] }>()

const turns = computed(() => props.turnsUntilBoardChanges ?? 1)
const label = computed(() =>
  props.pending
    ? 'Waiting...'
    : `Wait ${turns.value} ${turns.value === 1 ? 'turn' : 'turns'} for a new board`,
)
const ariaLabel = computed(
  () =>
    `Wait ${turns.value} ${turns.value === 1 ? 'turn' : 'turns'} until the board changes, without attempting a quest. Costs ${turns.value} ${turns.value === 1 ? 'turn' : 'turns'} and risks no lives.`,
)
</script>

<template>
  <section class="wait" :class="{ 'wait--urged': recommended }" aria-labelledby="wait-heading">
    <p class="wait__copy">
      <span id="wait-heading" class="wait__heading">Sit this board out</span>
      <span class="wait__body">
        <template v-if="recommended">Nothing here is worth the risk.</template>
        <template v-else>Risks nothing.</template>
        The board changes when a notice expires.
      </span>
    </p>

    <button
      type="button"
      class="seal wait__action"
      :class="{ 'seal--primary': recommended }"
      :disabled="disabled || pending"
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
