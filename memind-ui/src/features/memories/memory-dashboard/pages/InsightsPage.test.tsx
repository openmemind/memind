import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react"
import userEvent from "@testing-library/user-event"

import type { MemoryDashboardData } from "../memory-dashboard-data"
import { InsightsPage } from "./InsightsPage"

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

describe("InsightsPage", () => {
  it("switches branches and canvas nodes when a root is selected", async () => {
    const user = userEvent.setup()
    render(<InsightsPage data={insightsData} />)

    expect(screen.getByTestId("memory-insights-explorer")).toBeInTheDocument()
    expect(
      screen.getAllByText("Platform Scalability Thesis").length
    ).toBeGreaterThan(0)
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
    render(<InsightsPage data={insightsData} />)

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

  it("keeps the explorer readable while constraining the canvas to the content area", () => {
    render(<InsightsPage data={insightsData} />)

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
    render(<InsightsPage data={insightsData} />)

    expect(
      screen.queryByTestId("memory-insight-details")
    ).not.toBeInTheDocument()

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
