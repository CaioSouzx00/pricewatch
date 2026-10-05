<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { api } from '../api/client'
import { getPriceHistory, listProducts, type Product } from '../api/products'
import PriceRangeBar from '../components/PriceRangeBar.vue'
import PriceSparkline from '../components/PriceSparkline.vue'
import { formatDelta, pricePosition, toSeries, type Position } from '../utils/priceSeries'
import { formatMoney } from '../utils/stats'
import Skeleton from 'primevue/skeleton'

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

// Limita as requisições de histórico (uma por produto) para manter o dashboard rápido.
const MAX_ROWS = 20

const metrics = ref<DashboardMetrics | null>(null)
const rows = ref<Row[]>([])
const loading = ref(true)
const error = ref('')

async function load() {
    loading.value = true
    error.value = ''
    try {
        const [m, products] = await Promise.all([api<DashboardMetrics>('/dashboard/metrics'), listProducts()])
        metrics.value = m
        const tracked = products.filter((p) => p.active && p.currentPrice !== null).slice(0, MAX_ROWS)
        const histories = await Promise.allSettled(tracked.map((p) => getPriceHistory(p.id)))
        rows.value = tracked.map((product, i) => {
            const h = histories[i]
            const series = h.status === 'fulfilled' ? toSeries(h.value) : []
            return { product, values: series.map((s) => s.v), position: pricePosition(series, Number(product.currentPrice)) }
        })
    } catch (e) {
        console.error(e)
        error.value = 'Could not load your dashboard.'
    } finally {
        loading.value = false
    }
}

// Melhores oportunidades primeiro; produtos sem histórico suficiente por último.
const sorted = computed(() =>
    [...rows.value].sort((a, b) => (a.position?.vsAvg ?? Infinity) - (b.position?.vsAvg ?? Infinity))
)

const belowAverageCount = computed(() => rows.value.filter((r) => r.position && r.position.vsAvg <= -2).length)
const comparable = computed(() => rows.value.filter((r) => r.position))

// Quanto a cesta está acima/abaixo da soma das médias históricas (mesma moeda apenas).
const basketGap = computed(() => {
    const currencies = new Set(comparable.value.map((r) => r.product.currency))
    if (currencies.size !== 1 || comparable.value.length === 0) return null
    const gap = comparable.value.reduce((s, r) => s + (r.position!.current - r.position!.avg), 0)
    return { amount: gap, currency: [...currencies][0] }
})

const money = (v: string | number) => formatMoney(Number(v), 'BRL')
onMounted(load)
</script>

<template>
    <div class="dash">
        <h1 class="title">Dashboard</h1>

        <div v-if="loading" aria-busy="true">
            <div class="kpis">
                <Skeleton v-for="n in 4" :key="n" height="3.25rem" />
            </div>
            <Skeleton v-for="n in 4" :key="n" height="3rem" class="mb-2" />
        </div>

        <div v-else-if="error" class="state">
            <p>{{ error }}</p>
            <button class="link" @click="load">Try again</button>
        </div>

        <template v-else-if="metrics">
            <section class="kpis" aria-label="Summary">
                <div class="kpi lead">
                    <div class="label">Total value tracked</div>
                    <div class="value">{{ money(metrics.totalValueTracked) }}</div>
                    <div v-if="basketGap" class="context" :class="basketGap.amount <= 0 ? 'text-green' : 'text-red'">
                        {{ formatMoney(Math.abs(basketGap.amount), basketGap.currency) }}
                        {{ basketGap.amount <= 0 ? 'below' : 'above' }} historical averages
                    </div>
                </div>
                <div class="kpi">
                    <div class="label">Products</div>
                    <div class="value">{{ metrics.totalProducts }}</div>
                    <div v-if="metrics.staleProducts" class="context warn">{{ metrics.staleProducts }} not updated in 48h</div>
                </div>
                <div class="kpi">
                    <div class="label">Active alerts</div>
                    <div class="value">{{ metrics.activeAlerts }}</div>
                </div>
                <div class="kpi">
                    <div class="label">Alerts triggered · 7d</div>
                    <div class="value">{{ metrics.recentPriceDrops }}</div>
                </div>
            </section>

            <section aria-labelledby="position-title">
                <div class="section-head">
                    <h2 id="position-title">Price position</h2>
                    <p v-if="comparable.length">
                        {{ belowAverageCount }} of {{ comparable.length }} below their historical average
                    </p>
                </div>

                <div v-if="rows.length === 0" class="state">
                    <p>Track a product to see how its price compares to its own history.</p>
                    <RouterLink to="/products" class="link">Go to products</RouterLink>
                </div>

                <template v-else>
                    <ul class="rows">
                        <li v-for="r in sorted" :key="r.product.id">
                            <RouterLink :to="{ name: 'product-detail', params: { id: r.product.id } }" class="row">
                                <div class="name">
                                    <span class="n">{{ r.product.name }}</span>
                                    <span v-if="r.product.store" class="s">{{ r.product.store }}</span>
                                </div>
                                <PriceSparkline :values="r.values" class="spark" />
                                <div class="range">
                                    <PriceRangeBar v-if="r.position" :position="r.position" :currency="r.product.currency" />
                                    <span v-else class="s">Collecting data</span>
                                </div>
                                <div class="price">
                                    <span class="p">{{ formatMoney(Number(r.product.currentPrice), r.product.currency) }}</span>
                                    <span
                                        v-if="r.position"
                                        class="d"
                                        :class="r.position.tone === 'good' ? 'text-green' : r.position.tone === 'high' ? 'text-red' : 'text-muted'"
                                    >{{ formatDelta(r.position.vsAvg) }}</span>
                                </div>
                            </RouterLink>
                        </li>
                    </ul>
                </template>
            </section>
        </template>
    </div>
</template>

<style scoped>
.dash { max-width: 1100px; --range-bg: var(--p-surface-950); }
.title { font-size: 1.5rem; font-weight: 700; margin: 0 0 1.5rem; }

.kpis {
    display: grid;
    grid-template-columns: 1.6fr 1fr 1fr 1fr;
    gap: 1.5rem;
    padding-bottom: 1.75rem;
    margin-bottom: 2rem;
    border-bottom: 1px solid var(--p-surface-800);
}
.kpi + .kpi { padding-left: 1.5rem; border-left: 1px solid var(--p-surface-800); }
.label { font-size: 0.8125rem; color: var(--p-surface-400); margin-bottom: 0.35rem; }
.value { font-size: 1.375rem; font-weight: 600; letter-spacing: -0.01em; font-variant-numeric: tabular-nums; }
.kpi.lead .value { font-size: 2rem; letter-spacing: -0.02em; }
.context { font-size: 0.8125rem; margin-top: 0.35rem; font-variant-numeric: tabular-nums; }
.context.warn { color: #fbbf24; }

.section-head { display: flex; align-items: baseline; justify-content: space-between; gap: 1rem; flex-wrap: wrap; margin-bottom: 0.75rem; }
.section-head h2 { font-size: 1rem; font-weight: 600; margin: 0; }
.section-head p { margin: 0; font-size: 0.8125rem; color: var(--p-surface-400); }


.rows { list-style: none; margin: 0; padding: 0; }
.rows li { border-bottom: 1px solid var(--p-surface-800); }
.row {
    display: grid;
    grid-template-columns: minmax(0, 2fr) 96px minmax(0, 2fr) minmax(7rem, auto);
    align-items: center;
    gap: 1.5rem;
    padding: 0.9rem 0.5rem;
    margin: 0 -0.5rem;
    color: inherit; text-decoration: none;
    border-radius: 6px;
    transition: background 0.15s;
}
.row:hover { background: var(--p-surface-900); }
.row:focus-visible { outline: 2px solid var(--p-primary-500); outline-offset: -2px; }
.name { display: flex; flex-direction: column; min-width: 0; }
.n { font-weight: 500; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.s { font-size: 0.8125rem; color: var(--p-surface-500); }
.price { display: flex; flex-direction: column; align-items: flex-end; font-variant-numeric: tabular-nums; }
.p { font-weight: 600; }
.d { font-size: 0.8125rem; }

.state { padding: 2.5rem 1rem; text-align: center; color: var(--p-surface-400); border: 1px dashed var(--p-surface-700); border-radius: 8px; }
.state p { margin: 0 0 0.75rem; }
.link { color: var(--p-primary-500); background: none; border: 0; font: inherit; cursor: pointer; text-decoration: none; }
.link:hover { text-decoration: underline; }

@media (max-width: 900px) {
    .kpis { grid-template-columns: 1fr 1fr; }
    .kpi.lead { grid-column: 1 / -1; }
    .kpi:nth-child(2) { padding-left: 0; border-left: 0; }
}
@media (max-width: 720px) {
    /* No mobile: nome + preço em cima, barra de posição embaixo; sparkline some. */
    .row {
        grid-template-columns: minmax(0, 1fr) auto;
        grid-template-areas: 'name price' 'range range';
        gap: 0.5rem 1rem;
    }
    .name { grid-area: name; }
    .price { grid-area: price; }
    .range { grid-area: range; }
    .spark { display: none; }
}
@media (max-width: 480px) {
    .kpis { grid-template-columns: 1fr; gap: 1rem; }
    .kpi + .kpi { padding-left: 0; border-left: 0; }
}
</style>
