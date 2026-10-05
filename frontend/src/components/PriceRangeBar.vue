<script setup lang="ts">
import { computed } from 'vue'
import type { Position } from '../utils/priceSeries'
import { formatMoney } from '../utils/stats'

const props = defineProps<{ position: Position; currency: string; labels?: boolean }>()

const pct = (v: number) => {
  const { min, max } = props.position
  return max === min ? 50 : ((v - min) / (max - min)) * 100
}
const avgLeft = computed(() => pct(props.position.avg))
const curLeft = computed(() => pct(props.position.current))
const money = (v: number) => formatMoney(v, props.currency)
</script>

<template>
  <div
    class="range"
    role="img"
    :aria-label="`Current ${money(position.current)}; historical low ${money(position.min)}, average ${money(position.avg)}, high ${money(position.max)}`"
  >
    <div class="track">
      <div class="fill" :style="{ width: curLeft + '%' }" />
      <span class="avg" :style="{ left: avgLeft + '%' }" :title="`Average ${money(position.avg)}`" />
      <span :class="['dot', position.tone]" :style="{ left: curLeft + '%' }" :title="`Current ${money(position.current)}`" />
    </div>
    <div v-if="labels" class="ends">
      <span>{{ money(position.min) }}<small>low</small></span>
      <span>{{ money(position.max) }}<small>high</small></span>
    </div>
  </div>
</template>

<style scoped>
.range { width: 100%; min-width: 0; }
.track {
  position: relative;
  height: 2px;
  margin: 7px 0;
  background: var(--p-surface-700);
  border-radius: 1px;
}
.fill {
  position: absolute; inset: 0 auto 0 0;
  background: var(--p-surface-500);
  border-radius: 1px;
}
.avg {
  position: absolute; top: -4px;
  width: 1px; height: 10px;
  background: var(--p-surface-300);
  transform: translateX(-50%);
}
.dot {
  position: absolute; top: 50%;
  width: 10px; height: 10px;
  border-radius: 50%;
  transform: translate(-50%, -50%);
  box-shadow: 0 0 0 3px var(--range-bg, var(--p-surface-900));
  background: var(--p-surface-300);
  transition: left 0.3s ease;
}
.dot.good { background: var(--p-primary-500); }
.dot.high { background: #f87171; }
.ends {
  display: flex; justify-content: space-between;
  font-size: 0.75rem;
  color: var(--p-surface-300);
  font-variant-numeric: tabular-nums;
}
.ends small { margin-left: 0.35rem; color: var(--p-surface-500); }
</style>
