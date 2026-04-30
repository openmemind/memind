import { createFileRoute } from '@tanstack/react-router'
import { ItemsPage } from '@/features/items'

type ItemsSearch = {
  memoryId?: string
  pageNo?: number
  pageSize?: number
  scope?: string
  category?: string
  type?: string
  rawDataId?: string
}

export const Route = createFileRoute('/_app/items')({
  validateSearch: (search): ItemsSearch => ({
    memoryId: readString(search.memoryId),
    pageNo: readNumber(search.pageNo),
    pageSize: readNumber(search.pageSize),
    scope: readString(search.scope),
    category: readString(search.category),
    type: readString(search.type),
    rawDataId: readString(search.rawDataId),
  }),
  component: ItemsPage,
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
