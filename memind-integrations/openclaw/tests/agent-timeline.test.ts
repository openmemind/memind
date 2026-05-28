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
  buildAgentTimelineContent,
  normalizeAssistantMessageEvent,
  normalizeStopEvent,
  normalizeToolResultEvent,
  normalizeUserPromptEvent,
} from '../src/agent-timeline.js'
import type { OpenClawTimelineContext } from '../src/types.js'

const context: OpenClawTimelineContext = {
  sessionKey: 's1',
  workspaceDir: '/repo/acme',
  channelId: 'slack:C123',
  conversationId: 'thread-456',
  agentName: 'sales-assistant',
  turnId: 'turn-1',
}

describe('agent timeline normalization', () => {
  it('normalizes user prompts and strips injected Memind context', () => {
    const event = normalizeUserPromptEvent({
      prompt: '<memind_memories>old</memind_memories>\nSummarize renewal concerns',
      seq: 1,
      context,
      maxFieldChars: 8000,
    })

    expect(event).toMatchObject({
      seq: 1,
      kind: 'user_prompt',
      text: 'Summarize renewal concerns',
      metadata: expect.objectContaining({
        sessionKey: 's1',
        turnId: 'turn-1',
      }),
    })
    expect(event?.eventId).toMatch(/^openclaw-/)
    expect(event?.contentHash).toMatch(/^[a-f0-9]{64}$/)
  })

  it('normalizes assistant messages from final messages', () => {
    const event = normalizeAssistantMessageEvent({
      messages: [
        { role: 'user', content: 'question' },
        { role: 'assistant', content: 'answer' },
      ],
      seq: 2,
      context,
      maxFieldChars: 8000,
    })

    expect(event).toMatchObject({
      seq: 2,
      kind: 'assistant_message',
      text: 'answer',
    })
  })

  it('normalizes successful tool results', () => {
    const event = normalizeToolResultEvent({
      event: {
        toolName: 'gmail.search',
        input: { query: 'renewal' },
        output: { count: 3 },
        success: true,
      },
      seq: 3,
      context,
      maxFieldChars: 8000,
    })

    expect(event).toMatchObject({
      seq: 3,
      kind: 'tool_result',
      toolName: 'gmail.search',
      status: 'success',
    })
    expect(event?.input).toContain('renewal')
    expect(event?.output).toContain('"count":3')
  })

  it('normalizes failed tool results and truncates large values', () => {
    const event = normalizeToolResultEvent({
      event: {
        toolName: 'crm.update',
        input: { id: 'customer-1' },
        error: 'x'.repeat(100),
        success: false,
      },
      seq: 4,
      context,
      maxFieldChars: 32,
    })

    expect(event).toMatchObject({
      kind: 'tool_failure',
      status: 'failed',
      toolName: 'crm.update',
    })
    expect(event?.output?.length).toBeLessThanOrEqual(32)
    expect(event?.output).toContain('[truncated]')
  })

  it('builds general agent_timeline raw content', () => {
    const prompt = normalizeUserPromptEvent({
      prompt: 'Summarize renewal concerns',
      seq: 1,
      context,
      maxFieldChars: 8000,
    })
    const stop = normalizeStopEvent({ success: true, seq: 2, context })

    const payload = buildAgentTimelineContent({
      sourceClient: 'openclaw',
      sessionId: 's1',
      agentTurnId: 'turn-1',
      events: [prompt, stop].filter((event): event is NonNullable<typeof event> => Boolean(event)),
      context,
    })

    expect(payload).toMatchObject({
      type: 'agent_timeline',
      sourceClient: 'openclaw',
      sessionId: 's1',
      agentTurnId: 'turn-1',
      metadata: {
        profile: 'general',
        runtime: 'openclaw',
        sessionKey: 's1',
        turnId: 'turn-1',
      },
    })
    expect(payload.timelineId).toContain('openclaw')
  })
})
