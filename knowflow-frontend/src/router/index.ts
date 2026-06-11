import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '@/utils/token'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/dashboard' },
    { path: '/login', component: () => import('@/views/Login.vue'), meta: { public: true } },
    { path: '/register', component: () => import('@/views/Register.vue'), meta: { public: true } },
    {
      path: '/',
      component: () => import('@/layouts/MainLayout.vue'),
      children: [
        { path: 'dashboard', component: () => import('@/views/Dashboard.vue') },
        { path: 'kb', component: () => import('@/views/KnowledgeBaseList.vue') },
        { path: 'kb/:id', component: () => import('@/views/KnowledgeBaseDetail.vue') },
        { path: 'documents', component: () => import('@/views/DocumentManage.vue') },
        { path: 'chat', component: () => import('@/views/Chat.vue') },
        { path: 'settings', component: () => import('@/views/Settings.vue') },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/dashboard' },
  ],
})

router.beforeEach((to, _from, next) => {
  const token = getToken()
  if (to.meta.public && token && (to.path === '/login' || to.path === '/register')) {
    next('/dashboard')
  } else {
    next()
  }
})

export default router
