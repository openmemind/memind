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

import path from 'node:path'

import { RetrySpool } from './retry.js'
import { SubmittedStateStore } from './state.js'
import type { MemindOpenClawConfig } from './types.js'

export type RuntimeStores = {
  stateRoot: string
  retryRoot?: string
  state: SubmittedStateStore
  retry?: RetrySpool
}

export function resolveWorkspaceDir(
  event: Record<string, unknown> | undefined,
  ctx: Record<string, unknown> | undefined,
  fallback: string,
): string {
  return (
    stringValue(ctx?.workspaceDir) ??
    stringValue(ctx?.cwd) ??
    stringValue(event?.workspaceDir) ??
    stringValue(event?.cwd) ??
    fallback
  )
}

export function createRuntimeStores(
  cfg: MemindOpenClawConfig,
  getStateDir: () => string,
): { current(): RuntimeStores } {
  let cachedDir: string | undefined
  let cached: RuntimeStores | undefined
  return {
    current(): RuntimeStores {
      const stateDir = getStateDir()
      if (cached && cachedDir === stateDir) return cached
      const stateRoot = path.join(stateDir, 'state')
      const retryRoot = path.join(stateDir, 'retry')
      cachedDir = stateDir
      cached = {
        stateRoot,
        retryRoot: cfg.ingestRetrySpool ? retryRoot : undefined,
        state: new SubmittedStateStore(stateRoot, { maxAgeDays: cfg.stateMaxAgeDays }),
        retry: cfg.ingestRetrySpool
          ? new RetrySpool(retryRoot, {
              maxFiles: cfg.ingestRetryMaxFiles,
              maxAgeDays: cfg.ingestRetryMaxAgeDays,
            })
          : undefined,
      }
      return cached
    },
  }
}

function stringValue(value: unknown): string | undefined {
  return typeof value === 'string' && value.trim() ? value : undefined
}
