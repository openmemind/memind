import { apiPost } from '@/lib/api-client'
import type {
  RetrieveMemoryRequest,
  RetrieveMemoryResponse,
} from '@/features/types'

export function retrieveMemory(request: RetrieveMemoryRequest) {
  return apiPost<RetrieveMemoryResponse>('/open/v1/memory/retrieve', request)
}
