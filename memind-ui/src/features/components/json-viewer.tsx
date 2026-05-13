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

import { useState } from 'react'
import { ChevronDown, ChevronRight } from 'lucide-react'
import { compactJson, truncateText } from '@/lib/format'
import { Button } from '@/components/ui/button'

type JsonViewerProps = {
  value: unknown
  label?: string
  defaultExpanded?: boolean
}

export function JsonViewer({
  value,
  label = 'JSON',
  defaultExpanded = false,
}: JsonViewerProps) {
  const [expanded, setExpanded] = useState(defaultExpanded)
  const json = expanded
    ? prettyJson(value)
    : truncateText(compactJson(value), 160)

  return (
    <div className='flex min-w-0 flex-col gap-2'>
      <Button
        type='button'
        variant='ghost'
        size='sm'
        className='max-w-full justify-start'
        aria-expanded={expanded}
        onClick={() => setExpanded((current) => !current)}
      >
        {expanded ? <ChevronDown /> : <ChevronRight />}
        <span className='min-w-0 truncate'>{label}</span>
      </Button>
      <pre className='max-h-96 overflow-auto rounded-md bg-muted p-3 text-xs'>
        {json}
      </pre>
    </div>
  )
}

function prettyJson(value: unknown) {
  try {
    return JSON.stringify(value, null, 2)
  } catch {
    return String(value)
  }
}
