// ============================================================
// 领域类型定义 — 与 Java 版 health_db 表结构对齐
// ============================================================

export type Gender = '男' | '女'
export type ActivityLevel = '久坐' | '轻度活动' | '中度活动' | '重度活动' | '极重度活动'
export type Intensity = '低' | '中' | '高'
export type MealType = '早餐' | '午餐' | '晚餐' | '加餐'
export type GoalType = '减脂' | '减重' | '增肌' | '塑形' | '保持'

export interface User {
  username: string
  gender: Gender
  age: number
  height: number // cm
  activityLevel: ActivityLevel
  createdAt?: string
}

export interface HealthRecord {
  id?: number
  username: string
  recordDate: string // YYYY-MM-DD
  weight: number
  bodyFat: number
  waterRate: number
  muscleRate: number
  visceralFat: number
  boneMuscle: number
  bmr: number
  tdee: number
  bmi: number
  waist: number
  bodyAge: number
  bodyType: string
}

export interface ExerciseRecord {
  id?: number
  username: string
  recordDate: string
  exerciseType: string
  duration: number
  intensity: Intensity
  caloriesBurned: number
}

export interface Goal {
  id?: number
  username: string
  goalType: GoalType
  targetValue: number
  startDate: string
  endDate?: string
  currentStage?: number
}

export interface DietRecord {
  id?: number
  username: string
  recordDate: string
  mealType: MealType
  foodName: string
  calories: number
  protein: number
  carbs: number
  fat: number
}

export interface Achievement {
  id?: number
  username: string
  badgeName: string
  achievedDate: string
}

export interface Food {
  id?: number
  foodName: string
  calories: number
  protein: number
  carbs: number
  fat: number
}
