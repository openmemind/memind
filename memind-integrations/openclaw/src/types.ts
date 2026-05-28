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

import type {
  ExtractMemoryResponse,
  MessageValue,
  RawContentValue,
  RetrieveMemoryResponse,
} from '@openmemind/memind'

export type SecretRef = {
  source: string
  provider?: string
  id: string
}

export type AgentIdMode = 'project' | 'fixed' | 'session'
export type RecallInjectionPosition = 'context' | 'system-prepend' | 'system-append'
export type IngestionRole = 'user' | 'assistant'
export type SlotConfigLike = {
  slots?: Record<string, string>
  memoryPluginId?: string
}
export type JsonValue =
  | string
  | number
  | boolean
  | null
  | JsonValue[]
  | { [key: string]: JsonValue }

export type MemindOpenClawConfig = {
  memindApiUrl: string
  memindApiToken?: string | SecretRef
  userId?: string
  agentId: string
  agentIdMode: AgentIdMode
  sourceClient: string
  autoRetrieve: boolean
  autoIngest: boolean
  autoIngestAgentTimeline: boolean
  retrieveStrategy: 'SIMPLE'
  retrieveMaxEntries: number
  retrieveMaxChars: number
  retrieveContextTurns: number
  retrievePromptPreamble: string
  recallInjectionPosition: RecallInjectionPosition
  ingestionRoles: IngestionRole[]
  ingestionMaxMessagesPerHook: number
  ingestRetrySpool: boolean
  stateMaxAgeDays: number
  ingestRetryMaxFiles: number
  ingestRetryMaxAgeDays: number
  timelineMaxEvents: number
  timelineMaxFieldChars: number
  timelineFlushMinEvents: number
  captureToolCalls: boolean
  captureMedia: boolean
  skipNonInteractiveTriggers: boolean
  captureSubagents: boolean
  debug: boolean
}

export type OpenClawMessage = {
  role?: string
  content?: unknown
  timestamp?: string | number
  userName?: string
  user_name?: string
  id?: string
  uuid?: string
  toolCallId?: string
  [key: string]: unknown
}

export type NormalizedMessage = MessageValue & {
  fingerprint: string
}

export type AgentTimelineEvent = {
  eventId?: string
  seq?: number
  kind?: string
  occurredAt?: string
  text?: string
  toolName?: string
  input?: string
  output?: string
  status?: string
  durationMs?: number
  contentHash?: string
  path?: string
  operation?: string
  command?: string
  exitCode?: number
  metadata?: Record<string, JsonValue>
}

export type AgentTimelineContent = {
  type: 'agent_timeline'
  sourceClient?: string
  sourceVersion?: string
  sessionId: string
  agentTurnId: string
  timelineId: string
  events: AgentTimelineEvent[]
  project?: Record<string, JsonValue>
  metadata?: Record<string, JsonValue>
}

export type OpenClawTimelineContext = {
  sessionKey: string
  workspaceDir?: string
  channelId?: string
  conversationId?: string
  agentName?: string
  turnId?: string
}

export type Identity = {
  userId: string
  agentId: string
  sourceClient: string
  sessionKey?: string
}

export type MemindMemoryClient = {
  retrieve(
    request: {
      userId: string
      agentId: string
      query: string
      strategy: 'SIMPLE'
      trace?: boolean
    },
    options?: { timeoutMs?: number; maxRetries?: number; signal?: AbortSignal },
  ): Promise<RetrieveMemoryResponse>
  extract(
    request: {
      userId: string
      agentId: string
      rawContent: RawContentValue
      sourceClient?: string
    },
    options?: { timeoutMs?: number; maxRetries?: number; signal?: AbortSignal },
  ): Promise<ExtractMemoryResponse>
}
