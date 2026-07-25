<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { auth } from '@/stores/auth'
import { api } from '@/services/api'
import { round } from '@/lib/health'
import LineChart from '@/components/LineChart.vue'
import EmptyState from '@/components/EmptyState.vue'
import type { HealthRecord } from '@/types'

type Metric = 'weight' | 'bmi' | 'bodyFat' | 'muscleRate' | 'waterRate' | 'visceralFat'

const METRICS: { key: Metric; label: string; unit: string; color: string }[] = [
  { key: 'weight', label: '体重', unit: 'kg', color: '#1f8a6d' },
  { key: 'bmi', label: 'BMI', unit: '', color: '#ff7a5c' },
  { key: 'bodyFat', label: '体脂率', unit: '%', color: '#3b82c4' },
  { key: 'muscleRate', label: '肌肉率', unit: '%', color: '#f4b740' },
  { key: 'waterRate', label: '水分率', unit: '%', color: '#06b6d4' },
  { key: 'visceralFat', label: '内脏脂肪', unit: '级', color: '#e5484d' },
]

const metric = ref<Metric>('weight')
const range = ref(30)
const records = ref<HealthRecord[]>([])

const current = computed(() => METRICS.find((m) => m.key === metric.value)!)

const series = computed(() => {
  const list = records.value.slice(-range.value)
  return list.map((r) => ({ label: r.recordDate.slice(5), value: r[metric.value] as number }))
})

const stats = computed(() => {
  const vals = series.value.map((s) => s.value)
  if (!vals.length) return null
  const min = Math.min(...vals), max = Math.max(...vals)
  const avg = vals.reduce((a, b) => a + b, 0) / vals.length
  const change = vals.length > 1 ? vals[vals.length - 1] - vals[0] : 0
  return { min: round(min, 1), max: round(max, 1), avg: round(avg, 1), change: round(change, 1) }
})

const preds = computed(() => {
  const vals = series.value.map((s) => s.value)
  if (vals.length < 3) return []
  const n = vals.length
  const x = vals.map((_, i) => i)
  const sx = x.reduce((a, b) => a + b, 0), sy = vals.reduce((a, b) => a + b, 0)
  const sxy = x.reduce((a, b, i) => a + b * vals[i], 0), sxx = x.reduce((a) => a + 1, 0) * 0 + x.reduce((a, b) => a + b * b, 0)
  const denom = n * sxx - sx * sx
  if (Math.abs(denom) < 1e-9) return []
  const k = (n * sxy - sx * sy) / denom
  const b = (sy - k * sx) / n
  return [k * (n) + b, k * (n + 1) + b, k * (n + 2) + b].map((v) => round(v, 1))
})

onMounted(async () => { if (auth.user) records.value = await api.getRecords(auth.user.username) })
</script>

<template>
  <div class="hist">
    <section class="card">
      <div class="toolbar">
        <div class="metrics">
          <button v-for="m in METRICS" :key="m.key" :class="{ active: metric === m.key }"
            :style="metric === m.key ? { borderColor: m.color, color: m.color } : {}" @click="metric = m.key">
            {{ m.label }}
          </button>
        </div>
        <div class="ranges">
          <button v-for="r in [7, 30, 90]" :key="r" :class="{ active: range === r }" @click="range = r">{{ r }}天</button>
        </div>
      </div>

      <div v-if="series.length" class="chart-area">
        <LineChart :points="series" :predictions="preds" :unit="current.unit" :color="current.color" :height="300" />
        <p class="pred-hint" v-if="preds.length">虚线为基于线性回归的后续 3 期预测趋势</p>
      </div>
      <EmptyState v-else title="暂无足够数据" desc="至少需要 3 条记录才能绘制趋势曲线，先去打卡吧。" />
    </section>

    <section class="stats" v-if="stats">
      <div class="stat"><div class="stat-v tnum">{{ stats.min }}<i>{{ current.unit }}</i></div><div class="stat-l">区间最低</div></div>
      <div class="stat"><div class="stat-v tnum">{{ stats.max }}<i>{{ current.unit }}</i></div><div class="stat-l">区间最高</div></div>
      <div class="stat"><div class="stat-v tnum">{{ stats.avg }}<i>{{ current.unit }}</i></div><div class="stat-l">区间均值</div></div>
      <div class="stat"><div class="stat-v tnum" :class="stats.change <= 0 ? 'down' : 'up'">{{ stats.change > 0 ? '+' : '' }}{{ stats.change }}<i>{{ current.unit }}</i></div><div class="stat-l">区间变化</div></div>
    </section>
  </div>
</template>

<style scoped>
.hist { display: grid; gap: var(--s-6); }
.card { padding: var(--s-6); }
.toolbar { display: flex; justify-content: space-between; align-items: center; gap: var(--s-4); flex-wrap: wrap; margin-bottom: var(--s-5); }
.metrics { display: flex; flex-wrap: wrap; gap: 8px; }
.metrics button {
  border: 1.5px solid var(--border); background: var(--surface); color: var(--text-soft);
  padding: 8px 14px; border-radius: var(--r-pill); font-weight: 700; font-size: 13px; cursor: pointer; transition: all 0.2s;
}
.metrics button.active { background: var(--primary-ll); }
.ranges { display: flex; gap: 4px; background: var(--surface-2); padding: 4px; border-radius: var(--r-pill); }
.ranges button { border: none; background: none; padding: 7px 12px; border-radius: var(--r-pill); font-weight: 700; font-size: 12.5px; color: var(--text-soft); cursor: pointer; }
.ranges button.active { background: var(--surface); color: var(--primary-d); box-shadow: var(--sh-1); }
.chart-area { margin-top: var(--s-2); }
.pred-hint { font-size: 12px; color: var(--text-faint); margin: var(--s-3) 0 0; }

.stats { display: grid; grid-template-columns: repeat(4, 1fr); gap: var(--s-4); }
.stat { background: var(--surface); border: 1px solid var(--border); border-radius: var(--r-lg); padding: var(--s-5); }
.stat-v { font-family: var(--font-display); font-weight: 800; font-size: 24px; color: var(--text); }
.stat-v i { font-style: normal; font-size: 12px; color: var(--text-faint); margin-left: 2px; }
.stat-v.down { color: var(--primary-d); }
.stat-v.up { color: var(--accent-d); }
.stat-l { font-size: 12.5px; color: var(--text-soft); margin-top: 4px; }

@media (max-width: 720px) { .stats { grid-template-columns: repeat(2, 1fr); } }
</style>
