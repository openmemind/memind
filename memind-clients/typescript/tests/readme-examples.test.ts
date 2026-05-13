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
import {
  MemindAPIError,
  MemindClient,
  MemindTimeoutError,
  Message,
  RawContent,
  Strategy,
} from '../src/index.js'

describe('README examples', () => {
  it('typechecks and runs the core usage snippets', async () => {
    const mockFetch = vi
      .fn()
      .mockResolvedValueOnce({
        status: 200,
        headers: new Headers({ 'content-type': 'application/json' }),
        json: async () => ({ data: { status: 'UP', service: 'memind-server' } }),
      })
      .mockResolvedValueOnce({
        status: 200,
        headers: new Headers({ 'content-type': 'application/json' }),
        json: async () => ({
          data: { status: 'SUCCESS', rawDataIds: [], itemIds: [], insightIds: [] },
        }),
      })
      .mockResolvedValueOnce({
        status: 200,
        headers: new Headers({ 'content-type': 'application/json' }),
        json: async () => ({
          data: {
            status: 'OK',
            items: [],
            insights: [],
            rawData: [],
            evidences: [],
            strategy: 'SIMPLE',
            query: 'What does the user prefer?',
          },
        }),
      })

    const client = new MemindClient({
      baseUrl: 'http://localhost:8366',
      apiToken: 'test-token',
      fetch: mockFetch,
    })

    await client.health()
    await client.memory.extract({
      userId: 'user-1',
      agentId: 'agent-1',
      rawContent: RawContent.conversation([
        Message.user('Remember that I prefer concise answers.'),
      ]),
    })
    const result = await client.memory.retrieve({
      userId: 'user-1',
      agentId: 'agent-1',
      query: 'What does the user prefer?',
      strategy: Strategy.SIMPLE,
      trace: true,
    })

    expect(result.items).toEqual([])
    expect(MemindAPIError).toBeDefined()
    expect(MemindTimeoutError).toBeDefined()
  })
})
