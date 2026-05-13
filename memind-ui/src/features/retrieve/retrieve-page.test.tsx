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
import { RetrievePage } from './index'

const retrieveResponse = {
  items: [
    {
      id: '101',
      text: 'Alice likes durable memory systems',
      vectorScore: 0.91,
      finalScore: 0.87,
      occurredAt: null,
    },
  ],
  insights: [{ id: '201', text: 'Alice prefers explicit trace views', tier: 'hot' }],
  rawData: [
    {
      rawDataId: 'raw-1',
      caption: 'Conversation about retrieval tracing',
      maxScore: 0.77,
      itemIds: ['101'],
    },
  ],
  evidences: ['Evidence sentence one'],
  strategy: 'DEEP',
  query: 'memory trace',
  trace: {
    traceId: 'trace-1',
    startedAt: null,
    completedAt: null,
    truncated: false,
    stages: [
      {
        stage: 'candidate-search',
        tier: 'items',
        method: 'vector',
        status: 'OK',
        inputCount: 2,
        candidateCount: 3,
        resultCount: 1,
        degraded: true,
        skipped: false,
        startedAt: null,
        durationMillis: 12,
        attributes: { source: 'items' },
        candidates: [{ id: '101', score: 0.91 }],
      },
    ],
    merge: {
      inputCount: 3,
      outputCount: 2,
      deduplicatedCount: 1,
      sourceCount: 2,
      status: 'OK',
    },
    finalResults: {
      strategy: 'DEEP',
      status: 'OK',
      itemCount: 1,
      insightCount: 1,
      rawDataCount: 1,
      evidenceCount: 1,
    },
  },
}

function api(data: unknown) {
  return new Response(
    JSON.stringify({ data }),
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
        <RetrievePage />
      </SidebarProvider>
    </QueryClientProvider>
  )
}

describe('RetrievePage', () => {
  const fetchMock = vi.fn<typeof fetch>()

  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock)
    window.localStorage.clear()
    window.history.replaceState(null, '', '/retrieve')
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.clearAllMocks()
  })

  it('prefills userId and agentId from global memory scope', async () => {
    window.history.replaceState(null, '', '/retrieve?memoryId=alice%3Aagent-a')

    const { getByLabelText } = await renderPage()

    await expect.element(getByLabelText('userId')).toHaveValue('alice')
    await expect.element(getByLabelText('agentId')).toHaveValue('agent-a')
  })

  it('allows local userId and agentId override without changing global scope', async () => {
    window.history.replaceState(null, '', '/retrieve?memoryId=alice%3Aagent-a')
    fetchMock.mockResolvedValueOnce(api(retrieveResponse))

    const { getByLabelText, getByRole } = await renderPage()

    await userEvent.fill(getByLabelText('userId'), 'bob')
    await userEvent.fill(getByLabelText('agentId'), 'agent-b')
    await userEvent.fill(getByLabelText('Query'), 'memory trace')
    await userEvent.click(getByRole('button', { name: 'Retrieve' }))

    await expectRetrieve({
      userId: 'bob',
      agentId: 'agent-b',
      query: 'memory trace',
      strategy: 'SIMPLE',
      trace: false,
    })
    expect(new URLSearchParams(window.location.search).get('memoryId')).toBe(
      'alice:agent-a'
    )
  })

  it('blocks submit when userId is empty', async () => {
    const { getByLabelText, getByRole, getByText } = await renderPage()

    await userEvent.fill(getByLabelText('agentId'), 'agent-a')
    await userEvent.fill(getByLabelText('Query'), 'memory trace')
    await userEvent.click(getByRole('button', { name: 'Retrieve' }))

    await expect.element(getByText('userId is required.')).toBeInTheDocument()
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('blocks submit when agentId is empty', async () => {
    const { getByLabelText, getByRole, getByText } = await renderPage()

    await userEvent.fill(getByLabelText('userId'), 'alice')
    await userEvent.fill(getByLabelText('Query'), 'memory trace')
    await userEvent.click(getByRole('button', { name: 'Retrieve' }))

    await expect.element(getByText('agentId is required.')).toBeInTheDocument()
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('renders retrieved items insights raw data and evidences', async () => {
    window.history.replaceState(null, '', '/retrieve?memoryId=alice%3Aagent-a')
    fetchMock.mockResolvedValueOnce(api(retrieveResponse))

    const { getByLabelText, getByRole, getByText } = await renderPage()

    await userEvent.fill(getByLabelText('Query'), 'memory trace')
    await userEvent.click(getByRole('button', { name: 'Retrieve' }))

    await expect.element(getByText('Alice likes durable memory systems')).toBeInTheDocument()
    await expect.element(getByText('Alice prefers explicit trace views')).toBeInTheDocument()
    await expect.element(getByText('Conversation about retrieval tracing')).toBeInTheDocument()
    await expect.element(getByText('Evidence sentence one')).toBeInTheDocument()
  })

  it('trace section is collapsed by default', async () => {
    window.history.replaceState(null, '', '/retrieve?memoryId=alice%3Aagent-a')
    fetchMock.mockResolvedValueOnce(api(retrieveResponse))

    const { getByLabelText, getByRole } = await renderPage()

    await userEvent.fill(getByLabelText('Query'), 'memory trace')
    await userEvent.click(getByLabelText('Trace'))
    await userEvent.click(getByRole('button', { name: 'Retrieve' }))

    await expectRetrieve({
      userId: 'alice',
      agentId: 'agent-a',
      query: 'memory trace',
      strategy: 'SIMPLE',
      trace: true,
    })
    await expect
      .element(getByRole('button', { name: 'Trace details' }))
      .toHaveAttribute('aria-expanded', 'false')
  })

  it('expanded trace shows stage method status duration and counts', async () => {
    window.history.replaceState(null, '', '/retrieve?memoryId=alice%3Aagent-a')
    fetchMock.mockResolvedValueOnce(api(retrieveResponse))

    const { getByLabelText, getByRole, getByText } = await renderPage()

    await userEvent.fill(getByLabelText('Query'), 'memory trace')
    await userEvent.click(getByLabelText('Trace'))
    await userEvent.click(getByRole('button', { name: 'Retrieve' }))
    await userEvent.click(getByRole('button', { name: 'Trace details' }))

    await expect.element(getByText('candidate-search')).toBeInTheDocument()
    await expect.element(getByText('method: vector')).toBeInTheDocument()
    await expect.element(getByText('status: OK')).toBeInTheDocument()
    await expect.element(getByText('duration: 12ms')).toBeInTheDocument()
    await expect.element(getByText('inputCount: 2 -> resultCount: 1')).toBeInTheDocument()
  })

  it('attributes and candidates render inside collapsed JSON viewers', async () => {
    window.history.replaceState(null, '', '/retrieve?memoryId=alice%3Aagent-a')
    fetchMock.mockResolvedValueOnce(api(retrieveResponse))

    const { getByLabelText, getByRole } = await renderPage()

    await userEvent.fill(getByLabelText('Query'), 'memory trace')
    await userEvent.click(getByLabelText('Trace'))
    await userEvent.click(getByRole('button', { name: 'Retrieve' }))
    await userEvent.click(getByRole('button', { name: 'Trace details' }))

    await expect
      .element(getByRole('button', { name: 'attributes' }))
      .toHaveAttribute('aria-expanded', 'false')
    await expect
      .element(getByRole('button', { name: 'candidates' }))
      .toHaveAttribute('aria-expanded', 'false')
  })

  async function expectRetrieve(body: unknown) {
    await vi.waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        '/open/v1/memory/retrieve',
        expect.objectContaining({
          method: 'POST',
          body: JSON.stringify(body),
        })
      )
    )
  }
})
