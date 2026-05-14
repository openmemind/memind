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

import { parseConfig } from '../src/config.js'
import {
  isNonInteractiveTrigger,
  isSubagentSession,
  resolveIdentity,
  sanitizeIdentityPart,
} from '../src/identity.js'

describe('resolveIdentity', () => {
  it('uses configured userId and fixed agentId', () => {
    const cfg = parseConfig({ userId: 'u1', agentId: 'agent', agentIdMode: 'fixed' })
    const id = resolveIdentity(cfg, { cwd: '/tmp/project', sessionKey: 'agent:main:main' })
    expect(id).toEqual({
      userId: 'u1',
      agentId: 'agent',
      sourceClient: 'openclaw',
      sessionKey: 'agent:main:main',
    })
  })

  it('falls back to local system user', () => {
    const cfg = parseConfig({ agentIdMode: 'fixed' })
    const id = resolveIdentity(cfg, { systemUser: 'alice' })
    expect(id.userId).toBe('local__alice')
  })

  it('adds stable project suffix in project mode', () => {
    const cfg = parseConfig({ userId: 'u1', agentId: 'openclaw' })
    const first = resolveIdentity(cfg, { cwd: '/repo/a', gitRemoteUrl: 'git@github.com:x/y.git' })
    const second = resolveIdentity(cfg, {
      cwd: '/different',
      gitRemoteUrl: 'git@github.com:x/y.git',
    })
    expect(first.agentId).toMatch(/^openclaw__y-[a-f0-9]{10}$/)
    expect(second.agentId).toBe(first.agentId)
  })

  it('uses session suffix in session mode', () => {
    const cfg = parseConfig({ userId: 'u1', agentId: 'openclaw', agentIdMode: 'session' })
    const id = resolveIdentity(cfg, { sessionKey: 'agent:main:session:123' })
    expect(id.agentId).toMatch(/^openclaw__session-[a-f0-9]{10}$/)
  })
})

describe('session classification', () => {
  it('detects subagent sessions', () => {
    expect(isSubagentSession('agent:main:subagent:abc')).toBe(true)
    expect(isSubagentSession('agent:main:main')).toBe(false)
  })

  it('detects non-interactive triggers', () => {
    expect(isNonInteractiveTrigger('cron', 'agent:main:main')).toBe(true)
    expect(isNonInteractiveTrigger(undefined, 'agent:main:heartbeat:1')).toBe(true)
    expect(isNonInteractiveTrigger('user', 'agent:main:main')).toBe(false)
  })
})

describe('sanitizeIdentityPart', () => {
  it('normalizes unsafe identity fragments', () => {
    expect(sanitizeIdentityPart('Hello World/Repo.git')).toBe('repo')
    expect(sanitizeIdentityPart('')).toBe('default')
  })
})
