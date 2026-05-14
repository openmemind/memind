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

declare module 'openclaw/plugin-sdk' {
  export type OpenClawHookHandler = (event: unknown, ctx?: unknown) => unknown | Promise<unknown>

  export interface OpenClawPluginApi {
    pluginConfig?: Record<string, unknown>
    config?: {
      plugins?: {
        slots?: Record<string, string>
        memoryPluginId?: string
        entries?: Record<string, { enabled?: boolean; config?: Record<string, unknown> }>
      }
    }
    logger: {
      debug(message: string): void
      info(message: string): void
      warn(message: string): void
      error(message: string): void
    }
    on(event: string, handler: OpenClawHookHandler): void
    registerService(service: {
      id: string
      start: (context?: { stateDir?: string; [key: string]: unknown }) => void
      stop: () => void
    }): void
    resolvePath?(path: string): string
    registerMemoryCapability?(config: Record<string, unknown>): void
    [key: string]: unknown
  }
}

declare module 'openclaw/plugin-sdk/plugin-entry' {
  import type { OpenClawPluginApi } from 'openclaw/plugin-sdk'

  export interface PluginEntry {
    id: string
    name: string
    description?: string
    register(api: OpenClawPluginApi): void
  }

  export function definePluginEntry<T extends PluginEntry>(entry: T): T
}
