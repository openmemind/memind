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

import type { RawContentValue } from '@openmemind/memind'

import { buildAgentTimelineContent } from './agent-timeline.js'
import { isNonInteractiveTrigger, isSubagentSession } from './identity.js'
import type {
  AgentTimelineEvent,
  Identity,
  MemindMemoryClient,
  MemindOpenClawConfig,
  OpenClawTimelineContext,
} from './types.js'

export type CaptureInput = {
  cfg: MemindOpenClawConfig
  client: MemindMemoryClient
  identity: Identity
  event: Record<string, unknown>
  ctx?: Record<string, unknown>
  state: {
    listAgentEvents(sessionKey: string): Promise<AgentTimelineEvent[]>
    clearAgentEvents(sessionKey: string, eventIds?: string[]): Promise<void>
  }
  retry?: {
    enqueue(entry: {
      kind: 'extract'
      userId: string
      agentId: string
      sourceClient: string
      sessionKey: string
      fingerprints: string[]
      rawContent: RawContentValue
    }): Promise<void>
  }
  logger?: { warn(message: string): void; debug(message: string): void }
}

export async function handleCapture(input: CaptureInput): Promise<{ submitted: number }> {
  const { cfg, client, identity, event, ctx, state, retry, logger } = input
  if (!cfg.autoIngest || !cfg.autoIngestAgentTimeline || event.success === false) {
    return { submitted: 0 }
  }
  const trigger = typeof ctx?.trigger === 'string' ? ctx.trigger : undefined
  const sessionKey =
    typeof ctx?.sessionKey === 'string'
      ? ctx.sessionKey
      : typeof identity.sessionKey === 'string'
        ? identity.sessionKey
        : 'default'
  if (cfg.skipNonInteractiveTriggers && isNonInteractiveTrigger(trigger, sessionKey)) {
    return { submitted: 0 }
  }
  if (!cfg.captureSubagents && isSubagentSession(sessionKey)) return { submitted: 0 }

  const events = await state.listAgentEvents(sessionKey)
  if (events.length < cfg.timelineFlushMinEvents) return { submitted: 0 }
  const selectedFingerprints = events
    .map((event) => event.eventId)
    .filter((eventId): eventId is string => Boolean(eventId))
  const timelineContext = contextFrom(ctx, event, sessionKey, events)
  const rawContent = buildAgentTimelineContent({
    sourceClient: identity.sourceClient,
    sessionId: sessionKey,
    agentTurnId: timelineContext.turnId ?? `${sessionKey}-turn`,
    events,
    context: timelineContext,
  })

  try {
    const response = await client.extract(
      {
        userId: identity.userId,
        agentId: identity.agentId,
        rawContent,
        sourceClient: identity.sourceClient,
      },
      { timeoutMs: 15_000, maxRetries: 0 },
    )
    if (response.status === 'SUCCESS') {
      await state.clearAgentEvents(sessionKey, selectedFingerprints)
      return { submitted: events.length }
    }
    await retry?.enqueue({
      kind: 'extract',
      userId: identity.userId,
      agentId: identity.agentId,
      sourceClient: identity.sourceClient,
      sessionKey,
      fingerprints: selectedFingerprints,
      rawContent,
    })
    return { submitted: 0 }
  } catch (error: unknown) {
    logger?.warn(`memind-openclaw: capture failed: ${String(error)}`)
    await retry?.enqueue({
      kind: 'extract',
      userId: identity.userId,
      agentId: identity.agentId,
      sourceClient: identity.sourceClient,
      sessionKey,
      fingerprints: selectedFingerprints,
      rawContent,
    })
    return { submitted: 0 }
  }
}

function contextFrom(
  ctx: Record<string, unknown> | undefined,
  event: Record<string, unknown>,
  sessionKey: string,
  events: AgentTimelineEvent[],
): OpenClawTimelineContext {
  return {
    sessionKey,
    turnId:
      stringValue(ctx?.turnId ?? event.turnId) ?? latestEventTurnId(events) ?? `${sessionKey}-turn`,
    workspaceDir: stringValue(ctx?.workspaceDir ?? event.workspaceDir ?? ctx?.cwd ?? event.cwd),
    channelId: stringValue(ctx?.channelId ?? event.channelId),
    conversationId: stringValue(ctx?.conversationId ?? event.conversationId),
    agentName: stringValue(ctx?.agentName ?? event.agentName),
  }
}

function stringValue(value: unknown): string | undefined {
  return typeof value === 'string' && value.trim() ? value.trim() : undefined
}

function latestEventTurnId(events: AgentTimelineEvent[]): string | undefined {
  for (let index = events.length - 1; index >= 0; index -= 1) {
    const turnId = events[index]?.metadata?.turnId
    if (typeof turnId === 'string' && turnId.trim()) return turnId
  }
  return undefined
}
