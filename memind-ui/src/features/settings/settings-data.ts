import { useQuery } from "@tanstack/react-query"

import {
  fetchMemoryOptions,
  fetchUiPreferences,
  type MemoryOptionsResponse,
} from "./settings-api"

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
  memoryOptions?: MemoryOptionsResponse
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
  const [memoryOptions, uiPreferences] = await Promise.all([
    fetchMemoryOptions(),
    fetchUiPreferences(),
  ])

  return {
    ...settingsData,
    emptyState: {
      autoHideEmptyCollections: uiPreferences.autoHideEmptyCollections,
      showOnboardingTips: uiPreferences.showOnboardingTips,
    },
    memoryOptions,
    preferences: {
      defaultMemoryView: uiPreferences.defaultMemoryView,
      defaultTimeRange: uiPreferences.defaultTimeRange,
      theme: uiPreferences.theme,
    },
  }
}

export function useSettingsData() {
  return useQuery({
    queryKey: ["settings", "workspace"],
    queryFn: fetchSettingsData,
  })
}
