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

import type { HttpClientConfig } from '../core/http.js'
import { httpRequest } from '../core/http.js'
import {
  assertAddMessageResponse,
  assertExtractMemoryResponse,
  assertQueryMemoryItemsResponse,
  assertQueryMemoryRawDataResponse,
  assertRetrieveMemoryResponse,
} from '../core/validate.js'
import type { RequestOptions } from '../types/common.js'
import type {
  AddMessageRequest,
  CommitMemoryRequest,
  ExtractMemoryRequest,
  ExtractMemoryResponse,
  QueryMemoryItemsRequest,
  QueryMemoryItemsResponse,
  QueryMemoryRawDataRequest,
  QueryMemoryRawDataResponse,
  RetrieveMemoryRequest,
  RetrieveMemoryResponse,
} from '../types/memory.js'
import { assertMessage, assertRawContent } from '../types/message.js'

export class MemoryResource {
  constructor(private readonly config: HttpClientConfig) {}

  async extract(
    request: ExtractMemoryRequest,
    options?: RequestOptions,
  ): Promise<ExtractMemoryResponse> {
    assertRawContent(request.rawContent)
    const maxRetries = options?.maxRetries ?? 0
    const data = await httpRequest(this.config, {
      method: 'POST',
      path: '/memory/sync/extract',
      body: request,
      signal: options?.signal,
      timeoutMs: options?.timeoutMs,
      maxRetries,
    })
    return assertExtractMemoryResponse(data)
  }

  async addMessage(request: AddMessageRequest, options?: RequestOptions): Promise<void> {
    assertMessage(request.message)
    const maxRetries = options?.maxRetries ?? 0
    const data = await httpRequest(this.config, {
      method: 'POST',
      path: '/memory/sync/add-message',
      body: request,
      signal: options?.signal,
      timeoutMs: options?.timeoutMs,
      maxRetries,
    })
    assertAddMessageResponse(data)
  }

  async commit(request: CommitMemoryRequest, options?: RequestOptions): Promise<void> {
    const maxRetries = options?.maxRetries ?? 0
    const data = await httpRequest(this.config, {
      method: 'POST',
      path: '/memory/sync/commit',
      body: request,
      signal: options?.signal,
      timeoutMs: options?.timeoutMs,
      maxRetries,
    })
    assertExtractMemoryResponse(data)
  }

  async retrieve(
    request: RetrieveMemoryRequest,
    options?: RequestOptions,
  ): Promise<RetrieveMemoryResponse> {
    const data = await httpRequest(this.config, {
      method: 'POST',
      path: '/memory/retrieve',
      body: request,
      signal: options?.signal,
      timeoutMs: options?.timeoutMs,
      maxRetries: options?.maxRetries,
    })
    return assertRetrieveMemoryResponse(data)
  }

  async queryItems(
    request: QueryMemoryItemsRequest,
    options?: RequestOptions,
  ): Promise<QueryMemoryItemsResponse> {
    const data = await httpRequest(this.config, {
      method: 'POST',
      path: '/memory/items/query',
      body: request,
      signal: options?.signal,
      timeoutMs: options?.timeoutMs,
      maxRetries: options?.maxRetries,
    })
    return assertQueryMemoryItemsResponse(data)
  }

  async queryRawData(
    request: QueryMemoryRawDataRequest,
    options?: RequestOptions,
  ): Promise<QueryMemoryRawDataResponse> {
    const data = await httpRequest(this.config, {
      method: 'POST',
      path: '/memory/raw-data/query',
      body: request,
      signal: options?.signal,
      timeoutMs: options?.timeoutMs,
      maxRetries: options?.maxRetries,
    })
    return assertQueryMemoryRawDataResponse(data)
  }
}
