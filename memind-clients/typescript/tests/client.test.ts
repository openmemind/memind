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

import { afterEach, describe, expect, it, vi } from 'vitest'
import { MemindClient } from '../src/client.js'
import type { AgentTimelineContent, RawContentValue } from '../src/types/message.js'

type TestProcess = {
  env?: Record<string, string | undefined>
}

type TestGlobal = {
  process?: TestProcess | undefined
}

const testGlobal = globalThis as unknown as TestGlobal
const originalProcess = testGlobal.process

function setTestProcess(process: TestProcess | undefined): void {
  testGlobal.process = process
}

afterEach(() => {
  setTestProcess(originalProcess)
})

describe('MemindClient', () => {
  describe('construction', () => {
    it('accepts baseUrl in options', () => {
      const client = new MemindClient({ baseUrl: 'http://localhost:8366' })
      expect(client).toBeDefined()
      expect(client.memory).toBeDefined()
    })

    it('throws if baseUrl is missing and no env var', () => {
      expect(() => new MemindClient({})).toThrow(/baseUrl/)
    })

    it('throws if baseUrl is blank', () => {
      expect(() => new MemindClient({ baseUrl: '' })).toThrow(/baseUrl/)
      expect(() => new MemindClient({ baseUrl: '   ' })).toThrow(/baseUrl/)
    })

    it('reads MEMIND_BASE_URL from environment', () => {
      setTestProcess({ env: { MEMIND_BASE_URL: 'http://env-host:8366' } })

      const client = new MemindClient({})
      expect(client).toBeDefined()
    })

    it('constructor options override env vars', () => {
      setTestProcess({ env: { MEMIND_BASE_URL: 'http://env-host:8366' } })

      const client = new MemindClient({ baseUrl: 'http://explicit:8366' })
      expect(client).toBeDefined()
    })

    it('does not require a process global in browser-like runtimes', () => {
      setTestProcess(undefined)
      expect(() => new MemindClient({})).toThrow(/baseUrl/)
      const client = new MemindClient({ baseUrl: 'http://browser-host:8366' })
      expect(client).toBeDefined()
    })

    it('ignores blank apiToken', () => {
      const client = new MemindClient({ baseUrl: 'http://localhost:8366', apiToken: '' })
      expect(client).toBeDefined()
    })

    it('rejects invalid timeoutMs', () => {
      expect(() => new MemindClient({ baseUrl: 'http://x', timeoutMs: 0 })).toThrow(/timeoutMs/)
      expect(() => new MemindClient({ baseUrl: 'http://x', timeoutMs: -1 })).toThrow(/timeoutMs/)
      expect(() => new MemindClient({ baseUrl: 'http://x', timeoutMs: NaN })).toThrow(/timeoutMs/)
      expect(() => new MemindClient({ baseUrl: 'http://x', timeoutMs: Infinity })).toThrow(
        /timeoutMs/,
      )
    })

    it('rejects invalid maxRetries', () => {
      expect(() => new MemindClient({ baseUrl: 'http://x', maxRetries: -1 })).toThrow(/maxRetries/)
      expect(() => new MemindClient({ baseUrl: 'http://x', maxRetries: 1.5 })).toThrow(/maxRetries/)
    })

    it('rejects SDK-owned extraHeaders', () => {
      expect(
        () => new MemindClient({ baseUrl: 'http://x', extraHeaders: { Authorization: 'bad' } }),
      ).toThrow(/authorization/i)
      expect(
        () => new MemindClient({ baseUrl: 'http://x', extraHeaders: { 'Content-Type': 'bad' } }),
      ).toThrow(/content-type/i)
      expect(
        () => new MemindClient({ baseUrl: 'http://x', extraHeaders: { 'USER-AGENT': 'bad' } }),
      ).toThrow(/user-agent/i)
      expect(
        () => new MemindClient({ baseUrl: 'http://x', extraHeaders: { Accept: 'bad' } }),
      ).toThrow(/accept/i)
    })

    it('allows custom extraHeaders', async () => {
      const mockFetch = vi.fn().mockResolvedValue({
        status: 200,
        headers: new Headers({ 'content-type': 'application/json' }),
        json: async () => ({ data: { status: 'UP', service: 'memind-server' } }),
      })
      const client = new MemindClient({
        baseUrl: 'http://localhost:8366',
        fetch: mockFetch,
        extraHeaders: { 'X-Client-Feature': 'tests' },
      })

      await client.health()

      expect(mockFetch).toHaveBeenCalledWith(
        'http://localhost:8366/open/v1/health',
        expect.objectContaining({
          headers: expect.objectContaining({ 'X-Client-Feature': 'tests' }),
        }),
      )
    })
  })

  describe('health', () => {
    it('calls GET /open/v1/health', async () => {
      const mockFetch = vi.fn().mockResolvedValue({
        status: 200,
        headers: new Headers({ 'content-type': 'application/json' }),
        json: async () => ({ data: { status: 'UP', service: 'memind-server' } }),
      })

      const client = new MemindClient({
        baseUrl: 'http://localhost:8366',
        fetch: mockFetch,
      })

      const result = await client.health()
      expect(result.status).toBe('UP')
      expect(result.service).toBe('memind-server')
      expect(mockFetch).toHaveBeenCalledWith(
        'http://localhost:8366/open/v1/health',
        expect.objectContaining({
          method: 'GET',
          headers: expect.objectContaining({ Accept: 'application/json' }),
        }),
      )
    })

    it('sends auth and JSON headers for memory requests', async () => {
      const mockFetch = vi.fn().mockResolvedValue({
        status: 200,
        headers: new Headers({ 'content-type': 'application/json' }),
        json: async () => ({
          data: { status: 'SUCCESS', rawDataIds: [], itemIds: [], insightIds: [] },
        }),
      })
      const client = new MemindClient({
        baseUrl: 'http://localhost:8366/',
        apiToken: ' sk-test ',
        fetch: mockFetch,
      })

      await client.memory.extract({
        userId: 'u1',
        agentId: 'a1',
        rawContent: {
          type: 'conversation',
          messages: [{ role: 'USER', content: [{ type: 'text', text: 'hi' }] }],
        },
      })

      expect(mockFetch).toHaveBeenCalledWith(
        'http://localhost:8366/open/v1/memory/sync/extract',
        expect.objectContaining({
          method: 'POST',
          headers: expect.objectContaining({
            Accept: 'application/json',
            'Content-Type': 'application/json',
            Authorization: 'Bearer sk-test',
          }),
        }),
      )
    })

    it('preserves agent timeline raw content in extract requests', async () => {
      const mockFetch = vi.fn().mockResolvedValue({
        status: 200,
        headers: new Headers({ 'content-type': 'application/json' }),
        json: async () => ({
          data: { status: 'SUCCESS', rawDataIds: ['rd-1'], itemIds: [], insightIds: [] },
        }),
      })
      const client = new MemindClient({
        baseUrl: 'http://localhost:8366',
        fetch: mockFetch,
      })
      const raw: RawContentValue = {
        type: 'agent_timeline',
        sourceClient: 'claude-code',
        sessionId: 's',
        agentTurnId: 's-agent-turn-1-1',
        timelineId: 't',
        events: [],
      } satisfies AgentTimelineContent

      await client.memory.extract({
        userId: 'u1',
        agentId: 'a1',
        rawContent: raw,
        sourceClient: 'claude-code',
      })

      const body = JSON.parse(String(mockFetch.mock.calls[0]?.[1]?.body))
      expect(body.rawContent).toEqual({
        type: 'agent_timeline',
        sourceClient: 'claude-code',
        sessionId: 's',
        agentTurnId: 's-agent-turn-1-1',
        timelineId: 't',
        events: [],
      })
    })

    it('sends retrieve filters to POST /memory/retrieve', async () => {
      const mockFetch = vi.fn().mockResolvedValue({
        status: 200,
        headers: new Headers({ 'content-type': 'application/json' }),
        json: async () => ({
          data: { items: [], insights: [], rawData: [], evidences: [] },
        }),
      })
      const client = new MemindClient({
        baseUrl: 'http://localhost:8366',
        fetch: mockFetch,
      })

      await client.memory.retrieve({
        userId: 'u1',
        agentId: 'a1',
        query: 'recent project decisions',
        strategy: 'DEEP',
        scope: 'ALL',
        categories: ['resolution'],
        timeRange: { field: 'occurredAt', from: '2026-01-01T00:00:00Z' },
        metadataFilter: { all: [{ path: 'project', op: 'eq', value: 'memind' }] },
        include: { rawDataMetadata: true, rawDataSegment: false },
      })

      const body = JSON.parse(String(mockFetch.mock.calls[0]?.[1]?.body))
      expect(body).toMatchObject({
        scope: 'ALL',
        categories: ['resolution'],
        timeRange: { field: 'occurredAt', from: '2026-01-01T00:00:00Z' },
        metadataFilter: { all: [{ path: 'project', op: 'eq', value: 'memind' }] },
        include: { rawDataMetadata: true, rawDataSegment: false },
      })
    })

    it('queries memory items through the structured query endpoint', async () => {
      const mockFetch = vi.fn().mockResolvedValue({
        status: 200,
        headers: new Headers({ 'content-type': 'application/json' }),
        json: async () => ({
          data: {
            items: [
              {
                id: '101',
                text: 'Run targeted tests.',
                rawDataType: 'agent_timeline',
                sourceClient: 'claude-code',
                metadata: { project: 'memind' },
              },
            ],
            nextCursor: '101',
          },
        }),
      })
      const client = new MemindClient({
        baseUrl: 'http://localhost:8366',
        fetch: mockFetch,
      })

      const result = await client.memory.queryItems({
        userId: 'u1',
        agentId: 'a1',
        categories: ['playbook'],
        sourceClients: ['claude-code'],
        rawDataTypes: ['agent_timeline'],
        limit: 10,
      })

      expect(result.items[0]?.rawDataType).toBe('agent_timeline')
      expect(result.nextCursor).toBe('101')
      expect(mockFetch).toHaveBeenCalledWith(
        'http://localhost:8366/open/v1/memory/items/query',
        expect.objectContaining({ method: 'POST' }),
      )
    })

    it('queries memory raw data through the structured query endpoint', async () => {
      const mockFetch = vi.fn().mockResolvedValue({
        status: 200,
        headers: new Headers({ 'content-type': 'application/json' }),
        json: async () => ({
          data: {
            rawData: [
              {
                id: 'rd-1',
                type: 'agent_timeline',
                sourceClient: 'codex',
                caption: 'Fixed retry test.',
                metadata: { sessionId: 's1' },
                segment: { events: [] },
              },
            ],
            nextCursor: undefined,
          },
        }),
      })
      const client = new MemindClient({
        baseUrl: 'http://localhost:8366',
        fetch: mockFetch,
      })

      const result = await client.memory.queryRawData({
        userId: 'u1',
        agentId: 'a1',
        types: ['agent_timeline'],
        sourceClients: ['codex'],
        include: { segment: true },
      })

      expect(result.rawData[0]?.id).toBe('rd-1')
      expect(result.rawData[0]?.segment).toEqual({ events: [] })
      expect(mockFetch).toHaveBeenCalledWith(
        'http://localhost:8366/open/v1/memory/raw-data/query',
        expect.objectContaining({ method: 'POST' }),
      )
    })
  })
})
