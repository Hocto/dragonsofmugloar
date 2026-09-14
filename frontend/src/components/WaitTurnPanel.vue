<script setup lang="ts">
/**
 * The pass move, for a human.
 *
 * There is no "skip" in the Mugloar API, but a turn can still be given up, and the bot already does
 * it when every notice on the board is a bad bet. Manual mode promises the same board, the same
 * shop and the same moves, so it gets the same option.
 *
 * It is never disabled on the grounds that waiting looks unwise - the player decides. When the
 * strategy would also wait, the panel says so rather than deciding for them.
 */
defineProps<{
  recommended: boolean
  pending: boolean
  disabled: boolean
}>()

const emit = defineEmits<{ wait: [] }>()
</script>

<template>
  <section class="wait" :class="{ 'wait--urged': recommended }" aria-labelledby="wait-heading">
    <div class="wait__text">
      <h2 id="wait-heading" class="wait__heading">Let the turn pass</h2>
      <p class="wait__body">
        <template v-if="recommended">
          Nothing on the board is worth the risk right now. Waiting costs a turn and nothing else.
        </template>
        <template v-else>
          Costs a turn, risks nothing. Notices expire and new ones arrive either way.
        </template>
      </p>
    </div>

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
  gap: var(--gap-2) var(--gap-3);
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

.wait__heading {
  font-family: var(--display);
  font-size: var(--step-1);
  margin: 0;
}

.wait__body {
  margin: 0.15rem 0 0;
  font-size: 0.8125rem;
  color: var(--ink-soft);
  max-width: 34rem;
}

.wait--urged .wait__body {
  color: var(--ink);
}

.wait__action {
  flex: none;
}

@media (max-width: 30rem) {
  .wait__action {
    width: 100%;
  }
}
</style>
