<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { auth } from '@/stores/auth'
import { api } from '@/services/api'
import type { Achievement } from '@/types'

interface Badge { name: string; icon: string; desc: string }

const CATALOG: Badge[] = [
  { name: '初次打卡', icon: '🌱', desc: '完成第一次健康打卡' },
  { name: '坚持一周', icon: '🔥', desc: '累计记录达到 7 天' },
  { name: '月度达人', icon: '📅', desc: '累计记录达到 30 天' },
  { name: '健康达人', icon: '💎', desc: '健康评分达到 90 分以上' },
  { name: '减脂达标', icon: '🏃', desc: '达成一个减脂目标' },
  { name: '饮食管家', icon: '🥗', desc: '记录满 20 条饮食' },
  { name: '运动达人', icon: '⚡', desc: '累计运动消耗 1000 kcal' },
  { name: '目标达成', icon: '🎯', desc: '完成任意一个阶段目标' },
]

const earned = ref<Achievement[]>([])
const records = ref<any[]>([])
const diet = ref<any[]>([])
const exercise = ref<any[]>([])

const earnedSet = computed(() => new Set(earned.value.map((a) => a.badgeName)))
const badges = computed(() => CATALOG.map((b) => ({ ...b, got: earnedSet.value.has(b.name) })))
const earnedCount = computed(() => badges.value.filter((b) => b.got).length)

const stats = computed(() => ({
  days: new Set(records.value.map((r) => r.recordDate)).size,
  diet: diet.value.length,
  exCal: exercise.value.reduce((s: number, e: any) => s + (e.caloriesBurned || 0), 0),
}))

onMounted(async () => {
  if (!auth.user) return
  const u = auth.user.username
  earned.value = await api.getAchievements(u)
  records.value = await api.getRecords(u)
  diet.value = await api.getDiet(u)
  exercise.value = await api.getExercise(u)
})
</script>

<template>
  <div class="ach">
    <section class="card summary">
      <div class="sum-item"><div class="sum-v tnum">{{ earnedCount }}/{{ CATALOG.length }}</div><div class="sum-l">已解锁徽章</div></div>
      <div class="sum-item"><div class="sum-v tnum">{{ stats.days }}</div><div class="sum-l">打卡天数</div></div>
      <div class="sum-item"><div class="sum-v tnum">{{ stats.diet }}</div><div class="sum-l">饮食记录</div></div>
      <div class="sum-item"><div class="sum-v tnum">{{ stats.exCal }}</div><div class="sum-l">运动消耗(kcal)</div></div>
    </section>

    <section class="card wall">
      <div class="card-head"><h3>成就墙</h3><span class="muted">坚持，是最好的投资</span></div>
      <div class="badges">
        <div v-for="b in badges" :key="b.name" class="badge" :class="{ got: b.got }">
          <div class="badge-ic">{{ b.icon }}</div>
          <div class="badge-name">{{ b.name }}</div>
          <div class="badge-desc">{{ b.desc }}</div>
          <div class="badge-state">{{ b.got ? '已解锁' : '未解锁' }}</div>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.ach { display: grid; gap: var(--s-6); }
.card { padding: var(--s-6); }
.card-head { display: flex; justify-content: space-between; align-items: baseline; margin-bottom: var(--s-5); }
.card-head h3 { font-size: 17px; }
.summary { display: grid; grid-template-columns: repeat(4, 1fr); gap: var(--s-4); background: linear-gradient(120deg, var(--ink), var(--ink-2)); color: #fff; border: none; }
.sum-item { text-align: center; }
.sum-v { font-family: var(--font-display); font-weight: 800; font-size: 30px; color: #fff; }
.sum-l { font-size: 12.5px; color: var(--ink-text-soft); margin-top: 4px; }

.badges { display: grid; grid-template-columns: repeat(4, 1fr); gap: var(--s-4); }
.badge {
  border: 1.5px dashed var(--border); border-radius: var(--r-lg); padding: var(--s-5);
  text-align: center; display: grid; gap: 6px; background: var(--surface-2); opacity: 0.72;
  transition: all 0.25s var(--ease-out);
}
.badge.got { border-style: solid; border-color: var(--primary); background: linear-gradient(160deg, var(--primary-ll), var(--surface)); opacity: 1; box-shadow: var(--sh-1); }
.badge-ic { font-size: 34px; filter: grayscale(0.3); }
.badge.got .badge-ic { filter: none; }
.badge-name { font-family: var(--font-display); font-weight: 800; font-size: 15px; }
.badge-desc { font-size: 12px; color: var(--text-soft); line-height: 1.5; }
.badge-state { font-size: 11.5px; font-weight: 700; color: var(--text-faint); margin-top: 2px; }
.badge.got .badge-state { color: var(--primary-d); }

@media (max-width: 920px) { .badges { grid-template-columns: repeat(2, 1fr); } .summary { grid-template-columns: repeat(2, 1fr); } }
@media (max-width: 480px) { .badges { grid-template-columns: 1fr; } }
</style>
