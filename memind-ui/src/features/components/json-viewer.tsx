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
