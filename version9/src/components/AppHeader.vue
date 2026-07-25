<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { auth } from '@/stores/auth'
import { api } from '@/services/api'
import { calcHealthScore, scoreLevel } from '@/lib/health'
import type { HealthRecord } from '@/types'

defineProps<{ title: string }>()
const emit = defineEmits<{ (e: 'toggle-menu'): void }>()
const router = useRouter()
const route = useRoute()

const record = ref<HealthRecord | undefined>()
const checked = ref(false)

const score = computed(() => {
  const r = record.value
  if (!r || !auth.user) return null
  return calcHealthScore(r.bmi, r.bodyFat, r.visceralFat, r.muscleRate, r.waterRate, auth.user.gender)
})
const level = computed(() => (score.value == null ? '' : scoreLevel(score.value)))

async function load() {
  if (!auth.user) return
  record.value = await api.getLatestRecord(auth.user.username)
  checked.value = await api.isCheckedToday(auth.user.username)
}

function logout() {
  auth.logout()
  router.push({ name: 'login' })
}

onMounted(load)
</script>

<template>
  <header class="hd">
    <button class="hd-menu" aria-label="菜单" @click="emit('toggle-menu')">☰</button>
    <div class="hd-title">
      <h1>{{ title }}</h1>
      <p class="hd-sub">{{ route.meta.title ? '健康 · ' + (route.meta.title as string) : '健康管理' }}</p>
    </div>

    <div class="hd-right">
      <div v-if="score != null" class="hd-score" :title="`健康评分 ${score}/100`">
        <span class="hd-score-num tnum">{{ score }}</span>
        <span class="hd-score-lv">{{ level }}</span>
      </div>
      <div class="hd-check" :class="{ on: checked }">
        {{ checked ? '已打卡 ✓' : '未打卡' }}
      </div>
      <button class="hd-out" @click="logout" aria-label="退出登录">退出</button>
    </div>
  </header>
</template>

<style scoped>
.hd {
  display: flex; align-items: center; gap: var(--s-4);
  padding: var(--s-4) var(--s-6);
  background: var(--surface);
  border-bottom: 1px solid var(--border);
  position: sticky; top: 0; z-index: 30;
}
.hd-menu { display: none; border: none; background: none; font-size: 20px; cursor: pointer; color: var(--text); }
.hd-title h1 { font-size: 20px; }
.hd-sub { font-size: 12.5px; color: var(--text-faint); margin: 2px 0 0; }
.hd-right { margin-left: auto; display: flex; align-items: center; gap: var(--s-3); }
.hd-score {
  display: flex; align-items: baseline; gap: 6px;
  background: var(--primary-l); color: var(--primary-d);
  padding: 7px 13px; border-radius: var(--r-pill); font-weight: 800;
}
.hd-score-num { font-family: var(--font-display); font-size: 17px; }
.hd-score-lv { font-size: 12px; font-weight: 700; }
.hd-check {
  font-size: 12.5px; font-weight: 700; color: var(--text-faint);
  padding: 7px 12px; border-radius: var(--r-pill); background: var(--surface-2);
}
.hd-check.on { color: var(--primary-d); background: var(--primary-l); }
.hd-out {
  border: 1px solid var(--border); background: var(--surface); color: var(--text-soft);
  padding: 7px 14px; border-radius: var(--r-pill); font-weight: 700; font-size: 13px; cursor: pointer;
  transition: all 0.2s var(--ease-out);
}
.hd-out:hover { border-color: var(--danger); color: var(--danger); }

@media (max-width: 860px) {
  .hd-menu { display: block; }
  .hd-sub { display: none; }
  .hd-check { display: none; }
}
</style>
