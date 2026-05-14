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

import type { MemindOpenClawConfig, NormalizedMessage, OpenClawMessage } from './types.js'

const MEMIND_BLOCK_RE = /<memind_memories>[\s\S]*?<\/memind_memories>/g
const SENDER_METADATA_RE = /Sender\s*\(untrusted metadata\):\s*```json[\s\S]*?```\s*/gi
const CONVERSATION_METADATA_RE =
  /Conversation info\s*\(untrusted metadata\):\s*```json[\s\S]*?```\s*/gi
const INTERRUPTION_RE = /^\[(?:Request|Response) interrupted by user\]$/i

export function stripInjectedContext(text: string): string {
  return text
    .replace(MEMIND_BLOCK_RE, '')
    .replace(SENDER_METADATA_RE, '')
    .replace(CONVERSATION_METADATA_RE, '')
    .replace(/\n{3,}/g, '\n\n')
    .trim()
}

export function textFromContent(content: unknown): string {
  if (typeof content === 'string') return cleanText(content)
  if (!Array.isArray(content)) return ''
  const texts: string[] = []
  for (const block of content) {
    if (!block || typeof block !== 'object') continue
    const record = block as Record<string, unknown>
    if (record.type === 'text' && typeof record.text === 'string') {
      const text = cleanText(record.text)
      if (text) texts.push(text)
    }
  }
  return cleanText(texts.join('\n\n'))
}

export function containsUnsupportedBlocks(content: unknown): boolean {
  if (!Array.isArray(content)) return false
  return content.some((block) => {
    if (!block || typeof block !== 'object') return false
    const type = String((block as Record<string, unknown>).type ?? '').toLowerCase()
    return Boolean(type && type !== 'text')
  })
}

export function extractMessages(
  messages: OpenClawMessage[],
  cfg: MemindOpenClawConfig,
): NormalizedMessage[] {
  const allowed = new Set(cfg.ingestionRoles)
  const out: NormalizedMessage[] = []
  messages.forEach((message, index) => {
    const role = typeof message.role === 'string' ? message.role.toLowerCase() : ''
    if (!allowed.has(role as 'user' | 'assistant')) return
    if (containsUnsupportedBlocks(message.content)) return
    const text = textFromContent(message.content)
    if (isNoiseText(text)) return
    const normalizedRole = role === 'user' ? 'USER' : 'ASSISTANT'
    out.push({
      fingerprint: fingerprintMessage(message, normalizedRole, text, index),
      role: normalizedRole,
      content: [{ type: 'text', text }],
      timestamp: normalizeTimestamp(message.timestamp),
      userName: stringOrUndefined(message.userName ?? message.user_name),
    })
  })
  return out
}

export function readRecentContext(messages: OpenClawMessage[], cfg: MemindOpenClawConfig): string {
  if (cfg.retrieveContextTurns <= 0) return ''
  const pairs: Array<{ role: string; text: string }> = []
  for (const message of messages) {
    const role = typeof message.role === 'string' ? message.role.toLowerCase() : ''
    if (role !== 'user' && role !== 'assistant') continue
    const text = textFromContent(message.content)
    if (isNoiseText(text)) continue
    pairs.push({ role, text })
  }
  return pairs
    .slice(Math.max(0, pairs.length - cfg.retrieveContextTurns * 2))
    .map((entry) => `${entry.role}: ${entry.text}`)
    .join('\n')
}

function cleanText(text: string): string {
  return stripInjectedContext(text).trim()
}

function isNoiseText(text: string): boolean {
  const trimmed = text.trim()
  return !trimmed || INTERRUPTION_RE.test(trimmed)
}

function fingerprintMessage(
  message: OpenClawMessage,
  role: 'USER' | 'ASSISTANT',
  text: string,
  index: number,
): string {
  const source = {
    id: message.id ?? message.uuid,
    timestamp: message.timestamp,
    role,
    text,
    index,
  }
  return createHash('sha1').update(JSON.stringify(source)).digest('hex')
}

function normalizeTimestamp(value: unknown): string | undefined {
  if (typeof value === 'string') {
    return value
  }
  if (typeof value === 'number') {
    const date = new Date(value)
    return Number.isNaN(date.getTime()) ? undefined : date.toISOString()
  }
  return undefined
}

function stringOrUndefined(value: unknown): string | undefined {
  return typeof value === 'string' && value.trim() ? value : undefined
}
