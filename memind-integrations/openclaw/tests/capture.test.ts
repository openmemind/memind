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
      filterUnsubmitted: vi
        .fn()
        .mockImplementation(async (_sessionKey: string, fingerprints: string[]) => fingerprints),
      markSubmitted: vi.fn().mockResolvedValue(undefined),
    }
    const result = await handleCapture({
      cfg,
      client,
      identity,
      event: { success: true, messages: [{ role: 'user', content: 'remember me', id: 'f1' }] },
      ctx: { sessionKey: 's1' },
      state,
    })
    expect(result.submitted).toBe(1)
    expect(client.extract).toHaveBeenCalledWith(
      expect.objectContaining({
        userId: 'u1',
        agentId: 'a1',
        sourceClient: 'openclaw',
      }),
      expect.objectContaining({ maxRetries: 0 }),
    )
    expect(state.markSubmitted).toHaveBeenCalledWith('s1', expect.any(Array))
  })

  it('keeps only the final turn by default', async () => {
    const cfg = parseConfig({})
    const client: MemindMemoryClient = {
      retrieve: vi.fn(),
      extract: vi.fn().mockResolvedValue(successfulExtractResponse()),
    }
    const state = {
      filterUnsubmitted: vi
        .fn()
        .mockImplementation(async (_sessionKey: string, fingerprints: string[]) => fingerprints),
      markSubmitted: vi.fn().mockResolvedValue(undefined),
    }
    await handleCapture({
      cfg,
      client,
      identity,
      event: {
        success: true,
        messages: [
          { role: 'user', content: 'old question', id: 'old-u' },
          { role: 'assistant', content: 'old answer', id: 'old-a' },
          { role: 'user', content: 'new question', id: 'new-u' },
          { role: 'assistant', content: 'new answer', id: 'new-a' },
        ],
      },
      ctx: { sessionKey: 's1' },
      state,
    })
    expect(client.extract).toHaveBeenCalledWith(
      expect.objectContaining({
        rawContent: expect.objectContaining({
          type: 'conversation',
          messages: [
            expect.objectContaining({
              role: 'USER',
              content: [{ type: 'text', text: 'new question' }],
            }),
            expect.objectContaining({
              role: 'ASSISTANT',
              content: [{ type: 'text', text: 'new answer' }],
            }),
          ],
        }),
      }),
      expect.any(Object),
    )
  })

  it('respects ingestionMaxMessagesPerHook after final-turn selection', async () => {
    const cfg = parseConfig({ ingestionMaxMessagesPerHook: 1 })
    const client: MemindMemoryClient = {
      retrieve: vi.fn(),
      extract: vi.fn().mockResolvedValue(successfulExtractResponse()),
    }
    const state = {
      filterUnsubmitted: vi
        .fn()
        .mockImplementation(async (_sessionKey: string, fingerprints: string[]) => fingerprints),
      markSubmitted: vi.fn().mockResolvedValue(undefined),
    }
    const result = await handleCapture({
      cfg,
      client,
      identity,
      event: {
        success: true,
        messages: [
          { role: 'user', content: 'new question', id: 'new-u' },
          { role: 'assistant', content: 'new answer', id: 'new-a' },
        ],
      },
      ctx: { sessionKey: 's1' },
      state,
    })
    expect(result.submitted).toBe(1)
    expect(state.markSubmitted).toHaveBeenCalledWith(
      's1',
      expect.arrayContaining([expect.any(String)]),
    )
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
      state: { filterUnsubmitted: vi.fn(), markSubmitted: vi.fn() },
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
        filterUnsubmitted: vi
          .fn()
          .mockImplementation(async (_sessionKey: string, fingerprints: string[]) => fingerprints),
        markSubmitted: vi.fn(),
      },
      retry,
    })
    expect(result.submitted).toBe(0)
    expect(retry.enqueue).toHaveBeenCalled()
  })
})
