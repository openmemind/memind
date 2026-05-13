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
  AdminItemMemoryThreadView,
  AdminItemView,
  BatchDeleteResult,
  ItemDeleteRequest,
} from '@/features/types'
import type { PageParams, UserAgentFilter } from './common'

export type ItemListParams = PageParams &
  UserAgentFilter & {
    scope?: string
    category?: string
    type?: string
    rawDataId?: string
  }

export function listItems(params: ItemListParams = {}) {
  return apiGet<PageResult<AdminItemView>>('/admin/v1/items', params)
}

export function getItem(itemId: number) {
  return apiGet<AdminItemView>(`/admin/v1/items/${itemId}`)
}

export function listItemMemoryThreads(
  itemId: number,
  params: Required<Pick<UserAgentFilter, 'userId'>> &
    Pick<UserAgentFilter, 'agentId'>
) {
  return apiGet<AdminItemMemoryThreadView[]>(
    `/admin/v1/items/${itemId}/memory-threads`,
    params
  )
}

export function deleteItems(itemIds: number[]) {
  const body: ItemDeleteRequest = { itemIds }
  return apiDelete<BatchDeleteResult>('/admin/v1/items', body)
}
