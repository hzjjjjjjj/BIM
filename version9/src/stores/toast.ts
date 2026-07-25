import { reactive } from 'vue'

export interface Toast {
  id: number
  text: string
  type: 'success' | 'error' | 'info'
}

const state = reactive<{ items: Toast[] }>({ items: [] })
let seq = 0

export const toast = {
  state,
  push(text: string, type: Toast['type'] = 'info', ms = 2600) {
    const id = ++seq
    state.items.push({ id, text, type })
    setTimeout(() => {
      const i = state.items.findIndex((t) => t.id === id)
      if (i >= 0) state.items.splice(i, 1)
    }, ms)
  },
  success(text: string) { this.push(text, 'success') },
  error(text: string) { this.push(text, 'error', 3600) },
  info(text: string) { this.push(text, 'info') },
}
