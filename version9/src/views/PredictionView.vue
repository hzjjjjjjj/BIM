<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { auth } from '@/stores/auth'
import { api } from '@/services/api'
import { assessRisk, calcBMI, classifyBMI, predictTrend, round } from '@/lib/health'
import LineChart from '@/components/LineChart.vue'
import StatPill from '@/components/StatPill.vue'
import EmptyState from '@/components/EmptyState.vue'
import type { HealthRecord } from '@/types'

const records = ref<HealthRecord[]>([])
const height = computed(() => auth.user?.height ?? 170)

function toMs(r: HealthRecord) { return new Date(r.recordDate).getTime() }

const weights = computed(() => records.value.map((r) => r.weight))
const dates = computed(() => records.value.map(toMs))

const pred7 = computed(() => predictTrend(dates.value, weights.value, 7))
const pred14 = computed(() => predictTrend(dates.value, weights.value, 14))
const pred30 = computed(() => predictTrend(dates.value, weights.value, 30))

const hasData = computed(() => records.value.length >= 3)

const predBmi30 = computed(() => (isNaN(pred30.value) ? NaN : calcBMI(pred30.value, height.value)))

const series = computed(() => records.value.map((r) => ({ label: r.recordDate.slice(5), value: r.weight })))
const preds = computed(() => {
  if (!hasData.value) return []
  return [pred7.value, pred14.value, pred30.value].filter((v) => !isNaN(v)).map((v) => round(v, 1))
})

const risk = computed(() => (isNaN(predBmi30.value) ? '' : assessRisk(predBmi30.value)))
const riskTone = computed(() => {
  if (isNaN(predBmi30.value)) return 'neutral'
  if (predBmi30.value >= 28) return 'danger'
  if (predBmi30.value >= 24 || predBmi30.value < 18.5) return 'warn'
  return 'primary'
})

function bmiClassOf(w: number) { return classifyBMI(calcBMI(w, height.value)) }

onMounted(async () => { if (auth.user) records.value = await api.getRecords(auth.user.username, 30) })
</script>

<template>
  <div class="pred">
    <section class="card" v-if="hasData">
      <div class="card-head"><h3>未来体重预测</h3><span class="muted">线性回归 · 基于历史趋势</span></div>
      <div class="cards">
        <div class="pcard">
          <div class="pcard-t">7 天后</div>
          <div class="pcard-v tnum">{{ isNaN(pred7) ? '—' : round(pred7, 1) }}<i>kg</i></div>
          <div class="pcard-s">BMI {{ isNaN(pred7) ? '—' : calcBMI(pred7, height).toFixed(1) }} · {{ bmiClassOf(pred7) }}</div>
        </div>
        <div class="pcard">
          <div class="pcard-t">14 天后</div>
          <div class="pcard-v tnum">{{ isNaN(pred14) ? '—' : round(pred14, 1) }}<i>kg</i></div>
          <div class="pcard-s">BMI {{ isNaN(pred14) ? '—' : calcBMI(pred14, height).toFixed(1) }} · {{ bmiClassOf(pred14) }}</div>
        </div>
        <div class="pcard hl">
          <div class="pcard-t">30 天后</div>
          <div class="pcard-v tnum">{{ isNaN(pred30) ? '—' : round(pred30, 1) }}<i>kg</i></div>
          <div class="pcard-s">BMI {{ isNaN(pred30) ? '—' : calcBMI(pred30, height).toFixed(1) }} · {{ bmiClassOf(pred30) }}</div>
        </div>
      </div>

      <div class="chart-area">
        <LineChart :points="series" :predictions="preds" unit="kg" color="#1f8a6d" :height="280" />
        <p class="hint">实线为历史体重，虚线为预测延伸（含 7/14/30 天节点）。</p>
      </div>

      <div class="risk" :class="riskTone">
        <div class="risk-head"><span class="risk-ic">⚕</span> 30 天健康风险评估</div>
        <p>{{ risk }}</p>
        <div class="risk-badges">
          <StatPill text="预测 BMI 30d" :tone="riskTone" :icon="isNaN(predBmi30) ? '' : '📉'" />
          <StatPill :text="bmiClassOf(pred30)" tone="neutral" v-if="!isNaN(pred30)" />
        </div>
      </div>
    </section>

    <EmptyState v-else title="数据不足，无法预测" desc="预测需要至少 3 条记录。坚持打卡几天后，这里会给出未来体重与风险评估。" icon="🔮" />
  </div>
</template>

<style scoped>
.pred { display: grid; gap: var(--s-6); }
.card { padding: var(--s-6); }
.card-head { display: flex; justify-content: space-between; align-items: baseline; margin-bottom: var(--s-5); }
.card-head h3 { font-size: 17px; }
.cards { display: grid; grid-template-columns: repeat(3, 1fr); gap: var(--s-4); margin-bottom: var(--s-6); }
.pcard { border: 1px solid var(--border); border-radius: var(--r-lg); padding: var(--s-5); background: var(--surface-2); }
.pcard.hl { background: linear-gradient(160deg, var(--primary-ll), var(--surface)); border-color: var(--primary); }
.pcard-t { font-size: 13px; color: var(--text-soft); font-weight: 700; }
.pcard-v { font-family: var(--font-display); font-weight: 800; font-size: 32px; color: var(--text); margin: 6px 0; }
.pcard-v i { font-style: normal; font-size: 13px; color: var(--text-faint); margin-left: 3px; }
.pcard-s { font-size: 12.5px; color: var(--text-faint); }
.chart-area { margin-top: var(--s-2); }
.hint { font-size: 12px; color: var(--text-faint); margin: var(--s-3) 0 0; }

.risk { border-radius: var(--r-lg); padding: var(--s-5); border: 1px solid var(--border); margin-top: var(--s-5); }
.risk.primary { background: var(--primary-l); border-color: var(--primary); }
.risk.warn { background: var(--warn-l); border-color: var(--warn); }
.risk.danger { background: var(--danger-l); border-color: var(--danger); }
.risk.neutral { background: var(--surface-2); }
.risk-head { display: flex; align-items: center; gap: 8px; font-weight: 800; font-size: 14.5px; margin-bottom: 8px; }
.risk-ic { font-size: 16px; }
.risk p { margin: 0 0 var(--s-3); font-size: 13.5px; color: var(--text); line-height: 1.65; }
.risk-badges { display: flex; gap: 8px; flex-wrap: wrap; }

@media (max-width: 640px) { .cards { grid-template-columns: 1fr; } }
</style>
