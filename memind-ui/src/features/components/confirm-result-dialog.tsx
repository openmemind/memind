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

import {
  AlertDialog,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'

type ConfirmResultDialogProps = {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: string
  selectedCount: number
  description: string
  cascadeWarning?: string
  confirmText?: string
  isPending?: boolean
  result?: Record<string, unknown> | null
  onConfirm: () => void
}

export function ConfirmResultDialog({
  open,
  onOpenChange,
  title,
  selectedCount,
  description,
  cascadeWarning,
  confirmText = 'Delete',
  isPending = false,
  result,
  onConfirm,
}: ConfirmResultDialogProps) {
  return (
    <AlertDialog open={open} onOpenChange={onOpenChange}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>{title}</AlertDialogTitle>
          <AlertDialogDescription asChild>
            <div className='flex flex-col gap-3'>
              <p>{selectedCount} selected rows</p>
              <p>{description}</p>
            </div>
          </AlertDialogDescription>
        </AlertDialogHeader>

        {cascadeWarning ? (
          <Alert variant='destructive'>
            <AlertTitle>Cascading delete</AlertTitle>
            <AlertDescription>{cascadeWarning}</AlertDescription>
          </Alert>
        ) : null}

        {result ? (
          <div className='flex flex-col gap-2 text-sm'>
            <p className='font-medium'>Result</p>
            <ul className='flex flex-col gap-1 text-muted-foreground'>
              {summarizeResult(result).map(({ key, value }) => (
                <li key={key}>
                  {key}: {value}
                </li>
              ))}
            </ul>
          </div>
        ) : null}

        <AlertDialogFooter>
          <AlertDialogCancel disabled={isPending}>Cancel</AlertDialogCancel>
          <Button
            type='button'
            variant='destructive'
            disabled={isPending || selectedCount < 1}
            onClick={onConfirm}
          >
            {confirmText}
          </Button>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}

function summarizeResult(result: Record<string, unknown>) {
  return Object.entries(result).map(([key, value]) => ({
    key,
    value: Array.isArray(value) ? value.length : String(value),
  }))
}
