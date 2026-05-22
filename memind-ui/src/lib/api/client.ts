export type ApiQueryValue = string | number | boolean | null | undefined

export type FetchJsonOptions = Omit<RequestInit, "body"> & {
  body?: BodyInit | object | null
  query?: Record<string, ApiQueryValue>
}

type ErrorEnvelope = {
  error?: {
    code?: string
    details?: unknown
    message?: string
  }
}

type SuccessEnvelope<T> = {
  data: T
}

export class ApiError extends Error {
  readonly code: string
  readonly details?: unknown
  readonly requestId?: string
  readonly status: number

  constructor({
    code,
    details,
    message,
    requestId,
    status,
  }: {
    code: string
    details?: unknown
    message: string
    requestId?: string
    status: number
  }) {
    super(message)
    this.name = "ApiError"
    this.code = code
    this.details = details
    this.requestId = requestId
    this.status = status
  }
}

function apiBaseUrl() {
  return import.meta.env.VITE_API_BASE_URL?.replace(/\/$/, "") ?? ""
}

function withQuery(path: string, query?: Record<string, ApiQueryValue>) {
  const searchParams = new URLSearchParams()

  Object.entries(query ?? {}).forEach(([key, value]) => {
    if (value === undefined || value === null || value === "") {
      return
    }

    searchParams.set(key, String(value))
  })

  const queryString = searchParams.toString()
  return queryString ? `${path}?${queryString}` : path
}

function isSuccessEnvelope<T>(payload: unknown): payload is SuccessEnvelope<T> {
  return Boolean(
    payload &&
      typeof payload === "object" &&
      Object.hasOwn(payload, "data")
  )
}

async function parseJson(response: Response) {
  const text = await response.text()

  if (!text) {
    return undefined
  }

  try {
    return JSON.parse(text) as unknown
  } catch {
    throw new ApiError({
      code: "invalid_json",
      message: "API response was not valid JSON",
      requestId: response.headers.get("X-Request-Id") ?? undefined,
      status: response.status,
    })
  }
}

function bodyAndHeaders(options: FetchJsonOptions) {
  const headers = new Headers(options.headers)
  headers.set("Accept", "application/json")

  if (
    options.body &&
    typeof options.body === "object" &&
    !(options.body instanceof FormData) &&
    !(options.body instanceof Blob) &&
    !(options.body instanceof ArrayBuffer) &&
    !(options.body instanceof URLSearchParams)
  ) {
    headers.set("Content-Type", "application/json")
    return {
      body: JSON.stringify(options.body),
      headers,
    }
  }

  return {
    body: options.body as BodyInit | null | undefined,
    headers,
  }
}

export async function fetchJson<T>(
  path: string,
  options: FetchJsonOptions = {}
): Promise<T> {
  const { query, ...requestOptions } = options
  const url = `${apiBaseUrl()}${withQuery(path, query)}`
  const { body, headers } = bodyAndHeaders(requestOptions)

  const response = await fetch(url, {
    ...requestOptions,
    body,
    headers,
  })
  const payload = await parseJson(response)
  const requestId = response.headers.get("X-Request-Id") ?? undefined

  if (!response.ok) {
    const errorPayload = payload as ErrorEnvelope | undefined
    const error = errorPayload?.error

    throw new ApiError({
      code: error?.code ?? "http_error",
      details: error?.details,
      message: error?.message ?? response.statusText,
      requestId,
      status: response.status,
    })
  }

  if (!isSuccessEnvelope<T>(payload)) {
    throw new ApiError({
      code: "invalid_envelope",
      message: "API response did not include a data envelope",
      requestId,
      status: response.status,
    })
  }

  return payload.data
}
