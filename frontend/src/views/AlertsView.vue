<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { api, ApiError } from '../api/client'
import { listProducts, type Product } from '../api/products'
import { formatMoney } from '../utils/stats'
import { useConfirm } from 'primevue/useconfirm'
import { useToast } from 'primevue/usetoast'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import Select from 'primevue/select'
import InputNumber from 'primevue/inputnumber'
import Message from 'primevue/message'
import Paginator from 'primevue/paginator'

interface AlertItem {
  id: number
  productId: number
  productName?: string
  productImageUrl?: string | null
  type: string
  targetPrice: number | null
  percentageDrop: number | null
  active: boolean
  createdAt?: string
}

const confirm = useConfirm()
const toast = useToast()

const alerts = ref<AlertItem[]>([])
const products = ref<Product[]>([])
const loading = ref(true)
const currentPage = ref(0)
const pageSize = ref(10)

const paginatedAlerts = computed(() => {
  const start = currentPage.value * pageSize.value
  return alerts.value.slice(start, start + pageSize.value)
})

// Create alert modal state
const createModalOpen = ref(false)
const selectedProductId = ref<number | null>(null)
const alertType = ref('PRICE_BELOW')
const targetPrice = ref<number | null>(null)
const percentageDrop = ref<number | null>(null)
const modalError = ref('')
const modalLoading = ref(false)

const productMap = computed(() => {
  const map = new Map<number, Product>()
  for (const p of products.value) {
    map.set(p.id, p)
  }
  return map
})

const alertTypes = [
  { label: 'Queda Abaixo do Alvo', value: 'PRICE_BELOW' },
  { label: 'Aumento de Preço', value: 'PRICE_UP' },
  { label: 'Retorno ao Estoque', value: 'STOCK_CHANGE' },
  { label: 'Queda Percentual (%)', value: 'PERCENTAGE_DROP' },
  { label: 'Mínima Histórica', value: 'HISTORICAL_MIN' }
]

async function load() {
  loading.value = true
  try {
    const [alertsData, productsData] = await Promise.all([
      api<AlertItem[]>('/alerts'),
      listProducts().catch(() => [])
    ])
    products.value = productsData
    alerts.value = alertsData
  } catch (e) {
    toast.add({ severity: 'error', summary: 'Erro', detail: 'Falha ao carregar regras de alerta.', life: 3000 })
  } finally {
    loading.value = false
  }
}

function getProductName(alert: AlertItem): string {
  if (alert.productName) return alert.productName
  const p = productMap.value.get(alert.productId)
  return p ? p.name : `Ativo #${String(alert.productId).padStart(4, '0')}`
}

function getProductImage(alert: AlertItem): string | null {
  if (alert.productImageUrl) return alert.productImageUrl
  const p = productMap.value.get(alert.productId)
  return p?.imageUrl ?? null
}

function getCurrentPrice(alert: AlertItem): string {
  const p = productMap.value.get(alert.productId)
  if (!p || p.currentPrice === null) return '—'
  return formatMoney(Number(p.currentPrice), p.currency)
}

function formatRule(type: string): string {
  const map: Record<string, string> = {
    PRICE_BELOW: 'Alvo de Queda',
    PRICE_UP: 'Aumento de Cotação',
    STOCK_CHANGE: 'Retorno de Estoque',
    PERCENTAGE_DROP: 'Queda Percentual',
    HISTORICAL_MIN: 'Mínima Histórica'
  }
  return map[type] || type
}

function removeAlert(alert: AlertItem) {
  confirm.require({
    message: `Confirma a exclusão deste alerta para "${getProductName(alert)}"?`,
    header: 'Excluir Alerta',
    icon: 'pi pi-trash',
    rejectProps: {
      label: 'Cancelar',
      severity: 'secondary'
    },
    acceptProps: {
      label: 'Remover Alerta',
      severity: 'danger'
    },
    accept: async () => {
      try {
        await api(`/alerts/${alert.id}`, { method: 'DELETE' })
        alerts.value = alerts.value.filter(a => a.id !== alert.id)
        toast.add({
          severity: 'success',
          summary: 'Alerta Removido',
          detail: 'A regra de disparo foi cancelada.',
          life: 3000
        })
      } catch {
        toast.add({ severity: 'error', summary: 'Erro', detail: 'Não foi possível excluir o alerta.', life: 3000 })
      }
    }
  })
}

async function submitNewAlert() {
  if (!selectedProductId.value) {
    modalError.value = 'Selecione um ativo para vincular ao alerta.'
    return
  }

  modalError.value = ''
  modalLoading.value = true
  try {
    const newAlert = await api<AlertItem>('/alerts', {
      method: 'POST',
      body: {
        productId: selectedProductId.value,
        type: alertType.value,
        targetPrice: targetPrice.value,
        percentageDrop: percentageDrop.value
      }
    })
    alerts.value.unshift(newAlert)
    createModalOpen.value = false
    toast.add({
      severity: 'success',
      summary: 'Alerta Armado',
      detail: 'O novo gatilho foi registrado no terminal.',
      life: 3000
    })
    // Reset form
    selectedProductId.value = null
    targetPrice.value = null
    percentageDrop.value = null
  } catch (e) {
    modalError.value = e instanceof ApiError ? e.message : 'Falha ao armar alerta'
  } finally {
    modalLoading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="alerts-view-root">
    <!-- Header -->
    <header class="alerts-header">
      <div class="header-titles">
        <span class="tech-label">SISTEMA DE GATILHOS // AUTOMAÇÃO</span>
        <h1 class="view-title">Regras de Alerta</h1>
        <p class="view-sub">
          Gatilhos armados no terminal para notificar quando os ativos atingirem seus patamares de oportunidade.
        </p>
      </div>

      <div class="header-actions">
        <Button
          label="Novo Alerta"
          icon="pi pi-plus"
          class="p-button-primary cursor-hover"
          @click="createModalOpen = true"
        />
      </div>
    </header>

    <!-- Skeletons Loading -->
    <div v-if="loading" class="terminal-skeleton alerts-sk" />

    <!-- Empty State -->
    <div v-else-if="alerts.length === 0" class="terminal-state-box">
      <svg class="empty-radar-svg" viewBox="0 0 120 120" fill="none">
        <circle cx="60" cy="60" r="54" stroke="var(--border-strong)" stroke-dasharray="4 4" stroke-width="1.5" />
        <circle cx="60" cy="60" r="36" stroke="var(--border-subtle)" stroke-width="1" />
        <circle cx="60" cy="60" r="18" stroke="var(--accent)" stroke-width="1.5" stroke-opacity="0.6" />
        <path d="M60 42v22M60 74h.01" stroke="var(--accent)" stroke-width="2" stroke-linecap="round" />
      </svg>
      <h2 class="empty-headline">Nenhum gatilho armado</h2>
      <p class="empty-sub">
        Crie regras de preço alvo ou de queda percentual. O terminal notificará assim que uma oportunidade for detectada.
      </p>
      <Button
        label="Armar Primeiro Alerta"
        icon="pi pi-plus"
        class="p-button-primary cursor-hover"
        @click="createModalOpen = true"
      />
    </div>

    <!-- Alerts Table -->
    <div v-else class="alerts-table-card" data-lenis-prevent>
      <table class="market-table">
        <thead>
          <tr>
            <th>ATIVO VINCULADO</th>
            <th>REGRA DE DISPARO</th>
            <th>PARÂMETRO / ALVO</th>
            <th>PREÇO ATUAL</th>
            <th>ESTADO</th>
            <th class="text-right">AÇÕES</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="a in paginatedAlerts" :key="'alert-' + a.id" class="market-row">
            <td>
              <RouterLink :to="`/products/${a.productId}`" class="product-cell-link cursor-hover">
                <img
                  v-if="getProductImage(a)"
                  :src="getProductImage(a)!"
                  class="alert-thumb"
                  loading="lazy"
                />
                <div v-else class="alert-placeholder">
                  <i class="pi pi-box" />
                </div>
                <div class="product-cell-text">
                  <span class="product-name">{{ getProductName(a) }}</span>
                  <span class="product-id">ID #{{ String(a.productId).padStart(4, '0') }}</span>
                </div>
              </RouterLink>
            </td>

            <td>
              <span class="rule-badge">{{ formatRule(a.type) }}</span>
            </td>

            <td class="tabular num font-bold">
              <span v-if="a.type === 'PERCENTAGE_DROP'" class="tone-good">
                −{{ a.percentageDrop }}%
              </span>
              <span v-else-if="a.targetPrice">
                {{ formatMoney(a.targetPrice, 'BRL') }}
              </span>
              <span v-else class="text-muted">—</span>
            </td>

            <td class="tabular num">
              {{ getCurrentPrice(a) }}
            </td>

            <td>
              <span class="state-pill" :class="a.active ? 'state-active' : 'state-triggered'">
                <span class="state-dot" />
                {{ a.active ? 'ARMADO (ATIVO)' : 'DISPARADO' }}
              </span>
            </td>

            <td class="text-right">
              <Button
                icon="pi pi-trash"
                severity="danger"
                text
                rounded
                aria-label="Excluir Alerta"
                @click="removeAlert(a)"
              />
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Pagination Controls -->
    <div v-if="alerts.length > pageSize" class="pagination-container">
      <Paginator
        :rows="pageSize"
        :totalRecords="alerts.length"
        :rowsPerPageOptions="[10, 20, 50]"
        :first="currentPage * pageSize"
        class="terminal-paginator"
        @page="(e) => { currentPage = e.page; pageSize = e.rows; }"
      />
    </div>

    <!-- Create Alert Modal with Product Selector -->
    <Dialog
      v-model:visible="createModalOpen"
      modal
      header="Configurar Novo Gatilho de Alerta"
      :style="{ width: '460px' }"
    >
      <div class="modal-form">
        <Message v-if="modalError" severity="error" :closable="false" class="form-error">
          {{ modalError }}
        </Message>

        <div class="field-item">
          <label class="field-label">SELECIONE O ATIVO</label>
          <Select
            v-model="selectedProductId"
            :options="products"
            optionLabel="name"
            optionValue="id"
            placeholder="Selecione um produto cadastrado..."
            class="w-full"
            filter
          />
        </div>

        <div class="field-item">
          <label class="field-label">TIPO DE REGRA</label>
          <Select
            v-model="alertType"
            :options="alertTypes"
            optionLabel="label"
            optionValue="value"
            class="w-full"
          />
        </div>

        <div v-if="alertType === 'PRICE_BELOW' || alertType === 'PRICE_UP'" class="field-item">
          <label class="field-label">PREÇO ALVO (R$)</label>
          <InputNumber
            v-model="targetPrice"
            mode="currency"
            currency="BRL"
            locale="pt-BR"
            class="w-full"
          />
        </div>

        <div v-if="alertType === 'PERCENTAGE_DROP'" class="field-item">
          <label class="field-label">QUEDA PERCENTUAL MÍNIMA (%)</label>
          <InputNumber
            v-model="percentageDrop"
            suffix="%"
            class="w-full"
          />
        </div>

        <div class="modal-actions">
          <Button
            label="Cancelar"
            severity="secondary"
            type="button"
            @click="createModalOpen = false"
          />
          <Button
            label="Armar Gatilho"
            class="p-button-primary"
            :loading="modalLoading"
            @click="submitNewAlert"
          />
        </div>
      </div>
    </Dialog>
  </div>
</template>

<style scoped>
.alerts-view-root {
  display: flex;
  flex-direction: column;
  gap: 1.75rem;
}

.alerts-header {
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

.alerts-table-card {
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

.product-cell-link {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  text-decoration: none;
  min-width: 220px;
}

.alert-thumb {
  width: 32px;
  height: 32px;
  border-radius: 4px;
  object-fit: cover;
  flex-shrink: 0;
}

.alert-placeholder {
  width: 32px;
  height: 32px;
  background: var(--surface-3);
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-muted);
  font-size: 0.875rem;
  flex-shrink: 0;
}

.product-cell-text {
  display: flex;
  flex-direction: column;
}

.product-name {
  font-weight: 600;
  color: var(--text-primary);
  font-size: 0.875rem;
}

.product-id {
  font-family: var(--font-mono);
  font-size: 0.6875rem;
  color: var(--text-muted);
}

.rule-badge {
  font-family: var(--font-mono);
  font-size: 0.6875rem;
  padding: 0.15rem 0.45rem;
  background: var(--surface-2);
  border: 1px solid var(--border-subtle);
  border-radius: 3px;
  color: var(--text-secondary);
}

.state-pill {
  font-family: var(--font-mono);
  font-size: 0.625rem;
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0.15rem 0.5rem;
  border-radius: 3px;
  font-weight: 600;
}

.state-pill.state-active {
  background: var(--accent-subtle);
  color: var(--accent);
}

.state-pill.state-triggered {
  background: var(--surface-3);
  color: var(--text-muted);
}

.state-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentColor;
}

.text-right {
  text-align: right;
}

.alerts-sk {
  height: 320px;
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

/* Modal Form */
.modal-form {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.form-error {
  margin: 0;
}

.field-item {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.field-label {
  font-family: var(--font-mono);
  font-size: 0.6875rem;
  letter-spacing: 0.08em;
  color: var(--text-muted);
}

.modal-actions {
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

.pagination-container {
  display: flex;
  justify-content: center;
  padding: 1.5rem 0;
  border-top: 1px solid var(--border-subtle);
  margin-top: 0.5rem;
}
</style>
