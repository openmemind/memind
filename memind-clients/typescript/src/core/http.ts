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

import { VERSION } from '../version.js'
import {
  MemindAPIError,
  MemindAuthenticationError,
  MemindConnectionError,
  MemindError,
  MemindRateLimitError,
  MemindTimeoutError,
} from './errors.js'
import { computeRetryDelay, isRetryableStatus, parseRetryAfter } from './retry.js'
import { serializeBody } from './serialize.js'

export type HttpClientConfig = {
  baseUrl: string
  apiToken?: string
  timeoutMs: number
  maxRetries: number
  fetch: typeof globalThis.fetch
  extraHeaders: Record<string, string>
}

export type HttpRequestOptions = {
  method: 'GET' | 'POST'
  path: string
  body?: unknown
  signal?: AbortSignal
  timeoutMs?: number
  maxRetries?: number
}

type RuntimeProcess = {
  versions?: { node?: string }
}

function runtimeProcess(): RuntimeProcess | undefined {
  return (globalThis as typeof globalThis & { process?: RuntimeProcess }).process
}

function isNodeRuntime(): boolean {
  return typeof runtimeProcess()?.versions?.node === 'string'
}

function buildHeaders(config: HttpClientConfig, hasBody: boolean): Record<string, string> {
  const headers: Record<string, string> = {
    Accept: 'application/json',
  }
  if (hasBody) {
    headers['Content-Type'] = 'application/json'
  }
  if (config.apiToken) {
    headers.Authorization = `Bearer ${config.apiToken}`
  }
  if (isNodeRuntime()) {
    headers['User-Agent'] = `memind-typescript/${VERSION}`
  }
  for (const [key, value] of Object.entries(config.extraHeaders)) {
    headers[key] = value
  }
  return headers
}

function buildUrl(baseUrl: string, path: string): string {
  const normalizedPath = path.startsWith('/') ? path : `/${path}`
  return `${baseUrl.replace(/\/+$/, '')}/open/v1${normalizedPath}`
}

type EnvelopeSuccess = { data: unknown }
type EnvelopeError = { error: { code: string; message: string; details?: unknown } }

function isEnvelopeSuccess(body: unknown): body is EnvelopeSuccess {
  return typeof body === 'object' && body !== null && 'data' in body
}

function isEnvelopeError(body: unknown): body is EnvelopeError {
  if (typeof body !== 'object' || body === null || !('error' in body)) return false
  const error = (body as { error?: unknown }).error
  return (
    typeof error === 'object' &&
    error !== null &&
    typeof (error as { code?: unknown }).code === 'string' &&
    typeof (error as { message?: unknown }).message === 'string'
  )
}

function validateRequestTimeout(value: number | undefined): number | undefined {
  if (value === undefined) return undefined
  if (!Number.isFinite(value) || value <= 0) {
    throw new Error('RequestOptions: timeoutMs must be a positive finite number')
  }
  return value
}

function validateRequestRetries(value: number | undefined): number | undefined {
  if (value === undefined) return undefined
  if (!Number.isInteger(value) || value < 0) {
    throw new Error('RequestOptions: maxRetries must be a non-negative integer')
  }
  return value
}

export async function httpRequest(
  config: HttpClientConfig,
  options: HttpRequestOptions,
): Promise<unknown> {
  const effectiveTimeout = validateRequestTimeout(options.timeoutMs) ?? config.timeoutMs
  const effectiveRetries = validateRequestRetries(options.maxRetries) ?? config.maxRetries
  const url = buildUrl(config.baseUrl, options.path)
  const headers = buildHeaders(config, options.body !== undefined)
  const serializedBody = options.body !== undefined ? serializeBody(options.body) : undefined

  let lastNetworkError: Error | undefined

  for (let attempt = 0; attempt <= effectiveRetries; attempt++) {
    const timeoutController = new AbortController()
    let timedOut = false
    const timer = setTimeout(() => {
      timedOut = true
      timeoutController.abort()
    }, effectiveTimeout)

    const combined = new AbortController()
    const abortFromTimeout = () => combined.abort()
    const abortFromCaller = () => combined.abort()

    timeoutController.signal.addEventListener('abort', abortFromTimeout, { once: true })

    if (options.signal?.aborted) {
      clearTimeout(timer)
      timeoutController.signal.removeEventListener('abort', abortFromTimeout)
      throw new MemindConnectionError('Request cancelled by caller')
    }
    options.signal?.addEventListener('abort', abortFromCaller, { once: true })

    try {
      const response = await config.fetch(url, {
        method: options.method,
        headers,
        body: serializedBody,
        signal: combined.signal,
      })

      if (isRetryableStatus(response.status) && attempt < effectiveRetries) {
        const retryAfterMs = parseRetryAfter(response.headers.get('retry-after'))
        await sleep(computeRetryDelay(attempt, retryAfterMs))
        continue
      }

      return await parseResponse(response)
    } catch (err) {
      if (err instanceof MemindError) {
        throw err
      }

      if (err instanceof Error && err.name === 'AbortError') {
        if (timedOut) {
          throw new MemindTimeoutError(`Request timed out after ${effectiveTimeout}ms`)
        }
        throw new MemindConnectionError('Request cancelled by caller')
      }

      if (err instanceof Error) {
        lastNetworkError = err
        if (attempt < effectiveRetries) {
          await sleep(computeRetryDelay(attempt))
          continue
        }
        throw new MemindConnectionError(err.message)
      }

      throw new MemindConnectionError('Unknown network error')
    } finally {
      clearTimeout(timer)
      timeoutController.signal.removeEventListener('abort', abortFromTimeout)
      options.signal?.removeEventListener('abort', abortFromCaller)
    }
  }

  throw lastNetworkError ?? new MemindConnectionError('Request failed after retries')
}

async function parseResponse(response: Response): Promise<unknown> {
  const requestId = response.headers.get('x-request-id') ?? undefined
  const contentType = response.headers.get('content-type') ?? ''
  if (!contentType.toLowerCase().includes('application/json')) {
    throw new MemindAPIError(`Non-JSON response: ${contentType}`, {
      status: response.status,
      errorCode: 'parse_error',
      requestId,
    })
  }

  let body: unknown
  try {
    body = await response.json()
  } catch {
    throw new MemindAPIError('Failed to parse response JSON', {
      status: response.status,
      errorCode: 'parse_error',
      requestId,
    })
  }

  if (isEnvelopeError(body)) {
    const retryAfter = parseRetryAfter(response.headers.get('retry-after'))
    const errorOpts = {
      status: response.status,
      errorCode: body.error.code,
      requestId,
      body,
    }
    if (response.status === 401) {
      throw new MemindAuthenticationError(body.error.message, errorOpts)
    }
    if (response.status === 429) {
      throw new MemindRateLimitError(body.error.message, {
        ...errorOpts,
        retryAfter: retryAfter === undefined ? undefined : retryAfter / 1000,
      })
    }
    throw new MemindAPIError(body.error.message, errorOpts)
  }

  if (isEnvelopeSuccess(body) && response.status >= 200 && response.status < 300) {
    return body.data
  }

  if (isEnvelopeSuccess(body)) {
    throw new MemindAPIError(`HTTP ${response.status}`, {
      status: response.status,
      errorCode: 'http_error',
      requestId,
      body,
    })
  }

  throw new MemindAPIError('Invalid response envelope', {
    status: response.status,
    errorCode: 'parse_error',
    requestId,
    body,
  })
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}
