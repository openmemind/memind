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
import { serializeBody } from '../src/core/serialize.js'

describe('serializeBody', () => {
  it('drops undefined object properties recursively', () => {
    expect(
      serializeBody({
        keep: 'yes',
        drop: undefined,
        nested: { keep: 1, drop: undefined },
      }),
    ).toBe('{"keep":"yes","nested":{"keep":1}}')
  })

  it('converts undefined array entries to null like JSON arrays do', () => {
    expect(serializeBody({ values: [1, undefined, 3] })).toBe('{"values":[1,null,3]}')
  })

  it('rejects functions, symbols, bigint, and non-finite numbers', () => {
    expect(() => serializeBody({ bad: () => undefined })).toThrow(/function/)
    expect(() => serializeBody({ bad: Symbol('x') })).toThrow(/symbol/)
    expect(() => serializeBody({ bad: BigInt(1) })).toThrow(/bigint/)
    expect(() => serializeBody({ bad: Number.NaN })).toThrow(/finite/)
    expect(() => serializeBody({ bad: Infinity })).toThrow(/finite/)
  })

  it('rejects undefined at the top level because it cannot produce a JSON body', () => {
    expect(() => serializeBody(undefined)).toThrow(/top-level/)
  })

  it('rejects cyclic objects with a clear error', () => {
    const value: Record<string, unknown> = {}
    value.self = value
    expect(() => serializeBody(value)).toThrow(/cyclic/)
  })
})
