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
import { Role, Strategy } from '../src/types/common.js'
import { Message, RawContent } from '../src/types/message.js'

describe('Role', () => {
  it('exposes USER and ASSISTANT', () => {
    expect(Role.USER).toBe('USER')
    expect(Role.ASSISTANT).toBe('ASSISTANT')
  })
})

describe('Strategy', () => {
  it('exposes SIMPLE and DEEP', () => {
    expect(Strategy.SIMPLE).toBe('SIMPLE')
    expect(Strategy.DEEP).toBe('DEEP')
  })
})

describe('Message.user', () => {
  it('creates a user message with text content', () => {
    const msg = Message.user('hello')
    expect(msg).toEqual({
      role: 'USER',
      content: [{ type: 'text', text: 'hello' }],
    })
  })

  it('accepts optional timestamp', () => {
    const msg = Message.user('hi', { timestamp: '2026-01-01T00:00:00Z' })
    expect(msg.timestamp).toBe('2026-01-01T00:00:00Z')
  })
})

describe('Message.assistant', () => {
  it('creates an assistant message with text content', () => {
    const msg = Message.assistant('world')
    expect(msg).toEqual({
      role: 'ASSISTANT',
      content: [{ type: 'text', text: 'world' }],
    })
  })
})

describe('RawContent.conversation', () => {
  it('wraps messages in conversation type', () => {
    const msgs = [Message.user('hi')]
    const rc = RawContent.conversation(msgs)
    expect(rc).toEqual({ type: 'conversation', messages: msgs })
  })

  it('rejects empty messages array', () => {
    expect(() => RawContent.conversation([])).toThrow()
  })

  it('rejects malformed message values before network calls can use them', () => {
    expect(() => RawContent.conversation([{ role: 'USER', content: [] } as never])).toThrow(
      /content/,
    )
    expect(() =>
      RawContent.conversation([
        { role: 'SYSTEM', content: [{ type: 'text', text: 'bad' }] } as never,
      ]),
    ).toThrow(/role/)
  })
})

describe('RawContent.map', () => {
  it('creates a typed raw content object', () => {
    const rc = RawContent.map('document', { title: 'Test', body: 'Content' })
    expect(rc).toEqual({ type: 'document', title: 'Test', body: 'Content' })
  })

  it('rejects type "conversation"', () => {
    expect(() => RawContent.map('conversation')).toThrow()
  })

  it('rejects fields containing a "type" key', () => {
    expect(() => RawContent.map('doc', { type: 'bad' } as never)).toThrow()
  })

  it('rejects non-JSON field values', () => {
    expect(() => RawContent.map('doc', { bad: undefined as never })).toThrow(/JSON/)
    expect(() => RawContent.map('doc', { bad: Number.NaN })).toThrow(/JSON/)
    expect(() => RawContent.map('doc', { bad: (() => undefined) as never })).toThrow(/JSON/)
  })

  it('works without fields', () => {
    const rc = RawContent.map('ping')
    expect(rc).toEqual({ type: 'ping' })
  })

  it('rejects blank type', () => {
    expect(() => RawContent.map('')).toThrow()
    expect(() => RawContent.map('  ')).toThrow()
  })
})
