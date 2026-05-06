import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import '@/styles/index.css'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { userEvent } from 'vitest/browser'
import { SidebarInset, SidebarProvider } from '@/components/ui/sidebar'
import { ConfigPage } from './index'

const memoryOptions = {
  version: 7,
  config: {
    core: [
      option('enabled', true, 'boolean'),
      option('maxItems', 10, 'integer', { min: 1, max: 100 }),
      option('scoreThreshold', 0.5, 'double', { min: 0, max: 1 }),
      option('modelName', 'small', 'string'),
      option('strategy', 'simple', 'string', { enum: ['simple', 'deep'] }),
    ],
    advanced: [option('rawJson', { mode: 'debug' }, 'object')],
  },
}

const longOptionKey =
  'retrieval.pipeline.memory.thread.projection.materialization.policy.version.with.a.very.long.unbroken.identifier'
const longOptionValue =
  'value-with-a-very-long-unbroken-token-that-should-not-force-the-config-page-to-overflow-horizontally-'.repeat(
    4
  )

function option(
  key: string,
  value: unknown,
  type: string,
  constraints: Record<string, unknown> | null = null
) {
  return {
    key,
    value,
    description: `${key} description`,
    type,
    defaultValue: value,
    constraints,
  }
}

function api(data: unknown, status = 200, code = 'success', message?: string) {
  return new Response(
    JSON.stringify({
      code,
      data,
      message,
      timestamp: '2026-04-30T00:00:00Z',
    }),
    {
      status,
      headers: { 'content-type': 'application/json' },
      statusText: message,
    }
  )
}

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <SidebarProvider>
        <SidebarInset>
          <ConfigPage />
        </SidebarInset>
      </SidebarProvider>
    </QueryClientProvider>
  )
}

describe('ConfigPage', () => {
  const fetchMock = vi.fn<typeof fetch>()

  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock)
    window.history.replaceState(null, '', '/config')
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.clearAllMocks()
  })

  it('renders config grouped by section key with typed editors', async () => {
    fetchMock.mockResolvedValueOnce(api(memoryOptions))

    const { getByLabelText, getByRole } = await renderPage()

    await expect
      .element(getByRole('heading', { name: 'core' }))
      .toBeInTheDocument()
    await expect
      .element(getByRole('heading', { name: 'advanced' }))
      .toBeInTheDocument()
    await expect
      .element(getByRole('switch', { name: 'enabled' }))
      .toBeInTheDocument()
    await expect
      .element(getByLabelText('maxItems'))
      .toHaveAttribute('type', 'number')
    await expect
      .element(getByLabelText('scoreThreshold'))
      .toHaveAttribute('type', 'number')
    await expect
      .element(getByLabelText('modelName'))
      .toHaveAttribute('type', 'text')
    await expect
      .element(getByRole('combobox', { name: 'strategy' }))
      .toBeInTheDocument()
    await expect
      .element(getByRole('button', { name: 'rawJson value' }))
      .toBeInTheDocument()
    await expect
      .element(getByLabelText('rawJson JSON'))
      .toHaveValue('{"mode":"debug"}')
  })

  it('keeps long option names and values inside the viewport', async () => {
    fetchMock.mockResolvedValueOnce(
      api({
        version: 7,
        config: {
          longSectionNameWithAnUnbrokenIdentifier: [
            option(longOptionKey, longOptionValue, 'string'),
            option('longEnum', longOptionValue, 'string', {
              enum: [longOptionValue, 'short'],
            }),
          ],
        },
      })
    )

    const { getByLabelText, getByRole } = await renderPage()

    await expect
      .element(
        getByRole('heading', {
          name: 'longSectionNameWithAnUnbrokenIdentifier',
        })
      )
      .toBeInTheDocument()
    await expect.element(getByLabelText(longOptionKey)).toBeInTheDocument()

    expect(document.documentElement.scrollWidth).toBeLessThanOrEqual(
      document.documentElement.clientWidth
    )
  })

  it('save button is disabled before edits and dirty state appears after an edit', async () => {
    fetchMock.mockResolvedValueOnce(api(memoryOptions))

    const { getByLabelText, getByRole, getByText } = await renderPage()

    await expect
      .element(getByRole('button', { name: 'Save changes' }))
      .toBeDisabled()
    await userEvent.fill(getByLabelText('modelName'), 'large')

    await expect.element(getByText('1 unsaved change')).toBeInTheDocument()
    await expect
      .element(getByRole('button', { name: 'Save changes' }))
      .not.toBeDisabled()
  })

  it('editing two fields sends one PUT with the complete config object', async () => {
    fetchMock.mockResolvedValueOnce(api(memoryOptions)).mockResolvedValueOnce(
      api({
        version: 8,
        config: {
          ...memoryOptions.config,
          core: memoryOptions.config.core.map((item) => {
            if (item.key === 'maxItems') return { ...item, value: 25 }
            if (item.key === 'modelName') return { ...item, value: 'large' }
            return item
          }),
        },
      })
    )

    const { getByLabelText, getByRole } = await renderPage()

    await userEvent.fill(getByLabelText('maxItems'), '25')
    await userEvent.fill(getByLabelText('modelName'), 'large')
    await userEvent.click(getByRole('button', { name: 'Save changes' }))

    await vi.waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        '/admin/v1/config/memory-options',
        expect.objectContaining({
          method: 'PUT',
          body: JSON.stringify({
            expectedVersion: 7,
            config: {
              ...memoryOptions.config,
              core: memoryOptions.config.core.map((item) => {
                if (item.key === 'maxItems') return { ...item, value: 25 }
                if (item.key === 'modelName') return { ...item, value: 'large' }
                return item
              }),
            },
          }),
        })
      )
    )
  })

  it('conflict response shows refresh guidance', async () => {
    fetchMock
      .mockResolvedValueOnce(api(memoryOptions))
      .mockResolvedValueOnce(
        api({ reason: 'stale' }, 409, 'conflict', 'Version mismatch')
      )

    const { getByLabelText, getByRole, getByText } = await renderPage()

    await userEvent.fill(getByLabelText('modelName'), 'large')
    await userEvent.click(getByRole('button', { name: 'Save changes' }))

    await expect
      .element(
        getByText('The server config changed. Refresh before saving again.')
      )
      .toBeInTheDocument()
  })

  it('leaving with unsaved edits prompts for confirmation', async () => {
    fetchMock.mockResolvedValueOnce(api(memoryOptions))

    const { getByLabelText } = await renderPage()

    await userEvent.fill(getByLabelText('modelName'), 'large')
    const event = new Event('beforeunload', { cancelable: true })

    expect(window.dispatchEvent(event)).toBe(false)
    expect(event.defaultPrevented).toBe(true)
  })
})
