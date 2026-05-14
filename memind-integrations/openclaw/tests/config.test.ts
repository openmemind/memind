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

import { describe, expect, it } from 'vitest'

import { DEFAULT_CONFIG, parseConfig, shouldRunAutomaticHooks } from '../src/config.js'

describe('parseConfig', () => {
  it('applies safe defaults', () => {
    const cfg = parseConfig({})
    expect(cfg.memindApiUrl).toBe('http://127.0.0.1:8366')
    expect(cfg.agentId).toBe('openclaw')
    expect(cfg.agentIdMode).toBe('project')
    expect(cfg.sourceClient).toBe('openclaw')
    expect(cfg.autoRetrieve).toBe(true)
    expect(cfg.autoIngest).toBe(true)
    expect(cfg.captureToolCalls).toBe(false)
    expect(cfg.captureMedia).toBe(false)
    expect(cfg.captureSubagents).toBe(false)
  })

  it('accepts explicit config values', () => {
    const cfg = parseConfig({
      memindApiUrl: 'http://localhost:9999/',
      memindApiToken: 'token',
      userId: 'u1',
      agentId: 'agent',
      agentIdMode: 'fixed',
      autoRetrieve: false,
      retrieveMaxEntries: 3,
      ingestionRoles: ['user'],
    })
    expect(cfg.memindApiUrl).toBe('http://localhost:9999')
    expect(cfg.memindApiToken).toBe('token')
    expect(cfg.userId).toBe('u1')
    expect(cfg.agentId).toBe('agent')
    expect(cfg.agentIdMode).toBe('fixed')
    expect(cfg.autoRetrieve).toBe(false)
    expect(cfg.retrieveMaxEntries).toBe(3)
    expect(cfg.ingestionRoles).toEqual(['user'])
  })

  it('accepts SecretRef-shaped token values without inspecting secrets', () => {
    const token = { source: 'env', provider: 'default', id: 'MEMIND_API_TOKEN' }
    const cfg = parseConfig({ memindApiToken: token })
    expect(cfg.memindApiToken).toBe(token)
  })

  it('rejects unknown keys', () => {
    expect(() => parseConfig({ unknown: true })).toThrow('unknown keys: unknown')
  })

  it('rejects invalid numeric values', () => {
    expect(() => parseConfig({ retrieveMaxEntries: 0 })).toThrow('retrieveMaxEntries')
    expect(() => parseConfig({ ingestRetryMaxFiles: -1 })).toThrow('ingestRetryMaxFiles')
  })

  it('rejects unsupported retrieve strategies and reserved capture switches', () => {
    expect(() => parseConfig({ retrieveStrategy: 'DEEP' })).toThrow('retrieveStrategy')
    expect(() => parseConfig({ captureToolCalls: true })).toThrow('captureToolCalls')
    expect(() => parseConfig({ captureMedia: true })).toThrow('captureMedia')
  })
})

describe('shouldRunAutomaticHooks', () => {
  it('runs hooks only when selected in the memory slot or the legacy memoryPluginId field', () => {
    expect(
      shouldRunAutomaticHooks('memind-openclaw', { slots: { memory: 'memind-openclaw' } }),
    ).toBe(true)
    expect(shouldRunAutomaticHooks('memind-openclaw', { memoryPluginId: 'memind-openclaw' })).toBe(
      true,
    )
    expect(shouldRunAutomaticHooks('memind-openclaw', { slots: { memory: 'openclaw-mem0' } })).toBe(
      false,
    )
    expect(shouldRunAutomaticHooks('memind-openclaw', undefined)).toBe(false)
  })
})

describe('DEFAULT_CONFIG', () => {
  it('keeps documented defaults in one object', () => {
    expect(DEFAULT_CONFIG.retrievePromptPreamble).toContain('Relevant memories from Memind')
  })
})
