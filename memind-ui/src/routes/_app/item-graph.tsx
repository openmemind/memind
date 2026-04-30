import { createFileRoute } from '@tanstack/react-router'
import { ItemGraphPage } from '@/features/item-graph'

type ItemGraphSearch = {
  memoryId?: string
  tab?: string
  pageNo?: number
  pageSize?: number
  entityType?: string
  entityKey?: string
  itemId?: number
  linkType?: string
  evidenceSource?: string
  state?: string
  q?: string
}

export const Route = createFileRoute('/_app/item-graph')({
  validateSearch: (search): ItemGraphSearch => ({
    memoryId: readString(search.memoryId),
    tab: readString(search.tab),
    pageNo: readNumber(search.pageNo),
    pageSize: readNumber(search.pageSize),
    entityType: readString(search.entityType),
    entityKey: readString(search.entityKey),
    itemId: readNumber(search.itemId),
    linkType: readString(search.linkType),
    evidenceSource: readString(search.evidenceSource),
    state: readString(search.state),
    q: readString(search.q),
  }),
  component: ItemGraphPage,
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
