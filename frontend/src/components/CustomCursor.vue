<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'

const visible = ref(false)
const isHovering = ref(false)
const posX = ref(-100)
const posY = ref(-100)
const ringX = ref(-100)
const ringY = ref(-100)

let rafId: number | null = null
const isFinePointer = ref(false)

function onMouseMove(e: MouseEvent) {
  visible.value = true
  posX.value = e.clientX
  posY.value = e.clientY

  const target = e.target as HTMLElement | null
  if (target) {
    const isInteractive = target.closest('a, button, input, select, textarea, [role="button"], .interactive, .cursor-hover')
    isHovering.value = !!isInteractive
  }
}

function onMouseLeave() {
  visible.value = false
}

function updateRing() {
  // Smooth lerp for outer ring
  ringX.value += (posX.value - ringX.value) * 0.18
  ringY.value += (posY.value - ringY.value) * 0.18
  rafId = requestAnimationFrame(updateRing)
}

onMounted(() => {
  if (typeof window !== 'undefined') {
    isFinePointer.value = window.matchMedia('(pointer: fine)').matches && !window.matchMedia('(prefers-reduced-motion: reduce)').matches
    if (isFinePointer.value) {
      window.addEventListener('mousemove', onMouseMove, { passive: true })
      document.addEventListener('mouseleave', onMouseLeave)
      rafId = requestAnimationFrame(updateRing)
    }
  }
})

onBeforeUnmount(() => {
  if (typeof window !== 'undefined') {
    window.removeEventListener('mousemove', onMouseMove)
    document.removeEventListener('mouseleave', onMouseLeave)
    if (rafId) cancelAnimationFrame(rafId)
  }
})
</script>

<template>
  <div v-if="isFinePointer && visible" class="custom-cursor-root" aria-hidden="true">
    <div
      class="cursor-dot"
      :style="{ transform: `translate3d(${posX}px, ${posY}px, 0)` }"
    />
    <div
      class="cursor-ring"
      :class="{ 'is-hovering': isHovering }"
      :style="{ transform: `translate3d(${ringX}px, ${ringY}px, 0)` }"
    />
  </div>
</template>

<style scoped>
.custom-cursor-root {
  position: fixed;
  inset: 0;
  pointer-events: none;
  z-index: 10000;
  contain: layout style size;
}

.cursor-dot {
  position: absolute;
  top: 0;
  left: 0;
  width: 4px;
  height: 4px;
  margin-top: -2px;
  margin-left: -2px;
  background-color: var(--accent);
  border-radius: 50%;
  pointer-events: none;
  transition: opacity 0.2s ease, transform 0.04s linear;
}

.cursor-ring {
  position: absolute;
  top: 0;
  left: 0;
  width: 28px;
  height: 28px;
  margin-top: -14px;
  margin-left: -14px;
  border: 1px solid var(--accent);
  border-radius: 50%;
  opacity: 0.45;
  pointer-events: none;
  transition: width 0.25s var(--ease-out-expo), height 0.25s var(--ease-out-expo),
              margin 0.25s var(--ease-out-expo), opacity 0.25s ease,
              background-color 0.25s ease, border-color 0.25s ease;
}

.cursor-ring.is-hovering {
  width: 44px;
  height: 44px;
  margin-top: -22px;
  margin-left: -22px;
  opacity: 0.85;
  background-color: var(--accent-subtle);
  border-color: var(--accent);
}
</style>
