<script setup lang="ts">
import { computed } from 'vue'
import SplitBar from '../SplitBar.vue'
import { bySelectionScore, highRewardThreshold, powerVerdict, type Quest } from '../types'

/**
 * A reading interface. Rows are tight, figures are aligned in their own column, and colour is used
 * as sparingly as the brief allows: the amber ground on a high-reward row, the two segments of the
 * bar, and nothing else. Everything a colour says is written next to it.
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
  <section class="board" aria-labelledby="board-title-quiet">
    <div class="board__head">
      <h2 id="board-title-quiet" class="board__title">Quests</h2>
      <p class="board__note">Best first. Dimmed rows are ones the dragon would pass over.</p>
    </div>

    <ul class="board__list">
      <li
        v-for="row in rows"
        :key="row.quest.id"
        class="row"
        :class="{
          'row--rich': row.rich,
          'row--skip': row.quest.wouldSkip,
          'row--last': row.lastTurn,
        }"
      >
        <div class="row__main">
          <h3 class="row__text">{{ row.quest.text }}</h3>
          <p class="row__meta">
            <span class="row__difficulty">{{ row.quest.difficulty }}</span>
            <span class="row__sep" aria-hidden="true">·</span>
            <span class="u-num">rank {{ row.quest.difficultyRank }}/{{ row.quest.difficultyOf }}</span>
            <span class="row__sep" aria-hidden="true">·</span>
            <span :class="row.verdict.covered ? 'row__power--ok' : 'row__power--gap'">
              {{ row.verdict.label }}
            </span>
            <template v-if="row.quest.wouldSkip">
              <span class="row__sep" aria-hidden="true">·</span>
              <span class="row__skip">would pass</span>
            </template>
          </p>
          <SplitBar
            class="row__bar"
            :reward="row.quest.reward"
            :expected-share="row.quest.expectedShare"
            :board-max-reward="boardMax"
          />
        </div>

        <div class="row__figures">
          <p class="row__reward u-num">{{ row.quest.reward }}<span class="row__unit">g</span></p>
          <p class="row__expiry u-num" :class="{ 'row__expiry--last': row.lastTurn }">
            <template v-if="row.lastTurn">last turn</template>
            <template v-else>{{ row.quest.turnsRemaining }} turns</template>
          </p>
          <button
            type="button"
            class="row__action"
            :aria-label="`Take the quest: ${row.quest.text}. Reward ${row.quest.reward} gold, difficulty ${row.quest.difficulty}, ${row.verdict.label}.`"
            @click="emit('attempt', row.quest.id)"
          >
            Take
          </button>
        </div>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.board__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 0.5rem 1rem;
  margin-bottom: 0.9rem;
}

.board__title {
  font-family: var(--serif);
  font-size: 1.375rem;
  font-weight: 600;
  margin: 0;
}

.board__note {
  margin: 0;
  font-family: var(--sans);
  font-size: 0.75rem;
  color: var(--ink-muted);
}

.board__list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.row {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 0.5rem 1.25rem;
  padding: 0.85rem 0.75rem;
  border-bottom: 1px solid var(--rule);
  align-items: start;
}

.row--rich {
  background: var(--amber);
}

.row--skip {
  opacity: 0.55;
}

.row--last {
  box-shadow: inset 3px 0 0 0 var(--urgent);
}

.row__main {
  min-width: 0;
}

.row__text {
  font-family: var(--serif);
  font-size: 1rem;
  font-weight: 400;
  line-height: 1.4;
  margin: 0 0 0.3rem;
}

.row__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 0 0.4rem;
  margin: 0 0 0.55rem;
  font-family: var(--sans);
  font-size: 0.75rem;
  color: var(--ink-muted);
}

.row__difficulty {
  color: var(--ink);
  font-weight: 600;
}

.row__sep {
  color: var(--edge);
}

.row__power--ok {
  color: var(--covered);
}

.row__power--gap {
  color: var(--gap);
}

.row__skip {
  font-style: italic;
}

.row__bar {
  /* Set off from the meta line above, which it otherwise reads as an underline of. */
  margin-top: 0.15rem;
  max-width: 22rem;
  --bar-height: 3px;
}

.row__figures {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 0.2rem;
  text-align: right;
}

.row__reward {
  margin: 0;
  font-size: 1.125rem;
  font-weight: 700;
  line-height: 1;
}

.row__unit {
  font-size: 0.6em;
  font-weight: 400;
  color: var(--ink-muted);
}

.row__expiry {
  margin: 0;
  font-size: 0.6875rem;
  color: var(--ink-muted);
}

.row__expiry--last {
  color: var(--urgent);
  font-weight: 700;
}

.row__action {
  margin-top: 0.2rem;
  min-width: 4.25rem;
  padding: 0.4rem 0.9rem;
  background: transparent;
  border: 1px solid var(--edge);
  border-radius: 0;
  font-family: var(--sans);
  font-size: 0.8125rem;
}

.row__action:hover {
  border-color: var(--ink);
}

@media (max-width: 30rem) {
  .row {
    grid-template-columns: 1fr;
  }

  .row__figures {
    flex-direction: row;
    align-items: center;
    justify-content: flex-start;
    gap: 0.9rem;
    text-align: left;
  }

  .row__action {
    margin-top: 0;
    margin-left: auto;
  }
}
</style>
