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

import { createFileRoute } from '@tanstack/react-router'
import { BuffersPage } from '@/features/buffers'

type BuffersSearch = {
  memoryId?: string
  tab?: string
  page?: number
  pageSize?: number
  sessionId?: string
  insightTypeName?: string
  state?: string
}

export const Route = createFileRoute('/_app/buffers')({
  validateSearch: (search): BuffersSearch => ({
    memoryId: readString(search.memoryId),
    tab: readString(search.tab),
    page: readNumber(search.page),
    pageSize: readNumber(search.pageSize),
    sessionId: readString(search.sessionId),
    insightTypeName: readString(search.insightTypeName),
    state: readString(search.state),
  }),
  component: BuffersPage,
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
