<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{ values: number[]; width?: number; height?: number }>(), {
  width: 96,
  height: 28,
})

const PAD = 3

/** Mesmo desenho "em degraus" do gráfico principal: o preço só muda quando é coletado. */
const geometry = computed(() => {
  const v = props.values
  if (v.length < 2) return null
  const min = Math.min(...v)
  const span = Math.max(...v) - min || 1
  const x = (i: number) => PAD + (i * (props.width - PAD * 2)) / (v.length - 1)
  const y = (val: number) => props.height - PAD - ((val - min) / span) * (props.height - PAD * 2)
  let d = `M${x(0)},${y(v[0])}`
  for (let i = 1; i < v.length; i++) d += `H${x(i)}V${y(v[i])}`
  return { d, ex: x(v.length - 1), ey: y(v[v.length - 1]) }
})

// Preço caiu = bom (verde); subiu = ruim.
const tone = computed(() => {
  const v = props.values
  if (v.length < 2 || v[v.length - 1] === v[0]) return 'flat'
  return v[v.length - 1] < v[0] ? 'down' : 'up'
})
</script>

<template>
  <svg
    v-if="geometry"
    :class="['spark', tone]"
    :width="width"
    :height="height"
    :viewBox="`0 0 ${width} ${height}`"
    aria-hidden="true"
  >
    <path :d="geometry.d" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round" />
    <circle :cx="geometry.ex" :cy="geometry.ey" r="2.5" fill="currentColor" />
  </svg>
  <span v-else class="spark-empty" :style="{ width: width + 'px' }" />
</template>

<style scoped>
.spark { display: block; overflow: visible; }
.spark.down { color: var(--p-primary-500); }
.spark.up { color: #f87171; }
.spark.flat { color: var(--p-surface-500); }
.spark-empty {
  display: block;
  height: 1px;
  background: repeating-linear-gradient(90deg, var(--p-surface-700) 0 3px, transparent 3px 7px);
}
</style>
