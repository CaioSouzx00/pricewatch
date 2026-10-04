<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { api, ApiError } from '../api/client'
import { getPriceHistory, getProduct, type PricePoint, type Product } from '../api/products'
import PriceChart from '../components/PriceChart.vue'
import { formatMoney } from '../utils/stats'
import Card from 'primevue/card'
import Tag from 'primevue/tag'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import Select from 'primevue/select'
import InputNumber from 'primevue/inputnumber'
import Message from 'primevue/message'

const route = useRoute()
const id = Number(route.params.id)

const product = ref<Product | null>(null)
const history = ref<PricePoint[]>([])
const analytics = ref<any>(null)
const loading = ref(true)
const error = ref('')

const currency = computed(() => product.value?.currency ?? 'BRL')
const money = (v: number | string | null | undefined) => {
    if (v == null) return '—'
    return formatMoney(Number(v), currency.value)
}

const buyScore = computed(() => {
    if (!product.value || !analytics.value || !product.value.currentPrice) return null
    const price = Number(product.value.currentPrice)
    const min = Number(analytics.value.minPrice)
    const avg = Number(analytics.value.averagePrice)
    
    if (price <= min * 1.02) return { label: 'Great Deal', severity: 'success', icon: 'pi pi-verified' }
    if (price < avg) return { label: 'Good Price', severity: 'info', icon: 'pi pi-thumbs-up' }
    if (price > avg * 1.05) return { label: 'Expensive', severity: 'danger', icon: 'pi pi-exclamation-triangle' }
    return { label: 'Fair Price', severity: 'secondary', icon: 'pi pi-minus' }
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    if (!Number.isInteger(id)) throw new ApiError(400, 'Produto inválido')
    const [p, h, a] = await Promise.all([
        getProduct(id), 
        getPriceHistory(id),
        api<any>(`/products/${id}/analytics`)
    ])
    product.value = p
    history.value = h
    analytics.value = a
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Erro inesperado'
  } finally {
    loading.value = false
  }
}

const showDialog = ref(false)
const alertForm = ref({ type: 'PRICE_BELOW', targetPrice: null as number | null, percentageDrop: null as number | null })
const alertError = ref('')
const alertLoading = ref(false)

const alertTypes = [
    { label: 'Target Price', value: 'PRICE_BELOW' },
    { label: 'Price Increase', value: 'PRICE_UP' },
    { label: 'Back in Stock', value: 'STOCK_CHANGE' },
    { label: 'Percentage Drop', value: 'PERCENTAGE_DROP' },
    { label: 'Historical Minimum', value: 'HISTORICAL_MIN' }
]

async function submitAlert() {
    alertError.value = ''
    alertLoading.value = true
    try {
        await api('/alerts', {
            method: 'POST',
            body: JSON.stringify({
                productId: id,
                type: alertForm.value.type,
                targetPrice: alertForm.value.targetPrice,
                percentageDrop: alertForm.value.percentageDrop
            })
        })
        showDialog.value = false
    } catch (e) {
        alertError.value = e instanceof ApiError ? e.message : 'Failed to create alert'
    } finally {
        alertLoading.value = false
    }
}

onMounted(load)
</script>

<template>
  <div class="pb-8">
    <RouterLink to="/products" class="text-muted hover:text-green no-underline text-sm mb-4 inline-block">
      <i class="pi pi-arrow-left text-xs mr-1"></i> Back to products
    </RouterLink>

    <div v-if="loading" class="flex flex-col items-center p-8 text-muted border border-dashed border-[var(--p-surface-700)] rounded-xl mt-4">
      <i class="pi pi-spin pi-spinner text-2xl mb-2"></i>
      <span>Loading...</span>
    </div>

    <div v-else-if="error" class="flex flex-col items-center p-8 text-red border border-dashed border-red-900 rounded-xl bg-red-950/20 mt-4">
      <p class="mb-4">{{ error }}</p>
      <Button label="Try again" severity="secondary" @click="load" />
    </div>

    <template v-else-if="product">
      <div class="flex flex-wrap items-center gap-4 mb-6 mt-2">
        <h1 class="text-2xl font-bold m-0">{{ product.name }}</h1>
        <Tag v-if="product.store" :value="product.store" severity="secondary" rounded />
        <Tag v-if="buyScore" :value="buyScore.label" :severity="buyScore.severity" :icon="buyScore.icon" rounded />
        <div class="ml-auto flex items-center gap-3">
          <Button icon="pi pi-bell" label="Add Alert" size="small" @click="showDialog = true" />
          <a :href="product.url" target="_blank" rel="noopener noreferrer" class="text-green text-sm hover:underline flex items-center gap-1">
            Open in Store <i class="pi pi-external-link text-xs"></i>
          </a>
        </div>
      </div>

      <div class="grid grid-cols-1 lg:grid-cols-3 gap-6 mb-6">
        <!-- Main Info -->
        <Card class="lg:col-span-1">
            <template #title>Current Status</template>
            <template #content>
                <div class="text-4xl font-bold mb-2">{{ money(product.currentPrice) }}</div>
                <div class="flex flex-col gap-2 text-sm mt-4">
                    <div class="flex justify-between border-b border-[var(--p-surface-800)] pb-2">
                        <span class="text-muted">Stock</span>
                        <span :class="product.inStock ? 'text-green' : 'text-red font-bold'">
                            {{ product.inStock ? 'In Stock' : 'Out of Stock' }}
                        </span>
                    </div>
                    <div class="flex justify-between border-b border-[var(--p-surface-800)] pb-2">
                        <span class="text-muted">Tracking</span>
                        <span :class="product.active ? 'text-green' : 'text-red'">
                            {{ product.active ? 'Active' : 'Paused' }}
                        </span>
                    </div>
                </div>
            </template>
        </Card>

        <!-- Analytics -->
        <Card class="lg:col-span-2">
            <template #title>Price Intelligence</template>
            <template #content>
                <div v-if="analytics && history.length > 0" class="grid grid-cols-2 sm:grid-cols-3 gap-4">
                    <div class="p-3 bg-[var(--p-surface-950)] rounded-lg border border-[var(--p-surface-800)]">
                        <div class="text-xs text-muted mb-1">Historical Minimum</div>
                        <div class="text-lg font-bold">{{ money(analytics.minPrice) }}</div>
                        <div class="text-xs text-muted mt-1">{{ analytics.daysSinceLowest }} days ago</div>
                    </div>
                    <div class="p-3 bg-[var(--p-surface-950)] rounded-lg border border-[var(--p-surface-800)]">
                        <div class="text-xs text-muted mb-1">Historical Average</div>
                        <div class="text-lg font-bold">{{ money(analytics.averagePrice) }}</div>
                    </div>
                    <div class="p-3 bg-[var(--p-surface-950)] rounded-lg border border-[var(--p-surface-800)]">
                        <div class="text-xs text-muted mb-1">Historical Maximum</div>
                        <div class="text-lg font-bold">{{ money(analytics.maxPrice) }}</div>
                    </div>
                    <div class="p-3 bg-[var(--p-surface-950)] rounded-lg border border-[var(--p-surface-800)]">
                        <div class="text-xs text-muted mb-1">Change from Average</div>
                        <div class="text-lg font-bold" :class="(analytics.percentageChange || 0) <= 0 ? 'text-green' : 'text-red'">
                            {{ (analytics.percentageChange || 0) <= 0 ? '' : '+' }}{{ analytics.percentageChange }}%
                        </div>
                    </div>
                    <div class="p-3 bg-[var(--p-surface-950)] rounded-lg border border-[var(--p-surface-800)]">
                        <div class="text-xs text-muted mb-1">Volatility</div>
                        <div class="text-lg font-bold">{{ analytics.volatility }}</div>
                    </div>
                </div>
                <div v-else class="text-muted text-sm py-4">
                    Not enough data points for analytics.
                </div>
            </template>
        </Card>
      </div>

      <Card>
        <template #title>Price History</template>
        <template #content>
          <PriceChart v-if="history.length > 1" :points="history" :currency="currency" />
          <div v-else class="text-muted text-sm py-8 text-center border border-dashed border-[var(--p-surface-800)] rounded-lg">
            At least two data points are required to display the chart.
          </div>
        </template>
      </Card>

      <Dialog v-model:visible="showDialog" modal header="Create Alert" :style="{ width: '400px' }">
        <div class="flex flex-col gap-4 mt-2">
          <Message v-if="alertError" severity="error" :closable="false">{{ alertError }}</Message>
          <div class="flex flex-col gap-2">
            <label class="text-sm font-bold">Alert Rule</label>
            <Select v-model="alertForm.type" :options="alertTypes" optionLabel="label" optionValue="value" class="w-full" />
          </div>
          <div class="flex flex-col gap-2" v-if="alertForm.type === 'PRICE_BELOW' || alertForm.type === 'PRICE_UP'">
            <label class="text-sm font-bold">Target Price</label>
            <InputNumber v-model="alertForm.targetPrice" mode="currency" :currency="currency" locale="pt-BR" class="w-full" />
          </div>
          <div class="flex flex-col gap-2" v-if="alertForm.type === 'PERCENTAGE_DROP'">
            <label class="text-sm font-bold">Percentage Drop (%)</label>
            <InputNumber v-model="alertForm.percentageDrop" suffix="%" class="w-full" />
          </div>
          <div class="flex justify-end gap-2 mt-4">
            <Button label="Cancel" severity="secondary" text @click="showDialog = false" />
            <Button label="Create Alert" :loading="alertLoading" @click="submitAlert" />
          </div>
        </div>
      </Dialog>
    </template>
  </div>
</template>

<style scoped>
/* Basic grid utilities since we don't have tailwind fully available */
.grid { display: grid; }
.grid-cols-1 { grid-template-columns: repeat(1, minmax(0, 1fr)); }
.grid-cols-2 { grid-template-columns: repeat(2, minmax(0, 1fr)); }
@media (min-width: 640px) {
    .sm\:grid-cols-3 { grid-template-columns: repeat(3, minmax(0, 1fr)); }
}
@media (min-width: 1024px) {
    .lg\:grid-cols-3 { grid-template-columns: repeat(3, minmax(0, 1fr)); }
    .lg\:col-span-1 { grid-column: span 1 / span 1; }
    .lg\:col-span-2 { grid-column: span 2 / span 2; }
}
.pb-8 { padding-bottom: 2rem; }
.mb-4 { margin-bottom: 1rem; }
.mb-6 { margin-bottom: 1.5rem; }
.mt-2 { margin-top: 0.5rem; }
.mt-4 { margin-top: 1rem; }
.p-3 { padding: 0.75rem; }
.py-4 { padding-top: 1rem; padding-bottom: 1rem; }
.py-8 { padding-top: 2rem; padding-bottom: 2rem; }
.rounded-lg { border-radius: 0.5rem; }
</style>
