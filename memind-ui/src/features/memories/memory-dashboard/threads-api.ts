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
