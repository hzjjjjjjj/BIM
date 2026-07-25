import { createRouter, createWebHistory } from 'vue-router'
import { auth } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { public: true },
    },
    {
      path: '/',
      component: () => import('@/views/AppLayout.vue'),
      redirect: '/dashboard',
      children: [
        { path: 'dashboard', name: 'dashboard', component: () => import('@/views/DashboardView.vue'), meta: { title: '数据大屏' } },
        { path: 'input', name: 'input', component: () => import('@/views/DataInputView.vue'), meta: { title: '数据录入' } },
        { path: 'history', name: 'history', component: () => import('@/views/HistoryView.vue'), meta: { title: '历史趋势' } },
        { path: 'analysis', name: 'analysis', component: () => import('@/views/AnalysisView.vue'), meta: { title: '分析评估' } },
        { path: 'prediction', name: 'prediction', component: () => import('@/views/PredictionView.vue'), meta: { title: '预测分析' } },
        { path: 'goals', name: 'goals', component: () => import('@/views/GoalPlanView.vue'), meta: { title: '目标计划' } },
        { path: 'diet', name: 'diet', component: () => import('@/views/DietView.vue'), meta: { title: '饮食管理' } },
        { path: 'report', name: 'report', component: () => import('@/views/ReportView.vue'), meta: { title: '详细报告' } },
        { path: 'body3d', name: 'body3d', component: () => import('@/views/BodyModelView.vue'), meta: { title: '3D 体模' } },
        { path: 'achievements', name: 'achievements', component: () => import('@/views/AchievementView.vue'), meta: { title: '成就徽章' } },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/dashboard' },
  ],
})

router.beforeEach((to) => {
  if (!auth.isAuthenticated.value && !to.meta.public) {
    return { name: 'login' }
  }
  if (to.name === 'login' && auth.isAuthenticated.value) {
    return { name: 'dashboard' }
  }
  return true
})

export default router
