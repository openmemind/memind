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

import { parseConfig } from '../src/config.js'
import { formatMemindContext } from '../src/format.js'

describe('formatMemindContext', () => {
  it('formats high-level insights before items', () => {
    const cfg = parseConfig({ retrieveMaxEntries: 3 })
    const text = formatMemindContext(
      {
        insights: [
          { id: 'leaf', text: 'leaf insight', tier: 'LEAF' },
          { id: 'root', text: 'root insight', tier: 'ROOT' },
        ],
        items: [{ id: 'item-1', text: 'item text', vectorScore: 0.3, finalScore: 0.9 }],
        rawData: [],
        evidences: [],
      },
      cfg,
    )
    expect(text).toContain('<memind_memories>')
    expect(text).toContain('## Insights')
    expect(text).toContain('[insight:root] root insight')
    expect(text).toContain('## Memory Items')
    expect(text).toContain('[item:item-1] item text')
  })

  it('returns empty string when no memories are available', () => {
    const cfg = parseConfig({})
    expect(formatMemindContext({ insights: [], items: [], rawData: [], evidences: [] }, cfg)).toBe(
      '',
    )
  })

  it('respects max chars', () => {
    const cfg = parseConfig({ retrieveMaxChars: 80 })
    const text = formatMemindContext(
      {
        insights: [],
        items: [{ id: 'item-1', text: 'x'.repeat(500), vectorScore: 0.1, finalScore: 0.2 }],
        rawData: [],
        evidences: [],
      },
      cfg,
    )
    const body = text
      .replace(/^<memind_memories>\n/, '')
      .replace(`${cfg.retrievePromptPreamble}\n`, '')
      .replace(/\n<\/memind_memories>$/, '')
    expect(body.length).toBeLessThanOrEqual(80)
  })

  it('keeps the memory envelope valid when truncating', () => {
    const cfg = parseConfig({ retrieveMaxChars: 12 })
    const text = formatMemindContext(
      {
        insights: [],
        items: [
          {
            id: 'item-1',
            text: 'abcdefghijklmnopqrstuvwxyz',
            vectorScore: 0.1,
            finalScore: 0.2,
          },
        ],
        rawData: [],
        evidences: [],
      },
      cfg,
    )
    expect(text).toContain('</memind_memories>')
    expect(text).toContain('[truncated]')
  })
})
