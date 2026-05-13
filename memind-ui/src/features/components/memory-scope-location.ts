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

import { parseMemoryScope } from '@/lib/memory-scope'

const MEMORY_SCOPE_SEARCH_PARAM = 'memoryId'
const MEMORY_SCOPE_STORAGE_KEY = 'memind-ui:memory-scope'

export function readMemoryScopeFromLocation() {
  if (typeof window === 'undefined') return ''

  const params = new URLSearchParams(window.location.search)
  return (
    params.get(MEMORY_SCOPE_SEARCH_PARAM) ??
    window.localStorage.getItem(MEMORY_SCOPE_STORAGE_KEY) ??
    ''
  )
}

export function writeMemoryScopeToLocation(value: string) {
  if (typeof window === 'undefined') return

  const scope = parseMemoryScope(value)
  const url = new URL(window.location.href)
  url.searchParams.delete('page')

  if (scope.memoryId) {
    url.searchParams.set(MEMORY_SCOPE_SEARCH_PARAM, scope.memoryId)
    window.localStorage.setItem(MEMORY_SCOPE_STORAGE_KEY, scope.memoryId)
  } else {
    url.searchParams.delete(MEMORY_SCOPE_SEARCH_PARAM)
    window.localStorage.removeItem(MEMORY_SCOPE_STORAGE_KEY)
  }

  window.history.replaceState(window.history.state, '', url)
  window.dispatchEvent(
    new PopStateEvent('popstate', { state: window.history.state })
  )
}
