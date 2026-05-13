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

import type { HttpClientConfig } from './core/http.js'
import { httpRequest } from './core/http.js'
import { assertHealthResponse } from './core/validate.js'
import { MemoryResource } from './resources/memory.js'
import type { RequestOptions } from './types/common.js'
import type { HealthResponse } from './types/health.js'

const SDK_OWNED_HEADERS = new Set(['accept', 'content-type', 'authorization', 'user-agent'])

const DEFAULT_TIMEOUT_MS = 30_000
const DEFAULT_MAX_RETRIES = 2

export type MemindClientOptions = {
  baseUrl?: string
  apiToken?: string
  timeoutMs?: number
  maxRetries?: number
  fetch?: typeof globalThis.fetch
  extraHeaders?: Record<string, string>
}

type RuntimeProcess = {
  env?: Record<string, string | undefined>
}

function runtimeProcess(): RuntimeProcess | undefined {
  return (globalThis as typeof globalThis & { process?: RuntimeProcess }).process
}

function getEnv(name: string): string | undefined {
  try {
    return runtimeProcess()?.env?.[name] || undefined
  } catch {
    return undefined
  }
}

function resolveBaseUrl(options: MemindClientOptions): string {
  const url = options.baseUrl ?? getEnv('MEMIND_BASE_URL')
  if (!url || !url.trim()) {
    throw new Error('MemindClient requires a baseUrl (via options or MEMIND_BASE_URL env var)')
  }
  return url.trim().replace(/\/+$/, '')
}

function resolveApiToken(options: MemindClientOptions): string | undefined {
  const token = options.apiToken ?? getEnv('MEMIND_API_TOKEN')
  if (!token || !token.trim()) return undefined
  return token.trim()
}

function validateTimeoutMs(value: number | undefined): number {
  if (value === undefined) return DEFAULT_TIMEOUT_MS
  if (!Number.isFinite(value) || value <= 0) {
    throw new Error('MemindClient: timeoutMs must be a positive finite number')
  }
  return value
}

function validateMaxRetries(value: number | undefined): number {
  if (value === undefined) return DEFAULT_MAX_RETRIES
  if (!Number.isInteger(value) || value < 0) {
    throw new Error('MemindClient: maxRetries must be a non-negative integer')
  }
  return value
}

function validateExtraHeaders(headers: Record<string, string> | undefined): Record<string, string> {
  if (!headers) return {}
  for (const key of Object.keys(headers)) {
    if (SDK_OWNED_HEADERS.has(key.toLowerCase())) {
      throw new Error(
        `MemindClient: extraHeaders cannot override SDK-owned header "${key.toLowerCase()}"`,
      )
    }
  }
  return headers
}

export class MemindClient {
  readonly memory: MemoryResource
  private readonly config: HttpClientConfig

  constructor(options: MemindClientOptions) {
    const fetchImpl = options.fetch ?? globalThis.fetch?.bind(globalThis)
    if (typeof fetchImpl !== 'function') {
      throw new Error('MemindClient requires a fetch implementation')
    }
    this.config = {
      baseUrl: resolveBaseUrl(options),
      apiToken: resolveApiToken(options),
      timeoutMs: validateTimeoutMs(options.timeoutMs),
      maxRetries: validateMaxRetries(options.maxRetries),
      fetch: fetchImpl,
      extraHeaders: validateExtraHeaders(options.extraHeaders),
    }
    this.memory = new MemoryResource(this.config)
  }

  async health(options?: RequestOptions): Promise<HealthResponse> {
    const data = await httpRequest(this.config, {
      method: 'GET',
      path: '/health',
      signal: options?.signal,
      timeoutMs: options?.timeoutMs,
      maxRetries: options?.maxRetries,
    })
    return assertHealthResponse(data)
  }
}
