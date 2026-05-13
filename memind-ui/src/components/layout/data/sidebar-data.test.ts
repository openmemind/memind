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

import { describe, expect, it } from 'vitest'
import { sidebarData } from './sidebar-data'

describe('sidebarData', () => {
  it('orders Memind navigation by the primary admin workflow', () => {
    const items = sidebarData.navGroups[0]?.items ?? []

    expect(items.map((item) => item.title)).toEqual([
      'Dashboard',
      'Buffers',
      'Raw Data',
      'Memory Items',
      'Item Graph',
      'Memory Threads',
      'Insight Tree',
      'Settings',
    ])
    expect(items.map((item) => item.url)).toEqual([
      '/',
      '/buffers',
      '/raw-data',
      '/items',
      '/item-graph',
      '/memory-threads',
      '/insights',
      '/config',
    ])
  })

  it('does not expose Retrieve as a primary sidebar destination', () => {
    const items = sidebarData.navGroups.flatMap((group) => group.items)

    expect(items.some((item) => item.title === 'Retrieve')).toBe(false)
    expect(items.some((item) => item.url === '/retrieve')).toBe(false)
  })
})
