<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { api } from '../api/client'
import { getPriceHistory, listProducts, type Product } from '../api/products'
import PriceRangeBar from '../components/PriceRangeBar.vue'
import PriceSparkline from '../components/PriceSparkline.vue'
import ProductCard from '../components/ProductCard.vue'
import ProductFormModal from '../components/ProductFormModal.vue'
import { formatDelta, pricePosition, toSeries, type Position } from '../utils/priceSeries'
import { formatMoney } from '../utils/stats'
import { useCountUp } from '../composables/useCountUp'
import Button from 'primevue/button'

interface DashboardMetrics {
  totalProducts: number
  activeAlerts: number
  recentPriceDrops: number
  staleProducts: number
  totalValueTracked: string
}

interface Row {
  product: Product
  values: number[]
  position: Position | null
}

const MAX_ROWS = 20

const metrics = ref<DashboardMetrics | null>(null)
const rows = ref<Row[]>([])
const loading = ref(true)
const error = ref('')
const viewMode = ref<'editorial' | 'dense'>('editorial')
const createModalOpen = ref(false)

// Animated CountUp for total tracked
const totalValueNum = computed(() => Number(metrics.value?.totalValueTracked || 0))
const { display: totalTrackedDisplay } = useCountUp(() => totalValueNum.value, { currency: 'BRL' })

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [m, products] = await Promise.all([
      api<DashboardMetrics>('/dashboard/metrics'),
      listProducts()
    ])
    metrics.value = m
    const tracked = products.filter((p) => p.active && p.currentPrice !== null).slice(0, MAX_ROWS)
    const histories = await Promise.allSettled(tracked.map((p) => getPriceHistory(p.id)))

    rows.value = tracked.map((product, i) => {
      const h = histories[i]
      const series = h.status === 'fulfilled' ? toSeries(h.value) : []
      return {
        product,
        values: series.map((s) => s.v),
        position: pricePosition(series, Number(product.currentPrice))
      }
    })
  } catch {
    error.value = 'Falha ao sincronizar o livro de ofertas do terminal.'
  } finally {
    loading.value = false
  }
}

// Melhores oportunidades primeiro
const sortedRows = computed(() =>
  [...rows.value].sort((a, b) => (a.position?.vsAvg ?? Infinity) - (b.position?.vsAvg ?? Infinity))
)

const topOpportunity = computed(() => sortedRows.value[0] || null)
const otherOpportunities = computed(() => sortedRows.value.slice(1))

const belowAverageCount = computed(() => rows.value.filter((r) => r.position && r.position.vsAvg <= -2).length)
const comparable = computed(() => rows.value.filter((r) => r.position))

const basketGap = computed(() => {
  const currencies = new Set(comparable.value.map((r) => r.product.currency))
  if (currencies.size !== 1 || comparable.value.length === 0) return null
  const gap = comparable.value.reduce((s, r) => s + (r.position!.current - r.position!.avg), 0)
  return { amount: gap, currency: [...currencies][0] }
})

function onProductSaved() {
  createModalOpen.value = false
  load()
}

onMounted(load)
</script>

<template>
  <div class="dashboard-root">
    <!-- Top Terminal Meta Header -->
    <header class="dash-meta-header">
      <div class="dash-title-group">
        <span class="tech-label">TERMINAL DE CONTROLE // VISÃO GERAL</span>
        <h1 class="dash-title">Painel de Cotações</h1>
      </div>

      <div class="dash-actions">
        <!-- View Mode Switcher -->
        <div class="view-switch" role="group" aria-label="Modo de visualização">
          <button
            type="button"
            class="switch-btn cursor-hover"
            :class="{ 'is-active': viewMode === 'editorial' }"
            @click="viewMode = 'editorial'"
            title="Grid Editorial Assimétrico"
          >
            <i class="pi pi-th-large" />
            <span>Editorial</span>
          </button>
          <button
            type="button"
            class="switch-btn cursor-hover"
            :class="{ 'is-active': viewMode === 'dense' }"
            @click="viewMode = 'dense'"
            title="Tabela Densa Estilo Bolsa"
          >
            <i class="pi pi-list" />
            <span>Livro de Ofertas</span>
          </button>
        </div>

        <Button
          label="Novo Ativo"
          icon="pi pi-plus"
          class="p-button-primary cursor-hover"
          @click="createModalOpen = true"
        />
      </div>
    </header>

    <!-- Skeletons Loading State -->
    <div v-if="loading" class="loading-state">
      <div class="kpis-grid">
        <div v-for="n in 4" :key="'sk-kpi-' + n" class="terminal-skeleton kpi-sk" />
      </div>
      <div class="terminal-skeleton hero-sk" />
      <div class="grid-sk">
        <div v-for="n in 3" :key="'sk-card-' + n" class="terminal-skeleton card-sk" />
      </div>
    </div>

    <!-- Error State -->
    <div v-else-if="error" class="terminal-state-box error-box">
      <i class="pi pi-exclamation-circle state-icon text-red" />
      <p class="state-msg">{{ error }}</p>
      <Button label="Reconectar Terminal" severity="secondary" @click="load" />
    </div>

    <!-- Main Content Stream -->
    <template v-else-if="metrics">
      <!-- Monumental KPIs Strip -->
      <section class="kpis-section" aria-label="Métricas do Terminal">
        <div class="kpi-block lead-block">
          <span class="tech-label">PATRIMÔNIO VIGIADO</span>
          <div class="kpi-lead-value tabular num">{{ totalTrackedDisplay }}</div>
          <div v-if="basketGap" class="kpi-subtext" :class="basketGap.amount <= 0 ? 'tone-good' : 'tone-danger'">
            <span class="indicator-arrow">{{ basketGap.amount <= 0 ? '▼' : '▲' }}</span>
            {{ formatMoney(Math.abs(basketGap.amount), basketGap.currency) }}
            {{ basketGap.amount <= 0 ? 'abaixo da média histórica' : 'acima da média histórica' }}
          </div>
        </div>

        <div class="kpi-block">
          <span class="tech-label">ATIVOS MONITORADOS</span>
          <div class="kpi-value tabular num">{{ metrics.totalProducts }}</div>
          <div v-if="metrics.staleProducts" class="kpi-subtext tone-warning">
            <i class="pi pi-clock" /> {{ metrics.staleProducts }} estagnados há 48h+
          </div>
          <div v-else class="kpi-subtext text-muted">
            Todas as coletas atualizadas
          </div>
        </div>

        <div class="kpi-block">
          <span class="tech-label">ALERTAS ATIVOS</span>
          <div class="kpi-value tabular num">{{ metrics.activeAlerts }}</div>
          <div class="kpi-subtext text-muted">Gatilhos configurados</div>
        </div>

        <div class="kpi-block">
          <span class="tech-label">QUEDAS DETECTADAS (7D)</span>
          <div class="kpi-value tabular num tone-good">{{ metrics.recentPriceDrops }}</div>
          <div class="kpi-subtext tone-good">Oportunidades acionadas</div>
        </div>
      </section>

      <!-- Empty State -->
      <div v-if="rows.length === 0" class="terminal-state-box">
        <svg class="empty-radar-svg" viewBox="0 0 120 120" fill="none">
          <circle cx="60" cy="60" r="54" stroke="var(--border-strong)" stroke-dasharray="4 4" stroke-width="1.5" />
          <circle cx="60" cy="60" r="36" stroke="var(--border-subtle)" stroke-width="1" />
          <circle cx="60" cy="60" r="18" stroke="var(--accent)" stroke-width="1.5" stroke-opacity="0.6" />
          <line x1="60" y1="6" x2="60" y2="114" stroke="var(--border-subtle)" stroke-width="1" />
          <line x1="6" y1="60" x2="114" y2="60" stroke="var(--border-subtle)" stroke-width="1" />
          <circle cx="60" cy="60" r="4" fill="var(--accent)" />
        </svg>
        <h2 class="empty-headline">Nenhum ativo sob radar</h2>
        <p class="empty-sub">
          Cadastre links do Mercado Livre ou de lojas de sua preferência. O terminal coletará preços continuamente para identificar as quedas.
        </p>
        <Button
          label="Cadastrar Primeiro Ativo"
          icon="pi pi-plus"
          class="p-button-primary cursor-hover"
          @click="createModalOpen = true"
        />
      </div>

      <!-- Mode 1: Grid Editorial Assimétrico -->
      <section v-else-if="viewMode === 'editorial'" class="editorial-grid-section">
        <!-- Top Opportunity Hero Tile -->
        <div v-if="topOpportunity && topOpportunity.position" class="hero-opportunity-tile">
          <div class="hero-header-line">
            <div class="hero-tag">
              <span class="radar-dot" />
              <span class="tech-label">MAIOR OPORTUNIDADE DE QUEDA // #{{ String(topOpportunity.product.id).padStart(4, '0') }}</span>
            </div>
            <span v-if="topOpportunity.product.store" class="store-pill">{{ topOpportunity.product.store }}</span>
          </div>

          <div class="hero-main-content">
            <div class="hero-left">
              <RouterLink
                :to="{ name: 'product-detail', params: { id: topOpportunity.product.id } }"
                class="hero-title"
              >
                {{ topOpportunity.product.name }}
              </RouterLink>

              <div class="hero-price-wrap">
                <span class="hero-price tabular num">
                  {{ formatMoney(Number(topOpportunity.product.currentPrice), topOpportunity.product.currency) }}
                </span>
                <span class="hero-delta tone-good">
                  {{ formatDelta(topOpportunity.position.vsAvg) }} vs média
                </span>
              </div>

              <div class="hero-range-wrap">
                <span class="tech-label">POSIÇÃO NO HISTÓRICO:</span>
                <PriceRangeBar
                  :position="topOpportunity.position"
                  :currency="topOpportunity.product.currency"
                  labels
                />
              </div>
            </div>

            <div class="hero-right">
              <div class="hero-spark-wrap">
                <span class="tech-label">TRAJETÓRIA RECENTE</span>
                <PriceSparkline
                  :values="topOpportunity.values"
                  :width="180"
                  :height="52"
                />
              </div>

              <RouterLink
                :to="{ name: 'product-detail', params: { id: topOpportunity.product.id } }"
                class="hero-cta-btn cursor-hover"
              >
                <span>Análise Completa</span>
                <i class="pi pi-arrow-right" />
              </RouterLink>
            </div>
          </div>
        </div>

        <!-- Asymmetric Subgrid for Remaining Products -->
        <div class="editorial-subgrid">
          <ProductCard
            v-for="r in otherOpportunities"
            :key="r.product.id"
            :product="r.product"
            :sparkline-values="r.values"
            :variation-percent="r.position?.vsAvg"
            @changed="load"
            @removed="load"
          />
        </div>
      </section>

      <!-- Mode 2: Tabela Densa Estilo Bolsa (Livro de Cotações) -->
      <section v-else class="dense-terminal-section">
        <div class="table-container" data-lenis-prevent>
          <table class="market-table">
            <thead>
              <tr>
                <th class="th-id">ID</th>
                <th class="th-name">ATIVO / PRODUTO</th>
                <th class="th-store">LOJA</th>
                <th class="th-spark">SPARKLINE</th>
                <th class="th-range">POSIÇÃO VS HISTÓRICO</th>
                <th class="th-price">PREÇO ATUAL</th>
                <th class="th-delta">VARIAÇÃO</th>
                <th class="th-status">STATUS</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="r in sortedRows"
                :key="'row-' + r.product.id"
                class="market-row cursor-hover"
                @click="$router.push({ name: 'product-detail', params: { id: r.product.id } })"
              >
                <td class="td-id">
                  <span class="id-badge">#{{ String(r.product.id).padStart(4, '0') }}</span>
                </td>

                <td class="td-name">
                  <div class="product-cell">
                    <img
                      v-if="r.product.imageUrl"
                      :src="r.product.imageUrl"
                      :alt="r.product.name"
                      class="mini-thumb"
                      loading="lazy"
                    />
                    <div v-else class="mini-placeholder">
                      <i class="pi pi-box" />
                    </div>
                    <span class="cell-name">{{ r.product.name }}</span>
                  </div>
                </td>

                <td class="td-store">
                  <span class="store-tag">{{ r.product.store ?? 'E-commerce' }}</span>
                </td>

                <td class="td-spark">
                  <PriceSparkline :values="r.values" :width="88" :height="22" />
                </td>

                <td class="td-range">
                  <PriceRangeBar
                    v-if="r.position"
                    :position="r.position"
                    :currency="r.product.currency"
                  />
                  <span v-else class="tech-label text-muted">AGUARDANDO COLETAS</span>
                </td>

                <td class="td-price tabular num">
                  {{ formatMoney(Number(r.product.currentPrice), r.product.currency) }}
                </td>

                <td class="td-delta">
                  <span
                    v-if="r.position"
                    class="delta-badge-dense"
                    :class="r.position.tone === 'good' ? 'tone-good' : r.position.tone === 'high' ? 'tone-danger' : 'text-muted'"
                  >
                    {{ formatDelta(r.position.vsAvg) }}
                  </span>
                  <span v-else class="text-muted">—</span>
                </td>

                <td class="td-status">
                  <span
                    class="status-dot-cell"
                    :class="r.product.active ? 'active-green' : 'paused-red'"
                    :title="r.product.active ? 'Ativo' : 'Pausado'"
                  />
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </template>

    <!-- Create Product Modal -->
    <ProductFormModal
      v-if="createModalOpen"
      :product="null"
      @close="createModalOpen = false"
      @saved="onProductSaved"
    />
  </div>
</template>

<style scoped>
.dashboard-root {
  display: flex;
  flex-direction: column;
  gap: 2rem;
}

.dash-meta-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 1.5rem;
  padding-bottom: 1.5rem;
  border-bottom: 1px solid var(--border-subtle);
}

.dash-title-group {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.dash-title {
  font-family: var(--font-display);
  font-size: clamp(1.75rem, 3.5vw, 2.5rem);
  font-weight: 700;
  margin: 0;
  color: var(--text-primary);
  letter-spacing: -0.03em;
}

.dash-actions {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.view-switch {
  display: inline-flex;
  background: var(--surface-1);
  border: 1px solid var(--border-subtle);
  border-radius: 4px;
  padding: 2px;
}

.switch-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.4rem 0.75rem;
  background: transparent;
  border: none;
  border-radius: 3px;
  font-family: var(--font-body);
  font-size: 0.75rem;
  font-weight: 500;
  color: var(--text-muted);
  cursor: pointer;
  transition: all var(--duration-fast) ease;
}

.switch-btn:hover {
  color: var(--text-primary);
}

.switch-btn.is-active {
  background: var(--surface-2);
  color: var(--text-primary);
  font-weight: 600;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.4);
}

/* --- KPIs Section --- */
.kpis-section {
  display: grid;
  grid-template-columns: 1.8fr 1fr 1fr 1fr;
  gap: 1.5rem;
  background: var(--surface-1);
  border: 1px solid var(--border-subtle);
  border-radius: 6px;
  padding: 1.5rem 1.75rem;
}

.kpi-block {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  min-width: 0;
}

.kpi-block + .kpi-block {
  padding-left: 1.5rem;
  border-left: 1px solid var(--border-subtle);
}

.kpi-lead-value {
  font-family: var(--font-display);
  font-size: clamp(2rem, 3.5vw, 2.75rem);
  font-weight: 700;
  color: var(--text-primary);
  letter-spacing: -0.03em;
  line-height: 1.1;
}

.kpi-value {
  font-family: var(--font-display);
  font-size: 1.875rem;
  font-weight: 700;
  color: var(--text-primary);
  line-height: 1.1;
}

.kpi-subtext {
  font-family: var(--font-mono);
  font-size: 0.75rem;
  display: flex;
  align-items: center;
  gap: 0.35rem;
}

.indicator-arrow {
  font-size: 0.6875rem;
}

.tone-good { color: var(--accent); }
.tone-danger { color: var(--danger); }
.tone-warning { color: var(--warning); }

/* --- Hero Opportunity Tile (Editorial Grid) --- */
.hero-opportunity-tile {
  background: var(--surface-1);
  border: 1px solid var(--accent-border);
  border-radius: 6px;
  padding: 1.75rem;
  margin-bottom: 2rem;
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
  position: relative;
  overflow: hidden;
}

.hero-opportunity-tile::after {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  width: 4px;
  height: 100%;
  background: var(--accent);
}

.hero-header-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.hero-tag {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.radar-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--accent);
  box-shadow: 0 0 8px var(--accent);
}

.store-pill {
  font-family: var(--font-mono);
  font-size: 0.6875rem;
  padding: 0.15rem 0.5rem;
  background: var(--surface-2);
  border: 1px solid var(--border-subtle);
  border-radius: 3px;
  color: var(--text-secondary);
}

.hero-main-content {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  flex-wrap: wrap;
  gap: 2rem;
}

.hero-left {
  flex: 1;
  min-width: 300px;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.hero-title {
  font-family: var(--font-display);
  font-size: clamp(1.25rem, 2.5vw, 1.75rem);
  font-weight: 700;
  color: var(--text-primary);
  text-decoration: none;
  line-height: 1.2;
  transition: color var(--duration-fast) ease;
}

.hero-title:hover {
  color: var(--accent);
}

.hero-price-wrap {
  display: flex;
  align-items: baseline;
  gap: 1rem;
}

.hero-price {
  font-family: var(--font-display);
  font-size: clamp(2rem, 3.5vw, 2.75rem);
  font-weight: 700;
  letter-spacing: -0.03em;
  color: var(--text-primary);
}

.hero-delta {
  font-family: var(--font-mono);
  font-size: 1rem;
  font-weight: 700;
}

.hero-range-wrap {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  max-width: 480px;
}

.hero-right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 1.5rem;
}

.hero-spark-wrap {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 0.4rem;
}

.hero-cta-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.6rem;
  padding: 0.625rem 1.25rem;
  background: var(--accent);
  color: #09090B;
  font-weight: 600;
  font-size: 0.8125rem;
  border-radius: 4px;
  text-decoration: none;
  transition: background var(--duration-fast) ease;
}

.hero-cta-btn:hover {
  background: var(--accent-hover);
}

/* --- Editorial Subgrid --- */
.editorial-subgrid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 1.5rem;
}

/* --- Tabela Densa Estilo Bolsa --- */
.dense-terminal-section {
  background: var(--surface-1);
  border: 1px solid var(--border-subtle);
  border-radius: 6px;
  overflow: hidden;
}

.table-container {
  overflow-x: auto;
  width: 100%;
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

.id-badge {
  font-family: var(--font-mono);
  font-size: 0.6875rem;
  color: var(--text-muted);
}

.product-cell {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  min-width: 220px;
  max-width: 320px;
}

.mini-thumb {
  width: 28px;
  height: 28px;
  border-radius: 3px;
  object-fit: cover;
  flex-shrink: 0;
}

.mini-placeholder {
  width: 28px;
  height: 28px;
  background: var(--surface-3);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-muted);
  font-size: 0.75rem;
  border-radius: 3px;
  flex-shrink: 0;
}

.cell-name {
  font-weight: 500;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.store-tag {
  font-family: var(--font-mono);
  font-size: 0.6875rem;
  color: var(--text-secondary);
}

.td-spark {
  width: 100px;
}

.td-range {
  min-width: 180px;
}

.td-price {
  font-family: var(--font-display);
  font-weight: 700;
  font-size: 0.9375rem;
  color: var(--text-primary);
  white-space: nowrap;
}

.delta-badge-dense {
  font-family: var(--font-mono);
  font-size: 0.75rem;
  font-weight: 700;
}

.status-dot-cell {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.status-dot-cell.active-green {
  background: var(--accent);
  box-shadow: 0 0 6px var(--accent);
}

.status-dot-cell.paused-red {
  background: var(--danger);
}

/* --- Empty and Error States --- */
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
  width: 96px;
  height: 96px;
  margin-bottom: 1.5rem;
}

.empty-headline {
  font-family: var(--font-display);
  font-size: 1.375rem;
  font-weight: 700;
  margin: 0 0 0.5rem;
  color: var(--text-primary);
}

.empty-sub {
  font-size: 0.875rem;
  color: var(--text-muted);
  max-width: 440px;
  margin: 0 0 1.5rem;
  line-height: 1.5;
}

.loading-state {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.kpi-sk {
  height: 90px;
}

.hero-sk {
  height: 220px;
}

.grid-sk {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1.5rem;
}

.card-sk {
  height: 180px;
}

@media (max-width: 1024px) {
  .kpis-section {
    grid-template-columns: 1fr 1fr;
  }
  .kpi-block:nth-child(3) {
    padding-left: 0;
    border-left: none;
  }
}

@media (max-width: 640px) {
  .kpis-section {
    grid-template-columns: 1fr;
  }
  .kpi-block + .kpi-block {
    padding-left: 0;
    border-left: none;
    padding-top: 1rem;
    border-top: 1px solid var(--border-subtle);
  }
  .hero-main-content {
    flex-direction: column;
    align-items: flex-start;
  }
  .hero-right {
    align-items: flex-start;
    width: 100%;
  }
}
</style>
