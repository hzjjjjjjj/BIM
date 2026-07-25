// ============================================================
// 健康计算核心 — 完整移植自 Java HealthCalculator
// 所有公式与 Java 版逐行对齐，保证 web 端计算结果一致
// ============================================================

import type { ActivityLevel, Gender, GoalType, Intensity } from '@/types'

// ---------------- BMI ----------------
export function calcBMI(weightKg: number, heightCm: number): number {
  const h = heightCm / 100
  return weightKg / (h * h)
}

/** BMI 分类（中国标准） */
export function classifyBMI(bmi: number): string {
  if (bmi < 18.5) return '偏瘦'
  if (bmi < 24.0) return '正常'
  if (bmi < 28.0) return '超重'
  return '肥胖'
}

// ---------------- BMR（三种公式） ----------------
export function calcBMR_Harris(weight: number, height: number, age: number, gender: Gender): number {
  return gender === '男'
    ? 88.362 + 13.397 * weight + 4.799 * height - 5.677 * age
    : 447.593 + 9.247 * weight + 3.098 * height - 4.33 * age
}

export function calcBMR_Mifflin(weight: number, height: number, age: number, gender: Gender): number {
  const base = 10 * weight + 6.25 * height - 5 * age
  return gender === '男' ? base + 5 : base - 161
}

export function calcBMR_China(weight: number, age: number, gender: Gender): number {
  const base = weight * 24 - age * 5
  return gender === '男' ? base + 100 : base - 100
}

/** 三公式平均值 */
export function calcAvgBMR(weight: number, height: number, age: number, gender: Gender): number {
  return (calcBMR_Harris(weight, height, age, gender) +
    calcBMR_Mifflin(weight, height, age, gender) +
    calcBMR_China(weight, age, gender)) / 3
}

// ---------------- TDEE ----------------
export function getActivityFactor(level: ActivityLevel): number {
  switch (level) {
    case '久坐': return 1.2
    case '轻度活动': return 1.375
    case '中度活动': return 1.55
    case '重度活动': return 1.725
    case '极重度活动': return 1.9
    default: return 1.2
  }
}

export function calcTDEE(bmr: number, level: ActivityLevel): number {
  return bmr * getActivityFactor(level)
}

// ---------------- 内脏脂肪 ----------------
export function assessVisceralFat(level: number): string {
  if (level <= 4) return '正常'
  if (level <= 8) return '偏高'
  return '过高'
}

// ---------------- 骨骼肌肉量 ----------------
export function assessMuscle(boneMuscle: number, weight: number, gender: Gender): string {
  const standard = gender === '男' ? weight * 0.42 : weight * 0.36
  const ratio = boneMuscle / standard
  if (ratio < 0.9) return '偏低'
  if (ratio > 1.1) return '偏高'
  return '正常'
}

// ---------------- 身体年龄 ----------------
export function calcBodyAge(
  age: number, bodyFat: number, muscleRate: number,
  visceralFat: number, gender: Gender,
): number {
  let bodyAge = age
  const male = gender === '男'
  if ((male && bodyFat < 15) || (!male && bodyFat < 22)) bodyAge -= 5
  else if ((male && bodyFat > 25) || (!male && bodyFat > 32)) bodyAge += 5

  const stdMuscle = male ? 40.0 : 35.0
  if (muscleRate > stdMuscle * 1.1) bodyAge -= 3
  else if (muscleRate < stdMuscle * 0.9) bodyAge += 3

  if (visceralFat <= 4) bodyAge -= 2
  else if (visceralFat <= 8) bodyAge += 2
  else bodyAge += 5

  return Math.max(20, Math.min(60, bodyAge))
}

// ---------------- BMI + 体脂 综合体质分类 ----------------
export function classifyBodyType(bmi: number, bodyFat: number, gender: Gender): string {
  const male = gender === '男'
  const fatLow = male ? bodyFat < 12 : bodyFat < 20
  const fatHigh = male ? bodyFat > 25 : bodyFat > 32

  if (bmi < 18.5) return fatHigh ? '隐性肥胖型' : '消瘦型'
  if (bmi < 24.0) return fatHigh ? '隐性肥胖型' : '标准型'
  if (bmi < 28.0) return fatLow ? '肌肉型' : fatHigh ? '肥胖型' : '超重型'
  return fatLow ? '肌肉型' : '肥胖型'
}

// ---------------- 理想体重 ----------------
export function calcIdealWeight(heightCm: number): number {
  const h = heightCm / 100
  return h * h * 22
}

// ---------------- 腰围身高比 ----------------
export function assessWHtR(waist: number, height: number): string {
  const whtr = waist / height
  if (whtr < 0.4) return '偏瘦'
  if (whtr < 0.5) return '正常'
  if (whtr < 0.55) return '腹型肥胖风险'
  return '腹型肥胖'
}

export function classifyBodyShape(waist: number, gender: Gender): string {
  const male = gender === '男'
  if ((male && waist >= 90) || (!male && waist >= 85)) return '苹果型(中心性肥胖)'
  if ((male && waist < 85) || (!male && waist < 80)) return '梨型/标准型'
  return '轻度腹型肥胖'
}

// ---------------- 健康评分（五维加权 0-100） ----------------
export function calcHealthScore(
  bmi: number, bodyFat: number, visceralFat: number,
  muscleRate: number, waterRate: number, gender: Gender,
): number {
  const male = gender === '男'
  let score = 0

  if (bmi >= 18.5 && bmi < 24.0) score += 30
  else if ((bmi >= 24.0 && bmi < 28.0) || (bmi >= 17.0 && bmi < 18.5)) score += 20
  else score += 10

  const fatNormal = male ? (bodyFat >= 12 && bodyFat <= 25) : (bodyFat >= 20 && bodyFat <= 32)
  const fatSevere = male ? bodyFat > 30 : bodyFat > 38
  if (fatNormal) score += 25
  else if (fatSevere) score += 5
  else score += 15

  if (visceralFat <= 4) score += 20
  else if (visceralFat <= 8) score += 12
  else score += 5

  const stdMuscle = male ? 40.0 : 35.0
  if (muscleRate >= stdMuscle * 0.9 && muscleRate <= stdMuscle * 1.1) score += 15
  else if (muscleRate > stdMuscle * 1.1) score += 15
  else score += 8

  const waterNormal = male ? (waterRate >= 50 && waterRate <= 65) : (waterRate >= 45 && waterRate <= 60)
  if (waterNormal) score += 10
  else score += 5

  return score
}

export function scoreLevel(score: number): string {
  if (score >= 90) return '优秀'
  if (score >= 75) return '良好'
  if (score >= 60) return '及格'
  return '需改善'
}

export interface ScoreDim { label: string; score: number; max: number }

/** 健康评分五维拆解，便于前端分维度展示 */
export function healthScoreBreakdown(
  bmi: number, bodyFat: number, visceralFat: number,
  muscleRate: number, waterRate: number, gender: Gender,
): ScoreDim[] {
  const male = gender === '男'
  let bmiS = 10
  if (bmi >= 18.5 && bmi < 24.0) bmiS = 30
  else if ((bmi >= 24.0 && bmi < 28.0) || (bmi >= 17.0 && bmi < 18.5)) bmiS = 20

  const fatNormal = male ? (bodyFat >= 12 && bodyFat <= 25) : (bodyFat >= 20 && bodyFat <= 32)
  const fatSevere = male ? bodyFat > 30 : bodyFat > 38
  let fatS = fatNormal ? 25 : fatSevere ? 5 : 15

  let visS = visceralFat <= 4 ? 20 : visceralFat <= 8 ? 12 : 5

  const stdMuscle = male ? 40.0 : 35.0
  let musS = (muscleRate >= stdMuscle * 0.9 && muscleRate <= stdMuscle * 1.1) || muscleRate > stdMuscle * 1.1 ? 15 : 8

  const waterNormal = male ? (waterRate >= 50 && waterRate <= 65) : (waterRate >= 45 && waterRate <= 60)
  let waterS = waterNormal ? 10 : 5

  return [
    { label: 'BMI 体态', score: bmiS, max: 30 },
    { label: '体脂率', score: fatS, max: 25 },
    { label: '内脏脂肪', score: visS, max: 20 },
    { label: '肌肉量', score: musS, max: 15 },
    { label: '水分率', score: waterS, max: 10 },
  ]
}

// ---------------- 运动热量 ----------------
export function getMET(type: string): number {
  switch (type) {
    case '快走': return 3.5
    case '跑步': return 9.0
    case '游泳': return 7.0
    case '力量训练': return 5.0
    case '骑行': return 7.5
    case '瑜伽': return 3.0
    case '跳绳': return 12.0
    case '球类': return 6.0
    default: return 5.0
  }
}

export function calcExerciseCalories(
  type: string, duration: number, intensity: Intensity, weight: number,
): number {
  const met = getMET(type)
  const hours = duration / 60
  const factor = intensity === '低' ? 0.85 : intensity === '高' ? 1.15 : 1.0
  return Math.round(met * weight * hours * factor)
}

// ---------------- 预测算法 ----------------
function dayDiffMs(base: number, t: number): number {
  return (t - base) / (1000 * 60 * 60 * 24)
}

/** 线性回归趋势预测，数据不足返回 NaN */
export function predictTrend(dates: number[], values: number[], futureDays: number): number {
  const n = dates.length
  if (n < 3) return NaN
  const base = dates[0]
  let sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0
  for (let i = 0; i < n; i++) {
    const xi = dayDiffMs(base, dates[i])
    const yi = values[i]
    sumX += xi; sumY += yi; sumXY += xi * yi; sumX2 += xi * xi
  }
  const denom = n * sumX2 - sumX * sumX
  if (Math.abs(denom) < 1e-10) return values[n - 1]
  const k = (n * sumXY - sumX * sumY) / denom
  const b = (sumY - k * sumX) / n
  const lastX = dayDiffMs(base, dates[n - 1])
  return k * (lastX + futureDays) + b
}

export function trendDirection(dates: number[], values: number[]): string {
  const n = dates.length
  if (n < 3) return '数据不足'
  const base = dates[0]
  let sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0
  for (let i = 0; i < n; i++) {
    const xi = dayDiffMs(base, dates[i])
    const yi = values[i]
    sumX += xi; sumY += yi; sumXY += xi * yi; sumX2 += xi * xi
  }
  const denom = n * sumX2 - sumX * sumX
  if (Math.abs(denom) < 1e-10) return '趋于稳定'
  const k = (n * sumXY - sumX * sumY) / denom
  if (Math.abs(k) < 0.01) return '趋于稳定'
  return k > 0 ? '上升' : '下降'
}

/**
 * 目标达成天数预测
 * 返回 -1 无法达成，-2 进度过慢，0 已达标，正数=天数
 */
export function predictGoalDays(
  currentWeight: number, targetWeight: number,
  dailyCalorieDeficit: number, goalType: GoalType,
): number {
  if (Math.abs(dailyCalorieDeficit) < 100) return -2
  const diff = currentWeight - targetWeight
  if (goalType === '减脂' || goalType === '减重') {
    if (diff <= 0) return 0
    if (dailyCalorieDeficit <= 0) return -1
    return Math.ceil((diff * 7700) / dailyCalorieDeficit)
  } else if (goalType === '增肌') {
    if (diff >= 0) return 0
    return Math.ceil((Math.abs(diff) * 5500) / Math.abs(dailyCalorieDeficit))
  }
  return -1
}

export function assessRisk(predictedBMI30: number): string {
  if (predictedBMI30 >= 28.0)
    return '高风险 — 预测 30 天后 BMI 将进入肥胖区间，建议立即调整饮食和运动计划'
  if (predictedBMI30 >= 24.0 || predictedBMI30 < 18.5)
    return '中风险 — 预测 30 天后 BMI 偏离正常范围，需关注体重变化'
  return '低风险 — 预测 30 天后 BMI 维持在正常范围，继续保持'
}

// ---------------- 录入时自动补全派生字段 ----------------
export interface DerivedRecordInput {
  weight: number
  bodyFat: number
  waterRate: number
  muscleRate: number
  visceralFat: number
  boneMuscle: number
  waist: number
  gender: Gender
  age: number
  height: number
}

export function deriveRecord(input: DerivedRecordInput) {
  const bmi = calcBMI(input.weight, input.height)
  const bmr = calcAvgBMR(input.weight, input.height, input.age, input.gender)
  const tdee = calcTDEE(bmr, '久坐') // 活动系数由用户档案覆盖，这里给基准
  const bodyAge = calcBodyAge(input.age, input.bodyFat, input.muscleRate, input.visceralFat, input.gender)
  const bodyType = classifyBodyType(bmi, input.bodyFat, input.gender)
  return {
    bmi: round(bmi, 2),
    bmr: round(bmr, 2),
    tdee: round(tdee, 2),
    bodyAge,
    bodyType,
  }
}

export function round(v: number, digits = 1): number {
  const p = Math.pow(10, digits)
  return Math.round(v * p) / p
}
