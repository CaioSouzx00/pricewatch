<script setup lang="ts">
import {
  Chart,
  Filler,
  LineController,
  LineElement,
  LinearScale,
  PointElement,
  Tooltip,
  type ScriptableContext
} from 'chart.js'
import { computed, onBeforeUnmount, onMounted, ref, shallowRef, watch, nextTick } from 'vue'
import type { PricePoint } from '../api/products'
import { RANGES, sliceRange, toSeries, formatDelta } from '../utils/priceSeries'
import { formatMoney } from '../utils/stats'

Chart.register(LineController, LineElement, PointElement, LinearScale, Filler, Tooltip)

const props = defineProps<{ points: PricePoint[]; currency: string }>()

const canvas = ref<HTMLCanvasElement | null>(null)
const chart = shallowRef<Chart | null>(null)
const rangeKey = ref('all')
const rangeNavRef = ref<HTMLElement | null>(null)
const pillStyle = ref<{ left: string; width: string }>({ left: '0px', width: '0px' })

const series = computed(() => toSeries(props.points))
const visible = computed(() => sliceRange(series.value, RANGES.find((r) => r.key === rangeKey.value)?.days ?? null))
const average = computed(() => series.value.reduce((s, p) => s + p.v, 0) / (series.value.length || 1))
const available = computed(() => RANGES.map((r) => ({ ...r, enabled: r.days === null || sliceRange(series.value, r.days).length >= 2 })))

const dayFmt = new Intl.DateTimeFormat('pt-BR', { day: '2-digit', month: 'short' })
const fullFmt = new Intl.DateTimeFormat('pt-BR', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' })

const COLOR_ACCENT = '#10B981'
const COLOR_MUTED = '#8A8A93'
const COLOR_GRID = 'rgba(255, 255, 255, 0.05)'

function updatePill() {
  nextTick(() => {
    if (!rangeNavRef.value) return
    const activeBtn = rangeNavRef.value.querySelector<HTMLButtonElement>('button.is-active')
    if (activeBtn) {
      pillStyle.value = {
        left: `${activeBtn.offsetLeft}px`,
        width: `${activeBtn.offsetWidth}px`
      }
    }
  })
}

function setRange(key: string) {
  rangeKey.value = key
  updatePill()
}

function render() {
  if (!canvas.value) return
  chart.value?.destroy()
  const data = visible.value
  if (data.length === 0) return

  const values = data.map((p) => p.v)
  const lo = Math.min(...values)
  const hi = Math.max(...values)
  const pad = (hi - lo || hi * 0.04 || 1) * 0.15
  const avg = average.value
  const last = data.length - 1

  chart.value = new Chart(canvas.value, {
    type: 'line',
    data: {
      datasets: [
        {
          label: 'Preço',
          data: data.map((p) => ({ x: p.t, y: p.v })),
          borderColor: COLOR_ACCENT,
          borderWidth: 2.25,
          stepped: 'after',
          fill: true,
          backgroundColor: (context: ScriptableContext<'line'>) => {
            const ctx = context.chart.ctx
            const gradient = ctx.createLinearGradient(0, 0, 0, context.chart.height || 300)
            gradient.addColorStop(0, 'rgba(16, 185, 129, 0.16)')
            gradient.addColorStop(0.7, 'rgba(16, 185, 129, 0.03)')
            gradient.addColorStop(1, 'rgba(16, 185, 129, 0)')
            return gradient
          },
          pointRadius: (c) => (c.dataIndex === last || data[c.dataIndex]?.v === lo ? 4.5 : 0),
          pointHoverRadius: 6,
          pointBackgroundColor: (c) => (data[c.dataIndex]?.v === lo ? '#09090B' : COLOR_ACCENT),
          pointBorderColor: COLOR_ACCENT,
          pointBorderWidth: 2,
          order: 1,
        },
        {
          label: 'Média Histórica',
          data: data.length ? [{ x: data[0].t, y: avg }, { x: data[last].t, y: avg }] : [],
          borderColor: COLOR_MUTED,
          borderWidth: 1.5,
          borderDash: [5, 5],
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
      animation: {
        duration: 850,
        easing: 'easeOutQuart',
      },
      interaction: { mode: 'nearest', axis: 'x', intersect: false },
      layout: { padding: { right: 12, top: 12, bottom: 8, left: 4 } },
      plugins: {
        legend: { display: false },
        tooltip: {
          filter: (item) => item.datasetIndex === 0,
          backgroundColor: '#121214',
          borderColor: '#3F3F46',
          borderWidth: 1,
          titleColor: COLOR_MUTED,
          titleFont: { family: 'JetBrains Mono', size: 11 },
          bodyColor: '#F4F4F5',
          bodyFont: { family: 'Space Grotesk', size: 14, weight: 'bold' },
          footerColor: COLOR_ACCENT,
          footerFont: { family: 'JetBrains Mono', size: 11 },
          padding: 12,
          cornerRadius: 6,
          displayColors: false,
          callbacks: {
            title: (items) => fullFmt.format(new Date(items[0].parsed.x ?? 0)),
            label: (item) => formatMoney(item.parsed.y ?? 0, props.currency),
            footer: (items) => {
              const val = items[0]?.parsed.y ?? 0
              const delta = avg === 0 ? 0 : ((val - avg) / avg) * 100
              return `${formatDelta(delta)} contra a média histórica (${formatMoney(avg, props.currency)})`
            },
          },
        },
      },
      scales: {
        x: {
          type: 'linear',
          min: data[0]?.t,
          max: data[last]?.t,
          grid: { display: false },
          border: { color: COLOR_GRID },
          ticks: {
            color: COLOR_MUTED,
            font: { family: 'JetBrains Mono', size: 10 },
            maxTicksLimit: 6,
            maxRotation: 0,
            callback: (v) => dayFmt.format(new Date(Number(v))),
          },
        },
        y: {
          min: lo - pad,
          max: hi + pad,
          grid: { color: COLOR_GRID },
          border: { display: false },
          ticks: {
            color: COLOR_MUTED,
            font: { family: 'JetBrains Mono', size: 10 },
            maxTicksLimit: 5,
            callback: (v) => formatMoney(Number(v), props.currency).replace(/,00$/, ''),
          },
        },
      },
    },
  })
}

onMounted(() => {
  render()
  updatePill()
  window.addEventListener('resize', updatePill)
})

watch([visible, () => props.currency], render)

onBeforeUnmount(() => {
  chart.value?.destroy()
  window.removeEventListener('resize', updatePill)
})
</script>

<template>
  <div class="editorial-chart-container">
    <div class="chart-header">
      <div class="chart-legend">
        <span class="legend-item">
          <span class="legend-swatch line" />
          <span class="tech-label">PREÇO COLETADO</span>
        </span>
        <span class="legend-item">
          <span class="legend-swatch dash" />
          <span class="tech-label">MÉDIA HISTÓRICA: <strong class="num">{{ formatMoney(average, currency) }}</strong></span>
        </span>
      </div>

      <!-- Sliding Pill Range Selector -->
      <div ref="rangeNavRef" class="range-selector" role="group" aria-label="Intervalo de tempo">
        <div class="range-pill" :style="pillStyle" />
        <button
          v-for="r in available"
          :key="r.key"
          type="button"
          class="range-btn cursor-hover"
          :class="{ 'is-active': rangeKey === r.key }"
          :disabled="!r.enabled"
          :aria-pressed="rangeKey === r.key"
          @click="setRange(r.key)"
        >
          {{ r.label }}
        </button>
      </div>
    </div>

    <div class="canvas-wrap">
      <canvas ref="canvas" role="img" aria-label="Gráfico de histórico de cotações"></canvas>
    </div>
  </div>
</template>

<style scoped>
.editorial-chart-container {
  width: 100%;
  display: flex;
  flex-direction: column;
}

.chart-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 1rem;
  margin-bottom: 1.25rem;
  padding-bottom: 0.75rem;
  border-bottom: 1px solid var(--border-subtle);
}

.chart-legend {
  display: flex;
  align-items: center;
  gap: 1.5rem;
  flex-wrap: wrap;
}

.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
}

.legend-swatch {
  width: 16px;
  height: 0;
}

.legend-swatch.line {
  border-top: 2.5px solid var(--accent);
}

.legend-swatch.dash {
  border-top: 1.5px dashed var(--text-muted);
}

.range-selector {
  display: inline-flex;
  position: relative;
  background: var(--surface-2);
  border: 1px solid var(--border-subtle);
  border-radius: 6px;
  padding: 3px;
}

.range-pill {
  position: absolute;
  top: 3px;
  bottom: 3px;
  background: var(--surface-1);
  border: 1px solid var(--border-strong);
  border-radius: 4px;
  transition: left 0.25s var(--ease-out-expo), width 0.25s var(--ease-out-expo);
  pointer-events: none;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.4);
}

.range-btn {
  position: relative;
  z-index: 1;
  background: transparent;
  border: none;
  font-family: var(--font-mono);
  font-size: 0.75rem;
  font-weight: 500;
  color: var(--text-muted);
  padding: 0.3rem 0.75rem;
  cursor: pointer;
  border-radius: 4px;
  transition: color 0.15s ease;
}

.range-btn:hover:not(:disabled):not(.is-active) {
  color: var(--text-primary);
}

.range-btn.is-active {
  color: var(--text-primary);
  font-weight: 700;
}

.range-btn:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}

.canvas-wrap {
  position: relative;
  height: 340px;
  width: 100%;
}

@media (max-width: 640px) {
  .canvas-wrap {
    height: 240px;
  }
  .chart-header {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
