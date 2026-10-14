<script setup lang="ts">
import { ref } from 'vue'
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
    error.value = e instanceof ApiError ? e.message : 'Credenciais inválidas ou erro no terminal'
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
        <span class="tech-label">INTELIGÊNCIA DE PREÇOS EM TEMPO REAL</span>
        <h2 class="editorial-quote">
          "O preço é o único dado que não mente. Monitore cada oscilação antes de fechar negócio."
        </h2>

        <!-- Simulated Live Market Ticker Stream -->
        <div class="ticker-box">
          <div class="ticker-row">
            <span class="t-id">#0042</span>
            <span class="t-name">MacBook Pro M3 Max</span>
            <span class="t-price tabular num">R$ 18.499,00</span>
            <span class="t-delta tone-good">▼ −14,2%</span>
          </div>
          <div class="ticker-row">
            <span class="t-id">#0019</span>
            <span class="t-name">Sony WH-1000XM5</span>
            <span class="t-price tabular num">R$ 1.949,00</span>
            <span class="t-delta tone-good">▼ −8,5%</span>
          </div>
          <div class="ticker-row">
            <span class="t-id">#0087</span>
            <span class="t-name">Monitor Dell UltraSharp 32"</span>
            <span class="t-price tabular num">R$ 4.890,00</span>
            <span class="t-delta tone-danger">▲ +3,1%</span>
          </div>
        </div>
      </div>

      <div class="panel-footer">
        <div class="status-feed">
          <span class="pulse-green" />
          <span class="tech-label">MOTOR DE SCRAPING ATIVO // LATÊNCIA 18MS</span>
        </div>
        <span class="tech-label">V2.4.0</span>
      </div>
    </section>

    <!-- Right Form Section -->
    <section class="auth-form-section">
      <div class="form-container">
        <div class="form-header">
          <span class="tech-label">AUTENTICAÇÃO DE OPERADOR</span>
          <h1 class="form-title">Acessar Terminal</h1>
          <p class="form-sub">Insira suas credenciais cadastradas para ingressar no painel de monitoramento.</p>
        </div>

        <form @submit.prevent="submit" novalidate class="auth-form">
          <Message v-if="error" severity="error" :closable="false" class="auth-error">
            {{ error }}
          </Message>

          <div class="field-item">
            <label for="login-email" class="field-label">ENDEREÇO DE E-MAIL</label>
            <InputText
              id="login-email"
              v-model="email"
              type="email"
              autocomplete="email"
              required
              class="w-full"
              placeholder="operador@pricewatch.io"
            />
          </div>

          <div class="field-item">
            <div class="label-with-link">
              <label for="login-password" class="field-label">CHAVE DE ACESSO (SENHA)</label>
            </div>
            <Password
              id="login-password"
              v-model="password"
              :feedback="false"
              toggleMask
              inputClass="w-full"
              class="w-full"
              placeholder="••••••••••••"
              required
            />
          </div>

          <Button
            id="login-submit"
            type="submit"
            :loading="loading"
            :disabled="!email || !password"
            label="Ingressar no Terminal"
            class="p-button-primary auth-submit-btn cursor-hover"
          />
        </form>

        <div class="form-footer">
          <span class="footer-prompt">Ainda não possui credencial?</span>
          <RouterLink
            id="go-register"
            :to="{ name: 'register', query: route.query }"
            class="footer-link cursor-hover"
          >
            Criar conta de operador →
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

.ticker-box {
  background: var(--surface-2);
  border: 1px solid var(--border-subtle);
  border-radius: 6px;
  padding: 1rem;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.ticker-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-family: var(--font-mono);
  font-size: 0.75rem;
  padding: 0.25rem 0;
  border-bottom: 1px solid var(--border-subtle);
}

.ticker-row:last-child {
  border-bottom: none;
}

.t-id { color: var(--text-muted); }
.t-name {
  color: var(--text-primary);
  font-weight: 500;
  max-width: 180px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.t-price { font-weight: 700; color: var(--text-primary); }
.t-delta { font-weight: 700; font-size: 0.6875rem; }

.tone-good { color: var(--accent); }
.tone-danger { color: var(--danger); }

.panel-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 1.5rem;
  border-top: 1px solid var(--border-subtle);
}

.status-feed {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.pulse-green {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--accent);
  box-shadow: 0 0 6px var(--accent);
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

.label-with-link {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.field-label {
  font-family: var(--font-mono);
  font-size: 0.6875rem;
  letter-spacing: 0.08em;
  color: var(--text-muted);
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
