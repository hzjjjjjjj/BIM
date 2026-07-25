<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { auth } from '@/stores/auth'
import { toast } from '@/stores/toast'
import type { ActivityLevel, Gender, User } from '@/types'

const router = useRouter()
const tab = ref<'login' | 'register'>('login')
const loading = ref(false)

const loginForm = reactive({ username: 'demo', password: 'demo123' })

const regForm = reactive({
  username: '', password: '', password2: '',
  gender: '男' as Gender, age: 28, height: 175, activity: '中度活动' as ActivityLevel,
})

const activities: ActivityLevel[] = ['久坐', '轻度活动', '中度活动', '重度活动', '极重度活动']

async function doLogin() {
  loading.value = true
  try {
    await auth.login(loginForm.username.trim(), loginForm.password)
    toast.success('欢迎回来，' + loginForm.username)
    router.push({ name: 'dashboard' })
  } catch (e) {
    toast.error((e as Error).message)
  } finally {
    loading.value = false
  }
}

async function doRegister() {
  if (!regForm.username.trim()) return toast.error('请填写用户名')
  if (regForm.password.length < 6) return toast.error('密码至少 6 位')
  if (regForm.password !== regForm.password2) return toast.error('两次密码不一致')
  loading.value = true
  const user: User = {
    username: regForm.username.trim(), gender: regForm.gender,
    age: regForm.age, height: regForm.height, activityLevel: regForm.activity,
  }
  try {
    await auth.register(user, regForm.password)
    toast.success('注册成功，已自动登录')
    router.push({ name: 'dashboard' })
  } catch (e) {
    toast.error((e as Error).message)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth">
    <!-- 品牌面板 -->
    <section class="auth-brand">
      <div class="brand-glow" />
      <div class="brand-inner">
        <div class="brand-logo">❦</div>
        <h2 class="brand-title">体质评估<br />与预测系统</h2>
        <p class="brand-slogan">记录每一次称重，读懂身体的语言。</p>
        <ul class="brand-list">
          <li><span>📊</span> 八维健康总览与评分</li>
          <li><span>📈</span> 体重体脂趋势可视化</li>
          <li><span>🔮</span> 线性回归预测未来走向</li>
          <li><span>🎯</span> 减脂增肌目标规划</li>
        </ul>
        <div class="brand-foot">v5 · Vue 3 + Vite + TypeScript</div>
      </div>
    </section>

    <!-- 表单面板 -->
    <section class="auth-form">
      <div class="form-card">
        <div class="tabs">
          <button :class="{ active: tab === 'login' }" @click="tab = 'login'">登录</button>
          <button :class="{ active: tab === 'register' }" @click="tab = 'register'">注册</button>
        </div>

        <!-- 登录 -->
        <form v-if="tab === 'login'" class="frm" @submit.prevent="doLogin">
          <label class="field">
            <span>用户名</span>
            <input v-model="loginForm.username" autocomplete="username" placeholder="用户名" />
          </label>
          <label class="field">
            <span>密码</span>
            <input v-model="loginForm.password" type="password" autocomplete="current-password" placeholder="密码" />
          </label>
          <button class="btn-primary" type="submit" :disabled="loading">
            {{ loading ? '登录中…' : '登 录' }}
          </button>
          <p class="demo-hint">体验账号：<b>demo</b> / <b>demo123</b>（已含 30 天样例数据）</p>
        </form>

        <!-- 注册 -->
        <form v-else class="frm" @submit.prevent="doRegister">
          <label class="field">
            <span>用户名</span>
            <input v-model="regForm.username" placeholder="设置用户名" />
          </label>
          <div class="row2">
            <label class="field">
              <span>密码</span>
              <input v-model="regForm.password" type="password" placeholder="至少 6 位" />
            </label>
            <label class="field">
              <span>确认密码</span>
              <input v-model="regForm.password2" type="password" placeholder="再次输入" />
            </label>
          </div>
          <div class="row2">
            <label class="field">
              <span>性别</span>
              <select v-model="regForm.gender">
                <option>男</option><option>女</option>
              </select>
            </label>
            <label class="field">
              <span>年龄</span>
              <input v-model.number="regForm.age" type="number" min="5" max="120" />
            </label>
          </div>
          <div class="row2">
            <label class="field">
              <span>身高 (cm)</span>
              <input v-model.number="regForm.height" type="number" min="100" max="220" />
            </label>
            <label class="field">
              <span>活动水平</span>
              <select v-model="regForm.activity">
                <option v-for="a in activities" :key="a">{{ a }}</option>
              </select>
            </label>
          </div>
          <button class="btn-primary" type="submit" :disabled="loading">
            {{ loading ? '创建中…' : '创 建 账 号' }}
          </button>
        </form>
      </div>
    </section>
  </div>
</template>

<style scoped>
.auth { display: flex; min-height: 100vh; }
.auth-brand {
  flex: 1.05; position: relative; overflow: hidden;
  background: linear-gradient(155deg, #157a5a 0%, #0f5c46 55%, #0c2a22 100%);
  color: #fff; display: grid; place-items: center; padding: var(--s-8);
}
.brand-glow {
  position: absolute; width: 460px; height: 460px; border-radius: 50%;
  background: radial-gradient(circle, rgba(255, 122, 92, 0.35), transparent 65%);
  top: -120px; right: -100px; filter: blur(10px);
}
.brand-inner { position: relative; max-width: 420px; }
.brand-logo {
  width: 60px; height: 60px; border-radius: 18px; display: grid; place-items: center;
  font-size: 30px; background: linear-gradient(140deg, var(--gold), var(--accent)); color: #0c2a22;
  box-shadow: 0 12px 30px -10px rgba(255, 122, 92, 0.6);
}
.brand-title { font-size: 38px; line-height: 1.1; margin: 22px 0 12px; font-weight: 800; letter-spacing: -0.02em; }
.brand-slogan { color: rgba(255, 255, 255, 0.72); font-size: 15px; margin-bottom: 28px; }
.brand-list { list-style: none; padding: 0; margin: 0; display: grid; gap: 14px; }
.brand-list li { display: flex; align-items: center; gap: 12px; font-size: 15px; color: rgba(255, 255, 255, 0.9); }
.brand-list span { font-size: 18px; }
.brand-foot { margin-top: 34px; font-size: 12.5px; color: rgba(255, 255, 255, 0.5); letter-spacing: 0.04em; }

.auth-form { flex: 1; display: grid; place-items: center; padding: var(--s-7); background: var(--bg); }
.form-card { width: 100%; max-width: 400px; background: var(--surface); border: 1px solid var(--border); border-radius: var(--r-xl); padding: var(--s-7); box-shadow: var(--sh-2); }
.tabs { display: flex; gap: 6px; background: var(--surface-2); padding: 5px; border-radius: var(--r-pill); margin-bottom: var(--s-6); }
.tabs button { flex: 1; border: none; background: none; padding: 10px; border-radius: var(--r-pill); font-weight: 700; font-size: 14px; color: var(--text-soft); cursor: pointer; transition: all 0.2s var(--ease-out); }
.tabs button.active { background: var(--surface); color: var(--primary-d); box-shadow: var(--sh-1); }

.frm { display: grid; gap: var(--s-4); }
.field { display: grid; gap: 6px; }
.field span { font-size: 12.5px; font-weight: 700; color: var(--text-soft); }
.field input, .field select {
  border: 1.5px solid var(--border); background: var(--surface); border-radius: var(--r-md);
  padding: 11px 13px; font-size: 14.5px; transition: border-color 0.2s, box-shadow 0.2s;
}
.field input:focus, .field select:focus { outline: none; border-color: var(--primary); box-shadow: 0 0 0 3px var(--primary-l); }
.row2 { display: grid; grid-template-columns: 1fr 1fr; gap: var(--s-3); }
.btn-primary {
  margin-top: 6px; border: none; cursor: pointer; color: #fff; font-weight: 800; font-size: 15px;
  padding: 13px; border-radius: var(--r-md); letter-spacing: 0.1em;
  background: linear-gradient(135deg, var(--primary), var(--primary-d));
  box-shadow: 0 10px 22px -10px rgba(31, 138, 109, 0.8); transition: transform 0.2s var(--ease-out), box-shadow 0.2s;
}
.btn-primary:hover:not(:disabled) { transform: translateY(-2px); box-shadow: 0 14px 28px -10px rgba(31, 138, 109, 0.9); }
.btn-primary:disabled { opacity: 0.6; cursor: progress; }
.demo-hint { text-align: center; font-size: 12.5px; color: var(--text-faint); margin: 4px 0 0; }

@media (max-width: 820px) {
  .auth { flex-direction: column; }
  .auth-brand { flex: none; padding: var(--s-7) var(--s-5); }
  .brand-title { font-size: 30px; }
  .brand-list { display: none; }
}
</style>
