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

import { mkdtemp, rm, utimes } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import path from 'node:path'

import { afterEach, beforeEach, describe, expect, it } from 'vitest'

import { SubmittedStateStore } from '../src/state.js'

let dir: string

beforeEach(async () => {
  dir = await mkdtemp(path.join(tmpdir(), 'memind-openclaw-state-'))
})

afterEach(async () => {
  await rm(dir, { recursive: true, force: true })
})

describe('SubmittedStateStore', () => {
  it('marks and filters submitted fingerprints', async () => {
    const store = new SubmittedStateStore(dir, { maxAgeDays: 14 })
    await store.markSubmitted('session:1', ['a', 'b'])
    await expect(store.filterUnsubmitted('session:1', ['a', 'c'])).resolves.toEqual(['c'])
  })

  it('treats expired state files as empty', async () => {
    const store = new SubmittedStateStore(dir, { maxAgeDays: 1 })
    await store.markSubmitted('session:1', ['a'])
    const twoDaysAgo = new Date(Date.now() - 2 * 86_400_000)
    await utimes(path.join(dir, 'session_1.json'), twoDaysAgo, twoDaysAgo)
    await expect(store.filterUnsubmitted('session:1', ['a'])).resolves.toEqual(['a'])
  })
})
