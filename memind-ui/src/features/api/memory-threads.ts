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
