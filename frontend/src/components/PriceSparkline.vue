<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'

const props = withDefaults(defineProps<{ values: number[]; width?: number; height?: number }>(), {
  width: 108,
  height: 32,
})

const PAD = 4
const isDrawn = ref(false)

/** Desenho em degraus (stepped line) */
const geometry = computed(() => {
  const v = props.values
  if (v.length < 2) return null
  const min = Math.min(...v)
  const max = Math.max(...v)
  const span = max - min || 1
  const x = (i: number) => PAD + (i * (props.width - PAD * 2)) / (v.length - 1)
  const y = (val: number) => props.height - PAD - ((val - min) / span) * (props.height - PAD * 2)

  let d = `M${x(0)},${y(v[0])}`
  for (let i = 1; i < v.length; i++) {
    d += ` H${x(i)} V${y(v[i])}`
  }
  return { d, ex: x(v.length - 1), ey: y(v[v.length - 1]), min, max }
})

// Preço caiu = bom (verde/accent); subiu = perigo (vermelho/danger)
const tone = computed(() => {
  const v = props.values
  if (v.length < 2 || v[v.length - 1] === v[0]) return 'flat'
  return v[v.length - 1] < v[0] ? 'down' : 'up'
})

onMounted(() => {
  setTimeout(() => {
    isDrawn.value = true
  }, 40)
})
</script>

<template>
  <div class="sparkline-container" :style="{ width: `${width}px`, height: `${height}px` }">
    <svg
      v-if="geometry"
      :class="['spark', tone, { 'is-animated': isDrawn }]"
      :width="width"
      :height="height"
      :viewBox="`0 0 ${width} ${height}`"
      aria-hidden="true"
    >
      <path
        :d="geometry.d"
        fill="none"
        stroke="currentColor"
        stroke-width="1.75"
        stroke-linejoin="round"
        stroke-linecap="square"
        class="spark-path"
      />
      <circle :cx="geometry.ex" :cy="geometry.ey" r="2.5" fill="currentColor" class="spark-end-dot" />
    </svg>
    <div v-else class="spark-empty" />
  </div>
</template>

<style scoped>
.sparkline-container {
  display: inline-flex;
  align-items: center;
  position: relative;
  flex-shrink: 0;
}

.spark {
  display: block;
  overflow: visible;
}

.spark.down { color: var(--accent); }
.spark.up { color: var(--danger); }
.spark.flat { color: var(--text-muted); }

.spark-path {
  stroke-dasharray: 400;
  stroke-dashoffset: 400;
  transition: stroke-dashoffset 0.85s var(--ease-out-expo);
}

.spark.is-animated .spark-path {
  stroke-dashoffset: 0;
}

.spark-end-dot {
  opacity: 0;
  transform: scale(0);
  transform-origin: center;
  transition: opacity 0.3s ease 0.6s, transform 0.3s var(--ease-out-expo) 0.6s;
}

.spark.is-animated .spark-end-dot {
  opacity: 1;
  transform: scale(1);
}

.spark-empty {
  width: 100%;
  height: 1px;
  background: repeating-linear-gradient(90deg, var(--border-strong) 0 3px, transparent 3px 6px);
}
</style>
