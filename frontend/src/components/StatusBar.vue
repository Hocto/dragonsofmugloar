<script setup lang="ts">
import type { GameState, StateDelta } from '@/api/types'

/**
 * The HUD. It stays put while everything else changes, and it shows the delta from the last turn
 * next to each number so a change is legible without watching for it.
 */
defineProps<{
  state: GameState
  delta: StateDelta | null
  strategy: string
  mode: string
}>()

const emit = defineEmits<{ leave: [] }>()

function sign(value: number): string {
  return value > 0 ? `+${value}` : `${value}`
}
</script>

<template>
  <header class="hud">
    <dl class="hud__stats">
      <div class="hud__stat">
        <dt>Lives</dt>
        <dd class="numeral" :class="{ 'hud__value--danger': state.lives <= 1 }">
          {{ state.lives }}
          <span v-if="delta?.lives" class="hud__delta" :class="delta.lives > 0 ? 'up' : 'down'">
            {{ sign(delta.lives) }}
          </span>
        </dd>
      </div>
      <div class="hud__stat">
        <dt>Gold</dt>
        <dd class="numeral">
          {{ state.gold }}
          <span v-if="delta?.gold" class="hud__delta" :class="delta.gold > 0 ? 'up' : 'down'">
            {{ sign(delta.gold) }}
          </span>
        </dd>
      </div>
      <div class="hud__stat hud__stat--wide">
        <dt>Score</dt>
        <dd class="numeral hud__value--score">
          {{ state.score }}
          <span v-if="delta?.score" class="hud__delta up">{{ sign(delta.score) }}</span>
        </dd>
      </div>
      <div class="hud__stat">
        <dt>Level</dt>
        <dd class="numeral">
          {{ state.level }}
          <span v-if="delta?.level" class="hud__delta up">{{ sign(delta.level) }}</span>
        </dd>
      </div>
      <div class="hud__stat">
        <dt>Turn</dt>
        <dd class="numeral">{{ state.turn }}</dd>
      </div>
    </dl>
    <div class="hud__side">
      <p class="hud__mode">
        {{ mode === 'AUTO' ? `watching ${strategy}` : 'playing by hand' }}
      </p>
      <!--
        Always reachable, in both modes. An auto run keeps playing on the server after you leave;
        the label says so rather than letting the button imply it stops anything.
      -->
      <button
        type="button"
        class="hud__leave"
        :aria-label="mode === 'AUTO'
          ? 'Leave this run. The dragon keeps playing on the server; the link in the address bar brings you back.'
          : 'Leave this run and return to the start. The link in the address bar brings you back.'"
        @click="emit('leave')"
      >
        Leave
      </button>
    </div>
  </header>
</template>

<style scoped>
.hud {
  position: sticky;
  top: 0;
  z-index: 5;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--gap-2) var(--gap-3);
  padding: var(--gap-2) var(--gap-3);
  background: var(--paper-sunken);
  border-bottom: 1px solid var(--rule-strong);
  backdrop-filter: blur(2px);
}

.hud__stats {
  display: flex;
  flex-wrap: wrap;
  gap: var(--gap-2) var(--gap-4);
  margin: 0;
}

.hud__stat {
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.hud__stat dt {
  font-size: 0.6875rem;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--ink-faint);
}

.hud__stat dd {
  margin: 0;
  font-size: var(--step-2);
  line-height: 1.1;
  display: flex;
  align-items: baseline;
  gap: 0.35rem;
}

.hud__value--score {
  color: var(--gilt);
}

.hud__value--danger {
  color: var(--wax);
}

.hud__delta {
  font-size: 0.75rem;
  font-weight: 700;
}

.hud__delta.up {
  color: var(--moss);
}

.hud__delta.down {
  color: var(--wax);
}

.hud__side {
  display: flex;
  align-items: center;
  gap: var(--gap-3);
}

.hud__mode {
  font-family: var(--display);
  font-size: 0.8125rem;
  color: var(--ink-faint);
  font-style: italic;
}

.hud__leave {
  min-height: 2.25rem;
  padding: 0.3rem 0.8rem;
  border: 1px solid var(--rule-strong);
  border-radius: var(--radius);
  background: transparent;
  font-family: var(--display);
  font-size: 0.875rem;
  cursor: pointer;
}

.hud__leave:hover {
  background: var(--paper-raised);
}

@media (max-width: 30rem) {
  .hud__stats {
    gap: var(--gap-2) var(--gap-3);
  }

  .hud__stat dd {
    font-size: var(--step-1);
  }
}
</style>
