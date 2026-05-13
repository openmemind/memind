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
import { DashboardPage } from './index'

const dashboard = {
  totals: {
    rawData: 10,
    items: 20,
    insights: 3,
    memoryThreads: 4,
    graphEntities: 5,
    itemLinks: 6,
  },
  backlog: {
    conversationPending: 12,
    insightUnbuilt: 5,
    insightUngrouped: 2,
    threadOutboxPending: 1,
    threadOutboxFailed: 1,
    graphBatchRepairRequired: 7,
  },
  activity: {
    days: 7,
    rawDataCreated: [{ date: '2026-04-30', count: 2 }],
    itemsCreated: [{ date: '2026-04-30', count: 4 }],
    insightsCreated: [{ date: '2026-04-30', count: 1 }],
  },
  breakdown: {
    sourceClients: [{ name: 'sdk', count: 10 }],
    rawDataTypes: [],
    itemTypes: [],
    insightTypes: [],
    graphLinkTypes: [],
  },
  healthSignals: {
    graphEnabled: true,
    retrievalGraphAssistEnabled: true,
    threadProjectionStates: [{ state: 'ready', count: 1 }],
  },
}

const zeroDashboard = {
  totals: {
    rawData: 0,
    items: 0,
    insights: 0,
    memoryThreads: 0,
    graphEntities: 0,
    itemLinks: 0,
  },
  backlog: {
    conversationPending: 0,
    insightUnbuilt: 0,
    insightUngrouped: 0,
    threadOutboxPending: 0,
    threadOutboxFailed: 0,
    graphBatchRepairRequired: 0,
  },
  activity: {
    days: 7,
    rawDataCreated: [],
    itemsCreated: [],
    insightsCreated: [],
  },
  breakdown: {
    sourceClients: [],
    rawDataTypes: [],
    itemTypes: [],
    insightTypes: [],
    graphLinkTypes: [],
  },
  healthSignals: {
    graphEnabled: false,
    retrievalGraphAssistEnabled: false,
    threadProjectionStates: [],
  },
}

const jsonResponse = (data: unknown) =>
  new Response(
    JSON.stringify({ data }),
    { headers: { 'content-type': 'application/json' } }
  )

function renderDashboard() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  })

  return render(
    <QueryClientProvider client={queryClient}>
      <SidebarProvider>
        <DashboardPage />
      </SidebarProvider>
    </QueryClientProvider>
  )
}

describe('DashboardPage', () => {
  const fetchMock = vi.fn<typeof fetch>()

  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock)
    window.history.replaceState(null, '', '/')
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.clearAllMocks()
  })

  it('renders total metric cards', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(dashboard))

    const { getByLabelText } = await renderDashboard()

    await expect.element(getByLabelText('Memory Items 20')).toBeInTheDocument()
    await expect.element(getByLabelText('Graph Entities 5')).toBeInTheDocument()
  })

  it('renders zero-state copy when all counts are zero', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(zeroDashboard))

    const { getByText } = await renderDashboard()

    await expect
      .element(getByText('No memory activity for this scope yet.'))
      .toBeInTheDocument()
  })

  it('days selector defaults to 7', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(dashboard))

    const { getByLabelText } = await renderDashboard()

    await expect.element(getByLabelText('Activity days')).toHaveValue('7')
  })

  it('changing days to 30 refetches dashboard with days=30', async () => {
    fetchMock
      .mockResolvedValueOnce(jsonResponse(dashboard))
      .mockResolvedValueOnce(jsonResponse({ ...dashboard, activity: { ...dashboard.activity, days: 30 } }))

    const { getByLabelText } = await renderDashboard()
    await userEvent.selectOptions(getByLabelText('Activity days'), '30')

    await vi.waitFor(() =>
      expect(fetchMock).toHaveBeenLastCalledWith(
        '/admin/v1/dashboard?days=30',
        expect.objectContaining({ method: 'GET' })
      )
    )
  })

  it('renders backlog deep links and preserves memoryId', async () => {
    window.history.replaceState(null, '', '/?memoryId=alice%3Aagent-a')
    fetchMock.mockResolvedValueOnce(jsonResponse(dashboard))

    const { getByRole } = await renderDashboard()

    await expect
      .element(
        getByRole('link', {
          name: 'pending conversations 12',
        })
      )
      .toHaveAttribute(
        'href',
        '/buffers?tab=conversations&state=pending&memoryId=alice%3Aagent-a'
      )
    await expect
      .element(getByRole('link', { name: 'unbuilt insights 5' }))
      .toHaveAttribute(
        'href',
        '/buffers?tab=insights&state=unbuilt&memoryId=alice%3Aagent-a'
      )
    await expect
      .element(getByRole('link', { name: 'ungrouped insights 2' }))
      .toHaveAttribute(
        'href',
        '/buffers?tab=insights&state=ungrouped&memoryId=alice%3Aagent-a'
      )
    await expect
      .element(getByRole('link', { name: 'graph batches needing repair 7' }))
      .toHaveAttribute(
        'href',
        '/item-graph?tab=batches&state=REPAIR_REQUIRED&memoryId=alice%3Aagent-a'
      )
  })
})
