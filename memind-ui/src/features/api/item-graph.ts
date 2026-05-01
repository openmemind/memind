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
