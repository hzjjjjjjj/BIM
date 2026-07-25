// ============================================================
// 本地 Mock 数据层（localStorage 持久化）
// 镜像 Java 版 DBUtil 行为；生产环境可将 api.ts 切到 http 模式
// ============================================================

import type {
  Achievement, DietRecord, ExerciseRecord, Food, Goal, HealthRecord, User,
} from '@/types'
import {
  calcAvgBMR, calcBodyAge, calcBMI, calcHealthScore, calcTDEE,
  classifyBodyType, round,
} from '@/lib/health'
import type { Gender } from '@/types'

const KEYS = {
  users: 'v5_users',
  records: 'v5_records',
  exercise: 'v5_exercise',
  goals: 'v5_goals',
  diet: 'v5_diet',
  achievements: 'v5_achievements',
  foods: 'v5_foods',
  session: 'v5_session',
  seeded: 'v5_seeded_v1',
}

// ---------- 简易 SHA-256（Web Crypto） ----------
async function sha256(text: string): Promise<string> {
  const data = new TextEncoder().encode(text)
  const buf = await crypto.subtle.digest('SHA-256', data)
  return Array.from(new Uint8Array(buf))
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('')
}

// ---------- 存储助手 ----------
function read<T>(key: string, fallback: T): T {
  try {
    const raw = localStorage.getItem(key)
    return raw ? (JSON.parse(raw) as T) : fallback
  } catch {
    return fallback
  }
}
function write<T>(key: string, value: T): void {
  localStorage.setItem(key, JSON.stringify(value))
}
function uid(): number {
  return Date.now() + Math.floor(Math.random() * 1000)
}
function todayStr(): string {
  return new Date().toISOString().slice(0, 10)
}
function dateMinus(days: number): string {
  const d = new Date()
  d.setDate(d.getDate() - days)
  return d.toISOString().slice(0, 10)
}

// ---------- 80 种食物种子 ----------
const FOODS: Food[] = [
  ['米饭',116,2.6,25.9,0.3],['面条',137,4.5,28.5,0.5],['馒头',221,7,47,1],['包子',220,7.5,38,4],
  ['饺子',250,8,30,10],['油条',388,6.9,43,20],['豆浆',33,3,1.5,1.8],['牛奶',54,3,4.5,3],
  ['鸡蛋',144,13,1.5,9],['鸡胸肉',133,31,0,2.8],['鸡腿肉',181,24,0,9],['猪肉瘦',143,20,0,6],
  ['猪肉肥',395,14,0,37],['牛肉瘦',125,26,0,2],['牛肉肥',332,18,0,28],['羊肉',205,20,0,13],
  ['鱼肉',113,20,0,3],['虾肉',85,18,0,1],['豆腐',81,8,3.5,4],['豆腐干',140,16,5,6],
  ['豆浆粉',422,18,55,12],['白菜',13,1.5,2,0.1],['菠菜',23,2.5,3,0.3],['西兰花',34,3,5,0.5],
  ['菜花',25,2,4,0.3],['黄瓜',15,0.8,3,0.1],['西红柿',18,0.9,4,0.2],['茄子',25,1,5,0.2],
  ['青椒',22,1,4.5,0.2],['土豆',77,2,17,0.1],['红薯',86,1.6,20,0.1],['山药',57,1.5,12,0.1],
  ['南瓜',23,1,5,0.1],['冬瓜',12,0.4,2.5,0.1],['萝卜',16,0.6,3.5,0.1],['胡萝卜',41,1,9.5,0.2],
  ['洋葱',40,1.1,9,0.1],['大蒜',149,6,33,0.5],['姜',80,1.8,17,0.8],['苹果',52,0.3,14,0.2],
  ['香蕉',89,1.1,23,0.3],['橙子',47,0.9,12,0.1],['西瓜',30,0.6,7.5,0.1],['哈密瓜',34,0.5,8,0.1],
  ['葡萄',69,0.7,18,0.2],['草莓',32,0.7,7.5,0.3],['蓝莓',57,0.7,14.5,0.3],['桃子',39,0.9,10,0.1],
  ['梨',42,0.4,10,0.1],['李子',46,0.7,12,0.3],['樱桃',50,1,12,0.3],['芒果',60,0.8,15,0.4],
  ['火龙果',60,1.2,14,0.4],['猕猴桃',61,1,15,0.5],['柠檬',29,1.1,6,0.3],['核桃',654,15,14,65],
  ['花生',567,26,16,49],['瓜子',600,24,12,53],['黑芝麻',559,17,12,50],['小米',358,9,75,3],
  ['玉米',112,3.5,22,1.5],['燕麦',389,17,66,7],['荞麦',337,13,71,3],['意面',131,5,25,0.5],
  ['蛋糕',348,5,45,17],['面包',265,9,50,3],['饼干',450,6,65,18],['巧克力',546,4,60,32],
  ['冰淇淋',207,3.5,24,11],['酸奶',72,3.5,8,3],['奶酪',360,25,2,28],['黄油',717,0.5,0,81],
  ['植物油',884,0,0,100],['酱油',60,2,10,0],['醋',18,0.4,4,0],['蜂蜜',304,0.3,82,0],
  ['白糖',387,0,100,0],['红糖',380,0,95,0],['盐',0,0,0,0],['茶叶',0,0,0,0],['咖啡',1,0.1,0,0],
].map(([foodName, calories, protein, carbs, fat]) => ({
  foodName: foodName as string,
  calories: calories as number,
  protein: protein as number,
  carbs: carbs as number,
  fat: fat as number,
}))

function seed(): void {
  if (localStorage.getItem(KEYS.seeded)) return
  write(KEYS.foods, FOODS)

  // 体验账号 demo / demo123 + 30 天样例数据
  const demoUser: User = {
    username: 'demo', gender: '男', age: 28, height: 175, activityLevel: '中度活动',
  }
  const users = read<User[]>(KEYS.users, [])
  users.push(demoUser)
  write(KEYS.users, users)

  const records: HealthRecord[] = []
  const startWeight = 74
  const endWeight = 68.5
  for (let i = 29; i >= 0; i--) {
    const t = (29 - i) / 29
    const weight = round(startWeight + (endWeight - startWeight) * t + Math.sin(i) * 0.4, 1)
    const bodyFat = round(26 - 5 * t + Math.cos(i) * 0.3, 1)
    const waterRate = round(55 + 3 * t, 1)
    const muscleRate = round(38 + 2 * t, 1)
    const visceralFat = Math.max(3, Math.round(9 - 4 * t))
    const boneMuscle = round(weight * 0.42, 1)
    const waist = round(88 - 8 * t, 1)
    const bmi = round(calcBMI(weight, demoUser.height), 2)
    const bmr = calcAvgBMR(weight, demoUser.height, demoUser.age, demoUser.gender)
    const tdee = calcTDEE(bmr, demoUser.activityLevel)
    const bodyAge = calcBodyAge(demoUser.age, bodyFat, muscleRate, visceralFat, demoUser.gender)
    const bodyType = classifyBodyType(bmi, bodyFat, demoUser.gender)
    records.push({
      id: uid(), username: 'demo', recordDate: dateMinus(i),
      weight, bodyFat, waterRate, muscleRate, visceralFat, boneMuscle,
      bmr: round(bmr, 2), tdee: round(tdee, 2), bmi, waist, bodyAge, bodyType,
    })
  }
  write(KEYS.records, records)

  // 样例目标
  const goals: Goal[] = [
    { id: uid(), username: 'demo', goalType: '减脂', targetValue: 66, startDate: dateMinus(20), currentStage: 2 },
    { id: uid(), username: 'demo', goalType: '塑形', targetValue: 18, startDate: dateMinus(20), currentStage: 1 },
  ]
  write(KEYS.goals, goals)

  // 样例饮食（最近几天）
  const diet: DietRecord[] = [
    { id: uid(), username: 'demo', recordDate: todayStr(), mealType: '早餐', foodName: '鸡蛋', calories: 144, protein: 13, carbs: 1.5, fat: 9 },
    { id: uid(), username: 'demo', recordDate: todayStr(), mealType: '早餐', foodName: '牛奶', calories: 54, protein: 3, carbs: 4.5, fat: 3 },
    { id: uid(), username: 'demo', recordDate: todayStr(), mealType: '午餐', foodName: '米饭', calories: 116, protein: 2.6, carbs: 25.9, fat: 0.3 },
    { id: uid(), username: 'demo', recordDate: todayStr(), mealType: '午餐', foodName: '鸡胸肉', calories: 133, protein: 31, carbs: 0, fat: 2.8 },
    { id: uid(), username: 'demo', recordDate: todayStr(), mealType: '晚餐', foodName: '西兰花', calories: 34, protein: 3, carbs: 5, fat: 0.5 },
  ]
  write(KEYS.diet, diet)

  // 样例运动
  const exercise: ExerciseRecord[] = [
    { id: uid(), username: 'demo', recordDate: todayStr(), exerciseType: '跑步', duration: 30, intensity: '中', caloriesBurned: 315 },
    { id: uid(), username: 'demo', recordDate: dateMinus(1), exerciseType: '力量训练', duration: 40, intensity: '高', caloriesBurned: 280 },
  ]
  write(KEYS.exercise, exercise)

  // 样例成就
  const ach: Achievement[] = [
    { id: uid(), username: 'demo', badgeName: '初次打卡', achievedDate: dateMinus(29) },
    { id: uid(), username: 'demo', badgeName: '连续七天', achievedDate: dateMinus(22) },
    { id: uid(), username: 'demo', badgeName: '减脂达标', achievedDate: dateMinus(5) },
  ]
  write(KEYS.achievements, ach)

  localStorage.setItem(KEYS.seeded, '1')
}

// 确保体验账号 demo 始终存在且密码可用
// （修复 seed 遗漏凭证的问题，并兼容已 seeded 但缺凭证的旧数据）
async function ensureDemoAccount(): Promise<void> {
  const users = read<User[]>(KEYS.users, [])
  if (!users.some((x) => x.username === 'demo')) {
    users.push({ username: 'demo', gender: '男', age: 28, height: 175, activityLevel: '中度活动' })
    write(KEYS.users, users)
  }
  const creds = read<Record<string, { salt: string; hash: string }>>('v5_creds', {})
  if (!creds['demo']) {
    const salt = 'demo-salt-v1'
    creds['demo'] = { salt, hash: await sha256('demo123' + salt) }
    write('v5_creds', creds)
  }
}

// 确保种子完成
seed()
ensureDemoAccount()

// ============================================================
// 公开 API（与 http 模式签名一致）
// ============================================================

export const mockDb = {
  // ---- 认证 ----
  async register(u: User, password: string): Promise<User> {
    const users = read<User[]>(KEYS.users, [])
    if (users.some((x) => x.username === u.username)) {
      throw new Error('用户名已存在')
    }
    users.push(u)
    write(KEYS.users, users)
    const salt = Math.random().toString(36).slice(2)
    const hash = await sha256(password + salt)
    const creds = read<Record<string, { salt: string; hash: string }>>('v5_creds', {})
    creds[u.username] = { salt, hash }
    write('v5_creds', creds)
    return u
  },

  async login(username: string, password: string): Promise<User> {
    const users = read<User[]>(KEYS.users, [])
    const user = users.find((x) => x.username === username)
    if (!user) throw new Error('用户不存在')
    const creds = read<Record<string, { salt: string; hash: string }>>('v5_creds', {})
    const c = creds[username]
    if (!c) throw new Error('账号未设置密码，请使用体验账号或重新注册')
    const hash = await sha256(password + c.salt)
    if (hash !== c.hash) throw new Error('密码错误')
    return user
  },

  getUser(username: string): User | undefined {
    return read<User[]>(KEYS.users, []).find((x) => x.username === username)
  },

  // ---- 健康记录 ----
  async addRecord(r: HealthRecord): Promise<HealthRecord> {
    const list = read<HealthRecord[]>(KEYS.records, [])
    const rec: HealthRecord = { ...r, id: uid() }
    list.push(rec)
    write(KEYS.records, list)
    this.checkAndGrantAchievements(r.username)
    return rec
  },

  getRecords(username: string, days?: number): HealthRecord[] {
    const list = read<HealthRecord[]>(KEYS.records, []).filter((x) => x.username === username)
    list.sort((a, b) => a.recordDate.localeCompare(b.recordDate))
    return days ? list.slice(-days) : list
  },

  getLatestRecord(username: string): HealthRecord | undefined {
    return this.getRecords(username).slice(-1)[0]
  },

  // ---- 运动 ----
  async addExercise(e: ExerciseRecord): Promise<ExerciseRecord> {
    const list = read<ExerciseRecord[]>(KEYS.exercise, [])
    const rec = { ...e, id: uid() }
    list.push(rec)
    write(KEYS.exercise, list)
    return rec
  },
  getExercise(username: string, days?: number): ExerciseRecord[] {
    const list = read<ExerciseRecord[]>(KEYS.exercise, []).filter((x) => x.username === username)
    list.sort((a, b) => a.recordDate.localeCompare(b.recordDate))
    return days ? list.slice(-days) : list
  },
  getTodayExerciseCalories(username: string): number {
    return this.getExercise(username)
      .filter((x) => x.recordDate === todayStr())
      .reduce((s, x) => s + (x.caloriesBurned || 0), 0)
  },

  // ---- 目标 ----
  async addGoal(g: Goal): Promise<Goal> {
    const list = read<Goal[]>(KEYS.goals, [])
    const rec = { ...g, id: uid() }
    list.push(rec)
    write(KEYS.goals, list)
    return rec
  },
  getGoals(username: string): Goal[] {
    return read<Goal[]>(KEYS.goals, []).filter((x) => x.username === username)
  },

  // ---- 饮食 ----
  async addDiet(d: DietRecord): Promise<DietRecord> {
    const list = read<DietRecord[]>(KEYS.diet, [])
    const rec = { ...d, id: uid() }
    list.push(rec)
    write(KEYS.diet, list)
    return rec
  },
  getDiet(username: string, days?: number): DietRecord[] {
    const list = read<DietRecord[]>(KEYS.diet, []).filter((x) => x.username === username)
    list.sort((a, b) => a.recordDate.localeCompare(b.recordDate))
    return days ? list.slice(-days) : list
  },
  getTodayDietSummary(username: string): [number, number, number, number] {
    const list = this.getDiet(username).filter((x) => x.recordDate === todayStr())
    const cal = list.reduce((s, x) => s + (x.calories || 0), 0)
    const p = list.reduce((s, x) => s + (x.protein || 0), 0)
    const c = list.reduce((s, x) => s + (x.carbs || 0), 0)
    const f = list.reduce((s, x) => s + (x.fat || 0), 0)
    return [cal, round(p, 1), round(c, 1), round(f, 1)]
  },

  // ---- 成就 ----
  getAchievements(username: string): Achievement[] {
    return read<Achievement[]>(KEYS.achievements, []).filter((x) => x.username === username)
  },
  async grantAchievement(username: string, badge: string): Promise<void> {
    const list = read<Achievement[]>(KEYS.achievements, [])
    if (list.some((x) => x.username === username && x.badgeName === badge)) return
    list.push({ id: uid(), username, badgeName: badge, achievedDate: todayStr() })
    write(KEYS.achievements, list)
  },
  async checkAndGrantAchievements(username: string): Promise<string[]> {
    const granted: string[] = []
    const records = this.getRecords(username)
    const grant = async (badge: string) => {
      if (!this.getAchievements(username).some((a) => a.badgeName === badge)) {
        await this.grantAchievement(username, badge)
        granted.push(badge)
      }
    }
    if (records.length >= 1) await grant('初次打卡')
    if (records.length >= 7) await grant('坚持一周')
    if (records.length >= 30) await grant('月度达人')
    const latest = records.slice(-1)[0]
    if (latest) {
      const u = this.getUser(username)
      const g = (u?.gender ?? '男') as Gender
      const score = calcHealthScore(latest.bmi, latest.bodyFat, latest.visceralFat, latest.muscleRate, latest.waterRate, g)
      if (score >= 90) await grant('健康达人')
    }
    return granted
  },

  // ---- 食物库 ----
  getFoods(): Food[] {
    return read<Food[]>(KEYS.foods, [])
  },

  // ---- 打卡状态 ----
  isCheckedToday(username: string): boolean {
    return !!this.getLatestRecord(username) && this.getLatestRecord(username)!.recordDate === todayStr()
  },

  // ---- 会话 ----
  setSession(username: string): void {
    write(KEYS.session, { username, at: Date.now() })
  },
  getSession(): string | null {
    const s = read<{ username: string } | null>(KEYS.session, null)
    return s?.username ?? null
  },
  clearSession(): void {
    localStorage.removeItem(KEYS.session)
  },
}

export type MockDb = typeof mockDb
