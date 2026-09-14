<script setup lang="ts">
/**
 * The pass move, for a human.
 *
 * There is no "skip" in the Mugloar API, but a turn can still be given up, and the bot already does
 * it when every notice on the board is a bad bet. Manual mode promises the same board, the same
 * shop and the same moves, so it gets the same option.
 *
 * It sits above the board rather than below it. The moment you need it most is the moment the whole
 * board is a bad bet, and burying it under ten quest cards meant scrolling past every tempting
 * mistake to reach it - and tabbing through ten "Take the quest" buttons to get there by keyboard.
 *
 * One row in both states, so the panel does not resize when the board changes underneath it. It is
 * never disabled on the grounds that waiting looks unwise; when the strategy would also wait it
 * says so and leaves the decision alone.
 *
 * The countdown is the part that makes the move make sense. Waiting does not deal a new hand - the
 * board holds ten notices, solving one replaces it, and waiting replaces nothing. All a turn does
 * is age every notice by one, so the board changes only when something expires. Without saying how
 * far away that is, passing looks like it does nothing, because for that turn it very nearly does.
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
