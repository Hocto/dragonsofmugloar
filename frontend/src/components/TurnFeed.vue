<script setup lang="ts">
import type { TurnEvent } from '@/api/types'

/**
 * The live turn feed for auto runs.
 *
 * It reads as a chronicle rather than a log table, and every line carries the reasoning the
 * backend sent - what the bot picked, how likely it thought it was, what it cost. Watching a bot
 * play is only interesting if you can see why it played that way.
 */
defineProps<{ events: TurnEvent[]; connected: boolean }>()

function headline(event: TurnEvent): string {
  switch (event.action) {
    case 'STARTED':
      return 'Set out from the dragon den.'
    case 'BOUGHT':
      // The detail line below carries the item name and the reason, so the headline stays short.
      return event.success ? 'Went shopping.' : 'Could not afford it.'
    case 'IDLED':
      return 'Waited out the turn.'
    case 'FINISHED':
      return 'The run is over.'
    case 'FAILED':
      return 'The run broke off.'
    default:
      return event.success ? 'Quest done.' : 'Quest failed.'
  }
}

function deltaParts(event: TurnEvent): string[] {
  const parts: string[] = []
  if (event.delta.gold) parts.push(`${event.delta.gold > 0 ? '+' : ''}${event.delta.gold} gold`)
  if (event.delta.lives) parts.push(`${event.delta.lives > 0 ? '+' : ''}${event.delta.lives} life`)
  if (event.delta.level) parts.push(`+${event.delta.level} level`)
  if (event.delta.score) parts.push(`+${event.delta.score} score`)
  return parts
}
</script>

<template>
  <section class="feed" aria-labelledby="feed-heading">
    <h2 id="feed-heading" class="feed__heading">
      The chronicle
      <span class="feed__status" :class="{ 'feed__status--live': connected }">
        {{ connected ? 'live' : 'reconnecting' }}
      </span>
    </h2>

    <!--
      role="log" rather than an assertive live region. An auto run produces a turn every few
      hundred milliseconds, and announcing each one turns a screen reader into a metronome. The
      region is navigable; the HUD and the result panel are where the important changes are said
      out loud.
    -->
    <ol class="feed__list" role="log" aria-label="Turn by turn chronicle">
      <li
        v-for="event in events"
        :key="event.sequence"
        class="feed__entry"
        :class="{
          'feed__entry--good': event.action === 'SOLVED' && event.success,
          'feed__entry--bad': event.action === 'SOLVED' && !event.success,
        }"
      >
        <span class="feed__turn numeral">t{{ event.state.turn }}</span>
        <div class="feed__body">
          <p class="feed__headline">{{ headline(event) }}</p>
          <p v-if="event.action === 'SOLVED'" class="feed__detail">{{ event.description }}</p>
          <p
            v-else-if="event.action === 'BOUGHT' || event.action === 'IDLED'"
            class="feed__detail"
          >{{ event.description }}</p>
          <p class="feed__meta">
            <span v-if="event.risk" class="numeral">
              {{ event.risk }}<template v-if="event.successChance">
                &middot; {{ Math.round(event.successChance * 100) }}% called</template>
            </span>
            <span v-for="part in deltaParts(event)" :key="part" class="numeral feed__delta">
              {{ part }}
            </span>
          </p>
        </div>
      </li>
    </ol>
  </section>
</template>

<style scoped>
.feed__heading {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--gap-2);
  font-size: var(--step-2);
  margin-bottom: var(--gap-2);
  padding-bottom: var(--gap-1);
  border-bottom: 1px solid var(--rule);
}

.feed__status {
  font-family: var(--body);
  font-size: 0.6875rem;
  text-transform: uppercase;
  letter-spacing: 0.1em;
  color: var(--ink-faint);
}

.feed__status--live {
  color: var(--moss);
}

.feed__list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--gap-2);
  max-height: 30rem;
  overflow-y: auto;
}

.feed__entry {
  display: grid;
  grid-template-columns: 3rem 1fr;
  gap: var(--gap-2);
  padding-bottom: var(--gap-2);
  border-bottom: 1px dotted var(--rule);
}

.feed__turn {
  font-size: 0.75rem;
  color: var(--ink-faint);
  padding-top: 0.2rem;
}

.feed__headline {
  font-family: var(--display);
  font-size: var(--step-1);
}

.feed__entry--good .feed__headline {
  color: var(--moss);
}

.feed__entry--bad .feed__headline {
  color: var(--wax);
}

.feed__detail {
  font-size: 0.8125rem;
  color: var(--ink-soft);
}

.feed__meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--gap-2);
  font-size: 0.75rem;
  color: var(--ink-faint);
  margin-top: 0.15rem;
}

.feed__delta {
  color: var(--ink-soft);
}
</style>
