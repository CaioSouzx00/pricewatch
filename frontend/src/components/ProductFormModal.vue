<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ApiError } from '../api/client'
import { createProduct, updateProduct, type Product } from '../api/products'

const props = defineProps<{ product: Product | null }>()
const emit = defineEmits<{ close: []; saved: [product: Product] }>()

const name = ref('')
const url = ref('')
const price = ref('')
const currency = ref('BRL')
const error = ref('')
const saving = ref(false)

const isEdit = computed(() => props.product !== null)

watch(
  () => props.product,
  (p) => {
    name.value = p?.name ?? ''
    url.value = p?.url ?? ''
    price.value = p?.currentPrice ?? ''
    currency.value = p?.currency ?? 'BRL'
    error.value = ''
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
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Erro inesperado'
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="overlay" @mousedown.self="emit('close')" @keydown.esc="emit('close')">
    <section class="auth-card modal" role="dialog" aria-modal="true" aria-labelledby="product-form-title">
      <h2 id="product-form-title">{{ isEdit ? 'Editar produto' : 'Novo produto' }}</h2>

      <form @submit.prevent="submit" novalidate>
        <p v-if="error" id="product-form-error" class="error" role="alert">{{ error }}</p>

        <div class="field">
          <label for="product-name">Nome</label>
          <input id="product-name" v-model="name" type="text" maxlength="255" required autofocus />
        </div>
        <div class="field">
          <label for="product-url">URL do produto</label>
          <input id="product-url" v-model="url" type="url" placeholder="https://produto.mercadolivre.com.br/..." required />
        </div>
        <div class="row">
          <div class="field grow">
            <label for="product-price">Preço atual (opcional)</label>
            <input id="product-price" v-model="price" type="text" inputmode="decimal" placeholder="0,00" />
          </div>
          <div class="field currency">
            <label for="product-currency">Moeda</label>
            <input id="product-currency" v-model="currency" type="text" maxlength="3" />
          </div>
        </div>

        <div class="actions">
          <button id="product-cancel" class="btn ghost" type="button" @click="emit('close')">Cancelar</button>
          <button id="product-save" class="btn save" type="submit" :disabled="!canSubmit">
            {{ saving ? 'Salvando...' : 'Salvar' }}
          </button>
        </div>
      </form>
    </section>
  </div>
</template>

<style scoped>
.overlay {
  position: fixed;
  inset: 0;
  z-index: 10;
  display: grid;
  place-items: center;
  padding: 1.5rem;
  background: hsla(232, 50%, 3%, 0.7);
  backdrop-filter: blur(4px);
}
.modal {
  max-width: 480px;
}
.modal h2 {
  margin: 0 0 1.25rem;
}
.row {
  display: flex;
  gap: 0.75rem;
}
.grow {
  flex: 1;
}
.currency {
  width: 90px;
}
.actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.75rem;
  margin-top: 0.5rem;
}
.btn.save {
  width: auto;
  margin: 0;
  padding-inline: 1.5rem;
}
</style>
