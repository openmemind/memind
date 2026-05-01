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
  deleteRawData,
  getRawData,
  listRawData,
  type RawDataListParams,
} from '@/features/api/raw-data'
import { ConfirmResultDialog } from '@/features/components/confirm-result-dialog'
import {
  EmptyState,
  PageError,
  PageLoading,
  TableLoading,
} from '@/features/components/data-state'
import { JsonViewer } from '@/features/components/json-viewer'
import { readMemoryScopeFromLocation } from '@/features/components/memory-scope-location'
import type { AdminRawDataView } from '@/features/types'
import { compactJson, formatDateTime, truncateText } from '@/lib/format'
import { toUserAgentQuery } from '@/lib/memory-scope'

const DEFAULT_PAGE = 1
const DEFAULT_PAGE_SIZE = 10

export function RawDataPage() {
  const queryClient = useQueryClient()
  const [selectedIds, setSelectedIds] = useState<Set<string>>(() => new Set())
  const [deleteOpen, setDeleteOpen] = useState(false)
  const [detailRawDataId, setDetailRawDataId] = useState<string | null>(
    null
  )

  const search = readRawDataSearch()
  const memoryScope = readMemoryScopeFromLocation()
  const params = useMemo(
    () => buildRawDataListParams(search, memoryScope),
    [search, memoryScope]
  )
  const rawDataQuery = useQuery({
    queryKey: ['raw-data', params],
    queryFn: () => listRawData(params),
  })
  const deleteMutation = useMutation({
    mutationFn: (rawDataIds: string[]) => deleteRawData(rawDataIds),
    onSuccess: async () => {
      setSelectedIds(new Set())
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['raw-data'] }),
        queryClient.invalidateQueries({ queryKey: ['items'] }),
        queryClient.invalidateQueries({ queryKey: ['dashboard'] }),
      ])
    },
  })

  const rows = rawDataQuery.data?.list ?? []
  const selectedRawDataIds = [...selectedIds]

  return (
    <>
      <Header>
        <h1 className='truncate text-lg font-semibold'>Raw Data</h1>
      </Header>
      <Main>
        <div className='flex flex-col gap-4'>
          <div className='flex flex-wrap items-center justify-between gap-3'>
            <div className='flex flex-col gap-1'>
              <h2 className='text-2xl font-semibold'>Raw Data</h2>
              <p className='text-sm text-muted-foreground'>
                Inspect source records, captions, payloads, and cleanup impact.
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

          {rawDataQuery.isLoading ? <TableLoading columns={8} /> : null}
          {rawDataQuery.isError ? (
            <PageError
              message='Unable to load raw data.'
              onRetry={rawDataQuery.refetch}
            />
          ) : null}
          {rawDataQuery.data && rows.length === 0 ? (
            <EmptyState title='No raw data found.' />
          ) : null}
          {rows.length > 0 ? (
            <RawDataTable
              rows={rows}
              selectedIds={selectedIds}
              onToggleSelected={(rawDataId, checked) => {
                setSelectedIds((current) => {
                  const next = new Set(current)
                  if (checked) {
                    next.add(rawDataId)
                  } else {
                    next.delete(rawDataId)
                  }
                  return next
                })
              }}
              onView={setDetailRawDataId}
            />
          ) : null}

          {rawDataQuery.data ? (
            <p className='text-sm text-muted-foreground'>
              Total rows: {rawDataQuery.data.total}
            </p>
          ) : null}
        </div>
      </Main>

      <ConfirmResultDialog
        open={deleteOpen}
        onOpenChange={setDeleteOpen}
        title='Delete selected raw data'
        selectedCount={selectedRawDataIds.length}
        description='Only the selected raw data ids will be sent to the server.'
        cascadeWarning='Associated memory items will also be deleted.'
        isPending={deleteMutation.isPending}
        result={deleteMutation.data ? { ...deleteMutation.data } : null}
        onConfirm={() => deleteMutation.mutate(selectedRawDataIds)}
      />

      <RawDataDetailDialog
        rawDataId={detailRawDataId}
        onOpenChange={(open) => {
          if (!open) setDetailRawDataId(null)
        }}
      />
    </>
  )
}

function RawDataTable({
  rows,
  selectedIds,
  onToggleSelected,
  onView,
}: {
  rows: AdminRawDataView[]
  selectedIds: Set<string>
  onToggleSelected: (rawDataId: string, checked: boolean) => void
  onView: (rawDataId: string) => void
}) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead className='w-10'>Select</TableHead>
          <TableHead>Raw Data ID</TableHead>
          <TableHead>Memory ID</TableHead>
          <TableHead>Type</TableHead>
          <TableHead>Caption</TableHead>
          <TableHead>Source Client</TableHead>
          <TableHead>Content ID</TableHead>
          <TableHead>Start Time</TableHead>
          <TableHead>Created At</TableHead>
          <TableHead className='text-end'>Actions</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {rows.map((rawData) => (
          <TableRow key={rawData.rawDataId}>
            <TableCell>
              <Checkbox
                aria-label={`Select raw data ${rawData.rawDataId}`}
                checked={selectedIds.has(rawData.rawDataId)}
                onCheckedChange={(checked) =>
                  onToggleSelected(rawData.rawDataId, checked === true)
                }
              />
            </TableCell>
            <TableCell>{rawData.rawDataId}</TableCell>
            <TableCell>{rawData.memoryId}</TableCell>
            <TableCell>
              {rawData.type ? <Badge>{rawData.type}</Badge> : '-'}
            </TableCell>
            <TableCell className='max-w-96 whitespace-normal'>
              <span className='sr-only'>Caption preview: </span>
              {truncateText(rawData.caption, 12)}
            </TableCell>
            <TableCell>{fieldValue(rawData.sourceClient)}</TableCell>
            <TableCell>{fieldValue(rawData.contentId)}</TableCell>
            <TableCell>{formatDateTime(rawData.startTime)}</TableCell>
            <TableCell>{formatDateTime(rawData.createdAt)}</TableCell>
            <TableCell className='text-end'>
              <Button
                type='button'
                variant='outline'
                size='sm'
                aria-label={`View raw data ${rawData.rawDataId}`}
                onClick={() => onView(rawData.rawDataId)}
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

function RawDataDetailDialog({
  rawDataId,
  onOpenChange,
}: {
  rawDataId: string | null
  onOpenChange: (open: boolean) => void
}) {
  const query = useQuery({
    queryKey: ['raw-data', rawDataId, 'detail'],
    enabled: rawDataId !== null,
    queryFn: () => getRawData(rawDataId as string),
  })
  const rawData = query.data

  return (
    <Dialog open={rawDataId !== null} onOpenChange={onOpenChange}>
      <DialogContent className='max-h-[85vh] overflow-auto sm:max-w-3xl'>
        <DialogHeader>
          <DialogTitle>Raw data {rawDataId}</DialogTitle>
          <DialogDescription>{rawData?.memoryId}</DialogDescription>
        </DialogHeader>

        {query.isLoading ? <PageLoading /> : null}
        {query.isError ? (
          <PageError
            message='Unable to load raw data detail.'
            onRetry={query.refetch}
          />
        ) : null}
        {rawData ? (
          <div className='flex flex-col gap-5 text-sm'>
            <section className='flex flex-col gap-2'>
              <h3 className='font-medium'>Caption</h3>
              <p className='whitespace-pre-wrap rounded-md bg-muted p-3'>
                {fieldValue(rawData.caption)}
              </p>
            </section>

            <div className='grid gap-2 md:grid-cols-2'>
              <p>type: {fieldValue(rawData.type)}</p>
              <p>sourceClient: {fieldValue(rawData.sourceClient)}</p>
              <p>contentId: {fieldValue(rawData.contentId)}</p>
              <p>captionVectorId: {fieldValue(rawData.captionVectorId)}</p>
              <p>startTime: {formatDateTime(rawData.startTime)}</p>
              <p>endTime: {formatDateTime(rawData.endTime)}</p>
              <p>createdAt: {formatDateTime(rawData.createdAt)}</p>
              <p>updatedAt: {formatDateTime(rawData.updatedAt)}</p>
            </div>

            <JsonViewer
              label='payload JSON'
              value={rawData.segment ?? {}}
              defaultExpanded
            />
            <JsonViewer
              label='metadata'
              value={rawData.metadata ?? {}}
              defaultExpanded
            />
          </div>
        ) : null}
      </DialogContent>
    </Dialog>
  )
}

function buildRawDataListParams(
  search: RawDataSearch,
  memoryScope: string
): RawDataListParams {
  return {
    pageNo: search.pageNo,
    pageSize: search.pageSize,
    ...toUserAgentQuery(memoryScope),
    startTimeFrom: search.startTimeFrom,
    startTimeTo: search.startTimeTo,
  }
}

type RawDataSearch = {
  pageNo: number
  pageSize: number
  startTimeFrom?: string
  startTimeTo?: string
}

function readRawDataSearch(): RawDataSearch {
  const params = readSearchParams()
  return {
    pageNo: readNumberParam(params, 'pageNo', DEFAULT_PAGE),
    pageSize: readNumberParam(params, 'pageSize', DEFAULT_PAGE_SIZE),
    startTimeFrom: readStringParam(params, 'startTimeFrom'),
    startTimeTo: readStringParam(params, 'startTimeTo'),
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

function fieldValue(value: unknown) {
  if (value === null || value === undefined || value === '') return '-'
  if (Array.isArray(value)) return value.length > 0 ? value.join(', ') : '-'
  if (typeof value === 'object') return compactJson(value)
  return String(value)
}
