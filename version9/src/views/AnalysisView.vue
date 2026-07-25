<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { auth } from '@/stores/auth'
import { api } from '@/services/api'
import {
  assessMuscle, assessVisceralFat, assessWHtR, classifyBMI, classifyBodyShape,
  healthScoreBreakdown, calcHealthScore, scoreLevel,
} from '@/lib/health'
import BarMeter from '@/components/BarMeter.vue'
import ProgressRing from '@/components/ProgressRing.vue'
import StatPill from '@/components/StatPill.vue'
import EmptyState from '@/components/EmptyState.vue'
import type { HealthRecord } from '@/types'

const latest = ref<HealthRecord | undefined>()
const dims = ref<{ label: string; score: number; max: number }[]>([])
const score = ref(0)

const gender = computed(() => auth.user?.gender ?? '男')
const height = computed(() => auth.user?.height ?? 170)

const bodyComp = computed(() => {
  const r = latest.value
  if (!r) return []
  return [
    { label: '体脂率', value: r.bodyFat, max: 45, unit: '%', color: '#3b82c4', note: r.bodyFat < 25 ? '处于健康区间' : '偏高，需关注' },
    { label: '水分率', value: r.waterRate, max: 70, unit: '%', color: '#06b6d4', note: r.waterRate >= 50 ? '水分充足' : '建议多喝水' },
    { label: '肌肉率', value: r.muscleRate, max: 55, unit: '%', color: '#f4b740', note: assessMuscle(r.boneMuscle, r.weight, gender.value) },
    { label: '内脏脂肪', value: r.visceralFat, max: 20, unit: '级', color: '#e5484d', note: assessVisceralFat(r.visceralFat) },
  ]
})

onMounted(async () => {
  if (!auth.user) return
  const r = await api.getLatestRecord(auth.user.username)
  latest.value = r
  if (r) {
    dims.value = healthScoreBreakdown(r.bmi, r.bodyFat, r.visceralFat, r.muscleRate, r.waterRate, gender.value)
    score.value = calcHealthScore(r.bmi, r.bodyFat, r.visceralFat, r.muscleRate, r.waterRate, gender.value)
  }
})
</script>

<template>
  <div class="an" v-if="latest">
    <div class="an-grid">
      <!-- 体质总览 -->
      <section class="card overview">
        <div class="card-head"><h3>体质总览</h3></div>
        <div class="ov-top">
          <ProgressRing :value="score" :size="138" :stroke="12" label="综合评分" :color="score >= 75 ? 'var(--primary)' : score >= 60 ? 'var(--gold)' : 'var(--accent)'" />
          <div class="ov-tags">
            <StatPill :text="`BMI ${latest.bmi} · ${classifyBMI(latest.bmi)}`" tone="primary" />
            <StatPill :text="latest.bodyType" tone="accent" />
            <StatPill :text="`身体年龄 ${latest.bodyAge}岁`" tone="gold" />
            <StatPill :text="`腰臀比 ${assessWHtR(latest.waist, height)}`" :tone="assessWHtR(latest.waist, height).includes('肥胖') ? 'danger' : 'info'" />
            <StatPill :text="classifyBodyShape(latest.waist, gender)" :tone="classifyBodyShape(latest.waist, gender).includes('苹果') ? 'warn' : 'neutral'" />
          </div>
        </div>
        <p class="ov-tip">
          你的综合评分为 <b>{{ score }}</b> 分（{{ scoreLevel(score) }}）。
          {{ score >= 75 ? '身体指标整体均衡，继续保持当前生活方式。' : score >= 60 ? '部分指标尚有优化空间，可针对性调整饮食与运动。' : '多项指标偏离健康区间，建议制定改善计划。' }}
        </p>
      </section>

      <!-- 体成分 -->
      <section class="card comp">
        <div class="card-head"><h3>体成分构成</h3><span class="muted">相对上限占比</span></div>
        <div class="comp-list">
          <BarMeter v-for="b in bodyComp" :key="b.label" :label="b.label" :value="b.value" :max="b.max" :unit="b.unit" :color="b.color" :note="b.note" />
        </div>
      </section>
    </div>

    <!-- 五维评分 -->
    <section class="card dims">
      <div class="card-head"><h3>健康评分五维拆解</h3><span class="muted">满分 100</span></div>
      <div class="dims-grid">
        <div v-for="d in dims" :key="d.label" class="dim">
          <div class="dim-top"><span>{{ d.label }}</span><b class="tnum">{{ d.score }}/{{ d.max }}</b></div>
          <div class="dim-track"><div class="dim-fill" :style="{ width: (d.score / d.max * 100) + '%' }" /></div>
        </div>
      </div>
    </section>
  </div>

  <EmptyState v-else title="还没有可分析的数据" desc="完成一次打卡后，这里会给出体成分与体质解读。" icon="🔬" />
</template>

<style scoped>
.an { display: grid; gap: var(--s-6); }
.an-grid { display: grid; grid-template-columns: 1fr 1fr; gap: var(--s-6); align-items: start; }
.card { padding: var(--s-6); }
.card-head { display: flex; justify-content: space-between; align-items: baseline; margin-bottom: var(--s-5); }
.card-head h3 { font-size: 17px; }
.ov-top { display: flex; gap: var(--s-6); align-items: center; flex-wrap: wrap; }
.ov-tags { display: flex; flex-wrap: wrap; gap: 8px; flex: 1; min-width: 200px; }
.ov-tip { margin: var(--s-5) 0 0; font-size: 13.5px; color: var(--text-soft); line-height: 1.7; background: var(--primary-ll); padding: var(--s-4); border-radius: var(--r-md); }
.ov-tip b { color: var(--primary-d); }

.comp-list { display: grid; gap: var(--s-5); }
.dims-grid { display: grid; grid-template-columns: repeat(5, 1fr); gap: var(--s-5); }
.dim { display: grid; gap: 8px; }
.dim-top { display: flex; justify-content: space-between; font-size: 13px; }
.dim-top span { color: var(--text-soft); font-weight: 600; }
.dim-top b { font-family: var(--font-display); }
.dim-track { height: 8px; background: var(--surface-2); border-radius: 99px; overflow: hidden; }
.dim-fill { height: 100%; border-radius: 99px; background: linear-gradient(90deg, var(--primary), var(--accent)); transition: width 0.9s var(--ease-out); }

@media (max-width: 920px) { .an-grid { grid-template-columns: 1fr; } .dims-grid { grid-template-columns: 1fr 1fr; } }
@media (max-width: 520px) { .dims-grid { grid-template-columns: 1fr; } }
</style>
