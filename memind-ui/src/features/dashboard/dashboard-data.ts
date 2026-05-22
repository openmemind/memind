import { useQuery } from "@tanstack/react-query"

import { fetchJson } from "@/lib/api/client"

import {
  fetchDashboardAlertsSummary,
  fetchDashboardMemoriesSummary,
  fetchDashboardRecentMemories,
  type DashboardRecentMemory,
} from "./dashboard-api"

export type MetricTone = "default" | "success" | "warning" | "danger"

export type DashboardMetric = {
  label: string
  value: string
  detail: string
  trend: string
  tone: MetricTone
}

export type ActivityPoint = {
  label: string
  requests: number
  extractRequests: number
  insights: number
}

export type AlertSummaryItem = {
  memoryId: string
  time: string
}

export type RuntimeSignal = {
  label: string
  value: string
  detail: string
  tone: MetricTone
}

export type MemoryActivity = {
  memoryId: string
  userId: string
  agentId: string
  createdAt: string
  requests: string
  alert: "critical" | "healthy" | "info"
  updatedAt: string
}

export type DashboardData = {
  metrics: DashboardMetric[]
  activity: ActivityPoint[]
  alerts: AlertSummaryItem[]
  runtime: RuntimeSignal[]
  recentActivity: MemoryActivity[]
}

export type DashboardAlertsSummary = Awaited<
  ReturnType<typeof fetchDashboardAlertsSummary>
>

export type DashboardRecentMemories = Awaited<
  ReturnType<typeof fetchDashboardRecentMemories>
>

export type DashboardMemoriesSummary = Awaited<
  ReturnType<typeof fetchDashboardMemoriesSummary>
>

export type AdminDashboardView = {
  totals: {
    graphEntities: number
    insights: number
    itemLinks: number
    items: number
    memoryThreads: number
    rawData: number
  }
  backlog: {
    conversationPending: number
    graphBatchRepairRequired: number
    insightUnbuilt: number
    insightUngrouped: number
    threadOutboxFailed: number
    threadOutboxPending: number
  }
  activity: {
    days: number
    insightsCreated: AdminDailyCount[]
    itemsCreated: AdminDailyCount[]
    rawDataCreated: AdminDailyCount[]
  }
  breakdown: {
    graphLinkTypes: AdminNamedCount[]
    insightTypes: AdminNamedCount[]
    itemTypes: AdminNamedCount[]
    rawDataTypes: AdminNamedCount[]
    sourceClients: AdminNamedCount[]
  }
  healthSignals: {
    graphEnabled: boolean
    retrievalGraphAssistEnabled: boolean
    threadProjectionStates: Array<{
      count: number
      state: string
    }>
  }
}

export type AdminDailyCount = {
  count: number
  date: string
}

export type AdminNamedCount = {
  count: number
  name: string
}

function formatNumber(value: number) {
  return new Intl.NumberFormat("en-US").format(value)
}

function formatDateLabel(date: string) {
  const parsed = new Date(`${date}T00:00:00Z`)

  if (Number.isNaN(parsed.getTime())) {
    return date
  }

  return parsed.toLocaleDateString("en-US", {
    day: "numeric",
    month: "short",
    timeZone: "UTC",
  })
}

function dailyCountMap(points: AdminDailyCount[]) {
  return new Map(points.map((point) => [point.date, point.count]))
}

function mapActivity(activity: AdminDashboardView["activity"]) {
  const rawDataByDate = dailyCountMap(activity.rawDataCreated)
  const itemsByDate = dailyCountMap(activity.itemsCreated)
  const insightsByDate = dailyCountMap(activity.insightsCreated)
  const dates = Array.from(
    new Set([
      ...rawDataByDate.keys(),
      ...itemsByDate.keys(),
      ...insightsByDate.keys(),
    ])
  ).sort()

  return dates.map((date) => {
    const rawDataCount = rawDataByDate.get(date) ?? 0
    const itemsCount = itemsByDate.get(date) ?? 0
    const insightsCount = insightsByDate.get(date) ?? 0

    return {
      extractRequests: itemsCount,
      insights: insightsCount,
      label: formatDateLabel(date),
      requests: rawDataCount,
    }
  })
}

function mapAlerts(alertsSummary: DashboardAlertsSummary): AlertSummaryItem[] {
  return alertsSummary.items
}

function fallbackAlerts(view: AdminDashboardView): DashboardAlertsSummary {
  const alerts: AlertSummaryItem[] = []

  if (view.backlog.threadOutboxFailed > 0) {
    alerts.push({
      memoryId: "Thread outbox failed",
      time: formatNumber(view.backlog.threadOutboxFailed),
    })
  }

  if (view.backlog.graphBatchRepairRequired > 0) {
    alerts.push({
      memoryId: "Graph repair required",
      time: formatNumber(view.backlog.graphBatchRepairRequired),
    })
  }

  if (view.backlog.insightUnbuilt > 0) {
    alerts.push({
      memoryId: "Unbuilt insights",
      time: formatNumber(view.backlog.insightUnbuilt),
    })
  }

  return {
    critical: view.backlog.threadOutboxFailed,
    items: alerts,
    warning:
      view.backlog.graphBatchRepairRequired +
      view.backlog.insightUnbuilt +
      view.backlog.threadOutboxPending,
  }
}

function mapRecentMemories(rows: DashboardRecentMemory[]): MemoryActivity[] {
  return rows.map((row) => ({
    agentId: row.agentId,
    alert:
      row.alert === "critical" || row.alert === "warning"
        ? "critical"
        : "healthy",
    createdAt: row.createdAt,
    memoryId: row.memoryId,
    requests: formatNumber(row.requests),
    updatedAt: row.updatedAt,
    userId: row.userId,
  }))
}

export function mapAdminDashboardView(
  view: AdminDashboardView,
  alertsSummary: DashboardAlertsSummary = fallbackAlerts(view),
  recentMemories: DashboardRecentMemories = [],
  memoriesSummary?: DashboardMemoriesSummary
): DashboardData {
  const activity = mapActivity(view.activity)
  const memoriesCount = memoriesSummary?.page.totalItems ?? 0

  return {
    metrics: [
      {
        detail: "Memory workspaces",
        label: "memories",
        tone: "default",
        trend: "Current",
        value: formatNumber(memoriesCount),
      },
      {
        detail: "Extracted requests",
        label: "req count extract",
        tone: "default",
        trend: "Current",
        value: formatNumber(view.totals.items),
      },
      {
        detail: "Ingested requests",
        label: "req count",
        tone: "default",
        trend: "Current",
        value: formatNumber(view.totals.rawData),
      },
    ],
    activity,
    alerts: mapAlerts(alertsSummary),
    recentActivity: mapRecentMemories(recentMemories),
    runtime: [
      {
        detail: "Item graph pipeline",
        label: "Graph",
        tone: view.healthSignals.graphEnabled ? "success" : "warning",
        value: view.healthSignals.graphEnabled ? "Enabled" : "Disabled",
      },
      {
        detail: "Retrieval graph assist",
        label: "Graph Assist",
        tone: view.healthSignals.retrievalGraphAssistEnabled
          ? "success"
          : "warning",
        value: view.healthSignals.retrievalGraphAssistEnabled
          ? "Enabled"
          : "Disabled",
      },
      {
        detail: "Thread events waiting",
        label: "Outbox",
        tone: view.backlog.threadOutboxPending > 0 ? "warning" : "success",
        value: formatNumber(view.backlog.threadOutboxPending),
      },
      {
        detail: "Projection states",
        label: "Threads",
        tone: "default",
        value: formatNumber(view.totals.memoryThreads),
      },
    ],
  }
}

export async function fetchDashboardData() {
  const [view, alertsSummary, recentMemories, memoriesSummary] =
    await Promise.all([
      fetchJson<AdminDashboardView>("/admin/v1/dashboard", {
        query: { days: 7 },
      }),
      fetchDashboardAlertsSummary(),
      fetchDashboardRecentMemories(5),
      fetchDashboardMemoriesSummary(),
    ])

  return mapAdminDashboardView(
    view,
    alertsSummary,
    recentMemories,
    memoriesSummary
  )
}

export function useDashboardData() {
  return useQuery({
    queryKey: ["dashboard", "overview"],
    queryFn: fetchDashboardData,
  })
}
