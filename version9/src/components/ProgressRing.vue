<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  value: number
  max?: number
  size?: number
  stroke?: number
  color?: string
  track?: string
  label?: string
}>(), {
  max: 100,
  size: 132,
  stroke: 12,
  color: '#1f8a6d',
  track: '#e7e1d5',
  label: '',
})

const r = computed(() => (props.size - props.stroke) / 2)
const c = computed(() => 2 * Math.PI * r.value)
const pct = computed(() => Math.max(0, Math.min(1, props.value / props.max)))
const dash = computed(() => `${c.value * pct.value} ${c.value}`)
const gid = `pr-${Math.random().toString(36).slice(2, 7)}`
</script>

<template>
  <div class="pr" :style="{ width: size + 'px', height: size + 'px' }">
    <svg :width="size" :height="size" :viewBox="`0 0 ${size} ${size}`" role="img" :aria-label="`${label} ${value}/${max}`">
      <defs>
        <linearGradient :id="gid" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0%" :stop-color="color" />
          <stop offset="100%" :stop-color="color" stop-opacity="0.7" />
        </linearGradient>
      </defs>
      <circle :cx="size / 2" :cy="size / 2" :r="r" :stroke="track" :stroke-width="stroke" fill="none" />
      <circle
        :cx="size / 2" :cy="size / 2" :r="r" :stroke="`url(#${gid})`" :stroke-width="stroke"
        fill="none" stroke-linecap="round" :stroke-dasharray="dash"
        :transform="`rotate(-90 ${size / 2} ${size / 2})`"
        class="pr-arc"
      />
    </svg>
    <div class="pr-center">
      <div class="pr-value tnum">{{ Math.round(value) }}</div>
      <div v-if="label" class="pr-label">{{ label }}</div>
    </div>
  </div>
</template>

<style scoped>
.pr { position: relative; display: inline-grid; place-items: center; }
.pr-arc { transition: stroke-dasharray 1s var(--ease-out); }
.pr-center { position: absolute; inset: 0; display: grid; place-content: center; text-align: center; }
.pr-value {
  font-family: var(--font-display);
  font-weight: 800;
  font-size: 30px;
  line-height: 1;
  color: var(--text);
}
.pr-label { font-size: 12px; color: var(--text-soft); margin-top: 4px; }
</style>
