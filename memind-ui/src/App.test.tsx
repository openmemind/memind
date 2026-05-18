import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { act, render, screen, waitFor } from "@testing-library/react"
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
