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
  deleteInsights,
  getInsight,
  listInsights,
  type InsightListParams,
} from '@/features/api/insights'
import { ConfirmResultDialog } from '@/features/components/confirm-result-dialog'
import {
  EmptyState,
  PageError,
  PageLoading,
  TableLoading,
} from '@/features/components/data-state'
import { JsonViewer } from '@/features/components/json-viewer'
import { readMemoryScopeFromLocation } from '@/features/components/memory-scope-location'
import type { AdminInsightView, InsightPoint } from '@/features/types'
import { compactJson, formatDateTime, truncateText } from '@/lib/format'
import { toUserAgentQuery } from '@/lib/memory-scope'

const DEFAULT_PAGE = 1
const DEFAULT_PAGE_SIZE = 10

export function InsightsPage() {
  const queryClient = useQueryClient()
  const [selectedIds, setSelectedIds] = useState<Set<number>>(() => new Set())
  const [deleteOpen, setDeleteOpen] = useState(false)
  const [detailInsightId, setDetailInsightId] = useState<number | null>(
    null
  )

  const search = readInsightsSearch()
  const memoryScope = readMemoryScopeFromLocation()
  const params = useMemo(
    () => buildInsightListParams(search, memoryScope),
    [search, memoryScope]
  )
  const insightsQuery = useQuery({
    queryKey: ['insights', params],
    queryFn: () => listInsights(params),
  })
  const deleteMutation = useMutation({
    mutationFn: (insightIds: number[]) => deleteInsights(insightIds),
    onSuccess: async () => {
      setSelectedIds(new Set())
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['insights'] }),
        queryClient.invalidateQueries({ queryKey: ['dashboard'] }),
      ])
    },
  })

  const rows = insightsQuery.data?.list ?? []
  const selectedInsightIds = [...selectedIds]

  return (
    <>
      <Header>
        <h1 className='truncate text-lg font-semibold'>Insight Tree</h1>
      </Header>
      <Main>
        <div className='flex flex-col gap-4'>
          <div className='flex flex-wrap items-center justify-between gap-3'>
            <div className='flex flex-col gap-1'>
              <h2 className='text-2xl font-semibold'>Insight Tree</h2>
              <p className='text-sm text-muted-foreground'>
                Inspect extracted long-lived insights, reasoning points, and
                relationships.
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

          {insightsQuery.isLoading ? <TableLoading columns={8} /> : null}
          {insightsQuery.isError ? (
            <PageError
              message='Unable to load insights.'
              onRetry={insightsQuery.refetch}
            />
          ) : null}
          {insightsQuery.data && rows.length === 0 ? (
            <EmptyState title='No insights found.' />
          ) : null}
          {rows.length > 0 ? (
            <InsightsTable
              rows={rows}
              selectedIds={selectedIds}
              onToggleSelected={(insightId, checked) => {
                setSelectedIds((current) => {
                  const next = new Set(current)
                  if (checked) {
                    next.add(insightId)
                  } else {
                    next.delete(insightId)
                  }
                  return next
                })
              }}
              onView={setDetailInsightId}
            />
          ) : null}

          {insightsQuery.data ? (
            <p className='text-sm text-muted-foreground'>
              Total rows: {insightsQuery.data.total}
            </p>
          ) : null}
        </div>
      </Main>

      <ConfirmResultDialog
        open={deleteOpen}
        onOpenChange={setDeleteOpen}
        title='Delete selected insights'
        selectedCount={selectedInsightIds.length}
        description='Only the selected insight ids will be sent to the server.'
        isPending={deleteMutation.isPending}
        result={deleteMutation.data ? { ...deleteMutation.data } : null}
        onConfirm={() => deleteMutation.mutate(selectedInsightIds)}
      />

      <InsightDetailDialog
        insightId={detailInsightId}
        onOpenChange={(open) => {
          if (!open) setDetailInsightId(null)
        }}
      />
    </>
  )
}

function InsightsTable({
  rows,
  selectedIds,
  onToggleSelected,
  onView,
}: {
  rows: AdminInsightView[]
  selectedIds: Set<number>
  onToggleSelected: (insightId: number, checked: boolean) => void
  onView: (insightId: number) => void
}) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead className='w-10'>Select</TableHead>
          <TableHead>Insight ID</TableHead>
          <TableHead>Memory ID</TableHead>
          <TableHead>Name / Content</TableHead>
          <TableHead>Type</TableHead>
          <TableHead>Tier</TableHead>
          <TableHead>Scope</TableHead>
          <TableHead>Group Name</TableHead>
          <TableHead>Updated At</TableHead>
          <TableHead className='text-end'>Actions</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {rows.map((insight) => (
          <TableRow key={insight.insightId}>
            <TableCell>
              <Checkbox
                aria-label={`Select insight ${insight.insightId}`}
                checked={selectedIds.has(insight.insightId)}
                onCheckedChange={(checked) =>
                  onToggleSelected(insight.insightId, checked === true)
                }
              />
            </TableCell>
            <TableCell>{insight.insightId}</TableCell>
            <TableCell>{insight.memoryId}</TableCell>
            <TableCell className='max-w-96 whitespace-normal'>
              {truncateText(insight.name || insight.content, 120)}
            </TableCell>
            <TableCell>
              {insight.type ? <Badge>{insight.type}</Badge> : '-'}
            </TableCell>
            <TableCell>{fieldValue(insight.tier)}</TableCell>
            <TableCell>{fieldValue(insight.scope)}</TableCell>
            <TableCell>{fieldValue(insight.groupName)}</TableCell>
            <TableCell>{formatDateTime(insight.updatedAt)}</TableCell>
            <TableCell className='text-end'>
              <Button
                type='button'
                variant='outline'
                size='sm'
                aria-label={`View insight ${insight.insightId}`}
                onClick={() => onView(insight.insightId)}
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

function InsightDetailDialog({
  insightId,
  onOpenChange,
}: {
  insightId: number | null
  onOpenChange: (open: boolean) => void
}) {
  const query = useQuery({
    queryKey: ['insights', insightId, 'detail'],
    enabled: insightId !== null,
    queryFn: () => getInsight(insightId as number),
  })
  const insight = query.data

  return (
    <Dialog open={insightId !== null} onOpenChange={onOpenChange}>
      <DialogContent className='max-h-[85vh] overflow-auto sm:max-w-3xl'>
        <DialogHeader>
          <DialogTitle>Insight {insightId}</DialogTitle>
          <DialogDescription>{insight?.memoryId}</DialogDescription>
        </DialogHeader>

        {query.isLoading ? <PageLoading /> : null}
        {query.isError ? (
          <PageError
            message='Unable to load insight detail.'
            onRetry={query.refetch}
          />
        ) : null}
        {insight ? (
          <div className='flex flex-col gap-5 text-sm'>
            <section className='flex flex-col gap-2'>
              <h3 className='font-medium'>Content</h3>
              <p className='whitespace-pre-wrap rounded-md bg-muted p-3'>
                {insight.content}
              </p>
            </section>

            <section className='flex flex-col gap-2'>
              <h3 className='font-medium'>Categories</h3>
              {insight.categories.length > 0 ? (
                <div className='flex flex-wrap gap-2'>
                  {insight.categories.map((category) => (
                    <Badge key={category} variant='secondary'>
                      {category}
                    </Badge>
                  ))}
                </div>
              ) : (
                <p>-</p>
              )}
            </section>

            <section className='flex flex-col gap-2'>
              <h3 className='font-medium'>Points</h3>
              {insight.points.length > 0 ? (
                <ul className='flex flex-col gap-1'>
                  {insight.points.map((point, index) => (
                    <li key={pointKey(point, index)}>{pointText(point)}</li>
                  ))}
                </ul>
              ) : (
                <p>-</p>
              )}
            </section>

            <div className='grid gap-2 md:grid-cols-2'>
              <p>type: {fieldValue(insight.type)}</p>
              <p>tier: {fieldValue(insight.tier)}</p>
              <p>scope: {fieldValue(insight.scope)}</p>
              <p>groupName: {fieldValue(insight.groupName)}</p>
              <p>parentInsightId: {fieldValue(insight.parentInsightId)}</p>
              <p>childInsightIds: {fieldValue(insight.childInsightIds)}</p>
              <p>version: {fieldValue(insight.version)}</p>
              <p>lastReasonedAt: {formatDateTime(insight.lastReasonedAt)}</p>
              <p>createdAt: {formatDateTime(insight.createdAt)}</p>
              <p>updatedAt: {formatDateTime(insight.updatedAt)}</p>
            </div>

            <JsonViewer
              label='summaryEmbedding'
              value={insight.summaryEmbedding ?? []}
            />
          </div>
        ) : null}
      </DialogContent>
    </Dialog>
  )
}

function buildInsightListParams(
  search: InsightSearch,
  memoryScope: string
): InsightListParams {
  return {
    pageNo: search.pageNo,
    pageSize: search.pageSize,
    ...toUserAgentQuery(memoryScope),
    scope: search.scope,
    type: search.type,
    tier: search.tier,
  }
}

type InsightSearch = {
  pageNo: number
  pageSize: number
  scope?: string
  type?: string
  tier?: string
}

function readInsightsSearch(): InsightSearch {
  const params = readSearchParams()
  return {
    pageNo: readNumberParam(params, 'pageNo', DEFAULT_PAGE),
    pageSize: readNumberParam(params, 'pageSize', DEFAULT_PAGE_SIZE),
    scope: readStringParam(params, 'scope'),
    type: readStringParam(params, 'type'),
    tier: readStringParam(params, 'tier'),
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

function pointText(point: InsightPoint) {
  const text = point.text
  return typeof text === 'string' && text.trim() !== ''
    ? text
    : compactJson(point)
}

function pointKey(point: InsightPoint, index: number) {
  return `${index}:${compactJson(point)}`
}

function fieldValue(value: unknown) {
  if (value === null || value === undefined || value === '') return '-'
  if (Array.isArray(value)) return value.length > 0 ? value.join(', ') : '-'
  if (typeof value === 'object') return compactJson(value)
  return String(value)
}
