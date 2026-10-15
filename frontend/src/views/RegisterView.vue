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
const emailTouched = ref(false)

const emailInvalid = computed(
  () => emailTouched.value && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.value.trim())
)
const passwordTooShort = computed(() => password.value.length > 0 && password.value.length < 8)
const mismatch = computed(() => confirm.value.length > 0 && confirm.value !== password.value)
const canSubmit = computed(
  () =>
    !loading.value &&
    name.value.trim() !== '' &&
    email.value.trim() !== '' &&
    !emailInvalid.value &&
    password.value.length >= 8 &&
    password.value === confirm.value,
)

async function submit() {
  if (!canSubmit.value) return
  error.value = ''
  loading.value = true
  try {
    await auth.register(name.value.trim(), email.value.trim().toLowerCase(), password.value)
    await router.replace(safeRedirect(route.query.redirect))
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Falha ao registrar operador'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="auth-layout">
    <!-- Left Editorial Terminal Panel -->
    <section class="auth-editorial-panel">
      <div class="panel-header">
        <div class="brand-radar">
          <div class="radar-scan" />
          <div class="radar-dot" />
        </div>
        <span class="brand-title">PRICEWATCH // TERMINAL</span>
      </div>

      <div class="panel-hero">
        <span class="tech-label">CONTROLE ESTRATÉGICO DE E-COMMERCE</span>
        <h2 class="editorial-quote">
          "Antecipe movimentos de mercado. Configure gatilhos que compram no momento exato da queda."
        </h2>

        <div class="feature-checklist">
          <div class="feature-item">
            <span class="feat-check">✓</span>
            <div class="feat-text">
              <strong>Scraping Contínuo</strong>
              <span>Verificações periódicas sem necessidade de intervenção manual.</span>
            </div>
          </div>
          <div class="feature-item">
            <span class="feat-check">✓</span>
            <div class="feat-text">
              <strong>Curva em Degraus</strong>
              <span>Visualização analítica precisa de cada patamar de preço.</span>
            </div>
          </div>
          <div class="feature-item">
            <span class="feat-check">✓</span>
            <div class="feat-text">
              <strong>Disparadores em Milissegundos</strong>
              <span>Notificações quando o ativo atingir sua meta de preço.</span>
            </div>
          </div>
        </div>
      </div>

      <div class="panel-footer">
        <span class="tech-label">MOTOR DE DADOS // CRIPTOGRAFIA DE PONTA A PONTA</span>
        <span class="tech-label">V2.4.0</span>
      </div>
    </section>

    <!-- Right Form Section -->
    <section class="auth-form-section">
      <div class="form-container">
        <div class="form-header">
          <span class="tech-label">CREDENCIAMENTO NO TERMINAL</span>
          <h1 class="form-title">Criar Conta</h1>
          <p class="form-sub">Cadastre seus dados para habilitar o rastreamento autônomo de preços.</p>
        </div>

        <form @submit.prevent="submit" novalidate class="auth-form">
          <Message v-if="error" severity="error" :closable="false" class="auth-error">
            {{ error }}
          </Message>

          <div class="field-item">
            <label for="register-name" class="field-label">NOME COMPLETO DO OPERADOR</label>
            <InputText
              id="register-name"
              v-model="name"
              type="text"
              autocomplete="name"
              maxlength="120"
              required
              class="w-full"
              placeholder="Caio Souza"
            />
          </div>

          <div class="field-item">
            <label for="register-email" class="field-label">ENDEREÇO DE E-MAIL</label>
            <InputText
              id="register-email"
              v-model="email"
              type="email"
              autocomplete="email"
              required
              :invalid="emailInvalid"
              class="w-full"
              placeholder="operador@pricewatch.io"
              @blur="emailTouched = true"
            />
            <span v-if="emailInvalid" class="field-error">Formato de e-mail inválido</span>
          </div>

          <div class="field-item">
            <label for="register-password" class="field-label">CHAVE DE ACESSO (MÍNIMO 8 CARACTERES)</label>
            <Password
              id="register-password"
              v-model="password"
              :feedback="true"
              toggleMask
              inputClass="w-full"
              class="w-full"
              placeholder="••••••••••••"
              required
            />
            <span v-if="passwordTooShort" class="field-error">A senha requer ao menos 8 caracteres</span>
          </div>

          <div class="field-item">
            <label for="register-confirm" class="field-label">CONFIRMAR CHAVE DE ACESSO</label>
            <Password
              id="register-confirm"
              v-model="confirm"
              :feedback="false"
              toggleMask
              inputClass="w-full"
              class="w-full"
              placeholder="••••••••••••"
              required
            />
            <span v-if="mismatch" class="field-error">As senhas informadas não coincidem</span>
          </div>

          <Button
            id="register-submit"
            type="submit"
            :loading="loading"
            :disabled="!canSubmit"
            label="Registrar e Ingressar"
            class="p-button-primary auth-submit-btn cursor-hover"
          />
        </form>

        <div class="form-footer">
          <span class="footer-prompt">Já possui credencial cadastrada?</span>
          <RouterLink
            id="go-login"
            :to="{ name: 'login', query: route.query }"
            class="footer-link cursor-hover"
          >
            Acessar com e-mail existente →
          </RouterLink>
        </div>
      </div>
    </section>
  </main>
</template>

<style scoped>
.auth-layout {
  min-height: 100vh;
  display: grid;
  grid-template-columns: 1.1fr 1fr;
  background-color: var(--bg);
}

/* --- Left Editorial Panel --- */
.auth-editorial-panel {
  background: var(--surface-1);
  border-right: 1px solid var(--border-subtle);
  padding: 3.5rem;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  position: relative;
  overflow: hidden;
}

.auth-editorial-panel::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 2px;
  background: var(--accent);
}

.panel-header {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.brand-radar {
  width: 24px;
  height: 24px;
  border: 1px solid var(--accent-border);
  border-radius: 50%;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
}

.radar-scan {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  background: conic-gradient(from 0deg, transparent 0deg, var(--accent) 360deg);
  opacity: 0.25;
  animation: rotate-scan 4s linear infinite;
}

@keyframes rotate-scan {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.radar-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--accent);
}

.brand-title {
  font-family: var(--font-display);
  font-weight: 700;
  font-size: 0.875rem;
  letter-spacing: 0.1em;
  color: var(--text-primary);
}

.panel-hero {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
  max-width: 520px;
}

.editorial-quote {
  font-family: var(--font-display);
  font-size: clamp(1.5rem, 2.75vw, 2.25rem);
  font-weight: 600;
  line-height: 1.3;
  color: var(--text-primary);
  margin: 0;
  letter-spacing: -0.02em;
}

.feature-checklist {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  margin-top: 0.5rem;
}

.feature-item {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
}

.feat-check {
  font-family: var(--font-mono);
  font-size: 0.875rem;
  color: var(--accent);
  background: var(--accent-subtle);
  width: 22px;
  height: 22px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.feat-text {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
}

.feat-text strong {
  font-size: 0.875rem;
  font-weight: 600;
  color: var(--text-primary);
}

.feat-text span {
  font-size: 0.75rem;
  color: var(--text-muted);
}

.panel-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 1.5rem;
  border-top: 1px solid var(--border-subtle);
}

/* --- Right Form Section --- */
.auth-form-section {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 3rem 2rem;
}

.form-container {
  width: 100%;
  max-width: 400px;
  display: flex;
  flex-direction: column;
  gap: 2rem;
}

.form-header {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.form-title {
  font-family: var(--font-display);
  font-size: 2rem;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0;
  letter-spacing: -0.03em;
}

.form-sub {
  font-size: 0.8125rem;
  color: var(--text-muted);
  line-height: 1.5;
  margin: 0;
}

.auth-form {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.auth-error {
  margin: 0;
}

.field-item {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

.field-label {
  font-family: var(--font-mono);
  font-size: 0.6875rem;
  letter-spacing: 0.08em;
  color: var(--text-muted);
}

.field-error {
  font-family: var(--font-mono);
  font-size: 0.6875rem;
  color: var(--danger);
}

.auth-submit-btn {
  margin-top: 0.5rem;
  padding: 0.75rem !important;
  font-size: 0.875rem !important;
}

.w-full {
  width: 100%;
}

.form-footer {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  font-size: 0.8125rem;
  padding-top: 1.25rem;
  border-top: 1px solid var(--border-subtle);
}

.footer-prompt {
  color: var(--text-muted);
}

.footer-link {
  color: var(--accent);
  text-decoration: none;
  font-weight: 600;
  transition: color var(--duration-fast) ease;
}

.footer-link:hover {
  color: var(--accent-hover);
  text-decoration: underline;
}

@media (max-width: 900px) {
  .auth-layout {
    grid-template-columns: 1fr;
  }
  .auth-editorial-panel {
    display: none;
  }
  .auth-form-section {
    padding: 3rem 1.5rem;
  }
}
</style>
