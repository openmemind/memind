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
import { compactJson, formatDateTime, formatNumber, truncateText } from './format'

describe('format helpers', () => {
  it('formats missing dates as dash', () => {
    expect(formatDateTime(null)).toBe('-')
    expect(formatDateTime(undefined)).toBe('-')
  })

  it('formats valid dates with local date and time', () => {
    expect(formatDateTime('2026-04-30T12:34:56Z')).toContain('2026')
  })

  it('formats missing numbers as dash', () => {
    expect(formatNumber(null)).toBe('-')
    expect(formatNumber(undefined)).toBe('-')
  })

  it('formats numbers using locale separators', () => {
    expect(formatNumber(12345)).toBe('12,345')
  })

  it('serializes JSON values compactly', () => {
    expect(compactJson({ a: 1, b: true })).toBe('{"a":1,"b":true}')
    expect(compactJson('text')).toBe('"text"')
  })

  it('falls back to string conversion for unknown values', () => {
    const circular: Record<string, unknown> = {}
    circular.self = circular

    expect(compactJson(circular)).toBe('[object Object]')
  })

  it('truncates long text and handles missing values', () => {
    expect(truncateText(null, 10)).toBe('-')
    expect(truncateText('short', 10)).toBe('short')
    expect(truncateText('this is a long sentence', 10)).toBe('this is...')
  })
})
