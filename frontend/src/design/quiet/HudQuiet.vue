<script setup lang="ts">
import type { PlayerState } from '../types'

/**
 * One line, label and figure inline, tabular figures so the numbers do not jitter as they change.
 * Lives lead the line and carry the only weight.
 */
defineProps<{ player: PlayerState }>()
</script>

<template>
  <header class="hud" aria-label="Player state">
    <dl class="hud__row">
      <div class="hud__stat hud__stat--lives">
        <dt>Lives</dt>
        <dd class="u-num">{{ player.lives }}</dd>
      </div>
      <div class="hud__stat"><dt>Gold</dt><dd class="u-num">{{ player.gold }}</dd></div>
      <div class="hud__stat"><dt>Score</dt><dd class="u-num">{{ player.score }}</dd></div>
      <div class="hud__stat"><dt>Power</dt><dd class="u-num">{{ player.level }}</dd></div>
      <div class="hud__stat"><dt>Turn</dt><dd class="u-num">{{ player.turn }}</dd></div>
    </dl>
  </header>
</template>

<style scoped>
.hud {
  border-bottom: 1px solid var(--edge);
  padding-bottom: 0.6rem;
}

.hud__row {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 0.35rem 1.5rem;
  margin: 0;
  font-family: var(--sans);
}

.hud__stat {
  display: flex;
  align-items: baseline;
  gap: 0.4rem;
}

.hud__stat dt {
  font-size: 0.75rem;
  color: var(--ink-muted);
}

.hud__stat dd {
  margin: 0;
  font-size: 0.9375rem;
  font-weight: 600;
}

.hud__stat--lives dt {
  color: var(--ink);
}

.hud__stat--lives dd {
  font-size: 1.25rem;
  font-weight: 700;
}

/* One line of chrome at every width; tighten rather than wrap. */
@media (max-width: 30rem) {
  .hud__row {
    flex-wrap: nowrap;
    gap: 0 0.8rem;
  }

  .hud__stat {
    gap: 0.25rem;
  }

  .hud__stat dt {
    font-size: 0.625rem;
  }

  .hud__stat dd {
    font-size: 0.8125rem;
  }

  .hud__stat--lives dd {
    font-size: 1.0625rem;
  }
}
</style>
