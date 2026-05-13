//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

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
