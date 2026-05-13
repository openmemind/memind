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

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import type { ConversationBufferView } from '@/features/types'
import { formatDateTime, truncateText } from '@/lib/format'

export function ConversationBuffersTable({
  rows,
  selectedIds,
  onToggleSelected,
  onView,
}: {
  rows: ConversationBufferView[]
  selectedIds: Set<number>
  onToggleSelected: (id: number, checked: boolean) => void
  onView: (id: number) => void
}) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead className='w-10'>Select</TableHead>
          <TableHead>ID</TableHead>
          <TableHead>Session ID</TableHead>
          <TableHead>Memory ID</TableHead>
          <TableHead>Role</TableHead>
          <TableHead>Content</TableHead>
          <TableHead>User Name</TableHead>
          <TableHead>Source Client</TableHead>
          <TableHead>Extracted</TableHead>
          <TableHead>Timestamp</TableHead>
          <TableHead>Created At</TableHead>
          <TableHead>Updated At</TableHead>
          <TableHead className='text-end'>Actions</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {rows.map((row) => (
          <TableRow key={row.id}>
            <TableCell>
              <Checkbox
                aria-label={`Select conversation ${row.id}`}
                checked={selectedIds.has(row.id)}
                onCheckedChange={(checked) =>
                  onToggleSelected(row.id, checked === true)
                }
              />
            </TableCell>
            <TableCell>{row.id}</TableCell>
            <TableCell>{fieldValue(row.sessionId)}</TableCell>
            <TableCell>{row.memoryId}</TableCell>
            <TableCell>{fieldValue(row.role)}</TableCell>
            <TableCell className='max-w-96 whitespace-normal'>
              {truncateText(row.content, 120)}
            </TableCell>
            <TableCell>{fieldValue(row.userName)}</TableCell>
            <TableCell>{fieldValue(row.sourceClient)}</TableCell>
            <TableCell>
              <Badge variant={row.extracted ? 'default' : 'secondary'}>
                {row.extracted ? 'extracted' : 'pending'}
              </Badge>
            </TableCell>
            <TableCell>{formatDateTime(row.timestamp)}</TableCell>
            <TableCell>{formatDateTime(row.createdAt)}</TableCell>
            <TableCell>{formatDateTime(row.updatedAt)}</TableCell>
            <TableCell className='text-end'>
              <Button
                type='button'
                variant='outline'
                size='sm'
                aria-label={`View conversation ${row.id}`}
                onClick={() => onView(row.id)}
              >
                View
              </Button>
            </TableCell>
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
