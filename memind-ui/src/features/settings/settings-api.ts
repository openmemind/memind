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

import { fetchJson } from "@/lib/api/client"

export type MemoryOptionItem = {
  constraints?: Record<string, unknown>
  defaultValue?: unknown
  description?: string
  key: string
  type?: string
  value: unknown
}

export type MemoryOptionsConfig = Record<string, MemoryOptionItem[]>

export type MemoryOptionsResponse = {
  config: MemoryOptionsConfig
  version: number
}

export type MemoryOptionsUpdateRequest = {
  config: MemoryOptionsConfig
  expectedVersion: number
}

export type UiPreferences = {
  defaultTimeRange: "24h" | "7d" | "30d"
  defaultMemoryView: "table" | "list" | "grid"
  theme: "light" | "dark" | "system"
  showOnboardingTips: boolean
  autoHideEmptyCollections: boolean
}

export function fetchUiPreferences() {
  return fetchJson<UiPreferences>("/admin/v1/settings/ui-preferences")
}

export function updateUiPreferences(preferences: UiPreferences) {
  return fetchJson<UiPreferences>("/admin/v1/settings/ui-preferences", {
    body: preferences,
    method: "PUT",
  })
}

export function fetchMemoryOptions() {
  return fetchJson<MemoryOptionsResponse>("/admin/v1/config/memory-options")
}

export function updateMemoryOptions(request: MemoryOptionsUpdateRequest) {
  return fetchJson<MemoryOptionsResponse>("/admin/v1/config/memory-options", {
    body: request,
    method: "PUT",
  })
}
