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
import { userInfo } from 'node:os'
import path from 'node:path'

import type { Identity, MemindOpenClawConfig } from './types.js'

const NON_INTERACTIVE_TRIGGERS = new Set(['cron', 'heartbeat', 'automation', 'schedule'])

export type IdentityContext = {
  cwd?: string
  gitRemoteUrl?: string
  sessionKey?: string
  systemUser?: string
}

export function resolveIdentity(cfg: MemindOpenClawConfig, ctx: IdentityContext = {}): Identity {
  const userId = cfg.userId ?? `local__${sanitizeIdentityPart(ctx.systemUser ?? currentUser())}`
  return {
    userId,
    agentId: resolveAgentId(cfg, ctx),
    sourceClient: cfg.sourceClient,
    sessionKey: ctx.sessionKey,
  }
}

export function isNonInteractiveTrigger(
  trigger: string | undefined,
  sessionKey: string | undefined,
): boolean {
  if (trigger && NON_INTERACTIVE_TRIGGERS.has(trigger.toLowerCase())) return true
  if (!sessionKey) return false
  return /:(cron|heartbeat|automation|schedule)(:|$)/i.test(sessionKey)
}

export function isSubagentSession(sessionKey: string | undefined): boolean {
  return Boolean(sessionKey && /:subagent:/i.test(sessionKey))
}

export function sanitizeIdentityPart(value: string): string {
  const base =
    value
      .trim()
      .replace(/\.git$/i, '')
      .split(/[\\/]/)
      .filter(Boolean)
      .pop() ?? ''
  const normalized = base
    .toLowerCase()
    .replace(/[^a-z0-9_-]+/g, '-')
    .replace(/^-+|-+$/g, '')
  return normalized || 'default'
}

function resolveAgentId(cfg: MemindOpenClawConfig, ctx: IdentityContext): string {
  if (cfg.agentIdMode === 'fixed') return cfg.agentId
  const source =
    cfg.agentIdMode === 'session'
      ? (ctx.sessionKey ?? ctx.cwd ?? 'default-session')
      : (ctx.gitRemoteUrl ?? ctx.cwd ?? process.cwd())
  const label = cfg.agentIdMode === 'session' ? 'session' : sanitizeIdentityPart(source)
  return `${cfg.agentId}__${label}-${stableHash(source)}`
}

function stableHash(value: string): string {
  return createHash('sha1').update(value).digest('hex').slice(0, 10)
}

function currentUser(): string {
  try {
    return userInfo().username || 'default'
  } catch {
    return path.basename(process.cwd()) || 'default'
  }
}
