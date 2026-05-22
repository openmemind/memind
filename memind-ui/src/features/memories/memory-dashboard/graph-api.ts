import { fetchJson } from "@/lib/api/client"
import type { PageResult } from "@/lib/api/pagination"

export type MemoryGraphExplorer = {
  nodes: unknown[]
  edges: unknown[]
  summary: AdminGraphSummaryView
}

export type AdminGraphNamedCount = {
  count: number
  name: string
}

export type AdminGraphSummaryView = {
  aliasCount: number
  cooccurrenceCount: number
  entityCount: number
  entityCountByType: AdminGraphNamedCount[]
  graphBatchCountByState: AdminGraphNamedCount[]
  itemLinkCount: number
  itemLinkCountByType: AdminGraphNamedCount[]
  mentionCount: number
}

export type AdminGraphEntityView = {
  agentId?: string
  createdAt?: string
  displayName?: string
  entityKey?: string
  entityType?: string
  id: number
  memoryId?: string
  metadata?: Record<string, unknown>
  updatedAt?: string
  userId?: string
}

export type AdminGraphBatchView = {
  agentId?: string
  createdAt?: string
  errorMessage?: string
  extractionBatchId?: string
  id: number
  memoryId?: string
  retryPromotionSupported?: boolean
  state?: string
  updatedAt?: string
  userId?: string
}

export function fetchMemoryGraphExplorer(memoryId: string) {
  return fetchJson<MemoryGraphExplorer>("/admin/v1/item-graph/explorer", {
    query: { memoryId },
  })
}

export function fetchAdminGraphSummary(memoryId?: string) {
  return fetchJson<AdminGraphSummaryView>("/admin/v1/item-graph/summary", {
    query: { memoryId },
  })
}

export function fetchAdminGraphEntitiesPage(params: {
  entityType?: string
  memoryId?: string
  page?: number
  pageSize?: number
  q?: string
} = {}) {
  return fetchJson<PageResult<AdminGraphEntityView>>(
    "/admin/v1/item-graph/entities",
    { query: params }
  )
}

export function fetchAdminGraphBatchesPage(params: {
  memoryId?: string
  page?: number
  pageSize?: number
  state?: string
} = {}) {
  return fetchJson<PageResult<AdminGraphBatchView>>(
    "/admin/v1/item-graph/batches",
    { query: params }
  )
}
