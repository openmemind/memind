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

export type AdminInsightPoint = {
  content?: string
  pointId?: string
  sourceItemIds?: string[]
  type?: string
}

export type AdminInsightView = {
  agentId?: string
  categories?: string[]
  childInsightIds?: number[]
  content?: string
  createdAt?: string
  groupName?: string
  insightId: number
  lastReasonedAt?: string
  memoryId?: string
  name?: string
  parentInsightId?: number
  points?: AdminInsightPoint[]
  scope?: string
  tier?: string
  type?: string
  updatedAt?: string
  userId?: string
  version?: number
}

export type MemoryInsightTree = {
  roots: AdminInsightView[]
}

export function fetchMemoryInsightTree(memoryId: string) {
  const [userId, agentId] = memoryId.split(":", 2)

  return fetchJson<MemoryInsightTree>("/admin/v1/insights/tree", {
    query: { agentId, userId },
  })
}

export function fetchInsightsList(insightIds: Array<number | string>) {
  if (!insightIds.length) {
    return Promise.resolve([])
  }

  const query = new URLSearchParams()
  insightIds.forEach((insightId) => {
    query.append("insightIds", String(insightId))
  })

  return fetchJson<AdminInsightView[]>(
    `/admin/v1/insights/list?${query.toString()}`
  )
}

export function fetchAdminInsightsPage(params: {
  agentId?: string
  page?: number
  pageSize?: number
  scope?: string
  tier?: string
  type?: string
  userId?: string
} = {}) {
  return fetchJson<PageResult<AdminInsightView>>("/admin/v1/insights", {
    query: params,
  })
}

export function regenerateMemoryInsight(insightId: string | number) {
  return fetchJson<{ insightId: number; status: string }>(
    `/admin/v1/insights/${encodeURIComponent(insightId)}/regenerate`,
    { method: "POST" }
  )
}
