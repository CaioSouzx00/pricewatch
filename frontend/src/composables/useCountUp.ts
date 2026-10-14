import { ref, watch, onMounted } from 'vue'

export function useCountUp(
  targetValue: () => number | null | undefined,
  options: {
    duration?: number
    currency?: string
    decimals?: number
  } = {}
) {
  const { duration = 800, currency = 'BRL', decimals = 2 } = options
  const display = ref('—')
  let animationFrame: number | null = null

  function format(val: number): string {
    try {
      return new Intl.NumberFormat('pt-BR', {
        style: 'currency',
        currency,
        minimumFractionDigits: decimals,
        maximumFractionDigits: decimals,
      }).format(val)
    } catch {
      return `${currency} ${val.toFixed(decimals)}`
    }
  }

  function easeOutExpo(x: number): number {
    return x === 1 ? 1 : 1 - Math.pow(2, -10 * x)
  }

  function animate(to: number, from = 0) {
    if (animationFrame) cancelAnimationFrame(animationFrame)

    const startTime = performance.now()
    const diff = to - from

    function step(currentTime: number) {
      const elapsed = currentTime - startTime
      const progress = Math.min(elapsed / duration, 1)
      const eased = easeOutExpo(progress)
      const current = from + diff * eased

      display.value = format(current)

      if (progress < 1) {
        animationFrame = requestAnimationFrame(step)
      } else {
        display.value = format(to)
      }
    }

    animationFrame = requestAnimationFrame(step)
  }

  watch(
    () => targetValue(),
    (newVal, oldVal) => {
      if (newVal == null || isNaN(Number(newVal))) {
        display.value = '—'
        return
      }
      const num = Number(newVal)
      const prev = oldVal != null && !isNaN(Number(oldVal)) ? Number(oldVal) : num * 0.95
      animate(num, prev)
    },
    { immediate: true }
  )

  onMounted(() => {
    const val = targetValue()
    if (val != null && !isNaN(Number(val))) {
      animate(Number(val), 0)
    }
  })

  return {
    display,
  }
}
