import { useQuery } from '@tanstack/react-query'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { getGraphEntity } from '@/features/api/item-graph'
import { PageError, PageLoading } from '@/features/components/data-state'
import { JsonViewer } from '@/features/components/json-viewer'
import type { GraphEntityDetailView } from '@/features/types'
import { formatDateTime } from '@/lib/format'

export function EntityDetailDrawer({
  entityId,
  onOpenChange,
}: {
  entityId: number | null
  onOpenChange: (open: boolean) => void
}) {
  const query = useQuery({
    queryKey: ['item-graph', 'entities', entityId, 'detail'],
    enabled: entityId !== null,
    queryFn: () => getGraphEntity(entityId as number),
  })

  return (
    <Dialog open={entityId !== null} onOpenChange={onOpenChange}>
      <DialogContent className='max-h-[85vh] overflow-auto sm:max-w-4xl'>
        <DialogHeader>
          <DialogTitle>Graph entity {entityId}</DialogTitle>
          <DialogDescription>{query.data?.entity.memoryId ?? ''}</DialogDescription>
        </DialogHeader>

        {query.isLoading ? <PageLoading /> : null}
        {query.isError ? (
          <PageError
            message='Unable to load graph entity detail.'
            onRetry={query.refetch}
          />
        ) : null}
        {query.data ? <EntityDetail detail={query.data} /> : null}
      </DialogContent>
    </Dialog>
  )
}

function EntityDetail({ detail }: { detail: GraphEntityDetailView }) {
  return (
    <div className='flex flex-col gap-5 text-sm'>
      <div className='grid gap-2 md:grid-cols-2'>
        <p>entityKey: {detail.entity.entityKey}</p>
        <p>displayName: {fieldValue(detail.entity.displayName)}</p>
        <p>entityType: {fieldValue(detail.entity.entityType)}</p>
        <p>mentionCount: {detail.mentionCount}</p>
        <p>topMentionedItemIds: {detail.topMentionedItemIds.join(', ')}</p>
        <p>entityOverlapItemLinkCount: {detail.entityOverlapItemLinkCount}</p>
        <p>createdAt: {formatDateTime(detail.entity.createdAt)}</p>
        <p>updatedAt: {formatDateTime(detail.entity.updatedAt)}</p>
      </div>

      <JsonViewer label='metadata' value={detail.entity.metadata ?? {}} />

      <section className='flex flex-col gap-2'>
        <h3 className='font-medium'>Aliases</h3>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>ID</TableHead>
              <TableHead>Normalized Alias</TableHead>
              <TableHead>Evidence Count</TableHead>
              <TableHead>Entity Type</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {detail.aliases.map((alias) => (
              <TableRow key={alias.id}>
                <TableCell>{alias.id}</TableCell>
                <TableCell>{alias.normalizedAlias}</TableCell>
                <TableCell>{fieldValue(alias.evidenceCount)}</TableCell>
                <TableCell>{fieldValue(alias.entityType)}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </section>

      <section className='flex flex-col gap-2'>
        <h3 className='font-medium'>Top cooccurrences</h3>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Left Entity Key</TableHead>
              <TableHead>Right Entity Key</TableHead>
              <TableHead>Count</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {detail.topCooccurrences.map((row) => (
              <TableRow key={row.id}>
                <TableCell>{row.leftEntityKey}</TableCell>
                <TableCell>{row.rightEntityKey}</TableCell>
                <TableCell>{fieldValue(row.cooccurrenceCount)}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </section>
    </div>
  )
}

function fieldValue(value: unknown) {
  if (value === null || value === undefined || value === '') return '-'
  return String(value)
}
