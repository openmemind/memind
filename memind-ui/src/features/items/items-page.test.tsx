import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { userEvent } from 'vitest/browser'
import { SidebarProvider } from '@/components/ui/sidebar'
import { ItemsPage } from './index'

const item = {
  itemId: 101,
  userId: 'alice',
  agentId: 'agent-a',
  memoryId: 'alice:agent-a',
  content: 'Full item content',
  scope: 'conversation',
  category: 'fact',
  vectorId: 'vec-1',
  rawDataId: 'raw-1',
  contentHash: 'hash-1',
  occurredAt: '2026-04-30T00:00:00Z',
  observedAt: '2026-04-30T00:01:00Z',
  metadata: { topic: 'test' },
  type: 'note',
  rawDataType: 'message',
  sourceClient: 'sdk',
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
        <ItemsPage />
      </SidebarProvider>
    </QueryClientProvider>
  )
}

describe('ItemsPage', () => {
  const fetchMock = vi.fn<typeof fetch>()

  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock)
    window.history.replaceState(null, '', '/items')
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.clearAllMocks()
  })

  it('selecting two rows sends only the selected item ids', async () => {
    fetchMock
      .mockResolvedValueOnce(api({ total: 2, current: 1, list: [item, { ...item, itemId: 102 }] }))
      .mockResolvedValueOnce(api({ deletedCount: 2, affectedMemoryIds: ['alice:agent-a'] }))
      .mockResolvedValue(api({ total: 0, current: 1, list: [] }))

    const { getByLabelText, getByRole } = await renderPage()
    await userEvent.click(getByLabelText('Select item 101'))
    await userEvent.click(getByLabelText('Select item 102'))
    await userEvent.click(getByRole('button', { name: 'Delete selected' }))
    await userEvent.click(getByRole('button', { name: 'Delete' }))

    await vi.waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        '/admin/v1/items',
        expect.objectContaining({
          method: 'DELETE',
          body: JSON.stringify({ itemIds: [101, 102] }),
        })
      )
    )
  })

  it('empty state is visible for an empty list', async () => {
    fetchMock.mockResolvedValueOnce(api({ total: 0, current: 1, list: [] }))

    const { getByText } = await renderPage()

    await expect.element(getByText('No memory items found.')).toBeInTheDocument()
  })

  it('detail drawer shows full content and scope prompt when userId is missing', async () => {
    fetchMock
      .mockResolvedValueOnce(api({ total: 1, current: 1, list: [item] }))
      .mockResolvedValueOnce(api(item))

    const { getByRole, getByText } = await renderPage()
    await userEvent.click(getByRole('button', { name: 'View item 101' }))

    await expect.element(getByText('Full item content')).toBeInTheDocument()
    await expect.element(getByText('vectorId: vec-1')).toBeInTheDocument()
    await vi.waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        '/admin/v1/items/101',
        expect.objectContaining({ method: 'GET' })
      )
    )
    await expect
      .element(getByText('Set memory scope with userId to load associated threads.'))
      .toBeInTheDocument()
  })

  it('associated threads load when userId is present', async () => {
    window.history.replaceState(null, '', '/items?memoryId=alice%3Aagent-a')
    fetchMock
      .mockResolvedValueOnce(api({ total: 1, current: 1, list: [item] }))
      .mockResolvedValueOnce(api(item))
      .mockResolvedValueOnce(api([{ threadKey: 'thread-1', role: 'PRIMARY' }]))

    const { getByRole, getByText } = await renderPage()
    await userEvent.click(getByRole('button', { name: 'View item 101' }))

    await expect.element(getByText('thread-1')).toBeInTheDocument()
    await vi.waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        '/admin/v1/items/101/memory-threads?userId=alice&agentId=agent-a',
        expect.objectContaining({ method: 'GET' })
      )
    )
  })
})
