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
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
} from '@/components/ui/tabs'
import {
  deleteConversationBuffers,
  deleteInsightBuffers,
  getConversationBuffer,
  listConversationBuffers,
  listInsightBufferGroups,
  listInsightBuffers,
  markConversationsExtracted,
  updateInsightBufferBuilt,
  updateInsightBufferGroup,
  type ConversationBufferListParams,
  type ConversationBufferState,
  type InsightBufferListParams,
  type InsightBufferState,
} from '@/features/api/buffers'
import { ConfirmResultDialog } from '@/features/components/confirm-result-dialog'
import {
  EmptyState,
  PageError,
  PageLoading,
  TableLoading,
} from '@/features/components/data-state'
import { readMemoryScopeFromLocation } from '@/features/components/memory-scope-location'
import type { ConversationBufferView } from '@/features/types'
import { formatDateTime } from '@/lib/format'
import { ConversationBuffersTable } from './conversations-table'
import { InsightBuffersTable } from './insight-buffers-table'
import { InsightGroupsTable } from './insight-groups-table'

const DEFAULT_PAGE = 1
const DEFAULT_PAGE_SIZE = 10
const DEFAULT_CONVERSATION_STATE: ConversationBufferState = 'pending'
const DEFAULT_INSIGHT_STATE: InsightBufferState = 'unbuilt'

type BufferTab = 'conversations' | 'insights' | 'groups'

export function BuffersPage() {
  const [tab, setTab] = useState<BufferTab>(() => readTabFromLocation())
  const [, refreshLocation] = useState(0)
  const search = readBuffersSearch()
  const memoryId = readMemoryScopeFromLocation()

  const writeSearch = (patch: Record<string, string | number | undefined>) => {
    writeUrlSearch(patch)
    refreshLocation((current) => current + 1)
  }

  return (
    <>
      <Header>
        <h1 className='truncate text-lg font-semibold'>Buffers</h1>
      </Header>
      <Main>
        <div className='flex flex-col gap-4'>
          <div className='flex flex-col gap-1'>
            <h2 className='text-2xl font-semibold'>Buffers</h2>
            <p className='text-sm text-muted-foreground'>
              Manage pending conversation and insight buffer records before they
              are materialized into memory.
            </p>
          </div>

          <Tabs
            value={tab}
            onValueChange={(value) => {
              const nextTab = normalizeTab(value)
              setTab(nextTab)
              writeSearch({
                tab: nextTab === 'conversations' ? undefined : nextTab,
                page: undefined,
                state: undefined,
              })
            }}
          >
            <TabsList>
              <TabsTrigger value='conversations'>Conversations</TabsTrigger>
              <TabsTrigger value='insights'>Insight Buffers</TabsTrigger>
              <TabsTrigger value='groups'>Insight Buffer Groups</TabsTrigger>
            </TabsList>

            <TabsContent value='conversations'>
              <ConversationBuffersPanel
                search={search}
                memoryId={memoryId}
                onSearchChange={writeSearch}
              />
            </TabsContent>
            <TabsContent value='insights'>
              <InsightBuffersPanel
                search={search}
                memoryId={memoryId}
                onSearchChange={writeSearch}
              />
            </TabsContent>
            <TabsContent value='groups'>
              <InsightGroupsPanel search={search} memoryId={memoryId} />
            </TabsContent>
          </Tabs>
        </div>
      </Main>
    </>
  )
}

function ConversationBuffersPanel({
  search,
  memoryId,
  onSearchChange,
}: {
  search: BuffersSearch
  memoryId: string
  onSearchChange: (patch: Record<string, string | number | undefined>) => void
}) {
  const queryClient = useQueryClient()
  const [selectedIds, setSelectedIds] = useState<Set<number>>(() => new Set())
  const [deleteOpen, setDeleteOpen] = useState(false)
  const [detailId, setDetailId] = useState<number | null>(null)
  const state = normalizeConversationState(search.state)
  const params = useMemo(
    () => buildConversationParams(search, memoryId, state),
    [memoryId, search, state]
  )
  const conversationsQuery = useQuery({
    queryKey: ['buffers', 'conversations', params],
    queryFn: () => listConversationBuffers(params),
  })
  const markExtractedMutation = useMutation({
    mutationFn: (ids: number[]) => markConversationsExtracted(ids),
    onSuccess: async () => {
      setSelectedIds(new Set())
      await invalidateBuffers(queryClient)
    },
  })
  const deleteMutation = useMutation({
    mutationFn: (ids: number[]) => deleteConversationBuffers(ids),
    onSuccess: async () => {
      setSelectedIds(new Set())
      await invalidateBuffers(queryClient)
    },
  })

  const rows = conversationsQuery.data?.items ?? []
  const selectedConversationIds = [...selectedIds]

  return (
    <div className='flex flex-col gap-4'>
      <div className='flex flex-wrap items-end justify-between gap-3'>
        <div className='flex items-center gap-2'>
          <Label htmlFor='conversation-buffer-state'>
            Conversation buffer state
          </Label>
          <select
            id='conversation-buffer-state'
            aria-label='Conversation buffer state'
            value={state}
            className='h-9 rounded-md border bg-background px-3 text-sm'
            onChange={(event) =>
              onSearchChange({
                page: undefined,
                state:
                  event.target.value === DEFAULT_CONVERSATION_STATE
                    ? undefined
                    : event.target.value,
              })
            }
          >
            <option value='pending'>pending</option>
            <option value='extracted'>extracted</option>
            <option value='all'>all</option>
          </select>
        </div>

        <div className='flex flex-wrap gap-2'>
          <Button
            type='button'
            variant='outline'
            disabled={selectedIds.size === 0 || markExtractedMutation.isPending}
            onClick={() => markExtractedMutation.mutate(selectedConversationIds)}
          >
            Mark extracted
          </Button>
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
      </div>

      {conversationsQuery.isLoading ? <TableLoading columns={11} /> : null}
      {conversationsQuery.isError ? (
        <PageError
          message='Unable to load conversation buffers.'
          onRetry={conversationsQuery.refetch}
        />
      ) : null}
      {conversationsQuery.data && rows.length === 0 ? (
        <EmptyState title='No conversation buffers found.' />
      ) : null}
      {rows.length > 0 ? (
        <ConversationBuffersTable
          rows={rows}
          selectedIds={selectedIds}
          onToggleSelected={(id, checked) => {
            setSelectedIds((current) => toggleId(current, id, checked))
          }}
          onView={setDetailId}
        />
      ) : null}
      {conversationsQuery.data ? (
        <p className='text-sm text-muted-foreground'>
          Total rows: {conversationsQuery.data.page.totalItems}
        </p>
      ) : null}

      <ConfirmResultDialog
        open={deleteOpen}
        onOpenChange={setDeleteOpen}
        title='Delete selected conversation buffers'
        selectedCount={selectedConversationIds.length}
        description='Only the selected conversation buffer ids will be sent to the server.'
        isPending={deleteMutation.isPending}
        result={deleteMutation.data ? { ...deleteMutation.data } : null}
        onConfirm={() => deleteMutation.mutate(selectedConversationIds)}
      />

      <ConversationDetailDialog
        id={detailId}
        onOpenChange={(open) => {
          if (!open) setDetailId(null)
        }}
      />
    </div>
  )
}

function InsightBuffersPanel({
  search,
  memoryId,
  onSearchChange,
}: {
  search: BuffersSearch
  memoryId: string
  onSearchChange: (patch: Record<string, string | number | undefined>) => void
}) {
  const queryClient = useQueryClient()
  const [selectedIds, setSelectedIds] = useState<Set<number>>(() => new Set())
  const [deleteOpen, setDeleteOpen] = useState(false)
  const [groupName, setGroupName] = useState('')
  const state = normalizeInsightState(search.state)
  const params = useMemo(
    () => buildInsightParams(search, memoryId, state),
    [memoryId, search, state]
  )
  const insightsQuery = useQuery({
    queryKey: ['buffers', 'insights', params],
    queryFn: () => listInsightBuffers(params),
  })
  const builtMutation = useMutation({
    mutationFn: (request: { ids: number[]; built: boolean }) =>
      updateInsightBufferBuilt(request),
    onSuccess: async () => {
      setSelectedIds(new Set())
      await invalidateBuffers(queryClient)
    },
  })
  const groupMutation = useMutation({
    mutationFn: (request: { ids: number[]; groupName?: string | null }) =>
      updateInsightBufferGroup(request),
    onSuccess: async () => {
      setSelectedIds(new Set())
      await invalidateBuffers(queryClient)
    },
  })
  const deleteMutation = useMutation({
    mutationFn: (ids: number[]) => deleteInsightBuffers(ids),
    onSuccess: async () => {
      setSelectedIds(new Set())
      await invalidateBuffers(queryClient)
    },
  })

  const rows = insightsQuery.data?.items ?? []
  const selectedInsightIds = [...selectedIds]

  return (
    <div className='flex flex-col gap-4'>
      <div className='flex flex-wrap items-end justify-between gap-3'>
        <div className='flex items-center gap-2'>
          <Label htmlFor='insight-buffer-state'>Insight buffer state</Label>
          <select
            id='insight-buffer-state'
            aria-label='Insight buffer state'
            value={state}
            className='h-9 rounded-md border bg-background px-3 text-sm'
            onChange={(event) =>
              onSearchChange({
                page: undefined,
                state:
                  event.target.value === DEFAULT_INSIGHT_STATE
                    ? undefined
                    : event.target.value,
              })
            }
          >
            <option value='unbuilt'>unbuilt</option>
            <option value='ungrouped'>ungrouped</option>
            <option value='grouped'>grouped</option>
            <option value='built'>built</option>
            <option value='all'>all</option>
          </select>
        </div>

        <div className='flex flex-wrap items-center gap-2'>
          <Label htmlFor='insight-buffer-group-name' className='sr-only'>
            Group name
          </Label>
          <Input
            id='insight-buffer-group-name'
            value={groupName}
            placeholder='groupName'
            className='h-9 w-40'
            onChange={(event) => setGroupName(event.target.value)}
          />
          <Button
            type='button'
            variant='outline'
            disabled={selectedIds.size === 0 || groupMutation.isPending}
            onClick={() =>
              groupMutation.mutate({
                ids: selectedInsightIds,
                groupName: groupName.trim() || null,
              })
            }
          >
            Set group
          </Button>
          <Button
            type='button'
            variant='outline'
            disabled={selectedIds.size === 0 || builtMutation.isPending}
            onClick={() =>
              builtMutation.mutate({ ids: selectedInsightIds, built: true })
            }
          >
            Mark built
          </Button>
          <Button
            type='button'
            variant='outline'
            disabled={selectedIds.size === 0 || builtMutation.isPending}
            onClick={() =>
              builtMutation.mutate({ ids: selectedInsightIds, built: false })
            }
          >
            Mark unbuilt
          </Button>
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
      </div>

      {insightsQuery.isLoading ? <TableLoading columns={8} /> : null}
      {insightsQuery.isError ? (
        <PageError
          message='Unable to load insight buffers.'
          onRetry={insightsQuery.refetch}
        />
      ) : null}
      {insightsQuery.data && rows.length === 0 ? (
        <EmptyState title='No insight buffers found.' />
      ) : null}
      {rows.length > 0 ? (
        <InsightBuffersTable
          rows={rows}
          selectedIds={selectedIds}
          onToggleSelected={(id, checked) => {
            setSelectedIds((current) => toggleId(current, id, checked))
          }}
        />
      ) : null}
      {insightsQuery.data ? (
        <p className='text-sm text-muted-foreground'>
          Total rows: {insightsQuery.data.page.totalItems}
        </p>
      ) : null}

      <ConfirmResultDialog
        open={deleteOpen}
        onOpenChange={setDeleteOpen}
        title='Delete selected insight buffers'
        selectedCount={selectedInsightIds.length}
        description='Only the selected insight buffer ids will be sent to the server.'
        isPending={deleteMutation.isPending}
        result={deleteMutation.data ? { ...deleteMutation.data } : null}
        onConfirm={() => deleteMutation.mutate(selectedInsightIds)}
      />
    </div>
  )
}

function InsightGroupsPanel({
  search,
  memoryId,
}: {
  search: BuffersSearch
  memoryId: string
}) {
  const params = useMemo(
    () => ({
      memoryId: memoryId || undefined,
      insightTypeName: search.insightTypeName,
    }),
    [memoryId, search.insightTypeName]
  )
  const groupsQuery = useQuery({
    queryKey: ['buffers', 'insight-groups', params],
    queryFn: () => listInsightBufferGroups(params),
  })
  const rows = groupsQuery.data ?? []

  return (
    <div className='flex flex-col gap-4'>
      {groupsQuery.isLoading ? <TableLoading columns={6} /> : null}
      {groupsQuery.isError ? (
        <PageError
          message='Unable to load insight buffer groups.'
          onRetry={groupsQuery.refetch}
        />
      ) : null}
      {groupsQuery.data && rows.length === 0 ? (
        <EmptyState title='No insight buffer groups found.' />
      ) : null}
      {rows.length > 0 ? <InsightGroupsTable rows={rows} /> : null}
    </div>
  )
}

function ConversationDetailDialog({
  id,
  onOpenChange,
}: {
  id: number | null
  onOpenChange: (open: boolean) => void
}) {
  const detailQuery = useQuery({
    queryKey: ['buffers', 'conversations', id, 'detail'],
    enabled: id !== null,
    queryFn: () => getConversationBuffer(id as number),
  })
  const conversation = detailQuery.data

  return (
    <Dialog open={id !== null} onOpenChange={onOpenChange}>
      <DialogContent className='max-h-[85vh] overflow-auto sm:max-w-3xl'>
        <DialogHeader>
          <DialogTitle>Conversation buffer {id}</DialogTitle>
          <DialogDescription>{conversation?.memoryId ?? ''}</DialogDescription>
        </DialogHeader>

        {detailQuery.isLoading ? <PageLoading /> : null}
        {detailQuery.isError ? (
          <PageError
            message='Unable to load conversation buffer detail.'
            onRetry={detailQuery.refetch}
          />
        ) : null}
        {conversation ? <ConversationDetail conversation={conversation} /> : null}
      </DialogContent>
    </Dialog>
  )
}

function ConversationDetail({
  conversation,
}: {
  conversation: ConversationBufferView
}) {
  return (
    <div className='flex flex-col gap-5 text-sm'>
      <section className='flex flex-col gap-2'>
        <h3 className='font-medium'>Content</h3>
        <p className='whitespace-pre-wrap rounded-md bg-muted p-3'>
          {conversation.content}
        </p>
      </section>

      <div className='grid gap-2 md:grid-cols-2'>
        <p>sessionId: {fieldValue(conversation.sessionId)}</p>
        <p>memoryId: {conversation.memoryId}</p>
        <p>role: {fieldValue(conversation.role)}</p>
        <p>userName: {fieldValue(conversation.userName)}</p>
        <p>sourceClient: {fieldValue(conversation.sourceClient)}</p>
        <p>extracted: {conversation.extracted ? 'yes' : 'no'}</p>
        <p>timestamp: {formatDateTime(conversation.timestamp)}</p>
        <p>createdAt: {formatDateTime(conversation.createdAt)}</p>
        <p>updatedAt: {formatDateTime(conversation.updatedAt)}</p>
      </div>
    </div>
  )
}

function buildConversationParams(
  search: BuffersSearch,
  memoryId: string,
  state: ConversationBufferState
): ConversationBufferListParams {
  return {
    page: search.page,
    pageSize: search.pageSize,
    memoryId: memoryId || undefined,
    sessionId: search.sessionId,
    state,
  }
}

function buildInsightParams(
  search: BuffersSearch,
  memoryId: string,
  state: InsightBufferState
): InsightBufferListParams {
  return {
    page: search.page,
    pageSize: search.pageSize,
    memoryId: memoryId || undefined,
    insightTypeName: search.insightTypeName,
    state,
  }
}

type BuffersSearch = {
  page: number
  pageSize: number
  sessionId?: string
  insightTypeName?: string
  state?: string
}

function readBuffersSearch(): BuffersSearch {
  const params = readSearchParams()
  return {
    page: readNumberParam(params, 'page', DEFAULT_PAGE),
    pageSize: readNumberParam(params, 'pageSize', DEFAULT_PAGE_SIZE),
    sessionId: readStringParam(params, 'sessionId'),
    insightTypeName: readStringParam(params, 'insightTypeName'),
    state: readStringParam(params, 'state'),
  }
}

function readTabFromLocation(): BufferTab {
  return normalizeTab(readSearchParams().get('tab') ?? '')
}

function normalizeTab(value: string): BufferTab {
  if (value === 'insights' || value === 'groups') return value
  return 'conversations'
}

function normalizeConversationState(
  state: string | undefined
): ConversationBufferState {
  if (state === 'extracted' || state === 'all') return state
  return DEFAULT_CONVERSATION_STATE
}

function normalizeInsightState(state: string | undefined): InsightBufferState {
  if (
    state === 'ungrouped' ||
    state === 'grouped' ||
    state === 'built' ||
    state === 'all'
  ) {
    return state
  }
  return DEFAULT_INSIGHT_STATE
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

async function invalidateBuffers(queryClient: ReturnType<typeof useQueryClient>) {
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: ['buffers'] }),
    queryClient.invalidateQueries({ queryKey: ['dashboard'] }),
  ])
}

function fieldValue(value: unknown) {
  if (value === null || value === undefined || value === '') return '-'
  return String(value)
}
