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

import { useQuery } from "@tanstack/react-query"

import { fetchJson } from "@/lib/api/client"
import type { PageResult } from "@/lib/api/pagination"
import type { AdminDashboardView } from "@/features/dashboard/dashboard-data"

import {
  fetchAdminGraphBatchesPage,
  fetchAdminGraphEntitiesPage,
  fetchAdminGraphSummary,
  type AdminGraphBatchView,
  type AdminGraphEntityView,
  type AdminGraphSummaryView,
} from "./graph-api"
import {
  fetchAdminInsightsPage,
  type AdminInsightView,
} from "./insights-api"
import { fetchAdminItemsPage, type AdminItemView } from "./items-api"
import { fetchAdminRawDataPage, type AdminRawDataView } from "./raw-data-api"
import {
  fetchAdminMemoryThreadsPage,
  fetchAdminMemoryThreadStatus,
  type AdminMemoryThreadStatusView,
  type AdminMemoryThreadView,
} from "./threads-api"

export type MemoryDashboardMetric = {
  label: string
  value: string
  detail: string
  tone?: "default" | "danger"
}

export type MemoryPipelineStage = {
  label: string
  detail: string
  status: "complete" | "warning" | "pending"
}

export type MemoryAttentionItem = {
  label: string
  count: number
  tone?: "default" | "danger"
}

export type MemoryDistributionItem = {
  label: string
  value: number
}

export type MemoryCategoryItem = {
  label: string
  value: string
}

export type MemoryActivityItem = {
  title: string
  detail: string
  time: string
  tone: "default" | "muted"
}

export type MemoryRawDataSummary = {
  label: string
  value: string
  detail?: string
  trend?: string
}

export type MemoryRawDataRecord = {
  id: string
  shortId: string
  type: string
  typeLabel: string
  caption: string
  source: string
  vectorStatus: string
  createdAt: string
  segmentJson: string
  metadataJson: string
  associatedItems: Array<{
    label: string
    kind: string
  }>
}

export type MemoryRawDataPageData = {
  summary: MemoryRawDataSummary[]
  records: MemoryRawDataRecord[]
  paginationLabel: string
  pageLabel: string
}

export type MemoryItemSummary = {
  label: string
  value: string
  detail?: string
}

export type MemoryItemRecord = {
  id: string
  shortId: string
  content: string
  scope: "USER" | "AGENT"
  category: string
  type: "FACT" | "FORESIGHT"
  sourceRawDataId: string
  sourceRawDataShortId: string
  sourceType: string
  sourceIntegration: string
  vectorStatus: string
  vectorId: string
  contentHash: string
  observedAt: string
  observedAtFull: string
  occurredAt: string
  threadCount: number
  relatedThreads: Array<{
    id: string
    status: string
    updatedAt: string
  }>
  metadataJson: string
}

export type MemoryItemsPageData = {
  summary: MemoryItemSummary[]
  records: MemoryItemRecord[]
  paginationLabel: string
}

export type MemoryGraphSummary = {
  label: string
  value: string
  trend?: string
  tone?: "default" | "danger"
}

export type MemoryGraphNode = {
  id: string
  label: string
  type: "Person" | "Organization"
  icon: "person" | "organization"
  x: string
  y: string
  size: "md" | "lg"
}

export type MemoryGraphEntityDetail = {
  label: string
  entityId: string
  type: string
  aliases: string[]
  cooccurrences: Array<{
    label: string
    strength: number
  }>
  metadataJson: string
  mentionCount: number
}

export type MemoryGraphDiagnostic = {
  id: string
  state: "Repair Required" | "Completed"
  errorMessage: string
  lastAttempt: string
}

export type MemoryGraphPageData = {
  summary: MemoryGraphSummary[]
  nodes: MemoryGraphNode[]
  edges: Array<{
    from: string
    to: string
  }>
  selectedEntity: MemoryGraphEntityDetail
  diagnostics: MemoryGraphDiagnostic[]
}

export type MemoryThreadStatusSummary = {
  label: string
  value: string
  tone?: "default" | "muted"
}

export type MemoryThreadStoryline = {
  key: string
  title: string
  description: string
  status: "ACTIVE" | "DORMANT" | "CLOSED"
  category: string
  memberCount: number
  lastEvent: string
  detail: MemoryThreadDetail
}

export type MemoryThreadTimelineItem = {
  id: string
  role: "PRIMARY" | "SUPPORTING"
  timestamp: string
  content: string
  source: string
}

export type MemoryThreadDetail = {
  key: string
  title: string
  category: string
  status: "ACTIVE" | "DORMANT" | "CLOSED"
  narrative: string
  timeline: MemoryThreadTimelineItem[]
}

export type MemoryThreadsPageData = {
  projection: {
    status: string
    pending: number
    failed: number
    projectionId: string
    updatedAt: string
  }
  summary: MemoryThreadStatusSummary[]
  storylines: MemoryThreadStoryline[]
}

export type MemoryInsightHierarchyGroup = {
  label: string
  count: number
  items?: string[]
}

export type MemoryInsightNode = {
  id: string
  label: string
  title: string
  description: string
  category: string
  kind: "root" | "branch" | "leaf"
  children?: string
}

export type MemoryInsightDetail = {
  id: string
  kind: string
  title: string
  description: string
  points: string[]
  metadata: Array<{
    label: string
    value: string
  }>
  categories: string[]
}

export type MemoryInsightsPageData = {
  hierarchy: MemoryInsightHierarchyGroup[]
  root: MemoryInsightNode
  branches: Array<
    MemoryInsightNode & {
      selected?: boolean
      leaves: MemoryInsightNode[]
    }
  >
  roots?: Array<
    MemoryInsightNode & {
      branches: Array<
        MemoryInsightNode & {
          selected?: boolean
          leaves: MemoryInsightNode[]
        }
      >
    }
  >
  selectedDetail: MemoryInsightDetail
}

export type MemoryDashboardData = {
  id: string
  name: string
  status: string
  userId: string
  agentId: string
  lastActivity: string
  identity: {
    runtimeId: string
    agent: string
  }
  metrics: MemoryDashboardMetric[]
  pipeline: MemoryPipelineStage[]
  attention: MemoryAttentionItem[]
  rawDataByType: MemoryDistributionItem[]
  itemsByCategory: MemoryCategoryItem[]
  activity: MemoryActivityItem[]
  rawData: MemoryRawDataPageData
  items: MemoryItemsPageData
  graph: MemoryGraphPageData
  threads: MemoryThreadsPageData
  insights: MemoryInsightsPageData
}

export type AdminMemoryDashboardApiData = {
  dashboard: AdminDashboardView
  graphBatches: PageResult<AdminGraphBatchView>
  graphEntities: PageResult<AdminGraphEntityView>
  graphSummary: AdminGraphSummaryView
  insights: PageResult<AdminInsightView>
  items: PageResult<AdminItemView>
  rawData: PageResult<AdminRawDataView>
  threadStatus: AdminMemoryThreadStatusView
  threads: PageResult<AdminMemoryThreadView>
}

const dashboardByMemoryId: Record<string, MemoryDashboardData> = {
  "MEM-8429-XQ": {
    id: "mem_8f2a9c1d",
    name: "Memory-772",
    status: "Active",
    userId: "user_441",
    agentId: "agent_alpha",
    lastActivity: "5m ago",
    identity: {
      runtimeId: "mem_8f2a9c1d",
      agent: "nexus-v1",
    },
    metrics: [
      {
        label: "Raw Data",
        value: "458MB",
        detail: "total",
      },
      {
        label: "Memory Items",
        value: "3,412",
        detail: "entities",
      },
      {
        label: "Insights",
        value: "124",
        detail: "patterns",
      },
      {
        label: "Threads",
        value: "18",
        detail: "contexts",
      },
      {
        label: "Alerts",
        value: "2",
        detail: "attention",
        tone: "danger",
      },
    ],
    pipeline: [
      {
        label: "Raw Data",
        detail: "1.2k Ingested",
        status: "complete",
      },
      {
        label: "Items",
        detail: "3.4k Extracted",
        status: "complete",
      },
      {
        label: "Graph / Threads",
        detail: "18 Retry (3 Failed)",
        status: "warning",
      },
      {
        label: "Insights",
        detail: "12 Pending",
        status: "pending",
      },
    ],
    attention: [
      {
        label: "Pending conversations",
        count: 3,
      },
      {
        label: "Unbuilt insights",
        count: 12,
      },
      {
        label: "Graph repair required",
        count: 1,
        tone: "danger",
      },
    ],
    rawDataByType: [
      {
        label: "Text Content",
        value: 70,
      },
      {
        label: "Visual Artifacts",
        value: 20,
      },
      {
        label: "Audio Streams",
        value: 10,
      },
    ],
    itemsByCategory: [
      {
        label: "Person",
        value: "1.2k",
      },
      {
        label: "Place",
        value: "450",
      },
      {
        label: "Event",
        value: "892",
      },
      {
        label: "Concept",
        value: "98",
      },
    ],
    activity: [
      {
        title: "Conversation committed",
        detail: "Processed 14 messages into thread thr_a92.",
        time: "2m ago",
        tone: "default",
      },
      {
        title: "Items extracted",
        detail: 'Found 4 new entities: "Quantum Core", "Station Delta".',
        time: "12m ago",
        tone: "default",
      },
      {
        title: "Graph entity linked",
        detail: "Established 12 new relationships in knowledge graph.",
        time: "45m ago",
        tone: "default",
      },
      {
        title: "Insight built",
        detail: "Generated a purchase intent pattern from recent sessions.",
        time: "1h ago",
        tone: "muted",
      },
    ],
    rawData: {
      summary: [
        {
          label: "Total Records",
          value: "4.2k",
        },
        {
          label: "Recent (24h)",
          value: "+124",
          trend: "+12%",
        },
        {
          label: "With Caption",
          value: "98%",
        },
        {
          label: "Vectorized",
          value: "100%",
        },
        {
          label: "Linked Items",
          value: "3.1k",
        },
      ],
      paginationLabel: "Showing 1-15 of 4,218 records",
      pageLabel: "Page 1 of 282",
      records: [
        {
          id: "rd_7a2b9c1d-84e1-4f02-9844-01938ae2",
          shortId: "rd_7a2...",
          type: "conversation",
          typeLabel: "CONV",
          caption:
            "User inquired about API rate limits for the Nexus endpoint...",
          source: "Slack (Prod)",
          vectorStatus: "Live",
          createdAt: "2m ago",
          segmentJson: `{
  "start": 1709214000,
  "end": 1709214015,
  "tokens": 42,
  "confidence": 0.992,
  "language": "en-US",
  "speaker_id": "user_491"
}`,
          metadataJson: `{
  "source": "slack_integration",
  "channel_id": "C0523KL2",
  "client_os": "macos_14.2",
  "tags": ["critical", "api-errors"],
  "vector_id": "vec_8213-90"
}`,
          associatedItems: [
            {
              label: "API Documentation Snippet",
              kind: "article",
            },
            {
              label: "User Support Thread #902",
              kind: "thread",
            },
          ],
        },
        {
          id: "rd_8b1f0a62-83bd-44aa-8f89-13fc77b1e296",
          shortId: "rd_8b1...",
          type: "document",
          typeLabel: "DOC",
          caption: "Architecture schema for distributed memory nodes v2.4...",
          source: "G-Drive",
          vectorStatus: "Live",
          createdAt: "14m ago",
          segmentJson: `{
  "page": 12,
  "section": "vector_commit",
  "tokens": 186,
  "confidence": 0.984
}`,
          metadataJson: `{
  "source": "drive_document",
  "file_id": "doc_2918",
  "owner": "platform-team",
  "vector_id": "vec_9241-12"
}`,
          associatedItems: [
            {
              label: "Distributed Memory Schema",
              kind: "article",
            },
          ],
        },
        {
          id: "rd_4c9ae720-1a37-4d49-9b54-cccb9b5a9010",
          shortId: "rd_4c9...",
          type: "audio",
          typeLabel: "AUDIO",
          caption:
            "Meeting transcript from Sync Call #42 regarding Q3 metrics...",
          source: "Zoom",
          vectorStatus: "Live",
          createdAt: "1h ago",
          segmentJson: `{
  "start": 1709209000,
  "end": 1709209360,
  "tokens": 512,
  "speaker_count": 5
}`,
          metadataJson: `{
  "source": "zoom_transcript",
  "meeting_id": "sync_42",
  "tags": ["metrics", "planning"],
  "vector_id": "vec_3912-71"
}`,
          associatedItems: [
            {
              label: "Q3 Metrics Planning",
              kind: "thread",
            },
          ],
        },
        {
          id: "rd_2e4fa0c7-54f9-4a4b-a2a4-d26a14b02f13",
          shortId: "rd_2e4...",
          type: "tool",
          typeLabel: "TOOL",
          caption:
            "get_weather called for coordinates 34.0522 N, 118.2437 W...",
          source: "Agent-API",
          vectorStatus: "Live",
          createdAt: "2h ago",
          segmentJson: `{
  "tool": "get_weather",
  "latency_ms": 221,
  "tokens": 24,
  "result": "cached"
}`,
          metadataJson: `{
  "source": "agent_tool_call",
  "agent": "nexus-v1",
  "environment": "production",
  "vector_id": "vec_1880-44"
}`,
          associatedItems: [
            {
              label: "Weather Tool Usage",
              kind: "article",
            },
          ],
        },
      ],
    },
    items: {
      summary: [
        {
          label: "Total Items",
          value: "3,412",
        },
        {
          label: "USER Scope",
          value: "2.1k",
        },
        {
          label: "AGENT Scope",
          value: "1.3k",
        },
        {
          label: "FACT Items",
          value: "2.8k",
        },
        {
          label: "FORESIGHT Items",
          value: "612",
        },
        {
          label: "Vectorized",
          value: "3,412",
          detail: "100%",
        },
      ],
      paginationLabel: "Showing 1-25 of 3,412 items",
      records: [
        {
          id: "mi_2b9c7a1d",
          shortId: "mi_2b9c7...",
          content: "User prefers dark mode for all dashboard interfaces.",
          scope: "USER",
          category: "behavior",
          type: "FACT",
          sourceRawDataId: "rd_7a2b9c1d-84e1-4f02-9844-01938ae2",
          sourceRawDataShortId: "rd_7a2b...",
          sourceType: "conversation",
          sourceIntegration: "slack_integration",
          vectorStatus: "Vectorized",
          vectorId: "vec_8213_f9a1_223b_091c",
          contentHash: "h_9f2e...88a2",
          observedAt: "2m ago",
          observedAtFull: "2024-05-20 14:30:05",
          occurredAt: "-",
          threadCount: 3,
          relatedThreads: [
            {
              id: "thr_a92",
              status: "Active",
              updatedAt: "2m ago",
            },
          ],
          metadataJson: `{
  "confidence": 0.98,
  "inference_model": "gpt-4-turbo",
  "entities": ["user", "dark mode"],
  "sentiment": "neutral",
  "persistence": "permanent"
}`,
        },
        {
          id: "mi_5x1r2d6p",
          shortId: "mi_5x1r2...",
          content:
            "User frequently analyzes Q3 revenue spikes via SQL workbench.",
          scope: "USER",
          category: "behavior",
          type: "FACT",
          sourceRawDataId: "rd_9l3o0a62-83bd-44aa-8f89-13fc77b1e296",
          sourceRawDataShortId: "rd_9l3o...",
          sourceType: "document",
          sourceIntegration: "drive_document",
          vectorStatus: "Vectorized",
          vectorId: "vec_9241_a031_551d_0ac2",
          contentHash: "h_0a44...31bd",
          observedAt: "15m ago",
          observedAtFull: "2024-05-20 14:17:21",
          occurredAt: "-",
          threadCount: 1,
          relatedThreads: [
            {
              id: "thr_d41",
              status: "Idle",
              updatedAt: "15m ago",
            },
          ],
          metadataJson: `{
  "confidence": 0.94,
  "inference_model": "gpt-4-turbo",
  "entities": ["Q3 revenue", "SQL workbench"],
  "sentiment": "neutral",
  "persistence": "permanent"
}`,
        },
        {
          id: "mi_k8p4m0z9",
          shortId: "mi_k8p4m...",
          content:
            "Agent should prioritize latency over token efficiency for mobile clients.",
          scope: "AGENT",
          category: "directive",
          type: "FORESIGHT",
          sourceRawDataId: "rd_0f5g6720-1a37-4d49-9b54-cccb9b5a9010",
          sourceRawDataShortId: "rd_0f5g...",
          sourceType: "audio",
          sourceIntegration: "zoom_transcript",
          vectorStatus: "Vectorized",
          vectorId: "vec_3912_b89d_224a_77c0",
          contentHash: "h_8c92...124e",
          observedAt: "1h ago",
          observedAtFull: "2024-05-20 13:32:44",
          occurredAt: "-",
          threadCount: 5,
          relatedThreads: [
            {
              id: "thr_latency",
              status: "Active",
              updatedAt: "1h ago",
            },
          ],
          metadataJson: `{
  "confidence": 0.91,
  "inference_model": "gpt-4-turbo",
  "entities": ["latency", "mobile clients"],
  "sentiment": "neutral",
  "persistence": "session"
}`,
        },
      ],
    },
    graph: {
      summary: [
        {
          label: "Entities",
          value: "1,284",
          trend: "+12",
        },
        {
          label: "Aliases",
          value: "3,492",
        },
        {
          label: "Mentions",
          value: "12.5k",
        },
        {
          label: "Item Links",
          value: "8,103",
        },
        {
          label: "Cooccurrences",
          value: "542",
        },
        {
          label: "Batches Needing Repair",
          value: "7",
          tone: "danger",
        },
      ],
      nodes: [
        {
          id: "emusk_772",
          label: "Elon Musk",
          type: "Person",
          icon: "person",
          x: "34%",
          y: "30%",
          size: "md",
        },
        {
          id: "spacex_219",
          label: "SpaceX",
          type: "Organization",
          icon: "organization",
          x: "54%",
          y: "55%",
          size: "lg",
        },
      ],
      edges: [
        {
          from: "emusk_772",
          to: "spacex_219",
        },
      ],
      selectedEntity: {
        label: "Elon Musk",
        entityId: "emusk_772",
        type: "PERSON",
        aliases: ["Elon", "Musk", "Technoking"],
        cooccurrences: [
          {
            label: "SpaceX",
            strength: 82,
          },
          {
            label: "Tesla",
            strength: 61,
          },
        ],
        metadataJson: `{
  "last_extracted": "2023-11-20",
  "confidence": 0.982,
  "sources": ["twitter", "news_api"],
  "tags": ["tech", "exec"]
}`,
        mentionCount: 142,
      },
      diagnostics: [
        {
          id: "BATCH_EXT_9921",
          state: "Repair Required",
          errorMessage:
            "Schema mismatch on 'birth_date' attribute during extraction pipeline.",
          lastAttempt: "2023-11-20 14:22:11",
        },
        {
          id: "BATCH_EXT_9918",
          state: "Repair Required",
          errorMessage:
            "Circular alias reference detected: Entity 'X' refers to 'Y' which refers to 'X'.",
          lastAttempt: "2023-11-19 09:15:45",
        },
        {
          id: "BATCH_EXT_8812",
          state: "Completed",
          errorMessage: "-",
          lastAttempt: "2023-11-18 22:40:02",
        },
      ],
    },
    threads: {
      projection: {
        status: "COMMITTED",
        pending: 0,
        failed: 0,
        projectionId: "mi_8f2x...k82",
        updatedAt: "2024-05-21",
      },
      summary: [
        {
          label: "Active Threads",
          value: "12",
        },
        {
          label: "Dormant",
          value: "4",
          tone: "muted",
        },
        {
          label: "Closed",
          value: "2",
          tone: "muted",
        },
        {
          label: "Total Members",
          value: "156",
        },
      ],
      storylines: [
        {
          key: "thr_k9x_02931",
          title: "Career transition toward backend architecture",
          description:
            "User repeatedly discusses Java backend leadership and distributed systems.",
          status: "ACTIVE",
          category: "career",
          memberCount: 18,
          lastEvent: "12 min ago",
          detail: {
            key: "thr_k9x_02931",
            title: "Career transition toward backend architecture",
            category: "career",
            status: "ACTIVE",
            narrative:
              "The user has shifted focus from general full-stack engineering to deep specialization in backend systems. Key themes include architectural design patterns, distributed database management, and leadership roles within engineering teams. The thread shows increasing complexity in technical inquiry over the last 30 days.",
            timeline: [
              {
                id: "mi_02x_982",
                role: "PRIMARY",
                timestamp: "May 21, 2024 - 14:22",
                content:
                  '"I\'m considering taking a Lead Backend role at a series B startup. What should I prioritize in my first 90 days?"',
                source: "User Interview",
              },
              {
                id: "mi_02x_954",
                role: "SUPPORTING",
                timestamp: "May 18, 2024 - 09:15",
                content:
                  'Note on personal reading list: "Designing Data-Intensive Applications" by Martin Kleppmann.',
                source: "Web Clipper",
              },
              {
                id: "mi_02x_812",
                role: "SUPPORTING",
                timestamp: "May 12, 2024 - 18:40",
                content:
                  '"Thinking about the switch from Node.js to Go for high-throughput services."',
                source: "Mobile App",
              },
            ],
          },
        },
        {
          key: "thr_health_2218",
          title: "Health and Fitness Goals 2024",
          description:
            "Monitoring consistency in morning workouts and dietary preferences.",
          status: "ACTIVE",
          category: "goal",
          memberCount: 8,
          lastEvent: "2 hours ago",
          detail: {
            key: "thr_health_2218",
            title: "Health and Fitness Goals 2024",
            category: "goal",
            status: "ACTIVE",
            narrative:
              "The user is tracking consistency across morning workouts, recovery windows, and practical dietary preferences. Recent items show a shift from broad fitness intent to measurable routines and meal-planning constraints.",
            timeline: [
              {
                id: "mi_fit_204",
                role: "PRIMARY",
                timestamp: "May 21, 2024 - 08:10",
                content:
                  '"I want to keep workouts short enough to finish before the first standup, ideally under 35 minutes."',
                source: "Mobile App",
              },
              {
                id: "mi_fit_188",
                role: "SUPPORTING",
                timestamp: "May 19, 2024 - 19:42",
                content:
                  "Prefers high-protein dinners that can be prepared in one pan on weeknights.",
                source: "Meal Planner",
              },
              {
                id: "mi_fit_141",
                role: "SUPPORTING",
                timestamp: "May 14, 2024 - 07:28",
                content:
                  "Skipped morning training after late deployment; user wants a recovery fallback routine.",
                source: "Calendar Note",
              },
            ],
          },
        },
        {
          key: "thr_home_8031",
          title: "Home Renovation Logistics",
          description:
            "Tracking contractor quotes and interior design mood boards for kitchen.",
          status: "DORMANT",
          category: "home",
          memberCount: 24,
          lastEvent: "1 day ago",
          detail: {
            key: "thr_home_8031",
            title: "Home Renovation Logistics",
            category: "home",
            status: "DORMANT",
            narrative:
              "The renovation thread groups contractor quotes, kitchen layout decisions, and mood-board references. Activity has cooled after the user narrowed the scope to cabinet finishes and appliance lead times.",
            timeline: [
              {
                id: "mi_home_771",
                role: "PRIMARY",
                timestamp: "May 20, 2024 - 16:05",
                content:
                  "Contractor quote B is preferred if cabinet installation can start before the flooring work.",
                source: "Email",
              },
              {
                id: "mi_home_730",
                role: "SUPPORTING",
                timestamp: "May 17, 2024 - 12:30",
                content:
                  "Kitchen mood board favors matte black pulls, warm white tile, and concealed under-cabinet lighting.",
                source: "G-Drive",
              },
              {
                id: "mi_home_702",
                role: "SUPPORTING",
                timestamp: "May 11, 2024 - 10:12",
                content:
                  "Appliance lead times may push the final inspection into late June.",
                source: "Web Clipper",
              },
            ],
          },
        },
      ],
    },
    insights: {
      hierarchy: [
        {
          label: "Roots",
          count: 3,
          items: [
            "Platform Scalability...",
            "Market Expansion...",
            "User Retention...",
          ],
        },
        {
          label: "Branches",
          count: 12,
        },
        {
          label: "Leaves",
          count: 127,
        },
      ],
      root: {
        id: "INS-R-001",
        label: "Root Insight",
        title: "Platform Scalability Thesis",
        description:
          "Comprehensive synthesis of infrastructure constraints and growth projection modeling based on current latency trends.",
        category: "Strategic",
        kind: "root",
        children: "12 Branches",
      },
      branches: [
        {
          id: "INS-B-118",
          label: "Architecture",
          title: "Architecture Bottlenecks",
          description: "Summarizing database and networking latency issues.",
          category: "Architecture",
          kind: "branch",
          leaves: [
            {
              id: "INS-L-104",
              label: "Observation",
              title: "V8 Engine JIT Delay",
              description:
                "Observation #104: Heavy object mutation preventing optimization.",
              category: "Observation",
              kind: "leaf",
            },
            {
              id: "INS-L-102",
              label: "Bug",
              title: "Canvas Overflow #102",
              description:
                "Interaction layer failing to clear bounding boxes on zoom.",
              category: "Bug",
              kind: "leaf",
            },
          ],
        },
        {
          id: "INS-B-442",
          label: "Frontend",
          title: "Frontend Performance",
          description: "Analysis of rendering cycles and memory management.",
          category: "Frontend",
          kind: "branch",
          selected: true,
          leaves: [
            {
              id: "INS-L-331",
              label: "Metric",
              title: "DOM Depth Nesting",
              description:
                "Sidebar component tree exceeds 12 levels of nesting.",
              category: "Metric",
              kind: "leaf",
            },
          ],
        },
      ],
      selectedDetail: {
        id: "INS-B-442",
        kind: "Branch",
        title: "Frontend Performance Analysis",
        description:
          "This branch insight identifies critical performance degradation caused by inefficient rendering cycles in the primary dashboard view.",
        points: [
          "Rendering latency spikes observed during high-frequency WebSocket updates.",
          "Memory footprint increases linearly with node count on the canvas.",
        ],
        metadata: [
          {
            label: "GROUP",
            value: "Technical Debt",
          },
          {
            label: "TIER",
            value: "Strategic (B)",
          },
        ],
        categories: ["Optimization", "Frontend", "Critical"],
      },
    },
  },
}

const fallbackDashboard: MemoryDashboardData = {
  ...dashboardByMemoryId["MEM-8429-XQ"],
  id: "mem_mocked",
  name: "Memory Workspace",
  identity: {
    runtimeId: "mem_mocked",
    agent: "mock-agent",
  },
}

function formatCount(value: number | undefined) {
  return new Intl.NumberFormat("en-US").format(value ?? 0)
}

function formatDateTime(value: string | undefined) {
  if (!value) {
    return "-"
  }

  const date = new Date(value)

  if (Number.isNaN(date.getTime())) {
    return value
  }

  return date.toLocaleString("en-US", {
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    month: "short",
    year: "numeric",
  })
}

function formatDate(value: string | undefined) {
  if (!value) {
    return "-"
  }

  const date = new Date(value)

  if (Number.isNaN(date.getTime())) {
    return value
  }

  return date.toLocaleDateString("en-US", {
    day: "2-digit",
    month: "short",
    year: "numeric",
  })
}

function shortId(value: string | number | undefined) {
  const text = String(value ?? "-")

  return text.length > 8 ? `${text.slice(0, 6)}...` : text
}

function stringifyJson(value: unknown) {
  return JSON.stringify(value ?? {}, null, 2)
}

function titleCase(value: string | undefined) {
  if (!value) {
    return "Unknown"
  }

  return value
    .split(/[\s_-]+/)
    .filter(Boolean)
    .map((part) => `${part.charAt(0).toUpperCase()}${part.slice(1).toLowerCase()}`)
    .join(" ")
}

function uppercase(value: string | undefined, fallback: string) {
  return value?.toUpperCase() ?? fallback
}

function memoryScope(memoryId: string) {
  const [userId, agentId] = memoryId.split(":", 2)

  return {
    agentId: agentId || undefined,
    userId: userId || memoryId,
  }
}

function percentage(count: number, total: number) {
  if (total <= 0) {
    return 0
  }

  return Math.round((count / total) * 100)
}

function mapDistribution(items: Array<{ count: number; name: string }>) {
  const total = items.reduce((current, item) => current + item.count, 0)

  return items.map((item) => ({
    label: titleCase(item.name),
    value: percentage(item.count, total),
  }))
}

function mapCategoryItems(items: Array<{ count: number; name: string }>) {
  return items.map((item) => ({
    label: titleCase(item.name),
    value: formatCount(item.count),
  }))
}

function backlogTotal(backlog: AdminDashboardView["backlog"]) {
  return (
    backlog.conversationPending +
    backlog.graphBatchRepairRequired +
    backlog.insightUnbuilt +
    backlog.insightUngrouped +
    backlog.threadOutboxFailed +
    backlog.threadOutboxPending
  )
}

function latestActivityTime(apiData: AdminMemoryDashboardApiData) {
  return (
    apiData.threadStatus.updatedAt ??
    apiData.threads.items[0]?.updatedAt ??
    apiData.rawData.items[0]?.updatedAt ??
    apiData.items.items[0]?.updatedAt ??
    apiData.insights.items[0]?.updatedAt ??
    "-"
  )
}

function mapRawDataPage(rawData: PageResult<AdminRawDataView>): MemoryRawDataPageData {
  const vectorizedCount = rawData.items.filter((item) => item.captionVectorId).length
  const captionCount = rawData.items.filter((item) => item.caption).length

  return {
    pageLabel: `Page ${rawData.page.page} of ${rawData.page.totalPages}`,
    paginationLabel: `Showing ${rawData.items.length} of ${formatCount(
      rawData.page.totalItems
    )} records`,
    records: rawData.items.map((record) => ({
      associatedItems: [],
      caption: record.caption ?? "-",
      createdAt: formatDateTime(record.createdAt),
      id: record.rawDataId,
      metadataJson: stringifyJson(record.metadata),
      segmentJson: stringifyJson(record.segment),
      shortId: shortId(record.rawDataId),
      source: record.sourceClient ?? "-",
      type: record.type ?? "unknown",
      typeLabel: uppercase(record.type, "RAW"),
      vectorStatus: record.captionVectorId ? "Vectorized" : "Missing",
    })),
    summary: [
      { label: "Total Records", value: formatCount(rawData.page.totalItems) },
      { label: "Loaded", value: formatCount(rawData.items.length) },
      {
        label: "With Caption",
        value: formatCount(captionCount),
      },
      {
        label: "Vectorized",
        value: formatCount(vectorizedCount),
      },
      {
        label: "Page",
        value: `${rawData.page.page}/${rawData.page.totalPages}`,
      },
    ],
  }
}

function mapItemScope(value: string | undefined): MemoryItemRecord["scope"] {
  return value?.toUpperCase() === "AGENT" ? "AGENT" : "USER"
}

function mapItemType(value: string | undefined): MemoryItemRecord["type"] {
  return value?.toUpperCase() === "FORESIGHT" ? "FORESIGHT" : "FACT"
}

function mapItemsPage(items: PageResult<AdminItemView>): MemoryItemsPageData {
  const userScopeCount = items.items.filter(
    (item) => item.scope?.toUpperCase() !== "AGENT"
  ).length
  const agentScopeCount = items.items.length - userScopeCount
  const factCount = items.items.filter(
    (item) => item.type?.toUpperCase() !== "FORESIGHT"
  ).length
  const foresightCount = items.items.length - factCount
  const vectorizedCount = items.items.filter((item) => item.vectorId).length

  return {
    paginationLabel: `Showing ${items.items.length} of ${formatCount(
      items.page.totalItems
    )} items`,
    records: items.items.map((item) => ({
      category: item.category ?? "unknown",
      content: item.content ?? "-",
      contentHash: item.contentHash ?? "-",
      id: String(item.itemId),
      metadataJson: stringifyJson(item.metadata),
      observedAt: formatDateTime(item.observedAt),
      observedAtFull: formatDateTime(item.observedAt),
      occurredAt: formatDateTime(item.occurredAt),
      relatedThreads: [],
      scope: mapItemScope(item.scope),
      shortId: shortId(item.itemId),
      sourceIntegration: item.sourceClient ?? "-",
      sourceRawDataId: item.rawDataId ?? "-",
      sourceRawDataShortId: shortId(item.rawDataId),
      sourceType: item.rawDataType ?? "-",
      threadCount: 0,
      type: mapItemType(item.type),
      vectorId: item.vectorId ?? "-",
      vectorStatus: item.vectorId ? "Vectorized" : "Missing",
    })),
    summary: [
      { label: "Total Items", value: formatCount(items.page.totalItems) },
      { label: "USER Scope", value: formatCount(userScopeCount) },
      { label: "AGENT Scope", value: formatCount(agentScopeCount) },
      { label: "FACT Items", value: formatCount(factCount) },
      { label: "FORESIGHT Items", value: formatCount(foresightCount) },
      {
        detail: `${percentage(vectorizedCount, items.items.length)}%`,
        label: "Vectorized",
        value: formatCount(vectorizedCount),
      },
    ],
  }
}

function graphNodeType(entityType: string | undefined): MemoryGraphNode["type"] {
  return entityType?.toUpperCase().includes("ORG") ? "Organization" : "Person"
}

function mapGraphPage(
  summary: AdminGraphSummaryView,
  entities: PageResult<AdminGraphEntityView>,
  batches: PageResult<AdminGraphBatchView>
): MemoryGraphPageData {
  const nodes = entities.items.map((entity, index) => {
    const type = graphNodeType(entity.entityType)
    const column = index % 4
    const row = Math.floor(index / 4)

    return {
      icon: type === "Organization" ? "organization" : "person",
      id: String(entity.id),
      label: entity.displayName ?? entity.entityKey ?? `Entity ${entity.id}`,
      size: index === 0 ? "lg" : "md",
      type,
      x: `${18 + column * 20}%`,
      y: `${22 + row * 18}%`,
    } satisfies MemoryGraphNode
  })
  const selectedNode = nodes[0]
  const selectedEntity = entities.items[0]

  return {
    diagnostics: batches.items.map((batch) => ({
      errorMessage: batch.errorMessage ?? "-",
      id: batch.extractionBatchId ?? String(batch.id),
      lastAttempt: formatDateTime(batch.updatedAt ?? batch.createdAt),
      state:
        batch.state?.toLowerCase().includes("repair") ? "Repair Required" : "Completed",
    })),
    edges: [],
    nodes,
    selectedEntity: {
      aliases: selectedNode ? [selectedNode.label] : [],
      cooccurrences: [],
      entityId: selectedNode?.id ?? "-",
      label: selectedNode?.label ?? "No entity selected",
      mentionCount: summary.mentionCount,
      metadataJson: stringifyJson(selectedEntity?.metadata),
      type: selectedNode?.type.toUpperCase() ?? "-",
    },
    summary: [
      { label: "Entities", value: formatCount(summary.entityCount) },
      { label: "Aliases", value: formatCount(summary.aliasCount) },
      { label: "Mentions", value: formatCount(summary.mentionCount) },
      { label: "Item Links", value: formatCount(summary.itemLinkCount) },
      {
        label: "Cooccurrences",
        value: formatCount(summary.cooccurrenceCount),
      },
      {
        label: "Batches Needing Repair",
        tone: summary.graphBatchCountByState.some((item) =>
          item.name.toLowerCase().includes("repair")
        )
          ? "danger"
          : "default",
        value: formatCount(
          summary.graphBatchCountByState
            .filter((item) => item.name.toLowerCase().includes("repair"))
            .reduce((total, item) => total + item.count, 0)
        ),
      },
    ],
  }
}

function threadStatus(status: string | undefined): MemoryThreadStoryline["status"] {
  const normalized = status?.toUpperCase()

  if (normalized === "CLOSED") {
    return "CLOSED"
  }

  if (normalized === "DORMANT") {
    return "DORMANT"
  }

  return "ACTIVE"
}

function mapThreadNarrative(thread: AdminMemoryThreadView) {
  const latestUpdate = thread.snapshotJson?.latestUpdate

  return typeof latestUpdate === "string"
    ? latestUpdate
    : thread.headline ?? thread.displayLabel ?? thread.threadKey
}

function mapThreadsPage(
  threads: PageResult<AdminMemoryThreadView>,
  status: AdminMemoryThreadStatusView
): MemoryThreadsPageData {
  const activeCount = threads.items.filter(
    (thread) => threadStatus(thread.lifecycleStatus) === "ACTIVE"
  ).length
  const dormantCount = threads.items.filter(
    (thread) => threadStatus(thread.lifecycleStatus) === "DORMANT"
  ).length
  const closedCount = threads.items.filter(
    (thread) => threadStatus(thread.lifecycleStatus) === "CLOSED"
  ).length
  const totalMembers = threads.items.reduce(
    (total, thread) => total + (thread.memberCount ?? 0),
    0
  )

  return {
    projection: {
      failed: status.failedCount,
      pending: status.pendingCount,
      projectionId: status.materializationPolicyVersion ?? "-",
      status: uppercase(status.projectionState, "UNKNOWN"),
      updatedAt: formatDate(status.updatedAt),
    },
    storylines: threads.items.map((thread) => {
      const mappedStatus = threadStatus(thread.lifecycleStatus)
      const narrative = mapThreadNarrative(thread)

      return {
        category: thread.threadType ?? "thread",
        description: thread.headline ?? thread.displayLabel ?? thread.threadKey,
        detail: {
          category: thread.threadType ?? "thread",
          key: thread.threadKey,
          narrative,
          status: mappedStatus,
          timeline: [
            {
              content: narrative,
              id: `${thread.threadKey}-snapshot`,
              role: "PRIMARY",
              source: "Thread snapshot",
              timestamp: formatDateTime(thread.lastEventAt ?? thread.updatedAt),
            },
          ],
          title: thread.displayLabel ?? thread.threadKey,
        },
        key: thread.threadKey,
        lastEvent: formatDateTime(thread.lastEventAt ?? thread.updatedAt),
        memberCount: thread.memberCount ?? 0,
        status: mappedStatus,
        title: thread.displayLabel ?? thread.threadKey,
      }
    }),
    summary: [
      { label: "Active Threads", value: formatCount(activeCount) },
      { label: "Dormant", tone: "muted", value: formatCount(dormantCount) },
      { label: "Closed", tone: "muted", value: formatCount(closedCount) },
      { label: "Total Members", value: formatCount(totalMembers) },
    ],
  }
}

function insightNode(
  insight: AdminInsightView,
  kind: MemoryInsightNode["kind"]
): MemoryInsightNode {
  return {
    category: insight.type ?? "Insight",
    description: insight.content ?? insight.name ?? "-",
    id: String(insight.insightId),
    kind,
    label: titleCase(insight.tier ?? kind),
    title: insight.name ?? insight.groupName ?? `Insight ${insight.insightId}`,
  }
}

function mapInsightsPage(insights: PageResult<AdminInsightView>): MemoryInsightsPageData {
  const byId = new Map(
    insights.items.map((insight) => [insight.insightId, insight])
  )
  const childIds = new Set(
    insights.items.flatMap((insight) => insight.childInsightIds ?? [])
  )
  const rootInsights = insights.items.filter(
    (insight) =>
      insight.tier?.toUpperCase() === "ROOT" ||
      (!insight.parentInsightId && !childIds.has(insight.insightId))
  )

  function leavesForInsight(insight: AdminInsightView) {
    return (insight.childInsightIds ?? [])
      .map((childInsightId) => byId.get(childInsightId))
      .filter((child): child is AdminInsightView => Boolean(child))
      .map((child) => insightNode(child, "leaf"))
  }

  const roots = rootInsights.map((rootInsight) => ({
    ...insightNode(rootInsight, "root"),
    branches: (rootInsight.childInsightIds ?? [])
      .map((childInsightId) => byId.get(childInsightId))
      .filter((child): child is AdminInsightView => Boolean(child))
      .map((childInsight) => ({
        ...insightNode(childInsight, "branch"),
        leaves: leavesForInsight(childInsight),
      })),
  }))
  const selectedInsight =
    insights.items.find((insight) => insight.insightId === 442) ??
    rootInsights[0] ??
    insights.items[0]
  const selectedPoints =
    selectedInsight?.points
      ?.map((point) => point.content)
      .filter((point): point is string => Boolean(point)) ?? []

  return {
    branches: roots[0]?.branches ?? [],
    hierarchy: [
      { count: roots.length, items: roots.map((root) => root.title), label: "Roots" },
      {
        count: roots.reduce((total, root) => total + root.branches.length, 0),
        label: "Branches",
      },
      { count: 0, label: "Leaves" },
    ],
    root:
      roots[0] ??
      {
        category: "Insight",
        description: "No insights returned by the API.",
        id: "empty",
        kind: "root",
        label: "Root Insight",
        title: "No insight selected",
      },
    roots,
    selectedDetail: {
      categories: selectedInsight?.categories ?? [],
      description: selectedInsight?.content ?? "-",
      id: String(selectedInsight?.insightId ?? "empty"),
      kind: titleCase(selectedInsight?.tier ?? "Insight"),
      metadata: [
        { label: "TYPE", value: selectedInsight?.type ?? "-" },
        { label: "SCOPE", value: selectedInsight?.scope ?? "-" },
        { label: "TIER", value: selectedInsight?.tier ?? "-" },
      ],
      points: selectedPoints.length ? selectedPoints : [selectedInsight?.content ?? "-"],
      title: selectedInsight?.name ?? "No insight selected",
    },
  }
}

export function mapAdminMemoryDashboardData(
  memoryId: string,
  apiData: AdminMemoryDashboardApiData
): MemoryDashboardData {
  const scope = memoryScope(memoryId)
  const backlogCount = backlogTotal(apiData.dashboard.backlog)
  const latestActivity = latestActivityTime(apiData)
  const base = fallbackDashboard

  return {
    ...base,
    activity: [
      ...apiData.rawData.items.slice(0, 2).map((record) => ({
        detail: record.caption ?? record.rawDataId,
        time: formatDateTime(record.createdAt),
        title: "Raw data ingested",
        tone: "default" as const,
      })),
      ...apiData.items.items.slice(0, 2).map((item) => ({
        detail: item.content ?? String(item.itemId),
        time: formatDateTime(item.createdAt ?? item.observedAt),
        title: "Memory item extracted",
        tone: "default" as const,
      })),
      ...apiData.insights.items.slice(0, 1).map((insight) => ({
        detail: insight.content ?? insight.name ?? String(insight.insightId),
        time: formatDateTime(insight.updatedAt ?? insight.createdAt),
        title: "Insight reasoned",
        tone: "muted" as const,
      })),
    ],
    agentId: scope.agentId ?? "All agents",
    attention: [
      {
        count: apiData.dashboard.backlog.conversationPending,
        label: "Pending conversations",
      },
      {
        count: apiData.dashboard.backlog.insightUnbuilt,
        label: "Unbuilt insights",
      },
      {
        count: apiData.dashboard.backlog.graphBatchRepairRequired,
        label: "Graph repair required",
        tone:
          apiData.dashboard.backlog.graphBatchRepairRequired > 0
            ? "danger"
            : "default",
      },
    ],
    graph: mapGraphPage(
      apiData.graphSummary,
      apiData.graphEntities,
      apiData.graphBatches
    ),
    id: memoryId,
    identity: {
      agent: scope.agentId ?? "All agents",
      runtimeId: memoryId,
    },
    insights: mapInsightsPage(apiData.insights),
    items: mapItemsPage(apiData.items),
    itemsByCategory: mapCategoryItems(apiData.dashboard.breakdown.itemTypes),
    lastActivity: formatDateTime(latestActivity),
    metrics: [
      {
        detail: "records",
        label: "Raw Data",
        value: formatCount(apiData.dashboard.totals.rawData),
      },
      {
        detail: "items",
        label: "Memory Items",
        value: formatCount(apiData.dashboard.totals.items),
      },
      {
        detail: "patterns",
        label: "Insights",
        value: formatCount(apiData.dashboard.totals.insights),
      },
      {
        detail: "contexts",
        label: "Threads",
        value: formatCount(apiData.dashboard.totals.memoryThreads),
      },
      {
        detail: "attention",
        label: "Alerts",
        tone: backlogCount > 0 ? "danger" : "default",
        value: formatCount(backlogCount),
      },
    ],
    name: memoryId,
    pipeline: [
      {
        detail: `${formatCount(apiData.dashboard.totals.rawData)} Ingested`,
        label: "Raw Data",
        status: "complete",
      },
      {
        detail: `${formatCount(apiData.dashboard.totals.items)} Extracted`,
        label: "Items",
        status: "complete",
      },
      {
        detail: `${formatCount(
          apiData.dashboard.backlog.graphBatchRepairRequired
        )} Repair`,
        label: "Graph / Threads",
        status:
          apiData.dashboard.backlog.graphBatchRepairRequired > 0
            ? "warning"
            : "complete",
      },
      {
        detail: `${formatCount(apiData.dashboard.backlog.insightUnbuilt)} Pending`,
        label: "Insights",
        status:
          apiData.dashboard.backlog.insightUnbuilt > 0 ? "pending" : "complete",
      },
    ],
    rawData: mapRawDataPage(apiData.rawData),
    rawDataByType: mapDistribution(apiData.dashboard.breakdown.rawDataTypes),
    status: apiData.threadStatus.rebuildInProgress ? "Rebuilding" : "Active",
    threads: mapThreadsPage(apiData.threads, apiData.threadStatus),
    userId: scope.userId,
  }
}

async function fetchMemoryDashboard(memoryId: string) {
  const scope = memoryScope(memoryId)
  const dashboardRequest = fetchJson<AdminDashboardView>("/admin/v1/dashboard", {
    query: { days: 7, memoryId },
  })

  const [
    dashboard,
    rawData,
    items,
    graphSummary,
    graphEntities,
    graphBatches,
    threads,
    threadStatus,
    insights,
  ] = await Promise.all([
    dashboardRequest,
    fetchAdminRawDataPage({
      agentId: scope.agentId,
      page: 1,
      pageSize: 20,
      userId: scope.userId,
    }),
    fetchAdminItemsPage({
      agentId: scope.agentId,
      page: 1,
      pageSize: 20,
      userId: scope.userId,
    }),
    fetchAdminGraphSummary(memoryId),
    fetchAdminGraphEntitiesPage({ memoryId, page: 1, pageSize: 20 }),
    fetchAdminGraphBatchesPage({ memoryId, page: 1, pageSize: 20 }),
    fetchAdminMemoryThreadsPage({
      agentId: scope.agentId,
      page: 1,
      pageSize: 20,
      userId: scope.userId,
    }),
    fetchAdminMemoryThreadStatus({
      agentId: scope.agentId,
      userId: scope.userId,
    }),
    fetchAdminInsightsPage({
      agentId: scope.agentId,
      page: 1,
      pageSize: 20,
      userId: scope.userId,
    }),
  ])

  return mapAdminMemoryDashboardData(memoryId, {
    dashboard,
    graphBatches,
    graphEntities,
    graphSummary,
    insights,
    items,
    rawData,
    threadStatus,
    threads,
  })
}

export function useMemoryDashboard(memoryId: string) {
  return useQuery({
    queryKey: ["memories", "dashboard", memoryId],
    queryFn: () => fetchMemoryDashboard(memoryId),
  })
}
