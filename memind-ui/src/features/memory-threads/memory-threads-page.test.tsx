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
import { MemoryThreadsPage } from './index'

const thread = {
  userId: 'alice',
  agentId: 'agent-a',
  memoryId: 'alice:agent-a',
  threadKey: 'thread-1',
  threadType: 'topic',
  anchorKind: 'item',
  anchorKey: '101',
  displayLabel: 'Thread label',
  lifecycleStatus: 'ACTIVE',
  objectState: 'OPEN',
  headline: 'Thread headline',
  snapshotJson: { summary: 'snapshot' },
  snapshotVersion: 2,
  openedAt: '2026-04-30T00:00:00Z',
  lastEventAt: '2026-04-30T00:01:00Z',
  lastMeaningfulUpdateAt: '2026-04-30T00:02:00Z',
  closedAt: null,
  eventCount: 4,
  memberCount: 1,
  createdAt: '2026-04-30T00:03:00Z',
  updatedAt: '2026-04-30T00:04:00Z',
}

const membership = {
  userId: 'alice',
  agentId: 'agent-a',
  memoryId: 'alice:agent-a',
  threadKey: 'thread-1',
  itemId: 101,
  role: 'PRIMARY',
  primary: true,
  relevanceWeight: 0.8,
  createdAt: '2026-04-30T00:05:00Z',
  updatedAt: '2026-04-30T00:06:00Z',
}

const status = {
  projectionState: 'READY',
  pendingCount: 1,
  failedCount: 0,
  rebuildInProgress: false,
  lastProcessedItemId: 100,
  materializationPolicyVersion: 'v1',
  updatedAt: '2026-04-30T00:07:00Z',
  invalidationReason: null,
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
        <MemoryThreadsPage />
      </SidebarProvider>
    </QueryClientProvider>
  )
}

describe('MemoryThreadsPage', () => {
  const fetchMock = vi.fn<typeof fetch>()

  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock)
    window.localStorage.clear()
    window.history.replaceState(null, '', '/memory-threads')
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.clearAllMocks()
  })

  it('status filter default omits status query param', async () => {
    fetchMock.mockResolvedValueOnce(api(page([thread])))

    const { getByText } = await renderPage()

    await expect.element(getByText('thread-1')).toBeInTheDocument()
    await vi.waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        '/admin/v1/memory-threads?page=1&pageSize=10',
        expect.objectContaining({ method: 'GET' })
      )
    )
  })

  it('status options include ACTIVE DORMANT CLOSED', async () => {
    fetchMock.mockResolvedValueOnce(api(page([])))

    const { getByRole } = await renderPage()

    await expect.element(getByRole('option', { name: 'ACTIVE' })).toBeInTheDocument()
    await expect.element(getByRole('option', { name: 'DORMANT' })).toBeInTheDocument()
    await expect.element(getByRole('option', { name: 'CLOSED' })).toBeInTheDocument()
  })

  it('detail drawer is blocked when userId is missing', async () => {
    fetchMock.mockResolvedValueOnce(api(page([thread])))

    const { getByRole, getByText } = await renderPage()
    await userEvent.click(getByRole('button', { name: 'View thread thread-1' }))

    await expect.element(getByText('Set memory scope with userId to load thread details.')).toBeInTheDocument()
    expect(fetchMock).not.toHaveBeenCalledWith(
      '/admin/v1/memory-threads/thread-1?userId=alice&agentId=agent-a',
      expect.anything()
    )
  })

  it('detail drawer loads thread and memberships when userId is present', async () => {
    window.history.replaceState(null, '', '/memory-threads?memoryId=alice%3Aagent-a')
    fetchMock
      .mockResolvedValueOnce(api(status))
      .mockResolvedValueOnce(api(page([thread])))
      .mockResolvedValueOnce(api(thread))
      .mockResolvedValueOnce(api([membership]))

    const { getByRole, getByText } = await renderPage()
    await userEvent.click(getByRole('button', { name: 'View thread thread-1' }))

    await expect.element(getByText('Thread headline')).toBeInTheDocument()
    await expect.element(getByRole('cell', { name: 'PRIMARY', exact: true })).toBeInTheDocument()
    await expect.element(getByText('primary: yes')).toBeInTheDocument()
    await expect.element(getByText('relevanceWeight: 0.8')).toBeInTheDocument()
    await vi.waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        '/admin/v1/memory-threads/thread-1/items?userId=alice&agentId=agent-a',
        expect.objectContaining({ method: 'GET' })
      )
    )
  })

  it('focus=status opens the status panel', async () => {
    window.history.replaceState(
      null,
      '',
      '/memory-threads?memoryId=alice%3Aagent-a&focus=status'
    )
    fetchMock
      .mockResolvedValueOnce(api(status))
      .mockResolvedValueOnce(api(page([])))

    const { getByText } = await renderPage()

    await expect.element(getByText('Thread Projection Status')).toBeInTheDocument()
    await expect.element(getByText('projectionState: READY')).toBeInTheDocument()
  })

  it('rebuild is blocked when userId is missing', async () => {
    fetchMock.mockResolvedValueOnce(api(page([])))

    const { getByRole, getByText } = await renderPage()

    await expect.element(getByText('Set memory scope with userId to load thread status.')).toBeInTheDocument()
    await expect.element(getByRole('button', { name: 'Rebuild projections' })).toBeDisabled()
  })

  it('rebuild confirmation refreshes status and list', async () => {
    window.history.replaceState(null, '', '/memory-threads?memoryId=alice%3Aagent-a')
    fetchMock
      .mockResolvedValueOnce(api(status))
      .mockResolvedValueOnce(api(page([thread])))
      .mockResolvedValueOnce(api(1))
      .mockResolvedValue(api(page([thread])))

    const { getByRole } = await renderPage()
    await userEvent.click(getByRole('button', { name: 'Rebuild projections' }))
    await userEvent.click(getByRole('button', { name: 'Rebuild' }))

    await vi.waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        '/admin/v1/memory-threads/rebuild?userId=alice&agentId=agent-a',
        expect.objectContaining({ method: 'POST' })
      )
    )
  })
})
