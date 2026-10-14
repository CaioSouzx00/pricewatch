<script setup lang="ts">
import { computed } from 'vue'
import type { Position } from '../utils/priceSeries'
import { formatMoney } from '../utils/stats'

const props = defineProps<{ position: Position; currency: string; labels?: boolean }>()

const pct = (v: number) => {
  const { min, max } = props.position
  return max === min ? 50 : Math.max(0, Math.min(100, ((v - min) / (max - min)) * 100))
}

const avgLeft = computed(() => pct(props.position.avg))
const curLeft = computed(() => pct(props.position.current))
const money = (v: number) => formatMoney(v, props.currency)
</script>

<template>
  <div
    class="range-bar-root"
    role="img"
    :aria-label="`Atual ${money(position.current)}; Mínima ${money(position.min)}, Média ${money(position.avg)}, Máxima ${money(position.max)}`"
  >
    <div class="range-track">
      <!-- Fill to current -->
      <div
        class="range-fill"
        :class="position.tone"
        :style="{ width: `${curLeft}%` }"
      />
      <!-- Average Marker -->
      <div
        class="avg-marker"
        :style="{ left: `${avgLeft}%` }"
        :title="`Média histórica: ${money(position.avg)}`"
      >
        <span class="avg-line" />
      </div>
      <!-- Current Price Dot -->
      <div
        class="cur-marker"
        :class="position.tone"
        :style="{ left: `${curLeft}%` }"
        :title="`Preço atual: ${money(position.current)}`"
      />
    </div>

    <div v-if="labels" class="range-labels">
      <span class="label-min">
        <span class="label-tag">MÍN</span>
        <span class="num">{{ money(position.min) }}</span>
      </span>
      <span class="label-avg">
        <span class="label-tag">MÉD</span>
        <span class="num">{{ money(position.avg) }}</span>
      </span>
      <span class="label-max">
        <span class="label-tag">MÁX</span>
        <span class="num">{{ money(position.max) }}</span>
      </span>
    </div>
  </div>
</template>

<style scoped>
.range-bar-root {
  width: 100%;
  min-width: 0;
}

.range-track {
  position: relative;
  height: 4px;
  background: var(--surface-3);
  border-radius: 2px;
  margin: 10px 0;
}

.range-fill {
  position: absolute;
  top: 0;
  left: 0;
  bottom: 0;
  border-radius: 2px;
  background: var(--text-muted);
  transition: width 0.6s var(--ease-out-expo);
}

.range-fill.good {
  background: var(--accent);
}

.range-fill.high {
  background: var(--danger);
}

.avg-marker {
  position: absolute;
  top: -4px;
  bottom: -4px;
  width: 2px;
  transform: translateX(-50%);
  pointer-events: none;
  z-index: 2;
}

.avg-line {
  display: block;
  width: 2px;
  height: 12px;
  background: var(--text-primary);
  border-radius: 1px;
  box-shadow: 0 0 2px rgba(0, 0, 0, 0.5);
}

.cur-marker {
  position: absolute;
  top: 50%;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  transform: translate(-50%, -50%);
  background: var(--surface-1);
  border: 2px solid var(--text-primary);
  box-shadow: 0 0 0 2px var(--bg);
  transition: left 0.6s var(--ease-out-expo);
  z-index: 3;
}

.cur-marker.good {
  border-color: var(--accent);
  background: var(--accent);
}

.cur-marker.high {
  border-color: var(--danger);
  background: var(--danger);
}

.range-labels {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-family: var(--font-mono);
  font-size: 0.6875rem;
  color: var(--text-muted);
  margin-top: 0.25rem;
}

.label-min, .label-avg, .label-max {
  display: flex;
  align-items: center;
  gap: 0.3rem;
}

.label-tag {
  font-size: 0.5625rem;
  padding: 0.05rem 0.25rem;
  background: var(--surface-2);
  border: 1px solid var(--border-subtle);
  border-radius: 2px;
  color: var(--text-muted);
}
</style>
