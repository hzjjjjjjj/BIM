<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { auth } from '@/stores/auth'
import { api } from '@/services/api'
import {
  generateHealthReport,
  type HealthReport,
} from '@/lib/report'
import ProgressRing from '@/components/ProgressRing.vue'
import EmptyState from '@/components/EmptyState.vue'

const report = ref<HealthReport | null>(null)
const loading = ref(true)
const printing = ref(false)

// 用于打印时隐藏侧栏等 UI 元素
function handlePrint() {
  printing.value = true
  setTimeout(() => {
    window.print()
    // 打印对话框关闭后恢复（浏览器 print 是阻塞的，但为了保险）
    setTimeout(() => { printing.value = false }, 500)
  }, 100)
}

// 监听 afterprint 事件恢复状态
if (typeof window !== 'undefined') {
  window.addEventListener('afterprint', () => { printing.value = false })
}

onMounted(async () => {
  if (!auth.user) return
  const u = auth.user
  const username = u.username

  const [records, exercises, goals, diets, achievements] = await Promise.all([
    api.getRecords(username, 90),
    api.getExercise(username, 90),
    api.getGoals(username),
    api.getDiet(username, 30),
    api.getAchievements(username),
  ])

  report.value = generateHealthReport(u, records, exercises, goals, diets, achievements)
  loading.value = false
})

// 计算属性：各段数据快捷引用
const bc = computed(() => report.value?.bodyComposition)
const met = computed(() => report.value?.metabolic)
const scr = computed(() => report.value?.score)
const trd = computed(() => report.value?.trend)
const gls = computed(() => report.value?.goals)
const diet = computed(() => report.value?.diet)
const ex = computed(() => report.value?.exercise)
const ach = computed(() => report.value?.achievements)
const recs = computed(() => report.value?.recommendations ?? [])

// 建议按优先级排序
const sortedRecs = computed(() => {
  const order: Record<string, number> = { '高': 0, '中': 1, '低': 2 }
  return [...recs.value].sort((a, b) => (order[a.priority] ?? 9) - (order[b.priority] ?? 9))
})

// 颜色工具
function priorityColor(p: string): string {
  if (p === '高') return 'var(--accent)'
  if (p === '中') return 'var(--gold)'
  return 'var(--primary)'
}
</script>

<template>
  <div class="report-page" :class="{ printing }" v-if="report">
    <!-- ========== 报告头部 ========== -->
    <header class="rpt-header card">
      <div class="rpt-header-top">
        <div class="rpt-brand">
          <span class="rpt-logo">❦</span>
          <div>
            <h1>BMI 体质评估 — 详细健康报告</h1>
            <p class="rpt-sub">综合健康评估与个性化建议 · v6</p>
          </div>
        </div>
        <button class="print-btn" @click="handlePrint" title="打印或保存为 PDF">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="6 9 6 2 18 2 18 9"/><path d="M6 18H4a2 2 0 01-2-2v-5a2 2 0 012-2h16a2 2 0 012 2v5a2 2 0 01-2 2h-2"/><rect x="6" y="14" width="12" height="8"/></svg>
          打印 / 导出 PDF
        </button>
      </div>
      <div class="rpt-meta">
        <div class="meta-item">
          <span class="meta-label">报告对象</span>
          <b>{{ report.user.username }}</b>
          <span class="meta-detail">{{ report.user.gender }} · {{ report.user.age }}岁 · {{ report.user.height }}cm · {{ report.user.activityLevel }}</span>
        </div>
        <div class="meta-item">
          <span class="meta-label">生成时间</span>
          <b>{{ new Date(report.generatedAt).toLocaleString('zh-CN') }}</b>
        </div>
        <div class="meta-item" v-if="report.reportPeriod.start">
          <span class="meta-label">统计周期</span>
          <b>{{ report.reportPeriod.start }} ~ {{ report.reportPeriod.end }}</b>
          <span class="meta-detail">共 {{ trd?.recordCount ?? 0 }} 条健康记录</span>
        </div>
      </div>
    </header>

    <!-- ========== 总体评价摘要 ========== -->
    <section class="card summary-section" v-if="scr">
      <div class="summary-left">
        <ProgressRing :value="scr.totalScore" :max="100" :size="130" :stroke="11"
          :label="'健康评分'" :color="scr.totalScore >= 75 ? 'var(--primary)' : scr.totalScore >= 60 ? 'var(--gold)' : 'var(--accent)'" />
        <div class="summary-level">
          <span class="level-badge" :class="scr.totalScore >= 75 ? 'good' : scr.totalScore >= 60 ? 'mid' : 'low'">{{ scr.level }}</span>
        </div>
      </div>
      <div class="summary-right">
        <h3>总体评价</h3>
        <p class="summary-text">{{ report.overallSummary }}</p>
      </div>
    </section>

    <!-- ========== 01 体成分分析 ========== -->
    <section class="card" v-if="bc">
      <div class="sec-head"><span class="sec-num">01</span><h2>体成分分析</h2></div>
      <div class="grid-2col">
        <!-- 左：核心指标卡片 -->
        <div class="metric-grid-sm">
          <div class="metric-sm"><b>{{ bc.bmi.toFixed(1) }}</b><span>BMI</span><small>{{ bc.bmiLabel }}</small></div>
          <div class="metric-sm"><b>{{ report.latestRecord?.weight.toFixed(1) ?? '--' }}</b><span>体重 kg</span></div>
          <div class="metric-sm"><b>{{ bc.bodyFat }}%</b><span>体脂率</span></div>
          <div class="metric-sm"><b>{{ bc.muscleRate }}%</b><span>肌肉率</span></div>
          <div class="metric-sm"><b>{{ bc.waterRate }}%</b><span>水分率</span></div>
          <div class="metric-sm"><b>{{ bc.visceralFat }}</b><span>内脏脂肪 级</span><small>{{ bc.visceralFatLabel }}</small></div>
          <div class="metric-sm"><b>{{ bc.bodyAge }}</b><span>身体年龄 岁</span><small>实际 {{ bc.actualAge }} 岁</small></div>
          <div class="metric-sm"><b>{{ bc.waist }}cm</b><span>腰围</span><small>{{ bc.bodyShape }}</small></div>
        </div>
        <!-- 右：解读文字 -->
        <div class="analysis-text">
          <h4>体质解读</h4>
          <ul>
            <li><strong>BMI 分类：</strong>{{ bc.bmiLabel }}（BMI {{ bc.bmi.toFixed(1) }}）。理想体重为 <b>{{ bc.idealWeight }}kg</b>，当前相差 <b :class="Math.abs(bc.weightDiffFromIdeal) > 3 ? 'warn' : ''">{{ bc.weightDiffFromIdeal > 0 ? '+' : '' }}{{ bc.weightDiffFromIdeal }}kg</b>。</li>
            <li><strong>体质类型：</strong>{{ bc.bodyType }}。基于 BMI 与体脂率的交叉分类。</li>
            <li><strong>体型判定：</strong>{{ bc.bodyShape }}。腰围身高比评估结果：<b>{{ bc.whtrLabel }}</b>。</li>
            <li><strong>身体年龄：</strong>{{ bc.bodyAge }} 岁{{ bc.bodyAge !== bc.actualAge ? `（${bc.bodyAge < bc.actualAge ? '年轻' : '老化'} ${Math.abs(bc.bodyAge - bc.actualAge)} 年）` : '（与实际年龄一致）' }}。反映生活方式对生理年龄的影响。</li>
            <li><strong>骨骼肌肉：</strong>{{ bc.muscleAssessment }}。</li>
          </ul>
        </div>
      </div>
    </section>

    <!-- ========== 02 代谢评估 ========== -->
    <section class="card" v-if="met">
      <div class="sec-head"><span class="sec-num">02</span><h2>代谢评估</h2></div>
      <div class="grid-2col">
        <div class="met-formulas">
          <h4>基础代谢率（BMR）三公式对比</h4>
          <table class="data-table">
            <thead><tr><th>公式</th><th>结果 kcal/天</th><th>说明</th></tr></thead>
            <tbody>
              <tr><td>Harris-Benedict</td><td class="num">{{ met.bmrHarris }}</td><td>经典公式，考虑性别、体重、身高、年龄</td></tr>
              <tr><td>Mifflin-St Jeor</td><td class="num">{{ met.bmrMifflin }}</td><td>目前公认最准确的基础代谢公式</td></tr>
              <tr><td>中国修正公式</td><td class="num">{{ met.bmrChina }}</td><td>针对中国人群修正的经验公式</td></tr>
              <tr class="highlight-row"><td><b>平均值</b></td><td class="num"><b>{{ met.bmrAvg }}</b></td><td>取三者均值作为最终 BMR 参考</td></tr>
            </tbody>
          </table>
        </div>
        <div class="met-tdee">
          <div class="tdee-card">
            <div class="tdee-big"><b>{{ met.tdee }}</b><span>kcal / 天</span></div>
            <p>每日总能量消耗（TDEE）= BMR × 活动系数</p>
            <div class="factor-bar">
              <span>活动等级：<b>{{ met.activityLevel }}</b></span>
              <span>系数：<b>{{ met.activityFactor }}</b></span>
            </div>
          </div>
          <div class="tdee-note">
            <h4>代谢解读</h4>
            <p>TDEE 代表你每天维持当前体重所需的总热量。若要减重，每日摄入应低于 TDEE 300~500 kcal；若要增肌，则需超出 TDEE 200~300 kcal 并配合力量训练。</p>
          </div>
        </div>
      </div>
    </section>

    <!-- ========== 03 健康评分五维拆解 ========== -->
    <section class="card" v-if="scr">
      <div class="sec-head"><span class="sec-num">03</span><h2>健康评分五维拆解</h2><span class="muted">总分 {{ scr.totalScore }}/100（{{ scr.level }}）</span></div>
      <div class="dims-list">
        <div v-for="dim in scr.dimensions" :key="dim.label" class="dim-card">
          <div class="dim-header">
            <span class="dim-name">{{ dim.label }}</span>
            <span class="dim-score"><b>{{ dim.score }}</b>/{{ dim.max }}</span>
          </div>
          <div class="dim-track-wrap">
            <div class="dim-track"><div class="dim-fill" :style="{ width: (dim.score / dim.max * 100) + '%' }" /></div>
          </div>
          <p class="dim-comment">{{ dim.comment }}</p>
        </div>
      </div>
    </section>

    <!-- ========== 04 趋势分析 ========== -->
    <section class="card" v-if="trd">
      <div class="sec-head"><span class="sec-num">04</span><h2>趋势分析</h2></div>
      <div class="grid-2col">
        <div class="trend-stats">
          <h4>统计概览</h4>
          <table class="data-table compact">
            <tbody>
              <tr><td>记录天数</td><td class="num">{{ trd.recordCount }} 天</td></tr>
              <tr><td>起始日期</td><td>{{ trd.firstDate }}</td></tr>
              <tr><td>最新日期</td><td>{{ trd.lastDate }}</td></tr>
              <tr><td>起始体重</td><td class="num">{{ trd.startWeight }} kg</td></tr>
              <tr><td>当前体重</td><td class="num">{{ trd.endWeight }} kg</td></tr>
              <tr><td>总变化</td><td class="num" :class="trd.weightChange > 0 ? 'up' : trd.weightChange < 0 ? 'down' : ''">{{ trd.weightChange > 0 ? '+' : '' }}{{ trd.weightChange }} kg</td></tr>
              <tr><td>周均变化</td><td class="num" :class="trd.weeklyAvgChange > 0 ? 'up' : trd.weeklyAvgChange < 0 ? 'down' : ''">{{ trd.weeklyAvgChange > 0 ? '+' : '' }}{{ trd.weeklyAvgChange }} kg/周</td></tr>
              <tr><td>趋势方向</td><td><b>{{ trd.direction }}</b></td></tr>
            </tbody>
          </table>
        </div>
        <div class="trend-prediction">
          <h4>未来趋势预测（线性回归）</h4>
          <div class="pred-cards" v-if="trd.predictedWeight30d != null">
            <div class="pred-card">
              <span class="pred-label">30 天后预测体重</span>
              <b class="pred-val">{{ trd.predictedWeight30d }} kg</b>
            </div>
            <div class="pred-card" v-if="trd.predictedBMI30d != null">
              <span class="pred-label">30 天后预测 BMI</span>
              <b class="pred-val">{{ trd.predictedBMI30d }}</b>
            </div>
          </div>
          <div class="risk-box" :class="trd.riskAssessment.includes('高') ? 'risk-high' : trd.riskAssessment.includes('中') ? 'risk-mid' : 'risk-low'">
            <strong>风险评估：</strong>{{ trd.riskAssessment }}
          </div>
        </div>
      </div>
    </section>

    <!-- ========== 05 目标进度 ========== -->
    <section class="card" v-if="gls && gls.active">
      <div class="sec-head"><span class="sec-num">05</span><h2>目标进度追踪</h2></div>
      <div class="goal-list">
        <div v-for="(g, i) in gls.goals" :key="i" class="goal-card">
          <div class="goal-info">
            <span class="goal-type">{{ g.goalType }}</span>
            <span class="goal-target">目标值 {{ g.targetValue }}{{ g.goalType.includes('重') || g.goalType.includes('脂') ? 'kg' : '' }}</span>
            <span class="goal-dates">{{ g.startDate }} → {{ g.endDate ?? '进行中' }}</span>
          </div>
          <div class="goal-progress">
            <div class="goal-track"><div class="goal-fill" :style="{ width: Math.min(100, g.progressPct) + '%' }" /></div>
            <span class="goal-pct">{{ g.progressPct }}%</span>
          </div>
          <div class="goal-status">
            <b>{{ g.status }}</b>
            <span v-if="g.predictedDays > 0">预计还需 {{ g.predictedDays }} 天</span>
          </div>
        </div>
      </div>
    </section>
    <section class="card" v-else-if="gls && !gls.active">
      <EmptyState title="暂无目标" desc="在「目标计划」模块设定健康目标后，此处会显示进度追踪。" icon="🎯" />
    </section>

    <!-- ========== 06 饮食摘要 ========== -->
    <section class="card" v-if="diet">
      <div class="sec-head"><span class="sec-num">06</span><h2>饮食营养摘要</h2><span class="muted">近 {{ diet.dayCount }} 天</span></div>
      <div class="grid-2col">
        <div class="diet-overview">
          <h4>热量与宏量营养素</h4>
          <div class="diet-metrics">
            <div class="diet-metric"><b>{{ diet.totalCalories.toLocaleString() }}</b><span>总热量 kcal</span></div>
            <div class="diet-metric"><b>{{ diet.avgDailyCalories }}</b><span>日均 kcal</span></div>
            <div class="diet-metric"><b>{{ diet.totalProtein }}g</b><span>蛋白质</span></div>
            <div class="diet-metric"><b>{{ diet.totalCarbs }}g</b><span>碳水化合物</span></div>
            <div class="diet-metric"><b>{{ diet.totalFat }}g</b><span>脂肪</span></div>
          </div>
          <!-- 宏量比环形示意 -->
          <div class="macro-bars">
            <div class="macro-bar">
              <span>蛋白质</span><div class="macro-track"><div class="macro-fill prot" :style="{ width: diet.macroPct.protein + '%' }" /></div><b>{{ diet.macroPct.protein }}%</b>
            </div>
            <div class="macro-bar">
              <span>碳水</span><div class="macro-track"><div class="macro-track"><div class="macro-fill carb" :style="{ width: diet.macroPct.carbs + '%' }" /></div></div><b>{{ diet.macroPct.carbs }}%</b>
            </div>
            <div class="macro-bar">
              <span>脂肪</span><div class="macro-track"><div class="macro-fill fat" :style="{ width: diet.macroPct.fat + '%' }" /></div><b>{{ diet.macroPct.fat }}%</b>
            </div>
          </div>
        </div>
        <div class="diet-detail">
          <h4>三餐分布</h4>
          <table class="data-table" v-if="diet.mealDistribution.length">
            <thead><tr><th>餐次</th><th>热量 kcal</th><th>占比</th></tr></thead>
            <tbody>
              <tr v-for="m in diet.mealDistribution" :key="m.meal"><td>{{ m.meal }}</td><td class="num">{{ m.cal }}</td><td class="num">{{ m.pct }}%</td></tr>
            </tbody>
          </table>
          <h4 style="margin-top: var(--s-5)">高频食物 TOP 8</h4>
          <ul class="food-list">
            <li v-for="f in diet.topFoods" :key="f.name">
              <b>{{ f.name }}</b>
              <span class="food-cal">{{ f.calories }} kcal × {{ f.count }}次</span>
            </li>
          </ul>
        </div>
      </div>
    </section>

    <!-- ========== 07 运动摘要 ========== -->
    <section class="card" v-if="ex">
      <div class="sec-head"><span class="sec-num">07</span><h2>运动记录摘要</h2></div>
      <div class="grid-2col">
        <div class="ex-stats">
          <h4>总体数据</h4>
          <div class="ex-metrics">
            <div class="ex-metric"><b>{{ ex.totalSessions }}</b><span>总训练次数</span></div>
            <div class="ex-metric"><b>{{ ex.totalMinutes }}</b><span>总时长 分钟</span></div>
            <div class="ex-metric"><b>{{ ex.totalCaloriesBurned }}</b><span>总消耗 kcal</span></div>
            <div class="ex-metric"><b>{{ ex.avgPerSession }}</b><span>平均每次 分钟</span></div>
            <div class="ex-metric"><b>{{ ex.weeklyFreq }}</b><span>周均频率 次/周</span></div>
          </div>
        </div>
        <div class="ex-detail">
          <h4>运动类型分布</h4>
          <table class="data-table" v-if="ex.topExercises.length">
            <thead><tr><th>运动项目</th><th>次数</th><th>分钟</th><th>消耗 kcal</th></tr></thead>
            <tbody>
              <tr v-for="e in ex.topExercises" :key="e.type">
                <td><b>{{ e.type }}</b></td>
                <td class="num">{{ e.sessions }}</td>
                <td class="num">{{ e.minutes }}</td>
                <td class="num">{{ e.calories }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </section>

    <!-- ========== 08 成就徽章 ========== -->
    <section class="card" v-if="ach">
      <div class="sec-head"><span class="sec-num">08</span><h2>成就徽章</h2><span class="muted">共 {{ ach.totalEarned }} 枚</span></div>
      <div class="badge-grid" v-if="ach.badges.length">
        <div v-for="(b, i) in ach.badges" :key="i" class="badge-item">
          <span class="badge-icon">🏅</span>
          <b>{{ b.name }}</b>
          <time>{{ b.date }}</time>
        </div>
      </div>
      <p v-else class="empty-note">还没有获得成就徽章，继续坚持！</p>
    </section>

    <!-- ========== 09 综合建议 ========== -->
    <section class="card rec-section" v-if="sortedRecs.length">
      <div class="sec-head"><span class="sec-num">09</span><h2>综合健康建议</h2></div>
      <p class="rec-intro">以下建议基于你的各项健康指标自动生成，按优先级排序。建议逐一阅读并制定改善计划。</p>
      <div class="rec-list">
        <div v-for="(rec, i) in sortedRecs" :key="i" class="rec-card" :class="'pri-' + rec.priority.toLowerCase()">
          <div class="rec-head">
            <span class="rec-icon">{{ rec.icon }}</span>
            <span class="rec-cat">{{ rec.category }}</span>
            <span class="rec-pri" :style="{ color: priorityColor(rec.priority), borderColor: priorityColor(rec.priority) }">{{ rec.priority }}优先</span>
          </div>
          <h4 class="rec-title">{{ rec.title }}</h4>
          <p class="rec-detail">{{ rec.detail }}</p>
        </div>
      </div>
    </section>

    <!-- 报告尾部 -->
    <footer class="rpt-footer">
      <p>本报告由 BMI 体质评估系统 v6 自动生成 · 数据仅供参考，不构成医疗诊断依据 · 如有健康疑虑请咨询专业医师</p>
    </footer>
  </div>

  <!-- 加载 / 空 状态 -->
  <div v-else-if="loading" class="loading-wrap">
    <div class="spinner"></div>
    <p>正在聚合数据生成报告...</p>
  </div>
  <EmptyState v-else title="无法生成报告" desc="请先完成至少一次健康数据录入。" icon="📋" />
</template>

<style scoped>
.report-page {
  display: grid;
  gap: var(--s-6);
  max-width: 960px;
  margin: 0 auto;
}

/* ---- 卡片通用 ---- */
.card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--r-lg);
  padding: var(s-7);
  box-shadow: var(--sh-1);
}

/* ---- 报告头 ---- */
.rpt-header { background: linear-gradient(135deg, var(--surface) 40%, var(--primary-ll)); }
.rpt-header-top { display: flex; justify-content: space-between; align-items: flex-start; gap: var(s-5); flex-wrap: wrap; }
.rpt-brand { display: flex; align-items: center; gap: 14px; }
.rpt-logo {
  font-size: 32px; width: 52px; height: 52px; display: grid; place-items: center;
  border-radius: 14px; background: linear-gradient(140deg, var(--primary), var(--accent));
  box-shadow: 0 8px 22px -6px rgba(31, 138, 109, 0.7); color: #fff;
}
.rpt-brand h1 { font-family: var(--font-display); font-size: 22px; margin: 0; }
.rpt-sub { color: var(--text-soft); font-size: 13px; margin: 2px 0 0; }

.print-btn {
  display: inline-flex; align-items: center; gap: 8px;
  border: 1px solid var(--primary); background: var(--primary-l); color: var(--primary-d);
  padding: 10px 18px; border-radius: var(--r-md); cursor: pointer;
  font-weight: 700; font-size: 13.5px; transition: all 0.2s;
}
.print-btn:hover { background: var(--primary); color: #fff; }

.rpt-meta {
  display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: var(--s-4);
  margin-top: var(s-6); padding-top: var(s-5); border-top: 1px solid var(--border);
}
.meta-item { display: grid; gap: 2px; }
.meta-label { font-size: 11.5px; color: var(--text-faint); text-transform: uppercase; letter-spacing: 0.06em; }
.meta-item b { font-size: 15px; }
.meta-detail { font-size: 12px; color: var(--text-soft); }

/* ---- 总评摘要 ---- */
.summary-section { display: flex; align-items: center; gap: var(s-7); }
.summary-left { display: grid; justify-items: center; gap: 8px; flex: none; }
.level-badge {
  font-weight: 800; font-size: 13px; padding: 5px 16px; border-radius: var(r-pill);
}
.level-badge.good { background: var(--primary-l); color: var(--primary-d); }
.level-badge.mid { background: var(--gold-l); color: #9a6b06; }
.level-badge.low { background: var(--accent-l); color: var(--accent-d); }
.summary-right h3 { font-size: 17px; margin-bottom: var(s-3); }
.summary-text { font-size: 14px; line-height: 1.8; color: var(--text-soft); }

/* ---- 段标题 ---- */
.sec-head { display: flex; align-items: baseline; gap: 10px; margin-bottom: var(s-5); }
.sec-num {
  font-family: var(--font-display); font-weight: 900; font-size: 20px;
  color: var(--primary); line-height: 1;
}
.sec-head h2 { font-size: 18px; margin: 0; }

/* ---- 两列布局 ---- */
.grid-2col { display: grid; grid-template-columns: 1fr 1fr; gap: var(s-6); }

/* ---- 小指标网格 ---- */
.metric-grid-sm { display: grid; grid-template-columns: repeat(2, 1fr); gap: 10px; }
.metric-sm {
  background: var(--surface-2); border-radius: var(--r-md); padding: 12px 14px;
  display: grid; gap: 2px;
}
.metric-sm b { font-family: var(--font-display); font-size: 20px; }
.metric-sm span { font-size: 12px; color: var(--text-soft); font-weight: 600; }
.metric-sm small { font-size: 11px; color: var(--text-faint); }

/* ---- 解读文字 ---- */
.analysis-text h4 { font-size: 15px; margin-bottom: var(s-3); }
.analysis-text ul { list-style: none; padding: 0; display: grid; gap: 10px; }
.analysis-text li { font-size: 13.5px; line-height: 1.65; color: var(--text-soft); padding-left: 14px; position: relative; }
.analysis-text li::before { content: '•'; position: absolute; left: 0; color: var(--primary); font-weight: bold; }
.analysis-text li strong { color: var(--text); }
.analysis-text li b.warn { color: var(--accent); }
.analysis-text li b { color: var(--primary-d); }

/* ---- 表格 ---- */
.data-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.data-table th { text-align: left; padding: 8px 10px; background: var(--surface-2); border-bottom: 2px solid var(--border); font-weight: 700; font-size: 12px; }
.data-table td { padding: 8px 10px; border-bottom: 1px solid var(--border-2); color: var(--text-soft); }
.data-table .num { font-family: var(--font-display); font-weight: 700; color: var(--text); text-align: right; }
.data-table.compact td { padding: 6px 10px; }
.highlight-row { background: var(--primary-ll); }
.highlight-row td { border-bottom-color: var(--primary-l); }
.up { color: var(--accent) !important; }
.down { color: var(--primary) !important; }

/* ---- TDEE 卡片 ---- */
.tdee-card { background: linear-gradient(135deg, var(--ink) 0%, var(--ink-2) 100%); color: #fff; border-radius: var(--r-lg); padding: var(s-6); text-align: center; }
.tdee-big b { font-family: var(--font-display); font-size: 36px; display: block; }
.tdee-big span { font-size: 13px; opacity: 0.75; }
.tdee-card p { font-size: 13px; margin: var(s-3) 0 0; opacity: 0.85; }
.factor-bar { display: flex; justify-content: center; gap: var(s-6); margin-top: var(s-4); font-size: 13px; }
.tdee-note h4 { font-size: 15px; margin-bottom: var(s-3); }
.tdee-note p { font-size: 13px; color: var(--text-soft); line-height: 1.7; }

/* ---- 五维评分 ---- */
.dims-list { display: grid; gap: var(s-5); }
.dim-card { background: var(--surface-2); border-radius: var(--r-md); padding: var(s-5); }
.dim-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.dim-name { font-weight: 700; font-size: 14px; }
.dim-score b { font-family: var(--font-display); font-size: 17px; }
.dim-score { font-size: 13px; color: var(--text-soft); }
.dim-track-wrap { margin-bottom: 6px; }
.dim-track { height: 10px; background: var(--surface); border-radius: 99px; overflow: hidden; }
.dim-fill { height: 100%; border-radius: 99px; background: linear-gradient(90deg, var(--primary), var(--accent)); transition: width 0.8s var(--ease-out); }
.dim-comment { font-size: 12.5px; color: var(--text-faint); margin: 0; }

/* ---- 趋势预测 ---- */
.trend-stats h4, .trend-prediction h4 { font-size: 15px; margin-bottom: var(s-3); }
.pred-cards { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-bottom: var(s-5); }
.pred-card {
  background: var(--surface-2); border-radius: var(--r-md); padding: var(s-5);
  text-align: center; display: grid; gap: 4px;
}
.pred-label { font-size: 12px; color: var(--text-soft); }
.pred-val { font-family: var(--font-display); font-size: 24px; color: var(--primary-d); }
.risk-box { padding: var(s-4); border-radius: var(--r-md); font-size: 13.5px; line-height: 1.6; }
.risk-high { background: rgba(229,72,77,0.08); border: 1px solid rgba(229,72,77,0.25); color: var(--danger); }
.risk-mid { background: rgba(244,183,64,0.08); border: 1px solid rgba(244,183,64,0.25); color: #9a6b06; }
.risk-low { background: rgba(31,138,109,0.08); border: 1px solid rgba(31,138,109,0.25); color: var(--primary-d); }

/* ---- 目标 ---- */
.goal-list { display: grid; gap: var(s-4); }
.goal-card { background: var(--surface-2); border-radius: var(--r-md); padding: var(s-5); }
.goal-info { display: flex; align-items: center; gap: var(s-4); flex-wrap: wrap; margin-bottom: var(s-4); }
.goal-type { font-weight: 800; font-size: 15px; background: var(--primary-l); color: var(--primary-d); padding: 3px 12px; border-radius: var(--r-pill); }
.goal-target { font-weight: 600; }
.goal-dates { font-size: 12px; color: var(--text-faint); }
.goal-progress { display: flex; align-items: center; gap: 10px; margin-bottom: 6px; }
.goal-track { flex: 1; height: 8px; background: var(--surface); border-radius: 99px; overflow: hidden; }
.goal-fill { height: 100%; border-radius: 99px; background: linear-gradient(90deg, var(--primary), var(--gold)); transition: width 0.6s var(--ease-out); }
.goal-pct { font-family: var(--font-display); font-weight: 700; font-size: 14px; min-width: 42px; text-align: right; }
.goal-status { font-size: 13px; color: var(--text-soft); }
.goal-status b { color: var(--text); }

/* ---- 饮食 ---- */
.diet-overview h4, .diet-detail h4 { font-size: 15px; margin-bottom: var(s-3); }
.diet-metrics { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; margin-bottom: var(s-5); }
.diet-metric { background: var(--surface-2); border-radius: var(--r-md); padding: 10px; text-align: center; }
.diet-metric b { font-family: var(--font-display); font-size: 18px; display: block; }
.diet-metric span { font-size: 11px; color: var(--text-faint); }
.macro-bars { display: grid; gap: 8px; }
.macro-bar { display: flex; align-items: center; gap: 10px; font-size: 13px; }
.macro-bar span { min-width: 48px; font-weight: 600; }
.macro-track { flex: 1; height: 10px; background: var(--surface); border-radius: 99px; overflow: hidden; }
.macro-fill { height: 100%; border-radius: 99px; transition: width 0.6s; }
.macro-fill.prot { background: #3b82c4; }
.macro-fill.carb { background: #22c55e; }
.macro-fill.fat { background: #f59e0b; }
.food-list { list-style: none; padding: 0; display: grid; gap: 6px; }
.food-list li { display: flex; justify-content: space-between; align-items: center; font-size: 13px; padding: 4px 0; border-bottom: 1px dashed var(--border-2); }
.food-cal { color: var(--text-faint); font-size: 12px; }

/* ---- 运动 ---- */
.ex-stats h4, .ex-detail h4 { font-size: 15px; margin-bottom: var(s-3); }
.ex-metrics { display: grid; grid-template-columns: repeat(2, 1fr); gap: 10px; }
.ex-metric { background: var(--surface-2); border-radius: var(--r-md); padding: 12px; text-align: center; }
.ex-metric b { font-family: var(--font-display); font-size: 20px; display: block; }
.ex-metric span { font-size: 11.5px; color: var(--text-faint); }

/* ---- 成就 ---- */
.badge-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: 12px; }
.badge-item {
  background: var(--gold-l); border: 1px solid rgba(244,183,64,0.3);
  border-radius: var(--r-md); padding: var(s-4); text-align: center;
  display: grid; gap: 4px;
}
.badge-icon { font-size: 28px; }
.badge-item b { font-size: 14px; }
.badge-item time { font-size: 11px; color: var(--text-faint); }
.empty-note { color: var(--text-faint); font-size: 13px; text-align: center; padding: var(s-6) 0; }

/* ---- 建议 ---- */
.rec-section { border: 2px solid var(--primary-l); }
.rec-intro { font-size: 13.5px; color: var(--text-soft); margin-bottom: var(s-5); }
.rec-list { display: grid; gap: var(s-4); }
.rec-card {
  border-radius: var(--r-md); padding: var(s-5); border-left: 4px solid;
  background: var(--surface-2);
}
.rec-card.pri-high { border-left-color: var(--accent); }
.rec-card.pri-mid { border-left-color: var(--gold); }
.rec-card.pri-low { border-left-color: var(--primary); }
.rec-head { display: flex; align-items: center; gap: 10px; margin-bottom: 6px; }
.rec-icon { font-size: 20px; }
.rec-cat { font-size: 12px; font-weight: 700; color: var(--text-soft); text-transform: uppercase; letter-spacing: 0.04em; }
.rec-pri { font-size: 11px; font-weight: 800; padding: 2px 10px; border-radius: var(--r-pill); border: 1px solid; }
.rec-title { font-size: 15px; margin: 0 0 4px; }
.rec-detail { font-size: 13.5px; color: var(--text-soft); line-height: 1.65; margin: 0; }

/* ---- 尾部 ---- */
.rpt-footer { text-align: center; padding: var(s-6); color: var(--text-faint); font-size: 12px; border-top: 1px solid var(--border-2); }

/* ---- 加载态 ---- */
.loading-wrap { display: grid; justify-content: center; align-items: center; gap: var(s-5); padding: var(s-10) 0; color: var(--text-soft); }
.spinner { width: 36px; height: 36px; border: 3px solid var(--border); border-top-color: var(--primary); border-radius: 50%; animation: spin 0.7s linear infinite; justify-self: center; }
@keyframes spin { to { transform: rotate(360deg); } }

/* ---- 打印样式 ---- */
@media print {
  .report-page.printing .print-btn,
  .report-page.printing .rpt-footer { display: none !important; }
  .report-page.printing { background: #fff !important; color: #000 !important; }
  .reportpage.printing .card { box-shadow: none !important; border-color: #ddd !important; break-inside: avoid; page-break-inside: avoid; }
}

/* ---- 响应式 ---- */
@media (max-width: 860px) {
  .grid-2col { grid-template-columns: 1fr; }
  .metric-grid-sm { grid-template-columns: repeat(2, 1fr); }
  .diet-metrics { grid-template-columns: repeat(2, 1fr); }
  .summary-section { flex-direction: column; text-align: center; }
  .rpt-header-top { flex-direction: column; }
  .badge-grid { grid-template-columns: repeat(2, 1fr); }
  .ex-metrics { grid-template-columns: repeat(2, 1fr); }
  .pred-cards { grid-template-columns: 1fr; }
}
@media (max-width: 520px) {
  .metric-grid-sm { grid-template-columns: 1fr 1fr; }
  .diet-metrics { grid-template-columns: 1fr 1fr; }
  .ex-metrics { grid-template-columns: 1fr 1fr; }
  .badge-grid { grid-template-columns: 1fr; }
  .rpt-meta { grid-template-columns: 1fr; }
}
</style>
