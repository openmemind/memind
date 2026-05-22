import { fetchJson } from "@/lib/api/client"
import type { PageResult } from "@/lib/api/pagination"

export type DashboardAlertsSummary = {
  critical: number
  warning: number
  items: Array<{
    memoryId: string
    time: string
  }>
}

export type DashboardRecentMemory = {
  memoryId: string
  userId: string
  agentId: string
  createdAt: string
  requests: number
  alert: "critical" | "warning" | "healthy" | "info"
  updatedAt: string
}

type DashboardMemorySummary = {
  id: string
  name: string
  userId: string
  agentId: string
  status: string
  requests: number
  items: number
  insights: number
  alerts: {
    critical: number
    warning: number
  }
  lastActivity: string
  createdAt: string
}

export function fetchDashboardAlertsSummary() {
  return fetchJson<DashboardAlertsSummary>("/admin/v1/alerts/summary")
}

export function fetchDashboardRecentMemories(limit = 5) {
  return fetchJson<DashboardRecentMemory[]>(
    "/admin/v1/dashboard/recent-memories",
    {
      query: { limit },
    }
  )
}

export function fetchDashboardMemoriesSummary() {
  return fetchJson<PageResult<DashboardMemorySummary>>("/admin/v1/memories", {
    query: { page: 1, pageSize: 1 },
  })
}
