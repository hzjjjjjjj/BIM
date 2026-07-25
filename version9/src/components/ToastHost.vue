<script setup lang="ts">
import { toast } from '@/stores/toast'
</script>

<template>
  <div class="toast-host" aria-live="polite">
    <transition-group name="toast">
      <div v-for="t in toast.state.items" :key="t.id" class="toast" :class="t.type">
        <span class="t-ic">{{ t.type === 'success' ? '✓' : t.type === 'error' ? '!' : 'i' }}</span>
        <span>{{ t.text }}</span>
      </div>
    </transition-group>
  </div>
</template>

<style scoped>
.toast-host {
  position: fixed; top: 18px; left: 50%; transform: translateX(-50%);
  z-index: 200; display: grid; gap: 10px; pointer-events: none;
}
.toast {
  display: flex; align-items: center; gap: 9px;
  padding: 11px 16px; border-radius: var(--r-pill);
  background: var(--ink); color: #fff; font-size: 13.5px; font-weight: 600;
  box-shadow: var(--sh-pop); border: 1px solid var(--ink-line);
}
.toast.success { background: var(--primary-d); }
.toast.error { background: var(--danger); }
.t-ic {
  width: 18px; height: 18px; border-radius: 99px; display: grid; place-items: center;
  background: rgba(255, 255, 255, 0.22); font-size: 12px; font-weight: 800;
}
.toast-enter-active, .toast-leave-active { transition: all 0.3s var(--ease-out); }
.toast-enter-from, .toast-leave-to { opacity: 0; transform: translateY(-10px); }
</style>
