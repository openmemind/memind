import { useQuery } from "@tanstack/react-query"

export type MemoryStatus = "active" | "idle" | "warning" | "error"

export type MemorySummary = {
  label: string
  value: string
  detail: string
  tone: "default" | "success" | "warning" | "danger"
}

export type MemoryWorkspace = {
  id: string
  name: string
  userId: string
  agentId: string
  status: MemoryStatus
  requests: string
  items: string
  insights: string
  alerts: {
    critical: number
    warning: number
  }
  lastActivity: string
  createdAt: string
}

export type MemoriesData = {
  summaries: MemorySummary[]
  workspaces: MemoryWorkspace[]
}

const memoriesData: MemoriesData = {
  summaries: [
    {
      label: "Total Memories",
      value: "2,840",
      detail: "Across all collections",
      tone: "default",
    },
    {
      label: "Active Memories",
      value: "1,402",
      detail: "49.3% total utilization",
      tone: "success",
    },
    {
      label: "With Alerts",
      value: "14",
      detail: "3 critical alerts",
      tone: "danger",
    },
    {
      label: "New Memories",
      value: "156",
      detail: "Created in the last 24h",
      tone: "warning",
    },
  ],
  workspaces: [
    {
      id: "MEM-8429-XQ",
      name: "Customer-Core-01",
      userId: "u_alex_chen",
      agentId: "Agent-L4-Prime",
      status: "active",
      requests: "1.2k",
      items: "429",
      insights: "12",
      alerts: { critical: 0, warning: 0 },
      lastActivity: "2m ago",
      createdAt: "May 18, 2026",
    },
    {
      id: "MEM-3310-ZZ",
      name: "Support-Context-B",
      userId: "u_sarah_j",
      agentId: "Agent-Zeta",
      status: "error",
      requests: "892",
      items: "15",
      insights: "2",
      alerts: { critical: 1, warning: 2 },
      lastActivity: "1h ago",
      createdAt: "May 17, 2026",
    },
    {
      id: "MEM-1004-KL",
      name: "Analysis-Cluster-9",
      userId: "u_dev_team",
      agentId: "DataProcessor",
      status: "idle",
      requests: "4.1k",
      items: "2.1k",
      insights: "84",
      alerts: { critical: 0, warning: 0 },
      lastActivity: "14h ago",
      createdAt: "May 14, 2026",
    },
    {
      id: "MEM-5928-RP",
      name: "Research-Assistant-A",
      userId: "u_research_lab",
      agentId: "Retriever-Pro",
      status: "warning",
      requests: "2.7k",
      items: "864",
      insights: "31",
      alerts: { critical: 0, warning: 4 },
      lastActivity: "1d ago",
      createdAt: "May 11, 2026",
    },
  ],
}

async function fetchMemoriesData() {
  return memoriesData
}

export function useMemoriesData() {
  return useQuery({
    queryKey: ["memories", "list"],
    queryFn: fetchMemoriesData,
  })
}
