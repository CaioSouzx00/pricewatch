<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ApiError } from '../api/client'
import { listProducts, type Product } from '../api/products'
import ProductCard from '../components/ProductCard.vue'
import ProductFormModal from '../components/ProductFormModal.vue'
import { formatMoney } from '../utils/stats'
import { useToast } from 'primevue/usetoast'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import Select from 'primevue/select'
import Paginator from 'primevue/paginator'

const toast = useToast()

const products = ref<Product[]>([])
const loading = ref(true)
const error = ref('')
const formOpen = ref(false)
const editing = ref<Product | null>(null)
const viewMode = ref<'grid' | 'table'>('grid')

const searchQuery = ref('')
const sortOption = ref('date_desc')
const currentPage = ref(0)
const pageSize = ref(12)

const sortOptions = [
  { label: 'Data de Cadastro (Mais Recente)', value: 'date_desc' },
  { label: 'Data de Cadastro (Mais Antigo)', value: 'date_asc' },
  { label: 'Nome do Ativo (A → Z)', value: 'name_asc' },
  { label: 'Nome do Ativo (Z → A)', value: 'name_desc' },
  { label: 'Preço (Menor → Maior)', value: 'price_asc' },
  { label: 'Preço (Maior → Menor)', value: 'price_desc' }
]

async function load() {
  loading.value = true
  error.value = ''
  try {
    products.value = await listProducts()
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Falha na conexão com o terminal'
  } finally {
    loading.value = false
  }
}

const filteredProducts = computed(() => {
  let result = products.value

  if (searchQuery.value.trim()) {
    const query = searchQuery.value.toLowerCase()
    result = result.filter(p =>
      p.name.toLowerCase().includes(query) ||
      p.url.toLowerCase().includes(query) ||
      String(p.id).includes(query) ||
      (p.store && p.store.toLowerCase().includes(query))
    )
  }

  return result.sort((a, b) => {
    switch (sortOption.value) {
      case 'date_asc':
        return a.createdAt.localeCompare(b.createdAt)
      case 'date_desc':
        return b.createdAt.localeCompare(a.createdAt)
      case 'name_asc':
        return a.name.localeCompare(b.name)
      case 'name_desc':
        return b.name.localeCompare(a.name)
      case 'price_asc':
        return (Number(a.currentPrice) || 0) - (Number(b.currentPrice) || 0)
      case 'price_desc':
        return (Number(b.currentPrice) || 0) - (Number(a.currentPrice) || 0)
      default:
        return 0
    }
  })
})

const paginatedProducts = computed(() => {
  const start = currentPage.value * pageSize.value
  return filteredProducts.value.slice(start, start + pageSize.value)
})

watch([searchQuery, sortOption], () => {
  currentPage.value = 0
})

function openCreate() {
  editing.value = null
  formOpen.value = true
}

function openEdit(product: Product) {
  editing.value = product
  formOpen.value = true
}

function onSaved(saved: Product) {
  const index = products.value.findIndex((p) => p.id === saved.id)
  if (index >= 0) {
    products.value[index] = saved
    toast.add({ severity: 'success', summary: 'Ativo Atualizado', detail: `Dados de "${saved.name}" salvos.`, life: 3000 })
  } else {
    products.value.unshift(saved)
    toast.add({ severity: 'success', summary: 'Ativo Cadastrado', detail: `"${saved.name}" entrou no radar de monitoramento.`, life: 3000 })
  }
  formOpen.value = false
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
  <div class="products-view-root">
    <!-- Top Bar -->
    <header class="products-header">
      <div class="header-titles">
        <span class="tech-label">LIVRO DE ATIVOS // MONITORAMENTO</span>
        <h1 class="view-title">Produtos Rastreados</h1>
        <p class="view-sub">
          Atualmente gerenciando <strong>{{ products.length }}</strong> ativos de e-commerce com checagem automática.
        </p>
      </div>

      <div class="header-actions">
        <Button
          label="Novo Ativo"
          icon="pi pi-plus"
          class="p-button-primary cursor-hover"
          @click="openCreate"
        />
      </div>
    </header>

    <!-- Filters Strip -->
    <div v-if="products.length > 0" class="filters-strip">
      <div class="search-box">
        <i class="pi pi-search search-icon" />
        <InputText
          v-model="searchQuery"
          placeholder="Filtrar por nome, loja, #ID ou URL..."
          class="filter-search-input"
        />
        <span v-if="searchQuery" class="clear-search" @click="searchQuery = ''">
          <i class="pi pi-times" />
        </span>
      </div>

      <div class="controls-right">
        <Select
          v-model="sortOption"
          :options="sortOptions"
          optionLabel="label"
          optionValue="value"
          class="sort-select"
        />

        <div class="mode-toggle">
          <button
            type="button"
            class="mode-btn"
            :class="{ 'is-active': viewMode === 'grid' }"
            title="Visualização em Grade"
            @click="viewMode = 'grid'"
          >
            <i class="pi pi-th-large" />
          </button>
          <button
            type="button"
            class="mode-btn"
            :class="{ 'is-active': viewMode === 'table' }"
            title="Visualização em Lista Densa"
            @click="viewMode = 'table'"
          >
            <i class="pi pi-list" />
          </button>
        </div>
      </div>
    </div>

    <!-- Skeletons Loading -->
    <div v-if="loading" class="loading-grid">
      <div v-for="n in 6" :key="'sk-' + n" class="terminal-skeleton card-sk" />
    </div>

    <!-- Error State -->
    <div v-else-if="error" class="terminal-state-box">
      <i class="pi pi-exclamation-triangle text-red" />
      <p class="state-msg">{{ error }}</p>
      <Button label="Recarregar" severity="secondary" @click="load" />
    </div>

    <!-- Empty Database State -->
    <div v-else-if="products.length === 0" class="terminal-state-box">
      <svg class="empty-radar-svg" viewBox="0 0 120 120" fill="none">
        <circle cx="60" cy="60" r="54" stroke="var(--border-strong)" stroke-dasharray="4 4" stroke-width="1.5" />
        <circle cx="60" cy="60" r="36" stroke="var(--border-subtle)" stroke-width="1" />
        <circle cx="60" cy="60" r="18" stroke="var(--accent)" stroke-width="1.5" stroke-opacity="0.6" />
        <circle cx="60" cy="60" r="4" fill="var(--accent)" />
      </svg>
      <h2 class="empty-headline">Nenhum ativo monitorado</h2>
      <p class="empty-sub">
        Comece adicionando o link de um produto do Mercado Livre ou de outra loja para acompanhar variações de preço.
      </p>
      <Button
        label="Adicionar Primeiro Ativo"
        icon="pi pi-plus"
        class="p-button-primary cursor-hover"
        @click="openCreate"
      />
    </div>

    <!-- Filter Empty State -->
    <div v-else-if="filteredProducts.length === 0" class="terminal-state-box">
      <p class="state-msg">Nenhum ativo corresponde à busca "{{ searchQuery }}".</p>
      <Button label="Limpar Filtro" severity="secondary" @click="searchQuery = ''" />
    </div>

    <!-- Grid View Mode -->
    <div v-else-if="viewMode === 'grid'" class="products-grid">
      <ProductCard
        v-for="product in paginatedProducts"
        :key="product.id"
        :product="product"
        @edit="openEdit"
        @changed="onChanged"
        @removed="onRemoved"
      />
    </div>

    <!-- Table View Mode -->
    <div v-else class="table-card" data-lenis-prevent>
      <table class="market-table">
        <thead>
          <tr>
            <th>ID</th>
            <th>ATIVO</th>
            <th>LOJA</th>
            <th>PREÇO ATUAL</th>
            <th>STATUS</th>
            <th>ÚLTIMA COLETA</th>
            <th class="text-right">AÇÕES</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="p in paginatedProducts"
            :key="'tbl-' + p.id"
            class="market-row cursor-hover"
            @click="$router.push({ name: 'product-detail', params: { id: p.id } })"
          >
            <td>
              <span class="id-tag">#{{ String(p.id).padStart(4, '0') }}</span>
            </td>
            <td>
              <div class="table-name-wrap">
                <img v-if="p.imageUrl" :src="p.imageUrl" class="table-thumb" loading="lazy" />
                <span class="table-pname">{{ p.name }}</span>
              </div>
            </td>
            <td>
              <span class="table-store">{{ p.store ?? 'E-commerce' }}</span>
            </td>
            <td class="tabular num font-bold">
              {{ p.currentPrice ? formatMoney(Number(p.currentPrice), p.currency) : '—' }}
            </td>
            <td>
              <span class="status-badge" :class="p.active ? 'is-active' : 'is-paused'">
                {{ p.active ? 'Vigiando' : 'Pausado' }}
              </span>
            </td>
            <td class="tech-label">
              {{ p.updatedAt ? new Date(p.updatedAt).toLocaleDateString('pt-BR') : '—' }}
            </td>
            <td class="text-right" @click.stop>
              <Button
                icon="pi pi-pencil"
                severity="secondary"
                text
                rounded
                aria-label="Editar"
                @click="openEdit(p)"
              />
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Pagination Controls -->
    <div v-if="filteredProducts.length > pageSize" class="pagination-container">
      <Paginator
        :rows="pageSize"
        :totalRecords="filteredProducts.length"
        :rowsPerPageOptions="[12, 24, 48]"
        :first="currentPage * pageSize"
        class="terminal-paginator"
        @page="(e) => { currentPage = e.page; pageSize = e.rows; }"
      />
    </div>

    <!-- Create / Edit Modal -->
    <ProductFormModal
      v-if="formOpen"
      :product="editing"
      @close="formOpen = false"
      @saved="onSaved"
    />
  </div>
</template>

<style scoped>
.products-view-root {
  display: flex;
  flex-direction: column;
  gap: 1.75rem;
}

.products-header {
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

.filters-strip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 1rem;
}

.search-box {
  display: flex;
  align-items: center;
  position: relative;
  flex: 1;
  max-width: 440px;
}

.search-icon {
  position: absolute;
  left: 0.875rem;
  color: var(--text-muted);
  font-size: 0.875rem;
}

.filter-search-input {
  width: 100%;
  padding-left: 2.5rem !important;
}

.clear-search {
  position: absolute;
  right: 0.75rem;
  color: var(--text-muted);
  cursor: pointer;
  font-size: 0.75rem;
}

.controls-right {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.sort-select {
  min-width: 260px;
}

.mode-toggle {
  display: inline-flex;
  background: var(--surface-1);
  border: 1px solid var(--border-subtle);
  border-radius: 4px;
  padding: 2px;
}

.mode-btn {
  background: transparent;
  border: none;
  color: var(--text-muted);
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 3px;
  cursor: pointer;
  transition: all var(--duration-fast) ease;
}

.mode-btn:hover {
  color: var(--text-primary);
}

.mode-btn.is-active {
  background: var(--surface-2);
  color: var(--text-primary);
}

.products-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 1.5rem;
}

/* --- Table Mode --- */
.table-card {
  background: var(--surface-1);
  border: 1px solid var(--border-subtle);
  border-radius: 6px;
  overflow-x: auto;
}

.market-table {
  width: 100%;
  border-collapse: collapse;
  text-align: left;
  font-size: 0.8125rem;
}

.market-table th {
  padding: 0.875rem 1rem;
  background: var(--surface-2);
  border-bottom: 1px solid var(--border-subtle);
  font-family: var(--font-mono);
  font-size: 0.6875rem;
  font-weight: 600;
  color: var(--text-muted);
  letter-spacing: 0.05em;
  white-space: nowrap;
}

.market-row {
  border-bottom: 1px solid var(--border-subtle);
  transition: background var(--duration-fast) ease;
}

.market-row:hover {
  background: var(--surface-2);
}

.market-row td {
  padding: 0.875rem 1rem;
  vertical-align: middle;
}

.id-tag {
  font-family: var(--font-mono);
  font-size: 0.6875rem;
  color: var(--text-muted);
}

.table-name-wrap {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  max-width: 340px;
}

.table-thumb {
  width: 28px;
  height: 28px;
  border-radius: 3px;
  object-fit: cover;
  flex-shrink: 0;
}

.table-pname {
  font-weight: 600;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.table-store {
  font-family: var(--font-mono);
  font-size: 0.6875rem;
  color: var(--text-secondary);
}

.status-badge {
  font-family: var(--font-mono);
  font-size: 0.625rem;
  padding: 0.15rem 0.4rem;
  border-radius: 2px;
}

.status-badge.is-active {
  background: var(--accent-subtle);
  color: var(--accent);
}

.status-badge.is-paused {
  background: var(--danger-subtle);
  color: var(--danger);
}

.text-right {
  text-align: right;
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

.empty-radar-svg {
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

.state-msg {
  color: var(--text-muted);
  margin-bottom: 1rem;
}

.pagination-container {
  display: flex;
  justify-content: center;
  padding: 1.5rem 0;
  border-top: 1px solid var(--border-subtle);
  margin-top: 0.5rem;
}

@media (max-width: 768px) {
  .loading-grid {
    grid-template-columns: 1fr;
  }
  .filters-strip {
    flex-direction: column;
    align-items: stretch;
  }
  .search-box {
    max-width: 100%;
  }
  .controls-right {
    justify-content: space-between;
  }
}
</style>
