import { useMemo, useState, type ReactNode } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Header } from '@/components/layout/header'
import { Main } from '@/components/layout/main'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Label } from '@/components/ui/label'
import {
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
} from '@/components/ui/tabs'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import {
  deleteGraphAliases,
  deleteGraphCooccurrences,
  deleteGraphEntities,
  deleteGraphItemLinks,
  deleteGraphMentions,
  listGraphAliases,
  listGraphBatches,
  listGraphCooccurrences,
  listGraphEntities,
  listGraphItemLinks,
  listGraphMentions,
  type GraphBatchState,
} from '@/features/api/item-graph'
import { ConfirmResultDialog } from '@/features/components/confirm-result-dialog'
import {
  EmptyState,
  PageError,
  TableLoading,
} from '@/features/components/data-state'
import { readMemoryScopeFromLocation } from '@/features/components/memory-scope-location'
import type {
  BatchDeleteResult,
  GraphAliasView,
  GraphCooccurrenceView,
  GraphItemLinkView,
  GraphMentionView,
} from '@/features/types'
import { formatDateTime } from '@/lib/format'
import { EntityDetailDrawer } from './entity-detail-drawer'
import { SummaryTab } from './summary-tab'

const DEFAULT_PAGE = 1
const DEFAULT_PAGE_SIZE = 10

type GraphTab =
  | 'summary'
  | 'entities'
  | 'aliases'
  | 'mentions'
  | 'item-links'
  | 'cooccurrences'
  | 'batches'
type BatchStateFilter = GraphBatchState | 'all'

export function ItemGraphPage() {
  const [tab, setTab] = useState<GraphTab>(() => readTabFromLocation())
  const [, refreshLocation] = useState(0)
  const search = readGraphSearch()
  const memoryId = readMemoryScopeFromLocation()

  const writeSearch = (patch: Record<string, string | number | undefined>) => {
    writeUrlSearch(patch)
    refreshLocation((current) => current + 1)
  }

  return (
    <>
      <Header>
        <h1 className='truncate text-lg font-semibold'>Item Graph</h1>
      </Header>
      <Main>
        <div className='flex flex-col gap-4'>
          <div className='flex flex-col gap-1'>
            <h2 className='text-2xl font-semibold'>Item Graph</h2>
            <p className='text-sm text-muted-foreground'>
              Inspect graph entities, aliases, mentions, links, cooccurrences,
              and extraction batches.
            </p>
          </div>

          <Tabs
            value={tab}
            onValueChange={(value) => {
              const next = normalizeTab(value)
              setTab(next)
              writeSearch({
                tab: next === 'summary' ? undefined : next,
                pageNo: undefined,
                state: undefined,
              })
            }}
          >
            <TabsList className='flex flex-wrap'>
              <TabsTrigger value='summary'>Summary</TabsTrigger>
              <TabsTrigger value='entities'>Entities</TabsTrigger>
              <TabsTrigger value='aliases'>Aliases</TabsTrigger>
              <TabsTrigger value='mentions'>Mentions</TabsTrigger>
              <TabsTrigger value='item-links'>Item Links</TabsTrigger>
              <TabsTrigger value='cooccurrences'>Cooccurrences</TabsTrigger>
              <TabsTrigger value='batches'>Batches</TabsTrigger>
            </TabsList>

            <TabsContent value='summary'>
              <SummaryTab memoryId={memoryId} />
            </TabsContent>
            <TabsContent value='entities'>
              <EntitiesTab search={search} memoryId={memoryId} />
            </TabsContent>
            <TabsContent value='aliases'>
              <AliasesTab search={search} memoryId={memoryId} />
            </TabsContent>
            <TabsContent value='mentions'>
              <MentionsTab search={search} memoryId={memoryId} />
            </TabsContent>
            <TabsContent value='item-links'>
              <ItemLinksTab search={search} memoryId={memoryId} />
            </TabsContent>
            <TabsContent value='cooccurrences'>
              <CooccurrencesTab search={search} memoryId={memoryId} />
            </TabsContent>
            <TabsContent value='batches'>
              <BatchesTab
                search={search}
                memoryId={memoryId}
                onSearchChange={writeSearch}
              />
            </TabsContent>
          </Tabs>
        </div>
      </Main>
    </>
  )
}

function EntitiesTab({
  search,
  memoryId,
}: {
  search: GraphSearch
  memoryId: string
}) {
  const queryClient = useQueryClient()
  const [selectedKeys, setSelectedKeys] = useState<Set<string>>(() => new Set())
  const [deleteOpen, setDeleteOpen] = useState(false)
  const [detailId, setDetailId] = useState<number | null>(null)
  const params = useMemo(
    () => ({
      pageNo: search.pageNo,
      pageSize: search.pageSize,
      memoryId: memoryId || undefined,
    }),
    [memoryId, search.pageNo, search.pageSize]
  )
  const query = useQuery({
    queryKey: ['item-graph', 'entities', params],
    queryFn: () => listGraphEntities(params),
  })
  const deleteMutation = useMutation({
    mutationFn: (entityKeys: string[]) =>
      deleteGraphEntities({ memoryId, entityKeys }),
    onSuccess: async () => {
      setSelectedKeys(new Set())
      await invalidateGraph(queryClient)
    },
  })
  const rows = query.data?.list ?? []
  const selectedEntityKeys = [...selectedKeys]

  return (
    <div className='flex flex-col gap-4'>
      <Button
        type='button'
        variant='destructive'
        className='w-fit'
        disabled={!memoryId || selectedKeys.size === 0}
        onClick={() => {
          deleteMutation.reset()
          setDeleteOpen(true)
        }}
      >
        Delete selected entities
      </Button>
      <QueryTableState
        isLoading={query.isLoading}
        isError={query.isError}
        isEmpty={query.data !== undefined && rows.length === 0}
        errorMessage='Unable to load graph entities.'
        emptyTitle='No graph entities found.'
        onRetry={query.refetch}
      />
      {rows.length > 0 ? (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Select</TableHead>
              <TableHead>ID</TableHead>
              <TableHead>Memory ID</TableHead>
              <TableHead>Entity Key</TableHead>
              <TableHead>Display Name</TableHead>
              <TableHead>Entity Type</TableHead>
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
                    aria-label={`Select entity ${row.entityKey}`}
                    checked={selectedKeys.has(row.entityKey)}
                    onCheckedChange={(checked) =>
                      setSelectedKeys((current) =>
                        toggleId(current, row.entityKey, checked === true)
                      )
                    }
                  />
                </TableCell>
                <TableCell>{row.id}</TableCell>
                <TableCell>{row.memoryId}</TableCell>
                <TableCell>{row.entityKey}</TableCell>
                <TableCell>{fieldValue(row.displayName)}</TableCell>
                <TableCell>{fieldValue(row.entityType)}</TableCell>
                <TableCell>{formatDateTime(row.createdAt)}</TableCell>
                <TableCell>{formatDateTime(row.updatedAt)}</TableCell>
                <TableCell className='text-end'>
                  <Button
                    type='button'
                    variant='outline'
                    size='sm'
                    aria-label={`View entity ${row.id}`}
                    onClick={() => setDetailId(row.id)}
                  >
                    View
                  </Button>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      ) : null}
      <ConfirmResultDialog
        open={deleteOpen}
        onOpenChange={setDeleteOpen}
        title='Delete selected graph entities'
        selectedCount={selectedEntityKeys.length}
        description='Only selected entity keys will be sent to the server.'
        cascadeWarning='Aliases, mentions, and cooccurrences for these entities can also be removed.'
        isPending={deleteMutation.isPending}
        result={deleteMutation.data ? { ...deleteMutation.data } : null}
        onConfirm={() => deleteMutation.mutate(selectedEntityKeys)}
      />
      <EntityDetailDrawer
        entityId={detailId}
        onOpenChange={(open) => {
          if (!open) setDetailId(null)
        }}
      />
    </div>
  )
}

function AliasesTab(props: GraphTabProps) {
  const params = buildPageParams(props)
  const query = useQuery({
    queryKey: ['item-graph', 'aliases', params],
    queryFn: () => listGraphAliases(params),
  })
  return (
    <IdDeleteTab
      rows={query.data?.list ?? []}
      query={query}
      selectLabel={(row: GraphAliasView) => `Select alias ${row.id}`}
      deleteLabel='Delete selected aliases'
      emptyTitle='No graph aliases found.'
      errorMessage='Unable to load graph aliases.'
      deleteFn={deleteGraphAliases}
      columns={['ID', 'Memory ID', 'Entity Key', 'Normalized Alias', 'Entity Type', 'Evidence Count', 'Created At']}
      renderRow={(row: GraphAliasView) => [
        row.id,
        row.memoryId,
        row.entityKey,
        row.normalizedAlias,
        fieldValue(row.entityType),
        fieldValue(row.evidenceCount),
        formatDateTime(row.createdAt),
      ]}
    />
  )
}

function MentionsTab(props: GraphTabProps) {
  const params = buildPageParams(props)
  const query = useQuery({
    queryKey: ['item-graph', 'mentions', params],
    queryFn: () => listGraphMentions(params),
  })
  return (
    <IdDeleteTab
      rows={query.data?.list ?? []}
      query={query}
      selectLabel={(row: GraphMentionView) => `Select mention ${row.id}`}
      deleteLabel='Delete selected mentions'
      emptyTitle='No graph mentions found.'
      errorMessage='Unable to load graph mentions.'
      deleteFn={deleteGraphMentions}
      columns={['ID', 'Memory ID', 'Item ID', 'Entity Key', 'Confidence', 'Created At']}
      renderRow={(row: GraphMentionView) => [
        row.id,
        row.memoryId,
        row.itemId,
        row.entityKey,
        fieldValue(row.confidence),
        formatDateTime(row.createdAt),
      ]}
    />
  )
}

function ItemLinksTab(props: GraphTabProps) {
  const params = buildPageParams(props)
  const query = useQuery({
    queryKey: ['item-graph', 'item-links', params],
    queryFn: () => listGraphItemLinks(params),
  })
  return (
    <IdDeleteTab
      rows={query.data?.list ?? []}
      query={query}
      selectLabel={(row: GraphItemLinkView) => `Select item link ${row.id}`}
      deleteLabel='Delete selected item links'
      emptyTitle='No graph item links found.'
      errorMessage='Unable to load graph item links.'
      deleteFn={deleteGraphItemLinks}
      columns={['ID', 'Memory ID', 'Source Item ID', 'Target Item ID', 'Link Type', 'Relation Code', 'Evidence Source', 'Strength', 'Created At']}
      renderRow={(row: GraphItemLinkView) => [
        row.id,
        row.memoryId,
        row.sourceItemId,
        row.targetItemId,
        fieldValue(row.linkType),
        fieldValue(row.relationCode),
        fieldValue(row.evidenceSource),
        fieldValue(row.strength),
        formatDateTime(row.createdAt),
      ]}
    />
  )
}

function CooccurrencesTab(props: GraphTabProps) {
  const params = buildPageParams(props)
  const query = useQuery({
    queryKey: ['item-graph', 'cooccurrences', params],
    queryFn: () => listGraphCooccurrences(params),
  })
  return (
    <IdDeleteTab
      rows={query.data?.list ?? []}
      query={query}
      selectLabel={(row: GraphCooccurrenceView) =>
        `Select cooccurrence ${row.id}`}
      deleteLabel='Delete selected cooccurrences'
      emptyTitle='No graph cooccurrences found.'
      errorMessage='Unable to load graph cooccurrences.'
      deleteFn={deleteGraphCooccurrences}
      columns={['ID', 'Memory ID', 'Left Entity Key', 'Right Entity Key', 'Cooccurrence Count', 'Created At']}
      renderRow={(row: GraphCooccurrenceView) => [
        row.id,
        row.memoryId,
        row.leftEntityKey,
        row.rightEntityKey,
        fieldValue(row.cooccurrenceCount),
        formatDateTime(row.createdAt),
      ]}
    />
  )
}

function BatchesTab({
  search,
  memoryId,
  onSearchChange,
}: GraphTabProps & {
  onSearchChange: (patch: Record<string, string | number | undefined>) => void
}) {
  const state = normalizeBatchState(search.state)
  const params = useMemo(
    () => ({
      pageNo: search.pageNo,
      pageSize: search.pageSize,
      memoryId: memoryId || undefined,
      ...(state === 'all' ? {} : { state }),
    }),
    [memoryId, search.pageNo, search.pageSize, state]
  )
  const query = useQuery({
    queryKey: ['item-graph', 'batches', params],
    queryFn: () => listGraphBatches(params),
  })
  const rows = query.data?.list ?? []

  return (
    <div className='flex flex-col gap-4'>
      <div className='flex items-center gap-2'>
        <Label htmlFor='graph-batch-state'>Batch state</Label>
        <select
          id='graph-batch-state'
          aria-label='Graph batch state'
          value={state}
          className='h-9 rounded-md border bg-background px-3 text-sm'
          onChange={(event) =>
            onSearchChange({
              pageNo: undefined,
              state:
                event.target.value === 'all' ? undefined : event.target.value,
            })
          }
        >
          <option value='all'>all</option>
          <option value='PENDING'>PENDING</option>
          <option value='COMMITTED'>COMMITTED</option>
          <option value='REPAIR_REQUIRED'>REPAIR_REQUIRED</option>
        </select>
      </div>
      <QueryTableState
        isLoading={query.isLoading}
        isError={query.isError}
        isEmpty={query.data !== undefined && rows.length === 0}
        errorMessage='Unable to load graph batches.'
        emptyTitle='No graph batches found.'
        onRetry={query.refetch}
      />
      {rows.length > 0 ? (
        <SimpleTable
          columns={['ID', 'Memory ID', 'Extraction Batch ID', 'State', 'Error Message', 'Retry Promotion Supported', 'Created At', 'Updated At']}
          rows={rows.map((row) => [
            row.id,
            row.memoryId,
            row.extractionBatchId,
            <Badge key='state'>{row.state}</Badge>,
            fieldValue(row.errorMessage),
            row.retryPromotionSupported ? 'yes' : 'no',
            formatDateTime(row.createdAt),
            formatDateTime(row.updatedAt),
          ])}
        />
      ) : null}
    </div>
  )
}

function IdDeleteTab<T extends { id: number }>({
  rows,
  query,
  selectLabel,
  deleteLabel,
  emptyTitle,
  errorMessage,
  deleteFn,
  columns,
  renderRow,
}: {
  rows: T[]
  query: {
    isLoading: boolean
    isError: boolean
    data?: unknown
    refetch: () => void
  }
  selectLabel: (row: T) => string
  deleteLabel: string
  emptyTitle: string
  errorMessage: string
  deleteFn: (ids: number[]) => Promise<BatchDeleteResult>
  columns: string[]
  renderRow: (row: T) => ReactNode[]
}) {
  const queryClient = useQueryClient()
  const [selectedIds, setSelectedIds] = useState<Set<number>>(() => new Set())
  const [deleteOpen, setDeleteOpen] = useState(false)
  const mutation = useMutation({
    mutationFn: (ids: number[]) => deleteFn(ids),
    onSuccess: async () => {
      setSelectedIds(new Set())
      await invalidateGraph(queryClient)
    },
  })
  const selected = [...selectedIds]

  return (
    <div className='flex flex-col gap-4'>
      <Button
        type='button'
        variant='destructive'
        className='w-fit'
        disabled={selectedIds.size === 0}
        onClick={() => {
          mutation.reset()
          setDeleteOpen(true)
        }}
      >
        {deleteLabel}
      </Button>
      <QueryTableState
        isLoading={query.isLoading}
        isError={query.isError}
        isEmpty={query.data !== undefined && rows.length === 0}
        errorMessage={errorMessage}
        emptyTitle={emptyTitle}
        onRetry={query.refetch}
      />
      {rows.length > 0 ? (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Select</TableHead>
              {columns.map((column) => (
                <TableHead key={column}>{column}</TableHead>
              ))}
            </TableRow>
          </TableHeader>
          <TableBody>
            {rows.map((row) => (
              <TableRow key={row.id}>
                <TableCell>
                  <Checkbox
                    aria-label={selectLabel(row)}
                    checked={selectedIds.has(row.id)}
                    onCheckedChange={(checked) =>
                      setSelectedIds((current) =>
                        toggleId(current, row.id, checked === true)
                      )
                    }
                  />
                </TableCell>
                {renderRow(row).map((cell, index) => (
                  <TableCell key={cellKey(cell, index)}>{cell}</TableCell>
                ))}
              </TableRow>
            ))}
          </TableBody>
        </Table>
      ) : null}
      <ConfirmResultDialog
        open={deleteOpen}
        onOpenChange={setDeleteOpen}
        title={deleteLabel}
        selectedCount={selected.length}
        description='Only selected row ids will be sent to the server.'
        isPending={mutation.isPending}
        result={mutation.data ? { ...mutation.data } : null}
        onConfirm={() => mutation.mutate(selected)}
      />
    </div>
  )
}

function SimpleTable({
  columns,
  rows,
}: {
  columns: string[]
  rows: ReactNode[][]
}) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          {columns.map((column) => (
            <TableHead key={column}>{column}</TableHead>
          ))}
        </TableRow>
      </TableHeader>
      <TableBody>
        {rows.map((row, rowIndex) => (
          <TableRow key={rowKey(row, rowIndex)}>
            {row.map((cell, cellIndex) => (
              <TableCell key={cellKey(cell, cellIndex)}>{cell}</TableCell>
            ))}
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}

function QueryTableState({
  isLoading,
  isError,
  isEmpty,
  errorMessage,
  emptyTitle,
  onRetry,
}: {
  isLoading: boolean
  isError: boolean
  isEmpty: boolean
  errorMessage: string
  emptyTitle: string
  onRetry: () => void
}) {
  return (
    <>
      {isLoading ? <TableLoading columns={6} /> : null}
      {isError ? <PageError message={errorMessage} onRetry={onRetry} /> : null}
      {isEmpty ? <EmptyState title={emptyTitle} /> : null}
    </>
  )
}

type GraphTabProps = {
  search: GraphSearch
  memoryId: string
}

type GraphSearch = {
  pageNo: number
  pageSize: number
  state?: string
}

function buildPageParams({ search, memoryId }: GraphTabProps) {
  return {
    pageNo: search.pageNo,
    pageSize: search.pageSize,
    memoryId: memoryId || undefined,
  }
}

function readGraphSearch(): GraphSearch {
  const params = readSearchParams()
  return {
    pageNo: readNumberParam(params, 'pageNo', DEFAULT_PAGE),
    pageSize: readNumberParam(params, 'pageSize', DEFAULT_PAGE_SIZE),
    state: readStringParam(params, 'state'),
  }
}

function readTabFromLocation(): GraphTab {
  return normalizeTab(readSearchParams().get('tab') ?? '')
}

function normalizeTab(value: string): GraphTab {
  if (
    value === 'entities' ||
    value === 'aliases' ||
    value === 'mentions' ||
    value === 'item-links' ||
    value === 'cooccurrences' ||
    value === 'batches'
  ) {
    return value
  }
  return 'summary'
}

function normalizeBatchState(state: string | undefined): BatchStateFilter {
  if (
    state === 'PENDING' ||
    state === 'COMMITTED' ||
    state === 'REPAIR_REQUIRED'
  ) {
    return state
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

function toggleId<T>(current: Set<T>, id: T, checked: boolean) {
  const next = new Set(current)
  if (checked) {
    next.add(id)
  } else {
    next.delete(id)
  }
  return next
}

async function invalidateGraph(queryClient: ReturnType<typeof useQueryClient>) {
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: ['item-graph'] }),
    queryClient.invalidateQueries({ queryKey: ['dashboard'] }),
  ])
}

function fieldValue(value: unknown) {
  if (value === null || value === undefined || value === '') return '-'
  return String(value)
}

function rowKey(row: ReactNode[], index: number) {
  return `${index}:${row.map((cell) => String(cell)).join('|')}`
}

function cellKey(cell: ReactNode, index: number) {
  return `${index}:${String(cell)}`
}
