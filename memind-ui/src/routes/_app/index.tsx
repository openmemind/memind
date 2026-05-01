import { createFileRoute } from '@tanstack/react-router'
import { DashboardPage } from '@/features/dashboard'

type DashboardSearch = {
  memoryId?: string
  days?: number
}

export const Route = createFileRoute('/_app/')({
  validateSearch: (search): DashboardSearch => ({
    memoryId: readString(search.memoryId),
    days: readNumber(search.days),
  }),
  component: DashboardPage,
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
