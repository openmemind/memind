import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { userEvent } from 'vitest/browser'
import { SidebarProvider } from '@/components/ui/sidebar'
import { InsightsPage } from './index'

const insight = {
  insightId: 201,
  userId: 'alice',
  agentId: 'agent-a',
  memoryId: 'alice:agent-a',
  type: 'preference',
  scope: 'global',
  name: 'Preference',
  categories: ['profile'],
  content: 'Full insight content',
  points: [{ text: 'point one' }],
  groupName: 'group-a',
  lastReasonedAt: '2026-04-30T00:00:00Z',
  summaryEmbedding: [0.1, 0.2],
  tier: 'LONG_TERM',
  parentInsightId: 100,
  childInsightIds: [202],
  version: 3,
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
        <InsightsPage />
      </SidebarProvider>
    </QueryClientProvider>
  )
}

describe('InsightsPage', () => {
  const fetchMock = vi.fn<typeof fetch>()

  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock)
    window.history.replaceState(null, '', '/insights')
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.clearAllMocks()
  })

  it('uses the Insight Tree page title', async () => {
    fetchMock.mockResolvedValueOnce(api({ total: 0, current: 1, list: [] }))

    const { getByRole } = await renderPage()

    await expect
      .element(getByRole('heading', { level: 1, name: 'Insight Tree' }))
      .toBeInTheDocument()
  })

  it('selecting two rows sends only the selected insight ids', async () => {
    fetchMock
      .mockResolvedValueOnce(api({ total: 2, current: 1, list: [insight, { ...insight, insightId: 202 }] }))
      .mockResolvedValueOnce(api({ deletedCount: 2, affectedMemoryIds: ['alice:agent-a'] }))
      .mockResolvedValue(api({ total: 0, current: 1, list: [] }))

    const { getByLabelText, getByRole } = await renderPage()
    await userEvent.click(getByLabelText('Select insight 201'))
    await userEvent.click(getByLabelText('Select insight 202'))
    await userEvent.click(getByRole('button', { name: 'Delete selected' }))
    await userEvent.click(getByRole('button', { name: 'Delete' }))

    await vi.waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        '/admin/v1/insights',
        expect.objectContaining({
          method: 'DELETE',
          body: JSON.stringify({ insightIds: [201, 202] }),
        })
      )
    )
  })

  it('detail drawer shows content, points, categories, relations, embedding, version, and timestamps', async () => {
    fetchMock
      .mockResolvedValueOnce(api({ total: 1, current: 1, list: [insight] }))
      .mockResolvedValueOnce(api(insight))

    const { getByRole, getByText } = await renderPage()
    await userEvent.click(getByRole('button', { name: 'View insight 201' }))

    await expect.element(getByText('Full insight content')).toBeInTheDocument()
    await expect.element(getByText('profile')).toBeInTheDocument()
    await expect.element(getByText('point one')).toBeInTheDocument()
    await expect.element(getByText('parentInsightId: 100')).toBeInTheDocument()
    await expect.element(getByText('childInsightIds: 202')).toBeInTheDocument()
    await expect.element(getByText('version: 3')).toBeInTheDocument()
    await vi.waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        '/admin/v1/insights/201',
        expect.objectContaining({ method: 'GET' })
      )
    )
    await expect.element(getByText('summaryEmbedding')).toBeInTheDocument()
  })
})
