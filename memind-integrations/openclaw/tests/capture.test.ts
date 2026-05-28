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

import type { ExtractMemoryResponse } from '@openmemind/memind'
import { describe, expect, it, vi } from 'vitest'

import { parseConfig } from '../src/config.js'
import { handleCapture } from '../src/capture.js'
import type { Identity, MemindMemoryClient } from '../src/types.js'

const identity: Identity = {
  userId: 'u1',
  agentId: 'a1',
  sourceClient: 'openclaw',
  sessionKey: 's1',
}

function successfulExtractResponse(): ExtractMemoryResponse {
  return {
    status: 'SUCCESS',
    rawDataIds: [],
    itemIds: [],
    insightIds: [],
    insightPending: false,
  }
}

describe('handleCapture', () => {
  it('extracts successful turns and marks submitted fingerprints', async () => {
    const cfg = parseConfig({})
    const client: MemindMemoryClient = {
      retrieve: vi.fn(),
      extract: vi.fn().mockResolvedValue(successfulExtractResponse()),
    }
    const state = {
      listAgentEvents: vi.fn().mockResolvedValue([
        { eventId: 'prompt-1', seq: 1, kind: 'user_prompt', text: 'remember me' },
        { eventId: 'stop-1', seq: 2, kind: 'stop', status: 'success' },
      ]),
      clearAgentEvents: vi.fn().mockResolvedValue(undefined),
    }
    const result = await handleCapture({
      cfg,
      client,
      identity,
      event: { success: true },
      ctx: { sessionKey: 's1' },
      state,
    })
    expect(result.submitted).toBe(2)
    expect(client.extract).toHaveBeenCalledWith(
      expect.objectContaining({
        userId: 'u1',
        agentId: 'a1',
        sourceClient: 'openclaw',
        rawContent: expect.objectContaining({
          type: 'agent_timeline',
          metadata: expect.objectContaining({ profile: 'general', runtime: 'openclaw' }),
          events: [
            expect.objectContaining({ kind: 'user_prompt' }),
            expect.objectContaining({ kind: 'stop' }),
          ],
        }),
      }),
      expect.objectContaining({ maxRetries: 0 }),
    )
    expect(state.clearAgentEvents).toHaveBeenCalledWith('s1', ['prompt-1', 'stop-1'])
  })

  it('skips capture until enough timeline events are buffered', async () => {
    const cfg = parseConfig({})
    const client: MemindMemoryClient = { retrieve: vi.fn(), extract: vi.fn() }
    const state = {
      listAgentEvents: vi
        .fn()
        .mockResolvedValue([
          { eventId: 'prompt-1', seq: 1, kind: 'user_prompt', text: 'remember me' },
        ]),
      clearAgentEvents: vi.fn(),
    }
    const result = await handleCapture({
      cfg,
      client,
      identity,
      event: { success: true },
      ctx: { sessionKey: 's1' },
      state,
    })
    expect(result.submitted).toBe(0)
    expect(client.extract).not.toHaveBeenCalled()
  })

  it('respects disabled agent timeline ingestion', async () => {
    const cfg = parseConfig({ autoIngestAgentTimeline: false })
    const client: MemindMemoryClient = {
      retrieve: vi.fn(),
      extract: vi.fn().mockResolvedValue(successfulExtractResponse()),
    }
    const state = {
      listAgentEvents: vi.fn(),
      clearAgentEvents: vi.fn(),
    }
    const result = await handleCapture({
      cfg,
      client,
      identity,
      event: { success: true },
      ctx: { sessionKey: 's1' },
      state,
    })
    expect(result.submitted).toBe(0)
    expect(state.listAgentEvents).not.toHaveBeenCalled()
  })

  it('skips failed turns', async () => {
    const cfg = parseConfig({})
    const client: MemindMemoryClient = { retrieve: vi.fn(), extract: vi.fn() }
    const result = await handleCapture({
      cfg,
      client,
      identity,
      event: { success: false, messages: [{ role: 'user', content: 'no' }] },
      ctx: { sessionKey: 's1' },
      state: { listAgentEvents: vi.fn(), clearAgentEvents: vi.fn() },
    })
    expect(result.submitted).toBe(0)
    expect(client.extract).not.toHaveBeenCalled()
  })

  it('spools failed extraction', async () => {
    const cfg = parseConfig({})
    const client: MemindMemoryClient = {
      retrieve: vi.fn(),
      extract: vi.fn().mockRejectedValue(new Error('down')),
    }
    const retry = { enqueue: vi.fn().mockResolvedValue(undefined) }
    const result = await handleCapture({
      cfg,
      client,
      identity,
      event: { success: true, messages: [{ role: 'user', content: 'remember me' }] },
      ctx: { sessionKey: 's1' },
      state: {
        listAgentEvents: vi.fn().mockResolvedValue([
          { eventId: 'prompt-1', seq: 1, kind: 'user_prompt', text: 'remember me' },
          { eventId: 'stop-1', seq: 2, kind: 'stop', status: 'success' },
        ]),
        clearAgentEvents: vi.fn(),
      },
      retry,
    })
    expect(result.submitted).toBe(0)
    expect(retry.enqueue).toHaveBeenCalledWith(
      expect.objectContaining({
        rawContent: expect.objectContaining({ type: 'agent_timeline' }),
      }),
    )
  })
})
