import { createFileRoute } from '@tanstack/react-router'
import { RawDataPage } from '@/features/raw-data'

type RawDataSearch = {
  memoryId?: string
  pageNo?: number
  pageSize?: number
  startTimeFrom?: string
  startTimeTo?: string
}

export const Route = createFileRoute('/_app/raw-data')({
  validateSearch: (search): RawDataSearch => ({
    memoryId: readString(search.memoryId),
    pageNo: readNumber(search.pageNo),
    pageSize: readNumber(search.pageSize),
    startTimeFrom: readString(search.startTimeFrom),
    startTimeTo: readString(search.startTimeTo),
  }),
  component: RawDataPage,
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
