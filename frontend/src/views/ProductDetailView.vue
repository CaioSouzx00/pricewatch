<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ApiError } from '../api/client'
import { getPriceHistory, getProduct, type PricePoint, type Product } from '../api/products'
import AppHeader from '../components/AppHeader.vue'
import PriceChart from '../components/PriceChart.vue'
import { computeStats, formatMoney } from '../utils/stats'

const route = useRoute()
const id = Number(route.params.id)

const product = ref<Product | null>(null)
const history = ref<PricePoint[]>([])
const loading = ref(true)
const error = ref('')

const currency = computed(() => product.value?.currency ?? 'BRL')
const stats = computed(() => computeStats(history.value))
const money = (v: number) => formatMoney(v, currency.value)

function signed(change: { amount: number; percent: number } | null) {
  if (!change) return '—'
  const sign = change.amount > 0 ? '+' : change.amount < 0 ? '−' : ''
  return `${sign}${money(Math.abs(change.amount))} (${sign}${Math.abs(change.percent).toFixed(2)}%)`
}

// Preço subir é ruim (vermelho), cair é bom (verde).
const tone = (change: { amount: number } | null) =>
  !change || change.amount === 0 ? '' : change.amount < 0 ? 'down' : 'up'

async function load() {
  loading.value = true
  error.value = ''
  try {
    if (!Number.isInteger(id)) throw new ApiError(400, 'Produto inválido')
    ;[product.value, history.value] = await Promise.all([getProduct(id), getPriceHistory(id)])
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Erro inesperado'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <AppHeader />

  <main class="app-main">
    <RouterLink to="/" class="back">← Voltar para produtos</RouterLink>

    <div v-if="loading" id="detail-loading" class="state" role="status">
      <span class="spinner" aria-hidden="true"></span> Carregando...
    </div>

    <div v-else-if="error" id="detail-error" class="state" role="alert">
      <p class="error">{{ error }}</p>
      <button class="btn ghost" type="button" @click="load">Tentar novamente</button>
    </div>

    <template v-else-if="product">
      <header class="head">
        <h1>{{ product.name }}</h1>
        <span v-if="product.store" class="badge">{{ product.store }}</span>
        <a :href="product.url" target="_blank" rel="noopener noreferrer" class="link">Abrir na loja ↗</a>
      </header>

      <div v-if="!stats" id="history-empty" class="state">
        <p>Ainda não há histórico de preços. Use "Atualizar preço" na lista de produtos para coletar o primeiro.</p>
      </div>

      <template v-else>
        <section class="stats" aria-label="Estatísticas de preço">
          <div class="stat">
            <span>Preço atual</span>
            <strong id="stat-current">{{ money(stats.current) }}</strong>
          </div>
          <div class="stat">
            <span>Última variação</span>
            <strong id="stat-last" :class="tone(stats.lastChange)">{{ signed(stats.lastChange) }}</strong>
          </div>
          <div class="stat">
            <span>Desde a 1ª coleta</span>
            <strong id="stat-total" :class="tone(stats.totalChange)">{{ signed(stats.totalChange) }}</strong>
          </div>
          <div class="stat">
            <span>Mínimo</span>
            <strong id="stat-min">{{ money(stats.min) }}</strong>
          </div>
          <div class="stat">
            <span>Máximo</span>
            <strong id="stat-max">{{ money(stats.max) }}</strong>
          </div>
          <div class="stat">
            <span>Média ({{ stats.count }} coletas)</span>
            <strong id="stat-avg">{{ money(stats.average) }}</strong>
          </div>
        </section>

        <section class="panel">
          <h2>Evolução do preço</h2>
          <PriceChart v-if="history.length > 1" :points="history" :currency="currency" />
          <p v-else class="subtitle">São necessárias ao menos duas coletas para exibir o gráfico.</p>
        </section>
      </template>
    </template>
  </main>
</template>

<style scoped>
.back {
  display: inline-block;
  margin-bottom: 1rem;
  color: var(--muted);
  text-decoration: none;
  font-size: 0.9rem;
}
.back:hover {
  color: var(--text);
}
.head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.75rem;
  margin-bottom: 1.5rem;
}
.head h1 {
  margin: 0;
  font-size: 1.5rem;
  overflow-wrap: anywhere;
}
.badge {
  padding: 0.15rem 0.55rem;
  border-radius: 999px;
  font-size: 0.72rem;
  background: hsla(190, 90%, 55%, 0.15);
  color: var(--accent-2);
}
.link {
  font-size: 0.85rem;
  color: var(--accent-2);
  text-decoration: none;
}
.stats {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(190px, 1fr));
  gap: 0.75rem;
  margin-bottom: 1.5rem;
}
.stat {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  padding: 1rem;
  border: 1px solid var(--border);
  border-radius: 14px;
  background: var(--surface);
}
.stat span {
  font-size: 0.78rem;
  color: var(--muted);
}
.stat strong {
  font-size: 1.1rem;
}
.up {
  color: var(--danger);
}
.down {
  color: hsl(150, 70%, 60%);
}
.panel {
  padding: 1.25rem;
  border: 1px solid var(--border);
  border-radius: 16px;
  background: var(--surface);
}
.panel h2 {
  margin: 0 0 1rem;
  font-size: 1rem;
}
.state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.75rem;
  padding: 3rem 1rem;
  color: var(--muted);
  border: 1px dashed var(--border);
  border-radius: 16px;
  text-align: center;
}
#detail-loading {
  flex-direction: row;
  justify-content: center;
}
.spinner {
  width: 1.2rem;
  height: 1.2rem;
  border: 2px solid var(--border);
  border-top-color: var(--accent);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
