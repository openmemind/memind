import { useEffect, useId, useState } from 'react'
import { SlidersHorizontal } from 'lucide-react'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { parseMemoryScope } from '@/lib/memory-scope'

export const MEMORY_SCOPE_SEARCH_PARAM = 'memoryId'
export const MEMORY_SCOPE_STORAGE_KEY = 'memind-ui:memory-scope'

export function readMemoryScopeFromLocation() {
  if (typeof window === 'undefined') return ''

  const params = new URLSearchParams(window.location.search)
  return (
    params.get(MEMORY_SCOPE_SEARCH_PARAM) ??
    window.localStorage.getItem(MEMORY_SCOPE_STORAGE_KEY) ??
    ''
  )
}

export function MemoryScopePicker() {
  const id = useId()
  const [value, setValue] = useState('')

  useEffect(() => {
    setValue(readMemoryScopeFromLocation())
  }, [])

  const scope = parseMemoryScope(value)
  const title = scope.memoryId
    ? `Scope: ${scope.memoryId}`
    : 'Scope: all local memory data'

  return (
    <div className='flex min-w-0 items-center gap-2' title={title}>
      <SlidersHorizontal aria-hidden='true' data-icon='inline-start' />
      <Label htmlFor={id} className='sr-only'>
        Memory scope
      </Label>
      <Input
        id={id}
        value={value}
        placeholder='userId:agentId'
        className='h-8 w-40 sm:w-56'
        onChange={(event) => {
          const next = event.target.value
          setValue(next)
          writeMemoryScope(next)
        }}
      />
    </div>
  )
}

function writeMemoryScope(value: string) {
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
  window.dispatchEvent(new PopStateEvent('popstate', { state: window.history.state }))
}
