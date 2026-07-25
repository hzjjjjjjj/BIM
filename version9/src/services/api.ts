// ============================================================
// 统一数据服务层
// 默认走 mock（localStorage）；设置 VITE_API_MODE=http 且 VITE_API_BASE
// 后可无缝切换到真实后端（Java/Spring Boot 等 REST API）
// ============================================================

import { mockDb } from './mockDb'
import type {
  Achievement, DietRecord, ExerciseRecord, Food, Goal, HealthRecord, User,
} from '@/types'

const MODE = (import.meta.env.VITE_API_MODE ?? 'mock') as 'mock' | 'http'
const BASE = import.meta.env.VITE_API_BASE ?? '/api'

// ---------- HTTP 模式（预留，对接真实后端） ----------
async function httpReq<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE}${path}`, {
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    ...init,
  })
  if (!res.ok) {
    const msg = await res.text().catch(() => res.statusText)
    throw new Error(msg || `请求失败 (${res.status})`)
  }
  return res.json() as Promise<T>
}

export const api = {
  mode: MODE,

  // ---- 认证 ----
  register: (u: User, password: string) => mockOrHttp(
    () => mockDb.register(u, password),
    () => httpReq<User>('/auth/register', { method: 'POST', body: JSON.stringify({ ...u, password }) }),
  ),
  login: (username: string, password: string) => mockOrHttp(
    () => mockDb.login(username, password),
    () => httpReq<User>('/auth/login', { method: 'POST', body: JSON.stringify({ username, password }) }),
  ),
  getUser: (username: string) => mockOrHttp(
    () => mockDb.getUser(username)!,
    () => httpReq<User>(`/users/${encodeURIComponent(username)}`),
  ),
  setSession: (username: string) => mockDb.setSession(username),
  getSession: () => mockDb.getSession(),
  clearSession: () => mockDb.clearSession(),

  // ---- 健康记录 ----
  addRecord: (r: HealthRecord) => mockOrHttp(
    () => mockDb.addRecord(r),
    () => httpReq<HealthRecord>('/records', { method: 'POST', body: JSON.stringify(r) }),
  ),
  getRecords: (username: string, days?: number) => mockOrHttp(
    () => mockDb.getRecords(username, days),
    () => httpReq<HealthRecord[]>(`/records/${encodeURIComponent(username)}${days ? `?days=${days}` : ''}`),
  ),
  getLatestRecord: (username: string) => mockOrHttp(
    () => mockDb.getLatestRecord(username),
    async () => (await httpReq<HealthRecord[]>(`/records/${encodeURIComponent(username)}?days=1`)).slice(-1)[0],
  ),

  // ---- 运动 ----
  addExercise: (e: ExerciseRecord) => mockOrHttp(
    () => mockDb.addExercise(e),
    () => httpReq<ExerciseRecord>('/exercise', { method: 'POST', body: JSON.stringify(e) }),
  ),
  getExercise: (username: string, days?: number) => mockOrHttp(
    () => mockDb.getExercise(username, days),
    () => httpReq<ExerciseRecord[]>(`/exercise/${encodeURIComponent(username)}${days ? `?days=${days}` : ''}`),
  ),
  getTodayExerciseCalories: (username: string) => mockOrHttp(
    () => mockDb.getTodayExerciseCalories(username),
    async () => (await httpReq<{ calories: number }>(`/exercise/${encodeURIComponent(username)}/today`)).calories,
  ),

  // ---- 目标 ----
  addGoal: (g: Goal) => mockOrHttp(
    () => mockDb.addGoal(g),
    () => httpReq<Goal>('/goals', { method: 'POST', body: JSON.stringify(g) }),
  ),
  getGoals: (username: string) => mockOrHttp(
    () => mockDb.getGoals(username),
    () => httpReq<Goal[]>(`/goals/${encodeURIComponent(username)}`),
  ),

  // ---- 饮食 ----
  addDiet: (d: DietRecord) => mockOrHttp(
    () => mockDb.addDiet(d),
    () => httpReq<DietRecord>('/diet', { method: 'POST', body: JSON.stringify(d) }),
  ),
  getDiet: (username: string, days?: number) => mockOrHttp(
    () => mockDb.getDiet(username, days),
    () => httpReq<DietRecord[]>(`/diet/${encodeURIComponent(username)}${days ? `?days=${days}` : ''}`),
  ),
  getTodayDietSummary: (username: string) => mockOrHttp(
    () => mockDb.getTodayDietSummary(username),
    async () => {
      const s = await httpReq<{ cal: number; protein: number; carbs: number; fat: number }>(
        `/diet/${encodeURIComponent(username)}/today`,
      )
      return [s.cal, s.protein, s.carbs, s.fat]
    },
  ),

  // ---- 成就 ----
  getAchievements: (username: string) => mockOrHttp(
    () => mockDb.getAchievements(username),
    () => httpReq<Achievement[]>(`/achievements/${encodeURIComponent(username)}`),
  ),
  checkAndGrantAchievements: (username: string) => mockOrHttp(
    () => mockDb.checkAndGrantAchievements(username),
    () => httpReq<string[]>(`/achievements/${encodeURIComponent(username)}/check`, { method: 'POST' }),
  ),

  // ---- 食物 ----
  getFoods: () => mockOrHttp(
    () => mockDb.getFoods(),
    () => httpReq<Food[]>('/foods'),
  ),

  isCheckedToday: (username: string) => mockOrHttp(
    () => mockDb.isCheckedToday(username),
    async () => (await httpReq<{ checked: boolean }>(`/records/${encodeURIComponent(username)}/checked`)).checked,
  ),
}

async function mockOrHttp<T>(mockFn: () => T | Promise<T>, httpFn: () => Promise<T>): Promise<T> {
  if (MODE === 'http') return httpFn()
  return await mockFn()
}
