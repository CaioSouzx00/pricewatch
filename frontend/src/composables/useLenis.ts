import { onMounted, onBeforeUnmount } from 'vue'
import Lenis from 'lenis'

export function useLenis() {
  let lenis: Lenis | null = null
  let reqId: number | null = null

  onMounted(() => {
    if (typeof window === 'undefined') return
    const prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    if (prefersReducedMotion) return

    lenis = new Lenis({
      duration: 1.1,
      easing: (t) => Math.min(1, 1.001 - Math.pow(2, -10 * t)),
      orientation: 'vertical',
      gestureOrientation: 'vertical',
      smoothWheel: true,
      wheelMultiplier: 0.9,
    })

    function raf(time: number) {
      lenis?.raf(time)
      reqId = requestAnimationFrame(raf)
    }

    reqId = requestAnimationFrame(raf)
  })

  onBeforeUnmount(() => {
    if (reqId) cancelAnimationFrame(reqId)
    lenis?.destroy()
  })

  return {
    getLenis: () => lenis,
  }
}
