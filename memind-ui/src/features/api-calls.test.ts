import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"

import {
  fetchDashboardAlertsSummary,
  fetchDashboardMemoriesSummary,
  fetchDashboardRecentMemories,
} from "./dashboard/dashboard-api"
import { fetchMemoriesPage } from "./memories/memories-api"
import { fetchMemoryGraphExplorer } from "./memories/memory-dashboard/graph-api"
import { fetchMemoryInsightTree } from "./memories/memory-dashboard/insights-api"
import { fetchMemoryItemsPage } from "./memories/memory-dashboard/items-api"
import { forceMemorySnapshot } from "./memories/memory-dashboard/overview-api"
import { fetchMemoryRawDataPage } from "./memories/memory-dashboard/raw-data-api"
import { fetchMemoryThreadTimeline } from "./memories/memory-dashboard/threads-api"
import { fetchUiPreferences } from "./settings/settings-api"

function jsonResponse(data: unknown) {
  return new Response(JSON.stringify({ data }), { status: 200 })
}

describe("page API calls", () => {
  const originalFetch = globalThis.fetch

  beforeEach(() => {
    vi.stubGlobal(
      "fetch",
      vi.fn(() => jsonResponse({}))
    )
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    globalThis.fetch = originalFetch
  })

  it.each([
    {
      call: () => fetchDashboardAlertsSummary(),
      response: { critical: 0, items: [], warning: 0 },
      url: "/admin/v1/alerts/summary",
    },
    {
      call: () => fetchDashboardRecentMemories(5),
      response: [],
      url: "/admin/v1/dashboard/recent-memories?limit=5",
    },
    {
      call: () => fetchDashboardMemoriesSummary(),
      response: {
        items: [],
        page: {
          hasNext: false,
          hasPrevious: false,
          page: 1,
          pageSize: 1,
          totalItems: 0,
          totalPages: 0,
        },
      },
      url: "/admin/v1/memories?page=1&pageSize=1",
    },
    {
      call: () => fetchMemoriesPage(),
      response: {
        items: [],
        page: {
          hasNext: false,
          hasPrevious: false,
          page: 1,
          pageSize: 20,
          totalItems: 0,
          totalPages: 0,
        },
      },
      url: "/admin/v1/memories",
    },
    {
      call: () => forceMemorySnapshot("mem_1"),
      response: { memoryId: "mem_1", status: "accepted" },
      url: "/admin/v1/memories/mem_1/snapshot",
    },
    {
      call: () => fetchMemoryRawDataPage("mem_1"),
      response: {
        items: [],
        page: {
          hasNext: false,
          hasPrevious: false,
          page: 1,
          pageSize: 20,
          totalItems: 0,
          totalPages: 0,
        },
      },
      url: "/admin/v1/memories/mem_1/raw-data",
    },
    {
      call: () => fetchMemoryItemsPage("mem_1"),
      response: {
        items: [],
        page: {
          hasNext: false,
          hasPrevious: false,
          page: 1,
          pageSize: 20,
          totalItems: 0,
          totalPages: 0,
        },
      },
      url: "/admin/v1/memories/mem_1/items",
    },
    {
      call: () => fetchMemoryGraphExplorer("mem_1"),
      response: { edges: [], nodes: [], summary: {} },
      url: "/admin/v1/item-graph/explorer?memoryId=mem_1",
    },
    {
      call: () => fetchMemoryThreadTimeline("mem_1", "thr_1"),
      response: [],
      url: "/admin/v1/memory-threads/thr_1/timeline?userId=mem_1",
    },
    {
      call: () => fetchMemoryInsightTree("mem_1"),
      response: { roots: [] },
      url: "/admin/v1/insights/tree?userId=mem_1",
    },
    {
      call: () => fetchUiPreferences(),
      response: {
        autoHideEmptyCollections: true,
        defaultMemoryView: "table",
        defaultTimeRange: "7d",
        showOnboardingTips: true,
        theme: "system",
      },
      url: "/admin/v1/settings/ui-preferences",
    },
  ])("requests $url", async ({ call, response, url }) => {
    vi.mocked(fetch).mockResolvedValueOnce(jsonResponse(response))

    await call()

    expect(fetch).toHaveBeenCalledWith(
      url,
      expect.objectContaining({ headers: expect.any(Headers) })
    )
  })
})
