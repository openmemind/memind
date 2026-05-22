import { describe, expect, it } from "vitest"

import {
  mapAdminMemoryDashboardData,
  type AdminMemoryDashboardApiData,
} from "./memory-dashboard-data"

describe("mapAdminMemoryDashboardData", () => {
  it("maps backend admin views into the current memory dashboard UI model", () => {
    const apiData: AdminMemoryDashboardApiData = {
      dashboard: {
        activity: {
          days: 7,
          insightsCreated: [{ count: 1, date: "2026-05-20" }],
          itemsCreated: [{ count: 2, date: "2026-05-20" }],
          rawDataCreated: [{ count: 3, date: "2026-05-20" }],
        },
        backlog: {
          conversationPending: 1,
          graphBatchRepairRequired: 1,
          insightUnbuilt: 2,
          insightUngrouped: 0,
          threadOutboxFailed: 0,
          threadOutboxPending: 1,
        },
        breakdown: {
          graphLinkTypes: [{ count: 4, name: "semantic" }],
          insightTypes: [{ count: 2, name: "profile" }],
          itemTypes: [{ count: 3, name: "FACT" }],
          rawDataTypes: [{ count: 3, name: "conversation" }],
          sourceClients: [{ count: 3, name: "slack" }],
        },
        healthSignals: {
          graphEnabled: true,
          retrievalGraphAssistEnabled: true,
          threadProjectionStates: [{ count: 1, state: "committed" }],
        },
        totals: {
          graphEntities: 1,
          insights: 2,
          itemLinks: 4,
          items: 3,
          memoryThreads: 1,
          rawData: 3,
        },
      },
      graphBatches: {
        items: [
          {
            errorMessage: "schema mismatch",
            extractionBatchId: "batch_1",
            id: 1,
            state: "repair_required",
            updatedAt: "2026-05-20T12:00:00Z",
          },
        ],
        page: pageMeta(1),
      },
      graphEntities: {
        items: [
          {
            displayName: "OpenAI",
            entityKey: "org:openai",
            entityType: "ORGANIZATION",
            id: 42,
            metadata: { country: "US" },
          },
        ],
        page: pageMeta(1),
      },
      graphSummary: {
        aliasCount: 2,
        cooccurrenceCount: 3,
        entityCount: 1,
        entityCountByType: [{ count: 1, name: "ORGANIZATION" }],
        graphBatchCountByState: [{ count: 1, name: "repair_required" }],
        itemLinkCount: 4,
        itemLinkCountByType: [{ count: 4, name: "semantic" }],
        mentionCount: 5,
      },
      insights: {
        items: [
          {
            categories: ["product"],
            content: "User is evaluating API reliability.",
            groupName: "Reliability",
            insightId: 9,
            name: "Reliability concern",
            points: [{ content: "429 errors are recurring", type: "SUMMARY" }],
            scope: "USER",
            tier: "ROOT",
            type: "profile",
          },
        ],
        page: pageMeta(1),
      },
      items: {
        items: [
          {
            category: "preference",
            content: "User prefers stable APIs.",
            itemId: 123,
            observedAt: "2026-05-20T12:00:00Z",
            rawDataId: "rd_123456",
            rawDataType: "conversation",
            scope: "USER",
            sourceClient: "slack",
            type: "FACT",
            vectorId: "vec_1",
          },
        ],
        page: pageMeta(1),
      },
      rawData: {
        items: [
          {
            caption: "Slack thread about API reliability",
            captionVectorId: "vec_caption",
            createdAt: "2026-05-20T12:00:00Z",
            metadata: { channel: "support" },
            rawDataId: "rd_123456",
            segment: { text: "429 errors" },
            sourceClient: "slack",
            type: "conversation",
          },
        ],
        page: pageMeta(1),
      },
      threadStatus: {
        failedCount: 0,
        materializationPolicyVersion: "v1",
        pendingCount: 1,
        projectionState: "committed",
        rebuildInProgress: false,
        updatedAt: "2026-05-20T12:00:00Z",
      },
      threads: {
        items: [
          {
            displayLabel: "API reliability",
            eventCount: 3,
            headline: "User is tracking API errors",
            lifecycleStatus: "active",
            memberCount: 2,
            snapshotJson: { latestUpdate: "429 errors are recurring" },
            threadKey: "topic:api",
          },
        ],
        page: pageMeta(1),
      },
    }

    const data = mapAdminMemoryDashboardData("user_1:agent_1", apiData)

    expect(data.id).toBe("user_1:agent_1")
    expect(data.rawData.records[0]).toMatchObject({
      id: "rd_123456",
      metadataJson: expect.stringContaining('"channel"'),
      segmentJson: expect.stringContaining('"text"'),
      source: "slack",
      vectorStatus: "Vectorized",
    })
    expect(data.items.records[0]).toMatchObject({
      id: "123",
      sourceRawDataShortId: "rd_123...",
      threadCount: 0,
      vectorStatus: "Vectorized",
    })
    expect(data.graph.nodes[0]).toMatchObject({
      icon: "organization",
      id: "42",
      label: "OpenAI",
      type: "Organization",
    })
    expect(data.threads.storylines[0]).toMatchObject({
      detail: expect.objectContaining({
        narrative: "429 errors are recurring",
      }),
      key: "topic:api",
      status: "ACTIVE",
    })
    expect(data.insights.selectedDetail).toMatchObject({
      id: "9",
      points: ["429 errors are recurring"],
    })
  })
})

function pageMeta(totalItems: number) {
  return {
    hasNext: false,
    hasPrevious: false,
    page: 1,
    pageSize: 20,
    totalItems,
    totalPages: 1,
  }
}
