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

export type MemoryThreadTimelineItem = {
  id: string
  role: "PRIMARY" | "SUPPORTING"
  timestamp: string
  content: string
  source: string
}

export type AdminMemoryThreadView = {
  agentId?: string
  anchorKey?: string
  anchorKind?: string
  closedAt?: string | null
  createdAt?: string
  displayLabel?: string
  eventCount?: number
  headline?: string
  lastEventAt?: string
  lastMeaningfulUpdateAt?: string
  lifecycleStatus?: string
  memberCount?: number
  memoryId?: string
  objectState?: string
  openedAt?: string
  snapshotJson?: Record<string, unknown>
  snapshotVersion?: number
  threadKey: string
  threadType?: string
  updatedAt?: string
  userId?: string
}

export type AdminMemoryThreadStatusView = {
  failedCount: number
  invalidationReason?: string
  lastProcessedItemId?: number
  materializationPolicyVersion?: string
  pendingCount: number
  projectionState?: string
  rebuildInProgress: boolean
  updatedAt?: string
}

export function fetchMemoryThreadTimeline(
  memoryId: string,
  threadKey: string
) {
  const [userId, agentId] = memoryId.split(":", 2)

  return fetchJson<MemoryThreadTimelineItem[]>(
    `/admin/v1/memory-threads/${encodeURIComponent(threadKey)}/timeline`,
    { query: { agentId, userId } }
  )
}

export function fetchAdminMemoryThreadsPage(params: {
  agentId?: string
  page?: number
  pageSize?: number
  status?: string
  userId?: string
} = {}) {
  return fetchJson<PageResult<AdminMemoryThreadView>>(
    "/admin/v1/memory-threads",
    { query: params }
  )
}

export function fetchAdminMemoryThreadStatus(params: {
  agentId?: string
  userId: string
}) {
  return fetchJson<AdminMemoryThreadStatusView>(
    "/admin/v1/memory-threads/status",
    { query: params }
  )
}
