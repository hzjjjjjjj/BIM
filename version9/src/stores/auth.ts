// ============================================================
// 认证状态（轻量 reactive store，无需 Pinia）
// auth.user 直接是 User 对象（或 null），模板中可直接 auth.user?.x
// ============================================================

import { reactive, computed } from 'vue'
import { api } from '@/services/api'
import type { User } from '@/types'

const state = reactive<{ user: User | null; ready: boolean }>({
  user: null,
  ready: false,
})

export const auth = {
  state,
  get user(): User | null {
    return state.user
  },
  isAuthenticated: computed(() => !!state.user),

  async init(): Promise<void> {
    const username = api.getSession()
    if (username) {
      state.user = await api.getUser(username)
    }
    state.ready = true
  },

  async login(username: string, password: string): Promise<void> {
    const user = await api.login(username, password)
    api.setSession(user.username)
    state.user = user
  },

  async register(user: User, password: string): Promise<void> {
    const created = await api.register(user, password)
    api.setSession(created.username)
    state.user = created
  },

  logout(): void {
    api.clearSession()
    state.user = null
  },
}
