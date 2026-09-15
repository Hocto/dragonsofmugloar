<script setup lang="ts">
import type { RunMode } from '@/api/types'

defineProps<{ starting: boolean }>()
const emit = defineEmits<{ start: [mode: RunMode] }>()
</script>

<template>
  <main class="intro">
    <!-- Decorative; the text carries everything, so it is hidden from assistive tech. -->
    <div class="intro__art" aria-hidden="true" />
    <div class="intro__panel">
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

    <p class="intro__credit">
      Drawing by Nele Sergejeva, © 2018 Bigbank AS, from
      <a href="https://dragonsofmugloar.com" rel="noopener">dragonsofmugloar.com</a>.
    </p>
  </main>
</template>

<style scoped>
.intro {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--gap-4) var(--gap-3);
}

/*
 * The site's own dragon sketch, full bleed. Covering the viewport crops the parchment's torn edge,
 * so it reads as the paper the page is printed on rather than a sheet pinned to it. Positioned so
 * the dragon sits left of centre, where the panel leaves it visible on wide screens.
 */
.intro__art {
  position: fixed;
  inset: 0;
  background: url('/art/dragon-in-barn.webp') no-repeat 20% 60% / cover;
  z-index: -1;
}

/* Wide screens: the panel keeps to the right so the dragon on the left stays in view. */
@media (min-width: 64rem) {
  .intro {
    justify-content: flex-end;
    padding-right: clamp(var(--gap-4), 8vw, 8rem);
  }

  .intro__art {
    background-position: 0% 55%;
  }
}

/* Dark scheme: the parchment stays lit but sits under a warm scrim so the panel can hold cream text. */
@media (prefers-color-scheme: dark) {
  .intro__art::after {
    content: '';
    position: absolute;
    inset: 0;
    background: rgba(23, 19, 15, 0.62);
  }
}

/*
 * The text on a translucent notice over the sketch. Enough paper behind the type to keep contrast
 * wherever the drawing lands, little enough that the drawing shows through at the edges.
 */
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
  backdrop-filter: blur(2px);
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

  .intro__art {
    background-position: 30% 70%;
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

/* The sketch is the site's own artwork; the credit stays on screen wherever the sketch is. */
.intro__credit {
  position: fixed;
  left: var(--gap-3);
  bottom: var(--gap-2);
  margin: 0;
  padding: 0.2rem 0.5rem;
  font-size: 0.6875rem;
  color: var(--ink-soft);
  background: rgba(247, 240, 224, 0.75);
  border-radius: var(--radius);
}

.intro__credit a {
  color: inherit;
}

@media (prefers-color-scheme: dark) {
  .intro__credit {
    background: rgba(34, 28, 22, 0.75);
  }
}
</style>
