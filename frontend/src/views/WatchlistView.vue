<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { listProducts, type Product } from '../api/products'
import ProductCard from '../components/ProductCard.vue'
import ProductFormModal from '../components/ProductFormModal.vue'

const products = ref<Product[]>([])
const loading = ref(true)
const formOpen = ref(false)
const editing = ref<Product | null>(null)

const watchlist = computed(() => products.value.filter(p => p.isFavorite))

async function load() {
    loading.value = true
    try {
        products.value = await listProducts()
    } finally {
        loading.value = false
    }
}

function openEdit(product: Product) {
    editing.value = product
    formOpen.value = true
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
        <h1 class="text-2xl font-bold mb-6">Watchlist</h1>
        
        <div v-if="loading" class="text-muted">Loading watchlist...</div>
        
        <div v-else-if="watchlist.length === 0" class="flex flex-col items-center justify-center p-8 text-muted border border-dashed border-[var(--p-surface-700)] rounded-xl mt-4">
            <i class="pi pi-star text-4xl mb-4 text-[var(--p-surface-500)]"></i>
            <p>Your watchlist is empty.</p>
            <p class="text-sm">Go to Products and click the star icon to add them here.</p>
        </div>
        
        <div v-else class="grid">
            <ProductCard
                v-for="product in watchlist"
                :key="product.id"
                :product="product"
                @edit="openEdit"
                @changed="onChanged"
                @removed="onRemoved"
            />
        </div>
        
        <ProductFormModal v-if="formOpen" :product="editing" @close="formOpen = false" @saved="onChanged" />
    </div>
</template>

<style scoped>
.grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
    gap: 1.5rem;
}
</style>
