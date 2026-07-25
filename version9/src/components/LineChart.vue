<script setup lang="ts">
import { computed } from 'vue'

interface Pt { label: string; value: number }

const props = withDefaults(defineProps<{
  points: Pt[]
  predictions?: number[]
  color?: string
  unit?: string
  height?: number
  yMin?: number
  yMax?: number
}>(), {
  color: '#1f8a6d',
  unit: '',
  height: 260,
  predictions: () => [],
})

const W = 720
const H = computed(() => props.height)
const PAD = { l: 44, r: 16, t: 18, b: 28 }

interface Coord { x: number; y: number; v: number; label: string }

const coords = computed<Coord[]>(() => {
  const all = [...props.points.map((p) => p.value), ...props.predictions]
  if (all.length === 0) return []
  const min = props.yMin ?? Math.min(...all)
  const max = props.yMax ?? Math.max(...all)
  const span = max - min || 1
  const lo = min - span * 0.12
  const hi = max + span * 0.12
  const innerW = W - PAD.l - PAD.r
  const innerH = H.value - PAD.t - PAD.b
  const n = props.points.length
  return props.points.map((p, i) => {
    const x = PAD.l + (n <= 1 ? innerW / 2 : (innerW * i) / (n - 1))
    const y = PAD.t + innerH * (1 - (p.value - lo) / (hi - lo))
    return { x, y, v: p.value, label: p.label }
  })
})

const predCoords = computed<Coord[]>(() => {
  if (props.predictions.length === 0 || coords.value.length === 0) return []
  const last = coords.value[coords.value.length - 1]
  const innerW = W - PAD.l - PAD.r
  const all = [...props.points.map((p) => p.value), ...props.predictions]
  const min = props.yMin ?? Math.min(...all)
  const max = props.yMax ?? Math.max(...all)
  const span = max - min || 1
  const lo = min - span * 0.12
  const hi = max + span * 0.12
  const innerH = H.value - PAD.t - PAD.b
  const segStep = props.points.length > 1
    ? (coords.value[1].x - coords.value[0].x)
    : innerW / (props.predictions.length + 1)
  return props.predictions.map((v, i) => {
    const x = last.x + segStep * (i + 1)
    const y = PAD.t + innerH * (1 - (v - lo) / (hi - lo))
    return { x, y, v, label: `预测${i + 1}` }
  })
})

function path(coordsArr: Coord[]): string {
  if (coordsArr.length === 0) return ''
  return coordsArr.map((c, i) => `${i === 0 ? 'M' : 'L'}${c.x.toFixed(1)},${c.y.toFixed(1)}`).join(' ')
}

const linePath = computed(() => path(coords.value))
const predPath = computed(() => {
  if (predCoords.value.length === 0) return ''
  const anchor = coords.value[coords.value.length - 1]
  return `M${anchor.x.toFixed(1)},${anchor.y.toFixed(1)} ${path(predCoords.value).replace(/^M/, 'L')}`
})

const areaPath = computed(() => {
  if (coords.value.length === 0) return ''
  const first = coords.value[0]
  const last = coords.value[coords.value.length - 1]
  return `${linePath.value} L${last.x.toFixed(1)},${H.value - PAD.b} L${first.x.toFixed(1)},${H.value - PAD.b} Z`
})

const gid = `lc-${Math.random().toString(36).slice(2, 8)}`

const yTicks = computed(() => {
  const all = [...props.points.map((p) => p.value), ...props.predictions]
  if (all.length === 0) return []
  const min = props.yMin ?? Math.min(...all)
  const max = props.yMax ?? Math.max(...all)
  const span = max - min || 1
  const lo = min - span * 0.12
  const hi = max + span * 0.12
  const ticks: { y: number; v: number }[] = []
  const steps = 4
  const innerH = H.value - PAD.t - PAD.b
  for (let i = 0; i <= steps; i++) {
    const v = lo + ((hi - lo) * i) / steps
    const y = PAD.t + innerH * (1 - i / steps)
    ticks.push({ y, v })
  }
  return ticks
})

const xLabels = computed(() => {
  if (coords.value.length === 0) return []
  const step = Math.ceil(coords.value.length / 6)
  return coords.value.filter((_, i) => i % step === 0 || i === coords.value.length - 1)
})
</script>

<template>
  <div class="lc">
    <svg :viewBox="`0 0 ${W} ${H}`" preserveAspectRatio="none" class="lc-svg" role="img" :aria-label="`折线图${unit ? '，单位' + unit : ''}`">
      <defs>
        <linearGradient :id="gid" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" :stop-color="color" stop-opacity="0.22" />
          <stop offset="100%" :stop-color="color" stop-opacity="0" />
        </linearGradient>
      </defs>

      <!-- 网格与 Y 轴刻度 -->
      <g class="lc-grid">
        <line v-for="(t, i) in yTicks" :key="'y' + i" :x1="PAD.l" :x2="W - PAD.r" :y1="t.y" :y2="t.y" />
        <text v-for="(t, i) in yTicks" :key="'yt' + i" :x="PAD.l - 8" :y="t.y + 4" text-anchor="end" class="lc-tick">{{ t.v.toFixed(1) }}</text>
      </g>

      <!-- 面积 -->
      <path v-if="areaPath" :d="areaPath" :fill="`url(#${gid})`" />

      <!-- 历史折线 -->
      <path v-if="linePath" :d="linePath" fill="none" :stroke="color" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" />

      <!-- 预测虚线 -->
      <path v-if="predPath" :d="predPath" fill="none" :stroke="color" stroke-width="2.5" stroke-dasharray="6 5" stroke-linecap="round" opacity="0.7" />

      <!-- 数据点 -->
      <g>
        <circle v-for="(c, i) in coords" :key="'c' + i" :cx="c.x" :cy="c.y" r="3.2" :fill="color" stroke="#fff" stroke-width="1.6" />
      </g>

      <!-- X 轴标签 -->
      <text v-for="(c, i) in xLabels" :key="'xl' + i" :x="c.x" :y="H - 8" text-anchor="middle" class="lc-xlabel">{{ c.label }}</text>
    </svg>
  </div>
</template>

<style scoped>
.lc { width: 100%; }
.lc-svg { width: 100%; height: auto; display: block; }
.lc-grid line { stroke: var(--border); stroke-width: 1; stroke-dasharray: 2 4; }
.lc-tick { fill: var(--text-faint); font-size: 11px; font-family: var(--font-body); }
.lc-xlabel { fill: var(--text-faint); font-size: 11px; font-family: var(--font-body); }
</style>
