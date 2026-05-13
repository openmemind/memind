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

import { apiDelete, apiGet, type PageResult } from '@/lib/api-client'
import type {
  AdminGraphEntityDeleteResult,
  BatchDeleteResult,
  GraphAliasView,
  GraphBatchView,
  GraphCooccurrenceView,
  GraphEntityDeleteRequest,
  GraphEntityDetailView,
  GraphEntityView,
  GraphIdsRequest,
  GraphItemLinkView,
  GraphMentionView,
  ItemGraphSummaryView,
} from '@/features/types'
import type { MemoryIdFilter, PageParams } from './common'

export type GraphBatchState = 'PENDING' | 'COMMITTED' | 'REPAIR_REQUIRED'

type GraphEntityListParams = PageParams &
  MemoryIdFilter & {
    entityType?: string
    q?: string
  }

type GraphAliasListParams = PageParams &
  MemoryIdFilter & {
    entityKey?: string
    q?: string
  }

type GraphMentionListParams = PageParams &
  MemoryIdFilter & {
    itemId?: number
    entityKey?: string
  }

type GraphItemLinkListParams = PageParams &
  MemoryIdFilter & {
    itemId?: number
    linkType?: string
    evidenceSource?: string
  }

type GraphCooccurrenceListParams = PageParams &
  MemoryIdFilter & {
    entityKey?: string
  }

type GraphBatchListParams = PageParams &
  MemoryIdFilter & {
    state?: GraphBatchState
  }

export function getItemGraphSummary(params: MemoryIdFilter = {}) {
  return apiGet<ItemGraphSummaryView>('/admin/v1/item-graph/summary', params)
}

export function listGraphEntities(params: GraphEntityListParams = {}) {
  return apiGet<PageResult<GraphEntityView>>(
    '/admin/v1/item-graph/entities',
    params
  )
}

export function getGraphEntity(id: number) {
  return apiGet<GraphEntityDetailView>(`/admin/v1/item-graph/entities/${id}`)
}

export function deleteGraphEntities(request: GraphEntityDeleteRequest) {
  return apiDelete<AdminGraphEntityDeleteResult>(
    '/admin/v1/item-graph/entities',
    request
  )
}

export function listGraphAliases(params: GraphAliasListParams = {}) {
  return apiGet<PageResult<GraphAliasView>>(
    '/admin/v1/item-graph/aliases',
    params
  )
}

export function deleteGraphAliases(ids: number[]) {
  const body: GraphIdsRequest = { ids }
  return apiDelete<BatchDeleteResult>('/admin/v1/item-graph/aliases', body)
}

export function listGraphMentions(params: GraphMentionListParams = {}) {
  return apiGet<PageResult<GraphMentionView>>(
    '/admin/v1/item-graph/mentions',
    params
  )
}

export function deleteGraphMentions(ids: number[]) {
  const body: GraphIdsRequest = { ids }
  return apiDelete<BatchDeleteResult>('/admin/v1/item-graph/mentions', body)
}

export function listGraphItemLinks(params: GraphItemLinkListParams = {}) {
  return apiGet<PageResult<GraphItemLinkView>>(
    '/admin/v1/item-graph/item-links',
    params
  )
}

export function deleteGraphItemLinks(ids: number[]) {
  const body: GraphIdsRequest = { ids }
  return apiDelete<BatchDeleteResult>('/admin/v1/item-graph/item-links', body)
}

export function listGraphCooccurrences(
  params: GraphCooccurrenceListParams = {}
) {
  return apiGet<PageResult<GraphCooccurrenceView>>(
    '/admin/v1/item-graph/cooccurrences',
    params
  )
}

export function deleteGraphCooccurrences(ids: number[]) {
  const body: GraphIdsRequest = { ids }
  return apiDelete<BatchDeleteResult>(
    '/admin/v1/item-graph/cooccurrences',
    body
  )
}

export function listGraphBatches(params: GraphBatchListParams = {}) {
  return apiGet<PageResult<GraphBatchView>>(
    '/admin/v1/item-graph/batches',
    params
  )
}
