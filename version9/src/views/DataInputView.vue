<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { auth } from '@/stores/auth'
import { api } from '@/services/api'
import { calcAvgBMR, calcBMI, calcBodyAge, calcTDEE, classifyBodyType, classifyBMI, round } from '@/lib/health'
import { toast } from '@/stores/toast'
import StatPill from '@/components/StatPill.vue'
import type { HealthRecord } from '@/types'

const form = reactive({
  recordDate: new Date().toISOString().slice(0, 10),
  weight: 68.5, bodyFat: 21, waterRate: 58, muscleRate: 40,
  visceralFat: 5, boneMuscle: 28.8, waist: 80,
})

const preview = computed(() => {
  const u = auth.user!
  const bmi = calcBMI(form.weight, u.height)
  const bmr = calcAvgBMR(form.weight, u.height, u.age, u.gender)
  const tdee = calcTDEE(bmr, u.activityLevel)
  const bodyAge = calcBodyAge(u.age, form.bodyFat, form.muscleRate, form.visceralFat, u.gender)
  const bodyType = classifyBodyType(bmi, form.bodyFat, u.gender)
  return {
    bmi: round(bmi, 2), bmr: round(bmr, 1), tdee: round(tdee, 1), bodyAge, bodyType,
    bmiClass: classifyBMI(bmi),
  }
})

const recent = ref<HealthRecord[]>([])

async function refresh() {
  if (!auth.user) return
  recent.value = (await api.getRecords(auth.user.username, 8)).slice().reverse()
}

async function submit() {
  if (!auth.user) return
  const u = auth.user
  const rec: HealthRecord = {
    username: u.username,
    recordDate: form.recordDate,
    weight: form.weight, bodyFat: form.bodyFat, waterRate: form.waterRate,
    muscleRate: form.muscleRate, visceralFat: form.visceralFat, boneMuscle: form.boneMuscle,
    waist: form.waist,
    bmi: preview.value.bmi, bmr: preview.value.bmr, tdee: preview.value.tdee,
    bodyAge: preview.value.bodyAge, bodyType: preview.value.bodyType,
  }
  await api.addRecord(rec)
  toast.success('打卡成功 · ' + form.recordDate)
  refresh()
}

const fields = [
  { key: 'weight', label: '体重', unit: 'kg', min: 30, max: 250, step: 0.1 },
  { key: 'bodyFat', label: '体脂率', unit: '%', min: 3, max: 60, step: 0.1 },
  { key: 'waterRate', label: '水分率', unit: '%', min: 30, max: 80, step: 0.1 },
  { key: 'muscleRate', label: '肌肉率', unit: '%', min: 20, max: 60, step: 0.1 },
  { key: 'visceralFat', label: '内脏脂肪', unit: '级', min: 1, max: 30, step: 1 },
  { key: 'boneMuscle', label: '骨骼肌肉', unit: 'kg', min: 5, max: 50, step: 0.1 },
  { key: 'waist', label: '腰围', unit: 'cm', min: 40, max: 180, step: 0.1 },
] as const

onMounted(refresh)
</script>

<template>
  <div class="inp">
    <div class="inp-grid">
      <!-- 录入表单 -->
      <section class="card form-card">
        <div class="card-head">
          <h3>每日健康打卡</h3>
          <span class="muted">数据由体脂秤测量</span>
        </div>
        <label class="field date">
          <span>测量日期</span>
          <input type="date" v-model="form.recordDate" />
        </label>
        <div class="field-grid">
          <label v-for="f in fields" :key="f.key" class="field">
            <span>{{ f.label }} <em>({{ f.unit }})</em></span>
            <input type="number" v-model.number="(form as any)[f.key]" :min="f.min" :max="f.max" :step="f.step" />
          </label>
        </div>
        <button class="submit" @click="submit">保存打卡记录</button>
      </section>

      <!-- 自动派生预览 -->
      <section class="card preview">
        <div class="card-head"><h3>系统自动计算</h3></div>
        <div class="pv-bmi">
          <div class="pv-big tnum">{{ preview.bmi }}</div>
          <div class="pv-bmi-meta">
            <div class="pv-bmi-label">BMI</div>
            <StatPill :text="preview.bmiClass" tone="primary" />
          </div>
        </div>
        <div class="pv-list">
          <div class="pv-row"><span>基础代谢 BMR</span><b class="tnum">{{ preview.bmr }} kcal</b></div>
          <div class="pv-row"><span>每日消耗 TDEE</span><b class="tnum">{{ preview.tdee }} kcal</b></div>
          <div class="pv-row"><span>身体年龄</span><b class="tnum">{{ preview.bodyAge }} 岁</b></div>
          <div class="pv-row"><span>体质分类</span><b>{{ preview.bodyType }}</b></div>
        </div>
        <p class="pv-note">TDEE 依据你的活动水平「{{ auth.user?.activityLevel }}」估算。</p>
      </section>
    </div>

    <!-- 最近记录 -->
    <section class="card recent">
      <div class="card-head"><h3>最近记录</h3><span class="muted">最新 8 条</span></div>
      <div class="table-wrap">
        <table>
          <thead>
            <tr><th>日期</th><th>体重</th><th>BMI</th><th>体脂</th><th>肌肉率</th><th>内脏脂肪</th><th>体质</th></tr>
          </thead>
          <tbody>
            <tr v-for="r in recent" :key="r.id">
              <td>{{ r.recordDate }}</td>
              <td class="tnum">{{ r.weight }}</td>
              <td class="tnum">{{ r.bmi }}</td>
              <td class="tnum">{{ r.bodyFat }}%</td>
              <td class="tnum">{{ r.muscleRate }}%</td>
              <td class="tnum">{{ r.visceralFat }}</td>
              <td>{{ r.bodyType }}</td>
            </tr>
            <tr v-if="!recent.length"><td colspan="7" class="empty-cell">暂无记录</td></tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>

<style scoped>
.inp { display: grid; gap: var(--s-6); }
.inp-grid { display: grid; grid-template-columns: 1.4fr 1fr; gap: var(--s-6); align-items: start; }
.card-head { display: flex; justify-content: space-between; align-items: baseline; margin-bottom: var(--s-5); }
.card-head h3 { font-size: 17px; }
.field { display: grid; gap: 6px; }
.field > span { font-size: 12.5px; font-weight: 700; color: var(--text-soft); }
.field em { font-style: normal; color: var(--text-faint); font-weight: 500; }
.field input, .field select {
  border: 1.5px solid var(--border); background: var(--surface); border-radius: var(--r-md);
  padding: 10px 12px; font-size: 14.5px; transition: border-color 0.2s, box-shadow 0.2s;
}
.field input:focus { outline: none; border-color: var(--primary); box-shadow: 0 0 0 3px var(--primary-l); }
.field-grid { display: grid; grid-template-columns: 1fr 1fr; gap: var(--s-4); margin: var(--s-4) 0; }
.date { margin-bottom: var(--s-2); }
.submit {
  border: none; cursor: pointer; color: #fff; font-weight: 800; font-size: 15px; width: 100%;
  padding: 13px; border-radius: var(--r-md); letter-spacing: 0.05em;
  background: linear-gradient(135deg, var(--primary), var(--primary-d)); box-shadow: 0 10px 22px -10px rgba(31, 138, 109, 0.8);
  transition: transform 0.2s var(--ease-out);
}
.submit:hover { transform: translateY(-2px); }

.preview { padding: var(--s-6); background: linear-gradient(160deg, var(--surface), var(--primary-ll)); }
.pv-bmi { display: flex; align-items: center; gap: var(--s-5); padding-bottom: var(--s-5); border-bottom: 1px solid var(--border); }
.pv-big { font-family: var(--font-display); font-weight: 800; font-size: 52px; line-height: 1; color: var(--primary-d); }
.pv-bmi-meta { display: grid; gap: 6px; }
.pv-bmi-label { font-size: 13px; color: var(--text-soft); font-weight: 700; }
.pv-list { display: grid; gap: 12px; padding: var(--s-5) 0; }
.pv-row { display: flex; justify-content: space-between; align-items: baseline; font-size: 14px; }
.pv-row span { color: var(--text-soft); }
.pv-row b { font-weight: 700; }
.pv-note { font-size: 12.5px; color: var(--text-faint); margin: 0; }

.recent { padding: var(--s-6); }
.table-wrap { overflow-x: auto; }
table { width: 100%; border-collapse: collapse; font-size: 13.5px; }
th { text-align: left; color: var(--text-faint); font-weight: 700; font-size: 12px; padding: 10px 12px; border-bottom: 1px solid var(--border); }
td { padding: 11px 12px; border-bottom: 1px solid var(--surface-2); }
tbody tr:hover { background: var(--primary-ll); }
.empty-cell { text-align: center; color: var(--text-faint); padding: 24px; }

@media (max-width: 920px) { .inp-grid { grid-template-columns: 1fr; } }
@media (max-width: 520px) { .field-grid { grid-template-columns: 1fr; } }
</style>
