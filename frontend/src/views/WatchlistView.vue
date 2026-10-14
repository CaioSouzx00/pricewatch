<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { listProducts, type Product } from '../api/products'
import ProductCard from '../components/ProductCard.vue'
import ProductFormModal from '../components/ProductFormModal.vue'
import { formatMoney } from '../utils/stats'
import Button from 'primevue/button'

const products = ref<Product[]>([])
const loading = ref(true)
const formOpen = ref(false)
const editing = ref<Product | null>(null)

const watchlist = computed(() => products.value.filter(p => p.isFavorite))

const totalWatchlistValue = computed(() =>
  watchlist.value.reduce((acc, p) => acc + (Number(p.currentPrice) || 0), 0)
)

async function load() {
  loading.value = true
  try {
    products.value = await listProducts()
  } finally {
    loading.value = false
  }
}

function openEdit(product: Product) {
  editing.value = product
  formOpen.value = true
}

function onChanged(updated: Product) {
  const index = products.value.findIndex((p) => p.id === updated.id)
  if (index >= 0) products.value[index] = updated
}

function onRemoved(id: number) {
  products.value = products.value.filter((p) => p.id !== id)
}

onMounted(load)
</script>

<template>
  <div class="watchlist-view-root">
    <!-- Header -->
    <header class="watchlist-header">
      <div class="header-titles">
        <span class="tech-label">WATCHLIST // SELEÇÃO PRIORITÁRIA</span>
        <h1 class="view-title">Ativos Favoritos</h1>
        <p class="view-sub">
          Produtos marcados para aquisição imediata assim que a meta de desconto for acionada.
        </p>
      </div>

      <div v-if="watchlist.length > 0" class="watchlist-stats">
        <div class="stat-box">
          <span class="tech-label">TOTAL PRIORITÁRIO</span>
          <span class="stat-number tabular num">{{ watchlist.length }}</span>
        </div>
        <div class="stat-box">
          <span class="tech-label">VALOR TOTAL ESTIMADO</span>
          <span class="stat-number tabular num">{{ formatMoney(totalWatchlistValue, 'BRL') }}</span>
        </div>
      </div>
    </header>

    <!-- Skeletons -->
    <div v-if="loading" class="loading-grid">
      <div v-for="n in 3" :key="'sk-' + n" class="terminal-skeleton card-sk" />
    </div>

    <!-- Empty State -->
    <div v-else-if="watchlist.length === 0" class="terminal-state-box">
      <svg class="empty-star-svg" viewBox="0 0 100 100" fill="none">
        <polygon
          points="50,10 62,35 88,38 68,56 74,82 50,68 26,82 32,56 12,38 38,35"
          stroke="var(--favorite)"
          stroke-width="1.5"
          fill="none"
          stroke-dasharray="3 3"
        />
        <circle cx="50" cy="50" r="4" fill="var(--favorite)" />
      </svg>
      <h2 class="empty-headline">Sua Watchlist está vazia</h2>
      <p class="empty-sub">
        Navegue até a aba de Produtos ou use a busca para favoritar os ativos que deseja monitorar com prioridade.
      </p>
      <RouterLink to="/products" class="p-button p-button-primary cursor-hover">
        Ir para Produtos
      </RouterLink>
    </div>

    <!-- Watchlist Grid -->
    <div v-else class="watchlist-grid">
      <ProductCard
        v-for="product in watchlist"
        :key="product.id"
        :product="product"
        @edit="openEdit"
        @changed="onChanged"
        @removed="onRemoved"
      />
    </div>

    <ProductFormModal
      v-if="formOpen"
      :product="editing"
      @close="formOpen = false"
      @saved="onChanged"
    />
  </div>
</template>

<style scoped>
.watchlist-view-root {
  display: flex;
  flex-direction: column;
  gap: 1.75rem;
}

.watchlist-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 1.5rem;
  padding-bottom: 1.5rem;
  border-bottom: 1px solid var(--border-subtle);
}

.header-titles {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.view-title {
  font-family: var(--font-display);
  font-size: clamp(1.75rem, 3.5vw, 2.5rem);
  font-weight: 700;
  margin: 0;
  color: var(--text-primary);
  letter-spacing: -0.03em;
}

.view-sub {
  font-size: 0.8125rem;
  color: var(--text-muted);
  margin: 0;
}

.watchlist-stats {
  display: flex;
  gap: 1.5rem;
  background: var(--surface-1);
  border: 1px solid var(--border-subtle);
  border-radius: 4px;
  padding: 0.75rem 1.25rem;
}

.stat-box {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}

.stat-number {
  font-family: var(--font-display);
  font-size: 1.25rem;
  font-weight: 700;
  color: var(--text-primary);
}

.watchlist-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 1.5rem;
}

.loading-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1.5rem;
}

.card-sk {
  height: 180px;
}

.terminal-state-box {
  padding: 4rem 2rem;
  text-align: center;
  background: var(--surface-1);
  border: 1px dashed var(--border-strong);
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.empty-star-svg {
  width: 80px;
  height: 80px;
  margin-bottom: 1.25rem;
}

.empty-headline {
  font-family: var(--font-display);
  font-size: 1.25rem;
  font-weight: 700;
  margin: 0 0 0.5rem;
  color: var(--text-primary);
}

.empty-sub {
  font-size: 0.8125rem;
  color: var(--text-muted);
  max-width: 400px;
  margin: 0 0 1.5rem;
  line-height: 1.5;
}

@media (max-width: 768px) {
  .loading-grid {
    grid-template-columns: 1fr;
  }
}
</style>
