import { Badge } from '@/components/ui/badge'
import { Checkbox } from '@/components/ui/checkbox'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import type { InsightBufferView } from '@/features/types'
import { formatDateTime } from '@/lib/format'

export function InsightBuffersTable({
  rows,
  selectedIds,
  onToggleSelected,
}: {
  rows: InsightBufferView[]
  selectedIds: Set<number>
  onToggleSelected: (id: number, checked: boolean) => void
}) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead className='w-10'>Select</TableHead>
          <TableHead>ID</TableHead>
          <TableHead>Memory ID</TableHead>
          <TableHead>Insight Type Name</TableHead>
          <TableHead>Item ID</TableHead>
          <TableHead>Group Name</TableHead>
          <TableHead>Built</TableHead>
          <TableHead>Created At</TableHead>
          <TableHead>Updated At</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {rows.map((row) => (
          <TableRow key={row.id}>
            <TableCell>
              <Checkbox
                aria-label={`Select insight buffer ${row.id}`}
                checked={selectedIds.has(row.id)}
                onCheckedChange={(checked) =>
                  onToggleSelected(row.id, checked === true)
                }
              />
            </TableCell>
            <TableCell>{row.id}</TableCell>
            <TableCell>{row.memoryId}</TableCell>
            <TableCell>{row.insightTypeName}</TableCell>
            <TableCell>{fieldValue(row.itemId)}</TableCell>
            <TableCell>{fieldValue(row.groupName)}</TableCell>
            <TableCell>
              <Badge variant={row.built ? 'default' : 'secondary'}>
                {row.built ? 'built' : 'unbuilt'}
              </Badge>
            </TableCell>
            <TableCell>{formatDateTime(row.createdAt)}</TableCell>
            <TableCell>{formatDateTime(row.updatedAt)}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}

function fieldValue(value: unknown) {
  if (value === null || value === undefined || value === '') return '-'
  return String(value)
}
