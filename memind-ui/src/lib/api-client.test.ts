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

  it('unwraps success ApiResult data', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({
        code: 'success',
        data: { status: 'UP' },
        timestamp: '2026-04-30T00:00:00Z',
      })
    )

    await expect(
      apiGet<{ status: string }>('/open/v1/health')
    ).resolves.toEqual({
      status: 'UP',
    })
  })

  it('allows async success code 200 without data', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({
        code: '200',
        timestamp: '2026-04-30T00:00:00Z',
      })
    )

    await expect(
      apiPost<void>('/open/v1/memory/extract')
    ).resolves.toBeUndefined()
  })

  it('throws ApiError with traceId for server errors', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse(
        {
          code: 'validation_failed',
          message: 'userId is required',
          data: { field: 'userId' },
          timestamp: '2026-04-30T00:00:00Z',
          traceId: 'trace-1',
        },
        { status: 400 }
      )
    )

    await expect(apiGet('/admin/v1/items')).rejects.toMatchObject({
      status: 400,
      code: 'validation_failed',
      message: 'userId is required',
      traceId: 'trace-1',
      details: { field: 'userId' },
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
        code: 'success',
        data: [],
        timestamp: '2026-04-30T00:00:00Z',
      })
    )

    await apiGet('/admin/v1/items', {
      pageNo: 2,
      pageSize: 20,
      userId: 'alice',
      agentId: '',
      category: null,
      type: undefined,
      include: ['item', 'raw'],
    })

    expect(fetchMock).toHaveBeenCalledWith(
      '/admin/v1/items?pageNo=2&pageSize=20&userId=alice&include=item&include=raw',
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
