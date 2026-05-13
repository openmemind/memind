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

const RETRYABLE_STATUS_CODES = new Set([408, 429, 500, 502, 503, 504])

const INITIAL_DELAY_MS = 500
const MAX_DELAY_MS = 2000
const JITTER_FACTOR = 0.25

export function isRetryableStatus(status: number): boolean {
  return RETRYABLE_STATUS_CODES.has(status)
}

export function computeRetryDelay(attempt: number, retryAfterMs?: number): number {
  if (retryAfterMs !== undefined && retryAfterMs > 0) {
    return retryAfterMs
  }
  const base = Math.min(INITIAL_DELAY_MS * Math.pow(2, attempt), MAX_DELAY_MS)
  const jitter = base * JITTER_FACTOR * (Math.random() * 2 - 1)
  return Math.max(0, Math.round(base + jitter))
}

export function parseRetryAfter(value: string | null | undefined): number | undefined {
  if (!value) return undefined

  const seconds = Number(value)
  if (!Number.isNaN(seconds) && Number.isFinite(seconds) && seconds > 0) {
    return seconds * 1000
  }

  const date = new Date(value)
  if (!Number.isNaN(date.getTime())) {
    const delayMs = date.getTime() - Date.now()
    return delayMs > 0 ? delayMs : undefined
  }

  return undefined
}
