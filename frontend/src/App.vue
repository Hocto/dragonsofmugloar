<script setup lang="ts">
import { computed, ref } from 'vue'
import StartScreen from '@/components/StartScreen.vue'
import GameScreen from '@/components/GameScreen.vue'
import GameOverScreen from '@/components/GameOverScreen.vue'
import ErrorNotice from '@/components/ErrorNotice.vue'
import { useRun } from '@/composables/useRun'
import { useTurnStream } from '@/composables/useTurnStream'

/**
 * The only stateful component. It picks a screen from the store's phase and passes data down;
 * everything below it takes props and emits events.
 *
 * No router. The three screens are phases of one run rather than places you can navigate to or
 * link into, and a URL for "halfway through a game" would be a lie - the run lives on the server
 * and is not restorable from a path. If a second genuinely addressable page ever appears, that is
 * the moment to add one.
 */
const { store, screen, begin, retry, abandon } = useRun()
const { connected } = useTurnStream()

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
