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
import {
  MemindAPIError,
  MemindAuthenticationError,
  MemindConnectionError,
  MemindError,
  MemindRateLimitError,
  MemindTimeoutError,
} from '../src/core/errors.js'

describe('MemindError', () => {
  it('is an instance of Error', () => {
    const err = new MemindError('test')
    expect(err).toBeInstanceOf(Error)
    expect(err.message).toBe('test')
    expect(err.name).toBe('MemindError')
  })
})

describe('MemindAPIError', () => {
  it('carries status and errorCode', () => {
    const err = new MemindAPIError('not found', {
      status: 404,
      errorCode: 'not_found',
      requestId: 'req-123',
      body: { error: { code: 'not_found', message: 'not found' } },
    })
    expect(err).toBeInstanceOf(MemindError)
    expect(err.status).toBe(404)
    expect(err.errorCode).toBe('not_found')
    expect(err.requestId).toBe('req-123')
    expect(err.body).toEqual({ error: { code: 'not_found', message: 'not found' } })
    expect(err.name).toBe('MemindAPIError')
  })
})

describe('MemindAuthenticationError', () => {
  it('extends MemindAPIError with 401', () => {
    const err = new MemindAuthenticationError('unauthorized', { status: 401 })
    expect(err).toBeInstanceOf(MemindAPIError)
    expect(err.status).toBe(401)
    expect(err.name).toBe('MemindAuthenticationError')
  })
})

describe('MemindRateLimitError', () => {
  it('carries retryAfter', () => {
    const err = new MemindRateLimitError('rate limited', {
      status: 429,
      retryAfter: 30,
    })
    expect(err).toBeInstanceOf(MemindAPIError)
    expect(err.retryAfter).toBe(30)
    expect(err.name).toBe('MemindRateLimitError')
  })
})

describe('MemindConnectionError', () => {
  it('extends MemindError', () => {
    const err = new MemindConnectionError('connection refused')
    expect(err).toBeInstanceOf(MemindError)
    expect(err.name).toBe('MemindConnectionError')
  })
})

describe('MemindTimeoutError', () => {
  it('extends MemindError', () => {
    const err = new MemindTimeoutError('timed out')
    expect(err).toBeInstanceOf(MemindError)
    expect(err.name).toBe('MemindTimeoutError')
  })
})

describe('HTTP error mapping', () => {
  function mockFetchResponse(status: number, body: unknown, headers?: Record<string, string>) {
    return vi.fn().mockResolvedValue({
      status,
      headers: new Headers({ 'content-type': 'application/json', ...headers }),
      json: async () => body,
    })
  }

  it('maps 401 to MemindAuthenticationError', async () => {
    const client = new MemindClient({
      baseUrl: 'http://x',
      fetch: mockFetchResponse(401, { error: { code: 'unauthorized', message: 'bad token' } }),
    })
    await expect(client.health()).rejects.toBeInstanceOf(MemindAuthenticationError)
  })

  it('maps 429 to MemindRateLimitError with retryAfter', async () => {
    const client = new MemindClient({
      baseUrl: 'http://x',
      fetch: mockFetchResponse(
        429,
        { error: { code: 'rate_limited', message: 'slow down' } },
        { 'retry-after': '30' },
      ),
      maxRetries: 0,
    })
    const err = await client.health().catch((e: unknown) => e)
    expect(err).toBeInstanceOf(MemindRateLimitError)
    expect((err as MemindRateLimitError).retryAfter).toBe(30)
  })

  it('captures X-Request-Id', async () => {
    const client = new MemindClient({
      baseUrl: 'http://x',
      fetch: mockFetchResponse(
        500,
        { error: { code: 'internal', message: 'oops' } },
        { 'x-request-id': 'req-abc' },
      ),
      maxRetries: 0,
    })
    const err = await client.health().catch((e: unknown) => e)
    expect(err).toBeInstanceOf(MemindAPIError)
    expect((err as MemindAPIError).requestId).toBe('req-abc')
  })

  it('maps non-JSON response to parse error', async () => {
    const client = new MemindClient({
      baseUrl: 'http://x',
      fetch: vi.fn().mockResolvedValue({
        status: 200,
        headers: new Headers({ 'content-type': 'text/html' }),
        json: async () => ({}),
      }),
    })
    const err = await client.health().catch((e: unknown) => e)
    expect(err).toBeInstanceOf(MemindAPIError)
    expect((err as MemindAPIError).errorCode).toBe('parse_error')
  })

  it('maps network failure to MemindConnectionError', async () => {
    const client = new MemindClient({
      baseUrl: 'http://x',
      fetch: vi.fn().mockRejectedValue(new TypeError('fetch failed')),
      maxRetries: 0,
    })
    await expect(client.health()).rejects.toBeInstanceOf(MemindConnectionError)
  })

  it('maps timeout to MemindTimeoutError', async () => {
    const client = new MemindClient({
      baseUrl: 'http://x',
      timeoutMs: 1,
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
      maxRetries: 0,
    })
    await expect(client.health()).rejects.toBeInstanceOf(MemindTimeoutError)
  })

  it('already-aborted caller AbortSignal maps to MemindConnectionError', async () => {
    const controller = new AbortController()
    controller.abort()

    const client = new MemindClient({
      baseUrl: 'http://x',
      fetch: vi.fn().mockResolvedValue({
        status: 200,
        headers: new Headers({ 'content-type': 'application/json' }),
        json: async () => ({ data: { status: 'UP', service: 'memind-server' } }),
      }),
    })
    await expect(client.health({ signal: controller.signal })).rejects.toBeInstanceOf(
      MemindConnectionError,
    )
  })

  it('caller AbortSignal during fetch maps to MemindConnectionError, not timeout', async () => {
    const controller = new AbortController()
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
            controller.abort()
          }),
      ),
    })

    await expect(client.health({ signal: controller.signal })).rejects.toBeInstanceOf(
      MemindConnectionError,
    )
  })

  it('malformed success data maps to parse_error', async () => {
    const client = new MemindClient({
      baseUrl: 'http://x',
      fetch: mockFetchResponse(200, { data: { status: 'UP' } }),
    })
    const err = await client.health().catch((e: unknown) => e)
    expect(err).toBeInstanceOf(MemindAPIError)
    expect((err as MemindAPIError).errorCode).toBe('parse_error')
  })

  it('does not treat non-2xx data envelopes as success', async () => {
    const client = new MemindClient({
      baseUrl: 'http://x',
      fetch: mockFetchResponse(500, { data: { status: 'UP', service: 'memind-server' } }),
      maxRetries: 0,
    })
    const err = await client.health().catch((e: unknown) => e)
    expect(err).toBeInstanceOf(MemindAPIError)
    expect((err as MemindAPIError).status).toBe(500)
    expect((err as MemindAPIError).errorCode).toBe('http_error')
  })
})
