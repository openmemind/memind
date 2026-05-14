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

import { parseConfig } from '../src/config.js'
import { handleRecall } from '../src/recall.js'
import type { Identity, MemindMemoryClient } from '../src/types.js'

const identity: Identity = { userId: 'u1', agentId: 'a1', sourceClient: 'openclaw' }

describe('handleRecall', () => {
  it('retrieves and injects context', async () => {
    const cfg = parseConfig({})
    const client: MemindMemoryClient = {
      retrieve: vi.fn().mockResolvedValue({
        insights: [],
        items: [{ id: 'i1', text: 'remember this', vectorScore: 0.1, finalScore: 0.9 }],
        rawData: [],
        evidences: [],
      }),
      extract: vi.fn(),
    }
    const result = await handleRecall({
      cfg,
      client,
      identity,
      event: { rawMessage: 'what do you know?' },
      ctx: {},
    })
    expect(client.retrieve).toHaveBeenCalledWith(
      expect.objectContaining({ userId: 'u1', agentId: 'a1', query: 'what do you know?' }),
      expect.objectContaining({ maxRetries: 0 }),
    )
    expect(result).toEqual({
      prependContext: expect.stringContaining('<memind_memories>'),
    })
  })

  it('fails open on retrieve errors', async () => {
    const cfg = parseConfig({})
    const client: MemindMemoryClient = {
      retrieve: vi.fn().mockRejectedValue(new Error('down')),
      extract: vi.fn(),
    }
    const result = await handleRecall({
      cfg,
      client,
      identity,
      event: { rawMessage: 'hello' },
      ctx: {},
    })
    expect(result).toBeUndefined()
  })

  it('skips non-interactive triggers', async () => {
    const cfg = parseConfig({})
    const client: MemindMemoryClient = { retrieve: vi.fn(), extract: vi.fn() }
    const result = await handleRecall({
      cfg,
      client,
      identity,
      event: { rawMessage: 'hello' },
      ctx: { trigger: 'cron', sessionKey: 'agent:main:cron:1' },
    })
    expect(result).toBeUndefined()
    expect(client.retrieve).not.toHaveBeenCalled()
  })
})
