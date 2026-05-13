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

export class MemindError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'MemindError'
  }
}

export type MemindAPIErrorOptions = {
  status: number
  errorCode?: string
  requestId?: string
  body?: unknown
}

export class MemindAPIError extends MemindError {
  readonly status: number
  readonly errorCode?: string
  readonly requestId?: string
  readonly body?: unknown

  constructor(message: string, options: MemindAPIErrorOptions) {
    super(message)
    this.name = 'MemindAPIError'
    this.status = options.status
    this.errorCode = options.errorCode
    this.requestId = options.requestId
    this.body = options.body
  }
}

export class MemindAuthenticationError extends MemindAPIError {
  constructor(
    message: string,
    options: Omit<MemindAPIErrorOptions, 'status'> & { status?: number },
  ) {
    super(message, { ...options, status: options.status ?? 401 })
    this.name = 'MemindAuthenticationError'
  }
}

export type MemindRateLimitErrorOptions = MemindAPIErrorOptions & {
  retryAfter?: number
}

export class MemindRateLimitError extends MemindAPIError {
  readonly retryAfter?: number

  constructor(
    message: string,
    options: Omit<MemindRateLimitErrorOptions, 'status'> & { status?: number },
  ) {
    super(message, { ...options, status: options.status ?? 429 })
    this.name = 'MemindRateLimitError'
    this.retryAfter = options.retryAfter
  }
}

export class MemindConnectionError extends MemindError {
  constructor(message: string) {
    super(message)
    this.name = 'MemindConnectionError'
  }
}

export class MemindTimeoutError extends MemindError {
  constructor(message: string) {
    super(message)
    this.name = 'MemindTimeoutError'
  }
}
