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
  url.searchParams.delete('pageNo')

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
