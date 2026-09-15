<script setup lang="ts">
import { computed } from 'vue'
import type { RunView } from '@/api/types'

const props = defineProps<{ run: RunView }>()
const emit = defineEmits<{ restart: [] }>()

// Counts come from the server, over the whole run. The feed the client holds is capped, so a
// long run or one resumed after a refresh would have under-reported itself if counted here.
const solved = computed(() => props.run.summary.solved)
const failed = computed(() => props.run.summary.failed)
const bought = computed(() => props.run.summary.bought)
const brokeOff = computed(() => props.run.status === 'FAILED')
</script>

<template>
  <main class="over">
    <h1 class="over__title">{{ brokeOff ? 'The run broke off' : 'Out of lives' }}</h1>

    <p v-if="brokeOff" class="over__failure">{{ run.failure }}</p>

    <p class="over__score numeral">
      {{ run.state.score }}
      <span class="over__score-label">final score</span>
    </p>

    <dl class="over__summary">
      <div><dt>Quests solved</dt><dd class="numeral">{{ solved }}</dd></div>
      <div><dt>Quests failed</dt><dd class="numeral">{{ failed }}</dd></div>
      <div><dt>Bought in the shop</dt><dd class="numeral">{{ bought }}</dd></div>
      <div><dt>Turns taken</dt><dd class="numeral">{{ run.state.turn }}</dd></div>
      <div><dt>Dragon level reached</dt><dd class="numeral">{{ run.state.level }}</dd></div>
      <div><dt>Gold left over</dt><dd class="numeral">{{ run.state.gold }}</dd></div>
    </dl>

    <p class="over__verdict">
      {{ run.state.score >= 1000 ? 'Cleared the thousand.' : 'Short of a thousand this time.' }}
    </p>

    <button type="button" class="seal seal--primary" @click="emit('restart')">
      Back to the den
    </button>
  </main>
</template>

<style scoped>
.over {
  max-width: 32rem;
  margin: 0 auto;
  padding: var(--gap-5) var(--gap-3);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--gap-3);
  text-align: center;
}

.over__title {
  font-size: var(--step-3);
}

.over__failure {
  color: var(--wax);
  font-size: 0.8125rem;
}

.over__score {
  font-size: 4rem;
  line-height: 1;
  color: var(--gilt);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--gap-1);
}

.over__score-label {
  font-family: var(--body);
  font-size: 0.6875rem;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--ink-faint);
}

.over__summary {
  width: 100%;
  margin: 0;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(9rem, 1fr));
  gap: var(--gap-2);
  text-align: left;
}

.over__summary > div {
  padding: var(--gap-2);
  border: 1px solid var(--rule);
  border-radius: var(--radius);
  background: var(--paper-raised);
}

.over__summary dt {
  font-size: 0.6875rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--ink-faint);
}

.over__summary dd {
  margin: 0;
  font-size: var(--step-2);
}

.over__verdict {
  font-family: var(--display);
  font-size: var(--step-2);
}
</style>
