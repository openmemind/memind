import { apiGet } from '@/lib/api-client'
import type { AdminDashboardView } from '@/features/types'
import type { MemoryIdFilter } from './common'

type DashboardParams = MemoryIdFilter & {
  days?: number
}

export function getDashboard(params: DashboardParams = {}) {
  return apiGet<AdminDashboardView>('/admin/v1/dashboard', params)
}
