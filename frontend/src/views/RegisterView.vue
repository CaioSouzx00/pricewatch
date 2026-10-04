<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ApiError } from '../api/client'
import { safeRedirect } from '../router'
import { useAuth } from '../stores/auth'
import InputText from 'primevue/inputtext'
import Password from 'primevue/password'
import Button from 'primevue/button'
import Message from 'primevue/message'

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
    error.value = e instanceof ApiError ? e.message : 'Unexpected error'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="min-h-screen flex items-center justify-center p-4 bg-[var(--p-surface-950)]">
    <div class="w-full max-w-md p-8 rounded-2xl border border-[var(--p-surface-800)] bg-[var(--p-surface-900)] shadow-xl relative overflow-hidden">
      <!-- Decorative gradient blob -->
      <div class="absolute top-0 right-0 w-64 h-64 bg-green-500/10 rounded-full blur-3xl -translate-y-1/2 translate-x-1/2 pointer-events-none"></div>

      <div class="flex items-center gap-2 mb-8 relative z-10">
        <i class="pi pi-bullseye text-green text-3xl"></i>
        <span class="font-bold text-2xl tracking-tight text-[var(--p-surface-0)]">DealRadar</span>
      </div>

      <h1 class="text-2xl font-bold m-0 mb-2 relative z-10 text-[var(--p-surface-0)]">Create an account</h1>
      <p class="text-muted text-sm mb-8 relative z-10">Join us to start monitoring prices automatically.</p>

      <form @submit.prevent="submit" novalidate class="flex flex-col gap-5 relative z-10">
        <Message v-if="error" severity="error" :closable="false" class="m-0 mb-2">{{ error }}</Message>

        <div class="flex flex-col gap-2">
          <label for="register-name" class="text-sm text-muted">Full name</label>
          <InputText id="register-name" v-model="name" type="text" autocomplete="name" maxlength="120" required class="w-full" placeholder="John Doe" />
        </div>

        <div class="flex flex-col gap-2">
          <label for="register-email" class="text-sm text-muted">Email address</label>
          <InputText id="register-email" v-model="email" type="email" autocomplete="email" required class="w-full" placeholder="you@example.com" />
        </div>
        
        <div class="flex flex-col gap-2">
          <label for="register-password" class="text-sm text-muted">Password</label>
          <Password id="register-password" v-model="password" :feedback="true" toggleMask inputClass="w-full" class="w-full" placeholder="••••••••" required />
          <small v-if="passwordTooShort" class="text-red">Minimum 8 characters</small>
        </div>

        <div class="flex flex-col gap-2">
          <label for="register-confirm" class="text-sm text-muted">Confirm password</label>
          <Password id="register-confirm" v-model="confirm" :feedback="false" toggleMask inputClass="w-full" class="w-full" placeholder="••••••••" required />
          <small v-if="mismatch" class="text-red">Passwords do not match</small>
        </div>

        <Button id="register-submit" type="submit" :loading="loading" :disabled="!canSubmit" label="Create account" class="w-full mt-2" />
      </form>

      <p class="text-center text-sm text-muted mt-8 relative z-10">
        Already have an account?
        <RouterLink id="go-login" :to="{ name: 'login', query: route.query }" class="text-green hover:underline">Sign in</RouterLink>
      </p>
    </div>
  </main>
</template>

<style scoped>
.min-h-screen { min-height: 100vh; }
.flex { display: flex; }
.flex-col { flex-direction: column; }
.items-center { align-items: center; }
.justify-center { justify-content: center; }
.justify-between { justify-content: space-between; }
.p-4 { padding: 1rem; }
.p-8 { padding: 2rem; }
.w-full { width: 100%; }
.max-w-md { max-width: 28rem; }
.rounded-2xl { border-radius: 1rem; }
.border { border-width: 1px; }
.shadow-xl { box-shadow: 0 20px 25px -5px rgb(0 0 0 / 0.5), 0 8px 10px -6px rgb(0 0 0 / 0.5); }
.relative { position: relative; }
.absolute { position: absolute; }
.overflow-hidden { overflow: hidden; }
.top-0 { top: 0; }
.right-0 { right: 0; }
.w-64 { width: 16rem; }
.h-64 { height: 16rem; }
.bg-green-500\/10 { background-color: rgba(16, 185, 129, 0.1); }
.rounded-full { border-radius: 9999px; }
.blur-3xl { filter: blur(64px); }
.-translate-y-1\/2 { transform: translateY(-50%); }
.translate-x-1\/2 { transform: translateX(50%); }
.pointer-events-none { pointer-events: none; }
.z-10 { z-index: 10; }
.gap-2 { gap: 0.5rem; }
.gap-5 { gap: 1.25rem; }
.mb-2 { margin-bottom: 0.5rem; }
.mb-8 { margin-bottom: 2rem; }
.mt-2 { margin-top: 0.5rem; }
.mt-8 { margin-top: 2rem; }
.m-0 { margin: 0; }
.text-3xl { font-size: 1.875rem; line-height: 2.25rem; }
.text-2xl { font-size: 1.5rem; line-height: 2rem; }
.text-sm { font-size: 0.875rem; line-height: 1.25rem; }
.text-xs { font-size: 0.75rem; line-height: 1rem; }
.text-center { text-align: center; }
.tracking-tight { letter-spacing: -0.025em; }
.no-underline { text-decoration: none; }
.hover\:underline:hover { text-decoration: underline; }
</style>
