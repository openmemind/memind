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

async function fetchMemoryDashboard(memoryId: string) {
  return dashboardByMemoryId[memoryId] ?? fallbackDashboard
}

export function useMemoryDashboard(memoryId: string) {
  return useQuery({
    queryKey: ["memories", "dashboard", memoryId],
    queryFn: () => fetchMemoryDashboard(memoryId),
  })
}
