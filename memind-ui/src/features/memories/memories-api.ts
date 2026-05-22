import { fetchJson } from "@/lib/api/client"

export type MemoriesPageParams = {
  page?: number
  pageSize?: number
  q?: string
  status?: string
  alertLevel?: string
  createdRange?: string
  sort?: string
}

export type MemoriesPage = {
  items: Array<{
    id: string
    name: string
    userId: string
    agentId: string
    status: string
    requests: number
    items: number
    insights: number
    rawData?: number
    alerts: {
      critical: number
      warning: number
    }
    lastActivity: string
    createdAt: string
  }>
  page: {
    page: number
    pageSize: number
    totalItems: number
    totalPages: number
    hasPrevious: boolean
    hasNext: boolean
  }
}

export function fetchMemoriesPage(params: MemoriesPageParams = {}) {
  return fetchJson<MemoriesPage>("/admin/v1/memories", { query: params })
}
