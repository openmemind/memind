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

import { MemindClient } from '@openmemind/memind'

import type { MemindMemoryClient, MemindOpenClawConfig, SecretRef } from './types.js'

export function createMemindMemoryClient(cfg: MemindOpenClawConfig): MemindMemoryClient {
  const client = new MemindClient({
    baseUrl: cfg.memindApiUrl,
    apiToken: resolveToken(cfg.memindApiToken),
    timeoutMs: 15_000,
    maxRetries: 0,
  })
  return client.memory
}

export function resolveToken(
  token: string | SecretRef | undefined,
  env: Record<string, string | undefined> = process.env,
): string | undefined {
  if (!token) return undefined
  if (typeof token === 'string') return token.trim() || undefined
  if (token.source === 'env') return env[token.id]?.trim() || undefined
  return undefined
}
