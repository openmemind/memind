import { createFileRoute } from '@tanstack/react-router'
import { MemoryThreadsPage } from '@/features/memory-threads'

type MemoryThreadsSearch = {
  memoryId?: string
  pageNo?: number
  pageSize?: number
  status?: string
  focus?: string
}

export const Route = createFileRoute('/_app/memory-threads')({
  validateSearch: (search): MemoryThreadsSearch => ({
    memoryId: readString(search.memoryId),
    pageNo: readNumber(search.pageNo),
    pageSize: readNumber(search.pageSize),
    status: readString(search.status),
    focus: readString(search.focus),
  }),
  component: MemoryThreadsPage,
})

function readString(value: unknown) {
  return typeof value === 'string' && value.trim() !== '' ? value : undefined
}

function readNumber(value: unknown) {
  if (typeof value === 'number' && Number.isFinite(value)) return value
  if (typeof value === 'string' && value.trim() !== '') {
    const parsed = Number(value)
    return Number.isFinite(parsed) ? parsed : undefined
  }
  return undefined
}
