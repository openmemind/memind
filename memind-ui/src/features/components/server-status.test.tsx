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
import { ServerStatus } from './server-status'

const jsonResponse = (body: unknown) =>
  new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'content-type': 'application/json' },
  })

function renderWithQueryClient(node: React.ReactNode) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  })

  return render(
    <QueryClientProvider client={queryClient}>{node}</QueryClientProvider>
  )
}

describe('ServerStatus', () => {
  const fetchMock = vi.fn<typeof fetch>()

  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.clearAllMocks()
    vi.useRealTimers()
  })

  it('shows connecting before the first health result', async () => {
    fetchMock.mockReturnValueOnce(new Promise(() => {}))

    const { getByText } = await renderWithQueryClient(<ServerStatus />)

    await expect.element(getByText('connecting')).toBeInTheDocument()
  })

  it('shows connected after health returns success', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({ data: { status: 'UP', service: 'memind-server' } })
    )

    const { getByText } = await renderWithQueryClient(<ServerStatus />)

    await expect.element(getByText('connected')).toBeInTheDocument()
  })

  it('shows disconnected after a network error', async () => {
    fetchMock.mockRejectedValueOnce(new TypeError('Failed to fetch'))

    const { getByText } = await renderWithQueryClient(<ServerStatus />)

    await expect.element(getByText('disconnected')).toBeInTheDocument()
  })

  it('polls with a 30 second interval', async () => {
    vi.useFakeTimers()
    fetchMock.mockResolvedValue(
      jsonResponse({ data: { status: 'UP', service: 'memind-server' } })
    )

    await renderWithQueryClient(<ServerStatus />)
    await vi.waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1))

    await vi.advanceTimersByTimeAsync(30_000)

    expect(fetchMock).toHaveBeenCalledTimes(2)
  })
})
