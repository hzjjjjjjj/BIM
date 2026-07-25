<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { auth } from '@/stores/auth'
import { api } from '@/services/api'
import BodyModel3D, { type BodyProfile } from '@/components/BodyModel3D.vue'
import EmptyState from '@/components/EmptyState.vue'
import {
  calcHealthScore, classifyBMI, scoreLevel, classifyBodyShape, assessVisceralFat,
} from '@/lib/health'
import type { HealthRecord } from '@/types'

const latest = ref<HealthRecord | undefined>()
const profile = ref<BodyProfile | null>(null)
const showFat = ref(false)
const autoRotate = ref(true)
const bodyRef = ref<InstanceType<typeof BodyModel3D> | null>(null)

const gender = computed(() => auth.user?.gender ?? '男')
const score = computed(() => {
  const r = latest.value
  if (!r) return 0
  return calcHealthScore(r.bmi, r.bodyFat, r.visceralFat, r.muscleRate, r.waterRate, gender.value)
})

const chips = computed(() => {
  const r = latest.value
  if (!r) return []
  return [
    { label: 'BMI', value: `${r.bmi}`, sub: classifyBMI(r.bmi) },
    { label: '体重', value: `${r.weight}kg`, sub: profile.value?.bodyType ?? '' },
    { label: '体脂率', value: `${r.bodyFat}%`, sub: r.bodyFat < 25 ? '健康区间' : '偏高' },
    { label: '腰围', value: `${r.waist}cm`, sub: classifyBodyShape(r.waist, gender.value) },
    { label: '健康评分', value: `${score.value}`, sub: scoreLevel(score.value) },
  ]
})

const comp = computed(() => {
  const r = latest.value
  if (!r) return []
  return [
    { label: '体脂率', value: r.bodyFat, max: 45, unit: '%', color: 'var(--info)', note: r.bodyFat < 25 ? '健康区间' : '偏高，需关注' },
    { label: '水分率', value: r.waterRate, max: 70, unit: '%', color: 'var(--primary)', note: r.waterRate >= 50 ? '水分充足' : '建议多喝水' },
    { label: '肌肉率', value: r.muscleRate, max: 55, unit: '%', color: 'var(--gold)', note: '维持运动' },
    { label: '内脏脂肪', value: r.visceralFat, max: 20, unit: '级', color: 'var(--danger)', note: assessVisceralFat(r.visceralFat) },
  ]
})

function resetView(): void {
  bodyRef.value?.resetView()
}

onMounted(async () => {
  if (!auth.user) return
  const r = await api.getLatestRecord(auth.user.username)
  latest.value = r
  if (r) {
    profile.value = {
      gender: gender.value,
      heightCm: auth.user.height,
      weightKg: r.weight,
      bmi: r.bmi,
      bodyFat: r.bodyFat,
      waistCm: r.waist,
      muscleRate: r.muscleRate,
      bodyType: r.bodyType,
      bodyAge: r.bodyAge,
    }
  }
})
</script>

<template>
  <div class="bm" v-if="profile">
    <!-- 3D 舞台 -->
    <section class="bm-stage">
      <div class="bm-canvas">
        <BodyModel3D ref="bodyRef" :profile="profile" :show-fat="showFat" :auto-rotate="autoRotate" />
      </div>

      <!-- 指标 HUD -->
      <div class="bm-hud">
        <div v-for="c in chips" :key="c.label" class="hud-chip">
          <div class="hud-label">{{ c.label }}</div>
          <div class="hud-value tnum">{{ c.value }}</div>
          <div class="hud-sub">{{ c.sub }}</div>
        </div>
      </div>

      <!-- 控制台 -->
      <div class="bm-controls">
        <button class="ctl" :class="{ on: autoRotate }" @click="autoRotate = !autoRotate">↻ 自动旋转</button>
        <button class="ctl" :class="{ on: showFat }" @click="showFat = !showFat">🔥 体脂层</button>
        <button class="ctl" @click="resetView">⤢ 重置视角</button>
      </div>

      <div class="bm-tip">拖拽旋转 · 滚轮缩放 · 体脂层以杏橘色高亮脂肪分布</div>
    </section>

    <!-- 体成分面板 -->
    <aside class="bm-side">
      <section class="card">
        <div class="card-head"><h3>体成分构成</h3><span class="muted">相对上限占比</span></div>
        <div class="comp-list">
          <div v-for="b in comp" :key="b.label" class="comp-row">
            <div class="comp-top"><span>{{ b.label }}</span><b class="tnum">{{ b.value }}{{ b.unit }}</b></div>
            <div class="comp-track">
              <div class="comp-fill" :style="{ width: Math.min(100, (b.value / b.max) * 100) + '%', background: b.color }" />
            </div>
            <div class="comp-note">{{ b.note }}</div>
          </div>
        </div>
      </section>

      <section class="card legend">
        <div class="card-head"><h3>图例</h3></div>
        <div class="lg"><span class="dot skin" /> 人体孪生（按身高/体重/BMI/腰围生成）</div>
        <div class="lg"><span class="dot fat" /> 体脂层（体脂率越高越明显）</div>
        <p class="lg-tip">模型为参数化三维体态，反映当前记录下的体型趋势，非医学影像。</p>
      </section>
    </aside>
  </div>

  <EmptyState v-else title="还没有可建模的数据" desc="完成一次打卡后，这里会生成你的三维体质孪生。" icon="🧍" />
</template>

<style scoped>
.bm { display: grid; grid-template-columns: 1fr 340px; gap: var(--s-6); align-items: stretch; }
.bm-stage {
  position: relative; min-height: 560px; height: calc(100vh - 220px);
  border-radius: var(--r-xl); overflow: hidden;
  background:
    radial-gradient(120% 90% at 50% 8%, #ffffff 0%, #f3efe6 45%, #e9e2d4 100%);
  border: 1px solid var(--border);
  box-shadow: var(--sh-2);
}
.bm-canvas { position: absolute; inset: 0; }

.bm-hud { position: absolute; top: var(--s-5); left: var(--s-5); display: flex; flex-wrap: wrap; gap: 10px; max-width: 70%; }
.hud-chip {
  background: rgba(255, 255, 255, 0.72); backdrop-filter: blur(10px);
  border: 1px solid var(--border); border-radius: var(--r-md);
  padding: 9px 13px; min-width: 92px; box-shadow: var(--sh-1);
}
.hud-label { font-size: 11px; color: var(--text-soft); font-weight: 700; letter-spacing: 0.04em; }
.hud-value { font-family: var(--font-display); font-size: 19px; font-weight: 800; color: var(--text); margin: 1px 0; }
.hud-sub { font-size: 11px; color: var(--primary-d); font-weight: 600; }

.bm-controls {
  position: absolute; bottom: var(--s-5); left: 50%; transform: translateX(-50%);
  display: flex; gap: 8px; padding: 8px; border-radius: var(--r-pill);
  background: rgba(255, 255, 255, 0.78); backdrop-filter: blur(12px);
  border: 1px solid var(--border); box-shadow: var(--sh-2);
}
.ctl {
  border: 1px solid transparent; background: transparent; color: var(--text-soft);
  font-weight: 700; font-size: 13px; padding: 9px 15px; border-radius: var(--r-pill);
  cursor: pointer; transition: all 0.2s var(--ease-out);
}
.ctl:hover { background: var(--surface-2); color: var(--text); }
.ctl.on { background: var(--primary); color: #fff; box-shadow: 0 6px 16px -8px rgba(31, 138, 109, 0.8); }

.bm-tip {
  position: absolute; bottom: var(--s-5); right: var(--s-5);
  font-size: 11.5px; color: var(--text-faint); background: rgba(255, 255, 255, 0.7);
  padding: 6px 11px; border-radius: var(--r-pill); border: 1px solid var(--border);
}

.bm-side { display: grid; gap: var(--s-6); align-content: start; }
.card { padding: var(--s-6); }
.card-head { display: flex; justify-content: space-between; align-items: baseline; margin-bottom: var(--s-5); }
.card-head h3 { font-size: 17px; }

.comp-list { display: grid; gap: var(--s-5); }
.comp-row { display: grid; gap: 6px; }
.comp-top { display: flex; justify-content: space-between; font-size: 13px; }
.comp-top span { color: var(--text-soft); font-weight: 600; }
.comp-top b { font-family: var(--font-display); }
.comp-track { height: 8px; background: var(--surface-2); border-radius: 99px; overflow: hidden; }
.comp-fill { height: 100%; border-radius: 99px; transition: width 0.9s var(--ease-out); }
.comp-note { font-size: 11.5px; color: var(--text-faint); }

.legend .lg { display: flex; align-items: center; gap: 9px; font-size: 13px; color: var(--text-soft); margin-bottom: 10px; }
.dot { width: 14px; height: 14px; border-radius: 5px; flex: none; }
.dot.skin { background: linear-gradient(140deg, #e9dcc8, #d9c6ad); border: 1px solid var(--border-strong); }
.dot.fat { background: var(--accent); }
.lg-tip { font-size: 11.5px; color: var(--text-faint); line-height: 1.6; margin: 4px 0 0; }

@media (max-width: 980px) {
  .bm { grid-template-columns: 1fr; }
  .bm-stage { height: 60vh; min-height: 420px; }
}
</style>
