import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import { auth } from './stores/auth'
import './style.css'

auth.init().then(() => {
  const app = createApp(App)
  app.use(router)
  app.mount('#app')
})
