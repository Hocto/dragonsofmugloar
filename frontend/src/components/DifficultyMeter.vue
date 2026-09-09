<script setup lang="ts">
import { computed } from 'vue'

/**
 * The risk scale, drawn as filled pips.
 *
 * The requirement is that difficulty is readable without colour, so the pips carry the meaning and
 * the label carries it again in words. Colour is the third signal, not the first - remove it
 * entirely and this still reads "4 of 11, Quite likely".
 */
const props = defineProps<{
  label: string
  rank: number
  of: number
}>()

const pips = computed(() => Array.from({ length: props.of }, (_, i) => i < props.rank))
const severity = computed(() => {
  const share = props.rank / props.of
  if (share <= 0.35) return 'easy'
  if (share <= 0.65) return 'middling'
  return 'hard'
})
</script>

<template>
  <p class="meter" :class="`meter--${severity}`">
    <span class="meter__pips" aria-hidden="true">
      <span v-for="(filled, i) in pips" :key="i" class="pip" :class="{ 'pip--on': filled }" />
    </span>
    <span class="meter__label">{{ label }}</span>
    <span class="visually-hidden">, difficulty {{ rank }} of {{ of }}</span>
  </p>
</template>

<style scoped>
.meter {
  display: flex;
  align-items: center;
  gap: var(--gap-2);
  flex-wrap: wrap;
}

.meter__pips {
  display: inline-flex;
  gap: 2px;
}

.pip {
  width: 5px;
  height: 12px;
  border: 1px solid var(--rule-strong);
  border-radius: 1px;
  background: transparent;
}

.pip--on {
  background: currentColor;
  border-color: currentColor;
}

.meter__label {
  font-family: var(--display);
  font-size: var(--step-0);
  letter-spacing: 0.02em;
}

.meter--easy {
  color: var(--moss);
}

.meter--middling {
  color: var(--gilt);
}

.meter--hard {
  color: var(--wax);
}
</style>
