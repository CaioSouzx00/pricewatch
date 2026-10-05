<script setup lang="ts">
import { Chart, Filler, LineController, LineElement, LinearScale, PointElement, Tooltip } from 'chart.js'
import { computed, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import type { PricePoint } from '../api/products'
import { RANGES, sliceRange, toSeries, formatDelta } from '../utils/priceSeries'
import { formatMoney } from '../utils/stats'

Chart.register(LineController, LineElement, PointElement, LinearScale, Filler, Tooltip)

const props = defineProps<{ points: PricePoint[]; currency: string }>()

const canvas = ref<HTMLCanvasElement | null>(null)
const chart = shallowRef<Chart | null>(null)
const rangeKey = ref('all')

const series = computed(() => toSeries(props.points))
const visible = computed(() => sliceRange(series.value, RANGES.find((r) => r.key === rangeKey.value)?.days ?? null))
// A média de referência é sempre a do histórico completo, igual à do backend.
const average = computed(() => series.value.reduce((s, p) => s + p.v, 0) / (series.value.length || 1))
const available = computed(() => RANGES.map((r) => ({ ...r, enabled: r.days === null || sliceRange(series.value, r.days).length >= 2 })))

const dayFmt = new Intl.DateTimeFormat('pt-BR', { day: '2-digit', month: 'short' })
const fullFmt = new Intl.DateTimeFormat('pt-BR', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' })

const COLOR = '#10b981'
const MUTED = '#a1a1aa'
const GRID = 'rgba(244, 244, 245, 0.06)'

function render() {
  if (!canvas.value) return
  chart.value?.destroy()
  const data = visible.value
  const values = data.map((p) => p.v)
  const lo = Math.min(...values)
  const hi = Math.max(...values)
  const pad = (hi - lo || hi * 0.02 || 1) * 0.15
  const avg = average.value
  const last = data.length - 1

  chart.value = new Chart(canvas.value, {
    type: 'line',
    data: {
      datasets: [
        {
          label: 'Price',
          data: data.map((p) => ({ x: p.t, y: p.v })),
          borderColor: COLOR,
          borderWidth: 2,
          stepped: 'after', // o preço permanece constante até a próxima coleta
          fill: { target: 'origin', above: 'rgba(16, 185, 129, 0.07)' },
          // Só marcamos o que importa: menor preço do período e o preço atual.
          pointRadius: (c) => (c.dataIndex === last || data[c.dataIndex].v === lo ? 4 : 0),
          pointHoverRadius: 5,
          pointBackgroundColor: (c) => (data[c.dataIndex]?.v === lo ? '#09090b' : COLOR),
          pointBorderColor: COLOR,
          pointBorderWidth: 2,
          order: 1,
        },
        {
          label: 'Average',
          data: data.length ? [{ x: data[0].t, y: avg }, { x: data[last].t, y: avg }] : [],
          borderColor: MUTED,
          borderWidth: 1,
          borderDash: [4, 4],
          pointRadius: 0,
          pointHoverRadius: 0,
          fill: false,
          order: 2,
        },
      ],
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      animation: { duration: 250 },
      interaction: { mode: 'nearest', axis: 'x', intersect: false },
      layout: { padding: { right: 8, top: 8 } },
      plugins: {
        legend: { display: false },
        tooltip: {
          filter: (item) => item.datasetIndex === 0,
          backgroundColor: '#18181b',
          borderColor: '#3f3f46',
          borderWidth: 1,
          titleColor: MUTED,
          bodyColor: '#fafafa',
          padding: 10,
          displayColors: false,
          bodyFont: { weight: 600 },
          callbacks: {
            title: (items) => fullFmt.format(new Date(items[0].parsed.x ?? 0)),
            label: (item) => formatMoney(item.parsed.y ?? 0, props.currency),
            afterLabel: (item) => `${formatDelta(avg === 0 ? 0 : (((item.parsed.y ?? 0) - avg) / avg) * 100)} vs average`,
          },
        },
      },
      scales: {
        x: {
          type: 'linear',
          min: data[0]?.t,
          max: data[last]?.t,
          grid: { display: false },
          border: { color: GRID },
          ticks: {
            color: MUTED,
            maxTicksLimit: 5,
            maxRotation: 0,
            callback: (v) => dayFmt.format(new Date(Number(v))),
          },
        },
        y: {
          min: lo - pad,
          max: hi + pad,
          grid: { color: GRID },
          border: { display: false },
          ticks: {
            color: MUTED,
            maxTicksLimit: 5,
            callback: (v) => formatMoney(Number(v), props.currency).replace(/,00$/, ''),
          },
        },
      },
    },
  })
}

onMounted(render)
watch([visible, () => props.currency], render)
onBeforeUnmount(() => chart.value?.destroy())
</script>

<template>
  <div>
    <div class="chart-head">
      <div class="legend">
        <span><i class="swatch line" />Price</span>
        <span><i class="swatch dash" />Average {{ formatMoney(average, currency) }}</span>
      </div>
      <div class="ranges" role="group" aria-label="Time range">
        <button
          v-for="r in available"
          :key="r.key"
          type="button"
          :class="{ active: rangeKey === r.key }"
          :disabled="!r.enabled"
          :aria-pressed="rangeKey === r.key"
          @click="rangeKey = r.key"
        >
          {{ r.label }}
        </button>
      </div>
    </div>
    <div class="chart-wrap">
      <canvas ref="canvas" role="img" aria-label="Price history chart"></canvas>
    </div>
  </div>
</template>

<style scoped>
.chart-head {
  display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between;
  gap: 0.75rem; margin-bottom: 1rem;
}
.legend { display: flex; gap: 1.25rem; font-size: 0.8125rem; color: var(--p-surface-400); font-variant-numeric: tabular-nums; }
.legend span { display: inline-flex; align-items: center; gap: 0.5rem; }
.swatch { display: inline-block; width: 14px; height: 0; }
.swatch.line { border-top: 2px solid #10b981; }
.swatch.dash { border-top: 1px dashed #a1a1aa; }
.ranges { display: inline-flex; gap: 2px; padding: 2px; border: 1px solid var(--p-surface-800); border-radius: 6px; }
.ranges button {
  font: inherit; font-size: 0.75rem; font-weight: 500; letter-spacing: 0.02em;
  padding: 0.25rem 0.6rem; border: 0; border-radius: 4px; cursor: pointer;
  background: transparent; color: var(--p-surface-400);
  transition: background 0.15s, color 0.15s;
}
.ranges button:hover:not(:disabled):not(.active) { color: var(--p-surface-0); }
.ranges button.active { background: var(--p-surface-800); color: var(--p-surface-0); }
.ranges button:disabled { opacity: 0.35; cursor: default; }
.ranges button:focus-visible { outline: 2px solid var(--p-primary-500); outline-offset: 1px; }
.chart-wrap { position: relative; height: 320px; }
@media (max-width: 640px) {
  .chart-wrap { height: 240px; }
  .legend { order: 2; width: 100%; }
}
</style>
