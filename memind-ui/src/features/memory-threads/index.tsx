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

import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Header } from '@/components/layout/header'
import { Main } from '@/components/layout/main'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import {
  listMemoryThreads,
  type MemoryThreadListParams,
  type MemoryThreadStatus,
} from '@/features/api/memory-threads'
import {
  EmptyState,
  PageError,
  TableLoading,
} from '@/features/components/data-state'
import { readMemoryScopeFromLocation } from '@/features/components/memory-scope-location'
import type { AdminMemoryThreadView } from '@/features/types'
import { formatDateTime } from '@/lib/format'
import { toUserAgentQuery } from '@/lib/memory-scope'
import { ThreadDetailDrawer } from './thread-detail-drawer'
import { ThreadStatusPanel } from './thread-status-panel'

const DEFAULT_PAGE = 1
const DEFAULT_PAGE_SIZE = 10
type StatusFilter = MemoryThreadStatus | 'all'

export function MemoryThreadsPage() {
  const [, refreshLocation] = useState(0)
  const [detailThreadKey, setDetailThreadKey] = useState<string | null>(null)
  const search = readMemoryThreadsSearch()
  const memoryScope = readMemoryScopeFromLocation()
  const status = normalizeStatus(search.status)
  const params = useMemo(
    () => buildMemoryThreadParams(search, memoryScope, status),
    [memoryScope, search, status]
  )
  const threadsQuery = useQuery({
    queryKey: ['memory-threads', 'list', params],
    queryFn: () => listMemoryThreads(params),
  })
  const rows = threadsQuery.data?.items ?? []

  const writeSearch = (patch: Record<string, string | number | undefined>) => {
    writeUrlSearch(patch)
    refreshLocation((current) => current + 1)
  }

  return (
    <>
      <Header>
        <h1 className='truncate text-lg font-semibold'>Memory Threads</h1>
      </Header>
      <Main>
        <div className='flex flex-col gap-6'>
          <div className='flex flex-col gap-1'>
            <h2 className='text-2xl font-semibold'>Memory Threads</h2>
            <p className='text-sm text-muted-foreground'>
              Inspect projected thread state, memberships, and maintenance
              status.
            </p>
          </div>

          <ThreadStatusPanel memoryScope={memoryScope} />

          <div className='flex flex-wrap items-center justify-between gap-3'>
            <div className='flex items-center gap-2'>
              <Label htmlFor='memory-thread-status'>Status</Label>
              <select
                id='memory-thread-status'
                aria-label='Memory thread status'
                value={status}
                className='h-9 rounded-md border bg-background px-3 text-sm'
                onChange={(event) =>
                  writeSearch({
                    page: undefined,
                    status:
                      event.target.value === 'all'
                        ? undefined
                        : event.target.value,
                  })
                }
              >
                <option value='all'>all</option>
                <option value='ACTIVE'>ACTIVE</option>
                <option value='DORMANT'>DORMANT</option>
                <option value='CLOSED'>CLOSED</option>
              </select>
            </div>
          </div>

          {threadsQuery.isLoading ? <TableLoading columns={9} /> : null}
          {threadsQuery.isError ? (
            <PageError
              message='Unable to load memory threads.'
              onRetry={threadsQuery.refetch}
            />
          ) : null}
          {threadsQuery.data && rows.length === 0 ? (
            <EmptyState title='No memory threads found.' />
          ) : null}
          {rows.length > 0 ? (
            <MemoryThreadsTable rows={rows} onView={setDetailThreadKey} />
          ) : null}
          {threadsQuery.data ? (
            <p className='text-sm text-muted-foreground'>
              Total rows: {threadsQuery.data.page.totalItems}
            </p>
          ) : null}
        </div>
      </Main>

      <ThreadDetailDrawer
        threadKey={detailThreadKey}
        memoryScope={memoryScope}
        onOpenChange={(open) => {
          if (!open) setDetailThreadKey(null)
        }}
      />
    </>
  )
}

function MemoryThreadsTable({
  rows,
  onView,
}: {
  rows: AdminMemoryThreadView[]
  onView: (threadKey: string) => void
}) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Memory ID</TableHead>
          <TableHead>Thread Key</TableHead>
          <TableHead>Label / Headline</TableHead>
          <TableHead>Thread Type</TableHead>
          <TableHead>Status</TableHead>
          <TableHead>Object State</TableHead>
          <TableHead>Members</TableHead>
          <TableHead>Events</TableHead>
          <TableHead>Last Event At</TableHead>
          <TableHead className='text-end'>Actions</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {rows.map((thread) => (
          <TableRow key={thread.threadKey}>
            <TableCell>{thread.memoryId}</TableCell>
            <TableCell>{thread.threadKey}</TableCell>
            <TableCell>{thread.displayLabel || thread.headline || '-'}</TableCell>
            <TableCell>{thread.threadType}</TableCell>
            <TableCell>
              <Badge>{thread.lifecycleStatus}</Badge>
            </TableCell>
            <TableCell>{fieldValue(thread.objectState)}</TableCell>
            <TableCell>{thread.memberCount}</TableCell>
            <TableCell>{thread.eventCount}</TableCell>
            <TableCell>{formatDateTime(thread.lastEventAt)}</TableCell>
            <TableCell className='text-end'>
              <Button
                type='button'
                variant='outline'
                size='sm'
                aria-label={`View thread ${thread.threadKey}`}
                onClick={() => onView(thread.threadKey)}
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

function buildMemoryThreadParams(
  search: MemoryThreadsSearch,
  memoryScope: string,
  status: StatusFilter
): MemoryThreadListParams {
  return {
    page: search.page,
    pageSize: search.pageSize,
    ...toUserAgentQuery(memoryScope),
    ...(status === 'all' ? {} : { status }),
  }
}

type MemoryThreadsSearch = {
  page: number
  pageSize: number
  status?: string
}

function readMemoryThreadsSearch(): MemoryThreadsSearch {
  const params = readSearchParams()
  return {
    page: readNumberParam(params, 'page', DEFAULT_PAGE),
    pageSize: readNumberParam(params, 'pageSize', DEFAULT_PAGE_SIZE),
    status: readStringParam(params, 'status'),
  }
}

function normalizeStatus(status: string | undefined): StatusFilter {
  if (status === 'ACTIVE' || status === 'DORMANT' || status === 'CLOSED') {
    return status
  }
  return 'all'
}

function readSearchParams() {
  if (typeof window === 'undefined') return new URLSearchParams()
  return new URLSearchParams(window.location.search)
}

function readStringParam(params: URLSearchParams, key: string) {
  const value = params.get(key)?.trim()
  return value ? value : undefined
}

function readNumberParam(
  params: URLSearchParams,
  key: string,
  fallback: number
) {
  const value = params.get(key)
  if (!value) return fallback
  const parsed = Number(value)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback
}

function writeUrlSearch(patch: Record<string, string | number | undefined>) {
  if (typeof window === 'undefined') return
  const url = new URL(window.location.href)
  for (const [key, value] of Object.entries(patch)) {
    if (value === undefined || value === '') {
      url.searchParams.delete(key)
    } else {
      url.searchParams.set(key, String(value))
    }
  }
  window.history.replaceState(window.history.state, '', url)
}

function fieldValue(value: unknown) {
  if (value === null || value === undefined || value === '') return '-'
  return String(value)
}
