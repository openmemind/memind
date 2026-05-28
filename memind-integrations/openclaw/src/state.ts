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

import { mkdir, readFile, stat, writeFile } from 'node:fs/promises'
import path from 'node:path'

import type { AgentTimelineEvent } from './types.js'

type StateFile = {
  version: 1
  submitted: string[]
}

type AgentTimelineStateFile = {
  version: 1
  events: AgentTimelineEvent[]
}

export class SubmittedStateStore {
  constructor(
    private readonly root: string,
    private readonly options: { maxAgeDays: number },
  ) {}

  async filterUnsubmitted(sessionKey: string, fingerprints: string[]): Promise<string[]> {
    const state = await this.read(sessionKey)
    const submitted = new Set(state.submitted)
    return fingerprints.filter((fingerprint) => !submitted.has(fingerprint))
  }

  async markSubmitted(sessionKey: string, fingerprints: string[]): Promise<void> {
    const state = await this.read(sessionKey)
    const next = new Set(state.submitted)
    fingerprints.forEach((fingerprint) => next.add(fingerprint))
    await this.write(sessionKey, { version: 1, submitted: [...next].sort() })
  }

  private async read(sessionKey: string): Promise<StateFile> {
    try {
      const statePath = this.pathFor(sessionKey)
      const info = await stat(statePath)
      const ageDays = (Date.now() - info.mtimeMs) / 86_400_000
      if (ageDays > this.options.maxAgeDays) return { version: 1, submitted: [] }
      const raw = await readFile(statePath, 'utf8')
      const parsed = JSON.parse(raw) as StateFile
      if (parsed.version === 1 && Array.isArray(parsed.submitted)) return parsed
    } catch {
      return { version: 1, submitted: [] }
    }
    return { version: 1, submitted: [] }
  }

  private async write(sessionKey: string, state: StateFile): Promise<void> {
    await mkdir(this.root, { recursive: true })
    await writeFile(this.pathFor(sessionKey), JSON.stringify(state, null, 2), 'utf8')
  }

  private pathFor(sessionKey: string): string {
    return path.join(this.root, `${safeKey(sessionKey)}.json`)
  }
}

export class AgentTimelineStateStore {
  constructor(
    private readonly root: string,
    private readonly options: { maxAgeDays: number; maxEvents: number },
  ) {}

  async appendAgentEvent(sessionKey: string, event: AgentTimelineEvent): Promise<void> {
    if (!event.eventId) return
    const state = await this.read(sessionKey)
    if (state.events.some((candidate) => candidate.eventId === event.eventId)) return
    const events = [...state.events, event]
      .sort(compareEvents)
      .slice(Math.max(0, state.events.length + 1 - this.options.maxEvents))
    await this.write(sessionKey, { version: 1, events })
  }

  async listAgentEvents(sessionKey: string): Promise<AgentTimelineEvent[]> {
    return (await this.read(sessionKey)).events.sort(compareEvents)
  }

  async clearAgentEvents(sessionKey: string, eventIds?: string[]): Promise<void> {
    if (!eventIds || eventIds.length === 0) {
      await this.write(sessionKey, { version: 1, events: [] })
      return
    }
    const submitted = new Set(eventIds)
    const state = await this.read(sessionKey)
    await this.write(sessionKey, {
      version: 1,
      events: state.events.filter((event) => !event.eventId || !submitted.has(event.eventId)),
    })
  }

  async nextAgentEventSeq(sessionKey: string): Promise<number> {
    const events = await this.listAgentEvents(sessionKey)
    const maxSeq = events.reduce((max, event) => Math.max(max, event.seq ?? 0), 0)
    return maxSeq + 1
  }

  private async read(sessionKey: string): Promise<AgentTimelineStateFile> {
    try {
      const statePath = this.pathFor(sessionKey)
      const info = await stat(statePath)
      const ageDays = (Date.now() - info.mtimeMs) / 86_400_000
      if (ageDays > this.options.maxAgeDays) return { version: 1, events: [] }
      const raw = await readFile(statePath, 'utf8')
      const parsed = JSON.parse(raw) as AgentTimelineStateFile
      if (parsed.version === 1 && Array.isArray(parsed.events)) {
        return {
          version: 1,
          events: parsed.events
            .filter((event): event is AgentTimelineEvent => Boolean(event?.eventId))
            .sort(compareEvents),
        }
      }
    } catch {
      return { version: 1, events: [] }
    }
    return { version: 1, events: [] }
  }

  private async write(sessionKey: string, state: AgentTimelineStateFile): Promise<void> {
    await mkdir(this.root, { recursive: true })
    await writeFile(this.pathFor(sessionKey), JSON.stringify(state, null, 2), 'utf8')
  }

  private pathFor(sessionKey: string): string {
    return path.join(this.root, `${safeKey(sessionKey)}.json`)
  }
}

export function safeKey(value: string): string {
  return value.replace(/[^a-zA-Z0-9_.-]+/g, '_') || 'default'
}

function compareEvents(left: AgentTimelineEvent, right: AgentTimelineEvent): number {
  const seq = (left.seq ?? Number.MAX_SAFE_INTEGER) - (right.seq ?? Number.MAX_SAFE_INTEGER)
  if (seq !== 0) return seq
  return String(left.eventId ?? '').localeCompare(String(right.eventId ?? ''))
}
