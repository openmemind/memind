import { fetchJson } from "@/lib/api/client"
import type { PageResult } from "@/lib/api/pagination"
import type { AdminItemView } from "./items-api"

export type MemoryRawDataPageParams = {
  agentId?: string
  page?: number
  pageSize?: number
  q?: string
  type?: string
  source?: string
  startTimeFrom?: string
  startTimeTo?: string
  sort?: string
  userId?: string
}

export type MemoryRawDataPage = {
  summary: Array<{
    label: string
    value: string
    detail?: string
    trend?: string
  }>
  records: unknown[]
  paginationLabel: string
  pageLabel: string
}

export type AdminRawDataView = {
  agentId?: string
  caption?: string
  captionVectorId?: string
  contentId?: string
  createdAt?: string
  endTime?: string
  memoryId?: string
  metadata?: Record<string, unknown>
  rawDataId: string
  segment?: Record<string, unknown>
  sourceClient?: string
  startTime?: string
  type?: string
  updatedAt?: string
  userId?: string
}

export function fetchMemoryRawDataPage(
  memoryId: string,
  params: MemoryRawDataPageParams = {}
) {
  return fetchJson<PageResult<AdminRawDataView>>(
    `/admin/v1/memories/${encodeURIComponent(memoryId)}/raw-data`,
    { query: params }
  ).then((page) => ({
    pageLabel: `Page ${page.page.page} of ${page.page.totalPages}`,
    paginationLabel: `Showing ${page.items.length} of ${page.page.totalItems} records`,
    records: page.items,
    summary: [],
  }))
}

export function fetchAdminRawDataPage(params: MemoryRawDataPageParams = {}) {
  return fetchJson<PageResult<AdminRawDataView>>("/admin/v1/raw-data", {
    query: params,
  })
}

export function fetchAdminRawDataDetail(rawDataId: string) {
  return fetchJson<AdminRawDataView>(`/admin/v1/raw-data/${rawDataId}`)
}

export function fetchRawDataAssociatedItems(rawDataId: string) {
  return fetchJson<AdminItemView[]>(
    `/admin/v1/raw-data/${encodeURIComponent(rawDataId)}/items`
  )
}

export function reprocessRawData(rawDataId: string) {
  return fetchJson<{ rawDataId: string; status: string }>(
    `/admin/v1/raw-data/${encodeURIComponent(rawDataId)}/reprocess`,
    { method: "POST" }
  )
}
