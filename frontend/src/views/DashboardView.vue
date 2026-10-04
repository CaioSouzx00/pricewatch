<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api } from '../api/client'
import Card from 'primevue/card'

interface DashboardMetrics {
    totalProducts: number
    activeAlerts: number
    recentPriceDrops: number
    staleProducts: number
    totalValueTracked: string
}

const metrics = ref<DashboardMetrics | null>(null)
const loading = ref(true)

async function load() {
    try {
        metrics.value = await api<DashboardMetrics>('/dashboard/metrics')
    } catch (e) {
        console.error(e)
    } finally {
        loading.value = false
    }
}

onMounted(load)
</script>

<template>
    <div>
        <h1 class="text-2xl font-bold mb-6">Dashboard</h1>
        
        <div v-if="loading" class="text-muted">Loading metrics...</div>
        
        <div v-else-if="metrics" class="grid-layout">
            <Card class="metric-card">
                <template #content>
                    <div class="metric-title">Tracked Products</div>
                    <div class="metric-value">{{ metrics.totalProducts }}</div>
                </template>
            </Card>
            
            <Card class="metric-card">
                <template #content>
                    <div class="metric-title">Active Alerts</div>
                    <div class="metric-value">{{ metrics.activeAlerts }}</div>
                </template>
            </Card>
            
            <Card class="metric-card">
                <template #content>
                    <div class="metric-title">Recent Price Drops</div>
                    <div class="metric-value text-green">{{ metrics.recentPriceDrops }}</div>
                </template>
            </Card>

            <Card class="metric-card">
                <template #content>
                    <div class="metric-title">Total Value Tracked</div>
                    <div class="metric-value">R$ {{ metrics.totalValueTracked }}</div>
                </template>
            </Card>
        </div>
    </div>
</template>

<style scoped>
.grid-layout {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
    gap: 1.5rem;
}
.metric-card {
    border-radius: 12px;
}
.metric-title {
    color: var(--p-surface-400);
    font-size: 0.875rem;
    margin-bottom: 0.5rem;
}
.metric-value {
    font-size: 2rem;
    font-weight: 700;
    color: var(--p-surface-0);
}
</style>
