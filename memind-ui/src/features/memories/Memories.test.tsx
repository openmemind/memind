import { render, screen, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { describe, expect, it, vi } from "vitest"

import { Memories } from "./Memories"
import { useMemoriesData } from "./memories-data"

vi.mock("./memories-data", async (importOriginal) => {
  const actual = await importOriginal<typeof import("./memories-data")>()

  return {
    ...actual,
    useMemoriesData: vi.fn(),
  }
})

const useMemoriesDataMock = vi.mocked(useMemoriesData)

describe("Memories", () => {
  it("renders each memory as a single-row summary with raw data counts", async () => {
    const onOpenMemory = vi.fn()
    const user = userEvent.setup()

    useMemoriesDataMock.mockReturnValue({
      data: {
        pagination: {
          currentPage: 1,
          hasNext: false,
          hasPrevious: false,
          summary: "Showing 1 to 1 of 1 results",
          totalPages: 1,
        },
        summaries: [],
        workspaces: [
          {
            agentId: "agent-a",
            alerts: { critical: 0, warning: 0 },
            createdAt: "2026-05-21T08:00:00Z",
            id: "mem-raw-data",
            insights: "7",
            items: "42",
            lastActivity: "2m ago",
            name: "Detailed memory name",
            rawData: "4,218",
            requests: "99",
            status: "active",
            userId: "user-a",
          },
        ],
      },
      isError: false,
      isLoading: false,
    } as ReturnType<typeof useMemoriesData>)

    render(<Memories onOpenMemory={onOpenMemory} />)

    const table = screen.getByRole("table")
    const rows = within(table).getAllByRole("row")
    expect(rows).toHaveLength(2)
    expect(within(rows[0]).getByText("ID")).toBeInTheDocument()
    expect(within(rows[0]).getByText("User / Agent")).toBeInTheDocument()
    expect(within(rows[0]).getByText("Status")).toBeInTheDocument()
    expect(within(rows[0]).getByText("Items")).toBeInTheDocument()
    expect(within(rows[0]).getByText("Insight")).toBeInTheDocument()
    expect(within(rows[0]).getByText("Raw Data")).toBeInTheDocument()
    expect(within(rows[0]).getByText("Activity")).toBeInTheDocument()
    expect(within(rows[0]).getByText("Action")).toBeInTheDocument()
    expect(within(rows[0]).queryByText("Stats")).not.toBeInTheDocument()
    expect(within(rows[0]).queryByText("Alerts")).not.toBeInTheDocument()

    expect(within(rows[1]).getByText("mem-raw-data")).toBeInTheDocument()
    expect(within(rows[1]).getByText("user-a")).toBeInTheDocument()
    expect(within(rows[1]).getByText("agent-a")).toBeInTheDocument()
    expect(within(rows[1]).getByText("Active")).toBeInTheDocument()
    expect(within(rows[1]).getByText("42")).toBeInTheDocument()
    expect(within(rows[1]).getByText("7")).toBeInTheDocument()
    expect(within(rows[1]).getByText("4,218")).toBeInTheDocument()
    expect(within(rows[1]).getByText("2m ago")).toBeInTheDocument()
    expect(
      within(rows[1]).queryByText("Detailed memory name")
    ).not.toBeInTheDocument()
    expect(
      within(rows[1]).queryByText("2026-05-21T08:00:00Z")
    ).not.toBeInTheDocument()

    await user.click(within(rows[1]).getByRole("button", { name: "Open" }))

    expect(onOpenMemory).toHaveBeenCalledWith("mem-raw-data")
  })
})
