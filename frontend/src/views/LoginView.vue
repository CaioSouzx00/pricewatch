<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ApiError } from '../api/client'
import { safeRedirect } from '../router'
import { useAuth } from '../stores/auth'

const auth = useAuth()
const router = useRouter()
const route = useRoute()

const email = ref('')
const password = ref('')
const error = ref('')
const loading = ref(false)

async function submit() {
  error.value = ''
  loading.value = true
  try {
    await auth.login(email.value.trim(), password.value)
    await router.replace(safeRedirect(route.query.redirect))
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Erro inesperado'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="auth-page">
    <section class="auth-card">
      <span class="brand">MVP</span>
      <h1>Entrar</h1>
      <p class="subtitle">Acesse sua conta para acompanhar seus produtos.</p>

      <form @submit.prevent="submit" novalidate>
        <p v-if="error" id="login-error" class="error" role="alert">{{ error }}</p>

        <div class="field">
          <label for="login-email">E-mail</label>
          <input id="login-email" v-model="email" type="email" autocomplete="email" required />
        </div>
        <div class="field">
          <label for="login-password">Senha</label>
          <input id="login-password" v-model="password" type="password" autocomplete="current-password" required />
        </div>

        <button id="login-submit" class="btn" type="submit" :disabled="loading || !email || !password">
          {{ loading ? 'Entrando...' : 'Entrar' }}
        </button>
      </form>

      <p class="switch">
        Não tem conta?
        <RouterLink id="go-register" :to="{ name: 'register', query: route.query }">Cadastre-se</RouterLink>
      </p>
    </section>
  </main>
</template>
