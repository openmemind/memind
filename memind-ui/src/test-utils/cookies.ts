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

import { removeCookie } from '@/lib/cookies'

/**
 * Remove cookies visible on `document.cookie` for test isolation.
 *
 * - No `filter`: remove every cookie.
 * - `string`: remove only names that **start with** that string (prefix).
 * - `RegExp`: remove only names where `filter.test(name)` is true.
 */
export function clearCookies(filter?: string | RegExp): void {
  if (typeof document === 'undefined') return

  for (const part of document.cookie.split(';')) {
    const name = part.split('=')[0]?.trim()
    if (!name) continue

    const shouldRemove =
      filter === undefined
        ? true
        : typeof filter === 'string'
          ? name.startsWith(filter)
          : filter.test(name)

    if (shouldRemove) {
      removeCookie(name)
    }
  }
}
