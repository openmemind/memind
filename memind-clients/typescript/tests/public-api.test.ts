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
import * as api from '../src/index.js'
import type { ApiError, ApiResult, RequestOptions } from '../src/index.js'

function identityResult<T>(result: ApiResult<T>): ApiResult<T> {
  return result
}

describe('public API exports', () => {
  it('exports MemindClient', () => {
    expect(api.MemindClient).toBeDefined()
    expect(typeof api.MemindClient).toBe('function')
  })

  it('exports VERSION', () => {
    expect(api.VERSION).toBe('0.2.0')
  })

  it('exports Role constants', () => {
    expect(api.Role.USER).toBe('USER')
    expect(api.Role.ASSISTANT).toBe('ASSISTANT')
  })

  it('exports Strategy constants', () => {
    expect(api.Strategy.SIMPLE).toBe('SIMPLE')
    expect(api.Strategy.DEEP).toBe('DEEP')
  })

  it('exports Message helpers', () => {
    expect(api.Message.user).toBeDefined()
    expect(api.Message.assistant).toBeDefined()
  })

  it('exports RawContent helpers', () => {
    expect(api.RawContent.conversation).toBeDefined()
    expect(api.RawContent.map).toBeDefined()
  })

  it('exports public type aliases at type-check time', () => {
    const error: ApiError = { code: 'bad_request', message: 'Invalid request' }
    const success: ApiResult<string> = identityResult({ data: 'ok' })
    const failure: ApiResult<string> = identityResult({ error })
    const requestOptions: RequestOptions = { timeoutMs: 1000, maxRetries: 1 }

    expect(success).toEqual({ data: 'ok' })
    expect(failure).toEqual({ error })
    expect(requestOptions).toEqual({ timeoutMs: 1000, maxRetries: 1 })
  })

  it('exports error classes', () => {
    expect(api.MemindError).toBeDefined()
    expect(api.MemindAPIError).toBeDefined()
    expect(api.MemindAuthenticationError).toBeDefined()
    expect(api.MemindRateLimitError).toBeDefined()
    expect(api.MemindConnectionError).toBeDefined()
    expect(api.MemindTimeoutError).toBeDefined()
  })
})
