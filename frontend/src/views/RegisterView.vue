<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ApiError } from '../api/client'
import { safeRedirect } from '../router'
import { useAuth } from '../stores/auth'

const auth = useAuth()
const router = useRouter()
const route = useRoute()

const name = ref('')
const email = ref('')
const password = ref('')
const confirm = ref('')
const error = ref('')
const loading = ref(false)

const passwordTooShort = computed(() => password.value.length > 0 && password.value.length < 8)
const mismatch = computed(() => confirm.value.length > 0 && confirm.value !== password.value)
const canSubmit = computed(
  () =>
    !loading.value &&
    name.value.trim() !== '' &&
    email.value.trim() !== '' &&
    password.value.length >= 8 &&
    password.value === confirm.value,
)

async function submit() {
  if (!canSubmit.value) return
  error.value = ''
  loading.value = true
  try {
    await auth.register(name.value.trim(), email.value.trim(), password.value)
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
      <h1>Criar conta</h1>
      <p class="subtitle">Leva menos de um minuto.</p>

      <form @submit.prevent="submit" novalidate>
        <p v-if="error" id="register-error" class="error" role="alert">{{ error }}</p>

        <div class="field">
          <label for="register-name">Nome</label>
          <input id="register-name" v-model="name" type="text" autocomplete="name" maxlength="120" required />
        </div>
        <div class="field">
          <label for="register-email">E-mail</label>
          <input id="register-email" v-model="email" type="email" autocomplete="email" required />
        </div>
        <div class="field">
          <label for="register-password">Senha</label>
          <input id="register-password" v-model="password" type="password" autocomplete="new-password" required />
          <span class="hint" :style="passwordTooShort ? 'color: var(--danger)' : ''">Mínimo de 8 caracteres</span>
        </div>
        <div class="field">
          <label for="register-confirm">Confirmar senha</label>
          <input id="register-confirm" v-model="confirm" type="password" autocomplete="new-password" required />
          <span v-if="mismatch" class="hint" style="color: var(--danger)">As senhas não coincidem</span>
        </div>

        <button id="register-submit" class="btn" type="submit" :disabled="!canSubmit">
          {{ loading ? 'Criando...' : 'Criar conta' }}
        </button>
      </form>

      <p class="switch">
        Já tem conta?
        <RouterLink id="go-login" :to="{ name: 'login', query: route.query }">Entrar</RouterLink>
      </p>
    </section>
  </main>
</template>
