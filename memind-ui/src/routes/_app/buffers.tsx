import { createFileRoute } from '@tanstack/react-router'
import { BuffersPage } from '@/features/buffers'

type BuffersSearch = {
  memoryId?: string
  tab?: string
  pageNo?: number
  pageSize?: number
  sessionId?: string
  insightTypeName?: string
  state?: string
}

export const Route = createFileRoute('/_app/buffers')({
  validateSearch: (search): BuffersSearch => ({
    memoryId: readString(search.memoryId),
    tab: readString(search.tab),
    pageNo: readNumber(search.pageNo),
    pageSize: readNumber(search.pageSize),
    sessionId: readString(search.sessionId),
    insightTypeName: readString(search.insightTypeName),
    state: readString(search.state),
  }),
  component: BuffersPage,
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
