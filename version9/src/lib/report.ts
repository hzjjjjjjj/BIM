// ============================================================
// 详细健康报告生成引擎 — v6 核心模块
// 聚合所有模块数据，生成可打印/导出的综合健康评估报告
// ============================================================

import type {
  Gender, HealthRecord, ExerciseRecord, Goal,
  DietRecord, Achievement, User, ActivityLevel,
} from '@/types'
import {
  calcBMI, classifyBMI,
  calcBMR_Harris, calcBMR_Mifflin, calcBMR_China,
  calcTDEE, getActivityFactor,
  assessVisceralFat, assessMuscle, assessWHtR, classifyBodyShape,
  classifyBodyType, calcIdealWeight,
  calcHealthScore, scoreLevel, healthScoreBreakdown,
  predictTrend, trendDirection, assessRisk, predictGoalDays,
  round,
} from './health'

// ==================== 类型定义 ====================

/** 报告体成分段 */
export interface BodyCompositionSection {
  bmi: number
  bmiLabel: string
  bodyFat: number
  waterRate: number
  muscleRate: number
  visceralFat: number
  visceralFatLabel: string
  boneMuscle: number
  waist: number
  bodyAge: number
  actualAge: number
  bodyType: string
  bodyShape: string
  whtrLabel: string
  idealWeight: number
  weightDiffFromIdeal: number
  muscleAssessment: string
}

/** 报告代谢段 */
export interface MetabolicSection {
  bmrHarris: number
  bmrMifflin: number
  bmrChina: number
  bmrAvg: number
  tdee: number
  activityLevel: ActivityLevel
  activityFactor: number
}

/** 报告评分段 */
export interface ScoreSection {
  totalScore: number
  level: string
  dimensions: { label: string; score: number; max: number; comment: string }[]
}

/** 报告趋势段 */
export interface TrendSection {
  recordCount: number
  firstDate: string
  lastDate: string
  startWeight: number
  endWeight: number
  weightChange: number
  direction: string
  predictedWeight30d: number | null
  predictedBMI30d: number | null
  riskAssessment: string
  weeklyAvgChange: number
}

/** 报告目标段 */
export interface GoalSection {
  active: boolean
  goals: {
    goalType: string
    targetValue: number
    startDate: string
    endDate?: string
    currentStage?: number
    progressPct: number
    predictedDays: number
    status: string
  }[]
}

/** 报告饮食段 */
export interface DietSummarySection {
  totalCalories: number
  avgDailyCalories: number
  totalProtein: number
  totalCarbs: number
  totalFat: number
  dayCount: number
  macroPct: { protein: number; carbs: number; fat: number }
  topFoods: { name: string; calories: number; count: number }[]
  mealDistribution: { meal: string; cal: number; pct: number }[]
}

/** 报告运动段 */
export interface ExerciseSummarySection {
  totalSessions: number
  totalMinutes: number
  totalCaloriesBurned: number
  avgPerSession: number
  topExercises: { type: string; sessions: number; minutes: number; calories: number }[]
  weeklyFreq: number
}

/** 报告成就段 */
export interface AchievementSection {
  totalEarned: number
  badges: { name: string; date: string }[]
}

/** 综合建议项 */
export interface RecommendationItem {
  category: '饮食' | '运动' | '生活方式' | '医疗'
  priority: '高' | '中' | '低'
  title: string
  detail: string
  icon: string
}

/** 完整报告对象 */
export interface HealthReport {
  generatedAt: string
  reportPeriod: { start: string; end: string }
  user: {
    username: string
    gender: Gender
    age: number
    height: number
    activityLevel: ActivityLevel
  }
  latestRecord: HealthRecord | null
  bodyComposition: BodyCompositionSection | null
  metabolic: MetabolicSection | null
  score: ScoreSection | null
  trend: TrendSection | null
  goals: GoalSection | null
  diet: DietSummarySection | null
  exercise: ExerciseSummarySection | null
  achievements: AchievementSection | null
  recommendations: RecommendationItem[]
  overallSummary: string
}

// ==================== 生成入口 ====================

/**
 * 生成完整健康详细报告
 * @param user 用户档案
 * @param records 健康记录（按日期升序）
 * @param exercises 运动记录
 * @param goals 目标列表
 * @param diets 饮食记录
 * @param achievements 成就列表
 * @returns 完整报告对象
 */
export function generateHealthReport(
  user: User,
  records: HealthRecord[],
  exercises: ExerciseRecord[],
  goals: Goal[],
  diets: DietRecord[],
  achievements: Achievement[],
): HealthReport {
  const latest = records.length > 0 ? records[records.length - 1] : null
  const generatedAt = new Date().toISOString()
  const periodStart = records.length > 0 ? records[0].recordDate : ''
  const periodEnd = latest?.recordDate ?? ''

  return {
    generatedAt,
    reportPeriod: { start: periodStart, end: periodEnd },
    user: {
      username: user.username,
      gender: user.gender,
      age: user.age,
      height: user.height,
      activityLevel: user.activityLevel,
    },
    latestRecord: latest,
    bodyComposition: latest ? buildBodyComposition(latest, user) : null,
    metabolic: latest ? buildMetabolic(latest, user) : null,
    score: latest ? buildScore(latest, user) : null,
    trend: records.length >= 2 ? buildTrend(records, user) : null,
    goals: buildGoals(goals, latest),
    diet: diets.length > 0 ? buildDietSummary(diets) : null,
    exercise: exercises.length > 0 ? buildExerciseSummary(exercises) : null,
    achievements: buildAchievementSection(achievements),
    recommendations: generateRecommendations(latest, user, records, diets, exercises, goals),
    overallSummary: buildOverallSummary(latest, user, records),
  }
}

// ==================== 各段构建函数 ====================

function buildBodyComposition(r: HealthRecord, u: User): BodyCompositionSection {
  const idealW = calcIdealWeight(u.height)
  return {
    bmi: r.bmi,
    bmiLabel: classifyBMI(r.bmi),
    bodyFat: r.bodyFat,
    waterRate: r.waterRate,
    muscleRate: r.muscleRate,
    visceralFat: r.visceralFat,
    visceralFatLabel: assessVisceralFat(r.visceralFat),
    boneMuscle: r.boneMuscle,
    waist: r.waist,
    bodyAge: r.bodyAge,
    actualAge: u.age,
    bodyType: r.bodyType || classifyBodyType(r.bmi, r.bodyFat, u.gender),
    bodyShape: classifyBodyShape(r.waist, u.gender),
    whtrLabel: assessWHtR(r.waist, u.height),
    idealWeight: round(idealW, 1),
    weightDiffFromIdeal: round(r.weight - idealW, 1),
    muscleAssessment: assessMuscle(r.boneMuscle, r.weight, u.gender),
  }
}

function buildMetabolic(r: HealthRecord, u: User): MetabolicSection {
  const h = calcBMR_Harris(r.weight, u.height, u.age, u.gender)
  const m = calcBMR_Mifflin(r.weight, u.height, u.age, u.gender)
  const c = calcBMR_China(r.weight, u.age, u.gender)
  const avg = (h + m + c) / 3
  return {
    bmrHarris: round(h, 0),
    bmrMifflin: round(m, 0),
    bmrChina: round(c, 0),
    bmrAvg: round(avg, 0),
    tdee: round(calcTDEE(avg, u.activityLevel), 0),
    activityLevel: u.activityLevel,
    activityFactor: getActivityFactor(u.activityLevel),
  }
}

function buildScore(r: HealthRecord, u: User): ScoreSection {
  const total = calcHealthScore(r.bmi, r.bodyFat, r.visceralFat, r.muscleRate, r.waterRate, u.gender)
  const dims = healthScoreBreakdown(r.bmi, r.bodyFat, r.visceralFat, r.muscleRate, r.waterRate, u.gender)

  const comments: Record<string, string> = {
    'BMI 体态': dimComment('BMI 体态', dims[0], '体重处于正常范围', 'BMI 偏离正常区间，需关注'),
    '体脂率': dimComment('体脂率', dims[1], '脂肪含量健康', '体脂偏高或偏低'),
    '内脏脂肪': dimComment('内脏脂肪', dims[2], '内脏脂肪水平良好', '内脏脂肪偏高，有代谢风险'),
    '肌肉量': dimComment('肌肉量', dims[3], '肌肉量充足', '肌肉量偏低，建议增加力量训练'),
    '水分率': dimComment('水分率', dims[4], '水分摄入充足', '建议增加饮水量'),
  }

  return {
    totalScore: total,
    level: scoreLevel(total),
    dimensions: dims.map(d => ({
      label: d.label,
      score: d.score,
      max: d.max,
      comment: comments[d.label] || '',
    })),
  }
}

function dimComment(_label: string, dim: { score: number; max: number }, good: string, bad: string): string {
  const ratio = dim.score / dim.max
  if (ratio >= 0.8) return `优秀 — ${good}`
  if (ratio >= 0.5) return `一般 — ${bad}`
  return `需改善 — ${bad}，建议重点调整`
}

function buildTrend(records: HealthRecord[], u: User): TrendSection {
  const dates = records.map(r => new Date(r.recordDate).getTime())
  const values = records.map(r => r.weight)
  const start = records[0]
  const end = records[records.length - 1]
  const change = end.weight - start.weight
  const daySpan = Math.max(1, (dates[dates.length - 1] - dates[0]) / (1000 * 60 * 60 * 24))

  const predW = predictTrend(dates, values, 30)
  const predBMI = !isNaN(predW) ? calcBMI(predW, u.height) : null

  return {
    recordCount: records.length,
    firstDate: start.recordDate,
    lastDate: end.recordDate,
    startWeight: start.weight,
    endWeight: end.weight,
    weightChange: round(change, 2),
    direction: trendDirection(dates, values),
    predictedWeight30d: !isNaN(predW) ? round(predW, 2) : null,
    predictedBMI30d: predBMI !== null ? round(predBMI, 2) : null,
    riskAssessment: predBMI !== null ? assessRisk(predBMI) : '数据不足，无法评估风险',
    weeklyAvgChange: round((change / daySpan) * 7, 3),
  }
}

function buildGoals(goals: Goal[], latest: HealthRecord | null): GoalSection {
  if (goals.length === 0) return { active: false, goals: [] }

  return {
    active: true,
    goals: goals.map(g => {
      const currentW = latest?.weight ?? g.targetValue
      const diff = currentW - g.targetValue
      let progressPct = 0
      if (g.goalType === '减脂' || g.goalType === '减重') {
        // 目标是降低体重，初始假设从更高处来
        progressPct = Math.min(100, Math.max(0, Math.round((1 - Math.abs(diff) / g.targetValue) * 100)))
      } else {
        progressPct = Math.min(100, Math.max(0, Math.round((1 - Math.abs(diff) / (g.targetValue * 0.3)) * 100)))
      }

      const predDays = predictGoalDays(currentW, g.targetValue, latest?.tdee ? latest.tdee * 0.2 : -500, g.goalType as any)

      return {
        goalType: g.goalType,
        targetValue: g.targetValue,
        startDate: g.startDate,
        endDate: g.endDate,
        currentStage: g.currentStage,
        progressPct,
        predictedDays: predDays,
        status: goalStatusText(g.goalType, diff, predDays),
      }
    }),
  }
}

function goalStatusText(type: string, diff: number, predDays: number): string {
  if (predDays === 0) return '已达成 ✓'
  if (predDays < 0) return predDays === -1 ? '难以达成' : '进度偏慢'
  if (type === '减脂' || type === '减重') {
    return diff > 0 ? `进行中（预计还需 ${predDays} 天）` : '已接近目标'
  }
  return `进行中（预计还需 ${predDays} 天）`
}

function buildDietSummary(diets: DietRecord[]): DietSummarySection {
  const totalCal = diets.reduce((s, d) => s + d.calories, 0)
  const totalPro = diets.reduce((s, d) => s + d.protein, 0)
  const totalCarb = diets.reduce((s, d) => s + d.carbs, 0)
  const totalF = diets.reduce((s, d) => s + d.fat, 0)

  // 按日期去重计算天数
  const uniqueDays = new Set(diets.map(d => d.recordDate)).size
  const avgCal = uniqueDays > 0 ? Math.round(totalCal / uniqueDays) : 0

  // 热量宏量比
  const macroTotal = (totalPro * 4 + totalCarb * 4 + totalF * 9) || 1
  const macroPct = {
    protein: Math.round((totalPro * 4) / macroTotal * 100),
    carbs: Math.round((totalCarb * 4) / macroTotal * 100),
    fat: Math.round((totalF * 9) / macroTotal * 100),
  }

  // Top 食物
  const foodMap = new Map<string, { calories: number; count: number }>()
  for (const d of diets) {
    const prev = foodMap.get(d.foodName) || { calories: 0, count: 0 }
    foodMap.set(d.foodName, { calories: prev.calories + d.calories, count: prev.count + 1 })
  }
  const topFoods = [...foodMap.entries()]
    .sort((a, b) => b[1].calories - a[1].calories)
    .slice(0, 8)
    .map(([name, v]) => ({ name, calories: v.calories, count: v.count }))

  // 三餐分布
  const mealMap = new Map<string, number>()
  for (const d of diets) {
    mealMap.set(d.mealType, (mealMap.get(d.mealType) || 0) + d.calories)
  }
  const mealOrder: Array<'早餐' | '午餐' | '晚餐' | '加餐'> = ['早餐', '午餐', '晚餐', '加餐']
  const mealDistribution = mealOrder.map(meal => ({
    meal,
    cal: mealMap.get(meal) || 0,
    pct: totalCal > 0 ? Math.round(((mealMap.get(meal) || 0) / totalCal) * 100) : 0,
  }))

  return {
    totalCalories: totalCal,
    avgDailyCalories: avgCal,
    totalProtein: Math.round(totalPro),
    totalCarbs: Math.round(totalCarb),
    totalFat: Math.round(totalF),
    dayCount: uniqueDays,
    macroPct,
    topFoods,
    mealDistribution,
  }
}

function buildExerciseSummary(exercises: ExerciseRecord[]): ExerciseSummarySection {
  const totalMin = exercises.reduce((s, e) => s + e.duration, 0)
  const totalCal = exercises.reduce((s, e) => s + e.caloriesBurned, 0)

  const exMap = new Map<string, { sessions: number; minutes: number; calories: number }>()
  for (const e of exercises) {
    const prev = exMap.get(e.exerciseType) || { sessions: 0, minutes: 0, calories: 0 }
    exMap.set(e.exerciseType, {
      sessions: prev.sessions + 1,
      minutes: prev.minutes + e.duration,
      calories: prev.calories + e.caloriesBurned,
    })
  }
  const topExercises = [...exMap.entries()]
    .sort((a, b) => b[1].minutes - a[1].minutes)
    .map(([type, v]) => ({ type, ...v }))

  const uniqueDays = new Set(exercises.map(e => e.recordDate)).size
  const weekSpan = Math.max(1, uniqueDays / 7)

  return {
    totalSessions: exercises.length,
    totalMinutes: totalMin,
    totalCaloriesBurned: totalCal,
    avgPerSession: exercises.length > 0 ? Math.round(totalMin / exercises.length) : 0,
    topExercises,
    weeklyFreq: Math.round(exercises.length / weekSpan * 10) / 10,
  }
}

function buildAchievementSection(achievements: Achievement[]): AchievementSection {
  return {
    totalEarned: achievements.length,
    badges: achievements.map(a => ({ name: a.badgeName, date: a.achievedDate })),
  }
}

// ==================== 建议生成 ====================

function generateRecommendations(
  latest: HealthRecord | null,
  user: User,
  _records: HealthRecord[],
  diets: DietRecord[],
  exercises: ExerciseRecord[],
  goals: Goal[],
): RecommendationItem[] {
  const recs: RecommendationItem[] = []

  if (!latest) return recs

  // ---- BMI 建议 ----
  if (latest.bmi >= 28) {
    recs.push({ category: '饮食', priority: '高', title: '控制总热量摄入', detail: `当前 BMI 为 ${latest.bmi.toFixed(1)}，属于肥胖范围。建议每日热量缺口保持在 300-500 kcal，优先减少精制碳水和添加糖。`, icon: '🍽️' })
  } else if (latest.bmi < 18.5) {
    recs.push({ category: '饮食', priority: '高', title: '增加营养密度', detail: `当前 BMI 为 ${latest.bmi.toFixed(1)}，偏瘦。建议适当增加优质蛋白和复合碳水摄入，配合力量训练增肌。`, icon: '🍽️' })
  }

  // ---- 体脂建议 ----
  const fatHigh = (user.gender === '男' && latest.bodyFat > 25) || (user.gender === '女' && latest.bodyFat > 32)
  if (fatHigh) {
    recs.push({ category: '运动', priority: '高', title: '增加有氧运动频率', detail: `体脂率 ${latest.bodyFat}% 偏高。建议每周进行 3-4 次中等强度有氧运动（如快走、游泳），每次 30-45 分钟。`, icon: '🏃' })
  }

  // ---- 内脏脂肪建议 ----
  if (latest.visceralFat > 8) {
    recs.push({ category: '生活方式', priority: '高', title: '警惕内脏脂肪过高', detail: `内脏脂肪等级 ${latest.visceralFat}，偏高。除运动外，需特别注意减少酒精、加工食品和高糖饮料的摄入。`, icon: '⚠️' })
  }

  // ---- 肌肉建议 ----
  const stdMuscle = user.gender === '男' ? 40.0 : 35.0
  if (latest.muscleRate < stdMuscle * 0.9) {
    recs.push({ category: '运动', priority: '中', title: '加强力量训练', detail: `肌肉率 ${latest.muscleRate}% 偏低。建议每周 2-3 次力量训练（深蹲、硬拉、推举等复合动作），配合充足蛋白质摄入（1.2-1.6g/kg 体重）。`, icon: '💪' })
  }

  // ---- 水分建议 ----
  const waterOk = (user.gender === '男' && latest.waterRate >= 50) || (user.gender === '女' && latest.waterRate >= 45)
  if (!waterOk) {
    recs.push({ category: '生活方式', priority: '中', title: '增加每日饮水量', detail: `水分率仅 ${latest.waterRate}%。建议每日饮水 2000-2500ml，分次少量饮用，避免一次性大量饮水。`, icon: '💧' })
  }

  // ---- 腰围建议 ----
  if ((user.gender === '男' && latest.waist >= 90) || (user.gender === '女' && latest.waist >= 85)) {
    recs.push({ category: '医疗', priority: '中', title: '关注中心性肥胖', detail: `腰围 ${latest.waist}cm 已超标，提示中心性肥胖风险。建议定期监测血压、血糖、血脂指标，必要时咨询医生。`, icon: '🏥' })
  }

  // ---- 饮食结构建议 ----
  if (diets.length > 0) {
    const totalCals = diets.reduce((s, d) => s + d.calories, 0)
    const totalFatCals = diets.reduce((s, d) => s + d.fat * 9, 0)
    const fatPct = totalCals > 0 ? (totalFatCals / totalCals) * 100 : 0
    if (fatPct > 35) {
      recs.push({ category: '饮食', priority: '中', title: '优化脂肪来源', detail: `饮食中脂肪供能占比约 ${Math.round(fatPct)}%，偏高。建议将烹饪油替换为橄榄油/亚麻籽油，减少油炸食品和肥肉摄入。`, icon: '🥑' })
    }
  }

  // ---- 运动不足建议 ----
  if (exercises.length === 0) {
    recs.push({ category: '运动', priority: '中', title: '开始建立运动习惯', detail: '目前暂无运动记录。建议从每天 20 分钟快走开始，逐步增加到每周 150 分钟中等强度活动。', icon: '🏃' })
  } else if (user.activityLevel === '久坐') {
    recs.push({ category: '生活方式', priority: '低', title: '提升日常活动量', detail: '活动等级为久坐。尝试每坐 1 小时起身活动 5 分钟，走楼梯代替电梯，增加非运动性热量消耗 NEAT。', icon: '\u{1F6B6}' })
  }

  // ---- 目标相关建议 ----
  if (goals.length === 0 && latest) {
    recs.push({ category: '生活方式', priority: '低', title: '设定健康管理目标', detail: '尚未设置任何健康目标。建议在目标计划中设定一个具体可达成的目标，如减重 3kg 或连续运动 21 天，让改善更有方向感。', icon: '\u{1F3AF}' })
  }

  return recs.slice(0, 12) // 最多 12 条建议
}

function buildOverallSummary(
  latest: HealthRecord | null,
  user: User,
  records: HealthRecord[],
): string {
  if (!latest) return '暂无足够数据生成总体评价，请完成至少一次健康数据录入后查看详细报告。'

  const score = calcHealthScore(latest.bmi, latest.bodyFat, latest.visceralFat, latest.muscleRate, latest.waterRate, user.gender)
  const level = scoreLevel(score)
  const ageDiff = latest.bodyAge - user.age

  const parts: string[] = []

  parts.push(`综合健康评分 ${score} 分（${level}）。`)

  if (ageDiff < 0) {
    parts.push(`身体年龄 ${latest.bodyAge} 岁，比实际年龄年轻 ${Math.abs(ageDiff)} 岁，身体状况优于同龄人。`)
  } else if (ageDiff > 0) {
    parts.push(`身体年龄 ${latest.bodyAge} 岁，比实际年龄老 ${ageDiff} 岁，需要引起重视并积极改善。`)
  } else {
    parts.push(`身体年龄与实际年龄基本持平，保持现有生活习惯即可。`)
  }

  if (records.length >= 2) {
    const change = latest.weight - records[0].weight
    if (Math.abs(change) > 0.3) {
      const dir = change > 0 ? '上升' : '下降'
      parts.push(`统计期内体重${dir} ${Math.abs(change).toFixed(1)} kg。`)
    }
  }

  const visLabel = assessVisceralFat(latest.visceralFat)
  if (visLabel !== '正常') {
    parts.push(`${visLabel}的内脏脂肪水平是需要重点关注的风险因素。`)
  }

  return parts.join('')
}
