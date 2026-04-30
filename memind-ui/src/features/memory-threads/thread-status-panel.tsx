import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  AlertDialog,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import { Button } from '@/components/ui/button'
import {
  getMemoryThreadStatus,
  rebuildMemoryThreads,
} from '@/features/api/memory-threads'
import {
  PageError,
  PageLoading,
  ScopeRequiredState,
} from '@/features/components/data-state'
import type { AdminMemoryThreadStatusView } from '@/features/types'
import { formatDateTime } from '@/lib/format'
import { parseMemoryScope } from '@/lib/memory-scope'

export function ThreadStatusPanel({ memoryScope }: { memoryScope: string }) {
  const queryClient = useQueryClient()
  const [confirmOpen, setConfirmOpen] = useState(false)
  const scope = parseMemoryScope(memoryScope)
  const statusQuery = useQuery({
    queryKey: [
      'memory-threads',
      'status',
      scope.userId,
      scope.agentId,
      scope.hasAgentId,
    ],
    enabled: scope.hasUserId,
    queryFn: () =>
      getMemoryThreadStatus({
        userId: scope.userId,
        ...(scope.hasAgentId ? { agentId: scope.agentId } : {}),
      }),
  })
  const rebuildMutation = useMutation({
    mutationFn: () =>
      rebuildMemoryThreads({
        userId: scope.userId,
        ...(scope.hasAgentId ? { agentId: scope.agentId } : {}),
      }),
    onSuccess: async () => {
      setConfirmOpen(false)
      await queryClient.invalidateQueries({ queryKey: ['memory-threads'] })
    },
  })

  return (
    <section id='thread-status' className='flex flex-col gap-3'>
      <div className='flex flex-wrap items-center justify-between gap-3'>
        <h3 className='text-lg font-semibold'>Thread Projection Status</h3>
        <Button
          type='button'
          variant='outline'
          disabled={!scope.hasUserId || rebuildMutation.isPending}
          onClick={() => setConfirmOpen(true)}
        >
          Rebuild projections
        </Button>
      </div>

      {!scope.hasUserId ? (
        <ScopeRequiredState message='Set memory scope with userId to load thread status.' />
      ) : null}
      {statusQuery.isLoading ? <PageLoading /> : null}
      {statusQuery.isError ? (
        <PageError
          message='Unable to load thread projection status.'
          onRetry={statusQuery.refetch}
        />
      ) : null}
      {statusQuery.data ? <StatusFields status={statusQuery.data} /> : null}

      <AlertDialog open={confirmOpen} onOpenChange={setConfirmOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Rebuild memory thread projections</AlertDialogTitle>
            <AlertDialogDescription>
              Rebuild projections for the active memory scope after server-side
              validation.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={rebuildMutation.isPending}>
              Cancel
            </AlertDialogCancel>
            <Button
              type='button'
              variant='destructive'
              disabled={rebuildMutation.isPending}
              onClick={() => rebuildMutation.mutate()}
            >
              Rebuild
            </Button>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </section>
  )
}

function StatusFields({ status }: { status: AdminMemoryThreadStatusView }) {
  return (
    <div className='grid gap-2 rounded-md border p-3 text-sm md:grid-cols-2'>
      <p>projectionState: {fieldValue(status.projectionState)}</p>
      <p>pendingCount: {status.pendingCount}</p>
      <p>failedCount: {status.failedCount}</p>
      <p>rebuildInProgress: {status.rebuildInProgress ? 'yes' : 'no'}</p>
      <p>lastProcessedItemId: {fieldValue(status.lastProcessedItemId)}</p>
      <p>
        materializationPolicyVersion:{' '}
        {fieldValue(status.materializationPolicyVersion)}
      </p>
      <p>updatedAt: {formatDateTime(status.updatedAt)}</p>
      <p>invalidationReason: {fieldValue(status.invalidationReason)}</p>
    </div>
  )
}

function fieldValue(value: unknown) {
  if (value === null || value === undefined || value === '') return '-'
  return String(value)
}
