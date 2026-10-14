<script setup lang="ts">
import { computed, ref } from 'vue'
import { ApiError, api } from '../api/client'
import { deleteProduct, refreshProduct, updateProduct, type Product } from '../api/products'
import { formatMoney } from '../utils/stats'
import { useConfirm } from 'primevue/useconfirm'
import { useToast } from 'primevue/usetoast'
import PriceSparkline from './PriceSparkline.vue'

const props = defineProps<{
  product: Product
  sparklineValues?: number[]
  variationPercent?: number | null
}>()

const emit = defineEmits<{
  edit: [product: Product]
  changed: [product: Product]
  removed: [id: number]
}>()

const confirm = useConfirm()
const toast = useToast()

const busy = ref<'refresh' | 'toggle' | 'delete' | 'favorite' | null>(null)
const cardRef = ref<HTMLElement | null>(null)
const imgTranslate = ref({ x: 0, y: 0 })

const formattedId = computed(() => `#${String(props.product.id).padStart(4, '0')}`)

const priceFormatted = computed(() => {
  const p = props.product
  if (p.currentPrice === null) return 'Sob Consulta'
  return formatMoney(Number(p.currentPrice), p.currency)
})

const lastUpdateFormatted = computed(() => {
  try {
    const d = new Date(props.product.updatedAt || props.product.createdAt)
    return new Intl.DateTimeFormat('pt-BR', {
      day: '2-digit',
      month: '2-digit',
      hour: '2-digit',
      minute: '2-digit'
    }).format(d)
  } catch {
    return 'Recente'
  }
})

function onMouseMove(e: MouseEvent) {
  if (!cardRef.value) return
  const rect = cardRef.value.getBoundingClientRect()
  const x = (e.clientX - rect.left) / rect.width - 0.5
  const y = (e.clientY - rect.top) / rect.height - 0.5
  imgTranslate.value = { x: x * 8, y: y * 8 }
}

function onMouseLeave() {
  imgTranslate.value = { x: 0, y: 0 }
}

async function run(kind: NonNullable<typeof busy.value>, action: () => Promise<void>) {
  busy.value = kind
  try {
    await action()
  } catch (e) {
    const msg = e instanceof ApiError ? e.message : 'Falha na operação'
    toast.add({ severity: 'error', summary: 'Erro', detail: msg, life: 3500 })
  } finally {
    busy.value = null
  }
}

function refresh() {
  run('refresh', async () => {
    const updated = await refreshProduct(props.product.id)
    emit('changed', updated)
    toast.add({
      severity: 'success',
      summary: 'Preço Sincronizado',
      detail: `Cotação de "${props.product.name}" atualizada com sucesso.`,
      life: 3000
    })
  })
}

function toggle() {
  run('toggle', async () => {
    const updated = await updateProduct(props.product.id, { active: !props.product.active })
    emit('changed', updated)
    toast.add({
      severity: 'info',
      summary: updated.active ? 'Monitoramento Ativado' : 'Monitoramento Pausado',
      detail: `O rastreamento de "${props.product.name}" foi ${updated.active ? 'retomado' : 'pausado'}.`,
      life: 3000
    })
  })
}

function toggleFavorite() {
  run('favorite', async () => {
    const data = await api<Product>(`/products/${props.product.id}/favorite`, { method: 'PUT' })
    emit('changed', data)
    toast.add({
      severity: 'info',
      summary: data.isFavorite ? 'Salvo na Watchlist' : 'Removido da Watchlist',
      detail: `"${props.product.name}" foi ${data.isFavorite ? 'adicionado aos' : 'removido dos'} seus favoritos.`,
      life: 2500
    })
  })
}

function remove() {
  confirm.require({
    message: `Deseja realmente excluir "${props.product.name}"? Todo o histórico de preços acumulado será descontinuado.`,
    header: 'Confirmar Exclusão',
    icon: 'pi pi-trash',
    rejectProps: {
      label: 'Cancelar',
      severity: 'secondary'
    },
    acceptProps: {
      label: 'Excluir Ativo',
      severity: 'danger'
    },
    accept: () => {
      run('delete', async () => {
        await deleteProduct(props.product.id)
        emit('removed', props.product.id)
        toast.add({
          severity: 'success',
          summary: 'Ativo Excluído',
          detail: 'O produto e seu histórico foram removidos.',
          life: 3000
        })
      })
    }
  })
}
</script>

<template>
  <article
    ref="cardRef"
    class="product-card-root cursor-hover"
    :class="{ 'is-paused': !product.active }"
    @mousemove="onMouseMove"
    @mouseleave="onMouseLeave"
  >
    <!-- Top technical metadata line -->
    <div class="card-meta-bar">
      <div class="meta-left">
        <span class="product-id">{{ formattedId }}</span>
        <span v-if="product.store" class="store-badge">{{ product.store }}</span>
      </div>
      <div class="meta-right">
        <button
          type="button"
          class="fav-btn"
          :class="{ 'is-favorite': product.isFavorite }"
          :title="product.isFavorite ? 'Remover dos favoritos' : 'Adicionar aos favoritos'"
          :disabled="busy === 'favorite'"
          @click.stop="toggleFavorite"
        >
          <i :class="product.isFavorite ? 'pi pi-star-fill' : 'pi pi-star'" />
        </button>
      </div>
    </div>

    <!-- Main Hero: Thumbnail + Pricing info -->
    <div class="card-body">
      <div class="thumbnail-wrap">
        <img
          v-if="product.imageUrl"
          :src="product.imageUrl"
          :alt="product.name"
          loading="lazy"
          class="product-img"
          :style="{ transform: `translate3d(${imgTranslate.x}px, ${imgTranslate.y}px, 0)` }"
        />
        <div v-else class="img-placeholder">
          <div class="placeholder-grid" />
          <i class="pi pi-box placeholder-icon" />
          <span class="placeholder-tag">{{ product.store ?? 'ATIVO' }}</span>
        </div>
      </div>

      <div class="product-info">
        <RouterLink
          :to="{ name: 'product-detail', params: { id: product.id } }"
          class="product-title"
          :title="product.name"
        >
          {{ product.name }}
        </RouterLink>

        <!-- Huge Price Hero with Tabular Digits -->
        <div class="price-row">
          <span class="price-hero tabular">{{ priceFormatted }}</span>
          <span
            v-if="variationPercent !== undefined && variationPercent !== null"
            class="delta-badge"
            :class="variationPercent <= 0 ? 'tone-good' : 'tone-high'"
          >
            {{ variationPercent <= 0 ? '▼' : '▲' }} {{ Math.abs(variationPercent).toFixed(1) }}%
          </span>
        </div>

        <div class="status-row">
          <span v-if="product.inStock !== null" class="stock-pill" :class="product.inStock ? 'in-stock' : 'out-of-stock'">
            <span class="dot-indicator" />
            {{ product.inStock ? 'EM ESTOQUE' : 'ESGOTADO' }}
          </span>

          <span v-if="!product.active" class="paused-pill">
            PAUSADO
          </span>

          <span class="timestamp">
            UP: {{ lastUpdateFormatted }}
          </span>
        </div>
      </div>
    </div>

    <!-- Sparkline section -->
    <div v-if="sparklineValues && sparklineValues.length > 1" class="card-sparkline-row">
      <div class="sparkline-title">
        <span class="tech-label">TRAJETÓRIA</span>
      </div>
      <PriceSparkline :values="sparklineValues" :width="116" :height="26" />
    </div>

    <!-- Reveal Actions Bar on Hover (clip-path reveal) -->
    <div class="card-actions-reveal">
      <div class="actions-inner">
        <button
          type="button"
          class="action-btn"
          title="Sincronizar preço agora"
          :disabled="busy !== null"
          @click.stop="refresh"
        >
          <i class="pi pi-refresh" :class="{ 'pi-spin': busy === 'refresh' }" />
          <span>Sincronizar</span>
        </button>

        <button
          type="button"
          class="action-btn"
          :title="product.active ? 'Pausar monitoramento' : 'Retomar monitoramento'"
          :disabled="busy !== null"
          @click.stop="toggle"
        >
          <i :class="product.active ? 'pi pi-pause' : 'pi pi-play'" />
          <span>{{ product.active ? 'Pausar' : 'Retomar' }}</span>
        </button>

        <button
          type="button"
          class="action-btn"
          title="Editar produto"
          :disabled="busy !== null"
          @click.stop="emit('edit', product)"
        >
          <i class="pi pi-pencil" />
          <span>Editar</span>
        </button>

        <button
          type="button"
          class="action-btn danger-action"
          title="Excluir produto"
          :disabled="busy !== null"
          @click.stop="remove"
        >
          <i class="pi pi-trash" />
          <span>Excluir</span>
        </button>

        <a
          :href="product.url"
          target="_blank"
          rel="noopener noreferrer"
          class="action-btn link-action"
          title="Abrir no e-commerce"
          @click.stop
        >
          <i class="pi pi-external-link" />
        </a>
      </div>
    </div>
  </article>
</template>

<style scoped>
.product-card-root {
  position: relative;
  background: var(--surface-1);
  border: 1px solid var(--border-subtle);
  border-radius: 6px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  transition: border-color var(--duration-fast) ease, transform var(--duration-fast) var(--ease-terminal);
}

.product-card-root:hover {
  border-color: var(--border-strong);
  transform: translateY(-2px);
}

.product-card-root.is-paused {
  opacity: 0.65;
}

.card-meta-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.625rem 0.875rem 0.5rem;
  border-bottom: 1px solid var(--border-subtle);
}

.meta-left {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.product-id {
  font-family: var(--font-mono);
  font-size: 0.6875rem;
  color: var(--text-muted);
  letter-spacing: 0.05em;
}

.store-badge {
  font-family: var(--font-mono);
  font-size: 0.625rem;
  padding: 0.1rem 0.35rem;
  background: var(--surface-2);
  border: 1px solid var(--border-subtle);
  border-radius: 2px;
  color: var(--text-secondary);
  text-transform: uppercase;
}

.fav-btn {
  background: transparent;
  border: none;
  color: var(--text-muted);
  cursor: pointer;
  padding: 0.25rem;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  transition: all var(--duration-fast) ease;
}

.fav-btn:hover {
  color: var(--favorite);
  background: var(--surface-2);
}

.fav-btn.is-favorite {
  color: var(--favorite);
}

.card-body {
  padding: 0.875rem;
  display: flex;
  gap: 1rem;
}

.thumbnail-wrap {
  width: 76px;
  height: 76px;
  background: var(--surface-2);
  border: 1px solid var(--border-subtle);
  border-radius: 4px;
  overflow: hidden;
  position: relative;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.product-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.2s cubic-bezier(0.16, 1, 0.3, 1);
  will-change: transform;
}

.img-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  position: relative;
}

.placeholder-grid {
  position: absolute;
  inset: 0;
  background-image: radial-gradient(var(--border-strong) 1px, transparent 1px);
  background-size: 8px 8px;
  opacity: 0.3;
}

.placeholder-icon {
  font-size: 1.25rem;
  color: var(--text-muted);
  margin-bottom: 2px;
}

.placeholder-tag {
  font-family: var(--font-mono);
  font-size: 0.5rem;
  color: var(--text-muted);
  letter-spacing: 0.05em;
}

.product-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.product-title {
  font-family: var(--font-body);
  font-size: 0.875rem;
  font-weight: 600;
  color: var(--text-primary);
  text-decoration: none;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-bottom: 0.35rem;
  transition: color var(--duration-fast) ease;
}

.product-title:hover {
  color: var(--accent);
}

.price-row {
  display: flex;
  align-items: baseline;
  gap: 0.5rem;
  margin-bottom: 0.5rem;
}

.price-hero {
  font-family: var(--font-display);
  font-size: clamp(1.25rem, 2vw, 1.625rem);
  font-weight: 700;
  color: var(--text-primary);
  letter-spacing: -0.02em;
  line-height: 1.1;
}

.delta-badge {
  font-family: var(--font-mono);
  font-size: 0.6875rem;
  font-weight: 700;
  padding: 0.1rem 0.35rem;
  border-radius: 3px;
  line-height: 1;
}

.delta-badge.tone-good {
  background: var(--accent-subtle);
  color: var(--accent);
}

.delta-badge.tone-high {
  background: var(--danger-subtle);
  color: var(--danger);
}

.status-row {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  flex-wrap: wrap;
  margin-top: auto;
}

.stock-pill {
  font-family: var(--font-mono);
  font-size: 0.625rem;
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  letter-spacing: 0.05em;
}

.stock-pill.in-stock { color: var(--accent); }
.stock-pill.out-of-stock { color: var(--danger); }

.dot-indicator {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentColor;
}

.paused-pill {
  font-family: var(--font-mono);
  font-size: 0.625rem;
  padding: 0.1rem 0.35rem;
  background: var(--danger-subtle);
  color: var(--danger);
  border-radius: 2px;
}

.timestamp {
  font-family: var(--font-mono);
  font-size: 0.625rem;
  color: var(--text-muted);
  margin-left: auto;
}

.card-sparkline-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.4rem 0.875rem 0.625rem;
  border-top: 1px solid var(--border-subtle);
}

.sparkline-title {
  font-size: 0.625rem;
}

/* --- Reveal Actions (clip-path reveal on hover) --- */
.card-actions-reveal {
  background: var(--surface-2);
  border-top: 1px solid var(--border-subtle);
  padding: 0.5rem 0.75rem;
  clip-path: inset(100% 0 0 0);
  transition: clip-path 0.25s var(--ease-terminal);
}

.product-card-root:hover .card-actions-reveal,
.product-card-root:focus-within .card-actions-reveal {
  clip-path: inset(0 0 0 0);
}

.actions-inner {
  display: flex;
  align-items: center;
  gap: 0.35rem;
}

.action-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  background: var(--surface-1);
  border: 1px solid var(--border-subtle);
  border-radius: 3px;
  color: var(--text-secondary);
  font-family: var(--font-body);
  font-size: 0.6875rem;
  padding: 0.3rem 0.5rem;
  cursor: pointer;
  transition: all var(--duration-fast) ease;
  text-decoration: none;
}

.action-btn:hover {
  background: var(--surface-3);
  color: var(--text-primary);
  border-color: var(--border-strong);
}

.action-btn.danger-action:hover {
  background: var(--danger-subtle);
  color: var(--danger);
  border-color: var(--danger-border);
}

.action-btn.link-action {
  margin-left: auto;
}
</style>
