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
import {
  extractMessages,
  readRecentContext,
  stripInjectedContext,
  textFromContent,
} from '../src/content.js'

describe('stripInjectedContext', () => {
  it('removes Memind and OpenClaw metadata envelopes', () => {
    const text = [
      'Sender (untrusted metadata): ```json',
      '{"id":"u1"}',
      '```',
      '<memind_memories>old memory</memind_memories>',
      'Actual prompt',
    ].join('\n')
    expect(stripInjectedContext(text)).toBe('Actual prompt')
  })
})

describe('textFromContent', () => {
  it('extracts string content', () => {
    expect(textFromContent('hello')).toBe('hello')
  })

  it('extracts text blocks only by default', () => {
    const text = textFromContent([
      { type: 'text', text: 'one' },
      { type: 'image', source: { type: 'url', url: 'https://example.com/a.png' } },
      { type: 'toolCall', name: 'shell', arguments: { cmd: 'pwd' } },
      { type: 'text', text: 'two' },
    ])
    expect(text).toBe('one\n\ntwo')
  })
})

describe('extractMessages', () => {
  it('normalizes user and assistant messages', () => {
    const cfg = parseConfig({})
    const messages = extractMessages(
      [
        { role: 'user', content: 'hello', id: 'u1', timestamp: '2026-05-14T00:00:00Z' },
        { role: 'assistant', content: [{ type: 'text', text: 'hi' }], id: 'a1' },
      ],
      cfg,
    )
    expect(messages).toHaveLength(2)
    const [first, second] = messages
    if (!first || !second) {
      throw new Error('expected two extracted messages')
    }
    expect(first).toMatchObject({
      role: 'USER',
      content: [{ type: 'text', text: 'hello' }],
      timestamp: '2026-05-14T00:00:00Z',
    })
    expect(second).toMatchObject({ role: 'ASSISTANT' })
    expect(first.fingerprint).not.toBe(second.fingerprint)
  })

  it('drops unsupported roles and empty messages', () => {
    const cfg = parseConfig({})
    const messages = extractMessages(
      [
        { role: 'system', content: 'system' },
        { role: 'toolResult', content: 'result' },
        { role: 'user', content: '   ' },
        { role: 'assistant', content: '[Response interrupted by user]' },
      ],
      cfg,
    )
    expect(messages).toEqual([])
  })

  it('ignores tool calls when captureToolCalls is false', () => {
    const cfg = parseConfig({})
    const messages = extractMessages(
      [
        {
          role: 'assistant',
          content: [
            { type: 'text', text: 'using tool' },
            { type: 'toolCall', name: 'shell' },
          ],
        },
      ],
      cfg,
    )
    expect(messages).toEqual([])
  })

  it('ignores multimodal messages when captureMedia is false', () => {
    const cfg = parseConfig({})
    const messages = extractMessages(
      [
        {
          role: 'user',
          content: [
            { type: 'text', text: 'see this' },
            { type: 'image', source: { type: 'url', url: 'https://example.test/a.png' } },
          ],
        },
      ],
      cfg,
    )
    expect(messages).toEqual([])
  })
})

describe('readRecentContext', () => {
  it('uses the last N turns', () => {
    const cfg = parseConfig({ retrieveContextTurns: 1 })
    const context = readRecentContext(
      [
        { role: 'user', content: 'old user' },
        { role: 'assistant', content: 'old assistant' },
        { role: 'user', content: 'new user' },
        { role: 'assistant', content: 'new assistant' },
      ],
      cfg,
    )
    expect(context).toBe('user: new user\nassistant: new assistant')
  })
})
