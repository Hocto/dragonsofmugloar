<script setup lang="ts">
/** Every failed request lands here: what went wrong, and a way out. No dead ends. */
defineProps<{ message: string; retryable: boolean; retrying: boolean }>()

const emit = defineEmits<{ retry: []; dismiss: [] }>()
</script>

<template>
  <div class="error" role="alert">
    <p class="error__text">{{ message }}</p>
    <div class="error__actions">
      <button
        v-if="retryable"
        type="button"
        class="seal seal--primary"
        :disabled="retrying"
        @click="emit('retry')"
      >
        {{ retrying ? 'Trying again...' : 'Try again' }}
      </button>
      <button type="button" class="seal" @click="emit('dismiss')">Dismiss</button>
    </div>
  </div>
</template>

<style scoped>
.error {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--gap-2);
  padding: var(--gap-2) var(--gap-3);
  border: 1px solid var(--wax);
  background: var(--wax-wash);
  border-radius: var(--radius);
}

.error__text {
  font-family: var(--display);
  color: var(--wax);
}

.error__actions {
  display: flex;
  gap: var(--gap-2);
}
</style>
