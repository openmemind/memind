import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { vi } from "vitest"

import App from "./App"

type DocumentWithViewTransition = Document & {
  startViewTransition?: (callback: () => void) => { finished: Promise<void> }
}

function renderApp() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
    },
  })

  return render(
    <QueryClientProvider client={queryClient}>
      <App />
    </QueryClientProvider>
  )
}

describe("Dashboard", () => {
  beforeEach(() => {
    window.history.pushState({}, "", "/dashboard")
  })

  afterEach(() => {
    delete (document as DocumentWithViewTransition).startViewTransition
  })

  it("renders the template dashboard sections", async () => {
    renderApp()

    expect(
      await screen.findByRole("heading", { name: "Dashboard" })
    ).toBeInTheDocument()
    expect(screen.getByRole("button", { name: "24h" })).toBeInTheDocument()
    expect(screen.getByRole("button", { name: "7d" })).toBeInTheDocument()
    expect(screen.getByRole("button", { name: "30d" })).toBeInTheDocument()
    expect(
      screen.getByPlaceholderText("Search MemoryId / UserId / AgentId...")
    ).toBeInTheDocument()
    expect(screen.getByText("Request Activity")).toBeInTheDocument()
    expect(screen.getByText("Alerts Summary")).toBeInTheDocument()
    expect(screen.getByText("Recently Added Memories")).toBeInTheDocument()
  })

  it("renders the calm console shell polish", async () => {
    renderApp()

    expect(await screen.findByLabelText("Memind console")).toBeInTheDocument()
    expect(screen.getByRole("button", { name: "Dashboard" })).toHaveAttribute(
      "aria-current",
      "page"
    )
    expect(screen.getByRole("button", { name: "GitHub" })).toBeInTheDocument()
    expect(screen.queryByText("Runtime healthy")).not.toBeInTheDocument()
    expect(screen.queryByText("Local runtime")).not.toBeInTheDocument()
    expect(screen.getByTestId("console-content-surface")).toBeInTheDocument()
  })

  it("opens the Memories page from the primary navigation", async () => {
    const user = userEvent.setup()
    renderApp()

    await user.click(await screen.findByRole("button", { name: "Memories" }))

    expect(
      await screen.findByRole("heading", { name: "Memories" })
    ).toBeInTheDocument()
    expect(
      screen.getByText("Browse, filter, and open memory workspaces.")
    ).toBeInTheDocument()
    expect(screen.getByText("Total Memories")).toBeInTheDocument()
    expect(screen.getByText("Customer-Core-01")).toBeInTheDocument()
    expect(screen.getByRole("button", { name: "Sort" })).toBeInTheDocument()
    expect(screen.getAllByRole("combobox")).toHaveLength(3)
    expect(window.location.pathname).toBe("/memories")
  })

  it("opens a Memory instance dashboard and returns to the Memories list", async () => {
    const user = userEvent.setup()
    renderApp()

    await user.click(await screen.findByRole("button", { name: "Memories" }))
    await user.click(screen.getAllByRole("button", { name: "Open" })[0])

    expect(
      await screen.findByRole("heading", { name: "Memory Overview" })
    ).toBeInTheDocument()
    expect(
      screen.getAllByRole("button", { name: "Back to Console" }).length
    ).toBeGreaterThan(0)
    expect(screen.queryByText("Memory runtime")).not.toBeInTheDocument()
    expect(screen.queryByText("Memory-772")).not.toBeInTheDocument()
    expect(screen.queryByText("Identity Context")).not.toBeInTheDocument()
    expect(screen.queryByText("ID: mem_8f2a9c1d")).not.toBeInTheDocument()
    expect(screen.queryByText("Agent: nexus-v1")).not.toBeInTheDocument()
    expect(screen.getAllByText("mem_8f2a9c1d").length).toBeGreaterThan(0)
    expect(screen.getByText("nexus-v1")).toBeInTheDocument()
    expect(
      screen.getByRole("button", { name: "Copy mem_8f2a9c1d" })
    ).toBeInTheDocument()
    expect(
      screen.getByRole("button", { name: "Copy nexus-v1" })
    ).toBeInTheDocument()
    expect(screen.getByText("Memory Formation Pipeline")).toBeInTheDocument()
    expect(screen.getByText("Quick Retrieve")).toBeInTheDocument()
    expect(window.location.pathname).toBe("/memories/MEM-8429-XQ")

    await user.click(
      screen.getAllByRole("button", { name: "Back to Console" })[0]
    )

    expect(
      await screen.findByRole("heading", { name: "Memories" })
    ).toBeInTheDocument()
    expect(screen.getByText("Customer-Core-01")).toBeInTheDocument()
    expect(window.location.pathname).toBe("/memories")
  })

  it("renders direct routes from the current URL", async () => {
    window.history.pushState({}, "", "/analytics")
    renderApp()

    expect(
      await screen.findByRole("heading", { name: "Analytics" })
    ).toBeInTheDocument()
  })

  it("renders a direct Memory instance route from the current URL", async () => {
    window.history.pushState({}, "", "/memories/MEM-8429-XQ")
    renderApp()

    expect(
      await screen.findByRole("heading", { name: "Memory Overview" })
    ).toBeInTheDocument()
    expect(screen.queryByText("Memory runtime")).not.toBeInTheDocument()
    expect(screen.getByTestId("memory-workspace-surface")).toBeInTheDocument()
    expect(screen.getByTestId("memory-overview-header")).toBeInTheDocument()
  })

  it("opens the Memory Raw Data page from the workspace sidebar", async () => {
    const user = userEvent.setup()
    window.history.pushState({}, "", "/memories/MEM-8429-XQ")
    renderApp()

    expect(
      await screen.findByRole("heading", { name: "Memory Overview" })
    ).toBeInTheDocument()

    await user.click(screen.getByRole("button", { name: "Raw Data" }))

    expect(
      await screen.findByRole("heading", { name: "Raw Data" })
    ).toBeInTheDocument()
    expect(screen.getByTestId("memory-workspace-surface")).toBeInTheDocument()
    expect(screen.getByTestId("raw-data-toolbar")).toBeInTheDocument()
    expect(
      screen.getByText(
        "Inspect source records, captions, segments, metadata, and cleanup impact for this Memory."
      )
    ).toBeInTheDocument()
    expect(screen.getByText("Total Records")).toBeInTheDocument()
    expect(screen.getByText("With Caption")).toBeInTheDocument()
    expect(screen.getByPlaceholderText("Search records...")).toBeInTheDocument()
    expect(screen.getAllByRole("combobox")).toHaveLength(2)
    expect(screen.getByText("rd_7a2...")).toBeInTheDocument()
    expect(
      screen.getByRole("checkbox", {
        name: "Select rd_7a2b9c1d-84e1-4f02-9844-01938ae2",
      })
    ).not.toBeChecked()
    expect(screen.queryByText("Record Details")).not.toBeInTheDocument()
    expect(screen.getAllByRole("button", { name: "View" })).toHaveLength(4)

    await user.click(screen.getByText("rd_7a2..."))

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument()

    await user.click(screen.getAllByRole("button", { name: "View" })[0])

    expect(screen.getByRole("dialog")).toBeInTheDocument()
    expect(screen.getByTestId("record-details-sheet")).toHaveClass(
      "data-starting-style:translate-x-full"
    )
    expect(screen.getByText("Record Details")).toBeInTheDocument()
    expect(screen.getByText("Raw Caption")).toBeInTheDocument()
    expect(screen.getAllByText('"start"')[0]).toHaveClass("hljs-attr")
    expect(screen.getByText("1709214000")).toHaveClass("hljs-number")
    expect(screen.getByText('"speaker_id"')).toHaveClass("hljs-attr")
    expect(screen.getByText('"user_491"')).toHaveClass("hljs-string")
    expect(screen.getByText('"critical"')).toHaveClass("hljs-string")
    expect(
      screen.queryByTestId("record-details-overlay")
    ).not.toBeInTheDocument()
    expect(window.location.pathname).toBe("/memories/MEM-8429-XQ/raw-data")
  })

  it("keeps the Record Details sheet mounted until the close animation completes", async () => {
    const user = userEvent.setup()
    let finishCloseAnimation: () => void
    const closeAnimation = new Promise<void>((resolve) => {
      finishCloseAnimation = resolve
    })
    const originalGetAnimations = HTMLElement.prototype.getAnimations
    window.history.pushState({}, "", "/memories/MEM-8429-XQ/raw-data")
    renderApp()

    await screen.findByRole("heading", { name: "Raw Data" })
    await user.click(screen.getAllByRole("button", { name: "View" })[0])

    expect(screen.getByTestId("record-details-sheet")).toBeInTheDocument()

    Object.defineProperty(HTMLElement.prototype, "getAnimations", {
      configurable: true,
      value: vi.fn(() => [
        {
          finished: closeAnimation,
          pending: false,
          playState: "running",
        },
      ]),
    })

    try {
      await user.click(screen.getByRole("button", { name: "Close" }))

      expect(screen.getByTestId("record-details-sheet")).toBeInTheDocument()

      await act(async () => {
        finishCloseAnimation()
        await closeAnimation
      })

      await waitFor(() => {
        expect(
          screen.queryByTestId("record-details-sheet")
        ).not.toBeInTheDocument()
      })
    } finally {
      if (originalGetAnimations) {
        Object.defineProperty(HTMLElement.prototype, "getAnimations", {
          configurable: true,
          value: originalGetAnimations,
        })
      } else {
        delete HTMLElement.prototype.getAnimations
      }
    }
  })

  it("keeps the Memory sidebar mounted while switching workspace tabs", async () => {
    const user = userEvent.setup()
    window.history.pushState({}, "", "/memories/MEM-8429-XQ")
    renderApp()

    await screen.findByRole("heading", { name: "Memory Overview" })

    const sidebarBefore = screen
      .getByText("Memory Workspace")
      .closest('[data-slot="sidebar"]')

    await user.click(screen.getByRole("button", { name: "Raw Data" }))

    await screen.findByRole("heading", { name: "Raw Data" })

    const sidebarAfter = screen
      .getByText("Memory Workspace")
      .closest('[data-slot="sidebar"]')

    expect(sidebarAfter).toBe(sidebarBefore)
  })

  it("renders a direct Memory Raw Data route from the current URL", async () => {
    window.history.pushState({}, "", "/memories/MEM-8429-XQ/raw-data")
    renderApp()

    expect(
      await screen.findByRole("heading", { name: "Raw Data" })
    ).toBeInTheDocument()
    expect(
      screen.getByText("Showing 1-15 of 4,218 records")
    ).toBeInTheDocument()
    expect(screen.getByRole("button", { name: "Overview" })).toBeInTheDocument()
  })

  it("opens the Memory Items page from the workspace sidebar", async () => {
    const user = userEvent.setup()
    window.history.pushState({}, "", "/memories/MEM-8429-XQ")
    renderApp()

    await screen.findByRole("heading", { name: "Memory Overview" })

    await user.click(screen.getByRole("button", { name: "Items" }))

    expect(
      await screen.findByRole("heading", { name: "Memory Items" })
    ).toBeInTheDocument()
    expect(
      screen.getByText(
        "Inspect extracted memory facts, source raw data, metadata, vectors, and related threads."
      )
    ).toBeInTheDocument()
    expect(screen.getByText("Total Items")).toBeInTheDocument()
    expect(screen.getByText("USER Scope")).toBeInTheDocument()
    expect(screen.getByText("AGENT Scope")).toBeInTheDocument()
    expect(
      screen.getByPlaceholderText("Search memory items...")
    ).toBeInTheDocument()
    expect(screen.getAllByRole("combobox")).toHaveLength(4)
    expect(screen.getByText("mi_2b9c7...")).toBeInTheDocument()
    expect(
      screen.getByText("User prefers dark mode for all dashboard interfaces.")
    ).toBeInTheDocument()
    expect(screen.getByText("Showing 1-25 of 3,412 items")).toBeInTheDocument()
    expect(window.location.pathname).toBe("/memories/MEM-8429-XQ/items")
  })

  it("renders a direct Memory Items route and opens item details from View", async () => {
    const user = userEvent.setup()
    window.history.pushState({}, "", "/memories/MEM-8429-XQ/items")
    renderApp()

    expect(
      await screen.findByRole("heading", { name: "Memory Items" })
    ).toBeInTheDocument()
    expect(screen.getByRole("button", { name: "Raw Data" })).toBeInTheDocument()
    expect(screen.queryByTestId("item-details-panel")).not.toBeInTheDocument()

    await user.click(screen.getAllByRole("button", { name: "View" })[0])

    const details = screen.getByTestId("item-details-panel")

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument()
    expect(details).toHaveAttribute("data-animation", "slide-scale")
    expect(details).toHaveAttribute("data-expanded", "false")
    await waitFor(() => {
      expect(details).toHaveAttribute("data-state", "open")
    })
    expect(details).toHaveAttribute("data-anchor", "right")
    expect(details).toHaveClass("ml-auto")
    expect(details).toHaveClass("w-full")
    expect(details).toHaveClass("max-w-[30rem]")
    expect(within(details).getByText("Itemdetails")).toBeInTheDocument()
    expect(within(details).getByText("mi_2b9c7a1d")).toBeInTheDocument()
    expect(within(details).getByText("View Raw Data")).toBeInTheDocument()
    expect(
      within(details).getByText("Vector & Deduplication")
    ).toBeInTheDocument()
    expect(screen.getByText('"confidence"')).toHaveClass("hljs-attr")
    expect(screen.getByText("0.98")).toHaveClass("hljs-number")

    await user.click(
      within(details).getByRole("button", { name: "Expand item details" })
    )

    expect(screen.getByTestId("item-details-panel")).toHaveClass(
      "max-w-[calc(100vw-2rem)]"
    )
    expect(screen.getByTestId("item-details-panel")).toHaveClass(
      "transition-[max-width,transform,opacity]"
    )
    expect(screen.getByTestId("item-details-panel")).toHaveAttribute(
      "data-expanded",
      "true"
    )

    await user.click(
      within(screen.getByTestId("item-details-panel")).getByRole("button", {
        name: "Shrink item details",
      })
    )

    expect(screen.getByTestId("item-details-panel")).toHaveClass("ml-auto")
    expect(screen.getByTestId("item-details-panel")).toHaveClass(
      "max-w-[30rem]"
    )
    expect(screen.getByTestId("item-details-panel")).not.toHaveClass(
      "left-[calc(100vw-min(30rem,calc(100vw-2rem))-1rem)]"
    )
    expect(screen.getByTestId("item-details-panel")).toHaveAttribute(
      "data-expanded",
      "false"
    )

    await user.click(
      within(screen.getByTestId("item-details-panel")).getByRole("button", {
        name: "Close item details",
      })
    )

    expect(screen.getByTestId("item-details-panel")).toHaveAttribute(
      "data-state",
      "closed"
    )

    await waitFor(() => {
      expect(screen.queryByTestId("item-details-panel")).not.toBeInTheDocument()
    })
  })

  it("closes Memory Itemdetails when focus moves outside the floating panel", async () => {
    const user = userEvent.setup()
    window.history.pushState({}, "", "/memories/MEM-8429-XQ/items")
    renderApp()

    expect(
      await screen.findByRole("heading", { name: "Memory Items" })
    ).toBeInTheDocument()

    await user.click(screen.getAllByRole("button", { name: "View" })[0])

    expect(screen.getByTestId("item-details-panel")).toBeInTheDocument()

    await user.click(screen.getByTestId("items-toolbar"))

    await waitFor(() => {
      expect(screen.queryByTestId("item-details-panel")).not.toBeInTheDocument()
    })
  })

  it("opens the Memory Graph page from the workspace sidebar", async () => {
    const user = userEvent.setup()
    window.history.pushState({}, "", "/memories/MEM-8429-XQ")
    renderApp()

    await screen.findByRole("heading", { name: "Memory Overview" })

    await user.click(screen.getByRole("button", { name: "Graph" }))

    expect(
      await screen.findByRole("heading", { name: "Graph Explorer" })
    ).toBeInTheDocument()
    expect(
      screen.getByText(
        "Map connections between entities, mentions, and item relationships to discover hidden insights."
      )
    ).toBeInTheDocument()
    const graphCanvas = screen.getByTestId("memory-graph-canvas")

    expect(graphCanvas).toBeInTheDocument()
    expect(
      screen.getByPlaceholderText("Search entities...")
    ).toBeInTheDocument()
    expect(within(graphCanvas).getByText("Elon Musk")).toBeInTheDocument()
    expect(within(graphCanvas).getByText("SpaceX")).toBeInTheDocument()
    expect(screen.queryByText("Selection Detail")).not.toBeInTheDocument()
    expect(screen.queryByText("BATCH_EXT_9921")).not.toBeInTheDocument()
    expect(window.location.pathname).toBe("/memories/MEM-8429-XQ/graph")
  })

  it("renders a direct Memory Graph route", async () => {
    const user = userEvent.setup()
    window.history.pushState({}, "", "/memories/MEM-8429-XQ/graph")
    renderApp()

    expect(
      await screen.findByRole("heading", { name: "Graph Explorer" })
    ).toBeInTheDocument()
    expect(screen.getByRole("button", { name: "Items" })).toBeInTheDocument()
    expect(screen.getByTestId("memory-graph-flow")).toBeInTheDocument()
    expect(screen.queryByText("Selection Detail")).not.toBeInTheDocument()

    await user.click(
      screen.getByRole("button", {
        name: "Open Person entity Elon Musk",
      })
    )

    expect(screen.getByText("Selection Detail")).toBeInTheDocument()
    expect(screen.getByText("View All Mentions (142)")).toBeInTheDocument()
    expect(screen.getByText('"confidence"')).toHaveClass("hljs-attr")
    expect(screen.getByText("0.982")).toHaveClass("hljs-number")

    await user.click(screen.getByPlaceholderText("Search entities..."))

    expect(screen.queryByText("Selection Detail")).not.toBeInTheDocument()
  })

  it("opens the Memory Threads page from the workspace sidebar", async () => {
    const user = userEvent.setup()
    window.history.pushState({}, "", "/memories/MEM-8429-XQ")
    renderApp()

    await screen.findByRole("heading", { name: "Memory Overview" })

    await user.click(screen.getByRole("button", { name: "Threads" }))

    expect(
      await screen.findByRole("heading", { name: "Memory Threads" })
    ).toBeInTheDocument()
    expect(
      screen.getByText(
        "Manage storyline continuity and item memberships across your memory graph."
      )
    ).toBeInTheDocument()
    expect(screen.getByText("COMMITTED")).toBeInTheDocument()
    expect(screen.getByText("Active Threads")).toBeInTheDocument()
    expect(screen.getByText("Total Members")).toBeInTheDocument()
    expect(screen.getByPlaceholderText("Filter threads...")).toBeInTheDocument()
    expect(
      screen.getAllByText("Career transition toward backend architecture")
        .length
    ).toBeGreaterThan(0)
    expect(
      screen.getByText("Health and Fitness Goals 2024")
    ).toBeInTheDocument()
    expect(screen.getByText("thr_k9x_02931")).toBeInTheDocument()
    expect(screen.getByText("Narrative Snapshot")).toBeInTheDocument()
    expect(screen.getByText("Item Membership Timeline")).toBeInTheDocument()
    expect(screen.getByText("mi_02x_982")).toBeInTheDocument()
    expect(screen.getAllByRole("button", { name: /View Item/ })).toHaveLength(3)
    expect(window.location.pathname).toBe("/memories/MEM-8429-XQ/threads")
  })

  it("renders a direct Memory Threads route", async () => {
    window.history.pushState({}, "", "/memories/MEM-8429-XQ/threads")
    renderApp()

    expect(
      await screen.findByRole("heading", { name: "Memory Threads" })
    ).toBeInTheDocument()
    expect(screen.getByRole("button", { name: "Graph" })).toBeInTheDocument()
    expect(screen.getByText("Home Renovation Logistics")).toBeInTheDocument()
    expect(screen.getByText("Load 15 earlier items")).toBeInTheDocument()
  })

  it("switches the Thread detail when a storyline is selected", async () => {
    const user = userEvent.setup()
    window.history.pushState({}, "", "/memories/MEM-8429-XQ/threads")
    renderApp()

    expect(
      await screen.findByRole("heading", { name: "Memory Threads" })
    ).toBeInTheDocument()
    expect(screen.getByText("thr_k9x_02931")).toBeInTheDocument()

    await user.click(
      screen.getByRole("button", { name: /Health and Fitness Goals 2024/ })
    )

    expect(screen.getByText("thr_health_2218")).toBeInTheDocument()
    expect(
      screen.getAllByText("Health and Fitness Goals 2024").length
    ).toBeGreaterThan(0)
    expect(screen.getByText("mi_fit_204")).toBeInTheDocument()
    expect(screen.queryByText("thr_k9x_02931")).not.toBeInTheDocument()
  })

  it("opens the Memory Insights page as a template-style full workspace", async () => {
    const user = userEvent.setup()
    window.history.pushState({}, "", "/memories/MEM-8429-XQ")
    renderApp()

    await screen.findByRole("heading", { name: "Memory Overview" })

    await user.click(screen.getByRole("button", { name: "Insights" }))

    expect(
      await screen.findByRole("heading", { name: "Insight Tree" })
    ).toBeInTheDocument()
    const insightsWorkspace = screen.getByTestId("memory-insights-workspace")
    const insightsBody = screen.getByTestId("memory-insights-body")
    const insightsExplorer = screen.getByTestId("memory-insights-explorer")
    const toolbar = screen.getByTestId("memory-insights-toolbar")
    const canvasWorkspace = screen.getByTestId("memory-insights-canvas")

    expect(screen.getByTestId("memory-workspace-surface")).toHaveClass(
      "h-full",
      "w-full",
      "p-0"
    )
    expect(insightsWorkspace).toHaveClass("min-h-0", "flex-1")
    expect(insightsWorkspace).toHaveClass("h-full", "overflow-hidden")
    expect(insightsBody).toHaveClass("min-h-0", "flex-1", "overflow-hidden")
    expect(insightsBody).not.toHaveAttribute(
      "data-slot",
      "resizable-panel-group"
    )
    expect(insightsExplorer).toHaveClass("h-full", "w-72", "shrink-0")
    expect(canvasWorkspace).toHaveClass("h-full", "w-full", "flex-1")
    expect(canvasWorkspace.parentElement).toHaveClass(
      "min-w-0",
      "w-full",
      "flex-1"
    )
    expect(
      insightsWorkspace.querySelectorAll('[data-slot="card"]')
    ).toHaveLength(0)
    expect(toolbar.querySelector('[data-slot="separator"]')).toHaveClass(
      "self-center"
    )
    expect(
      screen.queryByPlaceholderText("Search insights...")
    ).not.toBeInTheDocument()
    expect(
      screen.queryByRole("button", { name: "Fit Tree" })
    ).not.toBeInTheDocument()
    expect(
      screen.queryByRole("button", { name: "Tree" })
    ).not.toBeInTheDocument()
    expect(
      screen.queryByRole("button", { name: "Table" })
    ).not.toBeInTheDocument()
    expect(
      insightsBody.querySelector('[data-slot="resizable-panel-group"]')
    ).not.toBeInTheDocument()
    expect(
      screen.queryByTestId("memory-insights-resize-handle")
    ).not.toBeInTheDocument()
    expect(screen.getByTestId("memory-insights-explorer")).toHaveClass(
      "w-72",
      "shrink-0"
    )
    expect(
      screen.getByTestId("memory-insights-canvas").parentElement
    ).toHaveClass("min-w-0", "w-full", "flex-1")
    expect(screen.getByText("Hierarchy Explorer")).toBeInTheDocument()
    expect(screen.getByText("Roots")).toBeInTheDocument()
    expect(screen.getByText("Branches")).toBeInTheDocument()
    expect(screen.getByText("Leaves")).toBeInTheDocument()
    expect(screen.getAllByText("Platform Scalability Thesis").length).toBe(2)
    expect(screen.getByText("Market Expansion Strategy")).toBeInTheDocument()
    expect(screen.getByText("User Retention Flow")).toBeInTheDocument()
    expect(
      screen.getByRole("button", { name: /Open root insight/ })
    ).toHaveAttribute("data-id", "INS-R-001")
    expect(screen.getByTestId("memory-insight-flow")).toHaveClass("react-flow")
    expect(
      screen.queryByTestId("memory-insight-link-canvas")
    ).not.toBeInTheDocument()
    expect(
      screen.getAllByText("Architecture Bottlenecks").length
    ).toBeGreaterThan(0)
    expect(screen.getAllByText("Frontend Performance").length).toBeGreaterThan(
      0
    )
    expect(screen.getAllByText("V8 Engine JIT Delay").length).toBeGreaterThan(0)
    expect(screen.getAllByText("Canvas Overflow #102").length).toBeGreaterThan(
      0
    )
    expect(screen.getAllByText("DOM Depth Nesting").length).toBeGreaterThan(0)
    expect(screen.queryByText("Node Details")).not.toBeInTheDocument()
    expect(window.location.pathname).toBe("/memories/MEM-8429-XQ/insights")
  })

  it("expands and collapses each Memory Insights explorer group", async () => {
    const user = userEvent.setup()
    window.history.pushState({}, "", "/memories/MEM-8429-XQ/insights")
    renderApp()

    expect(
      await screen.findByRole("heading", { name: "Insight Tree" })
    ).toBeInTheDocument()
    const explorer = screen.getByTestId("memory-insights-explorer")

    const rootsToggle = screen.getByRole("button", {
      name: /Toggle Roots group/,
    })
    const branchesToggle = screen.getByRole("button", {
      name: /Toggle Branches group/,
    })
    const leavesToggle = screen.getByRole("button", {
      name: /Toggle Leaves group/,
    })

    expect(rootsToggle).toHaveAttribute("aria-expanded", "true")
    expect(branchesToggle).toHaveAttribute("aria-expanded", "true")
    expect(leavesToggle).toHaveAttribute("aria-expanded", "true")
    expect(
      within(explorer).getByText("Architecture Bottlenecks")
    ).toBeInTheDocument()
    expect(
      within(explorer).getByText("V8 Engine JIT Delay")
    ).toBeInTheDocument()

    await user.click(branchesToggle)
    expect(branchesToggle).toHaveAttribute("aria-expanded", "false")
    expect(
      screen.queryByTestId("insight-explorer-branch-items")
    ).not.toBeInTheDocument()

    await user.click(leavesToggle)
    expect(leavesToggle).toHaveAttribute("aria-expanded", "false")
    expect(
      within(explorer).queryByText("V8 Engine JIT Delay")
    ).not.toBeInTheDocument()

    await user.click(rootsToggle)
    expect(rootsToggle).toHaveAttribute("aria-expanded", "false")
    expect(
      within(explorer).queryByText("Platform Scalability Thesis")
    ).not.toBeInTheDocument()
  })

  it("opens Memory Insight node details when a tree node is selected", async () => {
    window.history.pushState({}, "", "/memories/MEM-8429-XQ/insights")
    renderApp()

    expect(
      await screen.findByRole("heading", { name: "Insight Tree" })
    ).toBeInTheDocument()
    expect(screen.getByRole("button", { name: "Threads" })).toBeInTheDocument()
    expect(screen.queryByText("Node Details")).not.toBeInTheDocument()

    fireEvent.click(
      screen.getByRole("button", {
        name: /Open branch insight Frontend Performance/,
      })
    )

    expect(screen.getByText("Node Details")).toBeInTheDocument()
    expect(
      screen.getByText("Frontend Performance Analysis")
    ).toBeInTheDocument()
    const details = screen.getByTestId("memory-insight-details")

    expect(within(details).getByText("Insight Points")).toBeInTheDocument()
    expect(within(details).getByText("ID: INS-B-442")).toBeInTheDocument()
    expect(within(details).getByText("Technical Debt")).toBeInTheDocument()
    expect(within(details).getByText("Strategic (B)")).toBeInTheDocument()
    expect(within(details).getByText("Optimization")).toBeInTheDocument()
    expect(within(details).getByText("Frontend")).toBeInTheDocument()
    expect(within(details).getByText("Critical")).toBeInTheDocument()
    expect(
      screen.getByRole("button", { name: "Regenerate" })
    ).toBeInTheDocument()
  })

  it("collapses and restores the Memory Insights hierarchy explorer", async () => {
    const user = userEvent.setup()
    window.history.pushState({}, "", "/memories/MEM-8429-XQ/insights")
    renderApp()

    expect(
      await screen.findByRole("heading", { name: "Insight Tree" })
    ).toBeInTheDocument()
    expect(screen.getByText("Hierarchy Explorer")).toBeInTheDocument()

    await user.click(
      screen.getByRole("button", { name: "Collapse hierarchy explorer" })
    )

    await waitFor(() => {
      expect(screen.queryByText("Hierarchy Explorer")).not.toBeInTheDocument()
    })
    expect(
      screen.getByRole("button", { name: "Expand hierarchy explorer" })
    ).toBeInTheDocument()
    const canvasWorkspace = screen.getByTestId("memory-insights-canvas")

    expect(canvasWorkspace).toHaveClass("w-full", "flex-1")
    expect(canvasWorkspace.parentElement).toHaveClass(
      "min-w-0",
      "w-full",
      "flex-1"
    )

    await user.click(
      screen.getByRole("button", { name: "Expand hierarchy explorer" })
    )

    expect(await screen.findByText("Hierarchy Explorer")).toBeInTheDocument()
  })

  it("uses library animations instead of browser view transitions for Memory dashboard navigation", async () => {
    const user = userEvent.setup()
    const startViewTransition = vi.fn(() => {
      throw new TypeError("Browser view transition should not run")
    })
    ;(document as DocumentWithViewTransition).startViewTransition =
      startViewTransition

    renderApp()

    await user.click(await screen.findByRole("button", { name: "Memories" }))
    await user.click(screen.getAllByRole("button", { name: "Open" })[0])
    await screen.findByRole("heading", { name: "Memory Overview" })

    await user.click(
      screen.getAllByRole("button", { name: "Back to Console" })[0]
    )
    await screen.findByRole("heading", { name: "Memories" })

    expect(startViewTransition).not.toHaveBeenCalled()
  })

  it("opens the Analytics page from the primary navigation", async () => {
    const user = userEvent.setup()
    renderApp()

    await user.click(await screen.findByRole("button", { name: "Analytics" }))

    expect(
      await screen.findByRole("heading", { name: "Analytics" })
    ).toBeInTheDocument()
    expect(
      screen.getByText(
        "Observe runtime health, latency, failures, and memory activity."
      )
    ).toBeInTheDocument()
    expect(screen.getByText("RPM")).toBeInTheDocument()
    expect(screen.getByRole("button", { name: "15m" })).toBeInTheDocument()
    expect(screen.queryByText("Refresh:")).not.toBeInTheDocument()
    expect(
      screen.queryByText("v1.4.2-stable | Cluster: US-EAST-1")
    ).not.toBeInTheDocument()
    expect(screen.getByText("Request Health")).toBeInTheDocument()
    expect(screen.getByText("Recent Traces")).toBeInTheDocument()
    expect(screen.getByText("TRC_882x_12")).toBeInTheDocument()
    expect(window.location.pathname).toBe("/analytics")
  })

  it("keys the animated content region by the active sidebar tab", async () => {
    const user = userEvent.setup()
    renderApp()

    expect(
      await screen.findByTestId("animated-content-dashboard")
    ).toBeInTheDocument()

    await user.click(screen.getByRole("button", { name: "Analytics" }))

    expect(
      await screen.findByTestId("animated-content-analytics")
    ).toBeInTheDocument()
  })

  it("opens the API Keys page from the primary navigation", async () => {
    const user = userEvent.setup()
    renderApp()

    await user.click(await screen.findByRole("button", { name: "API Keys" }))

    expect(
      await screen.findByRole("heading", { name: "API Keys" })
    ).toBeInTheDocument()
    expect(
      screen.getByText(
        "Create, rotate, and monitor keys used to access Memind."
      )
    ).toBeInTheDocument()
    expect(
      screen.getByRole("button", { name: "Create API Key" })
    ).toBeInTheDocument()
    expect(screen.getByText("Production-Server-1")).toBeInTheDocument()
    expect(screen.getByText("mem_sk_abc123••••")).toBeInTheDocument()
    expect(screen.queryByText("Active keys")).not.toBeInTheDocument()
    expect(window.location.pathname).toBe("/api-keys")
  })

  it("opens the Settings page from the primary navigation", async () => {
    const user = userEvent.setup()
    renderApp()

    await user.click(await screen.findByRole("button", { name: "Settings" }))

    expect(
      await screen.findByRole("heading", { name: "Settings" })
    ).toBeInTheDocument()
    expect(
      screen.getByText(
        "Configure memory runtime behavior, model providers, security, and system preferences."
      )
    ).toBeInTheDocument()
    expect(
      screen.getByPlaceholderText("Search settings...")
    ).toBeInTheDocument()
    expect(screen.getByText("Display Preferences")).toBeInTheDocument()
    expect(screen.getByText("Empty State Behavior")).toBeInTheDocument()
    expect(screen.getByText("Danger Zone")).toBeInTheDocument()
    expect(
      screen.getAllByRole("button", { name: "Save changes" })
    ).toHaveLength(2)
    expect(window.location.pathname).toBe("/settings")
  })
})
