<script setup lang="ts">
import type { ShopItemView, ShopAdviceView } from '@/api/types'

/** The shop. Affordability is spelled out in words on each row. */
defineProps<{
  items: ShopItemView[]
  advice: ShopAdviceView | null
  gold: number
  actionable: boolean
  pendingItemId: string | null
  busy: boolean
}>()

const emit = defineEmits<{ buy: [itemId: string] }>()
</script>

<template>
  <section class="shop" aria-labelledby="shop-heading">
    <h2 id="shop-heading" class="shop__heading">The shop</h2>

    <p v-if="advice" class="shop__advice">{{ advice.reason }}</p>

    <ul class="shop__list">
      <li
        v-for="item in items"
        :key="item.id"
        class="shop__row"
        :class="{ 'shop__row--broke': !item.affordable, 'shop__row--healing': item.healing }"
      >
        <span class="shop__name">
          {{ item.name }}
          <span v-if="item.recommended" class="shop__pick">the bot would buy this</span>
        </span>
        <span class="shop__cost numeral">{{ item.cost }}g</span>
        <span class="shop__afford">
          <template v-if="item.affordable">affordable</template>
          <template v-else>{{ item.cost - gold }}g short</template>
        </span>
        <button
          v-if="actionable"
          type="button"
          class="seal shop__buy"
          :disabled="!item.affordable || busy || pendingItemId === item.id"
          :aria-label="`Buy ${item.name} for ${item.cost} gold`"
          @click="emit('buy', item.id)"
        >
          {{ pendingItemId === item.id ? 'Paying...' : 'Buy' }}
        </button>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.shop__heading {
  font-size: var(--step-2);
  margin-bottom: var(--gap-2);
  padding-bottom: var(--gap-1);
  border-bottom: 1px solid var(--rule);
}

.shop__advice {
  margin-bottom: var(--gap-2);
  font-style: italic;
  font-size: 0.8125rem;
  color: var(--ink-faint);
}

.shop__list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 1px;
  background: var(--rule);
  border: 1px solid var(--rule);
  border-radius: var(--radius);
  overflow: hidden;
}

.shop__row {
  display: grid;
  grid-template-columns: 1fr auto auto auto;
  align-items: center;
  gap: var(--gap-2);
  padding: var(--gap-2);
  background: var(--paper-raised);
}

.shop__row--broke {
  color: var(--ink-faint);
  background: var(--paper-sunken);
}

.shop__row--healing .shop__cost {
  color: var(--wax);
}

.shop__name {
  font-family: var(--display);
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: var(--gap-1);
}

.shop__pick {
  font-family: var(--body);
  font-size: 0.6875rem;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--moss);
}

.shop__cost {
  color: var(--gilt);
  font-weight: 600;
}

.shop__row--broke .shop__cost {
  color: inherit;
}

.shop__afford {
  font-size: 0.75rem;
  color: var(--ink-faint);
  min-width: 6ch;
  text-align: right;
}

.shop__buy {
  min-height: 2.25rem;
  padding: 0.3rem 0.8rem;
  font-size: var(--step-0);
}

@media (max-width: 34rem) {
  .shop__row {
    grid-template-columns: 1fr auto;
    grid-template-areas:
      'name cost'
      'afford buy';
  }

  .shop__name {
    grid-area: name;
  }
  .shop__cost {
    grid-area: cost;
  }
  .shop__afford {
    grid-area: afford;
    text-align: left;
  }
  .shop__buy {
    grid-area: buy;
  }
}
</style>
