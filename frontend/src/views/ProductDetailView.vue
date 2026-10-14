<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, ApiError } from '../api/client'
import { getPriceHistory, getProduct, refreshProduct, deleteProduct, type PricePoint, type Product } from '../api/products'
import PriceChart from '../components/PriceChart.vue'
import PriceRangeBar from '../components/PriceRangeBar.vue'
import ProductFormModal from '../components/ProductFormModal.vue'
import { formatMoney } from '../utils/stats'
import { formatDelta, pricePosition, toSeries } from '../utils/priceSeries'
import { useCountUp } from '../composables/useCountUp'
import { useConfirm } from 'primevue/useconfirm'
import { useToast } from 'primevue/usetoast'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import Select from 'primevue/select'
import InputNumber from 'primevue/inputnumber'
import Message from 'primevue/message'

const route = useRoute()
const router = useRouter()
const confirm = useConfirm()
const toast = useToast()

const id = Number(route.params.id)

const product = ref<Product | null>(null)
const history = ref<PricePoint[]>([])
const analytics = ref<any>(null)
const loading = ref(true)
const syncing = ref(false)
const error = ref('')
const editModalOpen = ref(false)

const currency = computed(() => product.value?.currency ?? 'BRL')
const currentPriceNum = computed(() => product.value?.currentPrice ? Number(product.value.currentPrice) : 0)

const { display: priceDisplay } = useCountUp(() => currentPriceNum.value, { currency: currency.value })

const money = (v: number | string | null | undefined) => {
  if (v == null) return '—'
  return formatMoney(Number(v), currency.value)
}

const buyScore = computed(() => {
  if (!product.value || !analytics.value || !product.value.currentPrice) return null
  const price = Number(product.value.currentPrice)
  const min = Number(analytics.value.minPrice)
  const avg = Number(analytics.value.averagePrice)

  if (price <= min * 1.01) return { label: 'MENOR HISTÓRICO', class: 'score-great', icon: 'pi pi-verified' }
  if (price < avg) return { label: 'ABAIXO DA MÉDIA', class: 'score-good', icon: 'pi pi-arrow-down-right' }
  if (price > avg * 1.05) return { label: 'ACIMA DA MÉDIA', class: 'score-high', icon: 'pi pi-arrow-up-right' }
  return { label: 'PREÇO JUSTO', class: 'score-fair', icon: 'pi pi-minus' }
})

const position = computed(() =>
  pricePosition(toSeries(history.value), product.value?.currentPrice ? Number(product.value.currentPrice) : undefined)
)

async function load() {
  loading.value = true
  error.value = ''
  try {
    if (!Number.isInteger(id)) throw new ApiError(400, 'Identificador de produto inválido')
    const [p, h, a] = await Promise.all([
      getProduct(id),
      getPriceHistory(id),
      api<any>(`/products/${id}/analytics`).catch(() => null)
    ])
    product.value = p
    history.value = h
    analytics.value = a
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Falha ao recuperar dados do ativo'
  } finally {
    loading.value = false
  }
}

async function syncNow() {
  if (!product.value) return
  syncing.value = true
  try {
    const updated = await refreshProduct(product.value.id)
    product.value = updated
    const [h, a] = await Promise.all([
      getPriceHistory(id),
      api<any>(`/products/${id}/analytics`).catch(() => null)
    ])
    history.value = h
    analytics.value = a
    toast.add({
      severity: 'success',
      summary: 'Ativo Sincronizado',
      detail: 'Cotação e histórico atualizados com sucesso.',
      life: 3000
    })
  } catch (e) {
    const msg = e instanceof ApiError ? e.message : 'Não foi possível sincronizar'
    toast.add({ severity: 'error', summary: 'Erro na Coleta', detail: msg, life: 3500 })
  } finally {
    syncing.value = false
  }
}

function removeProduct() {
  if (!product.value) return
  confirm.require({
    message: `Confirma a remoção definitiva de "${product.value.name}"? Todos os pontos históricos serão apagados.`,
    header: 'Excluir Ativo',
    icon: 'pi pi-trash',
    rejectProps: {
      label: 'Cancelar',
      severity: 'secondary'
    },
    acceptProps: {
      label: 'Excluir Definitivamente',
      severity: 'danger'
    },
    accept: async () => {
      try {
        await deleteProduct(product.value!.id)
        toast.add({
          severity: 'success',
          summary: 'Ativo Removido',
          detail: 'O produto foi excluído com sucesso.',
          life: 3000
        })
        router.push('/products')
      } catch (e) {
        toast.add({ severity: 'error', summary: 'Erro', detail: 'Falha ao remover produto.', life: 3000 })
      }
    }
  })
}

// Export functions (CSV / JSON)
function exportCSV() {
  if (!history.value.length || !product.value) return
  const headers = 'Data e Hora,Preco,Moeda,ID_Produto\n'
  const rows = history.value
    .map((p) => `"${p.checkedAt}",${p.price},"${currency.value}",${id}`)
    .join('\n')

  const blob = new Blob([headers + rows], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `historico-precos-ativo-${id}.csv`
  link.click()
  URL.revokeObjectURL(url)

  toast.add({
    severity: 'info',
    summary: 'Exportação Concluída',
    detail: 'Arquivo CSV gerado e baixado com sucesso.',
    life: 3000
  })
}

function exportJSON() {
  if (!product.value) return
  const data = {
    product: product.value,
    analytics: analytics.value,
    history: history.value,
    exportedAt: new Date().toISOString()
  }
  const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `dados-ativo-${id}.json`
  link.click()
  URL.revokeObjectURL(url)

  toast.add({
    severity: 'info',
    summary: 'Exportação Concluída',
    detail: 'Arquivo JSON gerado e baixado com sucesso.',
    life: 3000
  })
}

// Alert creation modal
const showAlertModal = ref(false)
const alertForm = ref({
  type: 'PRICE_BELOW',
  targetPrice: null as number | null,
  percentageDrop: null as number | null
})
const alertError = ref('')
const alertLoading = ref(false)

const alertTypes = [
  { label: 'Queda Abaixo do Alvo', value: 'PRICE_BELOW' },
  { label: 'Aumento de Preço', value: 'PRICE_UP' },
  { label: 'Retorno ao Estoque', value: 'STOCK_CHANGE' },
  { label: 'Queda Percentual (%)', value: 'PERCENTAGE_DROP' },
  { label: 'Mínima Histórica', value: 'HISTORICAL_MIN' }
]

async function submitAlert() {
  alertError.value = ''
  alertLoading.value = true
  try {
    // FIX: Client.ts already calls JSON.stringify(options.body).
    // Send plain object to avoid double JSON escaping (400 error).
    await api('/alerts', {
      method: 'POST',
      body: {
        productId: id,
        type: alertForm.value.type,
        targetPrice: alertForm.value.targetPrice,
        percentageDrop: alertForm.value.percentageDrop
      }
    })
    showAlertModal.value = false
    toast.add({
      severity: 'success',
      summary: 'Alerta Armado',
      detail: 'Você será notificado assim que o gatilho de preço for atingido.',
      life: 3500
    })
  } catch (e) {
    alertError.value = e instanceof ApiError ? e.message : 'Falha ao cadastrar alerta'
  } finally {
    alertLoading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="product-detail-root">
    <!-- Back breadcrumb -->
    <div class="detail-nav-bar">
      <RouterLink to="/products" class="back-link cursor-hover">
        <i class="pi pi-arrow-left" />
        <span>Retornar ao Catálogo de Ativos</span>
      </RouterLink>

      <div class="detail-meta-id">
        <span class="tech-label">ATIVO // #{{ String(id).padStart(4, '0') }}</span>
      </div>
    </div>

    <!-- Loading State -->
    <div v-if="loading" class="terminal-skeleton detail-sk" />

    <!-- Error State -->
    <div v-else-if="error" class="terminal-state-box error-box">
      <i class="pi pi-exclamation-triangle text-red" />
      <p class="error-msg">{{ error }}</p>
      <Button label="Tentar Novamente" severity="secondary" @click="load" />
    </div>

    <!-- Main Product Detail Content -->
    <template v-else-if="product">
      <!-- Hero Top Bar -->
      <section class="detail-hero-section">
        <div class="hero-header-row">
          <div class="hero-title-group">
            <div class="hero-badges">
              <span v-if="product.store" class="store-badge">{{ product.store }}</span>
              <span v-if="buyScore" class="score-badge" :class="buyScore.class">
                <i :class="buyScore.icon" />
                {{ buyScore.label }}
              </span>
              <span class="stock-badge" :class="product.inStock ? 'in-stock' : 'out-of-stock'">
                {{ product.inStock ? 'EM ESTOQUE' : 'ESGOTADO' }}
              </span>
            </div>
            <h1 class="product-headline">{{ product.name }}</h1>
          </div>

          <div class="hero-actions-group">
            <Button
              icon="pi pi-bell"
              label="Configurar Alerta"
              class="p-button-primary cursor-hover"
              @click="showAlertModal = true"
            />
            <Button
              icon="pi pi-refresh"
              label="Sincronizar"
              severity="secondary"
              :loading="syncing"
              class="cursor-hover"
              @click="syncNow"
            />
            <a
              :href="product.url"
              target="_blank"
              rel="noopener noreferrer"
              class="open-store-btn cursor-hover"
            >
              <span>Ver na Loja</span>
              <i class="pi pi-external-link" />
            </a>
          </div>
        </div>

        <!-- Monumental Hero Price Grid -->
        <div class="monumental-price-grid">
          <div class="price-hero-card">
            <span class="tech-label">COTAÇÃO ATUAL // VALOR VIGENTE</span>
            <div class="huge-price tabular num">{{ priceDisplay }}</div>
            <div v-if="position" class="price-verdict">
              <span class="verdict-delta" :class="position.vsAvg <= 0 ? 'tone-good' : 'tone-danger'">
                {{ formatDelta(position.vsAvg) }}
              </span>
              <span class="verdict-context text-muted">
                em relação à média histórica calculada de {{ money(analytics?.averagePrice) }}
              </span>
            </div>
          </div>

          <div class="quick-intel-card">
            <div class="intel-header">
              <span class="tech-label">DISPERSÃO HISTÓRICA</span>
              <div class="export-dropdown-group">
                <button type="button" class="export-btn cursor-hover" title="Exportar CSV" @click="exportCSV">
                  <i class="pi pi-file-excel" /> CSV
                </button>
                <button type="button" class="export-btn cursor-hover" title="Exportar JSON" @click="exportJSON">
                  <i class="pi pi-code" /> JSON
                </button>
              </div>
            </div>

            <PriceRangeBar
              v-if="position"
              :position="position"
              :currency="currency"
              labels
              class="hero-range-bar"
            />

            <div v-if="analytics" class="intel-facts-grid">
              <div class="intel-fact">
                <span class="tech-label">MÍNIMA HISTÓRICA</span>
                <span class="fact-val num tone-good">{{ money(analytics.minPrice) }}</span>
                <span class="fact-sub text-muted">
                  {{ analytics.daysSinceLowest === 0 ? 'Hoje' : `${analytics.daysSinceLowest}d atrás` }}
                </span>
              </div>
              <div class="intel-fact">
                <span class="tech-label">MÁXIMA HISTÓRICA</span>
                <span class="fact-val num">{{ money(analytics.maxPrice) }}</span>
              </div>
              <div class="intel-fact">
                <span class="tech-label">VOLATILIDADE</span>
                <span class="fact-val num">±{{ money(analytics.volatility) }}</span>
              </div>
              <div class="intel-fact">
                <span class="tech-label">COLETAS REALIZADAS</span>
                <span class="fact-val num">{{ history.length }}</span>
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- History Chart Card -->
      <section class="chart-section-card">
        <div class="card-topline">
          <div class="card-title-group">
            <span class="tech-label">SÉRIE TEMPORAL // COLETAS DO SCRAPER</span>
            <h2 class="card-section-title">Histórico de Cotações em Degraus</h2>
          </div>
          <div class="chart-actions-right">
            <button
              type="button"
              class="danger-link-btn"
              @click="removeProduct"
            >
              <i class="pi pi-trash" /> Excluir Ativo
            </button>
          </div>
        </div>

        <div class="chart-content-area">
          <PriceChart
            v-if="history.length > 1"
            :points="history"
            :currency="currency"
          />
          <div v-else class="insufficient-history">
            <i class="pi pi-info-circle info-icon" />
            <p class="insufficient-msg">Aguardando mais coletas para desenhar o gráfico interativo.</p>
            <span class="insufficient-sub">São necessárias no mínimo duas coletas em horários distintos.</span>
          </div>
        </div>
      </section>

      <!-- Create Alert Dialog (FIXED: Plain object body, no JSON.stringify duplication) -->
      <Dialog
        v-model:visible="showAlertModal"
        modal
        header="Criar Regra de Alerta de Preço"
        :style="{ width: '440px' }"
      >
        <div class="dialog-body">
          <Message v-if="alertError" severity="error" :closable="false" class="dialog-msg">
            {{ alertError }}
          </Message>

          <div class="dialog-field">
            <label class="dialog-label">REGRA DE DISPARO</label>
            <Select
              v-model="alertForm.type"
              :options="alertTypes"
              optionLabel="label"
              optionValue="value"
              class="w-full"
            />
          </div>

          <div
            v-if="alertForm.type === 'PRICE_BELOW' || alertForm.type === 'PRICE_UP'"
            class="dialog-field"
          >
            <label class="dialog-label">PREÇO ALVO ({{ currency }})</label>
            <InputNumber
              v-model="alertForm.targetPrice"
              mode="currency"
              :currency="currency"
              locale="pt-BR"
              class="w-full"
            />
            <span class="field-sub">Preço atual: {{ money(product.currentPrice) }}</span>
          </div>

          <div
            v-if="alertForm.type === 'PERCENTAGE_DROP'"
            class="dialog-field"
          >
            <label class="dialog-label">QUEDA MÍNIMA PERCENTUAL (%)</label>
            <InputNumber
              v-model="alertForm.percentageDrop"
              suffix="%"
              class="w-full"
            />
          </div>

          <div class="dialog-footer">
            <Button
              label="Cancelar"
              severity="secondary"
              type="button"
              @click="showAlertModal = false"
            />
            <Button
              label="Armar Alerta"
              class="p-button-primary"
              :loading="alertLoading"
              @click="submitAlert"
            />
          </div>
        </div>
      </Dialog>
    </template>
  </div>
</template>

<style scoped>
.product-detail-root {
  display: flex;
  flex-direction: column;
  gap: 2rem;
}

.detail-nav-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.back-link {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  color: var(--text-muted);
  text-decoration: none;
  font-size: 0.8125rem;
  transition: color var(--duration-fast) ease;
}

.back-link:hover {
  color: var(--accent);
}

/* --- Hero Section --- */
.detail-hero-section {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.hero-header-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  flex-wrap: wrap;
  gap: 1.5rem;
  padding-bottom: 1.25rem;
  border-bottom: 1px solid var(--border-subtle);
}

.hero-title-group {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  flex: 1;
  min-width: 320px;
}

.hero-badges {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex-wrap: wrap;
}

.store-badge {
  font-family: var(--font-mono);
  font-size: 0.6875rem;
  padding: 0.15rem 0.45rem;
  background: var(--surface-2);
  border: 1px solid var(--border-subtle);
  border-radius: 3px;
  color: var(--text-secondary);
}

.score-badge {
  font-family: var(--font-mono);
  font-size: 0.6875rem;
  font-weight: 600;
  padding: 0.15rem 0.5rem;
  border-radius: 3px;
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
}

.score-badge.score-great {
  background: var(--accent-subtle);
  color: var(--accent);
  border: 1px solid var(--accent-border);
}

.score-badge.score-good {
  background: var(--surface-2);
  color: var(--accent);
}

.score-badge.score-high {
  background: var(--danger-subtle);
  color: var(--danger);
}

.score-badge.score-fair {
  background: var(--surface-2);
  color: var(--text-secondary);
}

.stock-badge {
  font-family: var(--font-mono);
  font-size: 0.625rem;
  padding: 0.15rem 0.4rem;
  border-radius: 2px;
}
.stock-badge.in-stock { color: var(--accent); }
.stock-badge.out-of-stock { color: var(--danger); background: var(--danger-subtle); }

.product-headline {
  font-family: var(--font-display);
  font-size: clamp(1.5rem, 3vw, 2.25rem);
  font-weight: 700;
  color: var(--text-primary);
  margin: 0;
  letter-spacing: -0.02em;
  line-height: 1.2;
}

.hero-actions-group {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  flex-wrap: wrap;
}

.open-store-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.55rem 1rem;
  background: var(--surface-2);
  border: 1px solid var(--border-subtle);
  border-radius: 4px;
  color: var(--text-primary);
  font-size: 0.8125rem;
  font-weight: 500;
  text-decoration: none;
  transition: all var(--duration-fast) ease;
}

.open-store-btn:hover {
  background: var(--surface-3);
  border-color: var(--border-strong);
}

/* --- Monumental Price Grid --- */
.monumental-price-grid {
  display: grid;
  grid-template-columns: 1.2fr 1.8fr;
  gap: 1.5rem;
}

.price-hero-card {
  background: var(--surface-1);
  border: 1px solid var(--border-subtle);
  border-radius: 6px;
  padding: 1.75rem;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 0.5rem;
}

.huge-price {
  font-family: var(--font-display);
  font-size: clamp(2.5rem, 5vw, 4rem);
  font-weight: 700;
  color: var(--text-primary);
  letter-spacing: -0.04em;
  line-height: 1;
}

.price-verdict {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  margin-top: 0.5rem;
}

.verdict-delta {
  font-family: var(--font-mono);
  font-size: 1.125rem;
  font-weight: 700;
}

.verdict-context {
  font-size: 0.75rem;
  line-height: 1.4;
}

.quick-intel-card {
  background: var(--surface-1);
  border: 1px solid var(--border-subtle);
  border-radius: 6px;
  padding: 1.75rem;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 1.25rem;
}

.intel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.export-dropdown-group {
  display: flex;
  gap: 0.35rem;
}

.export-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  font-family: var(--font-mono);
  font-size: 0.6875rem;
  padding: 0.25rem 0.55rem;
  background: var(--surface-2);
  border: 1px solid var(--border-subtle);
  border-radius: 3px;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all var(--duration-fast) ease;
}

.export-btn:hover {
  background: var(--surface-3);
  color: var(--text-primary);
  border-color: var(--border-strong);
}

.hero-range-bar {
  margin: 0.5rem 0;
}

.intel-facts-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 1rem;
  padding-top: 1rem;
  border-top: 1px solid var(--border-subtle);
}

.intel-fact {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}

.fact-val {
  font-family: var(--font-display);
  font-size: 1.125rem;
  font-weight: 700;
  color: var(--text-primary);
}

.fact-sub {
  font-family: var(--font-mono);
  font-size: 0.6875rem;
}

/* --- Chart Section Card --- */
.chart-section-card {
  background: var(--surface-1);
  border: 1px solid var(--border-subtle);
  border-radius: 6px;
  padding: 1.75rem;
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.card-topline {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 1rem;
}

.card-title-group {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.card-section-title {
  font-family: var(--font-display);
  font-size: 1.25rem;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0;
}

.danger-link-btn {
  background: transparent;
  border: none;
  color: var(--danger);
  font-family: var(--font-body);
  font-size: 0.75rem;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0.35rem 0.6rem;
  border-radius: 3px;
  transition: background var(--duration-fast) ease;
}

.danger-link-btn:hover {
  background: var(--danger-subtle);
}

.insufficient-history {
  padding: 3.5rem 1rem;
  text-align: center;
  border: 1px dashed var(--border-subtle);
  border-radius: 6px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
}

.info-icon {
  font-size: 1.5rem;
  color: var(--text-muted);
}

.insufficient-msg {
  font-weight: 600;
  margin: 0;
  color: var(--text-primary);
}

.insufficient-sub {
  font-size: 0.75rem;
  color: var(--text-muted);
}

/* --- Dialog Form --- */
.dialog-body {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.dialog-msg {
  margin: 0;
}

.dialog-field {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.dialog-label {
  font-family: var(--font-mono);
  font-size: 0.6875rem;
  letter-spacing: 0.08em;
  color: var(--text-muted);
}

.field-sub {
  font-family: var(--font-mono);
  font-size: 0.6875rem;
  color: var(--text-muted);
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 0.75rem;
  margin-top: 0.5rem;
  padding-top: 1rem;
  border-top: 1px solid var(--border-subtle);
}

.w-full {
  width: 100%;
}

.detail-sk {
  height: 480px;
}

@media (max-width: 960px) {
  .monumental-price-grid {
    grid-template-columns: 1fr;
  }
  .intel-facts-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
