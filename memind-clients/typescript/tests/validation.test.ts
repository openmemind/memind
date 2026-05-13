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
import { MemindAPIError } from '../src/core/errors.js'
import {
  assertAddMessageResponse,
  assertExtractMemoryResponse,
  assertHealthResponse,
  assertRetrieveMemoryResponse,
} from '../src/core/validate.js'

function expectParseError(fn: () => unknown): void {
  try {
    fn()
    throw new Error('expected parse error')
  } catch (err) {
    expect(err).toBeInstanceOf(MemindAPIError)
    expect((err as MemindAPIError).errorCode).toBe('parse_error')
  }
}

describe('response validators', () => {
  it('validates health required scalar fields', () => {
    expect(assertHealthResponse({ status: 'UP', service: 'memind-server' })).toEqual({
      status: 'UP',
      service: 'memind-server',
    })
    expectParseError(() => assertHealthResponse({ status: 'UP' }))
  })

  it('normalizes missing extract arrays but rejects null arrays and bad scalar types', () => {
    expect(assertExtractMemoryResponse({ status: 'SUCCESS' })).toEqual({
      status: 'SUCCESS',
      rawDataIds: [],
      itemIds: [],
      insightIds: [],
      insightPending: false,
    })
    expectParseError(() => assertExtractMemoryResponse({ status: 'SUCCESS', rawDataIds: null }))
    expectParseError(() => assertExtractMemoryResponse({ status: 'SUCCESS', rawDataIds: [123] }))
    expectParseError(() =>
      assertExtractMemoryResponse({ status: 'SUCCESS', insightPending: 'false' }),
    )
  })

  it('validates add-message response and nested result', () => {
    expect(assertAddMessageResponse({ triggered: false })).toEqual({ triggered: false })
    expect(
      assertAddMessageResponse({
        triggered: true,
        result: { status: 'SUCCESS', rawDataIds: ['rd-1'], itemIds: [], insightIds: [] },
      }),
    ).toEqual({
      triggered: true,
      result: {
        status: 'SUCCESS',
        rawDataIds: ['rd-1'],
        itemIds: [],
        insightIds: [],
        insightPending: false,
      },
    })
    expectParseError(() => assertAddMessageResponse({ triggered: 'false' }))
    expectParseError(() => assertAddMessageResponse({ triggered: true, result: null }))
  })

  it('validates retrieve arrays, element shapes, and trace fields', () => {
    const response = assertRetrieveMemoryResponse({
      items: [{ id: 'item-1', text: 'likes coffee', vectorScore: 0.9, finalScore: 0.8 }],
      insights: [{ id: 'ins-1', text: 'prefers concise answers' }],
      rawData: [{ rawDataId: 'rd-1', maxScore: 0.7, itemIds: ['item-1'] }],
      evidences: ['evidence-1'],
      trace: {
        stages: [{ degraded: false, skipped: false, inputCount: 1 }],
        merge: { inputCount: 1, outputCount: 1, deduplicatedCount: 0, sourceCount: 1 },
        finalResults: { itemCount: 1, insightCount: 1, rawDataCount: 1, evidenceCount: 1 },
      },
      futureField: 'ignored',
    })

    expect(response.items).toEqual([expect.objectContaining({ id: 'item-1' })])
    expect(response.trace?.stages).toHaveLength(1)
    expectParseError(() => assertRetrieveMemoryResponse({ items: null }))
    expectParseError(() => assertRetrieveMemoryResponse({ items: [{ id: 1, text: 'bad' }] }))
    expectParseError(() => assertRetrieveMemoryResponse({ evidences: [1] }))
    expectParseError(() =>
      assertRetrieveMemoryResponse({ items: [], trace: { stages: [{ degraded: 'no' }] } }),
    )
  })
})
