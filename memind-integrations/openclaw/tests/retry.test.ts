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

import { mkdtemp, readdir, rm, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import path from 'node:path'

import type { ExtractMemoryResponse } from '@openmemind/memind'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { RetrySpool } from '../src/retry.js'

let dir: string

beforeEach(async () => {
  dir = await mkdtemp(path.join(tmpdir(), 'memind-openclaw-retry-'))
})

afterEach(async () => {
  await rm(dir, { recursive: true, force: true })
})

function successfulExtractResponse(): ExtractMemoryResponse {
  return {
    status: 'SUCCESS',
    rawDataIds: [],
    itemIds: [],
    insightIds: [],
    insightPending: false,
  }
}

describe('RetrySpool', () => {
  it('enqueues and flushes successful extract retries', async () => {
    const spool = new RetrySpool(dir, { maxFiles: 20, maxAgeDays: 7 })
    await spool.enqueue({
      kind: 'extract',
      userId: 'u',
      agentId: 'a',
      sourceClient: 'openclaw',
      sessionKey: 's',
      fingerprints: ['f1'],
      rawContent: { type: 'memind-openclaw-retry-test' },
    })
    const extract = vi.fn().mockResolvedValue(successfulExtractResponse())
    const flushed = await spool.flush({ extract })
    expect(flushed).toBe(1)
    expect(await spool.size()).toBe(0)
  })

  it('keeps failed retries', async () => {
    const spool = new RetrySpool(dir, { maxFiles: 20, maxAgeDays: 7 })
    await spool.enqueue({
      kind: 'extract',
      userId: 'u',
      agentId: 'a',
      sourceClient: 'openclaw',
      sessionKey: 's',
      fingerprints: ['f1'],
      rawContent: { type: 'memind-openclaw-retry-test' },
    })
    const extract = vi.fn().mockResolvedValue({
      ...successfulExtractResponse(),
      status: 'FAILED',
      errorMessage: 'server rejected retry',
    })
    const flushed = await spool.flush({ extract })
    expect(flushed).toBe(0)
    expect(await spool.size()).toBe(1)
  })

  it('continues flushing after corrupt retry files and failed requests', async () => {
    const spool = new RetrySpool(dir, { maxFiles: 20, maxAgeDays: 7 })
    await writeFile(path.join(dir, '000-corrupt.json'), '{not json', 'utf8')
    await spool.enqueue({
      kind: 'extract',
      userId: 'u',
      agentId: 'a',
      sourceClient: 'openclaw',
      sessionKey: 's',
      fingerprints: ['f1'],
      rawContent: { type: 'first-retry' },
    })
    await spool.enqueue({
      kind: 'extract',
      userId: 'u',
      agentId: 'a',
      sourceClient: 'openclaw',
      sessionKey: 's',
      fingerprints: ['f2'],
      rawContent: { type: 'second-retry' },
    })
    const extract = vi
      .fn()
      .mockRejectedValueOnce(new Error('transient'))
      .mockResolvedValueOnce(successfulExtractResponse())
    const flushed = await spool.flush({ extract })
    expect(flushed).toBe(1)
    expect(await spool.size()).toBe(1)
    await expect(readdir(path.join(dir, 'quarantine'))).resolves.toHaveLength(1)
  })

  it('enforces maxFiles when enqueueing new retry entries', async () => {
    const disabled = new RetrySpool(path.join(dir, 'disabled'), { maxFiles: 0, maxAgeDays: 7 })
    await disabled.enqueue({
      kind: 'extract',
      userId: 'u',
      agentId: 'a',
      sourceClient: 'openclaw',
      sessionKey: 's',
      fingerprints: ['f0'],
      rawContent: { type: 'disabled-retry' },
    })
    expect(await disabled.size()).toBe(0)

    const one = new RetrySpool(path.join(dir, 'one'), { maxFiles: 1, maxAgeDays: 7 })
    await one.enqueue({
      kind: 'extract',
      userId: 'u',
      agentId: 'a',
      sourceClient: 'openclaw',
      sessionKey: 's',
      fingerprints: ['f1'],
      rawContent: { type: 'first-retry' },
    })
    await one.enqueue({
      kind: 'extract',
      userId: 'u',
      agentId: 'a',
      sourceClient: 'openclaw',
      sessionKey: 's',
      fingerprints: ['f2'],
      rawContent: { type: 'second-retry' },
    })
    expect(await one.size()).toBe(1)
  })
})
