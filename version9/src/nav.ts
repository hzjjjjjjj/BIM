export interface NavItem {
  name: string
  path: string
  label: string
  icon: string
  hint: string
}

export const NAV: NavItem[] = [
  { name: 'dashboard', path: '/dashboard', label: '数据大屏', icon: '📊', hint: '健康总览与评分' },
  { name: 'input', path: '/input', label: '数据录入', icon: '✍️', hint: '每日称重打卡' },
  { name: 'history', path: '/history', label: '历史趋势', icon: '📈', hint: '指标变化曲线' },
  { name: 'analysis', path: '/analysis', label: '分析评估', icon: '🔬', hint: '体质成分解读' },
  { name: 'prediction', path: '/prediction', label: '预测分析', icon: '🔮', hint: '未来趋势预估' },
  { name: 'goals', path: '/goals', label: '目标计划', icon: '🎯', hint: '减脂增肌规划' },
  { name: 'diet', path: '/diet', label: '饮食管理', icon: '🥗', hint: '热量与营养' },
  { name: 'report', path: '/report', label: '详细报告', icon: '📋', hint: '综合健康报告' },
  { name: 'body3d', path: '/body3d', label: '3D 体模', icon: '🧍', hint: '三维体质孪生' },
  { name: 'achievements', path: '/achievements', label: '成就徽章', icon: '🏆', hint: '坚持的奖励' },
]
