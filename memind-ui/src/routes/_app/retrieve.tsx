import { createFileRoute } from '@tanstack/react-router'
import { RetrievePage } from '@/features/retrieve'

type RetrieveSearch = {
  memoryId?: string
}

export const Route = createFileRoute('/_app/retrieve')({
  validateSearch: (search): RetrieveSearch => ({
    memoryId: readString(search.memoryId),
  }),
  component: RetrievePage,
})

function readString(value: unknown) {
  return typeof value === 'string' && value.trim() !== '' ? value : undefined
}
