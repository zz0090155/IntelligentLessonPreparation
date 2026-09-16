import { createRouter, createWebHistory } from 'vue-router'
import LandingPage from '../views/LandingPage.vue'
import TeacherDashboard from '../views/TeacherDashboard.vue'
import StudentDashboard from '../views/StudentDashboard.vue'
import AuthView from '../views/AuthView.vue'
import AdminConsole from '../views/AdminConsole.vue'
import ResourceManager from '../views/ResourceManager.vue'
import KnowledgeManager from '../views/KnowledgeManager.vue'
import IterationManager from '../views/IterationManager.vue'
import TaskCenter from '../views/TaskCenter.vue'

const routes = [
  { path: '/', name: 'Landing', component: LandingPage },
  { path: '/auth/:role', name: 'Auth', component: AuthView },
  { path: '/teacher', name: 'TeacherDashboard', component: TeacherDashboard },
  { path: '/teacher/resources', name: 'ResourceManager', component: ResourceManager },
  { path: '/teacher/knowledge', name: 'KnowledgeManager', component: KnowledgeManager },
  { path: '/teacher/iterations', name: 'IterationManager', component: IterationManager },
  { path: '/teacher/tasks', name: 'TaskCenter', component: TaskCenter },
  { path: '/student', name: 'StudentDashboard', component: StudentDashboard },
  { path: '/admin', name: 'AdminConsole', component: AdminConsole },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to) => {
  if (to.name === 'TeacherDashboard' || to.name === 'ResourceManager' || to.name === 'KnowledgeManager' || to.name === 'IterationManager' || to.name === 'TaskCenter') {
    const token = localStorage.getItem('teacher_token')
    if (!token) {
      return {
        path: '/auth/teacher',
        query: { redirect: to.fullPath },
      }
    }
  }
  if (to.name === 'StudentDashboard') {
    const token = localStorage.getItem('student_token')
    if (!token) {
      return {
        path: '/auth/student',
        query: { redirect: to.fullPath },
      }
    }
  }
  return true
})

export default router
