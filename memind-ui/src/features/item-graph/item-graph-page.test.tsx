import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { userEvent } from 'vitest/browser'
import { SidebarProvider } from '@/components/ui/sidebar'
import { ItemGraphPage } from './index'

const summary = {
  entityCount: 10,
  aliasCount: 8,
  mentionCount: 7,
  itemLinkCount: 6,
  cooccurrenceCount: 5,
  graphBatchCountByState: [{ name: 'PENDING', count: 1 }],
  itemLinkCountByType: [{ name: 'RELATED', count: 2 }],
  entityCountByType: [{ name: 'person', count: 3 }],
}

const entity = {
  id: 1,
  memoryId: 'alice:agent-a',
  userId: 'alice',
  agentId: 'agent-a',
  entityKey: 'person:alice',
  displayName: 'Alice',
  entityType: 'person',
  metadata: { source: 'test' },
  createdAt: '2026-04-30T00:00:00Z',
  updatedAt: '2026-04-30T00:01:00Z',
}

const alias = {
  id: 2,
  memoryId: 'alice:agent-a',
  userId: 'alice',
  agentId: 'agent-a',
  entityKey: 'person:alice',
  entityType: 'person',
  normalizedAlias: 'alias-a',
  evidenceCount: 4,
  metadata: {},
  createdAt: '2026-04-30T00:02:00Z',
  updatedAt: '2026-04-30T00:03:00Z',
}

const mention = {
  id: 3,
  memoryId: 'alice:agent-a',
  userId: 'alice',
  agentId: 'agent-a',
  itemId: 101,
  entityKey: 'person:alice',
  confidence: 0.9,
  metadata: {},
  createdAt: '2026-04-30T00:04:00Z',
  updatedAt: '2026-04-30T00:05:00Z',
}

const itemLink = {
  id: 4,
  memoryId: 'alice:agent-a',
  userId: 'alice',
  agentId: 'agent-a',
  sourceItemId: 101,
  targetItemId: 102,
  linkType: 'RELATED',
  relationCode: 'rel',
  evidenceSource: 'graph',
  strength: 0.7,
  metadata: {},
  createdAt: '2026-04-30T00:06:00Z',
  updatedAt: '2026-04-30T00:07:00Z',
}

const cooccurrence = {
  id: 5,
  memoryId: 'alice:agent-a',
  userId: 'alice',
  agentId: 'agent-a',
  leftEntityKey: 'person:alice',
  rightEntityKey: 'topic:memory',
  cooccurrenceCount: 3,
  metadata: {},
  createdAt: '2026-04-30T00:08:00Z',
  updatedAt: '2026-04-30T00:09:00Z',
}

const batch = {
  id: 6,
  memoryId: 'alice:agent-a',
  userId: 'alice',
  agentId: 'agent-a',
  extractionBatchId: 'batch-1',
  state: 'PENDING',
  errorMessage: null,
  retryPromotionSupported: true,
  createdAt: '2026-04-30T00:10:00Z',
  updatedAt: '2026-04-30T00:11:00Z',
}

function api(data: unknown) {
  return new Response(
    JSON.stringify({ code: 'success', data, timestamp: '2026-04-30T00:00:00Z' }),
    { headers: { 'content-type': 'application/json' } }
  )
}

function page(list: unknown[]) {
  return { total: list.length, current: 1, list }
}

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <SidebarProvider>
        <ItemGraphPage />
      </SidebarProvider>
    </QueryClientProvider>
  )
}

describe('ItemGraphPage', () => {
  const fetchMock = vi.fn<typeof fetch>()

  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock)
    window.localStorage.clear()
    window.history.replaceState(null, '', '/item-graph')
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.clearAllMocks()
  })

  it('default tab is summary and renders graph counts', async () => {
    fetchMock.mockResolvedValueOnce(api(summary))

    const { getByRole, getByText } = await renderPage()

    await expect.element(getByRole('tab', { name: 'Summary' })).toHaveAttribute('aria-selected', 'true')
    await expect.element(getByText('entityCount: 10')).toBeInTheDocument()
    await expect.element(getByText('aliasCount: 8')).toBeInTheDocument()
    await expect.element(getByText('cooccurrenceCount: 5')).toBeInTheDocument()
  })

  it('tab=batches opens batches and default state omits state query param', async () => {
    window.history.replaceState(null, '', '/item-graph?tab=batches')
    fetchMock.mockResolvedValueOnce(api(page([batch])))

    const { getByRole, getByText } = await renderPage()

    await expect.element(getByRole('tab', { name: 'Batches' })).toHaveAttribute('aria-selected', 'true')
    await expect.element(getByText('batch-1')).toBeInTheDocument()
    await vi.waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        '/admin/v1/item-graph/batches?pageNo=1&pageSize=10',
        expect.objectContaining({ method: 'GET' })
      )
    )
  })

  it('entity delete is disabled without memoryId', async () => {
    window.history.replaceState(null, '', '/item-graph?tab=entities')
    fetchMock.mockResolvedValueOnce(api(page([entity])))

    const { getByLabelText, getByRole } = await renderPage()
    await userEvent.click(getByLabelText('Select entity person:alice'))

    await expect.element(getByRole('button', { name: 'Delete selected entities' })).toBeDisabled()
  })

  it('entity delete sends memoryId and selected entity keys', async () => {
    window.history.replaceState(null, '', '/item-graph?tab=entities&memoryId=alice%3Aagent-a')
    fetchMock
      .mockResolvedValueOnce(api(page([entity])))
      .mockResolvedValueOnce(api({
        deletedCount: 1,
        affectedMemoryIds: ['alice:agent-a'],
        deletedAliases: 1,
        deletedMentions: 1,
        deletedCooccurrences: 1,
        possiblyStaleEntityOverlapLinks: 0,
      }))
      .mockResolvedValue(api(page([])))

    const { getByLabelText, getByRole } = await renderPage()
    await userEvent.click(getByLabelText('Select entity person:alice'))
    await userEvent.click(getByRole('button', { name: 'Delete selected entities' }))
    await userEvent.click(getByRole('button', { name: 'Delete' }))

    await expectDelete('/admin/v1/item-graph/entities', {
      memoryId: 'alice:agent-a',
      entityKeys: ['person:alice'],
    })
  })

  it('entity detail drawer renders aliases, mention count, top items, cooccurrences, and overlap count', async () => {
    window.history.replaceState(null, '', '/item-graph?tab=entities&memoryId=alice%3Aagent-a')
    fetchMock
      .mockResolvedValueOnce(api(page([entity])))
      .mockResolvedValueOnce(api({
        entity,
        aliases: [alias],
        mentionCount: 7,
        topMentionedItemIds: [101],
        topCooccurrences: [cooccurrence],
        entityOverlapItemLinkCount: 2,
      }))

    const { getByRole, getByText } = await renderPage()
    await userEvent.click(getByRole('button', { name: 'View entity 1' }))

    await expect.element(getByText('alias-a')).toBeInTheDocument()
    await expect.element(getByText('mentionCount: 7')).toBeInTheDocument()
    await expect.element(getByText('topMentionedItemIds: 101')).toBeInTheDocument()
    await expect.element(getByText('topic:memory')).toBeInTheDocument()
    await expect.element(getByText('entityOverlapItemLinkCount: 2')).toBeInTheDocument()
  })

  it('alias delete sends selected row ids', async () => {
    window.history.replaceState(null, '', '/item-graph?tab=aliases&memoryId=alice%3Aagent-a')
    fetchMock
      .mockResolvedValueOnce(api(page([alias])))
      .mockResolvedValueOnce(api({ deletedCount: 1, affectedMemoryIds: ['alice:agent-a'] }))
      .mockResolvedValue(api(page([])))

    const { getByLabelText, getByRole } = await renderPage()
    await userEvent.click(getByLabelText('Select alias 2'))
    await userEvent.click(getByRole('button', { name: 'Delete selected aliases' }))
    await userEvent.click(getByRole('button', { name: 'Delete' }))

    await expectDelete('/admin/v1/item-graph/aliases', { ids: [2] })
  })

  it('mention delete sends selected row ids', async () => {
    window.history.replaceState(null, '', '/item-graph?tab=mentions&memoryId=alice%3Aagent-a')
    fetchMock
      .mockResolvedValueOnce(api(page([mention])))
      .mockResolvedValueOnce(api({ deletedCount: 1, affectedMemoryIds: ['alice:agent-a'] }))
      .mockResolvedValue(api(page([])))

    const { getByLabelText, getByRole } = await renderPage()
    await userEvent.click(getByLabelText('Select mention 3'))
    await userEvent.click(getByRole('button', { name: 'Delete selected mentions' }))
    await userEvent.click(getByRole('button', { name: 'Delete' }))

    await expectDelete('/admin/v1/item-graph/mentions', { ids: [3] })
  })

  it('item link delete sends selected row ids', async () => {
    window.history.replaceState(null, '', '/item-graph?tab=item-links&memoryId=alice%3Aagent-a')
    fetchMock
      .mockResolvedValueOnce(api(page([itemLink])))
      .mockResolvedValueOnce(api({ deletedCount: 1, affectedMemoryIds: ['alice:agent-a'] }))
      .mockResolvedValue(api(page([])))

    const { getByLabelText, getByRole } = await renderPage()
    await userEvent.click(getByLabelText('Select item link 4'))
    await userEvent.click(getByRole('button', { name: 'Delete selected item links' }))
    await userEvent.click(getByRole('button', { name: 'Delete' }))

    await expectDelete('/admin/v1/item-graph/item-links', { ids: [4] })
  })

  it('cooccurrence delete sends selected row ids', async () => {
    window.history.replaceState(null, '', '/item-graph?tab=cooccurrences&memoryId=alice%3Aagent-a')
    fetchMock
      .mockResolvedValueOnce(api(page([cooccurrence])))
      .mockResolvedValueOnce(api({ deletedCount: 1, affectedMemoryIds: ['alice:agent-a'] }))
      .mockResolvedValue(api(page([])))

    const { getByLabelText, getByRole } = await renderPage()
    await userEvent.click(getByLabelText('Select cooccurrence 5'))
    await userEvent.click(getByRole('button', { name: 'Delete selected cooccurrences' }))
    await userEvent.click(getByRole('button', { name: 'Delete' }))

    await expectDelete('/admin/v1/item-graph/cooccurrences', { ids: [5] })
  })

  async function expectDelete(path: string, body: unknown) {
    await vi.waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        path,
        expect.objectContaining({
          method: 'DELETE',
          body: JSON.stringify(body),
        })
      )
    )
  }
})
