import { computed, reactive } from 'vue'
import { api, setUnauthorizedHandler, TOKEN_KEY } from '../api/client'

export interface User {
  id: number
  email: string
  name: string
  createdAt: string
}

interface AuthResponse {
  token: string
  user: User
}

const state = reactive<{ token: string | null; user: User | null }>({
  token: localStorage.getItem(TOKEN_KEY),
  user: null,
})

function setSession(res: AuthResponse) {
  state.token = res.token
  state.user = res.user
  localStorage.setItem(TOKEN_KEY, res.token)
}

function logout() {
  state.token = null
  state.user = null
  localStorage.removeItem(TOKEN_KEY)
}

setUnauthorizedHandler(() => {
  logout()
  // Import dinâmico evita dependência circular com o router.
  import('../router').then(({ default: router }) => {
    const current = router.currentRoute.value
    if (current.meta.requiresAuth) router.replace({ name: 'login', query: { redirect: current.fullPath } })
  })
})

export const useAuth = () => ({
  isAuthenticated: computed(() => !!state.token),
  user: computed(() => state.user),

  async login(email: string, password: string) {
    setSession(await api<AuthResponse>('/auth/login', { body: { email, password } }))
  },

  async register(name: string, email: string, password: string) {
    setSession(await api<AuthResponse>('/auth/register', { body: { name, email, password } }))
  },

  /** Restaura o usuário a partir do token salvo; limpa a sessão se o token for inválido. */
  async restore() {
    if (!state.token || state.user) return
    try {
      state.user = await api<User>('/auth/me')
    } catch {
      logout()
    }
  },

  logout,
})
