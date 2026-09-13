<script setup lang="ts">
import { computed } from 'vue'
import type { GoldBreakdown } from './types'

/**
 * The gold breakdown, and the only part-to-whole chart in the app.
 *
 * It earns a donut because cumulative gold is the one genuinely additive total in the game:
 * everything ever earned is now either an upgrade, a potion, or still in the purse, and those three
 * sum to a meaningful whole. Quests are not a whole, which is why they get bars.
 *
 * Drawn with stroke-dasharray on flat arcs - no gradient, no shadow, no image.
 */
const props = defineProps<{ gold: GoldBreakdown; size?: number }>()

const RADIUS = 42
const CIRCUMFERENCE = 2 * Math.PI * RADIUS

const total = computed(() => props.gold.upgrades + props.gold.healing + props.gold.inHand)

const segments = computed(() => {
  const rows = [
    { key: 'upgrades', label: 'Spent on upgrades', value: props.gold.upgrades, tone: 'upgrades' },
    { key: 'healing', label: 'Spent on healing', value: props.gold.healing, tone: 'healing' },
    { key: 'inHand', label: 'Still in hand', value: props.gold.inHand, tone: 'in-hand' },
  ]
  let offset = 0
  return rows.map((row) => {
    const share = total.value > 0 ? row.value / total.value : 0
    const segment = {
      ...row,
      share,
      percent: Math.round(share * 100),
      dash: share * CIRCUMFERENCE,
      offset: -offset * CIRCUMFERENCE,
    }
    offset += share
    return segment
  })
})
</script>

<template>
  <figure class="donut">
    <figcaption class="donut__title">Where the gold went</figcaption>

    <div class="donut__body">
      <svg
        class="donut__chart"
        :width="size ?? 132"
        :height="size ?? 132"
        viewBox="0 0 100 100"
        role="img"
        :aria-label="`Of ${total} gold earned in total: ${segments
          .map((s) => `${s.value} ${s.label.toLowerCase()}`)
          .join(', ')}.`"
      >
        <circle class="donut__rail" cx="50" cy="50" :r="RADIUS" />
        <circle
          v-for="segment in segments"
          :key="segment.key"
          class="donut__arc"
          :class="`donut__arc--${segment.tone}`"
          cx="50"
          cy="50"
          :r="RADIUS"
          :stroke-dasharray="`${segment.dash} ${CIRCUMFERENCE}`"
          :stroke-dashoffset="segment.offset"
        />
      </svg>

      <!-- The legend, not the chart, is what makes this readable without colour. -->
      <dl class="donut__legend">
        <div v-for="segment in segments" :key="segment.key" class="donut__row">
          <dt>
            <span class="donut__swatch" :class="`donut__swatch--${segment.tone}`" aria-hidden="true" />
            {{ segment.label }}
          </dt>
          <dd class="u-num">{{ segment.value }}g <span class="donut__pct">{{ segment.percent }}%</span></dd>
        </div>
        <div class="donut__row donut__row--total">
          <dt>Earned in total</dt>
          <dd class="u-num">{{ total }}g</dd>
        </div>
      </dl>
    </div>
  </figure>
</template>

<style scoped>
.donut {
  margin: 0;
}

.donut__title {
  font-family: var(--serif);
  font-size: 1.0625rem;
  margin-bottom: 0.75rem;
}

.donut__body {
  display: flex;
  align-items: center;
  gap: 1.25rem;
  flex-wrap: wrap;
}

.donut__chart {
  /* Start the first arc at twelve o'clock instead of three. */
  transform: rotate(-90deg);
  flex: none;
}

.donut__rail,
.donut__arc {
  fill: none;
  stroke-width: 13;
}

.donut__rail {
  stroke: var(--rule);
}

.donut__arc--upgrades {
  stroke: var(--covered);
}
.donut__arc--healing {
  stroke: var(--urgent);
}
.donut__arc--in-hand {
  stroke: var(--gap);
}

.donut__legend {
  margin: 0;
  min-width: 12rem;
  flex: 1 1 12rem;
  font-family: var(--sans);
  font-size: 0.8125rem;
}

.donut__row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 0.75rem;
  padding: 0.3rem 0;
  border-bottom: 1px solid var(--rule);
}

.donut__row--total {
  border-bottom: 0;
  font-weight: 600;
}

.donut__row dt {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  color: var(--ink-muted);
}

.donut__row--total dt {
  color: var(--ink);
}

.donut__row dd {
  margin: 0;
  white-space: nowrap;
}

.donut__pct {
  color: var(--ink-muted);
  margin-left: 0.25rem;
}

.donut__swatch {
  width: 10px;
  height: 10px;
  flex: none;
}

.donut__swatch--upgrades {
  background: var(--covered);
}
.donut__swatch--healing {
  background: var(--urgent);
}
.donut__swatch--in-hand {
  background: var(--gap);
}
</style>
