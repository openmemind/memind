import { useQuery } from "@tanstack/react-query"

export type RuntimeMode = "balanced" | "low-latency" | "high-accuracy"
export type RetentionWindow = "30d" | "90d" | "180d" | "365d"
export type DefaultTimeRange = "24h" | "7d" | "30d"
export type DefaultMemoryView = "table" | "list" | "grid"
export type ThemePreference = "light" | "dark" | "system"

export type SettingsData = {
  workspace: {
    name: string
    environment: string
    region: string
    description: string
  }
  runtime: {
    mode: RuntimeMode
    maxConcurrentJobs: number
    autoRebuildThreads: boolean
    semanticDeduplication: boolean
    auditLogging: boolean
  }
  notifications: {
    emailAlerts: boolean
    slackAlerts: boolean
    webhookUrl: string
  }
  retention: {
    memoryItems: RetentionWindow
    traces: RetentionWindow
    redactSensitiveData: boolean
  }
  preferences: {
    defaultTimeRange: DefaultTimeRange
    defaultMemoryView: DefaultMemoryView
    theme: ThemePreference
  }
  emptyState: {
    showOnboardingTips: boolean
    autoHideEmptyCollections: boolean
  }
}

const settingsData: SettingsData = {
  workspace: {
    name: "Memind Production",
    environment: "Production",
    region: "us-east-1",
    description:
      "Primary runtime for memory extraction, retrieval, thread rebuilds, and operational API access.",
  },
  runtime: {
    mode: "balanced",
    maxConcurrentJobs: 24,
    autoRebuildThreads: true,
    semanticDeduplication: true,
    auditLogging: true,
  },
  notifications: {
    emailAlerts: true,
    slackAlerts: true,
    webhookUrl: "https://hooks.example.com/memind/runtime",
  },
  retention: {
    memoryItems: "180d",
    traces: "90d",
    redactSensitiveData: true,
  },
  preferences: {
    defaultTimeRange: "7d",
    defaultMemoryView: "table",
    theme: "system",
  },
  emptyState: {
    showOnboardingTips: true,
    autoHideEmptyCollections: false,
  },
}

async function fetchSettingsData() {
  return settingsData
}

export function useSettingsData() {
  return useQuery({
    queryKey: ["settings", "workspace"],
    queryFn: fetchSettingsData,
  })
}
