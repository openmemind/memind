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
