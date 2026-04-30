export type JsonRecord = Record<string, unknown>

export type NamedCount = {
  name: string
  count: number
}

export type StateCount = {
  state: string
  count: number
}

export type DailyCount = {
  date: string
  count: number
}

export type AdminDashboardView = {
  totals: {
    rawData: number
    items: number
    insights: number
    memoryThreads: number
    graphEntities: number
    itemLinks: number
  }
  backlog: {
    conversationPending: number
    insightUnbuilt: number
    insightUngrouped: number
    threadOutboxPending: number
    threadOutboxFailed: number
    graphBatchRepairRequired: number
  }
  activity: {
    days: number
    rawDataCreated: DailyCount[]
    itemsCreated: DailyCount[]
    insightsCreated: DailyCount[]
  }
  breakdown: {
    sourceClients: NamedCount[]
    rawDataTypes: NamedCount[]
    itemTypes: NamedCount[]
    insightTypes: NamedCount[]
    graphLinkTypes: NamedCount[]
  }
  healthSignals: {
    graphEnabled: boolean
    retrievalGraphAssistEnabled: boolean
    threadProjectionStates: StateCount[]
  }
}

export type AdminItemView = {
  itemId: number
  userId: string
  agentId: string | null
  memoryId: string
  content: string
  scope: string | null
  category: string | null
  vectorId: string | null
  rawDataId: string | null
  contentHash: string | null
  occurredAt: string | null
  observedAt: string | null
  metadata: JsonRecord | null
  type: string | null
  rawDataType: string | null
  sourceClient: string | null
  createdAt: string
  updatedAt: string
}

export type AdminRawDataView = {
  rawDataId: string
  userId: string
  agentId: string | null
  memoryId: string
  type: string | null
  sourceClient: string | null
  contentId: string | null
  segment: JsonRecord | null
  caption: string | null
  captionVectorId: string | null
  metadata: JsonRecord | null
  startTime: string | null
  endTime: string | null
  createdAt: string
  updatedAt: string
}

export type InsightPoint = JsonRecord

export type AdminInsightView = {
  insightId: number
  userId: string
  agentId: string | null
  memoryId: string
  type: string | null
  scope: string | null
  name: string | null
  categories: string[]
  content: string
  points: InsightPoint[]
  groupName: string | null
  lastReasonedAt: string | null
  summaryEmbedding: number[] | null
  tier: string | null
  parentInsightId: number | null
  childInsightIds: number[]
  version: number | null
  createdAt: string
  updatedAt: string
}

export type ConversationBufferView = {
  id: number
  sessionId: string | null
  userId: string
  agentId: string | null
  memoryId: string
  role: string | null
  content: string
  userName: string | null
  sourceClient: string | null
  timestamp: string | null
  extracted: boolean | null
  createdAt: string
  updatedAt: string
}

export type InsightBufferView = {
  id: number
  userId: string
  agentId: string | null
  memoryId: string
  insightTypeName: string
  itemId: number | null
  groupName: string | null
  built: boolean | null
  createdAt: string
  updatedAt: string
}

export type InsightBufferGroupView = {
  memoryId: string
  insightTypeName: string
  groupName: string | null
  total: number
  unbuilt: number
  built: number
}

export type AdminMemoryThreadView = {
  userId: string
  agentId: string | null
  memoryId: string
  threadKey: string
  threadType: string
  anchorKind: string | null
  anchorKey: string | null
  displayLabel: string | null
  lifecycleStatus: 'ACTIVE' | 'DORMANT' | 'CLOSED' | string
  objectState: string | null
  headline: string | null
  snapshotJson: JsonRecord | null
  snapshotVersion: number
  openedAt: string | null
  lastEventAt: string | null
  lastMeaningfulUpdateAt: string | null
  closedAt: string | null
  eventCount: number
  memberCount: number
  createdAt: string
  updatedAt: string
}

export type AdminMemoryThreadItemView = {
  userId: string
  agentId: string | null
  memoryId: string
  threadKey: string
  itemId: number
  role: string
  primary: boolean
  relevanceWeight: number
  createdAt: string
  updatedAt: string
}

export type AdminItemMemoryThreadView = {
  userId: string
  agentId: string | null
  memoryId: string
  threadKey: string
  threadType: string
  anchorKind: string | null
  anchorKey: string | null
  displayLabel: string | null
  lifecycleStatus: string
  objectState: string | null
  headline: string | null
  itemId: number
  role: string
  primary: boolean
  relevanceWeight: number
  createdAt: string
  updatedAt: string
}

export type AdminMemoryThreadStatusView = {
  projectionState: string | null
  pendingCount: number
  failedCount: number
  rebuildInProgress: boolean
  lastProcessedItemId: number | null
  materializationPolicyVersion: string | null
  updatedAt: string | null
  invalidationReason: string | null
}

export type ItemGraphSummaryView = {
  entityCount: number
  aliasCount: number
  mentionCount: number
  itemLinkCount: number
  cooccurrenceCount: number
  graphBatchCountByState: NamedCount[]
  itemLinkCountByType: NamedCount[]
  entityCountByType: NamedCount[]
}

export type GraphEntityView = {
  id: number
  memoryId: string
  userId: string
  agentId: string | null
  entityKey: string
  displayName: string | null
  entityType: string | null
  metadata: JsonRecord | null
  createdAt: string
  updatedAt: string
}

export type GraphAliasView = {
  id: number
  memoryId: string
  userId: string
  agentId: string | null
  entityKey: string
  entityType: string | null
  normalizedAlias: string
  evidenceCount: number | null
  metadata: JsonRecord | null
  createdAt: string
  updatedAt: string
}

export type GraphMentionView = {
  id: number
  memoryId: string
  userId: string
  agentId: string | null
  itemId: number
  entityKey: string
  confidence: number | null
  metadata: JsonRecord | null
  createdAt: string
  updatedAt: string
}

export type GraphItemLinkView = {
  id: number
  memoryId: string
  userId: string
  agentId: string | null
  sourceItemId: number
  targetItemId: number
  linkType: string | null
  relationCode: string | null
  evidenceSource: string | null
  strength: number | null
  metadata: JsonRecord | null
  createdAt: string
  updatedAt: string
}

export type GraphCooccurrenceView = {
  id: number
  memoryId: string
  userId: string
  agentId: string | null
  leftEntityKey: string
  rightEntityKey: string
  cooccurrenceCount: number | null
  metadata: JsonRecord | null
  createdAt: string
  updatedAt: string
}

export type GraphBatchView = {
  id: number
  memoryId: string
  userId: string
  agentId: string | null
  extractionBatchId: string
  state: 'PENDING' | 'COMMITTED' | 'REPAIR_REQUIRED' | string
  errorMessage: string | null
  retryPromotionSupported: boolean | null
  createdAt: string
  updatedAt: string
}

export type GraphEntityDetailView = {
  entity: GraphEntityView
  aliases: GraphAliasView[]
  mentionCount: number
  topMentionedItemIds: number[]
  topCooccurrences: GraphCooccurrenceView[]
  entityOverlapItemLinkCount: number
}

export type MemoryOptionItemView = {
  key: string
  value: unknown
  description: string | null
  type: 'string' | 'boolean' | 'integer' | 'double' | string
  defaultValue: unknown
  constraints: JsonRecord | null
}

export type MemoryOptionsGetResponse = {
  version: number
  config: Record<string, MemoryOptionItemView[]>
}

export type RetrievalTraceStageView = {
  stage: string
  tier: string | null
  method: string | null
  status: string | null
  inputCount: number | null
  candidateCount: number | null
  resultCount: number | null
  degraded: boolean
  skipped: boolean
  startedAt: string | null
  durationMillis: number | null
  attributes: JsonRecord | null
  candidates: unknown[]
}

export type RetrievalTraceView = {
  traceId: string
  startedAt: string | null
  completedAt: string | null
  truncated: boolean
  stages: RetrievalTraceStageView[]
  merge: {
    inputCount: number
    outputCount: number
    deduplicatedCount: number
    sourceCount: number
    status: string | null
  } | null
  finalResults: {
    strategy: string
    status: string | null
    itemCount: number
    insightCount: number
    rawDataCount: number
    evidenceCount: number
  } | null
}

export type RetrieveMemoryResponse = {
  items: Array<{
    id: string
    text: string
    vectorScore: number
    finalScore: number
    occurredAt: string | null
  }>
  insights: Array<{
    id: string
    text: string
    tier: string | null
  }>
  rawData: Array<{
    rawDataId: string
    caption: string | null
    maxScore: number
    itemIds: string[]
  }>
  evidences: string[]
  strategy: string
  query: string
  trace: RetrievalTraceView | null
}

export type BatchDeleteResult = {
  deletedCount: number
  affectedMemoryIds: string[]
}

export type AdminUpdateResult = {
  updatedCount: number
  affectedMemoryIds: string[]
}

export type RawDataDeleteResult = {
  deletedRawDataCount: number
  deletedItemCount: number
  affectedMemoryIds: string[]
  insightCleanupRequired: boolean
}

export type AdminGraphEntityDeleteResult = {
  deletedCount: number
  affectedMemoryIds: string[]
  deletedAliases: number
  deletedMentions: number
  deletedCooccurrences: number
  possiblyStaleEntityOverlapLinks: number
}

export type ItemDeleteRequest = { itemIds: number[] }
export type RawDataDeleteRequest = { rawDataIds: string[] }
export type InsightDeleteRequest = { insightIds: number[] }
export type AdminIdsRequest = { ids: number[] }
export type InsightBufferBuiltUpdateRequest = {
  ids: number[]
  built: boolean
}
export type InsightBufferGroupUpdateRequest = {
  ids: number[]
  groupName?: string | null
}
export type GraphEntityDeleteRequest = {
  memoryId: string
  entityKeys: string[]
}
export type GraphIdsRequest = { ids: number[] }
export type MemoryOptionsPutRequest = {
  expectedVersion: number
  config: Record<string, MemoryOptionItemView[]>
}
export type RetrieveMemoryRequest = {
  userId: string
  agentId: string
  query: string
  strategy: 'SIMPLE' | 'DEEP' | string
  trace?: boolean
}
