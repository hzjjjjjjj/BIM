<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { auth } from '@/stores/auth'
import { api } from '@/services/api'
import { calcTDEE, calcAvgBMR, predictGoalDays } from '@/lib/health'
import { toast } from '@/stores/toast'
import StatPill from '@/components/StatPill.vue'
import EmptyState from '@/components/EmptyState.vue'
import type { Goal, GoalType, HealthRecord } from '@/types'

const goalTypes: GoalType[] = ['减脂', '减重', '增肌', '塑形', '保持']
const form = reactive({ goalType: '减脂' as GoalType, targetValue: 66, endDate: '' })
const goals = ref<Goal[]>([])
const latest = ref<HealthRecord | undefined>()
const deficit = ref<number | null>(null)

async function refresh() {
  if (!auth.user) return
  const u = auth.user
  goals.value = await api.getGoals(u.username)
  latest.value = await api.getLatestRecord(u.username)
  if (latest.value) {
    const tdee = calcTDEE(calcAvgBMR(latest.value.weight, u.height, u.age, u.gender), u.activityLevel)
    const ex = await api.getTodayExerciseCalories(u.username)
    const diet = await api.getTodayDietSummary(u.username)
    deficit.value = Math.round(tdee + ex - diet[0])
  }
}

async function addGoal() {
  if (!auth.user || !latest.value) return toast.error('请先录入当前体重')
  await api.addGoal({
    username: auth.user.username, goalType: form.goalType,
    targetValue: form.targetValue, startDate: new Date().toISOString().slice(0, 10),
    endDate: form.endDate || undefined, currentStage: 1,
  })
  toast.success('目标已添加')
  refresh()
}

function daysFor(g: Goal): number {
  if (!latest.value || !canPredictFor(g.goalType) || deficit.value == null) return -2
  return predictGoalDays(latest.value.weight, g.targetValue, deficit.value, g.goalType)
}
function canPredictFor(t: GoalType) { return ['减脂', '减重', '增肌'].includes(t) }

function progress(g: Goal): number {
  if (!latest.value) return 0
  const cur = latest.value.weight
  const SPAN = 5 // 估算起点距目标 5kg，用于可视化进度
  const clamp = (v: number) => Math.max(0, Math.min(100, v))
  if (g.goalType === '增肌') {
    const base = g.targetValue - SPAN
    return clamp(((cur - base) / SPAN) * 100)
  }
  // 减脂 / 减重 / 塑形 / 保持：朝更小目标
  const base = g.targetValue + SPAN
  return clamp(((base - cur) / SPAN) * 100)
}

function dayText(d: number): string {
  if (d === 0) return '已达成 🎉'
  if (d === -1) return '难以达成'
  if (d === -2) return '热量差不足'
  return `约 ${d} 天`
}

onMounted(refresh)
</script>

<template>
  <div class="goal">
    <div class="g-grid">
      <!-- 新建目标 -->
      <section class="card new-goal">
        <div class="card-head"><h3>制定新目标</h3></div>
        <label class="field"><span>目标类型</span>
          <select v-model="form.goalType">
            <option v-for="t in goalTypes" :key="t">{{ t }}</option>
          </select>
        </label>
        <label class="field"><span>目标数值（kg）</span>
          <input type="number" step="0.1" v-model.number="form.targetValue" />
        </label>
        <label class="field"><span>期望完成日期（可选）</span>
          <input type="date" v-model="form.endDate" />
        </label>
        <div class="deficit" v-if="deficit != null">
          当前每日热量差 <b class="tnum">{{ deficit >= 0 ? '+' : '' }}{{ deficit }}</b> kcal
        </div>
        <button class="submit" @click="addGoal">添加目标</button>
      </section>

      <!-- 目标列表 -->
      <section class="card list">
        <div class="card-head"><h3>我的目标</h3><span class="muted">{{ goals.length }} 个</span></div>
        <div v-if="goals.length" class="goal-list">
          <div v-for="g in goals" :key="g.id" class="g-item">
            <div class="g-item-top">
              <span class="g-type">{{ g.goalType }}</span>
              <StatPill :text="dayText(daysFor(g))"
                :tone="daysFor(g) === 0 ? 'primary' : daysFor(g) > 0 ? 'gold' : 'warn'" />
            </div>
            <div class="g-target">目标 <b class="tnum">{{ g.targetValue }}</b> kg
              <span class="muted" v-if="latest"> · 当前 {{ latest.weight }} kg</span>
            </div>
            <div class="g-track"><div class="g-fill" :style="{ width: progress(g) + '%' }" /></div>
            <div class="g-meta" v-if="g.endDate">期望 {{ g.endDate }} 前完成</div>
          </div>
        </div>
        <EmptyState v-else title="还没有目标" desc="设定第一个健康目标，系统会估算达成天数。" icon="🎯" />
      </section>
    </div>
  </div>
</template>

<style scoped>
.goal { display: grid; gap: var(--s-6); }
.g-grid { display: grid; grid-template-columns: 360px 1fr; gap: var(--s-6); align-items: start; }
.card { padding: var(--s-6); }
.card-head { display: flex; justify-content: space-between; align-items: baseline; margin-bottom: var(--s-5); }
.card-head h3 { font-size: 17px; }
.field { display: grid; gap: 6px; margin-bottom: var(--s-4); }
.field > span { font-size: 12.5px; font-weight: 700; color: var(--text-soft); }
.field input, .field select {
  border: 1.5px solid var(--border); background: var(--surface); border-radius: var(--r-md);
  padding: 10px 12px; font-size: 14.5px;
}
.field input:focus, .field select:focus { outline: none; border-color: var(--primary); box-shadow: 0 0 0 3px var(--primary-l); }
.deficit { font-size: 13px; color: var(--text-soft); margin-bottom: var(--s-4); }
.deficit b { color: var(--primary-d); }
.submit {
  border: none; cursor: pointer; color: #fff; font-weight: 800; width: 100%; padding: 12px; border-radius: var(--r-md);
  background: linear-gradient(135deg, var(--primary), var(--primary-d)); box-shadow: 0 10px 22px -10px rgba(31, 138, 109, 0.8);
  transition: transform 0.2s var(--ease-out);
}
.submit:hover { transform: translateY(-2px); }

.goal-list { display: grid; gap: var(--s-4); }
.g-item { border: 1px solid var(--border); border-radius: var(--r-lg); padding: var(--s-5); background: var(--surface-2); }
.g-item-top { display: flex; justify-content: space-between; align-items: center; }
.g-type { font-family: var(--font-display); font-weight: 800; font-size: 16px; color: var(--text); }
.g-target { font-size: 13.5px; margin: 8px 0; }
.g-target b { font-size: 15px; }
.g-track { height: 9px; background: var(--surface); border-radius: 99px; overflow: hidden; border: 1px solid var(--border); }
.g-fill { height: 100%; border-radius: 99px; background: linear-gradient(90deg, var(--primary), var(--accent)); transition: width 0.9s var(--ease-out); }
.g-meta { font-size: 12px; color: var(--text-faint); margin-top: 7px; }

@media (max-width: 860px) { .g-grid { grid-template-columns: 1fr; } }
</style>
