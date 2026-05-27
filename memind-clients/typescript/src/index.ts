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

export { VERSION } from './version.js'
export { MemindClient } from './client.js'
export type { MemindClientOptions } from './client.js'
export { Role, Strategy } from './types/common.js'
export type { ApiError, ApiResult, RequestOptions } from './types/common.js'
export type { HealthResponse } from './types/health.js'
export { Message, RawContent } from './types/message.js'
export type {
  AgentTimelineContent,
  AgentTimelineEvent,
  ContentBlock,
  ConversationContent,
  JsonObjectRawContent,
  MessageValue,
  RawContentValue,
} from './types/message.js'
export type {
  AddMessageRequest,
  AddMessageResponse,
  CommitMemoryRequest,
  ExtractMemoryRequest,
  ExtractMemoryResponse,
  MemoryItem,
  MemoryRawData,
  MetadataCondition,
  MetadataFilter,
  QueryMemoryItemsRequest,
  QueryMemoryItemsResponse,
  QueryMemoryRawDataRequest,
  QueryMemoryRawDataResponse,
  RawDataQueryIncludeOptions,
  RetrievalTraceView,
  RetrieveIncludeOptions,
  RetrieveMemoryRequest,
  RetrieveMemoryResponse,
  RetrievedInsight,
  RetrievedItem,
  RetrievedRawData,
  TimeRange,
} from './types/memory.js'
export {
  MemindAPIError,
  MemindAuthenticationError,
  MemindConnectionError,
  MemindError,
  MemindRateLimitError,
  MemindTimeoutError,
} from './core/errors.js'
