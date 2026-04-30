import { apiDelete, apiGet, type PageResult } from '@/lib/api-client'
import type {
  AdminInsightView,
  BatchDeleteResult,
  InsightDeleteRequest,
} from '@/features/types'
import type { PageParams, UserAgentFilter } from './common'

export type InsightListParams = PageParams &
  UserAgentFilter & {
    scope?: string
    type?: string
    tier?: string
  }

export function listInsights(params: InsightListParams = {}) {
  return apiGet<PageResult<AdminInsightView>>('/admin/v1/insights', params)
}

export function getInsight(insightId: number) {
  return apiGet<AdminInsightView>(`/admin/v1/insights/${insightId}`)
}

export function deleteInsights(insightIds: number[]) {
  const body: InsightDeleteRequest = { insightIds }
  return apiDelete<BatchDeleteResult>('/admin/v1/insights', body)
}
