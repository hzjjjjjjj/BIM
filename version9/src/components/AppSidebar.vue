<script setup lang="ts">
import { RouterLink } from 'vue-router'
import { NAV } from '@/nav'
import { auth } from '@/stores/auth'

const emit = defineEmits<{ (e: 'navigate'): void }>()
</script>

<template>
  <aside class="sb">
    <div class="sb-brand">
      <div class="sb-logo">❦</div>
      <div class="sb-brand-txt">
        <div class="sb-name">体质评估</div>
        <div class="sb-tag">BMI · v6</div>
      </div>
    </div>

    <nav class="sb-nav" aria-label="主导航">
      <RouterLink
        v-for="item in NAV"
        :key="item.name"
        :to="item.path"
        class="sb-item"
        @click="emit('navigate')"
      >
        <span class="sb-ic">{{ item.icon }}</span>
        <span class="sb-label">{{ item.label }}</span>
        <span class="sb-hint">{{ item.hint }}</span>
      </RouterLink>
    </nav>

    <div class="sb-foot">
      <div class="sb-user">
        <span class="sb-avatar">{{ auth.user?.username?.charAt(0)?.toUpperCase() }}</span>
        <div class="sb-user-txt">
          <div class="sb-uname">{{ auth.user?.username }}</div>
          <div class="sb-umeta">{{ auth.user?.gender }} · {{ auth.user?.age }}岁 · {{ auth.user?.height }}cm</div>
        </div>
      </div>
    </div>
  </aside>
</template>

<style scoped>
.sb {
  width: 256px; flex: none; height: 100vh; position: sticky; top: 0;
  background: linear-gradient(170deg, var(--ink) 0%, var(--ink-2) 60%, var(--ink-3) 100%);
  color: var(--ink-text);
  display: flex; flex-direction: column;
  padding: var(--s-6) var(--s-4);
  gap: var(--s-5);
}
.sb-brand { display: flex; align-items: center; gap: 12px; padding: 4px 6px 0; }
.sb-logo {
  width: 42px; height: 42px; border-radius: 13px; flex: none;
  display: grid; place-items: center; font-size: 22px; color: #fff;
  background: linear-gradient(140deg, var(--primary), var(--accent));
  box-shadow: 0 6px 18px -6px rgba(31, 138, 109, 0.7);
}
.sb-name { font-family: var(--font-display); font-weight: 800; font-size: 17px; color: #fff; letter-spacing: -0.01em; }
.sb-tag { font-size: 11.5px; color: var(--ink-text-soft); letter-spacing: 0.06em; }

.sb-nav { display: flex; flex-direction: column; gap: 4px; margin-top: 4px; }
.sb-item {
  position: relative; display: grid; grid-template-columns: 24px 1fr; grid-template-rows: auto auto;
  column-gap: 12px; align-items: center;
  padding: 11px 12px; border-radius: var(--r-md);
  color: var(--ink-text-soft); transition: background 0.2s var(--ease-out), color 0.2s;
  overflow: hidden;
}
.sb-item .sb-ic { grid-row: 1 / 3; font-size: 18px; }
.sb-label { font-weight: 700; font-size: 14px; color: var(--ink-text); }
.sb-hint { font-size: 11px; color: var(--ink-text-soft); }
.sb-item:hover { background: rgba(255, 255, 255, 0.06); color: #fff; }
.sb-item.router-link-active {
  background: rgba(255, 255, 255, 0.1);
  box-shadow: inset 3px 0 0 var(--accent);
}
.sb-item.router-link-active .sb-label { color: #fff; }

.sb-foot { margin-top: auto; }
.sb-user {
  display: flex; align-items: center; gap: 11px;
  padding: 11px 12px; border-radius: var(--r-md);
  background: rgba(255, 255, 255, 0.05); border: 1px solid var(--ink-line);
}
.sb-avatar {
  width: 36px; height: 36px; border-radius: 10px; flex: none;
  display: grid; place-items: center; font-weight: 800; color: var(--ink);
  background: linear-gradient(140deg, var(--gold), var(--accent)); font-size: 16px;
}
.sb-uname { font-weight: 700; font-size: 13.5px; color: #fff; }
.sb-umeta { font-size: 11px; color: var(--ink-text-soft); }

@media (max-width: 860px) {
  .sb { width: 100%; height: auto; position: relative; flex-direction: row; flex-wrap: wrap; align-items: center; padding: 10px 12px; gap: 8px; }
  .sb-brand { flex: none; }
  .sb-nav { flex-direction: row; flex-wrap: wrap; flex: 1; margin: 0; gap: 4px; }
  .sb-item { grid-template-columns: 1fr; grid-template-rows: auto auto; justify-items: center; text-align: center; padding: 8px 10px; }
  .sb-item .sb-ic { grid-row: auto; }
  .sb-hint { display: none; }
  .sb-foot { margin: 0; width: 100%; }
  .sb-user { width: 100%; }
}
</style>
