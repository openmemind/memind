import { apiGet, apiPut } from '@/lib/api-client'
import type {
  MemoryOptionsGetResponse,
  MemoryOptionsPutRequest,
} from '@/features/types'

const CONFIG_PATH = '/admin/v1/config/memory-options'

export function getMemoryOptions() {
  return apiGet<MemoryOptionsGetResponse>(CONFIG_PATH)
}

export function updateMemoryOptions(request: MemoryOptionsPutRequest) {
  return apiPut<MemoryOptionsGetResponse>(CONFIG_PATH, request)
}
