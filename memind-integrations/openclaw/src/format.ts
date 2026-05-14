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

import type { RetrieveMemoryResponse } from '@openmemind/memind'

import type { MemindOpenClawConfig } from './types.js'

const TIER_RANK: Record<string, number> = { ROOT: 0, BRANCH: 1, LEAF: 2 }

export function formatMemindContext(
  data: RetrieveMemoryResponse,
  cfg: MemindOpenClawConfig,
): string {
  const insights = [...(data.insights ?? [])]
    .filter((insight) => insight.text)
    .sort((a, b) => (TIER_RANK[a.tier ?? 'LEAF'] ?? 2) - (TIER_RANK[b.tier ?? 'LEAF'] ?? 2))
  const highLevel = insights.filter((insight) =>
    ['ROOT', 'BRANCH'].includes((insight.tier ?? '').toUpperCase()),
  )
  const selectedInsights = (highLevel.length ? highLevel : insights).slice(
    0,
    Math.min(3, cfg.retrieveMaxEntries),
  )
  const remaining = cfg.retrieveMaxEntries - selectedInsights.length
  const selectedItems = [...(data.items ?? [])]
    .filter((item) => item.text)
    .sort((a, b) => (b.finalScore ?? b.vectorScore ?? 0) - (a.finalScore ?? a.vectorScore ?? 0))
    .slice(0, Math.max(0, remaining))

  const sections: string[] = []
  if (selectedInsights.length > 0) {
    sections.push('## Insights')
    sections.push(...selectedInsights.map((insight) => `- [insight:${insight.id}] ${insight.text}`))
  }
  if (selectedItems.length > 0) {
    if (sections.length > 0) sections.push('')
    sections.push('## Memory Items')
    sections.push(...selectedItems.map((item) => `- [item:${item.id}] ${item.text}`))
  }
  if (sections.length === 0 && data.status !== 'degraded') return ''

  let body = sections.join('\n')
  if (data.status === 'degraded') {
    body += '\n[Note: Memory retrieval encountered an error. Results may be incomplete.]'
  }
  if (body.length > cfg.retrieveMaxChars) {
    const suffix = '\n[truncated]'
    body = `${body.slice(0, Math.max(0, cfg.retrieveMaxChars - suffix.length)).trimEnd()}${suffix}`
  }
  return `<memind_memories>\n${cfg.retrievePromptPreamble}\n${body}\n</memind_memories>`
}
