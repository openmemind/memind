import type { PageResult } from '@/lib/api-client'
import type {
  AdminDashboardView,
  AdminGraphEntityDeleteResult,
  AdminInsightView,
  AdminItemMemoryThreadView,
  AdminItemView,
  AdminMemoryThreadItemView,
  AdminMemoryThreadStatusView,
  AdminMemoryThreadView,
  AdminRawDataView,
  AdminUpdateResult,
  BatchDeleteResult,
  ConversationBufferView,
  GraphAliasView,
  GraphBatchView,
  GraphCooccurrenceView,
  GraphEntityDetailView,
  GraphEntityView,
  GraphItemLinkView,
  GraphMentionView,
  InsightBufferGroupView,
  InsightBufferView,
  ItemGraphSummaryView,
  MemoryOptionsGetResponse,
  MemoryOptionsPutRequest,
  RawDataDeleteResult,
  RetrieveMemoryRequest,
  RetrieveMemoryResponse,
} from '@/features/types'

type MockMethod = 'GET' | 'POST' | 'PATCH' | 'PUT' | 'DELETE'

const now = '2026-05-06T06:00:00Z'
const memoryIds = ['alice:agent-a', 'alice:agent-b', 'bob:agent-a']
const longText =
  'This is deliberately long mock content used to validate table wrapping, drawer spacing, JSON sections, and dense operational review workflows in Memind UI.'

export async function mockApiRequest<T>(
  method: MockMethod,
  path: string,
  body?: unknown,
  query?: Record<string, unknown>
): Promise<T> {
  await delay(80)
  const data = routeMockRequest(method, path, body, normalizeQuery(query))
  return clone(data) as T
}

function routeMockRequest(
  method: MockMethod,
  path: string,
  body: unknown,
  query: URLSearchParams
) {
  if (method === 'GET' && path === '/open/v1/health') {
    return { status: 'UP', service: 'memind-server mock' }
  }
  if (method === 'GET' && path === '/admin/v1/dashboard')
    return dashboard(query)
  if (method === 'GET' && path === '/admin/v1/buffers/conversations') {
    return page(filterConversationBuffers(query), query)
  }
  if (method === 'GET' && path.startsWith('/admin/v1/buffers/conversations/')) {
    return findById(conversationBuffers, numberFromPath(path))
  }
  if (
    method === 'PATCH' &&
    path === '/admin/v1/buffers/conversations/extracted'
  ) {
    return updateResult(idsFromBody(body).length)
  }
  if (method === 'DELETE' && path === '/admin/v1/buffers/conversations') {
    return deleteResult(idsFromBody(body).length)
  }
  if (method === 'GET' && path === '/admin/v1/buffers/insights') {
    return page(filterInsightBuffers(query), query)
  }
  if (method === 'GET' && path === '/admin/v1/buffers/insights/groups') {
    return filterByMemoryId(insightBufferGroups, query)
  }
  if (method === 'PATCH' && path.startsWith('/admin/v1/buffers/insights/')) {
    return updateResult(idsFromBody(body).length)
  }
  if (method === 'DELETE' && path === '/admin/v1/buffers/insights') {
    return deleteResult(idsFromBody(body).length)
  }
  if (method === 'GET' && path === '/admin/v1/raw-data') {
    return page(filterRawData(query), query)
  }
  if (method === 'GET' && path.startsWith('/admin/v1/raw-data/')) {
    return findByKey(
      rawData,
      'rawDataId',
      decodeURIComponent(lastPathPart(path))
    )
  }
  if (method === 'DELETE' && path === '/admin/v1/raw-data') {
    const count = rawDataIdsFromBody(body).length
    return {
      deletedRawDataCount: count,
      deletedItemCount: count * 2,
      affectedMemoryIds: memoryIds,
      insightCleanupRequired: true,
    } satisfies RawDataDeleteResult
  }
  if (method === 'GET' && path === '/admin/v1/items') {
    return page(filterItems(query), query)
  }
  if (method === 'GET' && /^\/admin\/v1\/items\/\d+$/.test(path)) {
    return findByKey(items, 'itemId', numberFromPath(path))
  }
  if (
    method === 'GET' &&
    path.endsWith('/memory-threads') &&
    path.startsWith('/admin/v1/items/')
  ) {
    requireUserId(query)
    return itemThreadLinks(numberFromPath(path))
  }
  if (method === 'DELETE' && path === '/admin/v1/items') {
    return deleteResult(itemIdsFromBody(body).length)
  }
  if (method === 'GET' && path === '/admin/v1/insights') {
    return page(filterInsights(query), query)
  }
  if (method === 'GET' && /^\/admin\/v1\/insights\/\d+$/.test(path)) {
    return findByKey(insights, 'insightId', numberFromPath(path))
  }
  if (method === 'DELETE' && path === '/admin/v1/insights') {
    return deleteResult(insightIdsFromBody(body).length)
  }
  if (method === 'GET' && path === '/admin/v1/item-graph/summary') {
    return itemGraphSummary(query)
  }
  if (method === 'GET' && path === '/admin/v1/item-graph/entities') {
    return page(filterGraphEntities(query), query)
  }
  if (method === 'GET' && path.startsWith('/admin/v1/item-graph/entities/')) {
    return graphEntityDetail(numberFromPath(path))
  }
  if (method === 'DELETE' && path === '/admin/v1/item-graph/entities') {
    const entityKeys =
      (body as { entityKeys?: string[] } | undefined)?.entityKeys ?? []
    return {
      deletedCount: entityKeys.length,
      affectedMemoryIds: memoryIds,
      deletedAliases: entityKeys.length * 2,
      deletedMentions: entityKeys.length * 3,
      deletedCooccurrences: entityKeys.length,
      possiblyStaleEntityOverlapLinks: entityKeys.length,
    } satisfies AdminGraphEntityDeleteResult
  }
  if (method === 'GET' && path === '/admin/v1/item-graph/aliases') {
    return page(filterGraphAliases(query), query)
  }
  if (method === 'GET' && path === '/admin/v1/item-graph/mentions') {
    return page(filterGraphMentions(query), query)
  }
  if (method === 'GET' && path === '/admin/v1/item-graph/item-links') {
    return page(filterGraphItemLinks(query), query)
  }
  if (method === 'GET' && path === '/admin/v1/item-graph/cooccurrences') {
    return page(filterGraphCooccurrences(query), query)
  }
  if (method === 'GET' && path === '/admin/v1/item-graph/batches') {
    return page(filterGraphBatches(query), query)
  }
  if (method === 'DELETE' && path.startsWith('/admin/v1/item-graph/')) {
    return deleteResult(idsFromBody(body).length)
  }
  if (method === 'GET' && path === '/admin/v1/memory-threads') {
    return page(filterMemoryThreads(query), query)
  }
  if (method === 'GET' && path === '/admin/v1/memory-threads/status') {
    requireUserId(query)
    return threadStatus
  }
  if (method === 'POST' && path === '/admin/v1/memory-threads/rebuild') {
    requireUserId(query)
    return 12
  }
  if (
    method === 'GET' &&
    path.endsWith('/items') &&
    path.startsWith('/admin/v1/memory-threads/')
  ) {
    requireUserId(query)
    return threadMemberships(decodeURIComponent(pathPartFromEnd(path, 1)))
  }
  if (method === 'GET' && path.startsWith('/admin/v1/memory-threads/')) {
    requireUserId(query)
    return findByKey(
      memoryThreads,
      'threadKey',
      decodeURIComponent(lastPathPart(path))
    )
  }
  if (method === 'GET' && path === '/admin/v1/config/memory-options')
    return memoryOptions
  if (method === 'PUT' && path === '/admin/v1/config/memory-options') {
    const request = body as MemoryOptionsPutRequest
    return { version: request.expectedVersion + 1, config: request.config }
  }
  if (method === 'POST' && path === '/open/v1/memory/retrieve') {
    return retrieveResult(body as RetrieveMemoryRequest)
  }

  throw {
    status: 404,
    code: 'mock_not_found',
    message: `No mock handler for ${method} ${path}`,
  }
}

const conversationBuffers: ConversationBufferView[] = Array.from(
  { length: 16 },
  (_, index) => {
    const id = index + 1
    const scope = scopeAt(index)
    return {
      id,
      sessionId: `session-${Math.ceil(id / 3)}`,
      userId: scope.userId,
      agentId: scope.agentId,
      memoryId: scope.memoryId,
      role: id % 2 === 0 ? 'assistant' : 'user',
      content: `${longText} Conversation buffer ${id} includes enough text to inspect preview truncation and full drawer content.`,
      userName: id % 3 === 0 ? 'Bob' : 'Alice',
      sourceClient: id % 4 === 0 ? null : 'claude-code',
      timestamp: time(id),
      extracted: id % 4 === 0,
      createdAt: time(id + 1),
      updatedAt: time(id + 2),
    }
  }
)

const insightBuffers: InsightBufferView[] = Array.from(
  { length: 18 },
  (_, index) => {
    const id = index + 11
    const scope = scopeAt(index)
    return {
      id,
      userId: scope.userId,
      agentId: scope.agentId,
      memoryId: scope.memoryId,
      insightTypeName: ['preference', 'constraint', 'workflow'][index % 3],
      itemId: 1001 + index,
      groupName:
        index % 4 === 0 ? null : ['dev-style', 'tooling', 'handoff'][index % 3],
      built: index % 5 === 0,
      createdAt: time(index + 3),
      updatedAt: time(index + 4),
    }
  }
)

const insightBufferGroups: InsightBufferGroupView[] = [
  {
    memoryId: 'alice:agent-a',
    insightTypeName: 'preference',
    groupName: 'dev-style',
    total: 8,
    unbuilt: 5,
    built: 3,
  },
  {
    memoryId: 'alice:agent-a',
    insightTypeName: 'workflow',
    groupName: 'handoff',
    total: 4,
    unbuilt: 4,
    built: 0,
  },
  {
    memoryId: 'bob:agent-a',
    insightTypeName: 'constraint',
    groupName: null,
    total: 3,
    unbuilt: 3,
    built: 0,
  },
]

const rawData: AdminRawDataView[] = Array.from({ length: 15 }, (_, index) => {
  const id = `raw-${String(index + 1).padStart(3, '0')}`
  const scope = scopeAt(index)
  return {
    rawDataId: id,
    userId: scope.userId,
    agentId: scope.agentId,
    memoryId: scope.memoryId,
    type: ['CONVERSATION', 'DOCUMENT', 'TOOL_CALL'][index % 3],
    sourceClient: ['claude-code', 'sdk', 'web'][index % 3],
    contentId: `content-${index + 1}`,
    segment: {
      index,
      role: index % 2 === 0 ? 'user' : 'assistant',
      text: longText,
    },
    caption: `${longText} Raw data caption ${index + 1}.`,
    captionVectorId: `vector-raw-${index + 1}`,
    metadata: {
      channel: 'mock',
      tags: ['ui-review', index % 2 === 0 ? 'long' : 'short'],
    },
    startTime: time(index + 1),
    endTime: time(index + 2),
    createdAt: time(index + 3),
    updatedAt: time(index + 4),
  }
})

const items: AdminItemView[] = Array.from({ length: 22 }, (_, index) => {
  const itemId = 1001 + index
  const category = ['profile', 'event', 'directive', 'playbook'][index % 4]
  const scopeIdentity = scopeAt(index)
  return {
    itemId,
    userId: scopeIdentity.userId,
    agentId: scopeIdentity.agentId,
    memoryId: scopeIdentity.memoryId,
    content: `${longText} Memory item ${itemId} captures ${category} context and includes a long paragraph for detail drawer review.`,
    scope:
      category === 'directive' || category === 'playbook' ? 'AGENT' : 'USER',
    category,
    vectorId: `vector-item-${itemId}`,
    rawDataId: rawData[index % rawData.length].rawDataId,
    contentHash: `hash-${itemId}`,
    occurredAt: index % 5 === 0 ? null : time(index),
    observedAt: time(index + 1),
    metadata: {
      confidence: 0.75 + (index % 5) / 20,
      labels: ['mock', category],
      nested: { review: true },
    },
    type: ['fact', 'preference', 'workflow'][index % 3],
    rawDataType: rawData[index % rawData.length].type,
    sourceClient: rawData[index % rawData.length].sourceClient,
    createdAt: time(index + 2),
    updatedAt: time(index + 3),
  }
})

const insights: AdminInsightView[] = Array.from({ length: 14 }, (_, index) => {
  const insightId = 2001 + index
  const scopeIdentity = scopeAt(index)
  return {
    insightId,
    userId: scopeIdentity.userId,
    agentId: scopeIdentity.agentId,
    memoryId: scopeIdentity.memoryId,
    type: ['preference', 'constraint', 'workflow'][index % 3],
    scope: index % 2 === 0 ? 'USER' : 'AGENT',
    name: `Insight ${insightId} ${['Preferences', 'Constraints', 'Workflow'][index % 3]}`,
    categories: [['profile'], ['directive'], ['playbook', 'resolution']][
      index % 3
    ],
    content: `${longText} Insight ${insightId} summarizes repeated evidence and reasoning links.`,
    points: [
      { text: 'Observed in multiple conversations', confidence: 0.91 },
      { text: 'Useful for future retrieval', confidence: 0.83 },
    ],
    groupName:
      index % 4 === 0 ? null : ['dev-style', 'tooling', 'handoff'][index % 3],
    lastReasonedAt: time(index + 5),
    summaryEmbedding: [0.12, 0.34, 0.56],
    tier: index % 2 === 0 ? 'LONG_TERM' : 'SESSION',
    parentInsightId: index > 2 ? 2001 : null,
    childInsightIds: index < 3 ? [2004 + index] : [],
    version: 3 + index,
    createdAt: time(index + 2),
    updatedAt: time(index + 6),
  }
})

const graphEntities: GraphEntityView[] = Array.from(
  { length: 13 },
  (_, index) => {
    const scope = scopeAt(index)
    return {
      id: 3001 + index,
      memoryId: scope.memoryId,
      userId: scope.userId,
      agentId: scope.agentId,
      entityKey: ['react-query', 'vite-proxy', 'memory-scope', 'item-graph'][
        index % 4
      ],
      displayName: ['React Query', 'Vite proxy', 'Memory scope', 'Item graph'][
        index % 4
      ],
      entityType: ['TECH', 'CONCEPT', 'PROJECT'][index % 3],
      metadata: { aliases: index + 1, mock: true },
      createdAt: time(index + 2),
      updatedAt: time(index + 3),
    }
  }
)

const graphAliases: GraphAliasView[] = graphEntities.flatMap(
  (entity, index) => [
    {
      id: 4001 + index * 2,
      memoryId: entity.memoryId,
      userId: entity.userId,
      agentId: entity.agentId,
      entityKey: entity.entityKey,
      entityType: entity.entityType,
      normalizedAlias: entity.displayName?.toLowerCase() ?? entity.entityKey,
      evidenceCount: 2 + index,
      metadata: { source: 'mock' },
      createdAt: entity.createdAt,
      updatedAt: entity.updatedAt,
    },
  ]
)

const graphMentions: GraphMentionView[] = Array.from(
  { length: 16 },
  (_, index) => {
    const item = items[index % items.length]
    const entity = pickForMemory(graphEntities, item.memoryId, index)
    return {
      id: 5001 + index,
      memoryId: item.memoryId,
      userId: item.userId,
      agentId: item.agentId,
      itemId: item.itemId,
      entityKey: entity.entityKey,
      confidence: 0.6 + (index % 4) / 10,
      metadata: { sentence: index + 1 },
      createdAt: time(index + 1),
      updatedAt: time(index + 2),
    }
  }
)

const graphItemLinks: GraphItemLinkView[] = Array.from(
  { length: 15 },
  (_, index) => {
    const sourceItem = items[index % items.length]
    const targetItem = pickForMemory(items, sourceItem.memoryId, index + 1)
    return {
      id: 6001 + index,
      memoryId: sourceItem.memoryId,
      userId: sourceItem.userId,
      agentId: sourceItem.agentId,
      sourceItemId: sourceItem.itemId,
      targetItemId: targetItem.itemId,
      linkType: ['SEMANTIC', 'TEMPORAL', 'CAUSAL'][index % 3],
      relationCode: ['supports', 'follows', 'explains'][index % 3],
      evidenceSource: ['llm', 'temporal-window', 'graph-repair'][index % 3],
      strength: 0.55 + (index % 5) / 10,
      metadata: { batch: `batch-${index % 4}` },
      createdAt: time(index + 1),
      updatedAt: time(index + 2),
    }
  }
)

const graphCooccurrences: GraphCooccurrenceView[] = Array.from(
  { length: 11 },
  (_, index) => {
    const scope = scopeAt(index)
    const leftEntity = pickForMemory(graphEntities, scope.memoryId, index)
    const rightEntity = pickForMemory(graphEntities, scope.memoryId, index + 1)
    return {
      id: 7001 + index,
      memoryId: scope.memoryId,
      userId: scope.userId,
      agentId: scope.agentId,
      leftEntityKey: leftEntity.entityKey,
      rightEntityKey: rightEntity.entityKey,
      cooccurrenceCount: 2 + index,
      metadata: { window: 'conversation' },
      createdAt: time(index + 1),
      updatedAt: time(index + 2),
    }
  }
)

const graphBatches: GraphBatchView[] = Array.from({ length: 9 }, (_, index) => {
  const scope = scopeAt(index)
  return {
    id: 8001 + index,
    memoryId: scope.memoryId,
    userId: scope.userId,
    agentId: scope.agentId,
    extractionBatchId: `batch-${20260500 + index}`,
    state: ['PENDING', 'COMMITTED', 'REPAIR_REQUIRED'][index % 3],
    errorMessage:
      index % 3 === 2 ? 'Alias receipt mismatch detected in mock batch.' : null,
    retryPromotionSupported: index % 3 === 2,
    createdAt: time(index + 1),
    updatedAt: time(index + 2),
  }
})

const memoryThreads: AdminMemoryThreadView[] = Array.from(
  { length: 12 },
  (_, index) => {
    const threadKey = `thread-${index + 1}`
    const scope = scopeAt(index)
    return {
      userId: scope.userId,
      agentId: scope.agentId,
      memoryId: scope.memoryId,
      threadKey,
      threadType: ['TOPIC', 'TASK', 'DECISION'][index % 3],
      anchorKind: ['entity', 'insight', 'item'][index % 3],
      anchorKey: graphEntities[index % graphEntities.length].entityKey,
      displayLabel: `Thread ${index + 1} review`,
      lifecycleStatus: ['ACTIVE', 'DORMANT', 'CLOSED'][index % 3],
      objectState: ['OPEN', 'STALE', 'FINALIZED'][index % 3],
      headline: `${longText} Thread headline ${index + 1}.`,
      snapshotJson: {
        summary: 'Mock thread snapshot',
        importantItemIds: [items[index].itemId, items[index + 1].itemId],
      },
      snapshotVersion: 2 + index,
      openedAt: time(index + 1),
      lastEventAt: time(index + 3),
      lastMeaningfulUpdateAt: time(index + 4),
      closedAt: index % 3 === 2 ? time(index + 5) : null,
      eventCount: 3 + index,
      memberCount: 2 + (index % 4),
      createdAt: time(index + 1),
      updatedAt: time(index + 5),
    }
  }
)

const threadStatus: AdminMemoryThreadStatusView = {
  projectionState: 'READY',
  pendingCount: 6,
  failedCount: 1,
  rebuildInProgress: false,
  lastProcessedItemId: 1019,
  materializationPolicyVersion: 'thread-core-v1',
  updatedAt: now,
  invalidationReason: 'mock data includes one pending repair item',
}

const memoryOptions: MemoryOptionsGetResponse = {
  version: 12,
  config: {
    'extraction.common': [
      option('defaultScope', 'USER', 'string', {
        allowedValues: ['USER', 'AGENT'],
      }),
      option('timeout', 'PT10M', 'string', { format: 'iso-8601-duration' }),
      option('language', 'English', 'string'),
    ],
    'extraction.rawdata': [
      option('conversationStrategy', 'LLM', 'string', {
        enum: ['FIXED', 'LLM'],
      }),
      option('messagesPerChunk', 12, 'integer', { min: 1, max: 64 }),
      option('vectorBatchSize', 32, 'integer', { min: 1, max: 256 }),
    ],
    'extraction.item.graph': [
      option('enabled', true, 'boolean'),
      option('maxEntitiesPerItem', 8, 'integer', { min: 1, max: 32 }),
      option('minimumMentionConfidence', 0.72, 'double', { min: 0, max: 1 }),
    ],
    'retrieval.simple.graphAssist': [
      option('enabled', true, 'boolean'),
      option('maxSeedItems', 12, 'integer', { min: 1, max: 100 }),
      option('mode', 'BALANCED', 'string', {
        enum: ['OFF', 'BALANCED', 'AGGRESSIVE'],
      }),
    ],
    'memory.thread.materialization': [
      option('enabled', true, 'boolean'),
      option('policyVersion', 'thread-core-v1', 'string'),
      option(
        'veryLongOptionKeyUsedToValidateHorizontalWrappingAndFormLayoutWithDenseOperationalConfigurationPanels',
        'very-long-value-that-should-wrap-cleanly-without-forcing-horizontal-page-overflow-in-settings-review-mode',
        'string'
      ),
    ],
  },
}

function dashboard(query: URLSearchParams): AdminDashboardView {
  const days = Math.min(numberParam(query, 'days', 7), 30)
  const scopedRawData = filterByMemoryId(rawData, query)
  const scopedItems = filterByMemoryId(items, query)
  const scopedInsights = filterByMemoryId(insights, query)
  const scopedThreads = filterByMemoryId(memoryThreads, query)
  const scopedEntities = filterByMemoryId(graphEntities, query)
  const scopedItemLinks = filterByMemoryId(graphItemLinks, query)
  const scopedConversationBuffers = filterByMemoryId(conversationBuffers, query)
  const scopedInsightBuffers = filterByMemoryId(insightBuffers, query)
  const scopedGraphBatches = filterByMemoryId(graphBatches, query)

  return {
    totals: {
      rawData: scopedRawData.length,
      items: scopedItems.length,
      insights: scopedInsights.length,
      memoryThreads: scopedThreads.length,
      graphEntities: scopedEntities.length,
      itemLinks: scopedItemLinks.length,
    },
    backlog: {
      conversationPending: scopedConversationBuffers.filter(
        (row) => !row.extracted
      ).length,
      insightUnbuilt: scopedInsightBuffers.filter((row) => !row.built).length,
      insightUngrouped: scopedInsightBuffers.filter(
        (row) => !row.built && !row.groupName
      ).length,
      threadOutboxPending: 7,
      threadOutboxFailed: 1,
      graphBatchRepairRequired: scopedGraphBatches.filter(
        (row) => row.state === 'REPAIR_REQUIRED'
      ).length,
    },
    activity: {
      days,
      rawDataCreated: daily(days, 5),
      itemsCreated: daily(days, 8),
      insightsCreated: daily(days, 3),
    },
    breakdown: {
      sourceClients: countBy(scopedRawData, 'sourceClient'),
      rawDataTypes: countBy(scopedRawData, 'type'),
      itemTypes: countBy(scopedItems, 'type'),
      insightTypes: countBy(scopedInsights, 'type'),
      graphLinkTypes: countBy(scopedItemLinks, 'linkType'),
    },
    healthSignals: {
      graphEnabled: true,
      retrievalGraphAssistEnabled: true,
      threadProjectionStates: [
        { state: 'READY', count: 2 },
        { state: 'REBUILD_REQUIRED', count: 1 },
        { state: 'FAILED', count: 1 },
      ],
    },
  }
}

function retrieveResult(
  request: RetrieveMemoryRequest
): RetrieveMemoryResponse {
  return {
    items: items.slice(0, 5).map((item, index) => ({
      id: String(item.itemId),
      text: `${item.content} Query: ${request.query}`,
      vectorScore: 0.82 - index * 0.04,
      finalScore: 0.91 - index * 0.03,
      occurredAt: item.occurredAt,
    })),
    insights: insights.slice(0, 4).map((insight) => ({
      id: String(insight.insightId),
      text: insight.content,
      tier: insight.tier,
    })),
    rawData: rawData.slice(0, 3).map((row) => ({
      rawDataId: row.rawDataId,
      caption: row.caption,
      maxScore: 0.88,
      itemIds: [String(items[0].itemId), String(items[1].itemId)],
    })),
    evidences: [
      'Mock evidence: matching memory item content and entity mentions.',
      'Mock evidence: insight group has repeated support across conversations.',
    ],
    strategy: request.strategy,
    query: request.query,
    trace: request.trace
      ? {
          traceId: 'mock-trace-001',
          startedAt: time(1),
          completedAt: time(2),
          truncated: false,
          stages: [
            traceStage('keyword', 'SIMPLE', 24, 8),
            traceStage('graph-expand', 'GRAPH', 8, 11),
            traceStage('rerank', 'RERANK', 11, 5),
          ],
          merge: {
            inputCount: 19,
            outputCount: 8,
            deduplicatedCount: 4,
            sourceCount: 3,
            status: 'OK',
          },
          finalResults: {
            strategy: request.strategy,
            status: 'OK',
            itemCount: 5,
            insightCount: 4,
            rawDataCount: 3,
            evidenceCount: 2,
          },
        }
      : null,
  }
}

function itemGraphSummary(query: URLSearchParams): ItemGraphSummaryView {
  const entities = filterByMemoryId(graphEntities, query)
  const aliases = filterByMemoryId(graphAliases, query)
  const mentions = filterByMemoryId(graphMentions, query)
  const itemLinks = filterByMemoryId(graphItemLinks, query)
  const cooccurrences = filterByMemoryId(graphCooccurrences, query)
  const batches = filterByMemoryId(graphBatches, query)

  return {
    entityCount: entities.length,
    aliasCount: aliases.length,
    mentionCount: mentions.length,
    itemLinkCount: itemLinks.length,
    cooccurrenceCount: cooccurrences.length,
    graphBatchCountByState: countBy(batches, 'state'),
    itemLinkCountByType: countBy(itemLinks, 'linkType'),
    entityCountByType: countBy(entities, 'entityType'),
  }
}

function graphEntityDetail(id: number): GraphEntityDetailView {
  const entity = findByKey(graphEntities, 'id', id)
  return {
    entity,
    aliases: graphAliases.filter(
      (alias) => alias.entityKey === entity.entityKey
    ),
    mentionCount: graphMentions.filter(
      (mention) => mention.entityKey === entity.entityKey
    ).length,
    topMentionedItemIds: items.slice(0, 5).map((item) => item.itemId),
    topCooccurrences: graphCooccurrences.slice(0, 4),
    entityOverlapItemLinkCount: 3,
  }
}

function threadMemberships(threadKey: string): AdminMemoryThreadItemView[] {
  const thread = findByKey(memoryThreads, 'threadKey', threadKey)
  return items.slice(0, 4).map((item, index) => ({
    userId: thread.userId,
    agentId: thread.agentId,
    memoryId: thread.memoryId,
    threadKey,
    itemId: item.itemId,
    role: index === 0 ? 'anchor' : 'supporting',
    primary: index === 0,
    relevanceWeight: 1 - index * 0.15,
    createdAt: time(index + 2),
    updatedAt: time(index + 3),
  }))
}

function itemThreadLinks(itemId: number): AdminItemMemoryThreadView[] {
  return memoryThreads.slice(0, 3).map((thread, index) => ({
    ...thread,
    itemId,
    role: index === 0 ? 'anchor' : 'supporting',
    primary: index === 0,
    relevanceWeight: 0.95 - index * 0.2,
  }))
}

function filterConversationBuffers(query: URLSearchParams) {
  const state = query.get('state') ?? 'pending'
  const sessionId = query.get('sessionId')
  return filterByMemoryId(conversationBuffers, query).filter((row) => {
    if (state === 'pending' && row.extracted) return false
    if (state === 'extracted' && !row.extracted) return false
    if (sessionId && row.sessionId !== sessionId) return false
    return true
  })
}

function filterInsightBuffers(query: URLSearchParams) {
  const state = query.get('state') ?? 'unbuilt'
  const insightTypeName = query.get('insightTypeName')
  return filterByMemoryId(insightBuffers, query).filter((row) => {
    if (insightTypeName && row.insightTypeName !== insightTypeName) return false
    if (state === 'all') return true
    if (state === 'built') return row.built
    if (state === 'ungrouped') return !row.built && !row.groupName
    if (state === 'grouped') return !row.built && Boolean(row.groupName)
    return !row.built
  })
}

function filterRawData(query: URLSearchParams) {
  const startTimeFrom = query.get('startTimeFrom')
  const startTimeTo = query.get('startTimeTo')
  return filterByUserAgent(rawData, query).filter((row) => {
    if (startTimeFrom && (!row.startTime || row.startTime < startTimeFrom)) {
      return false
    }
    if (startTimeTo && (!row.startTime || row.startTime > startTimeTo)) {
      return false
    }
    return true
  })
}

function filterItems(query: URLSearchParams) {
  const scope = query.get('scope')
  const category = query.get('category')
  const type = query.get('type')
  const rawDataId = query.get('rawDataId')
  return filterByUserAgent(items, query).filter((row) => {
    if (scope && row.scope !== scope) return false
    if (category && row.category !== category) return false
    if (type && row.type !== type) return false
    if (rawDataId && row.rawDataId !== rawDataId) return false
    return true
  })
}

function filterInsights(query: URLSearchParams) {
  const scope = query.get('scope')
  const type = query.get('type')
  const tier = query.get('tier')
  return filterByUserAgent(insights, query).filter((row) => {
    if (scope && row.scope !== scope) return false
    if (type && row.type !== type) return false
    if (tier && row.tier !== tier) return false
    return true
  })
}

function filterGraphEntities(query: URLSearchParams) {
  const entityType = query.get('entityType')
  const q = query.get('q')?.toLowerCase()
  return filterByMemoryId(graphEntities, query).filter((row) => {
    if (entityType && row.entityType !== entityType) return false
    if (
      q &&
      ![row.entityKey, row.displayName, row.entityType].some((value) =>
        value?.toLowerCase().includes(q)
      )
    ) {
      return false
    }
    return true
  })
}

function filterGraphAliases(query: URLSearchParams) {
  const entityKey = query.get('entityKey')
  const q = query.get('q')?.toLowerCase()
  return filterByMemoryId(graphAliases, query).filter((row) => {
    if (entityKey && row.entityKey !== entityKey) return false
    if (
      q &&
      ![row.entityKey, row.normalizedAlias, row.entityType].some((value) =>
        value?.toLowerCase().includes(q)
      )
    ) {
      return false
    }
    return true
  })
}

function filterGraphMentions(query: URLSearchParams) {
  const itemId = query.get('itemId')
  const entityKey = query.get('entityKey')
  return filterByMemoryId(graphMentions, query).filter((row) => {
    if (itemId && row.itemId !== Number(itemId)) return false
    if (entityKey && row.entityKey !== entityKey) return false
    return true
  })
}

function filterGraphItemLinks(query: URLSearchParams) {
  const itemId = query.get('itemId')
  const linkType = query.get('linkType')
  const evidenceSource = query.get('evidenceSource')
  return filterByMemoryId(graphItemLinks, query).filter((row) => {
    if (
      itemId &&
      row.sourceItemId !== Number(itemId) &&
      row.targetItemId !== Number(itemId)
    ) {
      return false
    }
    if (linkType && row.linkType !== linkType) return false
    if (evidenceSource && row.evidenceSource !== evidenceSource) return false
    return true
  })
}

function filterGraphCooccurrences(query: URLSearchParams) {
  const entityKey = query.get('entityKey')
  return filterByMemoryId(graphCooccurrences, query).filter((row) => {
    if (
      entityKey &&
      row.leftEntityKey !== entityKey &&
      row.rightEntityKey !== entityKey
    ) {
      return false
    }
    return true
  })
}

function filterGraphBatches(query: URLSearchParams) {
  const state = query.get('state')
  return filterByMemoryId(graphBatches, query).filter(
    (row) => !state || row.state === state
  )
}

function filterMemoryThreads(query: URLSearchParams) {
  const status = query.get('status')
  return filterByUserAgent(memoryThreads, query).filter(
    (row) => !status || row.lifecycleStatus === status
  )
}

function filterByMemoryId<T extends { memoryId: string }>(
  rows: T[],
  query: URLSearchParams
) {
  const memoryId = query.get('memoryId')
  return memoryId ? rows.filter((row) => row.memoryId === memoryId) : rows
}

function requireUserId(query: URLSearchParams) {
  if (query.get('userId')) return
  throw {
    status: 400,
    code: 'validation_failed',
    message: 'userId is required',
  }
}

function filterByUserAgent<
  T extends { userId: string; agentId: string | null },
>(rows: T[], query: URLSearchParams) {
  const userId = query.get('userId')
  const agentId = query.get('agentId')
  return rows.filter((row) => {
    if (userId && row.userId !== userId) return false
    if (agentId && row.agentId !== agentId) return false
    return true
  })
}

function page<T>(rows: T[], query: URLSearchParams): PageResult<T> {
  const pageNo = numberParam(query, 'pageNo', 1)
  const pageSize = numberParam(query, 'pageSize', 10)
  const start = (pageNo - 1) * pageSize
  return {
    total: rows.length,
    current: pageNo,
    list: rows.slice(start, start + pageSize),
  }
}

function option(
  key: string,
  value: unknown,
  type: string,
  constraints: Record<string, unknown> | null = null
) {
  return {
    key,
    value,
    description: `Mock ${key} option for UI review.`,
    type,
    defaultValue: value,
    constraints,
  }
}

function findById<T extends { id: number }>(rows: T[], id: number) {
  const row = rows.find((item) => item.id === id)
  if (!row) throw notFound(`Mock row id ${id} was not found`)
  return row
}

function findByKey<T, K extends keyof T>(rows: T[], key: K, value: T[K]) {
  const row = rows.find((item) => item[key] === value)
  if (!row)
    throw notFound(`Mock row ${String(key)}=${String(value)} was not found`)
  return row
}

function normalizeQuery(query?: Record<string, unknown>) {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(query ?? {})) {
    if (value !== undefined && value !== null && value !== '')
      params.set(key, String(value))
  }
  return params
}

function numberParam(params: URLSearchParams, key: string, fallback: number) {
  const parsed = Number(params.get(key))
  return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback
}

function numberFromPath(path: string) {
  const matches = path.match(/\d+/g)
  return Number(matches ? matches[matches.length - 1] : 0)
}

function lastPathPart(path: string) {
  return pathPartFromEnd(path, 0)
}

function pathPartFromEnd(path: string, offset: number) {
  const parts = path.split('/')
  return parts[parts.length - 1 - offset] ?? ''
}

function scopeAt(index: number) {
  const memoryId = memoryIds[index % memoryIds.length]
  const [userId, agentId = null] = memoryId.split(':')
  return { memoryId, userId, agentId }
}

function pickForMemory<T extends { memoryId: string }>(
  rows: T[],
  memoryId: string,
  index: number
) {
  const scopedRows = rows.filter((row) => row.memoryId === memoryId)
  const pool = scopedRows.length ? scopedRows : rows
  return pool[index % pool.length]
}

function idsFromBody(body: unknown) {
  return (body as { ids?: number[] } | undefined)?.ids ?? []
}

function itemIdsFromBody(body: unknown) {
  return (body as { itemIds?: number[] } | undefined)?.itemIds ?? []
}

function insightIdsFromBody(body: unknown) {
  return (body as { insightIds?: number[] } | undefined)?.insightIds ?? []
}

function rawDataIdsFromBody(body: unknown) {
  return (body as { rawDataIds?: string[] } | undefined)?.rawDataIds ?? []
}

function updateResult(count: number): AdminUpdateResult {
  return { updatedCount: count, affectedMemoryIds: memoryIds }
}

function deleteResult(count: number): BatchDeleteResult {
  return { deletedCount: count, affectedMemoryIds: memoryIds }
}

function time(offsetHours: number) {
  return new Date(Date.UTC(2026, 4, 6, 6 + offsetHours)).toISOString()
}

function daily(days: number, base: number) {
  return Array.from({ length: days }, (_, index) => ({
    date: `2026-04-${String(24 + index).padStart(2, '0')}`,
    count: base + index,
  }))
}

function countBy<T, K extends keyof T>(rows: T[], key: K) {
  const countMap = new Map<string, number>()
  for (const row of rows) {
    const value = row[key]
    const name =
      value === null || value === undefined ? 'UNKNOWN' : String(value)
    countMap.set(name, (countMap.get(name) ?? 0) + 1)
  }
  return Array.from(countMap, ([name, count]) => ({ name, count }))
}

function traceStage(
  stage: string,
  tier: string,
  inputCount: number,
  resultCount: number
) {
  return {
    stage,
    tier,
    method: 'mock',
    status: 'OK',
    inputCount,
    candidateCount: inputCount + 3,
    resultCount,
    degraded: false,
    skipped: false,
    startedAt: time(1),
    durationMillis: 120 + resultCount * 10,
    attributes: { mock: true, stage },
    candidates: [{ id: `${stage}-candidate`, score: 0.87 }],
  }
}

function clone<T>(value: T): T {
  return JSON.parse(JSON.stringify(value)) as T
}

function delay(ms: number) {
  return new Promise((resolve) => globalThis.setTimeout(resolve, ms))
}

function notFound(message: string) {
  return {
    status: 404,
    code: 'mock_not_found',
    message,
  }
}
