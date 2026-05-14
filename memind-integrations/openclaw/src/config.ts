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
  AgentIdMode,
  IngestionRole,
  MemindOpenClawConfig,
  RecallInjectionPosition,
  SecretRef,
  SlotConfigLike,
} from './types.js'

export const PLUGIN_ID = 'memind-openclaw'

export const DEFAULT_CONFIG: MemindOpenClawConfig = {
  memindApiUrl: 'http://127.0.0.1:8366',
  agentId: 'openclaw',
  agentIdMode: 'project',
  sourceClient: 'openclaw',
  autoRetrieve: true,
  autoIngest: true,
  retrieveStrategy: 'SIMPLE',
  retrieveMaxEntries: 8,
  retrieveMaxChars: 6000,
  retrieveContextTurns: 1,
  retrievePromptPreamble: 'Relevant memories from Memind. Use only when directly helpful:',
  recallInjectionPosition: 'context',
  ingestionRoles: ['user', 'assistant'],
  ingestionMaxMessagesPerHook: 20,
  ingestRetrySpool: true,
  stateMaxAgeDays: 14,
  ingestRetryMaxFiles: 20,
  ingestRetryMaxAgeDays: 7,
  captureToolCalls: false,
  captureMedia: false,
  skipNonInteractiveTriggers: true,
  captureSubagents: false,
  debug: false,
}

const ALLOWED_KEYS = new Set(Object.keys(DEFAULT_CONFIG).concat(['memindApiToken', 'userId']))
const AGENT_ID_MODES = new Set<AgentIdMode>(['project', 'fixed', 'session'])
const INJECTION_POSITIONS = new Set<RecallInjectionPosition>([
  'context',
  'system-prepend',
  'system-append',
])
const RETRIEVE_STRATEGIES = new Set<MemindOpenClawConfig['retrieveStrategy']>(['SIMPLE'])
const INGESTION_ROLES = new Set<IngestionRole>(['user', 'assistant'])

export function parseConfig(value: unknown): MemindOpenClawConfig {
  const raw = objectValue(value, 'config')
  const unknown = Object.keys(raw).filter((key) => !ALLOWED_KEYS.has(key))
  if (unknown.length > 0) {
    throw new Error(`memind-openclaw config has unknown keys: ${unknown.join(', ')}`)
  }

  const cfg: MemindOpenClawConfig = { ...DEFAULT_CONFIG }

  cfg.memindApiUrl = stringValue(raw.memindApiUrl, cfg.memindApiUrl, 'memindApiUrl').replace(
    /\/+$/,
    '',
  )
  cfg.memindApiToken = tokenValue(raw.memindApiToken)
  cfg.userId = optionalString(raw.userId, 'userId')
  cfg.agentId = stringValue(raw.agentId, cfg.agentId, 'agentId')
  cfg.agentIdMode = enumValue(raw.agentIdMode, cfg.agentIdMode, AGENT_ID_MODES, 'agentIdMode')
  cfg.sourceClient = stringValue(raw.sourceClient, cfg.sourceClient, 'sourceClient')
  cfg.autoRetrieve = booleanValue(raw.autoRetrieve, cfg.autoRetrieve, 'autoRetrieve')
  cfg.autoIngest = booleanValue(raw.autoIngest, cfg.autoIngest, 'autoIngest')
  cfg.retrieveStrategy = enumValue(
    raw.retrieveStrategy,
    cfg.retrieveStrategy,
    RETRIEVE_STRATEGIES,
    'retrieveStrategy',
  )
  cfg.retrieveMaxEntries = positiveInteger(
    raw.retrieveMaxEntries,
    cfg.retrieveMaxEntries,
    'retrieveMaxEntries',
  )
  cfg.retrieveMaxChars = positiveInteger(
    raw.retrieveMaxChars,
    cfg.retrieveMaxChars,
    'retrieveMaxChars',
  )
  cfg.retrieveContextTurns = nonNegativeInteger(
    raw.retrieveContextTurns,
    cfg.retrieveContextTurns,
    'retrieveContextTurns',
  )
  cfg.retrievePromptPreamble = stringValue(
    raw.retrievePromptPreamble,
    cfg.retrievePromptPreamble,
    'retrievePromptPreamble',
  )
  cfg.recallInjectionPosition = enumValue(
    raw.recallInjectionPosition,
    cfg.recallInjectionPosition,
    INJECTION_POSITIONS,
    'recallInjectionPosition',
  )
  cfg.ingestionRoles = ingestionRoles(raw.ingestionRoles, cfg.ingestionRoles)
  cfg.ingestionMaxMessagesPerHook = positiveInteger(
    raw.ingestionMaxMessagesPerHook,
    cfg.ingestionMaxMessagesPerHook,
    'ingestionMaxMessagesPerHook',
  )
  cfg.ingestRetrySpool = booleanValue(
    raw.ingestRetrySpool,
    cfg.ingestRetrySpool,
    'ingestRetrySpool',
  )
  cfg.stateMaxAgeDays = positiveInteger(raw.stateMaxAgeDays, cfg.stateMaxAgeDays, 'stateMaxAgeDays')
  cfg.ingestRetryMaxFiles = nonNegativeInteger(
    raw.ingestRetryMaxFiles,
    cfg.ingestRetryMaxFiles,
    'ingestRetryMaxFiles',
  )
  cfg.ingestRetryMaxAgeDays = positiveInteger(
    raw.ingestRetryMaxAgeDays,
    cfg.ingestRetryMaxAgeDays,
    'ingestRetryMaxAgeDays',
  )
  cfg.captureToolCalls = reservedFalseBoolean(raw.captureToolCalls, 'captureToolCalls')
  cfg.captureMedia = reservedFalseBoolean(raw.captureMedia, 'captureMedia')
  cfg.skipNonInteractiveTriggers = booleanValue(
    raw.skipNonInteractiveTriggers,
    cfg.skipNonInteractiveTriggers,
    'skipNonInteractiveTriggers',
  )
  cfg.captureSubagents = booleanValue(
    raw.captureSubagents,
    cfg.captureSubagents,
    'captureSubagents',
  )
  cfg.debug = booleanValue(raw.debug, cfg.debug, 'debug')

  return cfg
}

export function shouldRunAutomaticHooks(
  pluginId: string,
  config: SlotConfigLike | undefined,
): boolean {
  return config?.slots?.memory === pluginId || config?.memoryPluginId === pluginId
}

function objectValue(value: unknown, label: string): Record<string, unknown> {
  if (value === undefined || value === null) return {}
  if (typeof value !== 'object' || Array.isArray(value)) {
    throw new Error(`${label} must be an object`)
  }
  return value as Record<string, unknown>
}

function stringValue(value: unknown, fallback: string, label: string): string {
  if (value === undefined) return fallback
  if (typeof value !== 'string' || !value.trim()) {
    throw new Error(`${label} must be a non-blank string`)
  }
  return value.trim()
}

function optionalString(value: unknown, label: string): string | undefined {
  if (value === undefined || value === null) return undefined
  return stringValue(value, '', label)
}

function tokenValue(value: unknown): string | SecretRef | undefined {
  if (value === undefined || value === null) return undefined
  if (typeof value === 'string') return value.trim() || undefined
  if (typeof value === 'object' && !Array.isArray(value)) {
    const ref = value as Record<string, unknown>
    if (typeof ref.source === 'string' && typeof ref.id === 'string') {
      return value as SecretRef
    }
  }
  throw new Error('memindApiToken must be a string or SecretRef object')
}

function booleanValue(value: unknown, fallback: boolean, label: string): boolean {
  if (value === undefined) return fallback
  if (typeof value !== 'boolean') throw new Error(`${label} must be a boolean`)
  return value
}

function reservedFalseBoolean(value: unknown, label: string): boolean {
  if (value === undefined || value === false) return false
  if (typeof value !== 'boolean') throw new Error(`${label} must be a boolean`)
  throw new Error(`${label} is reserved for a future release and must remain false in v0.1`)
}

function positiveInteger(value: unknown, fallback: number, label: string): number {
  const parsed = integerValue(value, fallback, label)
  if (parsed <= 0) throw new Error(`${label} must be a positive integer`)
  return parsed
}

function nonNegativeInteger(value: unknown, fallback: number, label: string): number {
  const parsed = integerValue(value, fallback, label)
  if (parsed < 0) throw new Error(`${label} must be a non-negative integer`)
  return parsed
}

function integerValue(value: unknown, fallback: number, label: string): number {
  if (value === undefined) return fallback
  if (!Number.isInteger(value)) throw new Error(`${label} must be an integer`)
  return value as number
}

function enumValue<T extends string>(
  value: unknown,
  fallback: T,
  allowed: Set<T>,
  label: string,
): T {
  if (value === undefined) return fallback
  if (typeof value !== 'string' || !allowed.has(value as T)) {
    throw new Error(`${label} has unsupported value: ${String(value)}`)
  }
  return value as T
}

function ingestionRoles(value: unknown, fallback: IngestionRole[]): IngestionRole[] {
  if (value === undefined) return fallback
  if (!Array.isArray(value) || value.length === 0) {
    throw new Error('ingestionRoles must be a non-empty array')
  }
  const roles = value.map((role) => {
    if (typeof role !== 'string' || !INGESTION_ROLES.has(role as IngestionRole)) {
      throw new Error(`ingestionRoles has unsupported role: ${String(role)}`)
    }
    return role as IngestionRole
  })
  return [...new Set(roles)]
}
