import type { PricePoint } from '../api/products'

export interface PriceStats {
  current: number
  min: number
  max: number
  average: number
  /** Variação entre as duas últimas coletas (null se houver menos de 2). */
  lastChange: { amount: number; percent: number } | null
  /** Variação desde a primeira coleta. */
  totalChange: { amount: number; percent: number } | null
  count: number
}

const change = (from: number, to: number) => ({
  amount: to - from,
  percent: from === 0 ? 0 : ((to - from) / from) * 100,
})

/** [points] em qualquer ordem; retorna null se vazio. */
export function computeStats(points: PricePoint[]): PriceStats | null {
  if (points.length === 0) return null
  const sorted = [...points].sort((a, b) => a.checkedAt.localeCompare(b.checkedAt))
  const values = sorted.map((p) => Number(p.price))
  const current = values[values.length - 1]
  return {
    current,
    min: Math.min(...values),
    max: Math.max(...values),
    average: values.reduce((a, b) => a + b, 0) / values.length,
    lastChange: values.length > 1 ? change(values[values.length - 2], current) : null,
    totalChange: values.length > 1 ? change(values[0], current) : null,
    count: values.length,
  }
}

export function formatMoney(value: number, currency: string): string {
  try {
    return new Intl.NumberFormat('pt-BR', { style: 'currency', currency }).format(value)
  } catch {
    return `${currency} ${value.toFixed(2)}`
  }
}
