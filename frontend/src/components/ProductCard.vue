<script setup lang="ts">
import { computed, ref } from 'vue'
import { ApiError, api } from '../api/client'
import { deleteProduct, refreshProduct, updateProduct, type Product } from '../api/products'
import Card from 'primevue/card'
import Button from 'primevue/button'
import Tag from 'primevue/tag'

const props = defineProps<{ product: Product }>()
const emit = defineEmits<{ edit: [product: Product]; changed: [product: Product]; removed: [id: number] }>()

const busy = ref<'refresh' | 'toggle' | 'delete' | 'favorite' | null>(null)
const error = ref('')

const price = computed(() => {
  const p = props.product
  if (p.currentPrice === null) return 'No price yet'
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
    error.value = e instanceof ApiError ? e.message : 'Unexpected error'
  } finally {
    busy.value = null
  }
}

const refresh = () => run('refresh', async () => emit('changed', await refreshProduct(props.product.id)))

const toggle = () =>
  run('toggle', async () => emit('changed', await updateProduct(props.product.id, { active: !props.product.active })))

const toggleFavorite = () => 
  run('favorite', async () => {
      const data = await api<Product>(`/products/${props.product.id}/favorite`, { method: 'PUT' })
      emit('changed', data)
  })

const remove = () => {
  if (!window.confirm(`Delete "${props.product.name}"? Price history will also be removed.`)) return
  return run('delete', async () => {
    await deleteProduct(props.product.id)
    emit('removed', props.product.id)
  })
}
</script>

<template>
  <Card :class="['product-card', { 'opacity-60': !product.active }]">
    <template #title>
        <div class="flex justify-between items-start gap-2">
            <RouterLink :to="{ name: 'product-detail', params: { id: product.id } }" class="text-[var(--p-surface-0)] hover:text-green no-underline font-bold text-lg">
                {{ product.name }}
            </RouterLink>
            <Button 
                :icon="product.isFavorite ? 'pi pi-star-fill' : 'pi pi-star'" 
                :class="product.isFavorite ? 'text-green' : 'text-muted'"
                text rounded aria-label="Favorite" 
                @click="toggleFavorite" 
                :loading="busy === 'favorite'"
                style="padding: 0; width: 2rem; height: 2rem; flex-shrink: 0;"
            />
        </div>
    </template>
    
    <template #subtitle>
        <div class="flex items-center gap-2 mt-1">
            <Tag v-if="product.store" :value="product.store" severity="secondary" rounded />
            <a :href="product.url" target="_blank" rel="noopener noreferrer" class="text-green text-sm hover:underline flex items-center gap-1">
                Open Store <i class="pi pi-external-link" style="font-size: 0.7rem"></i>
            </a>
        </div>
    </template>

    <template #content>
        <div class="text-3xl font-bold mt-2 mb-4">{{ price }}</div>
        
        <div class="flex gap-2 text-sm">
            <span v-if="product.inStock !== null" :class="product.inStock ? 'text-green' : 'text-red'">
                <i :class="product.inStock ? 'pi pi-check-circle' : 'pi pi-times-circle'"></i>
                {{ product.inStock ? 'In Stock' : 'Out of Stock' }}
            </span>
            <span v-if="!product.active" class="text-red">
                <i class="pi pi-pause-circle"></i> Paused
            </span>
        </div>
        
        <p v-if="error" class="text-red text-sm mt-2">{{ error }}</p>
    </template>

    <template #footer>
        <div class="flex gap-2 flex-wrap">
            <Button icon="pi pi-refresh" label="Refresh" size="small" severity="secondary" :loading="busy === 'refresh'" @click="refresh" />
            <Button :icon="product.active ? 'pi pi-pause' : 'pi pi-play'" :label="product.active ? 'Pause' : 'Resume'" size="small" severity="secondary" :loading="busy === 'toggle'" @click="toggle" />
            <Button icon="pi pi-pencil" label="Edit" size="small" severity="secondary" @click="emit('edit', product)" :disabled="busy !== null" />
            <Button icon="pi pi-trash" label="Delete" size="small" severity="danger" text :loading="busy === 'delete'" @click="remove" />
        </div>
    </template>
  </Card>
</template>

<style scoped>
.product-card {
    transition: transform 0.2s;
}
.product-card:hover {
    transform: translateY(-2px);
}
</style>
