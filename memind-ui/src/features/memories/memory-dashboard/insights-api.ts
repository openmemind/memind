import { fetchJson } from "@/lib/api/client"
import type { PageResult } from "@/lib/api/pagination"

export type MemoryInsightTree = {
  roots: unknown[]
}

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

export function fetchMemoryInsightTree(memoryId: string) {
  const [userId, agentId] = memoryId.split(":", 2)

  return fetchJson<MemoryInsightTree>("/admin/v1/insights/tree", {
    query: { agentId, userId },
  })
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
