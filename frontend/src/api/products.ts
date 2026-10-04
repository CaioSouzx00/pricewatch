import { api } from './client'

export interface Product {
  id: number
  userId: number
  name: string
  url: string
  store: string | null
  imageUrl: string | null
  currency: string
  /** Decimal como string, ex.: "1299.90" */
  currentPrice: string | null
  active: boolean
  isFavorite: boolean
  createdAt: string
  updatedAt: string
  inStock: boolean | null
}

export interface ProductInput {
  name: string
  url: string
  currency: string
  currentPrice?: string | null
}

export const listProducts = () => api<Product[]>('/products')

export const getProduct = (id: number) => api<Product>(`/products/${id}`)

export interface PricePoint {
  id: number
  productId: number
  /** Decimal como string */
  price: string
  checkedAt: string
}

/** Histórico retornado do mais recente para o mais antigo (até 100 pontos). */
export const getPriceHistory = (id: number) => api<PricePoint[]>(`/products/${id}/history`)

export const createProduct = (input: ProductInput) =>
  api<Product>('/products', { body: { ...input, currentPrice: input.currentPrice || undefined } })

export const updateProduct = (id: number, input: Partial<ProductInput> & { active?: boolean }) =>
  api<Product>(`/products/${id}`, { method: 'PUT', body: { ...input, currentPrice: input.currentPrice || undefined } })

export const deleteProduct = (id: number) => api<void>(`/products/${id}`, { method: 'DELETE' })

/** Dispara o scraping do produto e retorna o produto atualizado. */
export const refreshProduct = (id: number) => api<Product>(`/products/${id}/refresh`, { method: 'POST', body: undefined })
