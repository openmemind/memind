import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { userEvent } from 'vitest/browser'
import { SidebarProvider } from '@/components/ui/sidebar'
import { RawDataPage } from './index'

const rawData = {
  rawDataId: 'raw-1',
  userId: 'alice',
  agentId: 'agent-a',
  memoryId: 'alice:agent-a',
  type: 'message',
  sourceClient: 'sdk',
  contentId: 'content-1',
  segment: { text: 'segment' },
  caption: 'Full raw caption',
  captionVectorId: 'caption-vec-1',
  metadata: { source: 'test' },
  startTime: '2026-04-30T00:00:00Z',
  endTime: '2026-04-30T00:01:00Z',
  createdAt: '2026-04-30T00:02:00Z',
  updatedAt: '2026-04-30T00:03:00Z',
}

function api(data: unknown) {
  return new Response(
    JSON.stringify({ code: 'success', data, timestamp: '2026-04-30T00:00:00Z' }),
    { headers: { 'content-type': 'application/json' } }
  )
}

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <SidebarProvider>
        <RawDataPage />
      </SidebarProvider>
    </QueryClientProvider>
  )
}

describe('RawDataPage', () => {
  const fetchMock = vi.fn<typeof fetch>()

  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock)
    window.history.replaceState(null, '', '/raw-data')
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.clearAllMocks()
  })

  it('selecting two rows sends only the selected raw data ids', async () => {
    fetchMock
      .mockResolvedValueOnce(api({ total: 2, current: 1, list: [rawData, { ...rawData, rawDataId: 'raw-2' }] }))
      .mockResolvedValueOnce(api({ deletedRawDataCount: 2, deletedItemCount: 3, affectedMemoryIds: ['alice:agent-a'], insightCleanupRequired: true }))
      .mockResolvedValue(api({ total: 0, current: 1, list: [] }))

    const { getByLabelText, getByRole, getByText } = await renderPage()
    await userEvent.click(getByLabelText('Select raw data raw-1'))
    await userEvent.click(getByLabelText('Select raw data raw-2'))
    await userEvent.click(getByRole('button', { name: 'Delete selected' }))
    await expect
      .element(getByText('Associated memory items will also be deleted.'))
      .toBeInTheDocument()
    await userEvent.click(getByRole('button', { name: 'Delete' }))

    await vi.waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        '/admin/v1/raw-data',
        expect.objectContaining({
          method: 'DELETE',
          body: JSON.stringify({ rawDataIds: ['raw-1', 'raw-2'] }),
        })
      )
    )
  })

  it('detail drawer shows caption, JSON fields, content id, vector id, and timestamps', async () => {
    fetchMock
      .mockResolvedValueOnce(api({ total: 1, current: 1, list: [rawData] }))
      .mockResolvedValueOnce(api(rawData))

    const { getByRole, getByText } = await renderPage()
    await userEvent.click(getByRole('button', { name: 'View raw data raw-1' }))

    await expect.element(getByText('Full raw caption')).toBeInTheDocument()
    await expect.element(getByText('contentId: content-1')).toBeInTheDocument()
    await expect.element(getByText('captionVectorId: caption-vec-1')).toBeInTheDocument()
    await vi.waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        '/admin/v1/raw-data/raw-1',
        expect.objectContaining({ method: 'GET' })
      )
    )
    await expect.element(getByText('segment')).toBeInTheDocument()
  })
})
