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

import { readRecentContext, stripInjectedContext } from './content.js'
import { formatMemindContext } from './format.js'
import { isNonInteractiveTrigger } from './identity.js'
import type {
  Identity,
  MemindMemoryClient,
  MemindOpenClawConfig,
  OpenClawMessage,
} from './types.js'

export type RecallInput = {
  cfg: MemindOpenClawConfig
  client: MemindMemoryClient
  identity: Identity
  event: Record<string, unknown>
  ctx?: Record<string, unknown>
  logger?: { warn(message: string): void; debug(message: string): void }
}

export async function handleRecall(
  input: RecallInput,
): Promise<Record<string, string> | undefined> {
  const { cfg, client, identity, event, ctx, logger } = input
  if (!cfg.autoRetrieve) return undefined
  const trigger = typeof ctx?.trigger === 'string' ? ctx.trigger : undefined
  const sessionKey = typeof ctx?.sessionKey === 'string' ? ctx.sessionKey : undefined
  if (cfg.skipNonInteractiveTriggers && isNonInteractiveTrigger(trigger, sessionKey))
    return undefined

  const prompt = extractPrompt(event)
  if (!prompt || prompt.length < 5 || isBootstrapPrompt(prompt)) return undefined
  const recent = Array.isArray(event.messages)
    ? readRecentContext(event.messages as OpenClawMessage[], cfg)
    : ''
  const query = recent ? `${recent}\ncurrent: ${prompt}` : prompt

  try {
    const result = await client.retrieve(
      {
        userId: identity.userId,
        agentId: identity.agentId,
        query,
        strategy: cfg.retrieveStrategy,
        trace: false,
      },
      { timeoutMs: 12_000, maxRetries: 0 },
    )
    const context = formatMemindContext(result, cfg)
    if (!context) return undefined
    if (cfg.recallInjectionPosition === 'system-prepend') return { prependSystemContext: context }
    if (cfg.recallInjectionPosition === 'system-append') return { appendSystemContext: context }
    return { prependContext: context }
  } catch (error: unknown) {
    logger?.warn(`memind-openclaw: recall failed: ${String(error)}`)
    return undefined
  }
}

function extractPrompt(event: Record<string, unknown>): string {
  const raw =
    typeof event.rawMessage === 'string'
      ? event.rawMessage
      : typeof event.prompt === 'string'
        ? event.prompt
        : ''
  return stripInjectedContext(raw)
}

function isBootstrapPrompt(prompt: string): boolean {
  const lower = prompt.toLowerCase()
  return (
    lower.includes('a new session was started') ||
    lower.includes('session startup sequence') ||
    lower.includes('/new or /reset')
  )
}
