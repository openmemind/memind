import { apiGet, apiPost, type PageResult } from '@/lib/api-client'
import type {
  AdminMemoryThreadItemView,
  AdminMemoryThreadStatusView,
  AdminMemoryThreadView,
} from '@/features/types'
import type { PageParams, UserAgentFilter } from './common'

export type MemoryThreadStatus = 'ACTIVE' | 'DORMANT' | 'CLOSED'

export type MemoryThreadListParams = PageParams &
  UserAgentFilter & {
    status?: MemoryThreadStatus
  }

type RequiredUserScope = Required<Pick<UserAgentFilter, 'userId'>> &
  Pick<UserAgentFilter, 'agentId'>

export function listMemoryThreads(params: MemoryThreadListParams = {}) {
  return apiGet<PageResult<AdminMemoryThreadView>>(
    '/admin/v1/memory-threads',
    params
  )
}

export function getMemoryThread(threadKey: string, params: RequiredUserScope) {
  return apiGet<AdminMemoryThreadView>(
    `/admin/v1/memory-threads/${encodeURIComponent(threadKey)}`,
    params
  )
}

export function listMemoryThreadItems(
  threadKey: string,
  params: RequiredUserScope
) {
  return apiGet<AdminMemoryThreadItemView[]>(
    `/admin/v1/memory-threads/${encodeURIComponent(threadKey)}/items`,
    params
  )
}

export function getMemoryThreadStatus(params: RequiredUserScope) {
  return apiGet<AdminMemoryThreadStatusView>(
    '/admin/v1/memory-threads/status',
    params
  )
}

export function rebuildMemoryThreads(params: RequiredUserScope) {
  return apiPost<number>('/admin/v1/memory-threads/rebuild', undefined, params)
}
