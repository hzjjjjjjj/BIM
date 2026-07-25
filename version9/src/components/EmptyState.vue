<script setup lang="ts">
defineProps<{
  title: string
  desc?: string
  icon?: string
}>()
</script>

<template>
  <div class="empty card">
    <div class="empty-ic">{{ icon ?? '🌱' }}</div>
    <div class="empty-title">{{ title }}</div>
    <div v-if="desc" class="empty-desc">{{ desc }}</div>
    <slot />
  </div>
</template>

<style scoped>
.empty {
  padding: var(--s-8) var(--s-6);
  text-align: center;
  display: grid; gap: 8px; justify-items: center;
  border-style: dashed;
}
.empty-ic { font-size: 34px; }
.empty-title { font-family: var(--font-display); font-weight: 700; font-size: 17px; }
.empty-desc { color: var(--text-soft); font-size: 13.5px; max-width: 42ch; }
</style>
