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

import { Role } from './common.js'

export type UrlSource = {
  type: 'url'
  url: string
}

export type Base64Source = {
  type: 'base64'
  media_type: string
  data: string
}

export type Source = UrlSource | Base64Source

export type TextBlock = { type: 'text'; text: string }
export type ImageBlock = { type: 'image'; source: Source }
export type AudioBlock = { type: 'audio'; source: Source }
export type VideoBlock = { type: 'video'; source: Source }

export type ContentBlock = TextBlock | ImageBlock | AudioBlock | VideoBlock

export type MessageValue = {
  role: Role
  content: ContentBlock[]
  timestamp?: string
  userName?: string
  sourceClient?: string
}

export type MessageOptions = {
  timestamp?: string
}

export const Message = {
  user(text: string, options?: MessageOptions): MessageValue {
    return {
      role: Role.USER,
      content: [{ type: 'text', text }],
      ...options,
    }
  },
  assistant(text: string, options?: MessageOptions): MessageValue {
    return {
      role: Role.ASSISTANT,
      content: [{ type: 'text', text }],
      ...options,
    }
  },
} as const

export type JsonPrimitive = string | number | boolean | null
export type JsonValue = JsonPrimitive | JsonValue[] | { [key: string]: JsonValue }

export type ConversationContent = {
  type: 'conversation'
  messages: MessageValue[]
}

export type JsonObjectRawContent = {
  type: string
  [key: string]: JsonValue
}

export type AgentTimelineEvent = {
  eventId?: string
  seq?: number
  kind?: string
  occurredAt?: string
  text?: string
  toolName?: string
  input?: JsonValue
  output?: JsonValue
  status?: string
  durationMs?: number
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

export type RawContentValue = ConversationContent | AgentTimelineContent | JsonObjectRawContent

export const RawContent = {
  conversation(messages: MessageValue[]): ConversationContent {
    if (!Array.isArray(messages) || messages.length === 0) {
      throw new Error('RawContent.conversation requires a non-empty messages array')
    }
    for (const message of messages) {
      assertMessage(message)
    }
    return { type: 'conversation', messages }
  },

  map(type: string, fields?: Record<string, JsonValue>): JsonObjectRawContent {
    if (!type || !type.trim()) {
      throw new Error('RawContent.map requires a non-blank type')
    }
    if (type === 'conversation') {
      throw new Error(
        'RawContent.map cannot use type "conversation" - use RawContent.conversation() instead',
      )
    }
    if (fields && 'type' in fields) {
      throw new Error('RawContent.map fields must not contain a "type" key')
    }
    if (fields) {
      assertJsonValue(fields, 'fields')
    }
    return { type, ...fields }
  },
} as const

export function assertRawContent(rawContent: RawContentValue): void {
  if (!rawContent || typeof rawContent !== 'object' || Array.isArray(rawContent)) {
    throw new Error('rawContent must be an object')
  }
  if (typeof rawContent.type !== 'string' || !rawContent.type.trim()) {
    throw new Error('rawContent.type must be a non-blank string')
  }
  if (rawContent.type === 'conversation') {
    const messages = (rawContent as { messages?: unknown }).messages
    if (!Array.isArray(messages) || messages.length === 0) {
      throw new Error('conversation rawContent requires a non-empty messages array')
    }
    for (const message of messages) {
      assertMessage(message as MessageValue)
    }
    return
  }
  assertJsonValue(rawContent, 'rawContent')
}

export function assertMessage(message: MessageValue): void {
  if (!message || typeof message !== 'object') {
    throw new Error('Message must be an object')
  }
  if (message.role !== Role.USER && message.role !== Role.ASSISTANT) {
    throw new Error('Message role must be USER or ASSISTANT')
  }
  if (!Array.isArray(message.content) || message.content.length === 0) {
    throw new Error('Message content must be a non-empty array')
  }
  for (const block of message.content) {
    assertContentBlock(block)
  }
}

function assertContentBlock(block: ContentBlock): void {
  if (!block || typeof block !== 'object') {
    throw new Error('Content block must be an object')
  }
  if (block.type === 'text') {
    if (typeof block.text !== 'string') {
      throw new Error('Text content block requires string text')
    }
    return
  }
  if (block.type === 'image' || block.type === 'audio' || block.type === 'video') {
    assertSource(block.source)
    return
  }
  throw new Error(`Unsupported content block type: ${(block as { type?: unknown }).type}`)
}

function assertSource(source: Source): void {
  if (!source || typeof source !== 'object') {
    throw new Error('Media content block requires a source object')
  }
  if (source.type === 'url') {
    if (typeof source.url !== 'string' || !source.url.trim()) {
      throw new Error('URL source requires a non-blank url')
    }
    return
  }
  if (source.type === 'base64') {
    if (typeof source.media_type !== 'string' || !source.media_type.trim()) {
      throw new Error('Base64 source requires a non-blank media_type')
    }
    if (typeof source.data !== 'string' || !source.data) {
      throw new Error('Base64 source requires data')
    }
    return
  }
  throw new Error(`Unsupported source type: ${(source as { type?: unknown }).type}`)
}

function assertJsonValue(value: unknown, path: string, seen = new WeakSet<object>()): void {
  if (
    value === null ||
    typeof value === 'string' ||
    typeof value === 'boolean' ||
    (typeof value === 'number' && Number.isFinite(value))
  ) {
    return
  }
  if (
    typeof value !== 'object' ||
    value === undefined ||
    typeof value === 'function' ||
    typeof value === 'symbol' ||
    typeof value === 'bigint'
  ) {
    throw new Error(`RawContent.map field "${path}" must be JSON-serializable`)
  }
  if (seen.has(value)) {
    throw new Error(`RawContent.map field "${path}" must not be cyclic`)
  }
  seen.add(value)
  try {
    if (Array.isArray(value)) {
      value.forEach((item, index) => assertJsonValue(item, `${path}[${index}]`, seen))
      return
    }
    for (const [key, item] of Object.entries(value)) {
      assertJsonValue(item, `${path}.${key}`, seen)
    }
  } finally {
    seen.delete(value)
  }
}
