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

type SuccessEnvelope<T> = {
  data: T
}

type ErrorEnvelope = {
  error?: {
    code?: string
    message?: string
    details?: unknown
  }
}

export type PageResult<T> = {
  items: T[]
  page: PageMeta
}

export type PageMeta = {
  page: number
  pageSize: number
  totalItems: number
  totalPages: number
  hasPrevious: boolean
  hasNext: boolean
}

export type ApiError = {
  status?: number
  code?: string
  message: string
  requestId?: string
  details?: unknown
}

type HttpMethod = 'GET' | 'POST' | 'PATCH' | 'PUT' | 'DELETE'

export async function apiGet<T>(
  path: string,
  query?: Record<string, unknown>
): Promise<T> {
  return apiRequest<T>('GET', path, undefined, query)
}

export async function apiPost<T>(
  path: string,
  body?: unknown,
  query?: Record<string, unknown>
): Promise<T> {
  return apiRequest<T>('POST', path, body, query)
}

export async function apiPatch<T>(
  path: string,
  body?: unknown,
  query?: Record<string, unknown>
): Promise<T> {
  return apiRequest<T>('PATCH', path, body, query)
}

export async function apiPut<T>(
  path: string,
  body?: unknown,
  query?: Record<string, unknown>
): Promise<T> {
  return apiRequest<T>('PUT', path, body, query)
}

export async function apiDelete<T>(
  path: string,
  body?: unknown,
  query?: Record<string, unknown>
): Promise<T> {
  return apiRequest<T>('DELETE', path, body, query)
}

function buildUrl(path: string, query?: Record<string, unknown>) {
  const params = new URLSearchParams()

  for (const [key, value] of Object.entries(query ?? {})) {
    appendQueryValue(params, key, value)
  }

  const serialized = params.toString()
  return serialized ? `${path}?${serialized}` : path
}

function appendQueryValue(
  params: URLSearchParams,
  key: string,
  value: unknown
) {
  if (value === undefined || value === null || value === '') return
  if (Array.isArray(value)) {
    for (const item of value) {
      appendQueryValue(params, key, item)
    }
    return
  }

  if (value instanceof Date) {
    params.append(key, value.toISOString())
    return
  }

  params.append(key, String(value))
}

async function apiRequest<T>(
  method: HttpMethod,
  path: string,
  body?: unknown,
  query?: Record<string, unknown>
): Promise<T> {
  if (import.meta.env.VITE_MEMIND_MOCK_API === 'true') {
    const { mockApiResponse } = await import('./mock-api')
    return handleResponse<T>(await mockApiResponse(method, path, body, query))
  }

  const init: RequestInit = {
    method,
    headers: {
      accept: 'application/json',
      ...(body === undefined ? {} : { 'content-type': 'application/json' }),
    },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
  }

  let response: Response
  try {
    response = await fetch(buildUrl(path, query), init)
  } catch (error) {
    throw toNetworkError(error)
  }

  return handleResponse<T>(response)
}

async function handleResponse<T>(response: Response): Promise<T> {
  const payload = await readJson<SuccessEnvelope<T> | ErrorEnvelope>(response)

  if (!response.ok || isErrorEnvelope(payload)) {
    throw toApiError(response, payload)
  }

  if (isSuccessEnvelope(payload)) {
    return payload.data
  }

  return undefined as T
}

async function readJson<T>(response: Response): Promise<T | undefined> {
  const text = await response.text()
  if (!text) return undefined

  try {
    return JSON.parse(text) as T
  } catch {
    return undefined
  }
}

function toApiError<T>(
  response: Response,
  payload: SuccessEnvelope<T> | ErrorEnvelope | undefined
): ApiError {
  const requestId = response.headers.get('X-Request-Id') ?? undefined
  if (isErrorEnvelope(payload)) {
    return {
      status: response.status,
      code: payload.error?.code,
      message:
        payload.error?.message || response.statusText || 'Request failed',
      requestId,
      details: payload.error?.details,
    }
  }

  return {
    status: response.status,
    message: response.statusText || 'Request failed',
    requestId,
    details: payload,
  }
}

function toNetworkError(error: unknown): ApiError {
  if (error instanceof Error) {
    return { message: error.message }
  }

  return { message: 'Network request failed', details: error }
}

function isSuccessEnvelope<T>(
  payload: SuccessEnvelope<T> | ErrorEnvelope | undefined
): payload is SuccessEnvelope<T> {
  return Boolean(
    payload && typeof payload === 'object' && 'data' in payload
  )
}

function isErrorEnvelope(
  payload: SuccessEnvelope<unknown> | ErrorEnvelope | undefined
): payload is ErrorEnvelope {
  return Boolean(
    payload &&
      typeof payload === 'object' &&
      'error' in payload &&
      payload.error
  )
}
