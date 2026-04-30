import { useState } from 'react'
import { ChevronDown, ChevronRight } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { compactJson, truncateText } from '@/lib/format'

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
  const json = expanded ? prettyJson(value) : truncateText(compactJson(value), 160)

  return (
    <div className='flex flex-col gap-2'>
      <Button
        type='button'
        variant='ghost'
        size='sm'
        className='w-fit'
        aria-expanded={expanded}
        onClick={() => setExpanded((current) => !current)}
      >
        {expanded ? <ChevronDown /> : <ChevronRight />}
        {label}
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
