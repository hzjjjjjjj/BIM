<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { auth } from '@/stores/auth'
import { api } from '@/services/api'
import { calcHealthScore, classifyBMI, scoreLevel } from '@/lib/health'
import { NAV } from '@/nav'
import MetricCard from '@/components/MetricCard.vue'
import ProgressRing from '@/components/ProgressRing.vue'
import LineChart from '@/components/LineChart.vue'
import StatPill from '@/components/StatPill.vue'
import EmptyState from '@/components/EmptyState.vue'
import type { HealthRecord } from '@/types'

const router = useRouter()
const records = ref<HealthRecord[]>([])
const latest = ref<HealthRecord | undefined>()
const calorieDiff = ref<number | null>(null)
const checked = ref(false)
const loading = ref(true)

const score = computed(() => {
  const r = latest.value
  if (!r || !auth.user) return null
  return calcHealthScore(r.bmi, r.bodyFat, r.visceralFat, r.muscleRate, r.waterRate, auth.user.gender)
})

const weightSeries = computed(() => records.value.map((r) => ({
  label: r.recordDate.slice(5), value: r.weight,
})))

const trend = computed(() => {
  const arr = records.value
  if (arr.length < 2) return null
  const d = arr[arr.length - 1].weight - arr[0].weight
  return d
})

async function load() {
  if (!auth.user) return
  const u = auth.user.username
  records.value = await api.getRecords(u, 30)
  latest.value = await api.getLatestRecord(u)
  checked.value = await api.isCheckedToday(u)
  const tdee = latest.value ? latest.value.tdee : 0
  const ex = await api.getTodayExerciseCalories(u)
  const diet = await api.getTodayDietSummary(u)
  calorieDiff.value = Math.round(tdee + ex - diet[0])
  loading.value = false
}

onMounted(load)
</script>

<template>
  <div class="dash">
    <!-- 欢迎横幅 -->
    <section class="hero card">
      <div class="hero-left">
        <div class="hero-greet">下午好，{{ auth.user?.username }} 👋</div>
        <h2 class="hero-title">今天的身体状态如何？</h2>
        <p class="hero-sub" v-if="latest">
          最新记录 {{ latest.recordDate }} · BMI {{ latest.bmi }}（{{ classifyBMI(latest.bmi) }}）· 体质 {{ latest.bodyType }}
        </p>
        <p class="hero-sub" v-else>还没有记录，去「数据录入」完成第一次打卡吧。</p>
        <div class="hero-actions">
          <button class="hero-btn" @click="router.push({ name: 'input' })">＋ 立即打卡</button>
          <button class="hero-btn report-btn" @click="router.push({ name: 'report' })">📋 生成详细报告</button>
          <span v-if="!checked" class="hero-remind">⚠ 今天还没称重哦</span>
          <span v-else class="hero-remind ok">✓ 今日已打卡</span>
        </div>
      </div>
      <div class="hero-ring">
        <ProgressRing
          v-if="score != null" :value="score" :max="100" :size="148" :stroke="13"
          :color="score >= 75 ? 'var(--primary)' : score >= 60 ? 'var(--gold)' : 'var(--accent)'"
          label="健康评分"
        />
        <div v-if="score != null" class="hero-level" :class="score >= 75 ? 'good' : score >= 60 ? 'mid' : 'low'">
          {{ scoreLevel(score) }}
        </div>
      </div>
    </section>

    <!-- 指标卡 -->
    <section class="grid-metrics" v-if="latest">
      <MetricCard label="当前体重" :value="latest.weight" unit="kg" icon="⚖️" tint="var(--primary-l)" fg="var(--primary)"
        :sub="trend != null ? (trend <= 0 ? '较首期下降 ' + Math.abs(trend).toFixed(1) + 'kg' : '较首期上升 ' + trend.toFixed(1) + 'kg') : ''"
        :trend="trend == null ? 'flat' : trend < 0 ? 'down' : 'up'" :trendText="trend ? Math.abs(trend).toFixed(1) + 'kg' : ''" />
      <MetricCard label="BMI" :value="latest.bmi" unit="" icon="📐" tint="var(--accent-l)" fg="var(--accent-d)" :sub="classifyBMI(latest.bmi)" />
      <MetricCard label="体脂率" :value="latest.bodyFat" unit="%" icon="💧" tint="var(--info-l)" fg="#1f5e95" :sub="latest.bodyType" />
      <MetricCard label="肌肉率" :value="latest.muscleRate" unit="%" icon="💪" tint="var(--gold-l)" fg="#9a6b06" />
      <MetricCard label="内脏脂肪" :value="latest.visceralFat" unit="级" icon="🔥" tint="var(--danger-l)" fg="var(--danger)" />
      <MetricCard label="水分率" :value="latest.waterRate" unit="%" icon="🌊" tint="var(--primary-l)" fg="var(--primary-d)" />
      <MetricCard label="基础代谢" :value="latest.bmr" unit="kcal" icon="⚡" tint="var(--surface-2)" fg="var(--text)" />
      <MetricCard label="今日热量差" :value="calorieDiff ?? '--'" unit="kcal" icon="🍽️" tint="var(--accent-l)" fg="var(--accent-d)"
        :sub="calorieDiff != null && calorieDiff >= 0 ? '消耗 > 摄入，趋势向好' : '摄入偏多，注意控制'" />
    </section>

    <div v-else-if="!loading" class="empty-wrap">
      <EmptyState title="还没有健康数据" desc="完成第一次称重打卡后，这里会展示你的健康总览。" icon="🌱">
        <button class="cta" @click="router.push({ name: 'input' })">去录入数据</button>
      </EmptyState>
    </div>

    <!-- 趋势 -->
    <section class="card chart-card" v-if="records.length">
      <div class="card-head">
        <h3>体重趋势 <span class="muted">· 近 30 天</span></h3>
        <StatPill v-if="trend != null" :text="trend <= 0 ? '↓ ' + Math.abs(trend).toFixed(1) + 'kg' : '↑ ' + trend.toFixed(1) + 'kg'"
          :tone="trend <= 0 ? 'primary' : 'accent'" />
      </div>
      <LineChart :points="weightSeries" unit="kg" color="#1f8a6d" :height="240" />
    </section>

    <!-- 快捷入口 -->
    <section class="quick" v-if="latest">
      <button v-for="item in NAV.filter(n => n.name !== 'dashboard')" :key="item.name" class="quick-item" @click="router.push(item.path)">
        <span class="quick-ic">{{ item.icon }}</span>
        <span class="quick-label">{{ item.label }}</span>
        <span class="quick-hint">{{ item.hint }}</span>
      </button>
    </section>
  </div>
</template>

<style scoped>
.dash { display: grid; gap: var(--s-6); }
.hero { display: flex; align-items: center; gap: var(--s-6); padding: var(--s-7); background: linear-gradient(120deg, var(--surface) 60%, var(--primary-ll)); }
.hero-left { flex: 1; min-width: 0; }
.hero-greet { font-size: 14px; color: var(--text-soft); font-weight: 600; }
.hero-title { font-size: 26px; margin: 6px 0 8px; }
.hero-sub { color: var(--text-soft); font-size: 14px; margin: 0 0 14px; }
.hero-actions { display: flex; align-items: center; gap: var(--s-4); flex-wrap: wrap; }
.hero-btn {
  border: none; cursor: pointer; color: #fff; font-weight: 800; padding: 11px 20px; border-radius: var(--r-md);
  background: linear-gradient(135deg, var(--primary), var(--primary-d)); box-shadow: 0 10px 22px -10px rgba(31, 138, 109, 0.8);
  transition: transform 0.2s var(--ease-out);
}
.hero-btn:hover { transform: translateY(-2px); }
.report-btn { background: linear-gradient(135deg, var(--gold), #d4a012); box-shadow: 0 10px 22px -10px rgba(244,183,64,0.7); }
.hero-remind { font-size: 13px; font-weight: 700; color: var(--warn); }
.hero-remind.ok { color: var(--primary-d); }
.hero-ring { flex: none; display: grid; justify-items: center; gap: 8px; }
.hero-level { font-family: var(--font-display); font-weight: 800; font-size: 15px; padding: 4px 14px; border-radius: var(--r-pill); }
.hero-level.good { background: var(--primary-l); color: var(--primary-d); }
.hero-level.mid { background: var(--gold-l); color: #9a6b06; }
.hero-level.low { background: var(--accent-l); color: var(--accent-d); }

.grid-metrics { display: grid; grid-template-columns: repeat(4, 1fr); gap: var(--s-4); }
.chart-card { padding: var(--s-6); }
.card-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: var(--s-4); }
.card-head h3 { font-size: 16px; }

.quick { display: grid; grid-template-columns: repeat(4, 1fr); gap: var(--s-4); }
.quick-item {
  text-align: left; cursor: pointer; border: 1px solid var(--border); background: var(--surface);
  border-radius: var(--r-lg); padding: var(--s-5); display: grid; gap: 4px; transition: all 0.22s var(--ease-out);
}
.quick-item:hover { transform: translateY(-3px); box-shadow: var(--sh-2); border-color: var(--primary); }
.quick-ic { font-size: 22px; }
.quick-label { font-weight: 700; font-size: 14.5px; }
.quick-hint { font-size: 12px; color: var(--text-faint); }

.empty-wrap { display: grid; }
.cta {
  border: none; cursor: pointer; color: #fff; font-weight: 700; padding: 10px 18px; border-radius: var(--r-pill);
  background: linear-gradient(135deg, var(--primary), var(--primary-d)); margin-top: 4px;
}

@media (max-width: 1080px) { .grid-metrics, .quick { grid-template-columns: repeat(2, 1fr); } }
@media (max-width: 720px) {
  .hero { flex-direction: column; align-items: flex-start; }
  .hero-ring { align-self: center; }
  .grid-metrics, .quick { grid-template-columns: 1fr; }
}
</style>
