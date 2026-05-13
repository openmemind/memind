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

import { AlertCircle } from 'lucide-react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'

export function PageLoading() {
  return (
    <div className='flex flex-col gap-4'>
      <Skeleton className='h-8 w-48' />
      <Skeleton className='h-40 w-full' />
    </div>
  )
}

export function TableLoading({ columns }: { columns: number }) {
  return (
    <div className='flex flex-col gap-2' aria-label='Loading table'>
      {Array.from({ length: 5 }).map((_, rowIndex) => (
        <div
          key={rowIndex}
          className='grid gap-2'
          style={{ gridTemplateColumns: `repeat(${columns}, minmax(0, 1fr))` }}
        >
          {Array.from({ length: columns }).map((__, columnIndex) => (
            <Skeleton
              key={columnIndex}
              className='h-8 w-full'
            />
          ))}
        </div>
      ))}
    </div>
  )
}

export function EmptyState({
  title,
  description,
}: {
  title: string
  description?: string
}) {
  return (
    <Alert>
      <AlertTitle>{title}</AlertTitle>
      {description ? <AlertDescription>{description}</AlertDescription> : null}
    </Alert>
  )
}

export function PageError({
  message,
  requestId,
  onRetry,
}: {
  message: string
  requestId?: string
  onRetry?: () => void
}) {
  return (
    <Alert variant='destructive'>
      <AlertCircle aria-hidden='true' />
      <AlertTitle>Request failed</AlertTitle>
      <AlertDescription>
        <div className='flex flex-col gap-3'>
          <p>{message}</p>
          {requestId ? <p>requestId: {requestId}</p> : null}
          {onRetry ? (
            <Button type='button' variant='outline' size='sm' onClick={onRetry}>
              Retry
            </Button>
          ) : null}
        </div>
      </AlertDescription>
    </Alert>
  )
}

export function ScopeRequiredState({ message }: { message: string }) {
  return (
    <Alert>
      <AlertTitle>Scope required</AlertTitle>
      <AlertDescription>{message}</AlertDescription>
    </Alert>
  )
}
