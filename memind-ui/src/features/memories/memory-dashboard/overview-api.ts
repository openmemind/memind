import { fetchJson } from "@/lib/api/client"

export type MemoryActivityEvent = {
  title: string
  detail: string
  time: string
  tone: "default" | "muted"
}

export function fetchMemoryRecentActivity(memoryId: string) {
  return fetchJson<MemoryActivityEvent[]>(
    `/admin/v1/memories/${encodeURIComponent(memoryId)}/activity`
  )
}

export function forceMemorySnapshot(memoryId: string) {
  return fetchJson<{ memoryId: string; status: string }>(
    `/admin/v1/memories/${encodeURIComponent(memoryId)}/snapshot`,
    { method: "POST" }
  )
}
