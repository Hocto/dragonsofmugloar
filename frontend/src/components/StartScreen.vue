<script setup lang="ts">
import type { RunMode } from '@/api/types'

defineProps<{ starting: boolean }>()
const emit = defineEmits<{ start: [mode: RunMode] }>()
</script>

<template>
  <main class="intro">
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
  </main>
</template>

<style scoped>
.intro {
  max-width: 38rem;
  margin: 0 auto;
  padding: var(--gap-5) var(--gap-3);
  display: flex;
  flex-direction: column;
  gap: var(--gap-3);
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
