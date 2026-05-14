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

import { mkdir, readdir, readFile, rm, stat, writeFile } from 'node:fs/promises'
import { randomUUID } from 'node:crypto'
import path from 'node:path'

import type { RawContentValue } from '@openmemind/memind'

export type RetryEntry = {
  version?: 1
  kind: 'extract'
  userId: string
  agentId: string
  sourceClient: string
  sessionKey: string
  fingerprints: string[]
  rawContent: RawContentValue
  createdAt?: string
}

export type RetryOptions = {
  maxFiles: number
  maxAgeDays: number
}

export class RetrySpool {
  constructor(
    private readonly root: string,
    private readonly options: RetryOptions,
  ) {}

  async enqueue(entry: RetryEntry): Promise<void> {
    if (this.options.maxFiles <= 0) return
    await mkdir(this.root, { recursive: true })
    await this.prune()
    const enriched: RetryEntry = {
      version: 1,
      createdAt: new Date().toISOString(),
      ...entry,
    }
    await writeFile(
      path.join(this.root, `${Date.now()}-${randomUUID()}.json`),
      JSON.stringify(enriched, null, 2),
      'utf8',
    )
    await this.prune()
  }

  async flush(client: {
    extract(request: {
      userId: string
      agentId: string
      rawContent: RawContentValue
      sourceClient?: string
    }): Promise<{ status: string }>
  }): Promise<number> {
    await mkdir(this.root, { recursive: true })
    await this.prune()
    let flushed = 0
    for (const file of await this.files()) {
      const full = path.join(this.root, file)
      try {
        const entry = JSON.parse(await readFile(full, 'utf8')) as RetryEntry
        const result = await client.extract({
          userId: entry.userId,
          agentId: entry.agentId,
          rawContent: entry.rawContent,
          sourceClient: entry.sourceClient,
        })
        if (result.status === 'SUCCESS') {
          await rm(full, { force: true })
          flushed += 1
        }
      } catch (error: unknown) {
        if (error instanceof SyntaxError) {
          await this.quarantine(full, file)
        }
      }
    }
    return flushed
  }

  async size(): Promise<number> {
    return (await this.files()).length
  }

  private async prune(): Promise<void> {
    const files = await this.files()
    const now = Date.now()
    for (const file of files) {
      const full = path.join(this.root, file)
      const info = await stat(full)
      const ageDays = (now - info.mtimeMs) / 86_400_000
      if (ageDays > this.options.maxAgeDays) await rm(full, { force: true })
    }
    const remaining = await this.files()
    const overflow = remaining.length - this.options.maxFiles
    if (overflow > 0) {
      for (const file of remaining.slice(0, overflow)) {
        await rm(path.join(this.root, file), { force: true })
      }
    }
  }

  private async files(): Promise<string[]> {
    try {
      return (await readdir(this.root)).filter((file) => file.endsWith('.json')).sort()
    } catch {
      return []
    }
  }

  private async quarantine(fullPath: string, file: string): Promise<void> {
    const quarantineDir = path.join(this.root, 'quarantine')
    await mkdir(quarantineDir, { recursive: true })
    await writeFile(
      path.join(quarantineDir, `${Date.now()}-${file}`),
      await readFile(fullPath, 'utf8').catch(() => ''),
      'utf8',
    )
    await rm(fullPath, { force: true })
  }
}
