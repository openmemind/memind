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

export function serializeBody(value: unknown): string {
  if (value === undefined) {
    throw new Error('Request body cannot be top-level undefined')
  }
  const seen = new WeakSet<object>()
  return JSON.stringify(sanitizeJson(value, '$', seen))
}

function sanitizeJson(value: unknown, path: string, seen: WeakSet<object>): unknown {
  if (value === undefined) {
    return undefined
  }
  if (value === null || typeof value === 'string' || typeof value === 'boolean') {
    return value
  }
  if (typeof value === 'number') {
    if (!Number.isFinite(value)) {
      throw new Error(`Request body contains non-finite number at ${path}`)
    }
    return value
  }
  if (typeof value === 'function' || typeof value === 'symbol' || typeof value === 'bigint') {
    throw new Error(`Request body contains non-serializable ${typeof value} at ${path}`)
  }
  if (typeof value !== 'object') {
    throw new Error(`Request body contains unsupported value at ${path}`)
  }
  if (seen.has(value)) {
    throw new Error(`Request body contains cyclic object at ${path}`)
  }

  seen.add(value)
  try {
    if (Array.isArray(value)) {
      return value.map((item, index) => {
        const sanitized = sanitizeJson(item, `${path}[${index}]`, seen)
        return sanitized === undefined ? null : sanitized
      })
    }

    const result: Record<string, unknown> = {}
    for (const [key, item] of Object.entries(value)) {
      const sanitized = sanitizeJson(item, `${path}.${key}`, seen)
      if (sanitized !== undefined) {
        result[key] = sanitized
      }
    }
    return result
  } finally {
    seen.delete(value)
  }
}
