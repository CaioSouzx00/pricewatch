<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api } from '../api/client'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Tag from 'primevue/tag'
import Button from 'primevue/button'

const alerts = ref<any[]>([])
const loading = ref(true)

async function load() {
    loading.value = true
    try {
        alerts.value = await api<any[]>('/alerts')
    } finally {
        loading.value = false
    }
}

async function remove(id: number) {
    await api(`/alerts/${id}`, { method: 'DELETE' })
    alerts.value = alerts.value.filter(a => a.id !== id)
}

function formatType(type: string) {
    const map: Record<string, string> = {
        PRICE_BELOW: 'Target Price',
        PRICE_UP: 'Price Increase',
        STOCK_CHANGE: 'Stock Change',
        PERCENTAGE_DROP: 'Percentage Drop',
        HISTORICAL_MIN: 'Historical Min'
    }
    return map[type] || type
}

function getSeverity(active: boolean) {
    return active ? 'success' : 'secondary'
}

onMounted(load)
</script>

<template>
    <div>
        <h1 class="text-2xl font-bold mb-6">Alert History</h1>
        
        <DataTable :value="alerts" :loading="loading" class="p-datatable-sm">
            <template #empty>No alerts found.</template>
            <Column field="productName" header="Product">
                <template #body="{ data }">
                    <RouterLink :to="`/products/${data.productId}`" class="text-green hover:underline">
                        Product #{{ data.productId }}
                    </RouterLink>
                </template>
            </Column>
            <Column field="type" header="Rule">
                <template #body="{ data }">
                    {{ formatType(data.type) }}
                </template>
            </Column>
            <Column field="targetPrice" header="Value">
                <template #body="{ data }">
                    <span v-if="data.type === 'PERCENTAGE_DROP'">{{ data.percentageDrop }}%</span>
                    <span v-else-if="data.targetPrice">R$ {{ data.targetPrice }}</span>
                    <span v-else>-</span>
                </template>
            </Column>
            <Column field="active" header="Status">
                <template #body="{ data }">
                    <Tag :severity="getSeverity(data.active)" :value="data.active ? 'Active' : 'Triggered'"></Tag>
                </template>
            </Column>
            <Column header="Actions">
                <template #body="{ data }">
                    <Button icon="pi pi-trash" severity="danger" text rounded aria-label="Delete" @click="remove(data.id)" />
                </template>
            </Column>
        </DataTable>
    </div>
</template>
