<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ApiError } from '../api/client'
import { listProducts, type Product } from '../api/products'
import AppHeader from '../components/AppHeader.vue'
import ProductCard from '../components/ProductCard.vue'
import ProductFormModal from '../components/ProductFormModal.vue'

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
    error.value = e instanceof ApiError ? e.message : 'Erro inesperado'
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
  <AppHeader />

  <main class="app-main">
    <div class="toolbar">
      <div>
        <h1>Meus produtos</h1>
        <p class="subtitle">Produtos que você está monitorando.</p>
      </div>
      <button id="new-product" class="btn add" type="button" @click="openCreate">+ Novo produto</button>
    </div>

    <div v-if="loading" id="products-loading" class="state" role="status">
      <span class="spinner" aria-hidden="true"></span> Carregando produtos...
    </div>

    <div v-else-if="error" id="products-error" class="state" role="alert">
      <p class="error">{{ error }}</p>
      <button class="btn ghost" type="button" @click="load">Tentar novamente</button>
    </div>

    <div v-else-if="products.length === 0" id="products-empty" class="state">
      <p>Você ainda não monitora nenhum produto.</p>
      <button class="btn add" type="button" @click="openCreate">Adicionar o primeiro</button>
    </div>

    <div v-else id="products-list" class="grid">
      <ProductCard
        v-for="product in products"
        :key="product.id"
        :product="product"
        @edit="openEdit"
        @changed="onChanged"
        @removed="onRemoved"
      />
    </div>
  </main>

  <ProductFormModal v-if="formOpen" :product="editing" @close="formOpen = false" @saved="onSaved" />
</template>

<style scoped>
.toolbar {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 1.5rem;
}
.toolbar h1 {
  margin: 0 0 0.25rem;
}
.toolbar .subtitle {
  margin: 0;
}
.btn.add {
  width: auto;
  margin: 0;
  padding-inline: 1.25rem;
}
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 1rem;
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
}
.spinner {
  width: 1.2rem;
  height: 1.2rem;
  border: 2px solid var(--border);
  border-top-color: var(--accent);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
#products-loading {
  flex-direction: row;
  justify-content: center;
}
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
