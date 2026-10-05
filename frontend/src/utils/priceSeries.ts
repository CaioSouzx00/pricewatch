import type { PricePoint } from '../api/products'

export interface SeriesPoint {
  t: number
  v: number
}

export interface RangeOption {
  key: string
  label: string
  /** null = todo o histórico */
  days: number | null
}

export const RANGES: RangeOption[] = [
  { key: '7d', label: '7D', days: 7 },
  { key: '30d', label: '30D', days: 30 },
  { key: '90d', label: '90D', days: 90 },
  { key: 'all', label: 'All', days: null },
]

const DAY = 86_400_000

/** Converte o histórico da API em série ordenada cronologicamente. */
export function toSeries(points: PricePoint[]): SeriesPoint[] {
  return points
    .map((p) => ({ t: new Date(p.checkedAt).getTime(), v: Number(p.price) }))
    .filter((p) => Number.isFinite(p.t) && Number.isFinite(p.v))
    .sort((a, b) => a.t - b.t)
}

export function sliceRange(series: SeriesPoint[], days: number | null, now = Date.now()): SeriesPoint[] {
  if (days === null) return series
  const from = now - days * DAY
  return series.filter((p) => p.t >= from)
}

export interface Position {
  min: number
  max: number
  avg: number
  current: number
  /** Variação do preço atual contra a média histórica, em %. */
  vsAvg: number
  /** 0 (mínimo histórico) → 1 (máximo histórico). */
  ratio: number
  tone: 'good' | 'fair' | 'high'
}

/** Posição do preço atual dentro do intervalo histórico. Exige pelo menos 2 pontos. */
export function pricePosition(series: SeriesPoint[], current?: number): Position | null {
  if (series.length < 2) return null
  const values = series.map((p) => p.v)
  const price = current ?? values[values.length - 1]
  const min = Math.min(...values, price)
  const max = Math.max(...values, price)
  const avg = values.reduce((a, b) => a + b, 0) / values.length
  const vsAvg = avg === 0 ? 0 : ((price - avg) / avg) * 100
  return {
    min,
    max,
    avg,
    current: price,
    vsAvg,
    ratio: max === min ? 0.5 : (price - min) / (max - min),
    tone: vsAvg <= -2 ? 'good' : vsAvg > 5 ? 'high' : 'fair',
  }
}

/** "−4,2%" / "+1,0%" com sinal tipográfico. */
export function formatDelta(percent: number): string {
  const abs = Math.abs(percent).toFixed(1).replace('.', ',')
  if (Math.abs(percent) < 0.05) return '0,0%'
  return `${percent < 0 ? '−' : '+'}${abs}%`
}
