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

import {
  normalizeAssistantMessageEvent,
  normalizeCompactBoundaryEvent,
  normalizeSessionEndEvent,
  normalizeStopEvent,
  normalizeToolResultEvent,
  normalizeUserPromptEvent,
} from './agent-timeline.js'
import { handleCapture } from './capture.js'
import { createMemindMemoryClient } from './client.js'
import { parseConfig, PLUGIN_ID, shouldRunAutomaticHooks } from './config.js'
import { resolveIdentity } from './identity.js'
import { handleRecall } from './recall.js'
import { createRuntimeStores, resolveWorkspaceDir, type RuntimeStores } from './runtime.js'
import type { AgentTimelineEvent, OpenClawTimelineContext } from './types.js'

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

    if (cfg.autoIngest && cfg.autoIngestAgentTimeline) {
      api.on('session_start', async () => {
        const runtime = stores.current()
        if (runtime.retry) {
          runtime.retry.flush(client).catch((error: unknown) => {
            api.logger.warn(`memind-openclaw: retry flush failed: ${String(error)}`)
          })
        }
      })

      api.on('message_received', async () => {
        // OpenClaw exposes message_received in some runtimes, but automatic lifecycle memory is
        // anchored on completed agent turns to avoid ingesting partial user text.
      })

      api.on('before_agent_start', async (event: unknown, ctx: unknown) => {
        const hookEvent = objectRecord(event)
        const hookCtx = objectRecord(ctx)
        const runtime = stores.current()
        const context = await timelineContext(runtime, hookEvent, hookCtx, { startNewTurn: true })
        await appendTimelineEvent({
          runtime,
          sessionKey: context.sessionKey,
          event: normalizeUserPromptEvent({
            prompt: hookEvent.prompt ?? hookEvent.input,
            rawMessage: hookEvent.message,
            seq: await runtime.agentState.nextAgentEventSeq(context.sessionKey),
            context,
            maxFieldChars: cfg.timelineMaxFieldChars,
          }),
          logger: api.logger,
        })
      })

      api.on('tool_result_persist', async (event: unknown, ctx: unknown) => {
        const hookEvent = objectRecord(event)
        const hookCtx = objectRecord(ctx)
        const runtime = stores.current()
        const context = await timelineContext(runtime, hookEvent, hookCtx)
        await appendTimelineEvent({
          runtime,
          sessionKey: context.sessionKey,
          event: normalizeToolResultEvent({
            event: hookEvent,
            seq: await runtime.agentState.nextAgentEventSeq(context.sessionKey),
            context,
            maxFieldChars: cfg.timelineMaxFieldChars,
          }),
          logger: api.logger,
        })
      })

      api.on('after_compaction', async (event: unknown, ctx: unknown) => {
        const hookEvent = objectRecord(event)
        const hookCtx = objectRecord(ctx)
        const runtime = stores.current()
        const context = await timelineContext(runtime, hookEvent, hookCtx)
        await appendTimelineEvent({
          runtime,
          sessionKey: context.sessionKey,
          event: normalizeCompactBoundaryEvent({
            event: hookEvent,
            seq: await runtime.agentState.nextAgentEventSeq(context.sessionKey),
            context,
          }),
          logger: api.logger,
        })
      })

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
        const context = await timelineContext(runtime, hookEvent, hookCtx)
        if (hookEvent.success === false) {
          await clearIncompleteBufferedTurn(runtime, context.sessionKey, context.turnId)
          return handleCapture({
            cfg,
            client,
            identity,
            event: hookEvent,
            ctx: hookCtx,
            state: runtime.agentState,
            retry: runtime.retry,
            logger: api.logger,
          })
        }
        const assistant = normalizeAssistantMessageEvent({
          messages: hookEvent.messages,
          seq: await runtime.agentState.nextAgentEventSeq(context.sessionKey),
          context,
          maxFieldChars: cfg.timelineMaxFieldChars,
        })
        await appendTimelineEvent({
          runtime,
          sessionKey: context.sessionKey,
          event: assistant,
          logger: api.logger,
        })
        await appendTimelineEvent({
          runtime,
          sessionKey: context.sessionKey,
          event: normalizeStopEvent({
            success: hookEvent.success,
            seq: await runtime.agentState.nextAgentEventSeq(context.sessionKey),
            context,
          }),
          logger: api.logger,
        })
        return handleCapture({
          cfg,
          client,
          identity,
          event: hookEvent,
          ctx: hookCtx,
          state: runtime.agentState,
          retry: runtime.retry,
          logger: api.logger,
        })
      })

      api.on('session_end', async (event: unknown, ctx: unknown) => {
        const hookEvent = objectRecord(event)
        const hookCtx = objectRecord(ctx)
        const runtime = stores.current()
        const cwd = resolveWorkspaceDir(hookEvent, hookCtx, process.cwd())
        const identity = resolveIdentity(cfg, {
          cwd,
          sessionKey: stringValue(hookCtx.sessionKey),
        })
        const context = await timelineContext(runtime, hookEvent, hookCtx)
        await appendTimelineEvent({
          runtime,
          sessionKey: context.sessionKey,
          event: normalizeSessionEndEvent({
            event: hookEvent,
            seq: await runtime.agentState.nextAgentEventSeq(context.sessionKey),
            context,
          }),
          logger: api.logger,
        })
        return handleCapture({
          cfg,
          client,
          identity,
          event: hookEvent,
          ctx: hookCtx,
          state: runtime.agentState,
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

async function timelineContext(
  runtime: RuntimeStores,
  event: Record<string, unknown>,
  ctx: Record<string, unknown>,
  options: { startNewTurn?: boolean } = {},
): Promise<OpenClawTimelineContext> {
  const sessionKey = stringValue(ctx.sessionKey) ?? stringValue(event.sessionKey) ?? 'default'
  const turnId =
    stringValue(ctx.turnId) ??
    stringValue(event.turnId) ??
    (options.startNewTurn ? undefined : await latestBufferedTurnId(runtime, sessionKey)) ??
    `${sessionKey}-turn-${await runtime.agentState.nextAgentEventSeq(sessionKey)}`
  return {
    sessionKey,
    turnId,
    workspaceDir: stringValue(ctx.workspaceDir ?? event.workspaceDir ?? ctx.cwd ?? event.cwd),
    channelId: stringValue(ctx.channelId ?? event.channelId),
    conversationId: stringValue(ctx.conversationId ?? event.conversationId),
    agentName: stringValue(ctx.agentName ?? event.agentName),
  }
}

async function latestBufferedTurnId(
  runtime: RuntimeStores,
  sessionKey: string,
): Promise<string | undefined> {
  const events = await runtime.agentState.listAgentEvents(sessionKey)
  for (let index = events.length - 1; index >= 0; index -= 1) {
    const turnId = events[index]?.metadata?.turnId
    if (typeof turnId === 'string' && turnId.trim()) return turnId
  }
  return undefined
}

async function clearIncompleteBufferedTurn(
  runtime: RuntimeStores,
  sessionKey: string,
  turnId: string | undefined,
): Promise<void> {
  if (!turnId) return
  const events = (await runtime.agentState.listAgentEvents(sessionKey)).filter(
    (event) => event.metadata?.turnId === turnId,
  )
  const lastKind = events.at(-1)?.kind
  if (events.length > 0 && lastKind !== 'stop' && lastKind !== 'session_end') {
    await runtime.agentState.clearAgentEvents(
      sessionKey,
      events.map((event) => event.eventId).filter((eventId): eventId is string => Boolean(eventId)),
    )
  }
}

async function appendTimelineEvent(input: {
  runtime: RuntimeStores
  sessionKey: string
  event: AgentTimelineEvent | undefined
  logger: OpenClawPluginApi['logger']
}): Promise<void> {
  if (!input.event) return
  try {
    await input.runtime.agentState.appendAgentEvent(input.sessionKey, input.event)
  } catch (error: unknown) {
    input.logger.warn(`memind-openclaw: timeline append failed: ${String(error)}`)
  }
}

export default plugin
