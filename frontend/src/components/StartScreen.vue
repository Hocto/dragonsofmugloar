<script setup lang="ts">
import type { RunMode } from '@/api/types'
import DragonOrnament from './DragonOrnament.vue'

defineProps<{ starting: boolean }>()
const emit = defineEmits<{ start: [mode: RunMode] }>()
</script>

<template>
  <main class="intro">
    <div class="intro__panel">
    <!-- Decorative; the text carries everything, so it is hidden from assistive tech. -->
    <div class="intro__art" aria-hidden="true"><DragonOrnament /></div>
    <h1 class="intro__title">Dragons of Mugloar</h1>
    <p class="intro__lede">
      The kingdom pins its problems to a board and pays whoever solves them. Some notices are
      straightforward. Some will get you killed. All of them expire.
    </p>
    <p class="intro__note">
      Every quest that fails costs a life, and the run ends at zero. Gold buys healing and dragon
      upgrades, and a higher level makes the dangerous notices worth attempting.
    </p>

    <div class="intro__modes">
      <button
        type="button"
        class="seal seal--primary intro__mode"
        :disabled="starting"
        @click="emit('start', 'AUTO')"
      >
        <span class="intro__mode-title">Watch the strategy play</span>
        <span class="intro__mode-sub">
          The bot picks, and the chronicle explains every choice as it goes.
        </span>
      </button>

      <button
        type="button"
        class="seal intro__mode"
        :disabled="starting"
        @click="emit('start', 'MANUAL')"
      >
        <span class="intro__mode-title">Take the quests yourself</span>
        <span class="intro__mode-sub">
          Same board, same shop. The bot's opinion is shown but not enforced.
        </span>
      </button>
    </div>

    <p v-if="starting" class="intro__starting" role="status">Saddling up...</p>
    </div>
  </main>
</template>

<style scoped>
.intro {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding: clamp(var(--gap-4), 7vh, 4.5rem) var(--gap-3) var(--gap-4);
}

/*
 * A dragon in ink wash, low in the left of the viewport. Fixed so it stays put when the panel
 * scrolls on short screens, and faint enough behind the text that both schemes keep their contrast.
 */
.intro__art {
  position: fixed;
  left: 2vw;
  bottom: 1vh;
  width: min(64vw, 32rem);
  opacity: 0.12;
  z-index: -1;
  pointer-events: none;
}

/* Wide screens: the panel keeps to the right so the dragon on the left stays in view. */
@media (min-width: 64rem) {
  .intro {
    align-items: center;
    justify-content: flex-end;
    padding-right: clamp(var(--gap-4), 8vw, 8rem);
  }

  .intro__art {
    left: 1vw;
    bottom: 12vh;
    width: min(58vw, 56rem);
    opacity: 0.18;
  }
}

/* The panel is a sheet laid over the page, translucent enough for the drawing to show at its edges. */
.intro__panel {
  width: 100%;
  max-width: 38rem;
  display: flex;
  flex-direction: column;
  gap: var(--gap-3);
  padding: var(--gap-4) var(--gap-4) var(--gap-4);
  background: rgba(247, 240, 224, 0.86);
  border: 1px solid var(--paper-edge);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
}

@media (prefers-color-scheme: dark) {
  .intro__panel {
    background: rgba(34, 28, 22, 0.88);
  }
}

@media (max-width: 30rem) {
  .intro {
    padding: var(--gap-3) var(--gap-2);
    align-items: flex-start;
  }

  .intro__panel {
    padding: var(--gap-3);
  }

  /* No room beside the panel, so the dragon becomes a small emblem above the title instead. */
  .intro__art {
    position: static;
    width: 62%;
    margin: 0 auto calc(-1 * var(--gap-2));
    opacity: 0.55;
  }
}

.intro__title {
  font-size: var(--step-4);
  text-align: center;
  margin-bottom: var(--gap-2);
}

.intro__title::after {
  content: '';
  display: block;
  width: 5rem;
  height: 1px;
  margin: var(--gap-2) auto 0;
  background: var(--rule-strong);
}

.intro__lede {
  font-family: var(--display);
  font-size: var(--step-2);
  line-height: 1.4;
}

.intro__note {
  color: var(--ink-soft);
}

.intro__modes {
  display: grid;
  gap: var(--gap-2);
  margin-top: var(--gap-2);
}

.intro__mode {
  flex-direction: column;
  align-items: flex-start;
  gap: var(--gap-1);
  padding: var(--gap-3);
  text-align: left;
  min-height: 0;
}

.intro__mode-title {
  font-size: var(--step-2);
}

.intro__mode-sub {
  font-family: var(--body);
  font-size: 0.8125rem;
  opacity: 0.85;
  line-height: 1.4;
}

.intro__starting {
  text-align: center;
  font-style: italic;
  color: var(--ink-faint);
}
</style>
