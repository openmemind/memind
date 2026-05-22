import { fetchJson } from "@/lib/api/client"
import type { PageResult } from "@/lib/api/pagination"

export type MemoryItemsPageParams = {
  agentId?: string
  page?: number
  pageSize?: number
  q?: string
  scope?: string
  category?: string
  type?: string
  rawDataId?: string
  sort?: string
  userId?: string
}

export type MemoryItemsPage = {
  summary: Array<{
    label: string
    value: string
    detail?: string
  }>
  records: unknown[]
  paginationLabel: string
}

export type AdminItemView = {
  agentId?: string
  category?: string
  content?: string
  contentHash?: string
  createdAt?: string
  itemId: number
  memoryId?: string
  metadata?: Record<string, unknown>
  observedAt?: string
  occurredAt?: string
  rawDataId?: string
  rawDataType?: string
  scope?: string
  sourceClient?: string
  type?: string
  updatedAt?: string
  userId?: string
  vectorId?: string
}

export function fetchMemoryItemsPage(
  memoryId: string,
  params: MemoryItemsPageParams = {}
) {
  return fetchJson<PageResult<AdminItemView>>(
    `/admin/v1/memories/${encodeURIComponent(memoryId)}/items`,
    { query: params }
  ).then((page) => ({
    paginationLabel: `Showing ${page.items.length} of ${page.page.totalItems} items`,
    records: page.items,
    summary: [],
  }))
}

export function fetchAdminItemsPage(params: MemoryItemsPageParams = {}) {
  return fetchJson<PageResult<AdminItemView>>("/admin/v1/items", {
    query: params,
  })
}

export function fetchAdminItemDetail(itemId: number) {
  return fetchJson<AdminItemView>(`/admin/v1/items/${itemId}`)
}

export function fetchMemoryItemsSummary(memoryId: string) {
  return fetchJson<MemoryItemsPage["summary"]>(
    `/admin/v1/memories/${encodeURIComponent(memoryId)}/items/summary`
  )
}
