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

import { homedir } from 'node:os'
import path from 'node:path'

import type { OpenClawPluginApi } from 'openclaw/plugin-sdk'
import { definePluginEntry } from 'openclaw/plugin-sdk/plugin-entry'

import { handleCapture } from './capture.js'
import { createMemindMemoryClient } from './client.js'
import { parseConfig, PLUGIN_ID, shouldRunAutomaticHooks } from './config.js'
import { resolveIdentity } from './identity.js'
import { handleRecall } from './recall.js'
import { createRuntimeStores, resolveWorkspaceDir } from './runtime.js'

const plugin = definePluginEntry({
  id: PLUGIN_ID,
  name: 'Memind Memory',
  description: 'Memind memory backend for OpenClaw',

  register(api: OpenClawPluginApi) {
    const cfg = parseConfig(readPluginConfig(api))
    let stateDir = path.join(homedir(), '.memind', 'openclaw')
    const selected = shouldRunAutomaticHooks(PLUGIN_ID, {
      slots: api.config?.plugins?.slots,
      memoryPluginId:
        typeof api.config?.plugins?.memoryPluginId === 'string'
          ? api.config.plugins.memoryPluginId
          : undefined,
    })

    api.registerService({
      id: PLUGIN_ID,
      start: (context?: { stateDir?: string }) => {
        const provided = context?.stateDir
        if (typeof provided === 'string' && provided.trim()) stateDir = provided
        api.logger.info(`memind-openclaw: service initialized (stateDir: ${stateDir})`)
      },
      stop: () => {
        api.logger.info('memind-openclaw: stopped')
      },
    })

    if (!selected) {
      api.logger.warn(
        'memind-openclaw: plugin is enabled but not selected in plugins.slots.memory; automatic hooks are disabled',
      )
      return
    }

    api.registerMemoryCapability?.({
      runtime: {
        provider: 'memind',
        pluginId: PLUGIN_ID,
      },
    })

    const client = createMemindMemoryClient(cfg)
    const stores = createRuntimeStores(cfg, () => stateDir)

    if (cfg.autoRetrieve) {
      api.on('before_prompt_build', async (event: unknown, ctx: unknown) => {
        const hookEvent = objectRecord(event)
        const hookCtx = objectRecord(ctx)
        const cwd = resolveWorkspaceDir(hookEvent, hookCtx, process.cwd())
        debug(api, cfg.debug, `memind-openclaw: resolved workspaceDir=${cwd} stateDir=${stateDir}`)
        const identity = resolveIdentity(cfg, {
          cwd,
          sessionKey: stringValue(hookCtx.sessionKey),
        })
        return handleRecall({
          cfg,
          client,
          identity,
          event: hookEvent,
          ctx: hookCtx,
          logger: api.logger,
        })
      })
    }

    if (cfg.autoIngest) {
      api.on('agent_end', async (event: unknown, ctx: unknown) => {
        const hookEvent = objectRecord(event)
        const hookCtx = objectRecord(ctx)
        const runtime = stores.current()
        const cwd = resolveWorkspaceDir(hookEvent, hookCtx, process.cwd())
        debug(api, cfg.debug, `memind-openclaw: resolved workspaceDir=${cwd} stateDir=${stateDir}`)
        const identity = resolveIdentity(cfg, {
          cwd,
          sessionKey: stringValue(hookCtx.sessionKey),
        })
        if (runtime.retry) {
          runtime.retry.flush(client).catch((error: unknown) => {
            api.logger.warn(`memind-openclaw: retry flush failed: ${String(error)}`)
          })
        }
        return handleCapture({
          cfg,
          client,
          identity,
          event: hookEvent,
          ctx: hookCtx,
          state: runtime.state,
          retry: runtime.retry,
          logger: api.logger,
        })
      })
    }
  },
})

function readPluginConfig(api: OpenClawPluginApi): Record<string, unknown> {
  return api.pluginConfig ?? api.config?.plugins?.entries?.[PLUGIN_ID]?.config ?? {}
}

function debug(api: OpenClawPluginApi, enabled: boolean, message: string): void {
  if (enabled) api.logger.debug(message)
}

function objectRecord(value: unknown): Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : {}
}

function stringValue(value: unknown): string | undefined {
  return typeof value === 'string' && value.trim() ? value : undefined
}

export default plugin
