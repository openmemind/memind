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

import { describe, expect, it, vi } from 'vitest'
import { MemindClient } from '../src/client.js'
import { computeRetryDelay, isRetryableStatus, parseRetryAfter } from '../src/core/retry.js'
import { Message, RawContent } from '../src/types/message.js'

describe('isRetryableStatus', () => {
  it('returns true for retryable codes', () => {
    for (const code of [408, 429, 500, 502, 503, 504]) {
      expect(isRetryableStatus(code)).toBe(true)
    }
  })

  it('returns false for non-retryable codes', () => {
    for (const code of [200, 400, 401, 403, 404]) {
      expect(isRetryableStatus(code)).toBe(false)
    }
  })
})

describe('computeRetryDelay', () => {
  it('returns initial delay for attempt 0', () => {
    const delay = computeRetryDelay(0)
    expect(delay).toBeGreaterThanOrEqual(375)
    expect(delay).toBeLessThanOrEqual(625)
  })

  it('caps at max delay', () => {
    const delay = computeRetryDelay(10)
    expect(delay).toBeLessThanOrEqual(2500)
  })

  it('uses retry-after without capping server-requested delay', () => {
    expect(computeRetryDelay(0, 10_000)).toBe(10_000)
  })
})

describe('parseRetryAfter', () => {
  it('parses integer seconds', () => {
    expect(parseRetryAfter('30')).toBe(30000)
  })

  it('returns undefined for invalid values', () => {
    expect(parseRetryAfter(null)).toBeUndefined()
    expect(parseRetryAfter('')).toBeUndefined()
  })

  it('parses HTTP-date format', () => {
    const future = new Date(Date.now() + 5000).toUTCString()
    const result = parseRetryAfter(future)
    expect(result).toBeGreaterThan(3000)
    expect(result).toBeLessThanOrEqual(6000)
  })
})

describe('retry integration', () => {
  it('retrieve retries on 503', async () => {
    let calls = 0
    const mockFetch = vi.fn().mockImplementation(() => {
      calls++
      if (calls <= 2) {
        return Promise.resolve({
          status: 503,
          headers: new Headers({ 'content-type': 'application/json' }),
          json: async () => ({ error: { code: 'unavailable', message: 'down' } }),
        })
      }
      return Promise.resolve({
        status: 200,
        headers: new Headers({ 'content-type': 'application/json' }),
        json: async () => ({
          data: { status: 'OK', items: [], insights: [], rawData: [], evidences: [] },
        }),
      })
    })

    const client = new MemindClient({ baseUrl: 'http://x', fetch: mockFetch, maxRetries: 2 })
    const result = await client.memory.retrieve({
      userId: 'u1',
      agentId: 'a1',
      query: 'test',
      strategy: 'SIMPLE',
    })
    expect(result.items).toEqual([])
    expect(calls).toBe(3)
  })

  it('mutating ops do NOT retry by default', async () => {
    let calls = 0
    const mockFetch = vi.fn().mockImplementation(() => {
      calls++
      return Promise.resolve({
        status: 503,
        headers: new Headers({ 'content-type': 'application/json' }),
        json: async () => ({ error: { code: 'unavailable', message: 'down' } }),
      })
    })

    const client = new MemindClient({ baseUrl: 'http://x', fetch: mockFetch, maxRetries: 2 })
    await expect(
      client.memory.addMessage({
        userId: 'u1',
        agentId: 'a1',
        message: Message.user('hi'),
      }),
    ).rejects.toThrow()
    expect(calls).toBe(1)
  })

  it('mutating ops retry when per-request maxRetries is set', async () => {
    let calls = 0
    const mockFetch = vi.fn().mockImplementation(() => {
      calls++
      if (calls <= 1) {
        return Promise.resolve({
          status: 503,
          headers: new Headers({ 'content-type': 'application/json' }),
          json: async () => ({ error: { code: 'unavailable', message: 'down' } }),
        })
      }
      return Promise.resolve({
        status: 200,
        headers: new Headers({ 'content-type': 'application/json' }),
        json: async () => ({ data: { triggered: false } }),
      })
    })

    const client = new MemindClient({ baseUrl: 'http://x', fetch: mockFetch })
    await client.memory.addMessage(
      { userId: 'u1', agentId: 'a1', message: Message.user('hi') },
      { maxRetries: 1 },
    )
    expect(calls).toBe(2)
  })

  it('per-request maxRetries overrides client defaults for retrieve', async () => {
    let calls = 0
    const mockFetch = vi.fn().mockImplementation(() => {
      calls++
      return Promise.resolve({
        status: 503,
        headers: new Headers({ 'content-type': 'application/json' }),
        json: async () => ({ error: { code: 'unavailable', message: 'down' } }),
      })
    })

    const client = new MemindClient({ baseUrl: 'http://x', fetch: mockFetch, maxRetries: 2 })
    await expect(
      client.memory.retrieve(
        { userId: 'u1', agentId: 'a1', query: 'test', strategy: 'SIMPLE' },
        { maxRetries: 0 },
      ),
    ).rejects.toThrow()
    expect(calls).toBe(1)
  })

  it('per-request timeoutMs overrides the client default', async () => {
    const client = new MemindClient({
      baseUrl: 'http://x',
      timeoutMs: 10_000,
      fetch: vi.fn().mockImplementation(
        (_url, opts: RequestInit) =>
          new Promise((_resolve, reject) => {
            opts.signal?.addEventListener('abort', () => {
              const err = new Error('aborted')
              err.name = 'AbortError'
              reject(err)
            })
          }),
      ),
    })

    await expect(client.health({ timeoutMs: 1 })).rejects.toThrow(/timed out/)
  })

  it('invalid raw content is rejected before network calls', async () => {
    const mockFetch = vi.fn()
    const client = new MemindClient({ baseUrl: 'http://x', fetch: mockFetch })

    expect(() => RawContent.conversation([])).toThrow()
    expect(() =>
      RawContent.map('document', {
        title: 'ok',
        bad: undefined as never,
      }),
    ).toThrow(/JSON-serializable/)

    const rawContent = {
      type: 'document',
      title: 'ok',
      bad: undefined,
    } as never
    await expect(
      client.memory.extract({
        userId: 'u1',
        agentId: 'a1',
        rawContent,
      }),
    ).rejects.toThrow(/non-serializable|unsupported|undefined|serializable/)
    expect(mockFetch).not.toHaveBeenCalled()
  })
})
