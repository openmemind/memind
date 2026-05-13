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
    JSON.stringify({ data }),
    { headers: { 'content-type': 'application/json' } }
  )
}

function page(items: unknown[]) {
  return {
    items,
    page: {
      page: 1,
      pageSize: 10,
      totalItems: items.length,
      totalPages: 1,
      hasPrevious: false,
      hasNext: false,
    },
  }
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
    fetchMock.mockResolvedValueOnce(api(page([conversation])))

    const { getByRole, getByText } = await renderPage()

    await expect.element(getByRole('tab', { name: 'Conversations' })).toHaveAttribute('aria-selected', 'true')
    await expect.element(getByText('session-1')).toBeInTheDocument()
    await vi.waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        '/admin/v1/buffers/conversations?page=1&pageSize=10&state=pending',
        expect.objectContaining({ method: 'GET' })
      )
    )
  })

  it('tab=insights opens insight buffers and insight state defaults to unbuilt', async () => {
    window.history.replaceState(null, '', '/buffers?tab=insights')
    fetchMock.mockResolvedValueOnce(api(page([insightBuffer])))

    const { getByLabelText, getByRole, getByText } = await renderPage()

    await expect.element(getByRole('tab', { name: 'Insight Buffers' })).toHaveAttribute('aria-selected', 'true')
    await expect.element(getByLabelText('Insight buffer state')).toHaveValue('unbuilt')
    await expect.element(getByText('preference')).toBeInTheDocument()
    await vi.waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        '/admin/v1/buffers/insights?page=1&pageSize=10&state=unbuilt',
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
    fetchMock.mockResolvedValueOnce(api(page([])))

    const { getByRole } = await renderPage()

    await expect.element(getByRole('option', { name: 'ungrouped' })).toBeInTheDocument()
    await expect.element(getByRole('option', { name: 'grouped', exact: true })).toBeInTheDocument()
  })

  it('mark extracted sends selected conversation ids', async () => {
    fetchMock
      .mockResolvedValueOnce(api(page([conversation])))
      .mockResolvedValueOnce(api({ updatedCount: 1, affectedMemoryIds: ['alice:agent-a'] }))
      .mockResolvedValue(api(page([])))

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
      .mockResolvedValueOnce(api(page([insightBuffer])))
      .mockResolvedValueOnce(api({ updatedCount: 1, affectedMemoryIds: ['alice:agent-a'] }))
      .mockResolvedValue(api(page([])))

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
      .mockResolvedValueOnce(api(page([conversation])))
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
