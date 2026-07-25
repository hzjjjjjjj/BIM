<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import AppSidebar from '@/components/AppSidebar.vue'
import AppHeader from '@/components/AppHeader.vue'
import ToastHost from '@/components/ToastHost.vue'

const route = useRoute()
const title = computed(() => (route.meta.title as string) ?? '健康')
const drawer = ref(false)
</script>

<template>
  <div class="layout">
    <div class="layout-side" :class="{ open: drawer }">
      <AppSidebar @navigate="drawer = false" />
    </div>
    <div v-if="drawer" class="layout-scrim" @click="drawer = false" />

    <div class="layout-main">
      <AppHeader :title="title" @toggle-menu="drawer = !drawer" />
      <main class="layout-content">
        <RouterView />
      </main>
    </div>
    <ToastHost />
  </div>
</template>

<style scoped>
.layout { display: flex; min-height: 100vh; background: var(--bg); }
.layout-main { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.layout-content { padding: var(--s-7); max-width: 1240px; width: 100%; margin: 0 auto; }
.layout-scrim { display: none; }

@media (max-width: 860px) {
  .layout-side {
    position: fixed; inset: 0 auto 0 0; z-index: 60;
    transform: translateX(-100%); transition: transform 0.3s var(--ease-out);
    box-shadow: var(--sh-pop);
  }
  .layout-side.open { transform: translateX(0); }
  .layout-scrim { display: block; position: fixed; inset: 0; background: rgba(16, 33, 27, 0.45); z-index: 55; }
  .layout-content { padding: var(--s-5) var(--s-4); }
}
</style>
