<script setup lang="ts">
withDefaults(defineProps<{
  label: string
  value: number
  max: number
  unit?: string
  color?: string
  note?: string
}>(), {
  unit: '',
  color: '#1f8a6d',
  note: '',
})

function pct(value: number, max: number): number {
  return Math.max(0, Math.min(100, (value / max) * 100))
}
</script>

<template>
  <div class="bm">
    <div class="bm-head">
      <span class="bm-label">{{ label }}</span>
      <span class="bm-val tnum">{{ value }}<span class="bm-unit">{{ unit }}</span></span>
    </div>
    <div class="bm-track">
      <div class="bm-fill" :style="{ width: pct(value, max) + '%', background: color }" />
    </div>
    <div v-if="note" class="bm-note">{{ note }}</div>
  </div>
</template>

<style scoped>
.bm { display: grid; gap: 6px; }
.bm-head { display: flex; justify-content: space-between; align-items: baseline; }
.bm-label { font-size: 13px; color: var(--text-soft); font-weight: 600; }
.bm-val { font-family: var(--font-display); font-weight: 700; font-size: 15px; color: var(--text); }
.bm-unit { font-size: 11px; color: var(--text-faint); margin-left: 2px; }
.bm-track { height: 8px; background: var(--surface-2); border-radius: 99px; overflow: hidden; }
.bm-fill { height: 100%; border-radius: 99px; transition: width 0.9s var(--ease-out); }
.bm-note { font-size: 11.5px; color: var(--text-faint); }
</style>
