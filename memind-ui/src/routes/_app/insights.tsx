import { createFileRoute } from '@tanstack/react-router'
import { InsightsPage } from '@/features/insights'

type InsightsSearch = {
  memoryId?: string
  pageNo?: number
  pageSize?: number
  scope?: string
  type?: string
  tier?: string
}

export const Route = createFileRoute('/_app/insights')({
  validateSearch: (search): InsightsSearch => ({
    memoryId: readString(search.memoryId),
    pageNo: readNumber(search.pageNo),
    pageSize: readNumber(search.pageSize),
    scope: readString(search.scope),
    type: readString(search.type),
    tier: readString(search.tier),
  }),
  component: InsightsPage,
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
