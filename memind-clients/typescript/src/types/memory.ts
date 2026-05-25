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
