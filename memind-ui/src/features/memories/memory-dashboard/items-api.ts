//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

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
