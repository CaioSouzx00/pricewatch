import { ref, onMounted, onBeforeUnmount, type Ref } from 'vue'

export function useMagnetic(targetRef: Ref<HTMLElement | null>, strength = 0.25) {
  let isHovered = false

  function onMouseMove(e: MouseEvent) {
    if (!targetRef.value) return
    const rect = targetRef.value.getBoundingClientRect()
    const centerX = rect.left + rect.width / 2
    const centerY = rect.top + rect.height / 2
    const deltaX = (e.clientX - centerX) * strength
    const deltaY = (e.clientY - centerY) * strength

    targetRef.value.style.transform = `translate3d(${deltaX}px, ${deltaY}px, 0)`
  }

  function onMouseEnter() {
    isHovered = true
    if (targetRef.value) {
      targetRef.value.style.transition = 'transform 0.15s cubic-bezier(0.16, 1, 0.3, 1)'
    }
  }

  function onMouseLeave() {
    isHovered = false
    if (targetRef.value) {
      targetRef.value.style.transition = 'transform 0.4s cubic-bezier(0.16, 1, 0.3, 1)'
      targetRef.value.style.transform = 'translate3d(0, 0, 0)'
    }
  }

  onMounted(() => {
    // Only desktop fine pointer devices
    if (typeof window !== 'undefined' && window.matchMedia('(pointer: fine)').matches) {
      const el = targetRef.value
      if (el) {
        el.addEventListener('mouseenter', onMouseEnter)
        el.addEventListener('mousemove', onMouseMove)
        el.addEventListener('mouseleave', onMouseLeave)
      }
    }
  })

  onBeforeUnmount(() => {
    const el = targetRef.value
    if (el) {
      el.removeEventListener('mouseenter', onMouseEnter)
      el.removeEventListener('mousemove', onMouseMove)
      el.removeEventListener('mouseleave', onMouseLeave)
    }
  })
}
