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
      currency: currency.value.trim().toUpperCase() || 'BRL',
      currentPrice: price.value.trim().replace(',', '.') || null,
    }
    const saved = props.product ? await updateProduct(props.product.id, input) : await createProduct(input)
    emit('saved', saved)
    visible.value = false
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Falha ao salvar produto'
  } finally {
    saving.value = false
  }
}

function onHide() {
  emit('close')
}
</script>

<template>
  <Dialog
    v-model:visible="visible"
    modal
    :header="isEdit ? 'Editar Ativo de Monitoramento' : 'Novo Ativo de Monitoramento'"
    :style="{ width: '480px' }"
    @hide="onHide"
  >
    <form @submit.prevent="submit" novalidate class="form-body">
      <Message v-if="error" severity="error" :closable="false" class="form-error">
        {{ error }}
      </Message>

      <div class="field-group">
        <label for="product-name" class="field-label">NOME DO ATIVO / PRODUTO</label>
        <InputText
          id="product-name"
          v-model="name"
          type="text"
          maxlength="255"
          required
          autofocus
          placeholder="Ex: Apple MacBook Air M3 15 polegadas"
          class="field-input"
        />
      </div>

      <div class="field-group">
        <label for="product-url" class="field-label">URL DE MONITORAMENTO (MERCADO LIVRE, AMAZON, ETC.)</label>
        <InputText
          id="product-url"
          v-model="url"
          type="url"
          placeholder="https://..."
          required
          class="field-input"
        />
        <span class="field-hint">O coletor irá verificar este link periodicamente para atualizar o gráfico.</span>
      </div>

      <div class="field-row">
        <div class="field-group flex-2">
          <label for="product-price" class="field-label">PREÇO INICIAL (OPCIONAL)</label>
          <InputText
            id="product-price"
            v-model="price"
            type="text"
            inputmode="decimal"
            placeholder="0,00"
            class="field-input"
          />
        </div>

        <div class="field-group flex-1">
          <label for="product-currency" class="field-label">MOEDA</label>
          <InputText
            id="product-currency"
            v-model="currency"
            type="text"
            maxlength="3"
            class="field-input text-center"
          />
        </div>
      </div>

      <div class="modal-footer">
        <Button
          label="Cancelar"
          severity="secondary"
          type="button"
          @click="visible = false"
        />
        <Button
          type="submit"
          :label="saving ? 'Gravando...' : (isEdit ? 'Salvar Alterações' : 'Iniciar Monitoramento')"
          :loading="saving"
          :disabled="!canSubmit"
          class="p-button-primary"
        />
      </div>
    </form>
  </Dialog>
</template>

<style scoped>
.form-body {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.form-error {
  margin: 0;
}

.field-group {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.field-label {
  font-family: var(--font-mono);
  font-size: 0.6875rem;
  letter-spacing: 0.08em;
  color: var(--text-muted);
}

.field-input {
  width: 100%;
}

.field-hint {
  font-size: 0.75rem;
  color: var(--text-muted);
  line-height: 1.3;
}

.field-row {
  display: flex;
  gap: 1rem;
}

.flex-1 { flex: 1; }
.flex-2 { flex: 2; }
.text-center { text-align: center; }

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 0.75rem;
  margin-top: 0.75rem;
  padding-top: 1rem;
  border-top: 1px solid var(--border-subtle);
}
</style>
