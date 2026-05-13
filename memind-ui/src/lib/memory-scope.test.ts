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
import {
  parseMemoryScope,
  requireRetrieveScope,
  requireUserScope,
  toMemoryIdQuery,
  toUserAgentQuery,
} from './memory-scope'

describe('memory scope helpers', () => {
  it('parses user and agent from a memoryId', () => {
    expect(parseMemoryScope('alice:agent-a')).toEqual({
      memoryId: 'alice:agent-a',
      userId: 'alice',
      agentId: 'agent-a',
      hasUserId: true,
      hasAgentId: true,
    })
  })

  it('parses a user-only memoryId', () => {
    expect(parseMemoryScope('alice')).toEqual({
      memoryId: 'alice',
      userId: 'alice',
      agentId: '',
      hasUserId: true,
      hasAgentId: false,
    })
  })

  it('keeps empty scope valid but marks both parts absent', () => {
    expect(parseMemoryScope('')).toEqual({
      memoryId: '',
      userId: '',
      agentId: '',
      hasUserId: false,
      hasAgentId: false,
    })
  })

  it('does not treat an empty userId as valid', () => {
    expect(parseMemoryScope(':agent-a')).toEqual({
      memoryId: ':agent-a',
      userId: '',
      agentId: 'agent-a',
      hasUserId: false,
      hasAgentId: true,
    })
  })

  it('does not treat an empty agentId as valid', () => {
    expect(parseMemoryScope('alice:')).toEqual({
      memoryId: 'alice:',
      userId: 'alice',
      agentId: '',
      hasUserId: true,
      hasAgentId: false,
    })
  })

  it('splits only on the first colon', () => {
    expect(parseMemoryScope('alice:agent:extra')).toEqual({
      memoryId: 'alice:agent:extra',
      userId: 'alice',
      agentId: 'agent:extra',
      hasUserId: true,
      hasAgentId: true,
    })
  })

  it('serializes memoryId query only when the scope is non-empty', () => {
    expect(toMemoryIdQuery(' alice:agent-a ')).toEqual({
      memoryId: 'alice:agent-a',
    })
    expect(toMemoryIdQuery('   ')).toEqual({})
  })

  it('serializes user and optional agent query params', () => {
    expect(toUserAgentQuery('alice')).toEqual({ userId: 'alice' })
    expect(toUserAgentQuery('alice:agent-a')).toEqual({
      userId: 'alice',
      agentId: 'agent-a',
    })
    expect(toUserAgentQuery(':agent-a')).toEqual({})
    expect(toUserAgentQuery('alice:')).toEqual({ userId: 'alice' })
  })

  it('requires user scope for detail endpoints', () => {
    expect(() => requireUserScope(':agent-a')).toThrow(
      'Memory scope must include userId'
    )
    expect(requireUserScope('alice:')).toMatchObject({
      userId: 'alice',
      hasUserId: true,
      hasAgentId: false,
    })
  })

  it('requires both userId and agentId for retrieve', () => {
    expect(() => requireRetrieveScope('alice')).toThrow(
      'Memory scope must include userId and agentId'
    )
    expect(() => requireRetrieveScope('alice:')).toThrow(
      'Memory scope must include userId and agentId'
    )
    expect(requireRetrieveScope('alice:agent-a')).toMatchObject({
      userId: 'alice',
      agentId: 'agent-a',
      hasUserId: true,
      hasAgentId: true,
    })
  })
})
