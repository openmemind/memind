import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { userEvent } from 'vitest/browser'
import { SidebarProvider } from '@/components/ui/sidebar'
import { BuffersPage } from './index'

const conversation = {
  id: 1,
  sessionId: 'session-1',
  userId: 'alice',
  agentId: 'agent-a',
  memoryId: 'alice:agent-a',
  role: 'user',
  content: 'Conversation preview',
  userName: 'Alice',
  sourceClient: 'sdk',
  timestamp: '2026-04-30T00:00:00Z',
  extracted: false,
  createdAt: '2026-04-30T00:01:00Z',
  updatedAt: '2026-04-30T00:02:00Z',
}

const insightBuffer = {
  id: 11,
  userId: 'alice',
  agentId: 'agent-a',
  memoryId: 'alice:agent-a',
  insightTypeName: 'preference',
  itemId: 101,
  groupName: null,
  built: false,
  createdAt: '2026-04-30T00:03:00Z',
  updatedAt: '2026-04-30T00:04:00Z',
}

const insightGroup = {
  memoryId: 'alice:agent-a',
  insightTypeName: 'preference',
  groupName: 'group-a',
  total: 3,
  unbuilt: 2,
  built: 1,
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
        <BuffersPage />
      </SidebarProvider>
    </QueryClientProvider>
  )
}

describe('BuffersPage', () => {
  const fetchMock = vi.fn<typeof fetch>()

  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock)
    window.localStorage.clear()
    window.history.replaceState(null, '', '/buffers')
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.clearAllMocks()
  })

  it('default tab is conversations and conversation state defaults to pending', async () => {
    fetchMock.mockResolvedValueOnce(api({ total: 1, current: 1, list: [conversation] }))

    const { getByRole, getByText } = await renderPage()

    await expect.element(getByRole('tab', { name: 'Conversations' })).toHaveAttribute('aria-selected', 'true')
    await expect.element(getByText('session-1')).toBeInTheDocument()
    await vi.waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        '/admin/v1/buffers/conversations?pageNo=1&pageSize=10&state=pending',
        expect.objectContaining({ method: 'GET' })
      )
    )
  })

  it('tab=insights opens insight buffers and insight state defaults to unbuilt', async () => {
    window.history.replaceState(null, '', '/buffers?tab=insights')
    fetchMock.mockResolvedValueOnce(api({ total: 1, current: 1, list: [insightBuffer] }))

    const { getByLabelText, getByRole, getByText } = await renderPage()

    await expect.element(getByRole('tab', { name: 'Insight Buffers' })).toHaveAttribute('aria-selected', 'true')
    await expect.element(getByLabelText('Insight buffer state')).toHaveValue('unbuilt')
    await expect.element(getByText('preference')).toBeInTheDocument()
    await vi.waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        '/admin/v1/buffers/insights?pageNo=1&pageSize=10&state=unbuilt',
        expect.objectContaining({ method: 'GET' })
      )
    )
  })

  it('tab=groups opens insight groups', async () => {
    window.history.replaceState(null, '', '/buffers?tab=groups')
    fetchMock.mockResolvedValueOnce(api([insightGroup]))

    const { getByRole, getByText } = await renderPage()

    await expect.element(getByRole('tab', { name: 'Insight Buffer Groups' })).toHaveAttribute('aria-selected', 'true')
    await expect.element(getByText('group-a')).toBeInTheDocument()
  })

  it('insight state options include ungrouped and grouped', async () => {
    window.history.replaceState(null, '', '/buffers?tab=insights')
    fetchMock.mockResolvedValueOnce(api({ total: 0, current: 1, list: [] }))

    const { getByRole } = await renderPage()

    await expect.element(getByRole('option', { name: 'ungrouped' })).toBeInTheDocument()
    await expect.element(getByRole('option', { name: 'grouped', exact: true })).toBeInTheDocument()
  })

  it('mark extracted sends selected conversation ids', async () => {
    fetchMock
      .mockResolvedValueOnce(api({ total: 1, current: 1, list: [conversation] }))
      .mockResolvedValueOnce(api({ updatedCount: 1, affectedMemoryIds: ['alice:agent-a'] }))
      .mockResolvedValue(api({ total: 0, current: 1, list: [] }))

    const { getByLabelText, getByRole } = await renderPage()
    await userEvent.click(getByLabelText('Select conversation 1'))
    await userEvent.click(getByRole('button', { name: 'Mark extracted' }))

    await vi.waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        '/admin/v1/buffers/conversations/extracted',
        expect.objectContaining({
          method: 'PATCH',
          body: JSON.stringify({ ids: [1] }),
        })
      )
    )
  })

  it('mark built sends selected insight buffer ids and built flag', async () => {
    window.history.replaceState(null, '', '/buffers?tab=insights')
    fetchMock
      .mockResolvedValueOnce(api({ total: 1, current: 1, list: [insightBuffer] }))
      .mockResolvedValueOnce(api({ updatedCount: 1, affectedMemoryIds: ['alice:agent-a'] }))
      .mockResolvedValue(api({ total: 0, current: 1, list: [] }))

    const { getByLabelText, getByRole } = await renderPage()
    await userEvent.click(getByLabelText('Select insight buffer 11'))
    await userEvent.click(getByRole('button', { name: 'Mark built' }))

    await vi.waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        '/admin/v1/buffers/insights/built',
        expect.objectContaining({
          method: 'PATCH',
          body: JSON.stringify({ ids: [11], built: true }),
        })
      )
    )
  })

  it('conversation row opens detail drawer with full content', async () => {
    fetchMock
      .mockResolvedValueOnce(api({ total: 1, current: 1, list: [conversation] }))
      .mockResolvedValueOnce(api({ ...conversation, content: 'Full conversation content' }))

    const { getByRole, getByText } = await renderPage()
    await userEvent.click(getByRole('button', { name: 'View conversation 1' }))

    await expect.element(getByText('Full conversation content')).toBeInTheDocument()
    await expect.element(getByText('sessionId: session-1')).toBeInTheDocument()
    await vi.waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        '/admin/v1/buffers/conversations/1',
        expect.objectContaining({ method: 'GET' })
      )
    )
  })
})
