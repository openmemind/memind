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
