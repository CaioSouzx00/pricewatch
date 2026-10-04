<script setup lang="ts">
import { computed, ref } from 'vue'
import { ApiError } from '../api/client'
import { deleteProduct, refreshProduct, updateProduct, type Product } from '../api/products'

const props = defineProps<{ product: Product }>()
const emit = defineEmits<{ edit: [product: Product]; changed: [product: Product]; removed: [id: number] }>()

const busy = ref<'refresh' | 'toggle' | 'delete' | null>(null)
const error = ref('')

const price = computed(() => {
  const p = props.product
  if (p.currentPrice === null) return 'Sem preço ainda'
  try {
    return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: p.currency }).format(Number(p.currentPrice))
  } catch {
    return `${p.currency} ${p.currentPrice}`
  }
})

async function run(kind: NonNullable<typeof busy.value>, action: () => Promise<void>) {
  error.value = ''
  busy.value = kind
  try {
    await action()
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Erro inesperado'
  } finally {
    busy.value = null
  }
}

const refresh = () => run('refresh', async () => emit('changed', await refreshProduct(props.product.id)))

const toggle = () =>
  run('toggle', async () => emit('changed', await updateProduct(props.product.id, { active: !props.product.active })))

const remove = () => {
  if (!window.confirm(`Excluir "${props.product.name}"? O histórico de preços também será removido.`)) return
  return run('delete', async () => {
    await deleteProduct(props.product.id)
    emit('removed', props.product.id)
  })
}
</script>

<template>
  <article class="card" :class="{ paused: !product.active }">
    <header>
      <h3>
        <RouterLink :to="{ name: 'product-detail', params: { id: product.id } }" class="title-link">
          {{ product.name }}
        </RouterLink>
      </h3>
      <span v-if="product.store" class="badge">{{ product.store }}</span>
    </header>

    <p class="price">{{ price }}</p>
    <p class="meta">
      <span v-if="product.inStock !== null" :class="product.inStock ? 'ok' : 'out'">
        {{ product.inStock ? 'Em estoque' : 'Sem estoque' }}
      </span>
      <span v-if="!product.active" class="out">Monitoramento pausado</span>
    </p>
    <a :href="product.url" target="_blank" rel="noopener noreferrer" class="link">Abrir na loja ↗</a>

    <p v-if="error" class="error" role="alert">{{ error }}</p>

    <footer>
      <button class="btn ghost" type="button" :disabled="busy !== null" @click="refresh">
        {{ busy === 'refresh' ? 'Atualizando...' : 'Atualizar preço' }}
      </button>
      <button class="btn ghost" type="button" :disabled="busy !== null" @click="toggle">
        {{ product.active ? 'Pausar' : 'Retomar' }}
      </button>
      <button class="btn ghost" type="button" :disabled="busy !== null" @click="emit('edit', product)">Editar</button>
      <button class="btn ghost danger" type="button" :disabled="busy !== null" @click="remove">
        {{ busy === 'delete' ? 'Excluindo...' : 'Excluir' }}
      </button>
    </footer>
  </article>
</template>

<style scoped>
.card {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  padding: 1.25rem;
  border: 1px solid var(--border);
  border-radius: 16px;
  background: var(--surface);
  backdrop-filter: blur(12px);
  transition:
    transform 0.2s,
    border-color 0.2s;
}
.card:hover {
  transform: translateY(-3px);
  border-color: hsla(258, 90%, 70%, 0.5);
}
.card.paused {
  opacity: 0.7;
}
header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 0.5rem;
}
h3 {
  margin: 0;
  font-size: 1rem;
  overflow-wrap: anywhere;
}
.title-link {
  color: inherit;
  text-decoration: none;
}
.title-link:hover {
  color: var(--accent-2);
}
.badge {
  flex-shrink: 0;
  padding: 0.15rem 0.55rem;
  border-radius: 999px;
  font-size: 0.72rem;
  background: hsla(190, 90%, 55%, 0.15);
  color: var(--accent-2);
}
.price {
  margin: 0.4rem 0 0;
  font-size: 1.5rem;
  font-weight: 700;
}
.meta {
  display: flex;
  gap: 0.75rem;
  margin: 0;
  font-size: 0.82rem;
}
.ok {
  color: hsl(150, 70%, 60%);
}
.out {
  color: var(--danger);
}
.link {
  font-size: 0.85rem;
  color: var(--accent-2);
  text-decoration: none;
}
.link:hover {
  text-decoration: underline;
}
footer {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  margin-top: 0.75rem;
}
footer .btn {
  padding: 0.45rem 0.8rem;
  font-size: 0.82rem;
}
.btn.danger:hover:not(:disabled) {
  border-color: var(--danger);
  color: var(--danger);
  box-shadow: none;
}
.error {
  margin: 0.5rem 0 0;
}
</style>
