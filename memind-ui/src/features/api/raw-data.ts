import { apiDelete, apiGet, type PageResult } from '@/lib/api-client'
import type {
  AdminRawDataView,
  RawDataDeleteRequest,
  RawDataDeleteResult,
} from '@/features/types'
import type { PageParams, UserAgentFilter } from './common'

export type RawDataListParams = PageParams &
  UserAgentFilter & {
    startTimeFrom?: string
    startTimeTo?: string
  }

export function listRawData(params: RawDataListParams = {}) {
  return apiGet<PageResult<AdminRawDataView>>('/admin/v1/raw-data', params)
}

export function getRawData(rawDataId: string) {
  return apiGet<AdminRawDataView>(
    `/admin/v1/raw-data/${encodeURIComponent(rawDataId)}`
  )
}

export function deleteRawData(rawDataIds: string[]) {
  const body: RawDataDeleteRequest = { rawDataIds }
  return apiDelete<RawDataDeleteResult>('/admin/v1/raw-data', body)
}
