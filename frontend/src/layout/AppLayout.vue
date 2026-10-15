<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuth } from '../stores/auth'
import { useTheme } from '../composables/useTheme'
import { useConfirm } from 'primevue/useconfirm'
import { useToast } from 'primevue/usetoast'
import { listProducts, type Product } from '../api/products'
import { formatMoney } from '../utils/stats'
import CommandPalette from '../components/CommandPalette.vue'
import ProductFormModal from '../components/ProductFormModal.vue'

const router = useRouter()
const route = useRoute()
const auth = useAuth()
const { isDark, toggleTheme } = useTheme()
const confirm = useConfirm()
const toast = useToast()

const commandPaletteOpen = ref(false)
const createProductOpen = ref(false)
const tickerProducts = ref<Product[]>([])

const navItems = [
  { name: 'Dashboard', path: '/', routeName: 'dashboard', icon: 'pi pi-chart-bar', keyHint: '1' },
  { name: 'Produtos', path: '/products', routeName: 'products', icon: 'pi pi-box', keyHint: '2' },
  { name: 'Watchlist', path: '/watchlist', routeName: 'watchlist', icon: 'pi pi-star', keyHint: '3' },
  { name: 'Alertas', path: '/alerts', routeName: 'alerts', icon: 'pi pi-bell', keyHint: '4' },
]

function isActive(routeName: string): boolean {
  return route.name === routeName || (routeName === 'products' && route.name === 'product-detail')
}

async function loadTicker() {
  try {
    const products = await listProducts()
    tickerProducts.value = products.filter(p => p.currentPrice !== null).slice(0, 10)
  } catch {
    // fallback ticker stays active
  }
}

function handleLogout() {
  confirm.require({
    message: 'Deseja realmente desconectar sua conta do PriceWatch Terminal?',
    header: 'Confirmar Saída',
    icon: 'pi pi-power-off',
    rejectProps: {
      label: 'Cancelar',
      severity: 'secondary',
    },
    acceptProps: {
      label: 'Sair do Sistema',
      severity: 'danger',
    },
    accept: () => {
      auth.logout()
      router.replace({ name: 'login' })
      toast.add({
        severity: 'info',
        summary: 'Sessão Finalizada',
        detail: 'Você foi desconectado com sucesso.',
        life: 3000
      })
    }
  })
}

function onProductSaved() {
  toast.add({
    severity: 'success',
    summary: 'Produto Monitorado',
    detail: 'O novo produto foi cadastrado e seu preço será coletado periodicamente.',
    life: 3500
  })
  createProductOpen.value = false
  loadTicker()
  // If we're on products or dashboard, let the view react or refresh
}

onMounted(() => {
  loadTicker()

  // Keyboard navigation shortcuts
  window.addEventListener('keydown', (e) => {
    // Check if typing inside input
    const tag = (e.target as HTMLElement)?.tagName?.toLowerCase()
    if (tag === 'input' || tag === 'textarea' || tag === 'select') return

    if (e.key === '1') router.push('/')
    if (e.key === '2') router.push('/products')
    if (e.key === '3') router.push('/watchlist')
    if (e.key === '4') router.push('/alerts')
    if (e.key.toLowerCase() === 'n' && !e.ctrlKey && !e.metaKey) {
      createProductOpen.value = true
    }
  })
})
</script>

<template>
  <div class="terminal-layout">
    <!-- Top Infinite Ticker Bar -->
    <div class="terminal-ticker-wrap">
      <div class="ticker-prefix">
        <span class="ticker-pulse" />
        <span class="tech-label">FEED // TICKER</span>
      </div>

      <div class="ticker-track">
        <div class="ticker-content">
          <template v-if="tickerProducts.length > 0">
            <span
              v-for="p in tickerProducts"
              :key="'a-' + p.id"
              class="ticker-item cursor-hover"
              @click="router.push(`/products/${p.id}`)"
            >
              <span class="ticker-id">#{{ String(p.id).padStart(4, '0') }}</span>
              <span class="ticker-name">{{ p.name }}</span>
              <span class="ticker-price">{{ formatMoney(Number(p.currentPrice), p.currency) }}</span>
              <span class="ticker-store">{{ p.store ?? 'E-commerce' }}</span>
              <span class="ticker-sep">•</span>
            </span>
          </template>
          <template v-else>
            <span class="ticker-item">
              <span class="ticker-id">#SYS</span>
              <span class="ticker-name">SISTEMA DE RASTREAMENTO ONLINE</span>
              <span class="ticker-price">CONEXÃO ATIVA</span>
              <span class="ticker-store">MERCADO LIVRE / AMAZON</span>
              <span class="ticker-sep">•</span>
            </span>
            <span class="ticker-item">
              <span class="ticker-id">#RADAR</span>
              <span class="ticker-name">CADASTRE PRODUTOS PARA ATIVAR O FEED DE COTAÇÕES</span>
              <span class="ticker-price">LATÊNCIA 18MS</span>
              <span class="ticker-sep">•</span>
            </span>
          </template>
        </div>

        <!-- Duplicated track for continuous infinite scroll loop -->
        <div class="ticker-content" aria-hidden="true">
          <template v-if="tickerProducts.length > 0">
            <span
              v-for="p in tickerProducts"
              :key="'b-' + p.id"
              class="ticker-item cursor-hover"
              @click="router.push(`/products/${p.id}`)"
            >
              <span class="ticker-id">#{{ String(p.id).padStart(4, '0') }}</span>
              <span class="ticker-name">{{ p.name }}</span>
              <span class="ticker-price">{{ formatMoney(Number(p.currentPrice), p.currency) }}</span>
              <span class="ticker-store">{{ p.store ?? 'E-commerce' }}</span>
              <span class="ticker-sep">•</span>
            </span>
          </template>
          <template v-else>
            <span class="ticker-item">
              <span class="ticker-id">#SYS</span>
              <span class="ticker-name">SISTEMA DE RASTREAMENTO ONLINE</span>
              <span class="ticker-price">CONEXÃO ATIVA</span>
              <span class="ticker-store">MERCADO LIVRE / AMAZON</span>
              <span class="ticker-sep">•</span>
            </span>
            <span class="ticker-item">
              <span class="ticker-id">#RADAR</span>
              <span class="ticker-name">CADASTRE PRODUTOS PARA ATIVAR O FEED DE COTAÇÕES</span>
              <span class="ticker-price">LATÊNCIA 18MS</span>
              <span class="ticker-sep">•</span>
            </span>
          </template>
        </div>
      </div>

      <div class="ticker-suffix">
        <span class="status-indicator">ONLINE</span>
      </div>
    </div>

    <!-- Main Workspace Container -->
    <div class="terminal-body">
      <!-- High-Precision Nav Rail (Desktop) -->
      <aside class="terminal-rail">
        <div class="rail-header">
          <RouterLink to="/" class="rail-brand" title="PriceWatch Terminal">
            <div class="brand-radar">
              <div class="radar-scan" />
              <div class="radar-cross" />
            </div>
            <span class="brand-name">PRICEWATCH</span>
          </RouterLink>
        </div>

        <nav class="rail-nav">
          <RouterLink
            v-for="item in navItems"
            :key="item.path"
            :to="item.path"
            class="rail-item"
            :class="{ 'is-active': isActive(item.routeName) }"
            :title="`${item.name} (${item.keyHint})`"
          >
            <div class="item-icon-wrap">
              <i :class="item.icon" />
              <span v-if="isActive(item.routeName)" class="active-dot" />
            </div>
            <span class="item-label">{{ item.name }}</span>
            <span class="item-key">{{ item.keyHint }}</span>
          </RouterLink>

          <button
            type="button"
            class="rail-item quick-add-btn"
            title="Novo Ativo (N)"
            @click="createProductOpen = true"
          >
            <div class="item-icon-wrap add-icon">
              <i class="pi pi-plus" />
            </div>
            <span class="item-label">Novo Ativo</span>
            <span class="item-key">N</span>
          </button>
        </nav>

        <div class="rail-footer">
          <button
            type="button"
            class="rail-btn"
            :title="isDark ? 'Modo Claro' : 'Modo Escuro'"
            @click="toggleTheme"
          >
            <i :class="isDark ? 'pi pi-sun' : 'pi pi-moon'" />
            <span class="item-label">{{ isDark ? 'Tema Claro' : 'Tema Escuro' }}</span>
          </button>

          <button
            type="button"
            class="rail-btn logout-btn"
            title="Sair do Terminal"
            @click="handleLogout"
          >
            <i class="pi pi-power-off" />
            <span class="item-label">Encerrar</span>
          </button>
        </div>
      </aside>

      <!-- Center Content Area -->
      <div class="terminal-main-wrap">
        <!-- Top Editorial Header Bar -->
        <header class="terminal-topbar">
          <div class="topbar-left">
            <div class="search-trigger cursor-hover" @click="commandPaletteOpen = true">
              <i class="pi pi-search" />
              <span class="search-placeholder">Buscar ativos, cotações ou comandos...</span>
              <kbd class="shortcut-pill">Ctrl K</kbd>
            </div>
          </div>

          <div class="topbar-right">
            <div class="user-chip">
              <div class="user-avatar">
                {{ auth.user.value?.name ? auth.user.value.name.charAt(0).toUpperCase() : 'U' }}
              </div>
              <div class="user-meta">
                <span class="user-name">{{ auth.user.value?.name ?? 'Operador' }}</span>
                <span class="user-email">{{ auth.user.value?.email ?? 'terminal@pricewatch.io' }}</span>
              </div>
            </div>

            <button
              type="button"
              class="topbar-action-btn theme-toggle"
              :title="isDark ? 'Mudar para tema claro' : 'Mudar para tema escuro'"
              @click="toggleTheme"
            >
              <i :class="isDark ? 'pi pi-sun' : 'pi pi-moon'" />
            </button>

            <button
              type="button"
              class="topbar-action-btn"
              title="Encerrar Sessão"
              @click="handleLogout"
            >
              <i class="pi pi-sign-out" />
            </button>
          </div>
        </header>

        <!-- View Content Router -->
        <main class="terminal-main-content">
          <RouterView />
        </main>
      </div>
    </div>

    <!-- Mobile Navigation Bottom Bar -->
    <nav class="mobile-nav" aria-label="Navegação Mobile">
      <RouterLink
        v-for="item in navItems"
        :key="'mob-' + item.path"
        :to="item.path"
        class="mobile-nav-item"
        :class="{ 'is-active': isActive(item.routeName) }"
      >
        <i :class="item.icon" />
        <span>{{ item.name }}</span>
      </RouterLink>
      <button
        type="button"
        class="mobile-nav-item mobile-add"
        @click="createProductOpen = true"
      >
        <i class="pi pi-plus" />
        <span>Novo</span>
      </button>
    </nav>

    <!-- Global Command Palette -->
    <CommandPalette
      v-model="commandPaletteOpen"
      @open-create-product="createProductOpen = true"
    />

    <!-- Global Product Form Modal -->
    <ProductFormModal
      v-if="createProductOpen"
      :product="null"
      @close="createProductOpen = false"
      @saved="onProductSaved"
    />
  </div>
</template>

<style scoped>
.terminal-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background-color: var(--bg);
  position: relative;
}

/* --- Ticker Infinito Superior --- */
.terminal-ticker-wrap {
  height: 36px;
  background: var(--surface-1);
  border-bottom: 1px solid var(--border-subtle);
  display: flex;
  align-items: center;
  overflow: hidden;
  position: relative;
  z-index: 50;
  user-select: none;
}

.ticker-prefix {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0 1rem;
  background: var(--surface-1);
  border-right: 1px solid var(--border-subtle);
  height: 100%;
  flex-shrink: 0;
  z-index: 2;
}

.ticker-pulse {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--accent);
  box-shadow: 0 0 8px var(--accent);
  animation: pulse-glow 2s infinite ease-in-out;
}

@keyframes pulse-glow {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.4; transform: scale(0.85); }
}

.ticker-track {
  display: flex;
  overflow: hidden;
  white-space: nowrap;
  flex: 1;
  position: relative;
}

.ticker-content {
  display: flex;
  align-items: center;
  animation: ticker-slide 42s linear infinite;
  will-change: transform;
}

.ticker-track:hover .ticker-content {
  animation-play-state: paused;
}

@keyframes ticker-slide {
  from { transform: translateX(0); }
  to { transform: translateX(-100%); }
}

.ticker-item {
  display: inline-flex;
  align-items: center;
  gap: 0.6rem;
  padding: 0 1.25rem;
  font-family: var(--font-mono);
  font-size: 0.75rem;
  color: var(--text-secondary);
  transition: color 0.15s ease;
}

.ticker-item:hover {
  color: var(--text-primary);
}

.ticker-id {
  color: var(--text-muted);
  font-size: 0.6875rem;
}

.ticker-name {
  font-weight: 500;
  color: var(--text-primary);
  max-width: 220px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.ticker-price {
  font-weight: 600;
  color: var(--accent);
  font-variant-numeric: tabular-nums;
}

.ticker-store {
  font-size: 0.6875rem;
  padding: 0.1rem 0.35rem;
  background: var(--surface-2);
  border: 1px solid var(--border-subtle);
  border-radius: 2px;
  color: var(--text-muted);
}

.ticker-sep {
  color: var(--border-strong);
  margin-left: 0.25rem;
}

.ticker-suffix {
  padding: 0 1rem;
  background: var(--surface-1);
  border-left: 1px solid var(--border-subtle);
  height: 100%;
  display: flex;
  align-items: center;
  flex-shrink: 0;
  z-index: 2;
}

.status-indicator {
  font-family: var(--font-mono);
  font-size: 0.625rem;
  letter-spacing: 0.08em;
  color: var(--accent);
  font-weight: 600;
}

/* --- Layout Principal --- */
.terminal-body {
  display: flex;
  flex: 1;
  min-height: calc(100vh - 36px);
}

/* --- Rail de Navegação Compacto --- */
.terminal-rail {
  width: 220px;
  background: var(--surface-1);
  border-right: 1px solid var(--border-subtle);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  padding: 1.25rem 0.75rem;
  position: sticky;
  top: 36px;
  height: calc(100vh - 36px);
  z-index: 40;
}

.rail-header {
  padding: 0 0.5rem 1.5rem;
  border-bottom: 1px solid var(--border-subtle);
  margin-bottom: 1.25rem;
}

.rail-brand {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  text-decoration: none;
  color: var(--text-primary);
}

.brand-radar {
  width: 28px;
  height: 28px;
  border: 1px solid var(--accent-border);
  border-radius: 50%;
  position: relative;
  background: var(--surface-2);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  flex-shrink: 0;
}

.radar-scan {
  position: absolute;
  inset: 0;
  background: conic-gradient(from 0deg, transparent 0deg, var(--accent-subtle) 90deg, var(--accent) 360deg);
  opacity: 0.35;
  animation: radar-sweep 4s linear infinite;
  border-radius: 50%;
}

@keyframes radar-sweep {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.radar-cross {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--accent);
  position: relative;
  z-index: 2;
}

.brand-name {
  font-family: var(--font-display);
  font-weight: 700;
  font-size: 0.9375rem;
  letter-spacing: 0.08em;
  color: var(--text-primary);
}

.rail-nav {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  flex: 1;
}

.rail-item {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.625rem 0.75rem;
  border-radius: 4px;
  text-decoration: none;
  color: var(--text-secondary);
  font-size: 0.8125rem;
  font-weight: 500;
  transition: all var(--duration-fast) var(--ease-terminal);
  border: 1px solid transparent;
  background: transparent;
  cursor: pointer;
  width: 100%;
}

.rail-item:hover {
  background: var(--surface-2);
  color: var(--text-primary);
}

.rail-item.is-active {
  background: var(--surface-2);
  color: var(--text-primary);
  border-color: var(--border-subtle);
}

.item-icon-wrap {
  position: relative;
  width: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1rem;
}

.active-dot {
  position: absolute;
  left: -10px;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 14px;
  border-radius: 2px;
  background: var(--accent);
}

.item-label {
  flex: 1;
  text-align: left;
}

.item-key {
  font-family: var(--font-mono);
  font-size: 0.625rem;
  color: var(--text-muted);
  padding: 0.1rem 0.35rem;
  background: var(--surface-3);
  border-radius: 3px;
}

.quick-add-btn {
  margin-top: 0.75rem;
  border: 1px dashed var(--border-strong);
  color: var(--text-primary);
}

.quick-add-btn:hover {
  border-color: var(--accent);
  background: var(--accent-subtle);
  color: var(--accent);
}

.rail-footer {
  padding-top: 1rem;
  border-top: 1px solid var(--border-subtle);
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.rail-btn {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.625rem 0.75rem;
  border-radius: 4px;
  border: none;
  background: transparent;
  color: var(--text-secondary);
  font-family: var(--font-body);
  font-size: 0.8125rem;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--duration-fast) ease;
  width: 100%;
}

.rail-btn:hover {
  background: var(--surface-2);
  color: var(--text-primary);
}

.rail-btn.logout-btn:hover {
  color: var(--danger);
  background: var(--danger-subtle);
}

/* --- Área de Conteúdo Central --- */
.terminal-main-wrap {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.terminal-topbar {
  height: 54px;
  background: var(--bg);
  border-bottom: 1px solid var(--border-subtle);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 2rem;
  position: sticky;
  top: 36px;
  z-index: 30;
  backdrop-filter: blur(8px);
}

.topbar-left {
  display: flex;
  align-items: center;
  flex: 1;
  max-width: 480px;
}

.search-trigger {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  width: 100%;
  padding: 0.45rem 0.875rem;
  background: var(--surface-1);
  border: 1px solid var(--border-subtle);
  border-radius: 4px;
  color: var(--text-muted);
  font-size: 0.8125rem;
  transition: all var(--duration-fast) ease;
}

.search-trigger:hover {
  border-color: var(--border-strong);
  color: var(--text-secondary);
}

.search-placeholder {
  flex: 1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.shortcut-pill {
  font-family: var(--font-mono);
  font-size: 0.6875rem;
  padding: 0.1rem 0.4rem;
  background: var(--surface-2);
  border: 1px solid var(--border-subtle);
  border-radius: 3px;
  color: var(--text-muted);
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.user-chip {
  display: flex;
  align-items: center;
  gap: 0.625rem;
  padding: 0.25rem 0.625rem;
  border-radius: 4px;
  background: var(--surface-1);
  border: 1px solid var(--border-subtle);
}

.user-avatar {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--surface-3);
  color: var(--text-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: var(--font-mono);
  font-size: 0.6875rem;
  font-weight: 700;
}

.user-meta {
  display: flex;
  flex-direction: column;
}

.user-name {
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--text-primary);
  line-height: 1.2;
}

.user-email {
  font-size: 0.625rem;
  color: var(--text-muted);
  font-family: var(--font-mono);
  line-height: 1.2;
}

.topbar-action-btn {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--surface-1);
  border: 1px solid var(--border-subtle);
  border-radius: 4px;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all var(--duration-fast) ease;
}

.topbar-action-btn:hover {
  background: var(--surface-2);
  color: var(--text-primary);
  border-color: var(--border-strong);
}

.terminal-main-content {
  flex: 1;
  padding: 2.25rem 2rem 4rem;
  max-width: 1440px;
  width: 100%;
  margin: 0 auto;
}

/* --- Mobile Bottom Nav --- */
.mobile-nav {
  display: none;
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  height: 58px;
  background: var(--surface-1);
  border-top: 1px solid var(--border-subtle);
  z-index: 100;
  justify-content: space-around;
  align-items: center;
  padding: 0 0.5rem;
}

.mobile-nav-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  font-size: 0.625rem;
  color: var(--text-muted);
  text-decoration: none;
  padding: 0.4rem 0.75rem;
  border-radius: 4px;
  background: transparent;
  border: none;
}

.mobile-nav-item i {
  font-size: 1.125rem;
}

.mobile-nav-item.is-active {
  color: var(--accent);
}

.mobile-add {
  color: var(--text-primary);
}

/* --- Responsive Adjustments --- */
@media (max-width: 960px) {
  .terminal-rail {
    display: none;
  }
  .mobile-nav {
    display: flex;
  }
  .terminal-topbar {
    padding: 0 1rem;
  }
  .terminal-main-content {
    padding: 1.5rem 1rem 5rem;
  }
}
</style>
