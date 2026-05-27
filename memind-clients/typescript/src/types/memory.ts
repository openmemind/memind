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

import type { Strategy } from './common.js'
import type { MessageValue, RawContentValue } from './message.js'

export type MetadataCondition = {
  path: string
  op: 'eq' | 'in' | 'exists' | 'missing' | 'contains' | string
  value?: unknown
}

export type MetadataFilter = {
  all?: MetadataCondition[]
  any?: MetadataCondition[]
  not?: MetadataCondition[]
}

export type TimeRange = {
  field?: string
  from?: string
  to?: string
}

export type RetrieveIncludeOptions = {
  rawDataMetadata?: boolean
  rawDataSegment?: boolean
}

export type RawDataQueryIncludeOptions = {
  segment?: boolean
  metadata?: boolean
}

export type ExtractMemoryRequest = {
  userId: string
  agentId: string
  rawContent: RawContentValue
  sourceClient?: string
}

export type AddMessageRequest = {
  userId: string
  agentId: string
  message: MessageValue
  sourceClient?: string
}

export type CommitMemoryRequest = {
  userId: string
  agentId: string
  sourceClient?: string
}

export type RetrieveMemoryRequest = {
  userId: string
  agentId: string
  query: string
  strategy: Strategy
  trace?: boolean
  scope?: string
  categories?: string[]
  timeRange?: TimeRange
  metadataFilter?: MetadataFilter
  include?: RetrieveIncludeOptions
}

export type QueryMemoryItemsRequest = {
  userId: string
  agentId: string
  scope?: string
  categories?: string[]
  sourceClients?: string[]
  rawDataTypes?: string[]
  timeRange?: TimeRange
  metadataFilter?: MetadataFilter
  limit?: number
  cursor?: string
}

export type QueryMemoryRawDataRequest = {
  userId: string
  agentId: string
  types?: string[]
  sourceClients?: string[]
  timeRange?: TimeRange
  metadataFilter?: MetadataFilter
  include?: RawDataQueryIncludeOptions
  limit?: number
  cursor?: string
}

export type ExtractMemoryResponse = {
  status: string
  rawDataIds: string[]
  itemIds: number[]
  insightIds: number[]
  insightPending: boolean
  durationMillis?: number
  errorMessage?: string
}

export type AddMessageResponse = {
  triggered: boolean
  result?: ExtractMemoryResponse
}

export type RetrievedItem = {
  id: string
  text: string
  vectorScore: number
  finalScore: number
  occurredAt?: string
  category?: string
  metadata?: Record<string, unknown>
}

export type RetrievedInsight = {
  id: string
  text: string
  tier?: string
}

export type RetrievedRawData = {
  rawDataId: string
  caption?: string
  maxScore: number
  itemIds?: string[]
  type?: string
  sourceClient?: string
  metadata?: Record<string, unknown>
  startTime?: string
  endTime?: string
  createdAt?: string
}

export type StageView = {
  stage?: string
  tier?: string
  method?: string
  status?: string
  inputCount?: number
  candidateCount?: number
  resultCount?: number
  degraded: boolean
  skipped: boolean
  startedAt?: string
  durationMillis?: number
  attributes?: Record<string, unknown>
  candidates?: Record<string, unknown>[]
}

export type MergeView = {
  inputCount: number
  outputCount: number
  deduplicatedCount: number
  sourceCount: number
  status?: string
}

export type FinalView = {
  strategy?: string
  status?: string
  itemCount: number
  insightCount: number
  rawDataCount: number
  evidenceCount: number
}

export type RetrievalTraceView = {
  traceId?: string
  startedAt?: string
  completedAt?: string
  truncated?: boolean
  stages: StageView[]
  merge?: MergeView
  finalResults?: FinalView
}

export type RetrieveMemoryResponse = {
  status?: string
  items: RetrievedItem[]
  insights: RetrievedInsight[]
  rawData: RetrievedRawData[]
  evidences: string[]
  strategy?: string
  query?: string
  trace?: RetrievalTraceView
}

export type MemoryItem = {
  id: string
  text: string
  scope?: string
  category?: string
  type?: string
  rawDataId?: string
  rawDataType?: string
  sourceClient?: string
  occurredAt?: string
  observedAt?: string
  createdAt?: string
  metadata?: Record<string, unknown>
}

export type QueryMemoryItemsResponse = {
  items: MemoryItem[]
  nextCursor?: string
}

export type MemoryRawData = {
  id: string
  type?: string
  sourceClient?: string
  caption?: string
  metadata?: Record<string, unknown>
  segment?: Record<string, unknown>
  startTime?: string
  endTime?: string
  createdAt?: string
}

export type QueryMemoryRawDataResponse = {
  rawData: MemoryRawData[]
  nextCursor?: string
}
