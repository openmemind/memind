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
import { ItemGraphPage } from '@/features/item-graph'

type ItemGraphSearch = {
  memoryId?: string
  tab?: string
  page?: number
  pageSize?: number
  entityType?: string
  entityKey?: string
  itemId?: number
  linkType?: string
  evidenceSource?: string
  state?: string
  q?: string
}

export const Route = createFileRoute('/_app/item-graph')({
  validateSearch: (search): ItemGraphSearch => ({
    memoryId: readString(search.memoryId),
    tab: readString(search.tab),
    page: readNumber(search.page),
    pageSize: readNumber(search.pageSize),
    entityType: readString(search.entityType),
    entityKey: readString(search.entityKey),
    itemId: readNumber(search.itemId),
    linkType: readString(search.linkType),
    evidenceSource: readString(search.evidenceSource),
    state: readString(search.state),
    q: readString(search.q),
  }),
  component: ItemGraphPage,
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
