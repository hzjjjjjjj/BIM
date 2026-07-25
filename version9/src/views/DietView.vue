<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { auth } from '@/stores/auth'
import { api } from '@/services/api'
import { calcTDEE, calcAvgBMR } from '@/lib/health'
import { toast } from '@/stores/toast'
import DonutChart from '@/components/DonutChart.vue'
import StatPill from '@/components/StatPill.vue'
import EmptyState from '@/components/EmptyState.vue'
import type { DietRecord, Food, MealType } from '@/types'

const meals: MealType[] = ['早餐', '午餐', '晚餐', '加餐']
const foods = ref<Food[]>([])
const today = ref<DietRecord[]>([])
const latest = ref<any>()

const search = ref('')
const showFoods = ref(false)
const picked = ref<Food | null>(null)
const form = reactive({ mealType: '早餐' as MealType, foodName: '', calories: 0, protein: 0, carbs: 0, fat: 0 })

const filtered = computed(() => {
  const q = search.value.trim()
  if (!q) return foods.value.slice(0, 8)
  return foods.value.filter((f) => f.foodName.includes(q)).slice(0, 8)
})

const summary = ref<number[]>([0, 0, 0, 0])
const [cal, p, c, f] = [() => summary.value[0], () => summary.value[1], () => summary.value[2], () => summary.value[3]]

const tdee = computed(() => {
  if (!auth.user || !latest.value) return 0
  return Math.round(calcTDEE(calcAvgBMR(latest.value.weight, auth.user.height, auth.user.age, auth.user.gender), auth.user.activityLevel))
})
const remaining = computed(() => tdee.value - summary.value[0])

const macroSlices = computed(() => {
  const prot = p() * 4, carb = c() * 4, fat = f() * 9
  return [
    { label: '蛋白质', value: prot, color: '#3b82c4' },
    { label: '碳水', value: carb, color: '#f4b740' },
    { label: '脂肪', value: fat, color: '#ff7a5c' },
  ]
})

function pickFood(fd: Food) {
  picked.value = fd
  form.foodName = fd.foodName
  form.calories = fd.calories
  form.protein = fd.protein
  form.carbs = fd.carbs
  form.fat = fd.fat
  search.value = fd.foodName
  showFoods.value = false
}

async function addDiet() {
  if (!auth.user) return
  if (!form.foodName) return toast.error('请选择或输入食物')
  const rec: DietRecord = {
    username: auth.user.username, recordDate: new Date().toISOString().slice(0, 10),
    mealType: form.mealType, foodName: form.foodName,
    calories: form.calories, protein: form.protein, carbs: form.carbs, fat: form.fat,
  }
  await api.addDiet(rec)
  toast.success(`已添加 ${form.foodName}`)
  form.foodName = ''; form.calories = 0; form.protein = 0; form.carbs = 0; form.fat = 0
  search.value = ''; picked.value = null
  refresh()
}

function grouped(meal: MealType) { return today.value.filter((d) => d.mealType === meal) }

onMounted(async () => {
  if (!auth.user) return
  foods.value = await api.getFoods()
  latest.value = await api.getLatestRecord(auth.user.username)
  await refresh()
})

async function refresh() {
  if (!auth.user) return
  today.value = await api.getDiet(auth.user.username, 1)
  summary.value = await api.getTodayDietSummary(auth.user.username)
}
</script>

<template>
  <div class="diet">
    <div class="d-grid">
      <!-- 录入 -->
      <section class="card entry">
        <div class="card-head"><h3>添加饮食</h3></div>
        <label class="field"><span>餐次</span>
          <select v-model="form.mealType">
            <option v-for="m in meals" :key="m">{{ m }}</option>
          </select>
        </label>
        <label class="field"><span>食物（从库中选择或手动输入）</span>
          <div class="food-search">
            <input v-model="search" @focus="showFoods = true" @input="showFoods = true" placeholder="搜索食物，如：鸡胸肉" />
            <div v-if="showFoods && filtered.length" class="food-drop">
              <button v-for="fd in filtered" :key="fd.foodName" class="food-opt" @mousedown.prevent="pickFood(fd)">
                <span>{{ fd.foodName }}</span><span class="food-cal">{{ fd.calories }} kcal</span>
              </button>
            </div>
          </div>
        </label>
        <div class="mini-grid">
          <label class="field"><span>热量</span><input type="number" v-model.number="form.calories" /></label>
          <label class="field"><span>蛋白(g)</span><input type="number" v-model.number="form.protein" /></label>
          <label class="field"><span>碳水(g)</span><input type="number" v-model.number="form.carbs" /></label>
          <label class="field"><span>脂肪(g)</span><input type="number" v-model.number="form.fat" /></label>
        </div>
        <button class="submit" @click="addDiet">添加记录</button>
      </section>

      <!-- 今日概览 -->
      <section class="card today">
        <div class="card-head"><h3>今日营养</h3>
          <StatPill v-if="tdee" :text="`建议 ${tdee} kcal`" tone="info" />
        </div>
        <div class="today-body">
          <DonutChart :slices="macroSlices" :size="170" :thickness="20"
            :center-value="String(cal())" center-label="已摄入 kcal" />
          <div class="today-stats">
            <div class="ts"><span>蛋白质</span><b class="tnum">{{ p().toFixed(1) }} g</b></div>
            <div class="ts"><span>碳水</span><b class="tnum">{{ c().toFixed(1) }} g</b></div>
            <div class="ts"><span>脂肪</span><b class="tnum">{{ f().toFixed(1) }} g</b></div>
            <div class="ts hl"><span>剩余可摄入</span><b class="tnum" :class="remaining >= 0 ? 'ok' : 'over'">{{ remaining >= 0 ? remaining : remaining }} kcal</b></div>
          </div>
        </div>
      </section>
    </div>

    <!-- 今日饮食明细 -->
    <section class="card detail">
      <div class="card-head"><h3>今日饮食明细</h3><span class="muted">共 {{ today.length }} 条 · {{ cal() }} kcal</span></div>
      <div class="meals">
        <div v-for="m in meals" :key="m" class="meal">
          <div class="meal-h"><span>{{ m }}</span><span class="muted">{{ grouped(m).reduce((s, x) => s + x.calories, 0) }} kcal</span></div>
          <ul v-if="grouped(m).length">
            <li v-for="d in grouped(m)" :key="d.id">
              <span class="d-name">{{ d.foodName }}</span>
              <span class="d-cal tnum">{{ d.calories }} kcal</span>
            </li>
          </ul>
          <p v-else class="meal-empty">未记录</p>
        </div>
      </div>
      <EmptyState v-if="!today.length" title="今天还没吃东西记录" desc="从上面的表单添加你的第一餐吧。" icon="🥗" />
    </section>
  </div>
</template>

<style scoped>
.diet { display: grid; gap: var(--s-6); }
.d-grid { display: grid; grid-template-columns: 1fr 1fr; gap: var(--s-6); align-items: start; }
.card { padding: var(--s-6); }
.card-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: var(--s-5); }
.card-head h3 { font-size: 17px; }
.field { display: grid; gap: 6px; margin-bottom: var(--s-4); }
.field > span { font-size: 12.5px; font-weight: 700; color: var(--text-soft); }
.field input, .field select {
  border: 1.5px solid var(--border); background: var(--surface); border-radius: var(--r-md); padding: 10px 12px; font-size: 14.5px;
}
.field input:focus, .field select:focus { outline: none; border-color: var(--primary); box-shadow: 0 0 0 3px var(--primary-l); }
.food-search { position: relative; }
.food-drop { position: absolute; top: calc(100% + 4px); left: 0; right: 0; z-index: 20; background: var(--surface); border: 1px solid var(--border); border-radius: var(--r-md); box-shadow: var(--sh-3); overflow: hidden; }
.food-opt { display: flex; justify-content: space-between; width: 100%; border: none; background: none; padding: 10px 13px; cursor: pointer; font-size: 14px; }
.food-opt:hover { background: var(--primary-ll); }
.food-cal { color: var(--text-faint); font-size: 12.5px; }
.mini-grid { display: grid; grid-template-columns: 1fr 1fr; gap: var(--s-3); }
.submit { border: none; cursor: pointer; color: #fff; font-weight: 800; width: 100%; padding: 12px; border-radius: var(--r-md); background: linear-gradient(135deg, var(--primary), var(--primary-d)); box-shadow: 0 10px 22px -10px rgba(31, 138, 109, 0.8); transition: transform 0.2s var(--ease-out); }
.submit:hover { transform: translateY(-2px); }

.today-body { display: flex; align-items: center; gap: var(--s-6); flex-wrap: wrap; }
.today-stats { display: grid; gap: 12px; flex: 1; min-width: 180px; }
.ts { display: flex; justify-content: space-between; font-size: 14px; }
.ts span { color: var(--text-soft); }
.ts b { font-weight: 700; }
.ts.hl { border-top: 1px solid var(--border); padding-top: 10px; }
.ts.hl b.ok { color: var(--primary-d); }
.ts.hl b.over { color: var(--danger); }

.meals { display: grid; grid-template-columns: repeat(4, 1fr); gap: var(--s-4); }
.meal { border: 1px solid var(--border); border-radius: var(--r-md); padding: var(--s-4); background: var(--surface-2); }
.meal-h { display: flex; justify-content: space-between; font-weight: 700; font-size: 13.5px; margin-bottom: 8px; }
.meal-h .muted { font-weight: 600; font-size: 12px; }
.meal ul { list-style: none; margin: 0; padding: 0; display: grid; gap: 6px; }
.meal li { display: flex; justify-content: space-between; font-size: 13px; }
.d-name { color: var(--text); }
.d-cal { color: var(--text-faint); font-size: 12px; }
.meal-empty { color: var(--text-faint); font-size: 12.5px; margin: 0; }

@media (max-width: 920px) { .d-grid { grid-template-columns: 1fr; } .meals { grid-template-columns: 1fr 1fr; } }
@media (max-width: 520px) { .meals { grid-template-columns: 1fr; } }
</style>
