import { apiDelete, apiGet, apiPatch, type PageResult } from '@/lib/api-client'
import type {
  AdminIdsRequest,
  AdminUpdateResult,
  BatchDeleteResult,
  ConversationBufferView,
  InsightBufferBuiltUpdateRequest,
  InsightBufferGroupUpdateRequest,
  InsightBufferGroupView,
  InsightBufferView,
} from '@/features/types'
import type { MemoryIdFilter, PageParams } from './common'

export type ConversationBufferState = 'pending' | 'extracted' | 'all'
export type InsightBufferState =
  | 'unbuilt'
  | 'ungrouped'
  | 'grouped'
  | 'built'
  | 'all'

export type ConversationBufferListParams = PageParams &
  MemoryIdFilter & {
    sessionId?: string
    state?: ConversationBufferState
  }

export type InsightBufferListParams = PageParams &
  MemoryIdFilter & {
    insightTypeName?: string
    state?: InsightBufferState
  }

type InsightBufferGroupParams = MemoryIdFilter & {
  insightTypeName?: string
}

export function listConversationBuffers(
  params: ConversationBufferListParams = {}
) {
  return apiGet<PageResult<ConversationBufferView>>(
    '/admin/v1/buffers/conversations',
    params
  )
}

export function getConversationBuffer(id: number) {
  return apiGet<ConversationBufferView>(
    `/admin/v1/buffers/conversations/${id}`
  )
}

export function markConversationsExtracted(ids: number[]) {
  return apiPatch<AdminUpdateResult>(
    '/admin/v1/buffers/conversations/extracted',
    { ids }
  )
}

export function deleteConversationBuffers(ids: number[]) {
  return apiDelete<BatchDeleteResult>('/admin/v1/buffers/conversations', {
    ids,
  })
}

export function listInsightBuffers(params: InsightBufferListParams = {}) {
  return apiGet<PageResult<InsightBufferView>>(
    '/admin/v1/buffers/insights',
    params
  )
}

export function listInsightBufferGroups(params: InsightBufferGroupParams = {}) {
  return apiGet<InsightBufferGroupView[]>(
    '/admin/v1/buffers/insights/groups',
    params
  )
}

export function updateInsightBufferGroup(
  request: InsightBufferGroupUpdateRequest
) {
  return apiPatch<AdminUpdateResult>(
    '/admin/v1/buffers/insights/group',
    request
  )
}

export function updateInsightBufferBuilt(
  request: InsightBufferBuiltUpdateRequest
) {
  return apiPatch<AdminUpdateResult>(
    '/admin/v1/buffers/insights/built',
    request
  )
}

export function deleteInsightBuffers(ids: number[]) {
  const body: AdminIdsRequest = { ids }
  return apiDelete<BatchDeleteResult>('/admin/v1/buffers/insights', body)
}
