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
