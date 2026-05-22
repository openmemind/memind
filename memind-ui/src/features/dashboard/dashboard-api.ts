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
