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
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Header } from '@/components/layout/header'
import { Main } from '@/components/layout/main'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
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
  deleteItems,
  getItem,
  listItemMemoryThreads,
  listItems,
  type ItemListParams,
} from '@/features/api/items'
import { ConfirmResultDialog } from '@/features/components/confirm-result-dialog'
import {
  EmptyState,
  PageError,
  PageLoading,
  ScopeRequiredState,
  TableLoading,
} from '@/features/components/data-state'
import { JsonViewer } from '@/features/components/json-viewer'
import { readMemoryScopeFromLocation } from '@/features/components/memory-scope-location'
import type {
  AdminItemMemoryThreadView,
  AdminItemView,
} from '@/features/types'
import { compactJson, formatDateTime, truncateText } from '@/lib/format'
import { parseMemoryScope, toUserAgentQuery } from '@/lib/memory-scope'

const DEFAULT_PAGE = 1
const DEFAULT_PAGE_SIZE = 10

export function ItemsPage() {
  const queryClient = useQueryClient()
  const [selectedIds, setSelectedIds] = useState<Set<number>>(() => new Set())
  const [deleteOpen, setDeleteOpen] = useState(false)
  const [detailItemId, setDetailItemId] = useState<number | null>(null)

  const search = readItemsSearch()
  const memoryScope = readMemoryScopeFromLocation()
  const params = useMemo(
    () => buildItemListParams(search, memoryScope),
    [search, memoryScope]
  )
  const itemsQuery = useQuery({
    queryKey: ['items', params],
    queryFn: () => listItems(params),
  })
  const deleteMutation = useMutation({
    mutationFn: (itemIds: number[]) => deleteItems(itemIds),
    onSuccess: async () => {
      setSelectedIds(new Set())
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['items'] }),
        queryClient.invalidateQueries({ queryKey: ['dashboard'] }),
      ])
    },
  })

  const rows = itemsQuery.data?.items ?? []
  const selectedItemIds = [...selectedIds]

  return (
    <>
      <Header>
        <h1 className='truncate text-lg font-semibold'>Memory Items</h1>
      </Header>
      <Main>
        <div className='flex flex-col gap-4'>
          <div className='flex flex-wrap items-center justify-between gap-3'>
            <div className='flex flex-col gap-1'>
              <h2 className='text-2xl font-semibold'>Memory Items</h2>
              <p className='text-sm text-muted-foreground'>
                Inspect item content, metadata, source raw data, and related
                memory threads.
              </p>
            </div>
            <Button
              type='button'
              variant='destructive'
              disabled={selectedIds.size === 0}
              onClick={() => {
                deleteMutation.reset()
                setDeleteOpen(true)
              }}
            >
              Delete selected
            </Button>
          </div>

          {itemsQuery.isLoading ? <TableLoading columns={10} /> : null}
          {itemsQuery.isError ? (
            <PageError
              message='Unable to load memory items.'
              onRetry={itemsQuery.refetch}
            />
          ) : null}
          {itemsQuery.data && rows.length === 0 ? (
            <EmptyState title='No memory items found.' />
          ) : null}
          {rows.length > 0 ? (
            <ItemsTable
              rows={rows}
              selectedIds={selectedIds}
              onToggleSelected={(itemId, checked) => {
                setSelectedIds((current) => {
                  const next = new Set(current)
                  if (checked) {
                    next.add(itemId)
                  } else {
                    next.delete(itemId)
                  }
                  return next
                })
              }}
              onView={setDetailItemId}
            />
          ) : null}

          {itemsQuery.data ? (
            <p className='text-sm text-muted-foreground'>
              Total rows: {itemsQuery.data.page.totalItems}
            </p>
          ) : null}
        </div>
      </Main>

      <ConfirmResultDialog
        open={deleteOpen}
        onOpenChange={setDeleteOpen}
        title='Delete selected memory items'
        selectedCount={selectedItemIds.length}
        description='Only the selected row ids will be sent to the server.'
        isPending={deleteMutation.isPending}
        result={deleteMutation.data ? { ...deleteMutation.data } : null}
        onConfirm={() => deleteMutation.mutate(selectedItemIds)}
      />

      <ItemDetailDialog
        itemId={detailItemId}
        memoryScope={memoryScope}
        onOpenChange={(open) => {
          if (!open) setDetailItemId(null)
        }}
      />
    </>
  )
}

function ItemsTable({
  rows,
  selectedIds,
  onToggleSelected,
  onView,
}: {
  rows: AdminItemView[]
  selectedIds: Set<number>
  onToggleSelected: (itemId: number, checked: boolean) => void
  onView: (itemId: number) => void
}) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead className='w-10'>Select</TableHead>
          <TableHead>Item ID</TableHead>
          <TableHead>Memory ID</TableHead>
          <TableHead>Content</TableHead>
          <TableHead>Type</TableHead>
          <TableHead>Category</TableHead>
          <TableHead>Scope</TableHead>
          <TableHead>Raw Data ID</TableHead>
          <TableHead>Source Client</TableHead>
          <TableHead>Observed At</TableHead>
          <TableHead>Created At</TableHead>
          <TableHead className='text-end'>Actions</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {rows.map((item) => (
          <TableRow key={item.itemId}>
            <TableCell>
              <Checkbox
                aria-label={`Select item ${item.itemId}`}
                checked={selectedIds.has(item.itemId)}
                onCheckedChange={(checked) =>
                  onToggleSelected(item.itemId, checked === true)
                }
              />
            </TableCell>
            <TableCell>{item.itemId}</TableCell>
            <TableCell>{item.memoryId}</TableCell>
            <TableCell className='max-w-96 whitespace-normal'>
              <span className='sr-only'>Content preview: </span>
              {truncateText(item.content, 12)}
            </TableCell>
            <TableCell>{item.type ? <Badge>{item.type}</Badge> : '-'}</TableCell>
            <TableCell>{fieldValue(item.category)}</TableCell>
            <TableCell>{fieldValue(item.scope)}</TableCell>
            <TableCell>{fieldValue(item.rawDataId)}</TableCell>
            <TableCell>{fieldValue(item.sourceClient)}</TableCell>
            <TableCell>{formatDateTime(item.observedAt)}</TableCell>
            <TableCell>{formatDateTime(item.createdAt)}</TableCell>
            <TableCell className='text-end'>
              <Button
                type='button'
                variant='outline'
                size='sm'
                aria-label={`View item ${item.itemId}`}
                onClick={() => onView(item.itemId)}
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

function ItemDetailDialog({
  itemId,
  memoryScope,
  onOpenChange,
}: {
  itemId: number | null
  memoryScope: string
  onOpenChange: (open: boolean) => void
}) {
  const query = useQuery({
    queryKey: ['items', itemId, 'detail'],
    enabled: itemId !== null,
    queryFn: () => getItem(itemId as number),
  })
  const item = query.data

  return (
    <Dialog open={itemId !== null} onOpenChange={onOpenChange}>
      <DialogContent className='max-h-[85vh] overflow-auto sm:max-w-3xl'>
        <DialogHeader>
          <DialogTitle>Memory item {itemId}</DialogTitle>
          <DialogDescription>{item?.memoryId}</DialogDescription>
        </DialogHeader>

        {query.isLoading ? <PageLoading /> : null}
        {query.isError ? (
          <PageError
            message='Unable to load memory item detail.'
            onRetry={query.refetch}
          />
        ) : null}
        {item ? (
          <div className='flex flex-col gap-5 text-sm'>
            <section className='flex flex-col gap-2'>
              <h3 className='font-medium'>Content</h3>
              <p className='whitespace-pre-wrap rounded-md bg-muted p-3'>
                {item.content}
              </p>
            </section>

            <div className='grid gap-2 md:grid-cols-2'>
              <p>type: {fieldValue(item.type)}</p>
              <p>rawDataType: {fieldValue(item.rawDataType)}</p>
              <p>vectorId: {fieldValue(item.vectorId)}</p>
              <p>contentHash: {fieldValue(item.contentHash)}</p>
              <p>rawDataId: {fieldValue(item.rawDataId)}</p>
              <p>sourceClient: {fieldValue(item.sourceClient)}</p>
              <p>occurredAt: {formatDateTime(item.occurredAt)}</p>
              <p>observedAt: {formatDateTime(item.observedAt)}</p>
              <p>createdAt: {formatDateTime(item.createdAt)}</p>
              <p>updatedAt: {formatDateTime(item.updatedAt)}</p>
            </div>

            <JsonViewer
              label='metadata'
              value={item.metadata ?? {}}
              defaultExpanded
            />

            <AssociatedThreads itemId={item.itemId} memoryScope={memoryScope} />
          </div>
        ) : null}
      </DialogContent>
    </Dialog>
  )
}

function AssociatedThreads({
  itemId,
  memoryScope,
}: {
  itemId: number
  memoryScope: string
}) {
  const scope = parseMemoryScope(memoryScope)
  const threadsQuery = useQuery({
    queryKey: [
      'items',
      itemId,
      'memory-threads',
      scope.userId,
      scope.agentId,
      scope.hasAgentId,
    ],
    enabled: scope.hasUserId,
    queryFn: () =>
      listItemMemoryThreads(itemId, {
        userId: scope.userId,
        ...(scope.hasAgentId ? { agentId: scope.agentId } : {}),
      }),
  })

  if (!scope.hasUserId) {
    return (
      <ScopeRequiredState message='Set memory scope with userId to load associated threads.' />
    )
  }

  if (threadsQuery.isLoading) {
    return <PageLoading />
  }

  if (threadsQuery.isError) {
    return (
      <PageError
        message='Unable to load associated memory threads.'
        onRetry={threadsQuery.refetch}
      />
    )
  }

  const threads = threadsQuery.data ?? []
  if (threads.length === 0) {
    return <EmptyState title='No associated memory threads found.' />
  }

  return (
    <section className='flex flex-col gap-2'>
      <h3 className='font-medium'>Associated memory threads</h3>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Thread Key</TableHead>
            <TableHead>Role</TableHead>
            <TableHead>Primary</TableHead>
            <TableHead>Relevance</TableHead>
            <TableHead>Created At</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {threads.map((thread) => (
            <AssociatedThreadRow key={threadRowKey(thread)} thread={thread} />
          ))}
        </TableBody>
      </Table>
    </section>
  )
}

function AssociatedThreadRow({
  thread,
}: {
  thread: AdminItemMemoryThreadView
}) {
  return (
    <TableRow>
      <TableCell>{thread.threadKey}</TableCell>
      <TableCell>{fieldValue(thread.role)}</TableCell>
      <TableCell>{thread.primary ? 'yes' : 'no'}</TableCell>
      <TableCell>{fieldValue(thread.relevanceWeight)}</TableCell>
      <TableCell>{formatDateTime(thread.createdAt)}</TableCell>
    </TableRow>
  )
}

function buildItemListParams(
  search: ItemSearch,
  memoryScope: string
): ItemListParams {
  return {
    page: search.page,
    pageSize: search.pageSize,
    ...toUserAgentQuery(memoryScope),
    scope: search.scope,
    category: search.category,
    type: search.type,
    rawDataId: search.rawDataId,
  }
}

type ItemSearch = {
  page: number
  pageSize: number
  scope?: string
  category?: string
  type?: string
  rawDataId?: string
}

function readItemsSearch(): ItemSearch {
  const params = readSearchParams()
  return {
    page: readNumberParam(params, 'page', DEFAULT_PAGE),
    pageSize: readNumberParam(params, 'pageSize', DEFAULT_PAGE_SIZE),
    scope: readStringParam(params, 'scope'),
    category: readStringParam(params, 'category'),
    type: readStringParam(params, 'type'),
    rawDataId: readStringParam(params, 'rawDataId'),
  }
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

function threadRowKey(thread: AdminItemMemoryThreadView) {
  return `${thread.threadKey}:${thread.itemId}:${thread.role}`
}

function fieldValue(value: unknown) {
  if (value === null || value === undefined || value === '') return '-'
  if (Array.isArray(value)) return value.length > 0 ? value.join(', ') : '-'
  if (typeof value === 'object') return compactJson(value)
  return String(value)
}
