<script setup lang="ts">
import { computed } from 'vue'
import SplitBar from '../SplitBar.vue'
import { bySelectionScore, highRewardThreshold, powerVerdict, type Quest } from '../types'

/**
 * Quests set as entries in a broadsheet column: no boxes, hairline rules between them, a ranking
 * numeral in the gutter, and generous leading. High-reward entries take an amber ground that runs
 * the full width of the column, so value registers before the number is read.
 */
const props = defineProps<{ quests: Quest[]; level: number }>()
const emit = defineEmits<{ attempt: [questId: string] }>()

const ordered = computed(() => bySelectionScore(props.quests))
const threshold = computed(() => highRewardThreshold(props.quests))
const boardMax = computed(() => Math.max(0, ...props.quests.map((q) => q.reward)))

const rows = computed(() =>
  ordered.value.map((quest, index) => ({
    quest,
    rank: index + 1,
    rich: quest.reward >= threshold.value,
    verdict: powerVerdict(quest, props.level),
    lastTurn: quest.turnsRemaining <= 1,
  })),
)
</script>

<template>
  <section class="board" aria-labelledby="board-title-editorial">
    <h2 id="board-title-editorial" class="board__title">The Quest Board</h2>
    <p class="board__standfirst">
      Ordered as the dragon would take them, best first. Faded entries are ones it would pass over.
    </p>

    <ol class="board__list">
      <li
        v-for="row in rows"
        :key="row.quest.id"
        class="entry"
        :class="{
          'entry--rich': row.rich,
          'entry--skip': row.quest.wouldSkip,
          'entry--last': row.lastTurn,
        }"
      >
        <p class="entry__rank u-num" aria-hidden="true">{{ row.rank }}</p>

        <div class="entry__body">
          <p class="entry__head">
            <span class="entry__reward u-num">{{ row.quest.reward }}<span class="entry__unit">g</span></span>
            <span class="entry__expiry" :class="{ 'entry__expiry--last': row.lastTurn }">
              <template v-if="row.lastTurn">Last turn to take it</template>
              <template v-else>{{ row.quest.turnsRemaining }} turns left</template>
            </span>
          </p>

          <h3 class="entry__text">{{ row.quest.text }}</h3>

          <p class="entry__meta">
            <span class="entry__difficulty">
              {{ row.quest.difficulty }}
              <span class="entry__rank-of u-num">
                ({{ row.quest.difficultyRank }} of {{ row.quest.difficultyOf }})
              </span>
            </span>
            <span
              class="entry__power"
              :class="row.verdict.covered ? 'entry__power--ok' : 'entry__power--gap'"
            >{{ row.verdict.label }}</span>
            <span v-if="row.quest.wouldSkip" class="entry__skip">The dragon would pass</span>
          </p>

          <SplitBar
            class="entry__bar"
            :reward="row.quest.reward"
            :expected-share="row.quest.expectedShare"
            :board-max-reward="boardMax"
          />

          <button
            type="button"
            class="entry__action"
            :aria-label="`Take the quest: ${row.quest.text}. Reward ${row.quest.reward} gold, difficulty ${row.quest.difficulty}, ${row.verdict.label}.`"
            @click="emit('attempt', row.quest.id)"
          >
            Take it
          </button>
        </div>
      </li>
    </ol>
  </section>
</template>

<style scoped>
.board__title {
  font-family: var(--serif);
  font-size: clamp(1.75rem, 5vw, 2.5rem);
  font-weight: 600;
  margin: 0;
}

.board__standfirst {
  font-family: var(--serif);
  font-style: italic;
  color: var(--ink-muted);
  margin: 0.4rem 0 1.5rem;
  max-width: 34rem;
}

.board__list {
  list-style: none;
  margin: 0;
  padding: 0;
  border-top: 1px solid var(--ink);
}

.entry {
  display: grid;
  grid-template-columns: 2.5rem 1fr;
  gap: 0 1rem;
  padding: 1.5rem 1rem 1.5rem 0;
  border-bottom: 1px solid var(--rule);
}

/* The fill runs the width of the column, which is what makes value read at a glance. */
.entry--rich {
  background: var(--amber);
  padding-left: 1rem;
}

.entry--skip {
  opacity: 0.55;
}

/* One turn left: a solid bar down the edge, no animation. */
.entry--last {
  box-shadow: inset 4px 0 0 0 var(--urgent);
  padding-left: 1rem;
}

.entry__rank {
  margin: 0;
  font-family: var(--serif);
  font-size: 1.5rem;
  color: var(--ink-muted);
  text-align: right;
  line-height: 1.2;
}

.entry__body {
  min-width: 0;
}

.entry__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 1rem;
  margin: 0;
}

.entry__reward {
  font-family: var(--serif);
  font-size: 1.75rem;
  line-height: 1;
}

.entry__unit {
  font-size: 0.5em;
  margin-left: 0.1em;
}

.entry__expiry {
  font-family: var(--sans);
  font-size: 0.75rem;
  color: var(--ink-muted);
  text-align: right;
}

.entry__expiry--last {
  color: var(--urgent);
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.entry__text {
  font-family: var(--serif);
  font-size: 1.1875rem;
  font-weight: 400;
  line-height: 1.45;
  margin: 0.5rem 0 0.6rem;
  max-width: 38rem;
}

.entry__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 0.25rem 1.25rem;
  margin: 0 0 0.9rem;
  font-family: var(--sans);
  font-size: 0.75rem;
}

.entry__difficulty {
  font-weight: 600;
}

.entry__rank-of {
  color: var(--ink-muted);
  font-weight: 400;
}

.entry__power--ok {
  color: var(--covered);
}

.entry__power--gap {
  color: var(--gap);
}

.entry__skip {
  color: var(--ink-muted);
  font-style: italic;
}

.entry__bar {
  max-width: 26rem;
  --bar-height: 3px;
}

.entry__action {
  margin-top: 1rem;
  padding: 0.55rem 1.4rem;
  background: transparent;
  border: 1px solid var(--ink);
  border-radius: 0;
  font-family: var(--serif);
  font-size: 1rem;
}

.entry__action:hover {
  background: var(--ink);
  color: var(--paper);
}

@media (max-width: 30rem) {
  .entry {
    grid-template-columns: 1.75rem 1fr;
    padding: 1.15rem 0.75rem 1.15rem 0;
  }

  .entry__rank {
    font-size: 1.125rem;
  }

  .entry__reward {
    font-size: 1.5rem;
  }

  .entry__text {
    font-size: 1.0625rem;
  }
}
</style>
