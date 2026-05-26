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

import { describe, expect, it } from "vitest"

import { mapAdminDashboardView } from "./dashboard-data"

describe("mapAdminDashboardView", () => {
  it("maps backend dashboard totals, backlog, and activity into dashboard UI data", () => {
    const data = mapAdminDashboardView(
      {
        activity: {
          days: 2,
          insightsCreated: [{ count: 1, date: "2026-05-20" }],
          itemsCreated: [
            { count: 5, date: "2026-05-20" },
            { count: 7, date: "2026-05-21" },
          ],
          rawDataCreated: [{ count: 3, date: "2026-05-21" }],
        },
        backlog: {
          conversationPending: 2,
          graphBatchRepairRequired: 1,
          insightUnbuilt: 4,
          insightUngrouped: 1,
          threadOutboxFailed: 1,
          threadOutboxPending: 3,
        },
        breakdown: {
          graphLinkTypes: [{ count: 8, name: "semantic" }],
          insightTypes: [{ count: 6, name: "profile" }],
          itemTypes: [{ count: 10, name: "FACT" }],
          rawDataTypes: [{ count: 4, name: "conversation" }],
          sourceClients: [{ count: 4, name: "slack" }],
        },
        healthSignals: {
          graphEnabled: true,
          retrievalGraphAssistEnabled: false,
          threadProjectionStates: [{ count: 2, state: "COMMITTED" }],
        },
        totals: {
          graphEntities: 9,
          insights: 6,
          itemLinks: 8,
          items: 12,
          memoryThreads: 2,
          rawData: 4,
        },
      },
      undefined,
      [
        {
          agentId: "agent-1",
          alert: "healthy",
          createdAt: "2026-05-20T08:00:00Z",
          memoryId: "u1:a1",
          requests: 4,
          updatedAt: "2026-05-21T08:00:00Z",
          userId: "u1",
        },
      ],
      {
        items: [],
        page: {
          hasNext: false,
          hasPrevious: false,
          page: 1,
          pageSize: 1,
          totalItems: 2,
          totalPages: 2,
        },
      }
    )

    expect(data.metrics).toEqual([
      expect.objectContaining({
        label: "memories",
        value: "2",
      }),
      expect.objectContaining({
        label: "req count extract",
        value: "12",
      }),
      expect.objectContaining({
        label: "req count",
        value: "4",
      }),
    ])
    expect(data.activity).toEqual([
      { extractRequests: 5, insights: 1, label: "May 20", requests: 0 },
      { extractRequests: 7, insights: 0, label: "May 21", requests: 3 },
    ])
    expect(data.alerts).toEqual(
      expect.arrayContaining([{ memoryId: "Graph repair required", time: "1" }])
    )
    expect(data.runtime).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          label: "Graph Assist",
          tone: "warning",
          value: "Disabled",
        }),
      ])
    )
    expect(data.recentActivity).toEqual([
      {
        agentId: "agent-1",
        alert: "healthy",
        createdAt: "2026-05-20T08:00:00Z",
        memoryId: "u1:a1",
        requests: "4",
        updatedAt: "2026-05-21T08:00:00Z",
        userId: "u1",
      },
    ])
  })
})
