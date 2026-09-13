<script setup lang="ts">
import type { PlayerState } from '../types'

/**
 * Lives get a block of their own at display size; everything else is a quiet strip beside it. Still
 * one line of chrome, but the weight is unmistakably on the stat you can lose.
 */
defineProps<{ player: PlayerState }>()
</script>

<template>
  <header class="hud" aria-label="Player state">
    <div class="hud__lives">
      <p class="hud__lives-label">Lives</p>
      <p class="hud__lives-value u-num">{{ player.lives }}</p>
    </div>

    <dl class="hud__rest">
      <div class="hud__stat"><dt>Gold</dt><dd class="u-num">{{ player.gold }}</dd></div>
      <div class="hud__stat"><dt>Score</dt><dd class="u-num">{{ player.score }}</dd></div>
      <div class="hud__stat"><dt>Power</dt><dd class="u-num">{{ player.level }}</dd></div>
      <div class="hud__stat"><dt>Turn</dt><dd class="u-num">{{ player.turn }}</dd></div>
    </dl>
  </header>
</template>

<style scoped>
.hud {
  display: flex;
  align-items: stretch;
  gap: 1.25rem;
  border: 2px solid var(--ink);
}

.hud__lives {
  display: flex;
  align-items: baseline;
  gap: 0.6rem;
  padding: 0.5rem 1rem;
  background: var(--ink);
  color: var(--paper);
  flex: none;
}

.hud__lives-label {
  margin: 0;
  font-family: var(--sans);
  font-size: 0.6875rem;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.hud__lives-value {
  margin: 0;
  font-size: 1.75rem;
  font-weight: 700;
  line-height: 1;
}

.hud__rest {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.35rem 1.75rem;
  margin: 0;
  padding: 0.5rem 1rem 0.5rem 0;
  font-family: var(--sans);
}

.hud__stat {
  display: flex;
  align-items: baseline;
  gap: 0.4rem;
}

.hud__stat dt {
  font-size: 0.6875rem;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--ink-muted);
}

.hud__stat dd {
  margin: 0;
  font-size: 1.0625rem;
  font-weight: 700;
}

/* One line of chrome at every width; tighten rather than wrap. */
@media (max-width: 30rem) {
  .hud {
    gap: 0.5rem;
  }

  .hud__lives {
    padding: 0.35rem 0.45rem;
    gap: 0.3rem;
  }

  .hud__lives-label {
    font-size: 0.5rem;
    letter-spacing: 0.04em;
  }

  .hud__lives-value {
    font-size: 1.125rem;
  }

  .hud__rest {
    flex-wrap: nowrap;
    /* Without this the nowrap row refuses to shrink below its intrinsic width and pushes the
       whole bar 3px past a 360px viewport. */
    min-width: 0;
    gap: 0 0.5rem;
    padding: 0.35rem 0.4rem 0.35rem 0;
  }

  .hud__stat {
    gap: 0.2rem;
  }

  .hud__stat dt {
    font-size: 0.5rem;
    letter-spacing: 0.02em;
  }

  .hud__stat dd {
    font-size: 0.8125rem;
  }
}
</style>
