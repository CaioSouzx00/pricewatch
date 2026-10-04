<script setup lang="ts">
import {
  CategoryScale,
  Chart,
  Filler,
  LineController,
  LineElement,
  LinearScale,
  PointElement,
  Tooltip,
} from 'chart.js'
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { PricePoint } from '../api/products'
import { formatMoney } from '../utils/stats'

Chart.register(LineController, LineElement, PointElement, LinearScale, CategoryScale, Filler, Tooltip)

const props = defineProps<{ points: PricePoint[]; currency: string }>()

const canvas = ref<HTMLCanvasElement | null>(null)
let chart: Chart | null = null

const dateFormat = new Intl.DateTimeFormat('pt-BR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' })

function series() {
  const sorted = [...props.points].sort((a, b) => a.checkedAt.localeCompare(b.checkedAt))
  return {
    labels: sorted.map((p) => dateFormat.format(new Date(p.checkedAt))),
    values: sorted.map((p) => Number(p.price)),
  }
}

function render() {
  if (!canvas.value) return
  const { labels, values } = series()
  chart?.destroy()
  const ctx = canvas.value.getContext('2d')!
  const gradient = ctx.createLinearGradient(0, 0, 0, 300)
  gradient.addColorStop(0, 'rgba(139, 92, 246, 0.45)')
  gradient.addColorStop(1, 'rgba(139, 92, 246, 0)')

  chart = new Chart(canvas.value, {
    type: 'line',
    data: {
      labels,
      datasets: [
        {
          data: values,
          borderColor: '#8b5cf6',
          backgroundColor: gradient,
          fill: true,
          tension: 0.3,
          pointRadius: values.length > 40 ? 0 : 3,
          pointHoverRadius: 5,
        },
      ],
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      interaction: { mode: 'index', intersect: false },
      plugins: {
        legend: { display: false },
        tooltip: { callbacks: { label: (item) => formatMoney(item.parsed.y ?? 0, props.currency) } },
      },
      scales: {
        x: { ticks: { color: '#8f96ad', maxTicksLimit: 6 }, grid: { display: false } },
        y: {
          ticks: { color: '#8f96ad', callback: (v) => formatMoney(Number(v), props.currency) },
          grid: { color: 'rgba(160, 170, 220, 0.1)' },
        },
      },
    },
  })
}

onMounted(render)
watch(() => [props.points, props.currency], render)
onBeforeUnmount(() => chart?.destroy())
</script>

<template>
  <div class="chart-wrap">
    <canvas ref="canvas" role="img" aria-label="Gráfico de evolução do preço"></canvas>
  </div>
</template>

<style scoped>
.chart-wrap {
  position: relative;
  height: 320px;
}
</style>
