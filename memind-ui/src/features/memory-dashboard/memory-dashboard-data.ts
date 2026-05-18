import { useQuery } from "@tanstack/react-query"

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
          caption: "Meeting transcript from Sync Call #42 regarding Q3 metrics...",
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

async function fetchMemoryDashboard(memoryId: string) {
  return dashboardByMemoryId[memoryId] ?? fallbackDashboard
}

export function useMemoryDashboard(memoryId: string) {
  return useQuery({
    queryKey: ["memories", "dashboard", memoryId],
    queryFn: () => fetchMemoryDashboard(memoryId),
  })
}
