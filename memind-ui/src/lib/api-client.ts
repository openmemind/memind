type ApiResult<T> = {
  code: string
  message?: string
  data?: T
  timestamp: string
  traceId?: string
}

export type PageResult<T> = {
  total: number
  list: T[]
  current: number
}

export type ApiError = {
  status?: number
  code?: string
  message: string
  traceId?: string
  details?: unknown
}

const SUCCESS_CODES = new Set(['success', '200'])

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
    const { mockApiRequest } = await import('./mock-api')
    return mockApiRequest<T>(method, path, body, query)
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

  const payload = await readJson<ApiResult<T>>(response)

  if (!payload || !response.ok || !SUCCESS_CODES.has(payload.code)) {
    throw toApiError(response, payload)
  }

  return payload.data as T
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
  payload: ApiResult<T> | undefined
): ApiError {
  return {
    status: response.status,
    code: payload?.code,
    message: payload?.message || response.statusText || 'Request failed',
    traceId: payload?.traceId,
    details: payload?.data,
  }
}

function toNetworkError(error: unknown): ApiError {
  if (error instanceof Error) {
    return { message: error.message }
  }

  return { message: 'Network request failed', details: error }
}
