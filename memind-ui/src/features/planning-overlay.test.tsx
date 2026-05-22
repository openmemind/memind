import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { render, screen } from "@testing-library/react"
import { describe, expect, it, vi } from "vitest"

import { Analytics } from "./analytics/Analytics"
import { ApiKeys } from "./api-keys/ApiKeys"
import { AppShell } from "./shell/AppShell"

vi.mock("./analytics/analytics-data", async () => {
  const actual = await vi.importActual<
    typeof import("./analytics/analytics-data")
  >("./analytics/analytics-data")

  return {
    ...actual,
    useAnalyticsData: () => ({
      data: undefined,
      isError: false,
      isLoading: true,
    }),
  }
})

vi.mock("./api-keys/api-keys-data", async () => {
  const actual = await vi.importActual<
    typeof import("./api-keys/api-keys-data")
  >("./api-keys/api-keys-data")

  return {
    ...actual,
    useApiKeysData: () => ({
      data: undefined,
      isError: false,
      isLoading: true,
    }),
  }
})

function renderWithQueryClient(ui: React.ReactNode) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
    },
  })

  return render(
    <QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>
  )
}

describe("planning overlays", () => {
  it("renders the Analytics planning overlay before analytics data resolves", () => {
    renderWithQueryClient(
      <AppShell activePage="analytics" onPageChange={vi.fn()}>
        <Analytics />
      </AppShell>
    )

    expect(screen.getByTestId("analytics-planning-overlay")).toHaveTextContent(
      "Planning"
    )
    expect(screen.getByTestId("planning-content-inset")).toHaveClass(
      "overflow-hidden"
    )
    expect(screen.queryByText("Loading analytics...")).not.toBeInTheDocument()
  })

  it("renders the API Keys planning overlay before API key data resolves", () => {
    renderWithQueryClient(
      <AppShell activePage="api-keys" onPageChange={vi.fn()}>
        <ApiKeys />
      </AppShell>
    )

    expect(screen.getByTestId("api-keys-planning-overlay")).toHaveTextContent(
      "Planning"
    )
    expect(screen.getByTestId("planning-content-inset")).toHaveClass(
      "overflow-hidden"
    )
    expect(screen.queryByText("Loading API keys...")).not.toBeInTheDocument()
  })
})
