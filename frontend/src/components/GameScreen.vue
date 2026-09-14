<script setup lang="ts">
import { computed } from 'vue'
import StatusBar from './StatusBar.vue'
import AdBoard from './AdBoard.vue'
import ShopPanel from './ShopPanel.vue'
import WaitTurnPanel from './WaitTurnPanel.vue'
import TurnFeed from './TurnFeed.vue'
import TurnResult from './TurnResult.vue'
import type { RunView, TurnEvent } from '@/api/types'

const props = defineProps<{
  run: RunView
  feed: TurnEvent[]
  lastEvent: TurnEvent | null
  pendingAdId: string | null
  pendingItemId: string | null
  canAct: boolean
  busy: boolean
  streamConnected: boolean
}>()

const emit = defineEmits<{ solve: [adId: string]; buy: [itemId: string]; wait: [] }>()

const isAuto = computed(() => props.run.mode === 'AUTO')

/**
 * The strategy has refused every notice on the board, which is exactly when it waits. Derived here
 * rather than sent as its own field - the board already carries the per-ad verdict.
 */
const nothingWorthAttempting = computed(
  () => props.run.ads.length > 0 && props.run.ads.every((ad) => ad.skippedByStrategy),
)
</script>

<template>
  <div class="game">
    <StatusBar
      :state="run.state"
      :delta="lastEvent?.delta ?? null"
      :strategy="run.strategy"
      :mode="run.mode"
    />

    <main class="game__body">
      <div class="game__main">
        <TurnResult v-if="!isAuto && lastEvent && lastEvent.action !== 'STARTED'" :event="lastEvent" />

        <AdBoard
          :ads="run.ads"
          :actionable="!isAuto"
          :pending-ad-id="pendingAdId"
          :busy="busy || !canAct"
          @solve="emit('solve', $event)"
        />

        <WaitTurnPanel
          v-if="!isAuto"
          :recommended="nothingWorthAttempting"
          :pending="busy && !pendingAdId && !pendingItemId"
          :disabled="busy || !canAct"
          @wait="emit('wait')"
        />
      </div>

      <aside class="game__aside">
        <TurnFeed v-if="isAuto" :events="feed" :connected="streamConnected" />

        <ShopPanel
          :items="run.shop"
          :advice="run.shopAdvice"
          :gold="run.state.gold"
          :actionable="!isAuto"
          :pending-item-id="pendingItemId"
          :busy="busy || !canAct"
          @buy="emit('buy', $event)"
        />
      </aside>
    </main>
  </div>
</template>

<style scoped>
.game {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}

.game__body {
  width: 100%;
  max-width: var(--board-max);
  margin: 0 auto;
  padding: var(--gap-3);
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(0, 1fr);
  gap: var(--gap-4);
  align-items: start;
}

.game__main,
.game__aside {
  display: flex;
  flex-direction: column;
  gap: var(--gap-3);
  min-width: 0;
}

@media (max-width: 52rem) {
  .game__body {
    grid-template-columns: 1fr;
    gap: var(--gap-3);
  }
}

@media (max-width: 24rem) {
  .game__body {
    padding: var(--gap-2);
  }
}
</style>
