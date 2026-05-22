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

export type MemoriesPageParams = {
  page?: number
  pageSize?: number
  q?: string
  status?: string
  alertLevel?: string
  createdRange?: string
  sort?: string
}

export type MemoriesPage = {
  items: Array<{
    id: string
    name: string
    userId: string
    agentId: string
    status: string
    requests: number
    items: number
    insights: number
    rawData?: number
    alerts: {
      critical: number
      warning: number
    }
    lastActivity: string
    createdAt: string
  }>
  page: {
    page: number
    pageSize: number
    totalItems: number
    totalPages: number
    hasPrevious: boolean
    hasNext: boolean
  }
}

export function fetchMemoriesPage(params: MemoriesPageParams = {}) {
  return fetchJson<MemoriesPage>("/admin/v1/memories", { query: params })
}
