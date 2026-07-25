<script setup lang="ts">
withDefaults(defineProps<{
  label: string
  value: string | number
  unit?: string
  icon?: string
  tint?: string   // 图标底色
  fg?: string     // 图标前景
  sub?: string
  trend?: 'up' | 'down' | 'flat'
  trendText?: string
}>(), {
  unit: '',
  icon: '◆',
  tint: 'var(--primary-l)',
  fg: 'var(--primary)',
  sub: '',
  trend: 'flat',
  trendText: '',
})
</script>

<template>
  <div class="mc card">
    <div class="mc-top">
      <span class="mc-icon" :style="{ background: tint, color: fg }">{{ icon }}</span>
      <span v-if="trend !== 'flat'" class="mc-trend" :class="trend">
        {{ trend === 'up' ? '▲' : '▼' }} {{ trendText }}
      </span>
    </div>
    <div class="mc-label">{{ label }}</div>
    <div class="mc-value tnum">
      {{ value }}<span v-if="unit" class="mc-unit">{{ unit }}</span>
    </div>
    <div v-if="sub" class="mc-sub">{{ sub }}</div>
  </div>
</template>

<style scoped>
.mc { padding: var(--s-5); display: grid; gap: 4px; transition: transform 0.25s var(--ease-out), box-shadow 0.25s var(--ease-out); }
.mc:hover { transform: translateY(-3px); box-shadow: var(--sh-2); }
.mc-top { display: flex; align-items: center; justify-content: space-between; }
.mc-icon {
  width: 38px; height: 38px; border-radius: 12px;
  display: grid; place-items: center; font-size: 18px;
}
.mc-trend { font-size: 12px; font-weight: 700; }
.mc-trend.up { color: var(--accent-d); }
.mc-trend.down { color: var(--primary-d); }
.mc-label { font-size: 13px; color: var(--text-soft); font-weight: 600; margin-top: 10px; }
.mc-value { font-family: var(--font-display); font-weight: 800; font-size: 28px; color: var(--text); line-height: 1.1; }
.mc-unit { font-size: 13px; color: var(--text-faint); font-weight: 600; margin-left: 3px; }
.mc-sub { font-size: 12px; color: var(--text-faint); }
</style>
