import { useQuery } from '@tanstack/react-query'
import { getItemGraphSummary } from '@/features/api/item-graph'
import {
  EmptyState,
  PageError,
  PageLoading,
} from '@/features/components/data-state'
import type { ItemGraphSummaryView, NamedCount } from '@/features/types'

export function SummaryTab({ memoryId }: { memoryId: string }) {
  const query = useQuery({
    queryKey: ['item-graph', 'summary', memoryId],
    queryFn: () =>
      getItemGraphSummary({ memoryId: memoryId || undefined }),
  })

  if (query.isLoading) return <PageLoading />
  if (query.isError) {
    return (
      <PageError
        message='Unable to load item graph summary.'
        onRetry={query.refetch}
      />
    )
  }
  if (!query.data) return <EmptyState title='No item graph summary found.' />

  return <SummaryContent summary={query.data} />
}

function SummaryContent({ summary }: { summary: ItemGraphSummaryView }) {
  return (
    <div className='flex flex-col gap-4'>
      <div className='grid gap-3 md:grid-cols-2 xl:grid-cols-5'>
        <Metric label='entityCount' value={summary.entityCount} />
        <Metric label='aliasCount' value={summary.aliasCount} />
        <Metric label='mentionCount' value={summary.mentionCount} />
        <Metric label='itemLinkCount' value={summary.itemLinkCount} />
        <Metric label='cooccurrenceCount' value={summary.cooccurrenceCount} />
      </div>
      <div className='grid gap-4 lg:grid-cols-3'>
        <Breakdown title='Graph batches' rows={summary.graphBatchCountByState} />
        <Breakdown title='Item link types' rows={summary.itemLinkCountByType} />
        <Breakdown title='Entity types' rows={summary.entityCountByType} />
      </div>
    </div>
  )
}

function Metric({ label, value }: { label: string; value: number }) {
  return (
    <div className='rounded-md border p-3 text-sm'>
      <p>{label}: {value}</p>
    </div>
  )
}

function Breakdown({ title, rows }: { title: string; rows: NamedCount[] }) {
  return (
    <section className='rounded-md border p-3 text-sm'>
      <h3 className='font-medium'>{title}</h3>
      {rows.length === 0 ? (
        <p className='mt-2 text-muted-foreground'>No data.</p>
      ) : (
        <ul className='mt-2 flex flex-col gap-1'>
          {rows.map((row) => (
            <li key={row.name}>
              {row.name}: {row.count}
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
