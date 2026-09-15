<script setup lang="ts">
import { computed, ref } from 'vue'
import StartScreen from '@/components/StartScreen.vue'
import GameScreen from '@/components/GameScreen.vue'
import GameOverScreen from '@/components/GameOverScreen.vue'
import ErrorNotice from '@/components/ErrorNotice.vue'
import { useRun } from '@/composables/useRun'
import { useRunLocation } from '@/composables/useRunLocation'
import { useTurnStream } from '@/composables/useTurnStream'

/**
 * The only stateful component. It picks a screen from the store's phase and passes data down;
 * everything below it takes props and emits events.
 *
 * No router, but the run id lives in the URL hash. I originally argued a URL for "halfway through a
 * game" would be a lie because the run was not restorable from a path. That was wrong: the server
 * holds the run and replays it on request, so the id is exactly what a URL should carry. What is
 * still true is that one hash pattern does not need a routing library; useRunLocation is the whole
 * of it. A second addressable page would be the moment to add one.
 */
const { store, screen, begin, retry, abandon } = useRun()
const { connected } = useTurnStream()
useRunLocation()

const retrying = ref(false)

async function onRetry() {
  retrying.value = true
  try {
    await retry()
  } finally {
    retrying.value = false
  }
}

const showError = computed(() => store.error !== null)
</script>

<template>
  <div class="shell">
    <div v-if="showError && store.error" class="shell__error">
      <ErrorNotice
        :message="store.error.message"
        :retryable="store.error.retryable"
        :retrying="retrying"
        @retry="onRetry"
        @dismiss="store.clearError()"
      />
    </div>

    <StartScreen
      v-if="screen === 'start' || screen === 'starting'"
      :starting="screen === 'starting'"
      @start="begin"
    />

    <GameScreen
      v-else-if="screen === 'game' && store.run"
      :run="store.run"
      :feed="store.feed"
      :last-event="store.lastEvent"
      :pending-ad-id="store.pendingAdId"
      :pending-item-id="store.pendingItemId"
      :can-act="store.canAct"
      :busy="store.isBusy"
      :stream-connected="connected"
      @solve="store.solve"
      @buy="store.buy"
      @wait="store.waitOutTurn"
      @leave="abandon"
    />

    <GameOverScreen
      v-else-if="screen === 'over' && store.run"
      :run="store.run"
      :feed="store.feed"
      @restart="abandon"
    />
  </div>
</template>

<style scoped>
.shell {
  min-height: 100vh;
}

.shell__error {
  position: sticky;
  top: 0;
  z-index: 10;
  padding: var(--gap-2);
  max-width: var(--board-max);
  margin: 0 auto;
}
</style>
