<script setup lang="ts">
import type { PlayerState } from '../types'

/**
 * A masthead. Small-caps labels sit above their figures on one rule-bounded line, the way a
 * newspaper sets its date and edition.
 */
defineProps<{ player: PlayerState }>()
</script>

<template>
  <header class="hud" aria-label="Player state">
    <dl class="hud__row">
      <div class="hud__stat hud__stat--lives">
        <dt>Lives</dt>
        <dd class="hud__lives">{{ player.lives }}</dd>
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
  border-top: 2px solid var(--ink);
  border-bottom: 1px solid var(--ink);
  padding: 0.5rem 0;
}

.hud__row {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 0.5rem 2.25rem;
  margin: 0;
}

.hud__stat {
  display: flex;
  align-items: baseline;
  gap: 0.5rem;
}

.hud__stat dt {
  font-family: var(--sans);
  font-size: 0.625rem;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--ink-muted);
}

.hud__stat dd {
  margin: 0;
  font-family: var(--serif);
  font-size: 1.25rem;
  line-height: 1;
}

/* Lives are the only thing the player can lose, so they get the display size. */
.hud__stat--lives dt {
  color: var(--ink);
}

.hud__lives {
  font-size: 1.875rem !important;
  font-weight: 600;
}

/*
 * One line of chrome, at every width. Below this the labels and figures shrink and the row stops
 * wrapping rather than becoming two lines - a HUD that reflows is a HUD that moves the numbers
 * around while the player is reading them.
 */
@media (max-width: 30rem) {
  .hud__row {
    flex-wrap: nowrap;
    gap: 0 0.6rem;
  }

  .hud__stat {
    gap: 0.25rem;
  }

  .hud__stat dt {
    font-size: 0.5rem;
    letter-spacing: 0.04em;
  }

  .hud__stat dd {
    font-size: 0.8125rem;
  }

  .hud__lives {
    font-size: 1.0625rem !important;
  }
}
</style>
