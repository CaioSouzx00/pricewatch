import { createRouter, createWebHistory } from 'vue-router'
import { useAuth } from '../stores/auth'
import AppLayout from '../layout/AppLayout.vue'

declare module 'vue-router' {
  interface RouteMeta {
    requiresAuth?: boolean
    guestOnly?: boolean
    title?: string
  }
}

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      component: AppLayout,
      meta: { requiresAuth: true },
      children: [
        { path: '', name: 'dashboard', component: () => import('../views/DashboardView.vue'), meta: { title: 'Dashboard' } },
        { path: 'products', name: 'products', component: () => import('../views/ProductsView.vue'), meta: { title: 'Produtos' } },
        { path: 'products/:id', name: 'product-detail', component: () => import('../views/ProductDetailView.vue'), meta: { title: 'Detalhes do Ativo' } },
        { path: 'watchlist', name: 'watchlist', component: () => import('../views/WatchlistView.vue'), meta: { title: 'Watchlist' } },
        { path: 'alerts', name: 'alerts', component: () => import('../views/AlertsView.vue'), meta: { title: 'Alertas' } },
        { path: '404', name: 'not-found', component: () => import('../views/NotFoundView.vue'), meta: { title: 'Não Encontrado' } },
      ]
    },
    { path: '/login', name: 'login', component: () => import('../views/LoginView.vue'), meta: { guestOnly: true, title: 'Acesso ao Terminal' } },
    {
      path: '/register',
      name: 'register',
      component: () => import('../views/RegisterView.vue'),
      meta: { guestOnly: true, title: 'Criar Conta' },
    },
    {
      path: '/:pathMatch(.*)*',
      component: AppLayout,
      children: [
        { path: '', component: () => import('../views/NotFoundView.vue'), meta: { title: '404 - Página Não Encontrada' } }
      ]
    },
  ],
})

/** Aceita apenas caminhos internos, evitando open redirect via `?redirect=`. */
export function safeRedirect(value: unknown): string {
  return typeof value === 'string' && value.startsWith('/') && !value.startsWith('//') ? value : '/'
}

router.beforeEach(async (to) => {
  const auth = useAuth()
  await auth.restore()

  if (to.meta.title) {
    document.title = `${to.meta.title} // PriceWatch Terminal`
  }

  if (to.meta.requiresAuth && !auth.isAuthenticated.value) {
    return { name: 'login', query: to.fullPath !== '/' ? { redirect: to.fullPath } : undefined }
  }
  if (to.meta.guestOnly && auth.isAuthenticated.value) {
    return safeRedirect(to.query.redirect)
  }
})

export default router
