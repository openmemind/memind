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

import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { beforeEach, vi } from "vitest"

import type { MemoryDashboardData } from "../../dashboard/memory-dashboard-data"
import type { RefreshAction } from "../../dashboard/refresh-action"
import { InsightsPage } from "./InsightsPage"
import { createInsightTreeLayout } from "./insight-layout"
import { fetchInsightsList } from "./insights-api"

vi.mock("./insights-api", () => ({
  fetchInsightsList: vi.fn(),
}))

const insightsData = {
  insights: {
    hierarchy: [
      { label: "Roots", count: 2 },
      { label: "Branches", count: 4 },
      { label: "Leaves", count: 6 },
    ],
    root: {
      id: "root-a",
      label: "Root Insight",
      title: "Platform Scalability Thesis",
      description: "Root A description.",
      category: "Strategic",
      kind: "root",
      children: "2 Branches",
    },
    branches: [
      {
        id: "branch-a-1",
        label: "Architecture",
        title: "Architecture Bottlenecks",
        description: "Architecture branch description.",
        category: "Architecture",
        kind: "branch",
        leaves: [
          {
            id: "leaf-a-1",
            label: "Observation",
            title: "V8 Engine JIT Delay",
            description: "A leaf description.",
            category: "Observation",
            kind: "leaf",
          },
        ],
      },
      {
        id: "branch-a-2",
        label: "Frontend",
        title: "Frontend Performance",
        description: "Frontend branch description.",
        category: "Frontend",
        kind: "branch",
        leaves: [
          {
            id: "leaf-a-2",
            label: "Metric",
            title: "DOM Depth Nesting",
            description: "Another leaf description.",
            category: "Metric",
            kind: "leaf",
          },
        ],
      },
    ],
    roots: [
      {
        id: "root-a",
        label: "Root Insight",
        title: "Platform Scalability Thesis",
        description: "Root A description.",
        category: "Strategic",
        kind: "root",
        children: "2 Branches",
        branches: [
          {
            id: "branch-a-1",
            label: "Architecture",
            title: "Architecture Bottlenecks",
            description: "Architecture branch description.",
            category: "Architecture",
            kind: "branch",
            leaves: [
              {
                id: "leaf-a-1",
                label: "Observation",
                title: "V8 Engine JIT Delay",
                description: "A leaf description.",
                category: "Observation",
                kind: "leaf",
              },
            ],
          },
        ],
      },
      {
        id: "root-b",
        label: "Root Insight",
        title: "Market Expansion Strategy",
        description: "Root B description.",
        category: "Growth",
        kind: "root",
        children: "1 Branch",
        branches: [
          {
            id: "branch-b-1",
            label: "Market",
            title: "Enterprise Segment Signals",
            description: "Market branch description.",
            category: "Market",
            kind: "branch",
            leaves: [
              {
                id: "leaf-b-1",
                label: "Signal",
                title: "Procurement Cycle Shift",
                description: "Market leaf description.",
                category: "Signal",
                kind: "leaf",
              },
            ],
          },
        ],
      },
    ],
    selectedDetail: {
      id: "branch-a-2",
      kind: "Branch",
      title: "Frontend Performance Analysis",
      description: "Selected branch detail.",
      points: ["Rendering latency spikes."],
      metadata: [
        { label: "GROUP", value: "Technical Debt" },
        { label: "TIER", value: "Strategic (B)" },
      ],
      categories: ["Optimization", "Frontend", "Critical"],
    },
  },
} as MemoryDashboardData

const noopRefreshAction: RefreshAction = {
  isRefreshing: false,
  onRefresh: () => {},
}

function expectLayerNodesToAvoidOverlap(
  nodes: ReturnType<typeof createInsightTreeLayout>["nodes"],
  kind: "branch" | "leaf",
  minGap: number
) {
  const layerNodes = nodes
    .filter((node) => node.kind === kind)
    .sort((left, right) => left.x - right.x)

  for (let index = 1; index < layerNodes.length; index += 1) {
    const previousNode = layerNodes[index - 1]
    const currentNode = layerNodes[index]
    const previousRight = previousNode.x + previousNode.width

    expect(currentNode.x - previousRight).toBeGreaterThanOrEqual(minGap)
  }
}

describe("InsightsPage", () => {
  beforeEach(() => {
    vi.mocked(fetchInsightsList).mockReset()
  })

  it("spreads dense insight tree nodes so same-layer nodes do not overlap", () => {
    const denseRoot = {
      branches: Array.from({ length: 4 }, (_, branchIndex) => ({
        category: "Dense",
        description: `Branch ${branchIndex} description.`,
        id: `branch-${branchIndex}`,
        kind: "branch" as const,
        label: "Branch",
        leaves: Array.from({ length: 4 }, (_, leafIndex) => ({
          category: "Dense",
          description: `Leaf ${branchIndex}-${leafIndex} description.`,
          id: `leaf-${branchIndex}-${leafIndex}`,
          kind: "leaf" as const,
          label: "Leaf",
          title: `Leaf ${branchIndex}-${leafIndex}`,
        })),
        title: `Branch ${branchIndex}`,
      })),
      category: "Dense",
      children: "4 Children",
      description: "Root description.",
      id: "root-dense",
      kind: "root" as const,
      label: "Root",
      title: "Dense Root",
    }

    const layout = createInsightTreeLayout(denseRoot, null)

    expectLayerNodesToAvoidOverlap(layout.nodes, "branch", 10)
    expectLayerNodesToAvoidOverlap(layout.nodes, "leaf", 10)
  })

  it("shows an empty state instead of a placeholder tree when there are no insights", () => {
    const emptyInsightsData = {
      insights: {
        branches: [],
        hierarchy: [
          { label: "Roots", count: 0, items: [] },
          { label: "Branches", count: 0 },
          { label: "Leaves", count: 0 },
        ],
        root: null,
        roots: [],
        selectedDetail: null,
      },
    } as MemoryDashboardData

    render(
      <InsightsPage
        data={emptyInsightsData}
        refreshAction={noopRefreshAction}
      />
    )

    expect(screen.getByTestId("memory-insights-empty")).toBeInTheDocument()
    expect(screen.getByText("No insights yet")).toBeInTheDocument()
    expect(screen.queryByText("No insight selected")).not.toBeInTheDocument()
    expect(
      screen.queryByTestId("memory-insights-explorer")
    ).not.toBeInTheDocument()
    expect(screen.queryByTestId("memory-insights-canvas")).not.toBeInTheDocument()
  })

  it("does not select the first root insight or load children before user selection", () => {
    const rootsOnlyData = {
      insights: {
        branches: [],
        hierarchy: [{ label: "Roots", count: 2, items: [] }],
        root: null,
        roots: [
          {
            branches: [],
            category: "Strategic",
            childInsightIds: [118, 442],
            children: "2 Children",
            description: "Root A description.",
            id: "1",
            kind: "root",
            label: "Root",
            title: "Platform Scalability Thesis",
          },
          {
            branches: [],
            category: "Growth",
            childInsightIds: [],
            children: "0 Children",
            description: "Root B description.",
            id: "2",
            kind: "root",
            label: "Root",
            title: "Market Expansion Strategy",
          },
        ],
        selectedDetail: null,
      },
    } as MemoryDashboardData

    render(
      <InsightsPage data={rootsOnlyData} refreshAction={noopRefreshAction} />
    )

    const explorer = screen.getByTestId("memory-insights-explorer")

    expect(explorer).toBeInTheDocument()
    expect(
      within(explorer).getByText("Platform Scalability Thesis")
    ).toBeInTheDocument()
    expect(
      within(explorer).getByText("Market Expansion Strategy")
    ).toBeInTheDocument()
    expect(screen.queryByTestId("memory-insights-canvas")).not.toBeInTheDocument()
    expect(
      screen.queryByRole("button", {
        name: /Open root insight Platform Scalability Thesis/,
      })
    ).not.toBeInTheDocument()
    expect(fetchInsightsList).not.toHaveBeenCalled()
  })

  it("loads all insight descendant levels from list endpoint when a root is selected", async () => {
    const user = userEvent.setup()
    const rootsOnlyData = {
      insights: {
        branches: [],
        hierarchy: [{ label: "Roots", count: 1, items: [] }],
        root: null,
        roots: [
          {
            branches: [],
            category: "Strategic",
            childInsightIds: [118, 442],
            children: "2 Children",
            description: "Root A description.",
            id: "1",
            kind: "root",
            label: "Root",
            title: "Platform Scalability Thesis",
          },
        ],
        selectedDetail: null,
      },
    } as MemoryDashboardData

    vi.mocked(fetchInsightsList).mockImplementation(async (insightIds) => {
      if (insightIds.join(",") === "118,442") {
        return [
          {
            childInsightIds: [104],
            content: "Architecture branch description.",
            insightId: 118,
            name: "Architecture Bottlenecks",
            tier: "BRANCH",
            type: "Architecture",
          },
          {
            childInsightIds: [331],
            content: "Frontend branch description.",
            insightId: 442,
            name: "Frontend Performance",
            tier: "BRANCH",
            type: "Frontend",
          },
        ]
      }

      if (insightIds.join(",") === "104") {
        return [
          {
            childInsightIds: [],
            content: "A leaf description.",
            insightId: 104,
            name: "V8 Engine JIT Delay",
            tier: "LEAF",
            type: "Observation",
          },
        ]
      }

      if (insightIds.join(",") === "331") {
        return [
          {
            childInsightIds: [],
            content: "Another leaf description.",
            insightId: 331,
            name: "DOM Depth Nesting",
            tier: "LEAF",
            type: "Metric",
          },
        ]
      }

      return []
    })

    render(
      <InsightsPage data={rootsOnlyData} refreshAction={noopRefreshAction} />
    )

    await user.click(
      screen.getByRole("button", {
        name: /Select root Platform Scalability Thesis/,
      })
    )

    await waitFor(() => {
      expect(fetchInsightsList).toHaveBeenCalledTimes(3)
    })
    expect(fetchInsightsList).toHaveBeenNthCalledWith(1, [118, 442])
    expect(fetchInsightsList).toHaveBeenNthCalledWith(2, [104])
    expect(fetchInsightsList).toHaveBeenNthCalledWith(3, [331])
    expect(
      await screen.findByRole("button", {
        name: /Open branch insight Architecture Bottlenecks/,
      })
    ).toBeInTheDocument()
    expect(screen.getAllByText("Frontend Performance").length).toBeGreaterThan(
      0
    )
    expect(screen.getAllByText("V8 Engine JIT Delay").length).toBeGreaterThan(0)
    expect(screen.getAllByText("DOM Depth Nesting").length).toBeGreaterThan(0)
  })

  it("switches branches and canvas nodes when a root is selected", async () => {
    const user = userEvent.setup()
    render(
      <InsightsPage data={insightsData} refreshAction={noopRefreshAction} />
    )

    expect(screen.getByTestId("memory-insights-explorer")).toBeInTheDocument()
    expect(
      screen.getAllByText("Platform Scalability Thesis").length
    ).toBeGreaterThan(0)
    expect(screen.queryByText("Architecture Bottlenecks")).not.toBeInTheDocument()
    expect(screen.queryByText("V8 Engine JIT Delay")).not.toBeInTheDocument()
    expect(
      screen.queryByText("Enterprise Segment Signals")
    ).not.toBeInTheDocument()

    await user.click(
      screen.getByRole("button", {
        name: /Select root Platform Scalability Thesis/,
      })
    )

    expect(
      screen.getAllByText("Architecture Bottlenecks").length
    ).toBeGreaterThan(0)
    expect(screen.getAllByText("V8 Engine JIT Delay").length).toBeGreaterThan(0)
    expect(
      screen.queryByText("Enterprise Segment Signals")
    ).not.toBeInTheDocument()

    await user.click(
      screen.getByRole("button", {
        name: /Select root Market Expansion Strategy/,
      })
    )

    expect(
      screen.getAllByText("Enterprise Segment Signals").length
    ).toBeGreaterThan(0)
    expect(
      screen.getAllByText("Procurement Cycle Shift").length
    ).toBeGreaterThan(0)
    expect(screen.queryAllByText("Architecture Bottlenecks")).toHaveLength(0)
    expect(screen.queryAllByText("V8 Engine JIT Delay")).toHaveLength(0)
    expect(
      await screen.findByRole("button", {
        name: /Open branch insight Enterprise Segment Signals/,
      })
    ).toBeInTheDocument()
  })

  it("renders an opaque explorer and visible canvas edges", async () => {
    const user = userEvent.setup()
    render(
      <InsightsPage data={insightsData} refreshAction={noopRefreshAction} />
    )

    await user.click(
      screen.getByRole("button", {
        name: /Select root Platform Scalability Thesis/,
      })
    )

    const explorer = screen.getByTestId("memory-insights-explorer")
    const explorerStyle = explorer.getAttribute("style") ?? ""
    const edgeLayer = screen.getByTestId("memory-insight-edge-layer")

    expect(explorer).toHaveClass("bg-background")
    expect(explorerStyle).not.toContain("opacity")
    expect(edgeLayer).toHaveAttribute("data-edge-count", "2")
    expect(edgeLayer).toHaveAttribute(
      "data-edge-stroke",
      "var(--muted-foreground)"
    )
    await waitFor(() => {
      const edgePaths = document.querySelectorAll(".react-flow__edge-path")

      expect(edgePaths.length).toBeGreaterThan(0)
      expect(edgePaths[0]).toHaveStyle({
        stroke: "var(--muted-foreground)",
      })
    })
  })

  it("keeps the explorer readable while constraining the canvas to the content area", async () => {
    const user = userEvent.setup()
    render(
      <InsightsPage data={insightsData} refreshAction={noopRefreshAction} />
    )

    await user.click(
      screen.getByRole("button", {
        name: /Select root Platform Scalability Thesis/,
      })
    )

    const explorer = screen.getByTestId("memory-insights-explorer")
    const explorerHeader = screen.getByTestId("memory-insights-explorer-header")
    const explorerContent = screen.getByTestId(
      "memory-insights-explorer-content"
    )
    const canvas = screen.getByTestId("memory-insights-canvas")

    expect(explorer).toHaveClass("w-72")
    expect(explorer).toHaveClass("shrink-0")
    expect(explorerHeader).toHaveClass("min-w-0")
    expect(explorerContent).toHaveClass("min-w-0")
    expect(explorer).not.toHaveClass("min-w-[260px]")
    expect(explorerHeader).not.toHaveClass("min-w-[260px]")
    expect(explorerContent).not.toHaveClass("min-w-[260px]")
    expect(explorer.getAttribute("style") ?? "").not.toContain("288px")
    expect(canvas.parentElement).toHaveClass("min-w-0", "w-full", "flex-1")
  })

  it("shows node details after selecting a node and closes them on blur", async () => {
    const user = userEvent.setup()
    render(
      <InsightsPage data={insightsData} refreshAction={noopRefreshAction} />
    )

    expect(
      screen.queryByTestId("memory-insight-details")
    ).not.toBeInTheDocument()

    await user.click(
      screen.getByRole("button", {
        name: /Select root Platform Scalability Thesis/,
      })
    )

    fireEvent.click(
      await screen.findByRole("button", {
        name: /Open branch insight Architecture Bottlenecks/,
      })
    )

    const details = screen.getByTestId("memory-insight-details")
    expect(within(details).getByText("Node Details")).toBeInTheDocument()
    expect(within(details).getByText("ID: branch-a-1")).toBeInTheDocument()

    await user.click(screen.getByTestId("memory-insights-toolbar"))

    await waitFor(() => {
      expect(
        screen.queryByTestId("memory-insight-details")
      ).not.toBeInTheDocument()
    })
  })
})
