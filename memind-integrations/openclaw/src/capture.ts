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

import { RawContent, type RawContentValue } from '@openmemind/memind'

import { extractMessages } from './content.js'
import { isNonInteractiveTrigger, isSubagentSession } from './identity.js'
import type {
  Identity,
  MemindMemoryClient,
  MemindOpenClawConfig,
  OpenClawMessage,
} from './types.js'

export type CaptureInput = {
  cfg: MemindOpenClawConfig
  client: MemindMemoryClient
  identity: Identity
  event: Record<string, unknown>
  ctx?: Record<string, unknown>
  state: {
    filterUnsubmitted(sessionKey: string, fingerprints: string[]): Promise<string[]>
    markSubmitted(sessionKey: string, fingerprints: string[]): Promise<void>
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
  if (!cfg.autoIngest || event.success === false) return { submitted: 0 }
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

  const rawMessages = selectMessages(event)
  const messages = selectFinalTurn(extractMessages(rawMessages, cfg))
  if (messages.length === 0) return { submitted: 0 }
  const byFingerprint = new Map(messages.map((message) => [message.fingerprint, message]))
  const selectedFingerprints = (
    await state.filterUnsubmitted(sessionKey, [...byFingerprint.keys()])
  ).slice(0, cfg.ingestionMaxMessagesPerHook)
  if (selectedFingerprints.length === 0) return { submitted: 0 }
  const selectedMessages = selectedFingerprints
    .map((fingerprint) => byFingerprint.get(fingerprint))
    .filter((message): message is NonNullable<typeof message> => Boolean(message))
  if (selectedMessages.length === 0) return { submitted: 0 }
  const rawContent = RawContent.conversation(
    selectedMessages.map(({ fingerprint: _fingerprint, ...message }) => message),
  )

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
      await state.markSubmitted(sessionKey, selectedFingerprints)
      return { submitted: selectedFingerprints.length }
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

function selectMessages(event: Record<string, unknown>): OpenClawMessage[] {
  const context = event.context as { sessionEntry?: { messages?: unknown } } | undefined
  const fromContext = context?.sessionEntry?.messages
  if (Array.isArray(fromContext)) return fromContext as OpenClawMessage[]
  return Array.isArray(event.messages) ? (event.messages as OpenClawMessage[]) : []
}

function selectFinalTurn<T extends { role: 'USER' | 'ASSISTANT' }>(messages: T[]): T[] {
  let lastUserIndex = -1
  for (let index = messages.length - 1; index >= 0; index -= 1) {
    if (messages[index]?.role === 'USER') {
      lastUserIndex = index
      break
    }
  }
  if (lastUserIndex < 0) return messages
  return messages.slice(lastUserIndex)
}
