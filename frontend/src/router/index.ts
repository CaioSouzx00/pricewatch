import { createRouter, createWebHistory } from 'vue-router'
import { useAuth } from '../stores/auth'

declare module 'vue-router' {
  interface RouteMeta {
    requiresAuth?: boolean
    guestOnly?: boolean
  }
}

import AppLayout from '../layout/AppLayout.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      component: AppLayout,
      meta: { requiresAuth: true },
      children: [
        { path: '', name: 'dashboard', component: () => import('../views/DashboardView.vue') },
        { path: 'products', name: 'products', component: () => import('../views/ProductsView.vue') },
        { path: 'products/:id', name: 'product-detail', component: () => import('../views/ProductDetailView.vue') },
        { path: 'watchlist', name: 'watchlist', component: () => import('../views/WatchlistView.vue') },
        { path: 'alerts', name: 'alerts', component: () => import('../views/AlertsView.vue') },
      ]
    },
    { path: '/login', name: 'login', component: () => import('../views/LoginView.vue'), meta: { guestOnly: true } },
    {
      path: '/register',
      name: 'register',
      component: () => import('../views/RegisterView.vue'),
      meta: { guestOnly: true },
    },
    { path: '/:pathMatch(.*)*', redirect: { name: 'home' } },
  ],
})

/** Aceita apenas caminhos internos, evitando open redirect via `?redirect=`. */
export function safeRedirect(value: unknown): string {
  return typeof value === 'string' && value.startsWith('/') && !value.startsWith('//') ? value : '/'
}

router.beforeEach(async (to) => {
  const auth = useAuth()
  await auth.restore()

  if (to.meta.requiresAuth && !auth.isAuthenticated.value) {
    return { name: 'login', query: to.fullPath !== '/' ? { redirect: to.fullPath } : undefined }
  }
  if (to.meta.guestOnly && auth.isAuthenticated.value) {
    return safeRedirect(to.query.redirect)
  }
})

export default router
