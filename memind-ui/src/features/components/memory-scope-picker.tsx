import { useId, useState } from 'react'
import { SlidersHorizontal } from 'lucide-react'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { parseMemoryScope } from '@/lib/memory-scope'
import {
  readMemoryScopeFromLocation,
  writeMemoryScopeToLocation,
} from './memory-scope-location'

export function MemoryScopePicker() {
  const id = useId()
  const [value, setValue] = useState(() => readMemoryScopeFromLocation())

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
          writeMemoryScopeToLocation(next)
        }}
      />
    </div>
  )
}
