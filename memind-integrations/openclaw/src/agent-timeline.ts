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

import { createHash } from 'node:crypto'
import path from 'node:path'

import { stripInjectedContext, textFromContent } from './content.js'
import type { AgentTimelineContent, AgentTimelineEvent, OpenClawTimelineContext } from './types.js'

export function normalizeUserPromptEvent(input: {
  prompt?: unknown
  rawMessage?: unknown
  seq: number
  context: OpenClawTimelineContext
  maxFieldChars: number
}): AgentTimelineEvent | undefined {
  const text = clampText(textFromPrompt(input.prompt, input.rawMessage), input.maxFieldChars)
  if (!text) return undefined
  return event({
    kind: 'user_prompt',
    seq: input.seq,
    text,
    context: input.context,
  })
}

export function normalizeAssistantMessageEvent(input: {
  messages?: unknown
  seq: number
  context: OpenClawTimelineContext
  maxFieldChars: number
}): AgentTimelineEvent | undefined {
  const text = clampText(lastAssistantText(input.messages), input.maxFieldChars)
  if (!text) return undefined
  return event({
    kind: 'assistant_message',
    seq: input.seq,
    text,
    context: input.context,
  })
}

export function normalizeToolResultEvent(input: {
  event: Record<string, unknown>
  seq: number
  context: OpenClawTimelineContext
  maxFieldChars: number
}): AgentTimelineEvent | undefined {
  const toolName = firstString(input.event.toolName, input.event.name, input.event.tool)
  if (!toolName) return undefined
  const failed = isFailed(input.event)
  return event({
    kind: failed ? 'tool_failure' : 'tool_result',
    seq: input.seq,
    toolName,
    input: jsonText(
      input.event.input ?? input.event.args ?? input.event.arguments,
      input.maxFieldChars,
    ),
    output: jsonText(
      input.event.output ?? input.event.result ?? input.event.error ?? input.event.errorMessage,
      input.maxFieldChars,
    ),
    status: failed ? 'failed' : 'success',
    durationMs: numberValue(input.event.durationMs ?? input.event.elapsedMs),
    context: input.context,
    metadata: compactMetadata({
      operation: stringValue(input.event.operation),
      toolCallId: stringValue(input.event.toolCallId ?? input.event.id),
    }),
  })
}

export function normalizeCompactBoundaryEvent(input: {
  event: Record<string, unknown>
  seq: number
  context: OpenClawTimelineContext
}): AgentTimelineEvent {
  return event({
    kind: 'compact_boundary',
    seq: input.seq,
    status: 'success',
    context: input.context,
    metadata: compactMetadata({ reason: stringValue(input.event.reason) }),
  })
}

export function normalizeStopEvent(input: {
  success?: unknown
  seq: number
  context: OpenClawTimelineContext
}): AgentTimelineEvent {
  return event({
    kind: 'stop',
    seq: input.seq,
    status: input.success === false ? 'failed' : 'success',
    context: input.context,
  })
}

export function normalizeSessionEndEvent(input: {
  event: Record<string, unknown>
  seq: number
  context: OpenClawTimelineContext
}): AgentTimelineEvent {
  return event({
    kind: 'session_end',
    seq: input.seq,
    status: input.event.success === false ? 'failed' : 'success',
    context: input.context,
    metadata: compactMetadata({ reason: stringValue(input.event.reason) }),
  })
}

export function buildAgentTimelineContent(input: {
  sourceClient: string
  sessionId: string
  agentTurnId: string
  events: AgentTimelineEvent[]
  context: OpenClawTimelineContext
}): AgentTimelineContent {
  const events = input.events
    .filter((event): event is AgentTimelineEvent => Boolean(event))
    .sort((a, b) => (a.seq ?? 0) - (b.seq ?? 0))
  const timelineId = `openclaw-${shortHash({
    sourceClient: input.sourceClient,
    sessionId: input.sessionId,
    agentTurnId: input.agentTurnId,
    eventIds: events.map((event) => event.eventId),
  })}`
  const project = input.context.workspaceDir
    ? {
        name: path.basename(input.context.workspaceDir),
        rootPath: input.context.workspaceDir,
        metadata: {
          projectSlug: path.basename(input.context.workspaceDir),
        },
      }
    : undefined
  return {
    type: 'agent_timeline',
    sourceClient: input.sourceClient,
    sessionId: input.sessionId,
    agentTurnId: input.agentTurnId,
    timelineId,
    events,
    ...(project ? { project } : {}),
    metadata: compactMetadata({
      profile: 'general',
      runtime: 'openclaw',
      sessionKey: input.context.sessionKey,
      turnId: input.context.turnId ?? input.agentTurnId,
      channelId: input.context.channelId,
      conversationId: input.context.conversationId,
      workspaceDir: input.context.workspaceDir,
      agentName: input.context.agentName,
    }),
  }
}

function event(input: {
  kind: string
  seq: number
  context: OpenClawTimelineContext
  text?: string
  toolName?: string
  input?: string
  output?: string
  status?: string
  durationMs?: number
  metadata?: Record<string, unknown>
}): AgentTimelineEvent {
  const occurredAt = new Date().toISOString()
  const contentHash = hash({
    kind: input.kind,
    seq: input.seq,
    text: input.text,
    toolName: input.toolName,
    input: input.input,
    output: input.output,
    status: input.status,
    context: input.context,
  })
  return {
    eventId: `openclaw-${input.context.sessionKey}-${input.seq}-${contentHash.slice(0, 12)}`,
    seq: input.seq,
    kind: input.kind,
    occurredAt,
    ...(input.text ? { text: input.text } : {}),
    ...(input.toolName ? { toolName: input.toolName } : {}),
    ...(input.input ? { input: input.input } : {}),
    ...(input.output ? { output: input.output } : {}),
    ...(input.status ? { status: input.status } : {}),
    ...(input.durationMs !== undefined ? { durationMs: input.durationMs } : {}),
    contentHash,
    metadata: compactMetadata({
      sessionKey: input.context.sessionKey,
      turnId: input.context.turnId,
      channelId: input.context.channelId,
      conversationId: input.context.conversationId,
      workspaceDir: input.context.workspaceDir,
      agentName: input.context.agentName,
      ...input.metadata,
    }),
  }
}

function textFromPrompt(prompt: unknown, rawMessage: unknown): string {
  if (typeof prompt === 'string') return stripInjectedContext(prompt)
  if (typeof rawMessage === 'string') return stripInjectedContext(rawMessage)
  if (rawMessage && typeof rawMessage === 'object') {
    const message = rawMessage as Record<string, unknown>
    return textFromContent(message.content ?? message.text)
  }
  return ''
}

function lastAssistantText(messages: unknown): string {
  if (!Array.isArray(messages)) return ''
  for (let index = messages.length - 1; index >= 0; index -= 1) {
    const message = messages[index]
    if (!message || typeof message !== 'object') continue
    const record = message as Record<string, unknown>
    const role = typeof record.role === 'string' ? record.role.toLowerCase() : ''
    if (role === 'assistant') return textFromContent(record.content ?? record.text)
  }
  return ''
}

function isFailed(event: Record<string, unknown>): boolean {
  if (event.success === false) return true
  const status = stringValue(event.status)?.toLowerCase()
  return status === 'failed' || status === 'error' || status === 'cancelled' || Boolean(event.error)
}

function jsonText(value: unknown, maxChars: number): string | undefined {
  if (value === undefined || value === null) return undefined
  const text = typeof value === 'string' ? value : stableJson(value)
  return clampText(text, maxChars) || undefined
}

function clampText(value: string, maxChars: number): string {
  const text = stripInjectedContext(value)
  if (text.length <= maxChars) return text
  const suffix = '\n[truncated]'
  return text.slice(0, Math.max(0, maxChars - suffix.length)).trimEnd() + suffix
}

function compactMetadata(
  values: Record<string, unknown>,
): Record<string, string | number | boolean> {
  const out: Record<string, string | number | boolean> = {}
  for (const [key, value] of Object.entries(values)) {
    if (typeof value === 'string' && value.trim()) out[key] = value
    if (typeof value === 'number' && Number.isFinite(value)) out[key] = value
    if (typeof value === 'boolean') out[key] = value
  }
  return out
}

function firstString(...values: unknown[]): string | undefined {
  for (const value of values) {
    const text = stringValue(value)
    if (text) return text
  }
  return undefined
}

function stringValue(value: unknown): string | undefined {
  return typeof value === 'string' && value.trim() ? value.trim() : undefined
}

function numberValue(value: unknown): number | undefined {
  return typeof value === 'number' && Number.isFinite(value) ? value : undefined
}

function shortHash(value: unknown): string {
  return hash(value).slice(0, 16)
}

function hash(value: unknown): string {
  return createHash('sha256').update(stableJson(value)).digest('hex')
}

function stableJson(value: unknown): string {
  return JSON.stringify(sortJson(value))
}

function sortJson(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(sortJson)
  if (!value || typeof value !== 'object') return value
  return Object.fromEntries(
    Object.entries(value as Record<string, unknown>)
      .sort(([left], [right]) => left.localeCompare(right))
      .map(([key, item]) => [key, sortJson(item)]),
  )
}
