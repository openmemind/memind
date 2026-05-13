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
    fetchMock.mockResolvedValueOnce(api(page([])))

    const { getByRole } = await renderPage()

    await expect
      .element(getByRole('heading', { level: 1, name: 'Insight Tree' }))
      .toBeInTheDocument()
  })

  it('selecting two rows sends only the selected insight ids', async () => {
    fetchMock
      .mockResolvedValueOnce(api(page([insight, { ...insight, insightId: 202 }])))
      .mockResolvedValueOnce(api({ deletedCount: 2, affectedMemoryIds: ['alice:agent-a'] }))
      .mockResolvedValue(api(page([])))

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
      .mockResolvedValueOnce(api(page([insight])))
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
