<script setup lang="ts">
/**
 * The pass move for manual play. Sits above the board so it is reachable without scrolling or
 * tabbing past every quest, and keeps one row in both states so the board does not shift when the
 * recommendation changes. Waiting replaces nothing on the board; it only ages every notice by one
 * turn, so the countdown to the soonest expiry is shown to make the move legible.
 */
defineProps<{
  recommended: boolean
  pending: boolean
  disabled: boolean
  /** Turns until the soonest notice expires and is replaced. Null when the board is empty. */
  turnsUntilBoardChanges: number | null
}>()

const emit = defineEmits<{ wait: [] }>()
</script>

<template>
  <section class="wait" :class="{ 'wait--urged': recommended }" aria-labelledby="wait-heading">
    <p class="wait__copy">
      <span id="wait-heading" class="wait__heading">Let the turn pass</span>
      <span class="wait__body">
        <template v-if="recommended">Nothing here is worth the risk.</template>
        <template v-else>Costs a turn, risks nothing.</template>
        <template v-if="turnsUntilBoardChanges !== null">
          Board changes in
          <span class="wait__countdown numeral">{{ turnsUntilBoardChanges }}</span>
          {{ turnsUntilBoardChanges === 1 ? 'turn' : 'turns' }}.
        </template>
      </span>
    </p>

    <button
      type="button"
      class="seal wait__action"
      :class="{ 'seal--primary': recommended }"
      :disabled="disabled || pending"
      aria-label="Let the turn pass without attempting a quest. Costs one turn and risks no lives."
      @click="emit('wait')"
    >
      {{ pending ? 'Waiting...' : 'Wait it out' }}
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
