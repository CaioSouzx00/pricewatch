<script setup lang="ts">
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { listProducts, type Product } from '../api/products'
import { formatMoney } from '../utils/stats'
import { useTheme } from '../composables/useTheme'

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [val: boolean]; openCreateProduct: [] }>()

const router = useRouter()
const { toggleTheme, isDark } = useTheme()

const query = ref('')
const selectedIndex = ref(0)
const products = ref<Product[]>([])
const loading = ref(false)
const inputRef = ref<HTMLInputElement | null>(null)

async function fetchProducts() {
  if (products.value.length > 0) return
  loading.value = true
  try {
    products.value = await listProducts()
  } catch {
    // ignore
  } finally {
    loading.value = false
  }
}

watch(() => props.modelValue, (isOpen) => {
  if (isOpen) {
    query.value = ''
    selectedIndex.value = 0
    fetchProducts()
    setTimeout(() => {
      inputRef.value?.focus()
    }, 50)
  }
})

interface CommandItem {
  id: string
  title: string
  subtitle?: string
  category: 'Rotas' | 'Ações' | 'Produtos'
  icon: string
  badge?: string
  action: () => void
}

const staticCommands: CommandItem[] = [
  {
    id: 'cmd-dash',
    title: 'Visão Geral / Dashboard',
    subtitle: 'Métricas vitais, maiores quedas e livro de cotações',
    category: 'Rotas',
    icon: 'pi pi-chart-bar',
    badge: 'G D',
    action: () => { router.push('/'); close() }
  },
  {
    id: 'cmd-products',
    title: 'Monitor de Produtos',
    subtitle: 'Catálogo de ativos rastreados',
    category: 'Rotas',
    icon: 'pi pi-box',
    badge: 'G P',
    action: () => { router.push('/products'); close() }
  },
  {
    id: 'cmd-watchlist',
    title: 'Watchlist de Favoritos',
    subtitle: 'Produtos marcados para compra imediata',
    category: 'Rotas',
    icon: 'pi pi-star',
    badge: 'G W',
    action: () => { router.push('/watchlist'); close() }
  },
  {
    id: 'cmd-alerts',
    title: 'Regras de Alerta',
    subtitle: 'Disparadores de preço alvo e histórico',
    category: 'Rotas',
    icon: 'pi pi-bell',
    badge: 'G A',
    action: () => { router.push('/alerts'); close() }
  },
  {
    id: 'cmd-new',
    title: 'Cadastrar Novo Produto',
    subtitle: 'Adicionar URL do Mercado Livre ou e-commerce',
    category: 'Ações',
    icon: 'pi pi-plus',
    badge: 'N',
    action: () => {
      close()
      emit('openCreateProduct')
    }
  },
  {
    id: 'cmd-theme',
    title: computed(() => isDark.value ? 'Alternar para Modo Claro' : 'Alternar para Modo Escuro').value,
    subtitle: 'Ajustar contraste do terminal visual',
    category: 'Ações',
    icon: computed(() => isDark.value ? 'pi pi-sun' : 'pi pi-moon').value,
    action: () => { toggleTheme(); close() }
  }
]

const filteredItems = computed<CommandItem[]>(() => {
  const q = query.value.trim().toLowerCase()
  if (!q) {
    return [
      ...staticCommands,
      ...products.value.slice(0, 5).map((p) => ({
        id: `prod-${p.id}`,
        title: p.name,
        subtitle: `${p.store ?? 'Loja'} • ID #${String(p.id).padStart(4, '0')}`,
        category: 'Produtos' as const,
        icon: 'pi pi-tag',
        badge: p.currentPrice ? formatMoney(Number(p.currentPrice), p.currency) : 'Sem preço',
        action: () => { router.push(`/products/${p.id}`); close() }
      }))
    ]
  }

  const matchingStatic = staticCommands.filter(c =>
    c.title.toLowerCase().includes(q) || (c.subtitle && c.subtitle.toLowerCase().includes(q))
  )

  const matchingProducts = products.value
    .filter(p =>
      p.name.toLowerCase().includes(q) ||
      (p.store && p.store.toLowerCase().includes(q)) ||
      String(p.id).includes(q) ||
      p.url.toLowerCase().includes(q)
    )
    .slice(0, 8)
    .map((p) => ({
      id: `prod-${p.id}`,
      title: p.name,
      subtitle: `${p.store ?? 'E-commerce'} • #${String(p.id).padStart(4, '0')}`,
      category: 'Produtos' as const,
      icon: 'pi pi-tag',
      badge: p.currentPrice ? formatMoney(Number(p.currentPrice), p.currency) : 'Sem preço',
      action: () => { router.push(`/products/${p.id}`); close() }
    }))

  return [...matchingStatic, ...matchingProducts]
})

function close() {
  emit('update:modelValue', false)
}

function onKeyDown(e: KeyboardEvent) {
  if (!props.modelValue) {
    if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
      e.preventDefault()
      emit('update:modelValue', true)
    }
    return
  }

  if (e.key === 'Escape') {
    e.preventDefault()
    close()
  } else if (e.key === 'ArrowDown') {
    e.preventDefault()
    if (filteredItems.value.length > 0) {
      selectedIndex.value = (selectedIndex.value + 1) % filteredItems.value.length
    }
  } else if (e.key === 'ArrowUp') {
    e.preventDefault()
    if (filteredItems.value.length > 0) {
      selectedIndex.value = (selectedIndex.value - 1 + filteredItems.value.length) % filteredItems.value.length
    }
  } else if (e.key === 'Enter') {
    e.preventDefault()
    const item = filteredItems.value[selectedIndex.value]
    if (item) item.action()
  }
}

onMounted(() => {
  window.addEventListener('keydown', onKeyDown)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeyDown)
})
</script>

<template>
  <div v-if="modelValue" class="palette-backdrop" @click.self="close" data-lenis-prevent>
    <div class="palette-window" role="dialog" aria-modal="true" aria-label="Command Palette">
      <div class="palette-search">
        <i class="pi pi-search search-icon" />
        <input
          ref="inputRef"
          v-model="query"
          type="text"
          placeholder="Buscar produto, comando ou rota..."
          class="search-input"
          spellcheck="false"
        />
        <kbd class="esc-badge" @click="close">ESC</kbd>
      </div>

      <div class="palette-results" data-lenis-prevent>
        <div v-if="filteredItems.length === 0" class="no-results">
          <p class="empty-title">Nenhum comando ou ativo encontrado</p>
          <span class="empty-hint">Tente buscar por nome de produto, loja (#ID) ou rotas.</span>
        </div>

        <div
          v-for="(item, index) in filteredItems"
          :key="item.id"
          class="palette-item"
          :class="{ 'is-selected': index === selectedIndex }"
          @click="item.action()"
          @mouseenter="selectedIndex = index"
        >
          <div class="item-left">
            <span class="item-icon-box">
              <i :class="item.icon" />
            </span>
            <div class="item-text">
              <div class="item-title">{{ item.title }}</div>
              <div v-if="item.subtitle" class="item-subtitle">{{ item.subtitle }}</div>
            </div>
          </div>

          <div class="item-right">
            <span v-if="item.badge" class="item-badge">{{ item.badge }}</span>
            <span class="item-cat">{{ item.category }}</span>
          </div>
        </div>
      </div>

      <div class="palette-footer">
        <div class="footer-hint">
          <span><kbd>↑</kbd><kbd>↓</kbd> navegar</span>
          <span><kbd>↵</kbd> selecionar</span>
          <span><kbd>esc</kbd> fechar</span>
        </div>
        <div class="footer-brand">
          <span class="radar-dot" /> TERMINAL CMD-V2
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.palette-backdrop {
  position: fixed;
  inset: 0;
  background-color: rgba(9, 9, 11, 0.75);
  backdrop-filter: blur(8px);
  z-index: 9998;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding: 12vh 1rem 2rem;
  animation: fadeIn 0.15s ease;
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

.palette-window {
  width: 100%;
  max-width: 640px;
  background: var(--surface-1);
  border: 1px solid var(--border-strong);
  border-radius: 8px;
  box-shadow: 0 32px 64px -16px rgba(0, 0, 0, 0.85);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.palette-search {
  display: flex;
  align-items: center;
  padding: 0.875rem 1.25rem;
  border-bottom: 1px solid var(--border-subtle);
  background: var(--surface-1);
  gap: 0.75rem;
}

.search-icon {
  font-size: 1.125rem;
  color: var(--text-muted);
}

.search-input {
  flex: 1;
  background: transparent;
  border: none;
  font-family: var(--font-body);
  font-size: 1rem;
  color: var(--text-primary);
  outline: none;
}

.search-input::placeholder {
  color: var(--text-muted);
}

.esc-badge {
  font-family: var(--font-mono);
  font-size: 0.6875rem;
  padding: 0.15rem 0.4rem;
  background: var(--surface-2);
  border: 1px solid var(--border-subtle);
  border-radius: 3px;
  color: var(--text-muted);
  cursor: pointer;
}

.palette-results {
  max-height: 380px;
  overflow-y: auto;
  padding: 0.5rem;
}

.palette-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.625rem 0.875rem;
  border-radius: 4px;
  cursor: pointer;
  transition: background 0.1s ease;
  margin-bottom: 2px;
}

.palette-item.is-selected {
  background: var(--surface-2);
}

.item-left {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  min-width: 0;
}

.item-icon-box {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  background: var(--surface-3);
  color: var(--text-secondary);
  font-size: 0.875rem;
  flex-shrink: 0;
}

.palette-item.is-selected .item-icon-box {
  color: var(--accent);
  background: var(--accent-subtle);
}

.item-text {
  min-width: 0;
}

.item-title {
  font-size: 0.875rem;
  font-weight: 500;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.item-subtitle {
  font-size: 0.75rem;
  color: var(--text-muted);
  font-family: var(--font-mono);
  margin-top: 1px;
}

.item-right {
  display: flex;
  align-items: center;
  gap: 0.625rem;
  flex-shrink: 0;
}

.item-badge {
  font-family: var(--font-mono);
  font-size: 0.75rem;
  font-weight: 600;
  padding: 0.15rem 0.45rem;
  border-radius: 3px;
  background: var(--surface-3);
  color: var(--text-primary);
}

.item-cat {
  font-family: var(--font-mono);
  font-size: 0.625rem;
  text-transform: uppercase;
  color: var(--text-muted);
  letter-spacing: 0.05em;
}

.no-results {
  padding: 2.5rem 1rem;
  text-align: center;
}

.empty-title {
  margin: 0 0 0.35rem;
  font-weight: 500;
  font-size: 0.875rem;
  color: var(--text-primary);
}

.empty-hint {
  font-size: 0.75rem;
  color: var(--text-muted);
}

.palette-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.625rem 1.25rem;
  background: var(--bg);
  border-top: 1px solid var(--border-subtle);
  font-size: 0.75rem;
  color: var(--text-muted);
}

.footer-hint {
  display: flex;
  gap: 1rem;
}

.footer-hint kbd {
  font-family: var(--font-mono);
  font-size: 0.6875rem;
  background: var(--surface-2);
  border: 1px solid var(--border-subtle);
  padding: 0.1rem 0.3rem;
  border-radius: 2px;
  margin-right: 0.25rem;
}

.footer-brand {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  font-family: var(--font-mono);
  font-size: 0.6875rem;
  letter-spacing: 0.08em;
}

.radar-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--accent);
}
</style>
