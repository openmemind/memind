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

import { describe, expect, it } from 'vitest'

import { parseConfig } from '../src/config.js'
import { createRuntimeStores, resolveWorkspaceDir } from '../src/runtime.js'

describe('resolveWorkspaceDir', () => {
  it('prefers OpenClaw context workspaceDir over event cwd and process cwd', () => {
    expect(
      resolveWorkspaceDir(
        { cwd: '/repo/from-event' },
        { workspaceDir: '/repo/from-ctx' },
        '/repo/from-process',
      ),
    ).toBe('/repo/from-ctx')
  })

  it('uses event cwd when context workspaceDir is unavailable', () => {
    expect(resolveWorkspaceDir({ cwd: '/repo/from-event' }, {}, '/repo/from-process')).toBe(
      '/repo/from-event',
    )
  })
})

describe('createRuntimeStores', () => {
  it('creates stores from the current stateDir lazily', () => {
    const stores = createRuntimeStores(parseConfig({}), () => '/tmp/openclaw-state')
    const runtime = stores.current()
    expect(runtime.stateRoot).toBe(path.join('/tmp/openclaw-state', 'state'))
    expect(runtime.retryRoot).toBe(path.join('/tmp/openclaw-state', 'retry'))
    expect(runtime.state).toBe(stores.current().state)
  })

  it('recreates stores when service start updates stateDir', () => {
    let stateDir = '/tmp/first'
    const stores = createRuntimeStores(parseConfig({}), () => stateDir)
    const first = stores.current()
    stateDir = '/tmp/second'
    const second = stores.current()
    expect(second.stateRoot).toBe(path.join('/tmp/second', 'state'))
    expect(second.state).not.toBe(first.state)
  })
})
