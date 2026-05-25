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

import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { vi } from "vitest"

const apiFixtures = vi.hoisted(() => {
  function page<T>(items: T[], totalItems = items.length, pageNumber = 1) {
    return {
      items,
      page: {
        hasNext: pageNumber < Math.max(1, Math.ceil(totalItems / 20)),
        hasPrevious: pageNumber > 1,
        page: pageNumber,
        pageSize: 20,
        totalItems,
        totalPages: Math.max(1, Math.ceil(totalItems / 20)),
      },
    }
  }

  const dashboard = {
    activity: {
      days: 7,
      insightsCreated: [{ count: 12, date: "2026-05-20" }],
      itemsCreated: [{ count: 48, date: "2026-05-20" }],
      rawDataCreated: [{ count: 124, date: "2026-05-20" }],
    },
    backlog: {
      conversationPending: 3,
      graphBatchRepairRequired: 1,
      insightUnbuilt: 12,
      insightUngrouped: 0,
      threadOutboxFailed: 0,
      threadOutboxPending: 18,
    },
    breakdown: {
      graphLinkTypes: [{ count: 8103, name: "semantic" }],
      insightTypes: [{ count: 124, name: "pattern" }],
      itemTypes: [
        { count: 2800, name: "FACT" },
        { count: 612, name: "FORESIGHT" },
      ],
      rawDataTypes: [
        { count: 2952, name: "conversation" },
        { count: 844, name: "document" },
        { count: 422, name: "audio" },
      ],
      sourceClients: [{ count: 4218, name: "slack" }],
    },
    healthSignals: {
      graphEnabled: true,
      retrievalGraphAssistEnabled: true,
      threadProjectionStates: [{ count: 18, state: "COMMITTED" }],
    },
    totals: {
      graphEntities: 1284,
      insights: 124,
      itemLinks: 8103,
      items: 3412,
      memoryThreads: 18,
      rawData: 4218,
    },
  }
  const dashboardRecentMemories = [
    {
      agentId: "Agent-L4-Prime",
      alert: "healthy",
      createdAt: "2026-05-20T08:00:00Z",
      memoryId: "MEM-8429-XQ",
      requests: 4,
      updatedAt: "2026-05-21T08:00:00Z",
      userId: "u_alex_chen",
    },
  ]

  const rawData = [
    {
      caption: "User inquired about API rate limits for the Nexus endpoint...",
      captionVectorId: "vec_8213-90",
      createdAt: "2026-05-20T14:30:00Z",
      metadata: {
        source: "slack_integration",
        tags: ["critical", "api-errors"],
        vector_id: "vec_8213-90",
      },
      rawDataId: "rd_7a2b9c1d-84e1-4f02-9844-01938ae2",
      segment: {
        confidence: 0.992,
        end: 1709214015,
        language: "en-US",
        speaker_id: "user_491",
        start: 1709214000,
        tokens: 42,
      },
      sourceClient: "Slack (Prod)",
      type: "conversation",
    },
    {
      caption: "Architecture schema for distributed memory nodes v2.4...",
      captionVectorId: "vec_9241-12",
      createdAt: "2026-05-20T14:16:00Z",
      metadata: { source: "drive_document" },
      rawDataId: "rd_8b1f0a62-83bd-44aa-8f89-13fc77b1e296",
      segment: { page: 12, tokens: 186 },
      sourceClient: "G-Drive",
      type: "document",
    },
    {
      caption: "Meeting transcript from Sync Call #42 regarding Q3 metrics...",
      captionVectorId: "vec_3912-71",
      createdAt: "2026-05-20T13:32:00Z",
      metadata: { source: "zoom_transcript" },
      rawDataId: "rd_4c9ae720-1a37-4d49-9b54-cccb9b5a9010",
      segment: { speaker_count: 5, tokens: 512 },
      sourceClient: "Zoom",
      type: "audio",
    },
    {
      caption: "get_weather called for coordinates 34.0522 N...",
      captionVectorId: "vec_1880-44",
      createdAt: "2026-05-20T12:30:00Z",
      metadata: { source: "agent_tool_call" },
      rawDataId: "rd_2e4fa0c7-54f9-4a4b-a2a4-d26a14b02f13",
      segment: { result: "cached", tool: "get_weather" },
      sourceClient: "Agent-API",
      type: "tool",
    },
  ]

  const items = [
    {
      category: "behavior",
      content: "User prefers dark mode for all dashboard interfaces.",
      contentHash: "h_9f2e...88a2",
      createdAt: "2026-05-20T14:30:05Z",
      itemId: 293712,
      metadata: {
        confidence: 0.98,
        entities: ["user", "dark mode"],
      },
      observedAt: "2026-05-20T14:30:05Z",
      rawDataId: "rd_7a2b9c1d-84e1-4f02-9844-01938ae2",
      rawDataType: "conversation",
      scope: "USER",
      sourceClient: "slack_integration",
      type: "FACT",
      vectorId: "vec_8213_f9a1_223b_091c",
    },
    {
      category: "behavior",
      content: "User frequently analyzes Q3 revenue spikes via SQL workbench.",
      contentHash: "h_0a44...31bd",
      createdAt: "2026-05-20T14:17:21Z",
      itemId: 512611,
      metadata: { confidence: 0.94 },
      observedAt: "2026-05-20T14:17:21Z",
      rawDataId: "rd_8b1f0a62-83bd-44aa-8f89-13fc77b1e296",
      rawDataType: "document",
      scope: "USER",
      sourceClient: "drive_document",
      type: "FACT",
      vectorId: "vec_9241_a031_551d_0ac2",
    },
    {
      category: "directive",
      content:
        "Agent should prioritize latency over token efficiency for mobile clients.",
      contentHash: "h_8c92...124e",
      createdAt: "2026-05-20T13:32:44Z",
      itemId: 840409,
      metadata: { confidence: 0.91 },
      observedAt: "2026-05-20T13:32:44Z",
      rawDataId: "rd_4c9ae720-1a37-4d49-9b54-cccb9b5a9010",
      rawDataType: "audio",
      scope: "AGENT",
      sourceClient: "zoom_transcript",
      type: "FORESIGHT",
      vectorId: "vec_3912_b89d_224a_77c0",
    },
  ]

  const threads = [
    {
      displayLabel: "Career transition toward backend architecture",
      eventCount: 3,
      headline:
        "User repeatedly discusses Java backend leadership and distributed systems.",
      lastEventAt: "2026-05-20T14:22:00Z",
      lifecycleStatus: "ACTIVE",
      memberCount: 18,
      snapshotJson: {
        latestUpdate:
          "The user has shifted focus from full-stack engineering to backend systems.",
      },
      threadKey: "thr_k9x_02931",
      threadType: "career",
      updatedAt: "2026-05-20T14:22:00Z",
    },
    {
      displayLabel: "Health and Fitness Goals 2024",
      eventCount: 3,
      headline:
        "Monitoring consistency in morning workouts and dietary preferences.",
      lastEventAt: "2026-05-20T08:10:00Z",
      lifecycleStatus: "ACTIVE",
      memberCount: 8,
      snapshotJson: {
        latestUpdate:
          "The user is tracking consistency across morning workouts.",
      },
      threadKey: "thr_health_2218",
      threadType: "goal",
      updatedAt: "2026-05-20T08:10:00Z",
    },
    {
      displayLabel: "Home Renovation Logistics",
      eventCount: 3,
      headline:
        "Tracking contractor quotes and interior design mood boards for kitchen.",
      lastEventAt: "2026-05-19T16:05:00Z",
      lifecycleStatus: "DORMANT",
      memberCount: 24,
      snapshotJson: {
        latestUpdate:
          "The renovation thread groups contractor quotes and kitchen layout decisions.",
      },
      threadKey: "thr_home_8031",
      threadType: "home",
      updatedAt: "2026-05-19T16:05:00Z",
    },
  ]

  const insights = [
    {
      categories: ["Optimization", "Frontend", "Critical"],
      childInsightIds: [118, 442],
      content:
        "Comprehensive synthesis of infrastructure constraints and growth projection modeling.",
      groupName: "Technical Debt",
      insightId: 1,
      name: "Platform Scalability Thesis",
      points: [
        {
          content:
            "Rendering latency spikes observed during high-frequency updates.",
          type: "SUMMARY",
        },
      ],
      scope: "USER",
      tier: "ROOT",
      type: "Strategic",
    },
    {
      categories: ["Architecture"],
      childInsightIds: [104, 102],
      content: "Summarizing database and networking latency issues.",
      insightId: 118,
      name: "Architecture Bottlenecks",
      points: [{ content: "Database latency is recurring.", type: "SUMMARY" }],
      scope: "USER",
      tier: "BRANCH",
      type: "Architecture",
    },
    {
      categories: ["Optimization", "Frontend", "Critical"],
      childInsightIds: [331],
      content: "Analysis of rendering cycles and memory management.",
      insightId: 442,
      name: "Frontend Performance",
      points: [
        {
          content: "Memory footprint increases linearly with node count.",
          type: "SUMMARY",
        },
      ],
      scope: "USER",
      tier: "BRANCH",
      type: "Frontend",
    },
  ]

  const conversationBuffers = [
    {
      agentId: "Agent-L4-Prime",
      content: "Need to commit checkout flow conversation before extraction.",
      createdAt: "2026-05-20T14:35:00Z",
      extracted: false,
      id: 9001,
      memoryId: "MEM-8429-XQ",
      role: "user",
      sessionId: "session-checkout-42",
      sourceClient: "chat-ui",
      timestamp: "2026-05-20T14:35:00Z",
      updatedAt: "2026-05-20T14:36:00Z",
      userId: "u_alex_chen",
      userName: "Alex Chen",
    },
    {
      agentId: "Agent-L4-Prime",
      content: "Extracted renewal-plan conversation waiting for cleanup.",
      createdAt: "2026-05-20T13:10:00Z",
      extracted: true,
      id: 9002,
      memoryId: "MEM-8429-XQ",
      role: "assistant",
      sessionId: "session-renewal-18",
      sourceClient: "agent-runtime",
      timestamp: "2026-05-20T13:10:00Z",
      updatedAt: "2026-05-20T13:18:00Z",
      userId: "u_alex_chen",
      userName: "Alex Chen",
    },
  ]

  const insightBuffers = [
    {
      agentId: "Agent-L4-Prime",
      built: false,
      createdAt: "2026-05-20T14:40:00Z",
      groupName: null,
      id: 7001,
      insightTypeName: "preference",
      itemId: 293712,
      memoryId: "MEM-8429-XQ",
      updatedAt: "2026-05-20T14:41:00Z",
      userId: "u_alex_chen",
    },
    {
      agentId: "Agent-L4-Prime",
      built: true,
      createdAt: "2026-05-20T12:25:00Z",
      groupName: "retention",
      id: 7002,
      insightTypeName: "behavior",
      itemId: 512611,
      memoryId: "MEM-8429-XQ",
      updatedAt: "2026-05-20T12:50:00Z",
      userId: "u_alex_chen",
    },
  ]

  return {
    alertsSummary: {
      critical: 0,
      items: [
        { memoryId: "Graph repair required", time: "1" },
        { memoryId: "Unbuilt insights", time: "12" },
      ],
      warning: 31,
    },
    dashboard,
    dashboardRecentMemories,
    graphBatches: page([
      {
        errorMessage: "Schema mismatch",
        extractionBatchId: "BATCH_EXT_9921",
        id: 1,
        state: "repair_required",
        updatedAt: "2026-05-20T14:22:11Z",
      },
    ]),
    graphEntities: page([
      {
        displayName: "Elon Musk",
        entityKey: "person:elon-musk",
        entityType: "PERSON",
        id: 1,
        metadata: { confidence: 0.982 },
      },
      {
        displayName: "SpaceX",
        entityKey: "org:spacex",
        entityType: "ORGANIZATION",
        id: 2,
        metadata: { confidence: 0.972 },
      },
    ]),
    graphSummary: {
      aliasCount: 3492,
      cooccurrenceCount: 542,
      entityCount: 1284,
      entityCountByType: [{ count: 1, name: "PERSON" }],
      graphBatchCountByState: [{ count: 7, name: "repair_required" }],
      itemLinkCount: 8103,
      itemLinkCountByType: [{ count: 8103, name: "semantic" }],
      mentionCount: 142,
    },
    conversationBuffers: page(conversationBuffers, 42),
    insights: page(insights, 3),
    insightsTree: {
      roots: insights,
    },
    insightBuffers: page(insightBuffers, 42),
    items: page(items, 3412),
    memoryOptions: {
      config: {
        "extraction.common": [
          {
            description: "Maximum extraction timeout",
            key: "extraction.common.timeout",
            type: "duration",
            value: "PT30S",
          },
        ],
        "extraction.item": [
          {
            description: "Enable item graph extraction",
            key: "extraction.item.graph.enabled",
            type: "boolean",
            value: true,
          },
        ],
        "retrieval.simple": [
          {
            description: "Enable graph-assisted retrieval",
            key: "retrieval.simple.graphAssist.enabled",
            type: "boolean",
            value: true,
          },
        ],
        memoryThread: [
          {
            description: "Enable memory thread projection",
            key: "memoryThread.projection.enabled",
            type: "boolean",
            value: true,
          },
        ],
      },
      version: 3,
    },
    uiPreferences: {
      autoHideEmptyCollections: false,
      defaultMemoryView: "table",
      defaultTimeRange: "7d",
      showOnboardingTips: true,
      theme: "system",
    },
    memoriesPage: {
      items: [
        {
          agentId: "Agent-L4-Prime",
          alerts: { critical: 0, warning: 0 },
          createdAt: "May 18, 2026",
          id: "MEM-8429-XQ",
          insights: 12,
          items: 429,
          lastActivity: "2m ago",
          name: "Customer-Core-01",
          rawData: 1200,
          requests: 1200,
          status: "active",
          userId: "u_alex_chen",
        },
        {
          agentId: "Agent-Zeta",
          alerts: { critical: 1, warning: 2 },
          createdAt: "May 17, 2026",
          id: "MEM-3310-ZZ",
          insights: 2,
          items: 15,
          lastActivity: "1h ago",
          name: "Support-Context-B",
          rawData: 892,
          requests: 892,
          status: "error",
          userId: "u_sarah_j",
        },
      ],
      page: {
        hasNext: false,
        hasPrevious: false,
        page: 1,
        pageSize: 25,
        totalItems: 2,
        totalPages: 1,
      },
    },
    rawData: page(rawData, 4218),
    threadStatus: {
      failedCount: 0,
      materializationPolicyVersion: "mi_8f2x...k82",
      pendingCount: 0,
      projectionState: "committed",
      rebuildInProgress: false,
      updatedAt: "2026-05-21T00:00:00Z",
    },
    threads: page(threads, 18),
  }
})

vi.mock("./features/memories/memories-api", () => ({
  fetchMemoriesPage: vi.fn(async () => apiFixtures.memoriesPage),
}))

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

function jsonResponse(data: unknown) {
  return Promise.resolve(
    new Response(JSON.stringify({ data }), {
      headers: { "Content-Type": "application/json" },
      status: 200,
    })
  )
}

function pagedFixture<T>(
  fixture: {
    items: T[]
    page: {
      pageSize: number
      totalItems: number
    }
  },
  pageNumber: number
) {
  const totalPages = Math.max(
    1,
    Math.ceil(fixture.page.totalItems / fixture.page.pageSize)
  )

  return {
    items: fixture.items,
    page: {
      hasNext: pageNumber < totalPages,
      hasPrevious: pageNumber > 1,
      page: pageNumber,
      pageSize: fixture.page.pageSize,
      totalItems: fixture.page.totalItems,
      totalPages,
    },
  }
}

function routeFixture(url: string) {
  const parsed = new URL(url, "http://localhost")
  const path = parsed.pathname

  if (path === "/admin/v1/dashboard") {
    return apiFixtures.dashboard
  }

  if (path === "/admin/v1/alerts/summary") {
    return apiFixtures.alertsSummary
  }

  if (path === "/admin/v1/dashboard/recent-memories") {
    return apiFixtures.dashboardRecentMemories
  }

  if (path === "/admin/v1/memories") {
    return apiFixtures.memoriesPage
  }

  if (path === "/admin/v1/raw-data") {
    return pagedFixture(
      apiFixtures.rawData,
      Number(parsed.searchParams.get("page") ?? 1)
    )
  }

  if (path === "/admin/v1/memories/MEM-8429-XQ/raw-data") {
    return pagedFixture(
      apiFixtures.rawData,
      Number(parsed.searchParams.get("page") ?? 1)
    )
  }

  if (path === "/admin/v1/items") {
    return pagedFixture(
      apiFixtures.items,
      Number(parsed.searchParams.get("page") ?? 1)
    )
  }

  if (path === "/admin/v1/memories/MEM-8429-XQ/items") {
    return pagedFixture(
      apiFixtures.items,
      Number(parsed.searchParams.get("page") ?? 1)
    )
  }

  if (path === "/admin/v1/item-graph/summary") {
    return apiFixtures.graphSummary
  }

  if (path === "/admin/v1/item-graph/entities") {
    return apiFixtures.graphEntities
  }

  if (path === "/admin/v1/item-graph/batches") {
    return apiFixtures.graphBatches
  }

  if (path === "/admin/v1/memory-threads") {
    return apiFixtures.threads
  }

  if (path === "/admin/v1/memory-threads/status") {
    return apiFixtures.threadStatus
  }

  if (path === "/admin/v1/buffers/conversations") {
    return pagedFixture(
      apiFixtures.conversationBuffers,
      Number(parsed.searchParams.get("page") ?? 1)
    )
  }

  if (path === "/admin/v1/buffers/insights") {
    return pagedFixture(
      apiFixtures.insightBuffers,
      Number(parsed.searchParams.get("page") ?? 1)
    )
  }

  if (path === "/admin/v1/insights") {
    return apiFixtures.insights
  }

  if (path === "/admin/v1/insights/tree") {
    return apiFixtures.insightsTree
  }

  if (path === "/admin/v1/insights/list") {
    const insightIds = parsed.searchParams
      .getAll("insightIds")
      .map((insightId) => Number(insightId))

    return apiFixtures.insights.items.filter((insight) =>
      insightIds.includes(insight.insightId)
    )
  }

  if (path === "/admin/v1/config/memory-options") {
    return apiFixtures.memoryOptions
  }

  if (path === "/admin/v1/settings/ui-preferences") {
    return apiFixtures.uiPreferences
  }

  throw new Error(`Unhandled API fixture: ${path}`)
}

describe("Dashboard", () => {
  beforeEach(() => {
    vi.stubGlobal(
      "fetch",
      vi.fn((input: RequestInfo | URL) =>
        jsonResponse(routeFixture(String(input)))
      )
    )
    window.history.pushState({}, "", "/dashboard")
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    delete (document as DocumentWithViewTransition).startViewTransition
  })

  it("renders the dashboard from the template structure", async () => {
    renderApp()

    expect(
      await screen.findByRole("heading", { name: "Overview" })
    ).toBeInTheDocument()
    expect(
      screen.getByText(
        "Track memory growth, requests, alerts, and recent activity."
      )
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
    expect(
      screen.queryByRole("button", { name: "Filter" })
    ).not.toBeInTheDocument()
    const metricCards = screen.getAllByTestId("dashboard-metric-card")
    expect(metricCards).toHaveLength(3)
    expect(within(metricCards[0]).getByText("memories")).toBeInTheDocument()
    expect(within(metricCards[0]).getByText("2")).toBeInTheDocument()
    expect(
      within(metricCards[1]).getByText("req count extract")
    ).toBeInTheDocument()
    expect(within(metricCards[1]).getByText("3,412")).toBeInTheDocument()
    expect(within(metricCards[2]).getByText("req count")).toBeInTheDocument()
    expect(within(metricCards[2]).getByText("4,218")).toBeInTheDocument()
    expect(screen.queryByText("Raw Data")).not.toBeInTheDocument()
    expect(screen.queryByText("Memory Items")).not.toBeInTheDocument()
    expect(screen.queryByText("Insights")).not.toBeInTheDocument()
    expect(screen.queryByText("Graph Entities")).not.toBeInTheDocument()
    expect(screen.queryByText("Backlog")).not.toBeInTheDocument()
    expect(screen.queryByText("Total Memories")).not.toBeInTheDocument()
    expect(screen.queryByText("Total Requests")).not.toBeInTheDocument()
    expect(screen.queryByText("Failed Requests")).not.toBeInTheDocument()
    expect(screen.queryByText("Active Alerts")).not.toBeInTheDocument()
    expect(screen.queryByText("New Memories")).not.toBeInTheDocument()
    expect(screen.getAllByText("Critical").length).toBeGreaterThan(0)
    expect(screen.getByText("Warning")).toBeInTheDocument()
    const table = screen.getByRole("table")
    expect(within(table).getAllByRole("row")).toHaveLength(2)
    expect(within(table).getByText("MEM-8429-XQ")).toBeInTheDocument()
    expect(within(table).getByText("Agent-L4-Prime")).toBeInTheDocument()
    expect(screen.getByText("Showing 1 recent memories")).toBeInTheDocument()
  })

  it("renders request activity through the chart component", async () => {
    renderApp()

    expect(await screen.findByText("Request Activity")).toBeInTheDocument()

    const panel = screen.getByTestId("request-activity-panel")
    expect(within(panel).getByText("7-day flow")).toBeInTheDocument()
    expect(within(panel).getAllByText("req count").length).toBeGreaterThan(0)
    expect(
      within(panel).getAllByText("req count extract").length
    ).toBeGreaterThan(0)
    expect(within(panel).getByText("insights")).toBeInTheDocument()
    expect(within(panel).queryByText("Failed")).not.toBeInTheDocument()
    expect(
      within(panel).queryByText("Successful")
    ).not.toBeInTheDocument()

    const chart = screen.getByTestId("request-activity-chart")
    expect(chart).toHaveAttribute("data-slot", "chart")
    expect(chart).toHaveClass("h-56")
    expect(
      screen.queryByLabelText("Request activity trend")
    ).not.toBeInTheDocument()
  })

  it("opens a recent memory workspace from the dashboard table", async () => {
    const user = userEvent.setup()
    renderApp()

    expect(
      await screen.findByText("Recently Added Memories")
    ).toBeInTheDocument()

    const recentTable = screen.getByRole("table")
    await user.click(within(recentTable).getByRole("button", { name: "Open" }))

    expect(
      await screen.findByRole("heading", { name: "Memory Overview" })
    ).toBeInTheDocument()
    expect(window.location.pathname).toBe("/memories/MEM-8429-XQ")
  })

  const workspaceEndpointPaths = [
    "/admin/v1/dashboard",
    "/admin/v1/memories/MEM-8429-XQ/raw-data",
    "/admin/v1/memories/MEM-8429-XQ/items",
    "/admin/v1/item-graph/summary",
    "/admin/v1/item-graph/entities",
    "/admin/v1/item-graph/batches",
    "/admin/v1/memory-threads",
    "/admin/v1/memory-threads/status",
    "/admin/v1/insights",
    "/admin/v1/insights/tree",
    "/admin/v1/buffers/conversations",
    "/admin/v1/buffers/insights",
  ]

  function requestCount(apiPath: string) {
    return vi
      .mocked(fetch)
      .mock.calls.filter(
        ([input]) =>
          new URL(String(input), "http://localhost").pathname === apiPath
      ).length
  }

  it.each([
    {
      heading: "Memory Overview",
      path: "/memories/MEM-8429-XQ",
      refreshedPaths: ["/admin/v1/dashboard"],
    },
    {
      heading: "Raw Data",
      path: "/memories/MEM-8429-XQ/raw-data",
      refreshedPaths: ["/admin/v1/memories/MEM-8429-XQ/raw-data"],
    },
    {
      heading: "Memory Items",
      path: "/memories/MEM-8429-XQ/items",
      refreshedPaths: ["/admin/v1/memories/MEM-8429-XQ/items"],
    },
    {
      heading: "Graph Explorer",
      path: "/memories/MEM-8429-XQ/graph",
      refreshedPaths: [
        "/admin/v1/item-graph/summary",
        "/admin/v1/item-graph/entities",
        "/admin/v1/item-graph/batches",
      ],
    },
    {
      heading: "Memory Threads",
      path: "/memories/MEM-8429-XQ/threads",
      refreshedPaths: [
        "/admin/v1/memory-threads",
        "/admin/v1/memory-threads/status",
      ],
    },
    {
      heading: "Insight Tree",
      path: "/memories/MEM-8429-XQ/insights",
      refreshedPaths: ["/admin/v1/insights/tree"],
    },
    {
      heading: "Memory Buffers",
      path: "/memories/MEM-8429-XQ/buffers",
      refreshedPaths: [
        "/admin/v1/buffers/conversations",
        "/admin/v1/buffers/insights",
      ],
    },
  ])(
    "refetches only the active workspace page from $path",
    async ({ heading, path, refreshedPaths }) => {
      const user = userEvent.setup()
      window.history.pushState({}, "", path)
      renderApp()

      await screen.findByRole("heading", { name: heading })

      const initialCounts = new Map(
        workspaceEndpointPaths.map((apiPath) => [
          apiPath,
          requestCount(apiPath),
        ])
      )

      await user.click(screen.getByRole("button", { name: "Refresh" }))

      await waitFor(() => {
        for (const apiPath of refreshedPaths) {
          expect(requestCount(apiPath)).toBe(
            (initialCounts.get(apiPath) ?? 0) + 1
          )
        }
      })

      for (const apiPath of workspaceEndpointPaths) {
        if (!refreshedPaths.includes(apiPath)) {
          expect(requestCount(apiPath)).toBe(initialCounts.get(apiPath))
        }
      }
    }
  )

  it.each([
    [
      "/memories/MEM-8429-XQ/raw-data",
      "Raw Data",
      "/admin/v1/memories/MEM-8429-XQ/raw-data",
    ],
    [
      "/memories/MEM-8429-XQ/items",
      "Memory Items",
      "/admin/v1/memories/MEM-8429-XQ/items",
    ],
    [
      "/memories/MEM-8429-XQ/buffers",
      "Memory Buffers",
      "/admin/v1/buffers/conversations",
    ],
  ])(
    "refetches page-scoped records from %s when Refresh is clicked",
    async (path, heading, apiPath) => {
      const user = userEvent.setup()
      window.history.pushState({}, "", path)
      renderApp()

      await screen.findByRole("heading", { name: heading })

      const pageRequestCount = () =>
        vi
          .mocked(fetch)
          .mock.calls.filter(
            ([input]) =>
              new URL(String(input), "http://localhost").pathname === apiPath
          ).length

      expect(pageRequestCount()).toBe(1)

      await user.click(screen.getByRole("button", { name: "Refresh" }))

      await waitFor(() => {
        expect(pageRequestCount()).toBe(2)
      })
    }
  )

  it.each([
    [
      "/memories/MEM-8429-XQ/raw-data",
      "Raw Data",
      "/admin/v1/memories/MEM-8429-XQ/raw-data",
    ],
    [
      "/memories/MEM-8429-XQ/items",
      "Memory Items",
      "/admin/v1/memories/MEM-8429-XQ/items",
    ],
    [
      "/memories/MEM-8429-XQ/buffers",
      "Memory Buffers",
      "/admin/v1/buffers/conversations",
    ],
  ])(
    "loads the next table page from %s when pagination is clicked",
    async (path, heading, apiPath) => {
      const user = userEvent.setup()
      window.history.pushState({}, "", path)
      renderApp()

      await screen.findByRole("heading", { name: heading })

      await user.click(screen.getByRole("button", { name: "Go to next page" }))

      await waitFor(() => {
        expect(
          vi
            .mocked(fetch)
            .mock.calls.some(([input]) => {
              const requestUrl = new URL(String(input), "http://localhost")

              return (
                requestUrl.pathname === apiPath &&
                requestUrl.searchParams.get("page") === "2"
              )
            })
        ).toBe(true)
      })
    }
  )

  it("renders the calm console shell polish", async () => {
    renderApp()

    expect(await screen.findByLabelText("Memind console")).toBeInTheDocument()
    expect(screen.getByRole("button", { name: "Dashboard" })).toHaveAttribute(
      "aria-current",
      "page"
    )
    expect(screen.getByRole("button", { name: "Memories" })).toBeInTheDocument()
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
    const table = screen.getByRole("table")
    expect(within(table).getByText("ID")).toBeInTheDocument()
    expect(within(table).getByText("User / Agent")).toBeInTheDocument()
    expect(within(table).getByText("Status")).toBeInTheDocument()
    expect(within(table).getByText("Items")).toBeInTheDocument()
    expect(within(table).getByText("Insight")).toBeInTheDocument()
    expect(within(table).getByText("Raw Data")).toBeInTheDocument()
    expect(within(table).getByText("Activity")).toBeInTheDocument()
    expect(within(table).getByText("MEM-8429-XQ")).toBeInTheDocument()
    expect(within(table).getByText("u_alex_chen")).toBeInTheDocument()
    expect(within(table).getByText("Agent-L4-Prime")).toBeInTheDocument()
    expect(within(table).getByText("429")).toBeInTheDocument()
    expect(within(table).getByText("1,200")).toBeInTheDocument()
    expect(
      within(table).queryByText("Customer-Core-01")
    ).not.toBeInTheDocument()
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
    expect(screen.getAllByText("MEM-8429-XQ").length).toBeGreaterThan(0)
    expect(screen.getByText("All agents")).toBeInTheDocument()
    expect(
      screen.getByRole("button", { name: "Copy MEM-8429-XQ" })
    ).toBeInTheDocument()
    expect(
      screen.getByRole("button", { name: "Copy All agents" })
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
    expect(screen.getByText("MEM-8429-XQ")).toBeInTheDocument()
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
    expect(
      screen.getByText(
        "Inspect source records, captions, segments, metadata, and cleanup impact for this Memory."
      )
    ).toBeInTheDocument()
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

    const details = screen.getByTestId("record-details-panel")

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument()
    expect(details).toHaveAttribute("data-animation", "slide-scale")
    expect(details).toHaveAttribute("data-anchor", "right")
    expect(details).toHaveAttribute("data-expanded", "false")
    await waitFor(() => {
      expect(details).toHaveAttribute("data-state", "open")
    })
    expect(within(details).getByText("Record Details")).toBeInTheDocument()
    expect(within(details).getByText("Raw Caption")).toBeInTheDocument()
    expect(within(details).getAllByText('"start"')[0]).toHaveClass("hljs-attr")
    expect(within(details).getByText("1709214000")).toHaveClass("hljs-number")
    expect(within(details).getByText('"speaker_id"')).toHaveClass("hljs-attr")
    expect(within(details).getByText('"user_491"')).toHaveClass("hljs-string")
    expect(within(details).getByText('"critical"')).toHaveClass("hljs-string")
    expect(window.location.pathname).toBe("/memories/MEM-8429-XQ/raw-data")
  })

  it("keeps the Record Details panel mounted until the close animation completes", async () => {
    const user = userEvent.setup()
    window.history.pushState({}, "", "/memories/MEM-8429-XQ/raw-data")
    renderApp()

    await screen.findByRole("heading", { name: "Raw Data" })
    await user.click(screen.getAllByRole("button", { name: "View" })[0])

    const details = screen.getByTestId("record-details-panel")

    await waitFor(() => {
      expect(details).toHaveAttribute("data-state", "open")
    })

    await user.click(
      within(details).getByRole("button", { name: "Close record details" })
    )

    expect(screen.getByTestId("record-details-panel")).toBeInTheDocument()
    expect(screen.getByTestId("record-details-panel")).toHaveAttribute(
      "data-state",
      "closed"
    )

    fireEvent.transitionEnd(screen.getByTestId("record-details-panel"))

    await waitFor(() => {
      expect(screen.queryByTestId("record-details-panel")).not.toBeInTheDocument()
    })
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

  it("opens the matching workspace pages from Needs Attention items", async () => {
    const user = userEvent.setup()
    window.history.pushState({}, "", "/memories/MEM-8429-XQ")
    renderApp()

    await screen.findByRole("heading", { name: "Memory Overview" })

    await user.click(
      screen.getByRole("button", { name: /Pending conversations/ })
    )

    expect(
      await screen.findByRole("heading", { name: "Memory Threads" })
    ).toBeInTheDocument()
    expect(window.location.pathname).toBe("/memories/MEM-8429-XQ/threads")

    await user.click(screen.getByRole("button", { name: "Overview" }))
    await screen.findByRole("heading", { name: "Memory Overview" })

    await user.click(screen.getByRole("button", { name: /Unbuilt insights/ }))

    expect(
      await screen.findByRole("heading", { name: "Insight Tree" })
    ).toBeInTheDocument()
    expect(window.location.pathname).toBe("/memories/MEM-8429-XQ/insights")

    await user.click(screen.getByRole("button", { name: "Overview" }))
    await screen.findByRole("heading", { name: "Memory Overview" })

    await user.click(
      screen.getByRole("button", { name: /Graph repair required/ })
    )

    expect(
      await screen.findByRole("heading", { name: "Graph Explorer" })
    ).toBeInTheDocument()
    expect(window.location.pathname).toBe("/memories/MEM-8429-XQ/graph")
  })

  it("renders a direct Memory Raw Data route from the current URL", async () => {
    window.history.pushState({}, "", "/memories/MEM-8429-XQ/raw-data")
    renderApp()

    expect(
      await screen.findByRole("heading", { name: "Raw Data" })
    ).toBeInTheDocument()
    expect(screen.getByText("Showing 4 of 4,218 records")).toBeInTheDocument()
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
    expect(screen.getByText("293712")).toBeInTheDocument()
    expect(
      screen.getByText("User prefers dark mode for all dashboard interfaces.")
    ).toBeInTheDocument()
    expect(screen.getByText("Showing 3 of 3,412 items")).toBeInTheDocument()
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
    expect(details).toHaveClass("max-w-120")
    expect(within(details).getByText("Itemdetails")).toBeInTheDocument()
    expect(within(details).getByText("293712")).toBeInTheDocument()
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
      "max-w-120"
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

  it("keeps Memory Itemdetails open until the close button is clicked", async () => {
    const user = userEvent.setup()
    window.history.pushState({}, "", "/memories/MEM-8429-XQ/items")
    renderApp()

    expect(
      await screen.findByRole("heading", { name: "Memory Items" })
    ).toBeInTheDocument()

    await user.click(screen.getAllByRole("button", { name: "View" })[0])

    expect(screen.getByTestId("item-details-panel")).toBeInTheDocument()

    await user.click(screen.getByRole("heading", { name: "Memory Items" }))

    expect(screen.getByTestId("item-details-panel")).toBeInTheDocument()

    await user.click(
      within(screen.getByTestId("item-details-panel")).getByRole("button", {
        name: "Close item details",
      })
    )

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
    expect(screen.getByTestId("thread-workbench")).toBeInTheDocument()
    expect(screen.getByTestId("thread-sidebar")).toBeInTheDocument()
    expect(screen.getByTestId("thread-detail")).toBeInTheDocument()
    expect(screen.getByText("ACTIVE THREAD")).toBeInTheDocument()
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
    expect(screen.getByText("thr_k9x_02931-snapshot")).toBeInTheDocument()
    expect(screen.getAllByRole("button", { name: /View Item/ })).toHaveLength(1)
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

  it("opens the Memory Buffers page from the workspace sidebar", async () => {
    const user = userEvent.setup()
    window.history.pushState({}, "", "/memories/MEM-8429-XQ")
    renderApp()

    await screen.findByRole("heading", { name: "Memory Overview" })

    await user.click(screen.getByRole("button", { name: "Buffers" }))

    expect(
      await screen.findByRole("heading", { name: "Memory Buffers" })
    ).toBeInTheDocument()
    expect(
      screen.getByText(
        "Inspect pending conversation and insight buffers before they are committed into memory."
      )
    ).toBeInTheDocument()
    expect(screen.getByTestId("memory-buffers-filter")).toBeInTheDocument()
    expect(
      within(screen.getByTestId("memory-buffers-filter")).getByRole("tab", {
        name: "Conversation",
      })
    ).toHaveAttribute("data-state", "active")
    expect(
      within(screen.getByTestId("memory-buffers-filter")).queryByRole("tab", {
        name: "All",
      })
    ).not.toBeInTheDocument()
    expect(
      within(screen.getByTestId("memory-buffers-filter")).getByRole("tab", {
        name: "Insight",
      })
    ).toBeInTheDocument()

    const table = screen.getByRole("table")

    expect(within(table).getByText("Buffer")).toBeInTheDocument()
    expect(within(table).getAllByText("Conversation").length).toBeGreaterThan(0)
    expect(within(table).queryByText("Insight")).not.toBeInTheDocument()
    expect(within(table).getByText("session-checkout-42")).toBeInTheDocument()
    expect(
      within(table).getByText(
        "Need to commit checkout flow conversation before extraction."
      )
    ).toBeInTheDocument()
    expect(within(table).queryByText("preference")).not.toBeInTheDocument()
    expect(within(table).queryByText("293712")).not.toBeInTheDocument()
    expect(
      screen.getByText("Showing 2 of 42 conversation buffers")
    ).toBeInTheDocument()
    expect(window.location.pathname).toBe("/memories/MEM-8429-XQ/buffers")
  })

  it("renders a direct Memory Buffers route and switches buffer tables", async () => {
    const user = userEvent.setup()
    window.history.pushState({}, "", "/memories/MEM-8429-XQ/buffers")
    renderApp()

    expect(
      await screen.findByRole("heading", { name: "Memory Buffers" })
    ).toBeInTheDocument()
    expect(screen.getByRole("button", { name: "Insights" })).toBeInTheDocument()

    await user.click(screen.getByRole("tab", { name: "Insight" }))

    expect(screen.queryByText("session-checkout-42")).not.toBeInTheDocument()
    expect(screen.getByText("preference")).toBeInTheDocument()
    expect(screen.getByText("retention")).toBeInTheDocument()
    expect(screen.getByText("Showing 2 of 42 insight buffers")).toBeInTheDocument()

    await user.click(screen.getByRole("tab", { name: "Conversation" }))

    expect(screen.getByText("session-checkout-42")).toBeInTheDocument()
    expect(screen.getByText("session-renewal-18")).toBeInTheDocument()
    expect(screen.queryByText("retention")).not.toBeInTheDocument()
    expect(
      screen.getByText("Showing 2 of 42 conversation buffers")
    ).toBeInTheDocument()
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
    expect(screen.getByText("thr_health_2218-snapshot")).toBeInTheDocument()
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
    expect(screen.queryByTestId("memory-insights-canvas")).not.toBeInTheDocument()
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
    expect(screen.getByText("Hierarchy Explorer")).toBeInTheDocument()
    expect(screen.getByText("Roots")).toBeInTheDocument()
    expect(screen.getByText("Branches")).toBeInTheDocument()
    expect(screen.getByText("Leaves")).toBeInTheDocument()
    expect(screen.getAllByText("Platform Scalability Thesis").length).toBe(1)
    expect(
      screen.queryByText("Market Expansion Strategy")
    ).not.toBeInTheDocument()
    expect(screen.queryByText("User Retention Flow")).not.toBeInTheDocument()
    expect(
      screen.queryByRole("button", { name: /Open root insight/ })
    ).not.toBeInTheDocument()

    await user.click(
      screen.getByRole("button", {
        name: /Select root Platform Scalability Thesis/,
      })
    )

    const canvasWorkspace = await screen.findByTestId("memory-insights-canvas")

    expect(canvasWorkspace).toHaveClass("h-full", "w-full", "flex-1")
    expect(canvasWorkspace.parentElement).toHaveClass(
      "min-w-0",
      "w-full",
      "flex-1"
    )
    expect(
      screen.getByRole("button", { name: /Open root insight/ })
    ).toHaveAttribute("data-id", "1")
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
    expect(screen.queryByText("V8 Engine JIT Delay")).not.toBeInTheDocument()
    expect(screen.queryByText("Canvas Overflow #102")).not.toBeInTheDocument()
    expect(screen.queryByText("DOM Depth Nesting")).not.toBeInTheDocument()
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
      within(explorer).queryByText("V8 Engine JIT Delay")
    ).not.toBeInTheDocument()

    await user.click(
      screen.getByRole("button", {
        name: /Select root Platform Scalability Thesis/,
      })
    )

    expect(
      within(explorer).getAllByText("Architecture Bottlenecks").length
    ).toBeGreaterThan(0)

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

    await fireEvent.click(
      screen.getByRole("button", {
        name: /Select root Platform Scalability Thesis/,
      })
    )

    fireEvent.click(
      await screen.findByRole("button", {
        name: /Open branch insight Frontend Performance/,
      })
    )

    expect(screen.getByText("Node Details")).toBeInTheDocument()
    const details = screen.getByTestId("memory-insight-details")

    expect(
      within(details).getByText("Frontend Performance")
    ).toBeInTheDocument()
    expect(within(details).getByText("Insight Points")).toBeInTheDocument()
    expect(within(details).getByText("ID: 442")).toBeInTheDocument()
    expect(within(details).getAllByText("Frontend").length).toBeGreaterThan(0)
    expect(within(details).getByText("Branch")).toBeInTheDocument()
    expect(within(details).getByText("Operational")).toBeInTheDocument()
    expect(within(details).getByText("branch")).toBeInTheDocument()
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
      screen.getByRole("button", {
        name: /Select root Platform Scalability Thesis/,
      })
    )

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
    expect(screen.getByTestId("planning-content-inset")).toHaveClass(
      "overflow-hidden"
    )
    expect(screen.getByTestId("analytics-planning-overlay")).toHaveTextContent(
      "Planning"
    )
    expect(screen.getByTestId("analytics-planning-overlay")).toHaveClass(
      "backdrop-blur-md"
    )
    expect(screen.getByText("Roadmap in progress")).toBeInTheDocument()
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
    expect(screen.getByTestId("planning-content-inset")).toHaveClass(
      "overflow-hidden"
    )
    expect(screen.getByTestId("api-keys-planning-overlay")).toHaveTextContent(
      "Planning"
    )
    expect(screen.getByTestId("api-keys-planning-overlay")).toHaveClass(
      "backdrop-blur-md"
    )
    expect(screen.getByText("Roadmap in progress")).toBeInTheDocument()
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
    expect(
      screen.queryByRole("heading", { name: "Extraction Common" })
    ).not.toBeInTheDocument()
    expect(
      screen.queryByText("Maximum extraction timeout")
    ).not.toBeInTheDocument()
    expect(screen.queryByDisplayValue("PT30S")).not.toBeInTheDocument()
    expect(screen.queryByText("Danger Zone")).not.toBeInTheDocument()
    expect(
      screen.queryByRole("button", { name: "Purge Cache" })
    ).not.toBeInTheDocument()
    expect(
      screen.queryByRole("button", { name: "Save changes" })
    ).not.toBeInTheDocument()
    expect(
      screen.queryByRole("button", { name: "Reset changes" })
    ).not.toBeInTheDocument()
    expect(screen.getByTestId("settings-secondary-nav")).toHaveClass(
      "h-full",
      "overflow-hidden"
    )
    expect(
      within(screen.getByTestId("settings-secondary-nav")).getByRole("button", {
        name: "General",
      })
    ).toHaveAttribute("aria-current", "page")
    expect(
      within(screen.getByTestId("settings-secondary-nav")).getByRole("button", {
        name: "Extraction Common",
      })
    ).toBeInTheDocument()
    expect(
      within(screen.getByTestId("settings-secondary-nav")).getByRole("button", {
        name: "Extraction Item",
      })
    ).toBeInTheDocument()
    expect(
      within(screen.getByTestId("settings-secondary-nav")).getByRole("button", {
        name: "Retrieval Simple",
      })
    ).toBeInTheDocument()
    expect(
      within(screen.getByTestId("settings-secondary-nav")).getByRole("button", {
        name: "Memory Thread",
      })
    ).toBeInTheDocument()
    expect(
      within(screen.getByTestId("settings-secondary-nav")).queryByRole(
        "button",
        {
          name: "Models",
        }
      )
    ).not.toBeInTheDocument()
    expect(screen.getByTestId("settings-detail-pane")).toHaveClass(
      "overflow-y-auto"
    )

    await user.click(
      within(screen.getByTestId("settings-secondary-nav")).getByRole("button", {
        name: "Extraction Common",
      })
    )

    expect(screen.queryByText("Display Preferences")).not.toBeInTheDocument()
    expect(screen.queryByText("Empty State Behavior")).not.toBeInTheDocument()
    expect(
      screen.queryByRole("button", { name: "Common" })
    ).not.toBeInTheDocument()
    expect(
      screen.queryByRole("button", { name: "Item" })
    ).not.toBeInTheDocument()
    expect(
      screen.getByRole("heading", { name: "Extraction Common" })
    ).toBeInTheDocument()
    expect(screen.getByText("Maximum extraction timeout")).toBeInTheDocument()
    expect(screen.getByDisplayValue("PT30S")).toBeInTheDocument()
    expect(
      screen.queryByText("Enable item graph extraction")
    ).not.toBeInTheDocument()

    await user.click(
      within(screen.getByTestId("settings-secondary-nav")).getByRole("button", {
        name: "Extraction Item",
      })
    )

    expect(
      screen.getByRole("heading", { name: "Extraction Item" })
    ).toBeInTheDocument()
    expect(screen.getByText("Enable item graph extraction")).toBeInTheDocument()
    expect(
      screen.queryByText("Maximum extraction timeout")
    ).not.toBeInTheDocument()
    expect(window.location.pathname).toBe("/settings")
  })

  it("shows floating settings actions only after a setting changes", async () => {
    const user = userEvent.setup()
    renderApp()

    await user.click(await screen.findByRole("button", { name: "Settings" }))

    expect(
      screen.queryByRole("button", { name: "Save changes" })
    ).not.toBeInTheDocument()

    await user.click(screen.getByRole("button", { name: "List" }))

    expect(
      await screen.findByRole("button", { name: "Save changes" })
    ).toBeInTheDocument()
    expect(
      screen.getByRole("button", { name: "Discard changes" })
    ).toBeInTheDocument()

    await user.click(screen.getByRole("button", { name: "Discard changes" }))

    expect(
      screen.queryByRole("button", { name: "Save changes" })
    ).not.toBeInTheDocument()
    expect(
      screen.queryByRole("button", { name: "Discard changes" })
    ).not.toBeInTheDocument()
  })

  it("saves edited backend memory options", async () => {
    const user = userEvent.setup()
    renderApp()

    await user.click(await screen.findByRole("button", { name: "Settings" }))
    await user.click(
      within(screen.getByTestId("settings-secondary-nav")).getByRole("button", {
        name: "Extraction Common",
      })
    )

    const timeoutInput = await screen.findByLabelText("Timeout")
    await user.clear(timeoutInput)
    await user.type(timeoutInput, "PT45S")
    await user.click(screen.getByRole("button", { name: "Save changes" }))

    await waitFor(() => {
      expect(fetch).toHaveBeenCalledWith(
        "/admin/v1/config/memory-options",
        expect.objectContaining({
          body: JSON.stringify({
            config: {
              "extraction.common": [
                {
                  description: "Maximum extraction timeout",
                  key: "extraction.common.timeout",
                  type: "duration",
                  value: "PT45S",
                },
              ],
              "extraction.item": [
                {
                  description: "Enable item graph extraction",
                  key: "extraction.item.graph.enabled",
                  type: "boolean",
                  value: true,
                },
              ],
              "retrieval.simple": [
                {
                  description: "Enable graph-assisted retrieval",
                  key: "retrieval.simple.graphAssist.enabled",
                  type: "boolean",
                  value: true,
                },
              ],
              memoryThread: [
                {
                  description: "Enable memory thread projection",
                  key: "memoryThread.projection.enabled",
                  type: "boolean",
                  value: true,
                },
              ],
            },
            expectedVersion: 3,
          }),
          method: "PUT",
        })
      )
    })
  })
})
