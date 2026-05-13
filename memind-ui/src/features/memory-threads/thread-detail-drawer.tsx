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
import {
  getMemoryThread,
  listMemoryThreadItems,
} from '@/features/api/memory-threads'
import {
  EmptyState,
  PageError,
  PageLoading,
  ScopeRequiredState,
} from '@/features/components/data-state'
import { JsonViewer } from '@/features/components/json-viewer'
import type {
  AdminMemoryThreadItemView,
  AdminMemoryThreadView,
} from '@/features/types'
import { formatDateTime } from '@/lib/format'
import { parseMemoryScope } from '@/lib/memory-scope'

export function ThreadDetailDrawer({
  threadKey,
  memoryScope,
  onOpenChange,
}: {
  threadKey: string | null
  memoryScope: string
  onOpenChange: (open: boolean) => void
}) {
  const scope = parseMemoryScope(memoryScope)
  const threadQuery = useQuery({
    queryKey: [
      'memory-threads',
      threadKey,
      'detail',
      scope.userId,
      scope.agentId,
      scope.hasAgentId,
    ],
    enabled: threadKey !== null && scope.hasUserId,
    queryFn: () =>
      getMemoryThread(threadKey as string, {
        userId: scope.userId,
        ...(scope.hasAgentId ? { agentId: scope.agentId } : {}),
      }),
  })
  const membershipsQuery = useQuery({
    queryKey: [
      'memory-threads',
      threadKey,
      'items',
      scope.userId,
      scope.agentId,
      scope.hasAgentId,
    ],
    enabled: threadKey !== null && scope.hasUserId,
    queryFn: () =>
      listMemoryThreadItems(threadKey as string, {
        userId: scope.userId,
        ...(scope.hasAgentId ? { agentId: scope.agentId } : {}),
      }),
  })

  return (
    <Dialog open={threadKey !== null} onOpenChange={onOpenChange}>
      <DialogContent className='max-h-[85vh] overflow-auto sm:max-w-3xl'>
        <DialogHeader>
          <DialogTitle>Memory thread {threadKey}</DialogTitle>
          <DialogDescription>
            {threadQuery.data?.memoryId ?? memoryScope}
          </DialogDescription>
        </DialogHeader>

        {!scope.hasUserId ? (
          <ScopeRequiredState message='Set memory scope with userId to load thread details.' />
        ) : null}
        {threadQuery.isLoading ? <PageLoading /> : null}
        {threadQuery.isError ? (
          <PageError
            message='Unable to load memory thread detail.'
            onRetry={threadQuery.refetch}
          />
        ) : null}
        {threadQuery.data ? (
          <ThreadDetail thread={threadQuery.data} memberships={membershipsQuery.data ?? []} />
        ) : null}
        {membershipsQuery.isError ? (
          <PageError
            message='Unable to load thread item memberships.'
            onRetry={membershipsQuery.refetch}
          />
        ) : null}
      </DialogContent>
    </Dialog>
  )
}

function ThreadDetail({
  thread,
  memberships,
}: {
  thread: AdminMemoryThreadView
  memberships: AdminMemoryThreadItemView[]
}) {
  return (
    <div className='flex flex-col gap-5 text-sm'>
      <div className='grid gap-2 md:grid-cols-2'>
        <p>headline: {fieldValue(thread.headline)}</p>
        <p>threadType: {thread.threadType}</p>
        <p>anchorKind: {fieldValue(thread.anchorKind)}</p>
        <p>anchorKey: {fieldValue(thread.anchorKey)}</p>
        <p>objectState: {fieldValue(thread.objectState)}</p>
        <p>snapshotVersion: {thread.snapshotVersion}</p>
        <p>openedAt: {formatDateTime(thread.openedAt)}</p>
        <p>closedAt: {formatDateTime(thread.closedAt)}</p>
        <p>lastEventAt: {formatDateTime(thread.lastEventAt)}</p>
        <p>
          lastMeaningfulUpdateAt:{' '}
          {formatDateTime(thread.lastMeaningfulUpdateAt)}
        </p>
        <p>createdAt: {formatDateTime(thread.createdAt)}</p>
        <p>updatedAt: {formatDateTime(thread.updatedAt)}</p>
      </div>

      <JsonViewer
        label='snapshotJson'
        value={thread.snapshotJson ?? {}}
        defaultExpanded
      />

      <section className='flex flex-col gap-2'>
        <h3 className='font-medium'>Thread item memberships</h3>
        {memberships.length === 0 ? (
          <EmptyState title='No thread item memberships found.' />
        ) : (
          <MembershipTable memberships={memberships} />
        )}
      </section>
    </div>
  )
}

function MembershipTable({
  memberships,
}: {
  memberships: AdminMemoryThreadItemView[]
}) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Item ID</TableHead>
          <TableHead>Role</TableHead>
          <TableHead>Primary</TableHead>
          <TableHead>Relevance Weight</TableHead>
          <TableHead>Created At</TableHead>
          <TableHead>Updated At</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {memberships.map((membership) => (
          <TableRow key={`${membership.threadKey}:${membership.itemId}`}>
            <TableCell>{membership.itemId}</TableCell>
            <TableCell>{membership.role}</TableCell>
            <TableCell>primary: {membership.primary ? 'yes' : 'no'}</TableCell>
            <TableCell>
              relevanceWeight: {membership.relevanceWeight}
            </TableCell>
            <TableCell>{formatDateTime(membership.createdAt)}</TableCell>
            <TableCell>{formatDateTime(membership.updatedAt)}</TableCell>
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
