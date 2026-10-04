<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ApiError } from '../api/client'
import { createProduct, updateProduct, type Product } from '../api/products'
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'
import Button from 'primevue/button'
import Message from 'primevue/message'

const props = defineProps<{ product: Product | null }>()
const emit = defineEmits<{ close: []; saved: [product: Product] }>()

const name = ref('')
const url = ref('')
const price = ref('')
const currency = ref('BRL')
const error = ref('')
const saving = ref(false)
const visible = ref(true)

const isEdit = computed(() => props.product !== null)

watch(
  () => props.product,
  (p) => {
    name.value = p?.name ?? ''
    url.value = p?.url ?? ''
    price.value = p?.currentPrice ?? ''
    currency.value = p?.currency ?? 'BRL'
    error.value = ''
    visible.value = true
  },
  { immediate: true },
)

const canSubmit = computed(() => !saving.value && name.value.trim() !== '' && url.value.trim() !== '')

async function submit() {
  if (!canSubmit.value) return
  error.value = ''
  saving.value = true
  try {
    const input = {
      name: name.value.trim(),
      url: url.value.trim(),
      currency: currency.value.trim().toUpperCase(),
      currentPrice: price.value.trim().replace(',', '.') || null,
    }
    const saved = props.product ? await updateProduct(props.product.id, input) : await createProduct(input)
    emit('saved', saved)
    visible.value = false
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Unexpected error'
  } finally {
    saving.value = false
  }
}

function onHide() {
    emit('close')
}
</script>

<template>
  <Dialog v-model:visible="visible" modal :header="isEdit ? 'Edit Product' : 'Add Product'" :style="{ width: '400px' }" @hide="onHide">
    <form @submit.prevent="submit" novalidate class="flex flex-col gap-4 mt-2">
      <Message v-if="error" severity="error" :closable="false" class="m-0">{{ error }}</Message>

      <div class="flex flex-col gap-2">
        <label for="product-name" class="text-sm font-semibold">Name</label>
        <InputText id="product-name" v-model="name" type="text" maxlength="255" required autofocus placeholder="e.g. iPhone 15 Pro" class="w-full" />
      </div>
      
      <div class="flex flex-col gap-2">
        <label for="product-url" class="text-sm font-semibold">Product URL</label>
        <InputText id="product-url" v-model="url" type="url" placeholder="https://..." required class="w-full" />
      </div>
      
      <div class="flex gap-4">
        <div class="flex flex-col gap-2 flex-grow">
          <label for="product-price" class="text-sm font-semibold">Current Price (Optional)</label>
          <InputText id="product-price" v-model="price" type="text" inputmode="decimal" placeholder="0.00" class="w-full" />
        </div>
        <div class="flex flex-col gap-2 w-24">
          <label for="product-currency" class="text-sm font-semibold">Currency</label>
          <InputText id="product-currency" v-model="currency" type="text" maxlength="3" class="w-full" />
        </div>
      </div>

      <div class="flex justify-end gap-2 mt-4">
        <Button label="Cancel" severity="secondary" text @click="visible = false" />
        <Button type="submit" :label="saving ? 'Saving...' : 'Save'" :loading="saving" :disabled="!canSubmit" />
      </div>
    </form>
  </Dialog>
</template>

<style scoped>
.flex { display: flex; }
.flex-col { flex-direction: column; }
.gap-2 { gap: 0.5rem; }
.gap-4 { gap: 1rem; }
.mt-2 { margin-top: 0.5rem; }
.mt-4 { margin-top: 1rem; }
.m-0 { margin: 0; }
.w-full { width: 100%; }
.w-24 { width: 6rem; }
.flex-grow { flex-grow: 1; }
.justify-end { justify-content: flex-end; }
.text-sm { font-size: 0.875rem; }
.font-semibold { font-weight: 600; }
</style>
