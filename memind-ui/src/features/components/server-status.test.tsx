import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { ServerStatus } from './server-status'

const jsonResponse = (body: unknown) =>
  new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'content-type': 'application/json' },
  })

function renderWithQueryClient(node: React.ReactNode) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  })

  return render(
    <QueryClientProvider client={queryClient}>{node}</QueryClientProvider>
  )
}

describe('ServerStatus', () => {
  const fetchMock = vi.fn<typeof fetch>()

  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.clearAllMocks()
    vi.useRealTimers()
  })

  it('shows connecting before the first health result', async () => {
    fetchMock.mockReturnValueOnce(new Promise(() => {}))

    const { getByText } = await renderWithQueryClient(<ServerStatus />)

    await expect.element(getByText('connecting')).toBeInTheDocument()
  })

  it('shows connected after health returns success', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({
        code: 'success',
        data: { status: 'UP', service: 'memind-server' },
        timestamp: '2026-04-30T00:00:00Z',
      })
    )

    const { getByText } = await renderWithQueryClient(<ServerStatus />)

    await expect.element(getByText('connected')).toBeInTheDocument()
  })

  it('shows disconnected after a network error', async () => {
    fetchMock.mockRejectedValueOnce(new TypeError('Failed to fetch'))

    const { getByText } = await renderWithQueryClient(<ServerStatus />)

    await expect.element(getByText('disconnected')).toBeInTheDocument()
  })

  it('polls with a 30 second interval', async () => {
    vi.useFakeTimers()
    fetchMock.mockResolvedValue(
      jsonResponse({
        code: 'success',
        data: { status: 'UP', service: 'memind-server' },
        timestamp: '2026-04-30T00:00:00Z',
      })
    )

    await renderWithQueryClient(<ServerStatus />)
    await vi.waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1))

    await vi.advanceTimersByTimeAsync(30_000)

    expect(fetchMock).toHaveBeenCalledTimes(2)
  })
})
