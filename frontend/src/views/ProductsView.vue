<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ApiError } from '../api/client'
import { listProducts, type Product } from '../api/products'
import ProductCard from '../components/ProductCard.vue'
import ProductFormModal from '../components/ProductFormModal.vue'
import Button from 'primevue/button'

const products = ref<Product[]>([])
const loading = ref(true)
const error = ref('')
const formOpen = ref(false)
const editing = ref<Product | null>(null)

async function load() {
  loading.value = true
  error.value = ''
  try {
    products.value = await listProducts()
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Unexpected error'
  } finally {
    loading.value = false
  }
}

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
  if (index >= 0) products.value[index] = saved
  else products.value.unshift(saved)
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
  <div>
    <div class="flex justify-between items-center mb-6">
      <div>
        <h1 class="text-2xl font-bold m-0">Products</h1>
        <p class="text-muted m-0 mt-1">Products you are currently tracking.</p>
      </div>
      <Button label="New Product" icon="pi pi-plus" @click="openCreate" />
    </div>

    <div v-if="loading" class="flex flex-col items-center p-8 text-muted border border-dashed border-[var(--p-surface-700)] rounded-xl">
      <i class="pi pi-spin pi-spinner text-2xl mb-2"></i>
      <span>Loading products...</span>
    </div>

    <div v-else-if="error" class="flex flex-col items-center p-8 text-red border border-dashed border-red-900 rounded-xl bg-red-950/20">
      <p class="mb-4">{{ error }}</p>
      <Button label="Try again" severity="secondary" @click="load" />
    </div>

    <div v-else-if="products.length === 0" class="flex flex-col items-center p-8 text-muted border border-dashed border-[var(--p-surface-700)] rounded-xl">
      <p class="mb-4">You are not monitoring any products yet.</p>
      <Button label="Add your first product" icon="pi pi-plus" @click="openCreate" />
    </div>

    <div v-else class="grid">
      <ProductCard
        v-for="product in products"
        :key="product.id"
        :product="product"
        @edit="openEdit"
        @changed="onChanged"
        @removed="onRemoved"
      />
    </div>

    <ProductFormModal v-if="formOpen" :product="editing" @close="formOpen = false" @saved="onSaved" />
  </div>
</template>

<style scoped>
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 1.5rem;
}
</style>
