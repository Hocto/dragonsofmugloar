<script setup lang="ts">
import { computed } from 'vue'
import SplitBar from '../SplitBar.vue'
import { bySelectionScore, highRewardThreshold, powerVerdict, type Quest } from '../types'

/**
 * Reward drives the composition. A high-reward quest takes a full amber block with the figure set
 * very large; an ordinary one collapses to a compact strip. The list is deliberately not a uniform
 * grid - the shape of the board is itself the answer to "where is the money".
 */
const props = defineProps<{ quests: Quest[]; level: number }>()
const emit = defineEmits<{ attempt: [questId: string] }>()

const ordered = computed(() => bySelectionScore(props.quests))
const threshold = computed(() => highRewardThreshold(props.quests))
const boardMax = computed(() => Math.max(0, ...props.quests.map((q) => q.reward)))

const rows = computed(() =>
  ordered.value.map((quest) => ({
    quest,
    rich: quest.reward >= threshold.value,
    verdict: powerVerdict(quest, props.level),
    lastTurn: quest.turnsRemaining <= 1,
  })),
)
</script>

<template>
  <section class="board" aria-labelledby="board-title-bold">
    <h2 id="board-title-bold" class="board__title">Quests</h2>
    <p class="board__note">
      Biggest blocks pay most. Best first. Faded ones the dragon would pass over.
    </p>

    <ul class="board__list">
      <li
        v-for="row in rows"
        :key="row.quest.id"
        class="block"
        :class="{
          'block--rich': row.rich,
          'block--skip': row.quest.wouldSkip,
          'block--last': row.lastTurn,
        }"
      >
        <p class="block__reward u-num">
          {{ row.quest.reward }}<span class="block__unit">g</span>
        </p>

        <div class="block__body">
          <h3 class="block__text">{{ row.quest.text }}</h3>

          <p class="block__meta">
            <span class="block__difficulty">{{ row.quest.difficulty }}</span>
            <span class="u-num block__rank">
              {{ row.quest.difficultyRank }}/{{ row.quest.difficultyOf }}
            </span>
            <span
              class="block__power"
              :class="row.verdict.covered ? 'block__power--ok' : 'block__power--gap'"
            >{{ row.verdict.label }}</span>
          </p>

          <p v-if="row.quest.wouldSkip" class="block__skip">The dragon would pass on this one</p>

          <SplitBar
            class="block__bar"
            :reward="row.quest.reward"
            :expected-share="row.quest.expectedShare"
            :board-max-reward="boardMax"
          />
        </div>

        <div class="block__foot">
          <p class="block__expiry" :class="{ 'block__expiry--last': row.lastTurn }">
            <template v-if="row.lastTurn">LAST TURN</template>
            <template v-else>{{ row.quest.turnsRemaining }} turns left</template>
          </p>
          <button
            type="button"
            class="block__action"
            :aria-label="`Take the quest: ${row.quest.text}. Reward ${row.quest.reward} gold, difficulty ${row.quest.difficulty}, ${row.verdict.label}.`"
            @click="emit('attempt', row.quest.id)"
          >
            Take it
          </button>
        </div>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.board__title {
  font-family: var(--serif);
  font-size: 1.75rem;
  font-weight: 600;
  margin: 0;
}

.board__note {
  margin: 0.25rem 0 1.25rem;
  font-family: var(--sans);
  font-size: 0.8125rem;
  color: var(--ink-muted);
}

.board__list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.block {
  display: grid;
  grid-template-columns: minmax(5.5rem, auto) 1fr;
  grid-template-areas:
    'reward body'
    'foot   foot';
  gap: 0.5rem 1.25rem;
  padding: 0.9rem 1rem;
  border: 1px solid var(--edge);
  align-items: start;
}

/* The block is the encoding: big amber field, big figure. */
.block--rich {
  background: var(--amber);
  border-color: var(--amber-strong);
  padding: 1.4rem 1.25rem;
  gap: 0.75rem 1.5rem;
}

.block--skip {
  opacity: 0.55;
}

.block--last {
  border-left: 6px solid var(--urgent);
}

.block__reward {
  grid-area: reward;
  margin: 0;
  font-size: 1.75rem;
  font-weight: 700;
  line-height: 0.95;
  letter-spacing: -0.02em;
}

.block--rich .block__reward {
  font-size: clamp(2.5rem, 9vw, 3.5rem);
}

.block__unit {
  font-size: 0.42em;
  font-weight: 400;
  margin-left: 0.08em;
}

.block__body {
  grid-area: body;
  min-width: 0;
}

.block__text {
  font-family: var(--serif);
  font-size: 1.0625rem;
  font-weight: 400;
  line-height: 1.4;
  margin: 0 0 0.45rem;
}

.block--rich .block__text {
  font-size: 1.1875rem;
}

.block__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 0.3rem 0.9rem;
  margin: 0 0 0.5rem;
  font-family: var(--sans);
  font-size: 0.75rem;
}

.block__difficulty {
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

.block__rank {
  color: var(--ink-muted);
}

.block__power--ok {
  color: var(--covered);
  font-weight: 600;
}

.block__power--gap {
  color: var(--gap);
  font-weight: 600;
}

.block__skip {
  margin: 0 0 0.5rem;
  font-family: var(--sans);
  font-size: 0.75rem;
  font-style: italic;
  color: var(--ink-muted);
}

.block__bar {
  max-width: 24rem;
  --bar-height: 6px;
}

.block__foot {
  grid-area: foot;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  flex-wrap: wrap;
  margin-top: 0.25rem;
}

.block__expiry {
  margin: 0;
  font-family: var(--sans);
  font-size: 0.75rem;
  color: var(--ink-muted);
}

.block__expiry--last {
  color: var(--urgent);
  font-weight: 700;
  letter-spacing: 0.1em;
}

.block__action {
  padding: 0.5rem 1.5rem;
  background: var(--ink);
  color: var(--paper);
  border: 1px solid var(--ink);
  border-radius: 0;
  font-family: var(--sans);
  font-size: 0.875rem;
  font-weight: 600;
}

.block__action:hover {
  background: transparent;
  color: var(--ink);
}

@media (max-width: 30rem) {
  .block {
    grid-template-columns: 1fr;
    grid-template-areas:
      'reward'
      'body'
      'foot';
  }

  .block--rich .block__reward {
    font-size: 2.75rem;
  }
}
</style>
