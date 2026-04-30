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
