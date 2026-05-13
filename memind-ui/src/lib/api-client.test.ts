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

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { apiGet, apiPost } from './api-client'

const jsonResponse = (body: unknown, init: ResponseInit = {}) =>
  new Response(JSON.stringify(body), {
    status: init.status ?? 200,
    headers: { 'content-type': 'application/json', ...init.headers },
    ...init,
  })

describe('api-client', () => {
  const fetchMock = vi.fn<typeof fetch>()

  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.unstubAllEnvs()
    vi.clearAllMocks()
  })

  it('unwraps success envelope data', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({
        data: { status: 'UP' },
      })
    )

    await expect(
      apiGet<{ status: string }>('/open/v1/health')
    ).resolves.toEqual({
      status: 'UP',
    })
  })

  it('allows command success with null data', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({
        data: null,
      })
    )

    await expect(
      apiPost<null>('/open/v1/memory/sync/extract')
    ).resolves.toBeNull()
  })

  it('throws ApiError with requestId for server errors', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse(
        {
          error: {
            code: 'validation_failed',
            message: 'userId is required',
            details: { fieldErrors: { userId: 'must not be blank' } },
          },
        },
        { status: 400, headers: { 'X-Request-Id': 'request-1' } }
      )
    )

    await expect(apiGet('/admin/v1/items')).rejects.toMatchObject({
      status: 400,
      code: 'validation_failed',
      message: 'userId is required',
      requestId: 'request-1',
      details: { fieldErrors: { userId: 'must not be blank' } },
    })
  })

  it('throws ApiError for network failures', async () => {
    fetchMock.mockRejectedValueOnce(new TypeError('Failed to fetch'))

    await expect(apiGet('/admin/v1/items')).rejects.toMatchObject({
      message: 'Failed to fetch',
    })
  })

  it('serializes query params without undefined values', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({
        data: [],
      })
    )

    await apiGet('/admin/v1/items', {
      page: 2,
      pageSize: 20,
      userId: 'alice',
      agentId: '',
      category: null,
      type: undefined,
      include: ['item', 'raw'],
    })

    expect(fetchMock).toHaveBeenCalledWith(
      '/admin/v1/items?page=2&pageSize=20&userId=alice&include=item&include=raw',
      expect.objectContaining({ method: 'GET' })
    )
  })

  it('uses local mock data instead of fetch when mock api mode is enabled', async () => {
    vi.stubEnv('VITE_MEMIND_MOCK_API', 'true')

    await expect(apiGet('/admin/v1/dashboard')).resolves.toMatchObject({
      totals: expect.objectContaining({
        rawData: expect.any(Number),
        items: expect.any(Number),
        insights: expect.any(Number),
      }),
      backlog: expect.objectContaining({
        conversationPending: expect.any(Number),
      }),
    })
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('preserves user scope requirements in mock api mode', async () => {
    vi.stubEnv('VITE_MEMIND_MOCK_API', 'true')

    await expect(
      apiGet('/admin/v1/items/1001/memory-threads')
    ).rejects.toMatchObject({
      status: 400,
      code: 'validation_failed',
      message: 'userId is required',
    })
    expect(fetchMock).not.toHaveBeenCalled()
  })
})
