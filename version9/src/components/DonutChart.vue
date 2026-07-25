<script setup lang="ts">
import { computed } from 'vue'

interface Slice { label: string; value: number; color: string }

const props = withDefaults(defineProps<{
  slices: Slice[]
  size?: number
  thickness?: number
  centerLabel?: string
  centerValue?: string
}>(), {
  size: 180,
  thickness: 22,
  centerLabel: '',
  centerValue: '',
})

const total = computed(() => props.slices.reduce((s, x) => s + Math.max(0, x.value), 0) || 1)
const r = computed(() => (props.size - props.thickness) / 2)
const c = computed(() => 2 * Math.PI * r.value)

interface Arc extends Slice { offset: number; len: number }
const arcs = computed<Arc[]>(() => {
  let acc = 0
  return props.slices.map((s) => {
    const frac = Math.max(0, s.value) / total.value
    const len = frac * c.value
    const arc = { ...s, offset: -acc, len }
    acc += len
    return arc
  })
})
</script>

<template>
  <div class="dc" :style="{ width: size + 'px' }">
    <div class="dc-ring">
      <svg :width="size" :height="size" :viewBox="`0 0 ${size} ${size}`" role="img" aria-label="占比环形图">
        <circle :cx="size / 2" :cy="size / 2" :r="r" fill="none" stroke="var(--surface-2)" :stroke-width="thickness" />
        <circle
          v-for="(a, i) in arcs" :key="i"
          :cx="size / 2" :cy="size / 2" :r="r"
          :stroke="a.color" fill="none" :stroke-width="thickness" stroke-linecap="round"
          :stroke-dasharray="`${Math.max(0, a.len - 2)} ${c - Math.max(0, a.len - 2)}`"
          :transform="`rotate(-90 ${size / 2} ${size / 2})`"
          :style="{ strokeDashoffset: a.offset + 'px' }"
          class="dc-arc"
        />
      </svg>
      <div class="dc-center" v-if="centerValue">
        <div class="dc-value tnum">{{ centerValue }}</div>
        <div class="dc-clabel">{{ centerLabel }}</div>
      </div>
    </div>
    <ul class="dc-legend">
      <li v-for="(a, i) in arcs" :key="i">
        <span class="dot" :style="{ background: a.color }" />
        <span class="dc-name">{{ a.label }}</span>
        <span class="dc-num tnum">{{ Math.round((a.value / total) * 100) }}%</span>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.dc { display: flex; align-items: center; gap: var(--s-5); flex-wrap: wrap; }
.dc-ring { position: relative; flex: none; }
.dc-arc { transition: stroke-dasharray 0.9s var(--ease-out); }
.dc-center { position: absolute; inset: 0; display: grid; place-content: center; text-align: center; }
.dc-value { font-family: var(--font-display); font-weight: 800; font-size: 24px; color: var(--text); }
.dc-clabel { font-size: 12px; color: var(--text-soft); }
.dc-legend { list-style: none; margin: 0; padding: 0; display: grid; gap: 8px; flex: 1; min-width: 140px; }
.dc-legend li { display: flex; align-items: center; gap: 8px; font-size: 13px; }
.dot { width: 10px; height: 10px; border-radius: 3px; flex: none; }
.dc-name { color: var(--text-soft); }
.dc-num { margin-left: auto; font-weight: 700; color: var(--text); }
</style>
