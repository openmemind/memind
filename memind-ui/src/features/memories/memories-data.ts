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

import { useQuery } from "@tanstack/react-query"

import { fetchMemoriesPage, type MemoriesPage } from "./memories-api"

export type MemoryStatus = "active" | "idle" | "warning" | "error"

export type MemorySummary = {
  label: string
  value: string
  detail: string
  tone: "default" | "success" | "warning" | "danger"
}

export type MemoryWorkspace = {
  id: string
  name: string
  userId: string
  agentId: string
  status: MemoryStatus
  requests: string
  items: string
  insights: string
  rawData: string
  alerts: {
    critical: number
    warning: number
  }
  lastActivity: string
  createdAt: string
}

export type MemoriesData = {
  pagination: {
    currentPage: number
    hasNext: boolean
    hasPrevious: boolean
    summary: string
    totalPages: number
  }
  summaries: MemorySummary[]
  workspaces: MemoryWorkspace[]
}

function formatCount(value: number) {
  return new Intl.NumberFormat("en-US").format(value)
}

function formatPaginationSummary(
  page: MemoriesPage["page"],
  itemCount: number
) {
  if (page.totalItems === 0 || itemCount === 0) {
    return `Showing 0 of ${formatCount(page.totalItems)} results`
  }

  const firstItem = (page.page - 1) * page.pageSize + 1
  const lastItem = firstItem + itemCount - 1

  return `Showing ${formatCount(firstItem)} to ${formatCount(
    lastItem
  )} of ${formatCount(page.totalItems)} results`
}

export function mapMemoriesPage(page: MemoriesPage): MemoriesData {
  return {
    pagination: {
      currentPage: page.page.page,
      hasNext: page.page.hasNext,
      hasPrevious: page.page.hasPrevious,
      summary: formatPaginationSummary(page.page, page.items.length),
      totalPages: page.page.totalPages,
    },
    summaries: [
      {
        detail: "Across all collections",
        label: "Total Memories",
        tone: "default",
        value: formatCount(page.page.totalItems),
      },
      {
        detail: "Loaded from current page",
        label: "Active Memories",
        tone: "success",
        value: formatCount(page.items.length),
      },
      {
        detail: "Critical + warning",
        label: "With Alerts",
        tone: "danger",
        value: formatCount(
          page.items.reduce(
            (total, item) => total + item.alerts.critical + item.alerts.warning,
            0
          )
        ),
      },
      {
        detail: `Page ${page.page.page} of ${page.page.totalPages}`,
        label: "New Memories",
        tone: "warning",
        value: formatCount(page.items.length),
      },
    ],
    workspaces: page.items.map((item) => ({
      agentId: item.agentId,
      alerts: item.alerts,
      createdAt: item.createdAt,
      id: item.id,
      insights: formatCount(item.insights),
      items: formatCount(item.items),
      lastActivity: item.lastActivity,
      name: item.name,
      rawData: formatCount(item.rawData ?? item.requests ?? 0),
      requests: formatCount(item.requests),
      status: item.status as MemoryStatus,
      userId: item.userId,
    })),
  }
}

async function fetchMemoriesData() {
  const page = await fetchMemoriesPage({ page: 1, pageSize: 25 })

  return mapMemoriesPage(page)
}

export function useMemoriesData() {
  return useQuery({
    queryKey: ["memories", "list"],
    queryFn: fetchMemoriesData,
  })
}
