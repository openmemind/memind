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
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { JsonViewer } from '@/features/components/json-viewer'
import type { RetrievalTraceStageView, RetrievalTraceView } from '@/features/types'

export function RetrieveTrace({ trace }: { trace: RetrievalTraceView | null }) {
  const [open, setOpen] = useState(false)

  if (!trace) return null

  return (
    <section className='rounded-md border p-4'>
      <Button
        type='button'
        variant='ghost'
        className='w-fit'
        aria-expanded={open}
        onClick={() => setOpen((current) => !current)}
      >
        {open ? <ChevronDown /> : <ChevronRight />}
        Trace details
      </Button>

      {open ? (
        <div className='mt-4 flex flex-col gap-4'>
          <div className='grid gap-3 md:grid-cols-2'>
            <TraceSummary
              title='Merge'
              rows={[
                ['inputCount', trace.merge?.inputCount],
                ['outputCount', trace.merge?.outputCount],
                ['deduplicatedCount', trace.merge?.deduplicatedCount],
                ['sourceCount', trace.merge?.sourceCount],
                ['status', trace.merge?.status],
              ]}
            />
            <TraceSummary
              title='Final results'
              rows={[
                ['strategy', trace.finalResults?.strategy],
                ['status', trace.finalResults?.status],
                ['itemCount', trace.finalResults?.itemCount],
                ['insightCount', trace.finalResults?.insightCount],
                ['rawDataCount', trace.finalResults?.rawDataCount],
                ['evidenceCount', trace.finalResults?.evidenceCount],
              ]}
            />
          </div>

          <div className='flex flex-col gap-3'>
            {trace.stages.map((stage, index) => (
              <TraceStage key={`${stage.stage}:${index}`} stage={stage} />
            ))}
          </div>
        </div>
      ) : null}
    </section>
  )
}

function TraceStage({ stage }: { stage: RetrievalTraceStageView }) {
  return (
    <article className='rounded-md border p-3 text-sm'>
      <div className='flex flex-wrap items-center gap-2'>
        <h3 className='font-medium'>{stage.stage}</h3>
        {stage.degraded ? <Badge variant='secondary'>degraded</Badge> : null}
        {stage.skipped ? <Badge variant='secondary'>skipped</Badge> : null}
      </div>
      <div className='mt-2 grid gap-1 md:grid-cols-2'>
        <p>method: {display(stage.method)}</p>
        <p>status: {display(stage.status)}</p>
        <p>duration: {displayDuration(stage.durationMillis)}</p>
        <p>
          inputCount: {display(stage.inputCount)} -&gt; resultCount:{' '}
          {display(stage.resultCount)}
        </p>
        <p>candidateCount: {display(stage.candidateCount)}</p>
        <p>tier: {display(stage.tier)}</p>
      </div>
      <div className='mt-3 grid gap-3 md:grid-cols-2'>
        <JsonViewer label='attributes' value={stage.attributes ?? {}} />
        <JsonViewer label='candidates' value={stage.candidates} />
      </div>
    </article>
  )
}

function TraceSummary({
  title,
  rows,
}: {
  title: string
  rows: Array<[string, unknown]>
}) {
  return (
    <section className='rounded-md border p-3 text-sm'>
      <h3 className='font-medium'>{title}</h3>
      <dl className='mt-2 grid gap-1'>
        {rows.map(([label, value]) => (
          <div key={label} className='flex gap-2'>
            <dt className='text-muted-foreground'>{label}:</dt>
            <dd>{display(value)}</dd>
          </div>
        ))}
      </dl>
    </section>
  )
}

function display(value: unknown) {
  if (value === null || value === undefined || value === '') return '-'
  return String(value)
}

function displayDuration(value: number | null) {
  if (value === null || value === undefined) return '-'
  return `${value}ms`
}
