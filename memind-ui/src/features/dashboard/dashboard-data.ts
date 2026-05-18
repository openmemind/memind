import { useQuery } from "@tanstack/react-query"

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
  total: number
  success: number
  failed: number
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

const dashboardData: DashboardData = {
  metrics: [
    {
      label: "Total Memories",
      value: "1,284",
      detail: "Tracked memory scopes",
      trend: "+12%",
      tone: "success",
    },
    {
      label: "Total Requests",
      value: "45.2k",
      detail: "3.2k today",
      trend: "+3.2k today",
      tone: "default",
    },
    {
      label: "Failed Requests",
      value: "12",
      detail: "Down 5%",
      trend: "-5%",
      tone: "success",
    },
    {
      label: "Active Alerts",
      value: "3",
      detail: "Critical",
      trend: "Critical",
      tone: "danger",
    },
    {
      label: "New Memories",
      value: "48",
      detail: "Last 24h",
      trend: "Last 24h",
      tone: "default",
    },
  ],
  activity: [
    { label: "12:00 AM", total: 420, success: 410, failed: 10 },
    { label: "06:00 AM", total: 620, success: 610, failed: 12 },
    { label: "12:00 PM", total: 540, success: 533, failed: 7 },
    { label: "06:00 PM", total: 760, success: 748, failed: 12 },
    { label: "11:59 PM", total: 920, success: 908, failed: 12 },
  ],
  alerts: [
    { memoryId: "mem_f293_1a", time: "2m ago" },
    { memoryId: "mem_a812_4d", time: "14m ago" },
    { memoryId: "mem_c009_2b", time: "1h ago" },
  ],
  runtime: [
    {
      label: "Runtime",
      value: "Available",
      detail: "Memory runtime ready",
      tone: "success",
    },
    {
      label: "P95 Latency",
      value: "242 ms",
      detail: "Retrieval response time",
      tone: "success",
    },
    {
      label: "Outbox",
      value: "18",
      detail: "Thread events waiting",
      tone: "warning",
    },
    {
      label: "Error Rate",
      value: "0.18%",
      detail: "Last 24 hours",
      tone: "default",
    },
  ],
  recentActivity: [
    {
      memoryId: "mem_f293_1a",
      userId: "alex.h@corp.ai",
      agentId: "Customer Support v4",
      createdAt: "2026-05-18",
      requests: "1,402",
      alert: "critical",
      updatedAt: "2m ago",
    },
    {
      memoryId: "mem_a812_4d",
      userId: "sarah.j@tech.io",
      agentId: "Personal Assistant",
      createdAt: "2026-05-18",
      requests: "842",
      alert: "healthy",
      updatedAt: "14m ago",
    },
    {
      memoryId: "mem_c009_2b",
      userId: "dev_ops@node.net",
      agentId: "Log Analyzer",
      createdAt: "2026-05-17",
      requests: "12.5k",
      alert: "healthy",
      updatedAt: "1h ago",
    },
    {
      memoryId: "mem_z441_9x",
      userId: "finance@capital.com",
      agentId: "Predictive Model B",
      createdAt: "2026-05-17",
      requests: "3,120",
      alert: "info",
      updatedAt: "3h ago",
    },
  ],
}

async function fetchDashboardData() {
  return dashboardData
}

export function useDashboardData() {
  return useQuery({
    queryKey: ["dashboard", "overview"],
    queryFn: fetchDashboardData,
  })
}
