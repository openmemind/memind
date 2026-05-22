import { describe, expect, it } from "vitest"

import { mapMemoriesPage } from "./memories-data"
import type { MemoriesPage } from "./memories-api"

describe("mapMemoriesPage", () => {
  it("builds pagination text from backend page metadata", () => {
    const data = mapMemoriesPage({
      items: [
        {
          agentId: "agent-a",
          alerts: { critical: 0, warning: 1 },
          createdAt: "2026-05-21T08:00:00Z",
          id: "mem-26",
          insights: 7,
          items: 42,
          lastActivity: "2026-05-21T09:00:00Z",
          name: "Memory 26",
          requests: 99,
          status: "active",
          userId: "user-a",
        },
      ],
      page: {
        hasNext: true,
        hasPrevious: true,
        page: 2,
        pageSize: 25,
        totalItems: 75,
        totalPages: 3,
      },
    } satisfies MemoriesPage)

    expect(data.pagination.summary).toBe("Showing 26 to 26 of 75 results")
    expect(data.pagination.currentPage).toBe(2)
    expect(data.pagination.totalPages).toBe(3)
    expect(data.pagination.hasPrevious).toBe(true)
    expect(data.pagination.hasNext).toBe(true)
  })

  it("maps raw data counts for the memory list", () => {
    const data = mapMemoriesPage({
      items: [
        {
          agentId: "agent-a",
          alerts: { critical: 0, warning: 0 },
          createdAt: "2026-05-21T08:00:00Z",
          id: "mem-raw-data",
          insights: 7,
          items: 42,
          lastActivity: "2026-05-21T09:00:00Z",
          name: "Memory raw data",
          rawData: 4218,
          requests: 99,
          status: "active",
          userId: "user-a",
        },
      ],
      page: {
        hasNext: false,
        hasPrevious: false,
        page: 1,
        pageSize: 25,
        totalItems: 1,
        totalPages: 1,
      },
    } as unknown as MemoriesPage)

    expect(data.workspaces[0]).toMatchObject({
      rawData: "4,218",
    })
  })

  it("shows an empty pagination summary when the backend has no items", () => {
    const data = mapMemoriesPage({
      items: [],
      page: {
        hasNext: false,
        hasPrevious: false,
        page: 1,
        pageSize: 25,
        totalItems: 0,
        totalPages: 0,
      },
    } satisfies MemoriesPage)

    expect(data.pagination.summary).toBe("Showing 0 of 0 results")
  })
})
