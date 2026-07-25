<script setup lang="ts">
withDefaults(defineProps<{
  text: string
  tone?: 'primary' | 'accent' | 'gold' | 'danger' | 'warn' | 'neutral' | 'info'
  icon?: string
}>(), {
  tone: 'neutral',
  icon: '',
})
</script>

<template>
  <span class="pill" :class="tone">
    <span v-if="icon" class="pill-ic">{{ icon }}</span>{{ text }}
  </span>
</template>

<style scoped>
.pill {
  display: inline-flex; align-items: center; gap: 5px;
  padding: 4px 11px; border-radius: var(--r-pill);
  font-size: 12.5px; font-weight: 700; line-height: 1;
  border: 1px solid transparent;
}
.pill-ic { font-size: 12px; }
.primary { background: var(--primary-l); color: var(--primary-d); }
.accent { background: var(--accent-l); color: var(--accent-d); }
.gold { background: var(--gold-l); color: #9a6b06; }
.danger { background: var(--danger-l); color: var(--danger); }
.warn { background: var(--warn-l); color: #9a5a08; }
.info { background: var(--info-l); color: #1f5e95; }
.neutral { background: var(--surface-2); color: var(--text-soft); border-color: var(--border); }
</style>
