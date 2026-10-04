<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ApiError } from '../api/client'
import { listProducts, type Product } from '../api/products'
import ProductCard from '../components/ProductCard.vue'
import ProductFormModal from '../components/ProductFormModal.vue'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import Select from 'primevue/select'

const products = ref<Product[]>([])
const loading = ref(true)
const error = ref('')
const formOpen = ref(false)
const editing = ref<Product | null>(null)

const searchQuery = ref('')
const sortOption = ref('date_desc')

const sortOptions = [
    { label: 'Date Added (Newest)', value: 'date_desc' },
    { label: 'Date Added (Oldest)', value: 'date_asc' },
    { label: 'Name (A-Z)', value: 'name_asc' },
    { label: 'Name (Z-A)', value: 'name_desc' },
    { label: 'Price (Low to High)', value: 'price_asc' },
    { label: 'Price (High to Low)', value: 'price_desc' }
]

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

const filteredProducts = computed(() => {
    let result = products.value

    if (searchQuery.value.trim()) {
        const query = searchQuery.value.toLowerCase()
        result = result.filter(p => 
            p.name.toLowerCase().includes(query) || 
            p.url.toLowerCase().includes(query) ||
            (p.store && p.store.toLowerCase().includes(query))
        )
    }

    return result.sort((a, b) => {
        switch (sortOption.value) {
            case 'date_asc':
                return a.createdAt.localeCompare(b.createdAt)
            case 'date_desc':
                return b.createdAt.localeCompare(a.createdAt)
            case 'name_asc':
                return a.name.localeCompare(b.name)
            case 'name_desc':
                return b.name.localeCompare(a.name)
            case 'price_asc':
                return (Number(a.currentPrice) || 0) - (Number(b.currentPrice) || 0)
            case 'price_desc':
                return (Number(b.currentPrice) || 0) - (Number(a.currentPrice) || 0)
            default:
                return 0
        }
    })
})

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

    <div class="flex gap-4 mb-6" v-if="products.length > 0">
        <span class="relative flex-grow">
            <i class="pi pi-search absolute left-3 top-1/2 -translate-y-1/2 text-muted" />
            <InputText v-model="searchQuery" placeholder="Search by name, url, or store..." class="w-full pl-10" />
        </span>
        <Select v-model="sortOption" :options="sortOptions" optionLabel="label" optionValue="value" class="w-64" />
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

    <div v-else-if="filteredProducts.length === 0" class="flex flex-col items-center p-8 text-muted border border-dashed border-[var(--p-surface-700)] rounded-xl">
        <p class="mb-0">No products match your search query.</p>
    </div>

    <div v-else class="grid">
      <ProductCard
        v-for="product in filteredProducts"
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
.w-64 { width: 16rem; }
.pl-10 { padding-left: 2.5rem; }
</style>
