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

import { describe, expect, it, vi } from 'vitest'

import plugin from '../src/index.js'

type HookHandler = (...args: unknown[]) => unknown | Promise<unknown>
type RegisteredService = { start(args: { stateDir?: string }): void; stop(): void }
type TestApi = ReturnType<typeof api>['api']
type OptionalPluginConfigApi = Omit<TestApi, 'pluginConfig'> & {
  pluginConfig?: Record<string, unknown>
}

function api(slots: Record<string, string> | undefined = { memory: 'memind-openclaw' }) {
  const handlers: Record<string, HookHandler> = {}
  return {
    api: {
      pluginConfig: {},
      config: { plugins: { slots, entries: {} } },
      logger: { debug: vi.fn(), info: vi.fn(), warn: vi.fn(), error: vi.fn() },
      on: vi.fn((event: string, handler: HookHandler) => {
        handlers[event] = handler
      }),
      registerService: vi.fn(),
    },
    handlers,
  }
}

describe('plugin entry', () => {
  it('registers service and hooks when selected as memory slot', () => {
    const fixture = api()
    plugin.register(fixture.api as TestApi)
    expect(fixture.api.registerService).toHaveBeenCalledWith(
      expect.objectContaining({ id: 'memind-openclaw' }),
    )
    expect(fixture.api.on).toHaveBeenCalledWith('before_prompt_build', expect.any(Function))
    expect(fixture.api.on).toHaveBeenCalledWith('agent_end', expect.any(Function))
  })

  it('warns and skips hooks when not selected as memory slot', () => {
    const fixture = api({ memory: 'openclaw-mem0' })
    plugin.register(fixture.api as TestApi)
    expect(fixture.api.registerService).toHaveBeenCalled()
    expect(fixture.api.on).not.toHaveBeenCalled()
    expect(fixture.api.logger.warn).toHaveBeenCalledWith(expect.stringContaining('not selected'))
  })

  it('reads config from plugin entries when pluginConfig is unavailable', () => {
    const fixture = api()
    const pluginConfigApi = fixture.api as OptionalPluginConfigApi
    delete pluginConfigApi.pluginConfig
    fixture.api.config.plugins.entries = {
      'memind-openclaw': { enabled: true, config: { autoRetrieve: false } },
    }
    plugin.register(pluginConfigApi as TestApi)
    expect(fixture.api.on).not.toHaveBeenCalledWith('before_prompt_build', expect.any(Function))
    expect(fixture.api.on).toHaveBeenCalledWith('agent_end', expect.any(Function))
  })

  it('registers a minimal memory capability marker when supported by OpenClaw', () => {
    const fixture = api()
    const registerMemoryCapability = vi.fn()
    const apiWithCapability = { ...fixture.api, registerMemoryCapability }
    plugin.register(
      apiWithCapability as TestApi & { registerMemoryCapability: typeof registerMemoryCapability },
    )
    expect(registerMemoryCapability).toHaveBeenCalledWith(
      expect.objectContaining({
        runtime: expect.objectContaining({ provider: 'memind' }),
      }),
    )
  })

  it('uses OpenClaw stateDir and workspace context after service start', async () => {
    const fixture = api()
    fixture.api.pluginConfig = { debug: true, ingestRetrySpool: false }
    plugin.register(fixture.api as TestApi)
    const service = (fixture.api.registerService.mock.calls as Array<[RegisteredService]>)[0]?.[0]
    expect(service).toBeDefined()
    service?.start({ stateDir: '/tmp/openclaw-plugin-state' })
    const agentEnd = fixture.handlers.agent_end
    expect(agentEnd).toBeDefined()
    await agentEnd?.(
      {
        success: false,
        messages: [],
      },
      {
        sessionKey: 'agent:main:main',
        workspaceDir: '/repo/from-ctx',
      },
    )
    expect(fixture.api.logger.debug).toHaveBeenCalledWith(
      expect.stringContaining('/tmp/openclaw-plugin-state'),
    )
    expect(fixture.api.logger.debug).toHaveBeenCalledWith(expect.stringContaining('/repo/from-ctx'))
  })
})
