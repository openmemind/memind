import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import type { InsightBufferGroupView } from '@/features/types'
import { formatNumber } from '@/lib/format'

export function InsightGroupsTable({
  rows,
}: {
  rows: InsightBufferGroupView[]
}) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Memory ID</TableHead>
          <TableHead>Insight Type Name</TableHead>
          <TableHead>Group Name</TableHead>
          <TableHead>Total</TableHead>
          <TableHead>Unbuilt</TableHead>
          <TableHead>Built</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {rows.map((row) => (
          <TableRow key={groupKey(row)}>
            <TableCell>{row.memoryId}</TableCell>
            <TableCell>{row.insightTypeName}</TableCell>
            <TableCell>{fieldValue(row.groupName)}</TableCell>
            <TableCell>{formatNumber(row.total)}</TableCell>
            <TableCell>{formatNumber(row.unbuilt)}</TableCell>
            <TableCell>{formatNumber(row.built)}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}

function groupKey(row: InsightBufferGroupView) {
  return `${row.memoryId}:${row.insightTypeName}:${row.groupName ?? ''}`
}

function fieldValue(value: unknown) {
  if (value === null || value === undefined || value === '') return '-'
  return String(value)
}
