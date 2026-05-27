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

import { MemindAPIError } from './errors.js'
import type { HealthResponse } from '../types/health.js'
import type {
  AddMessageResponse,
  ExtractMemoryResponse,
  FinalView,
  MemoryItem,
  MemoryRawData,
  MergeView,
  QueryMemoryItemsResponse,
  QueryMemoryRawDataResponse,
  RetrievalTraceView,
  RetrieveMemoryResponse,
  RetrievedInsight,
  RetrievedItem,
  RetrievedRawData,
  StageView,
} from '../types/memory.js'

function parseError(message: string): never {
  throw new MemindAPIError(message, { status: 0, errorCode: 'parse_error' })
}

function objectRecord(value: unknown, field: string): Record<string, unknown> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    parseError(`Expected object for "${field}"`)
  }
  return value as Record<string, unknown>
}

function assertString(value: unknown, field: string): string {
  if (typeof value !== 'string') {
    parseError(`Expected string for "${field}", got ${typeof value}`)
  }
  return value
}

function optionalString(value: unknown, field: string): string | undefined {
  if (value === undefined || value === null) return undefined
  return assertString(value, field)
}

function assertNumber(value: unknown, field: string): number {
  if (typeof value !== 'number' || !Number.isFinite(value)) {
    parseError(`Expected finite number for "${field}", got ${typeof value}`)
  }
  return value
}

function optionalNumber(value: unknown, field: string): number | undefined {
  if (value === undefined || value === null) return undefined
  return assertNumber(value, field)
}

function assertBoolean(value: unknown, field: string): boolean {
  if (typeof value !== 'boolean') {
    parseError(`Expected boolean for "${field}", got ${typeof value}`)
  }
  return value
}

function optionalBoolean(value: unknown, field: string): boolean | undefined {
  if (value === undefined || value === null) return undefined
  return assertBoolean(value, field)
}

function assertArray(value: unknown, field: string): unknown[] {
  if (value === null) {
    parseError(`Expected array for "${field}", got null`)
  }
  if (value === undefined) return []
  if (!Array.isArray(value)) {
    parseError(`Expected array for "${field}", got ${typeof value}`)
  }
  return value
}

function assertStringArray(value: unknown, field: string): string[] {
  return assertArray(value, field).map((item, index) => assertString(item, `${field}[${index}]`))
}

function assertNumberArray(value: unknown, field: string): number[] {
  return assertArray(value, field).map((item, index) => assertNumber(item, `${field}[${index}]`))
}

export function assertHealthResponse(data: unknown): HealthResponse {
  const obj = objectRecord(data, 'health')
  return {
    status: assertString(obj.status, 'status'),
    service: assertString(obj.service, 'service'),
  }
}

export function assertExtractMemoryResponse(data: unknown): ExtractMemoryResponse {
  const obj = objectRecord(data, 'extract')
  const response: ExtractMemoryResponse = {
    status: assertString(obj.status, 'status'),
    rawDataIds: assertStringArray(obj.rawDataIds, 'rawDataIds'),
    itemIds: assertNumberArray(obj.itemIds, 'itemIds'),
    insightIds: assertNumberArray(obj.insightIds, 'insightIds'),
    insightPending:
      obj.insightPending === undefined
        ? false
        : assertBoolean(obj.insightPending, 'insightPending'),
  }
  const durationMillis = optionalNumber(obj.durationMillis, 'durationMillis')
  if (durationMillis !== undefined) response.durationMillis = durationMillis
  const errorMessage = optionalString(obj.errorMessage, 'errorMessage')
  if (errorMessage !== undefined) response.errorMessage = errorMessage
  return response
}

export function assertAddMessageResponse(data: unknown): AddMessageResponse {
  const obj = objectRecord(data, 'addMessage')
  const response: AddMessageResponse = {
    triggered: assertBoolean(obj.triggered, 'triggered'),
  }
  if (obj.result !== undefined) {
    if (obj.result === null) {
      parseError('Expected object for "result", got null')
    }
    response.result = assertExtractMemoryResponse(obj.result)
  }
  return response
}

export function assertRetrieveMemoryResponse(data: unknown): RetrieveMemoryResponse {
  const obj = objectRecord(data, 'retrieve')
  const response: RetrieveMemoryResponse = {
    items: assertArray(obj.items, 'items').map(assertRetrievedItem),
    insights: assertArray(obj.insights, 'insights').map(assertRetrievedInsight),
    rawData: assertArray(obj.rawData, 'rawData').map(assertRetrievedRawData),
    evidences: assertStringArray(obj.evidences, 'evidences'),
  }
  const status = optionalString(obj.status, 'status')
  if (status !== undefined) response.status = status
  const strategy = optionalString(obj.strategy, 'strategy')
  if (strategy !== undefined) response.strategy = strategy
  const query = optionalString(obj.query, 'query')
  if (query !== undefined) response.query = query
  if (obj.trace !== undefined && obj.trace !== null) {
    response.trace = assertRetrievalTraceView(obj.trace)
  }
  return response
}

function assertRetrievedItem(value: unknown, index: number): RetrievedItem {
  const obj = objectRecord(value, `items[${index}]`)
  const item: RetrievedItem = {
    id: assertString(obj.id, `items[${index}].id`),
    text: assertString(obj.text, `items[${index}].text`),
    vectorScore:
      obj.vectorScore === undefined
        ? 0
        : assertNumber(obj.vectorScore, `items[${index}].vectorScore`),
    finalScore:
      obj.finalScore === undefined ? 0 : assertNumber(obj.finalScore, `items[${index}].finalScore`),
  }
  const occurredAt = optionalString(obj.occurredAt, `items[${index}].occurredAt`)
  if (occurredAt !== undefined) item.occurredAt = occurredAt
  const category = optionalString(obj.category, `items[${index}].category`)
  if (category !== undefined) item.category = category
  if (obj.metadata !== undefined && obj.metadata !== null) {
    item.metadata = objectRecord(obj.metadata, `items[${index}].metadata`)
  }
  return item
}

function assertRetrievedInsight(value: unknown, index: number): RetrievedInsight {
  const obj = objectRecord(value, `insights[${index}]`)
  const insight: RetrievedInsight = {
    id: assertString(obj.id, `insights[${index}].id`),
    text: assertString(obj.text, `insights[${index}].text`),
  }
  const tier = optionalString(obj.tier, `insights[${index}].tier`)
  if (tier !== undefined) insight.tier = tier
  return insight
}

function assertRetrievedRawData(value: unknown, index: number): RetrievedRawData {
  const obj = objectRecord(value, `rawData[${index}]`)
  const rawData: RetrievedRawData = {
    rawDataId: assertString(obj.rawDataId, `rawData[${index}].rawDataId`),
    maxScore:
      obj.maxScore === undefined ? 0 : assertNumber(obj.maxScore, `rawData[${index}].maxScore`),
  }
  const caption = optionalString(obj.caption, `rawData[${index}].caption`)
  if (caption !== undefined) rawData.caption = caption
  if (obj.itemIds !== undefined && obj.itemIds !== null) {
    rawData.itemIds = assertStringArray(obj.itemIds, `rawData[${index}].itemIds`)
  }
  for (const key of ['type', 'sourceClient', 'startTime', 'endTime', 'createdAt'] as const) {
    const value = optionalString(obj[key], `rawData[${index}].${key}`)
    if (value !== undefined) rawData[key] = value
  }
  if (obj.metadata !== undefined && obj.metadata !== null) {
    rawData.metadata = objectRecord(obj.metadata, `rawData[${index}].metadata`)
  }
  return rawData
}

export function assertQueryMemoryItemsResponse(data: unknown): QueryMemoryItemsResponse {
  const obj = objectRecord(data, 'queryItems')
  const response: QueryMemoryItemsResponse = {
    items: assertArray(obj.items, 'items').map(assertMemoryItem),
  }
  const nextCursor = optionalString(obj.nextCursor, 'nextCursor')
  if (nextCursor !== undefined) response.nextCursor = nextCursor
  return response
}

function assertMemoryItem(value: unknown, index: number): MemoryItem {
  const obj = objectRecord(value, `items[${index}]`)
  const item: MemoryItem = {
    id: assertString(obj.id, `items[${index}].id`),
    text: assertString(obj.text, `items[${index}].text`),
  }
  for (const key of [
    'scope',
    'category',
    'type',
    'rawDataId',
    'rawDataType',
    'sourceClient',
    'occurredAt',
    'observedAt',
    'createdAt',
  ] as const) {
    const value = optionalString(obj[key], `items[${index}].${key}`)
    if (value !== undefined) item[key] = value
  }
  if (obj.metadata !== undefined && obj.metadata !== null) {
    item.metadata = objectRecord(obj.metadata, `items[${index}].metadata`)
  }
  return item
}

export function assertQueryMemoryRawDataResponse(data: unknown): QueryMemoryRawDataResponse {
  const obj = objectRecord(data, 'queryRawData')
  const response: QueryMemoryRawDataResponse = {
    rawData: assertArray(obj.rawData, 'rawData').map(assertMemoryRawData),
  }
  const nextCursor = optionalString(obj.nextCursor, 'nextCursor')
  if (nextCursor !== undefined) response.nextCursor = nextCursor
  return response
}

function assertMemoryRawData(value: unknown, index: number): MemoryRawData {
  const obj = objectRecord(value, `rawData[${index}]`)
  const rawData: MemoryRawData = {
    id: assertString(obj.id, `rawData[${index}].id`),
  }
  for (const key of [
    'type',
    'sourceClient',
    'caption',
    'startTime',
    'endTime',
    'createdAt',
  ] as const) {
    const value = optionalString(obj[key], `rawData[${index}].${key}`)
    if (value !== undefined) rawData[key] = value
  }
  if (obj.metadata !== undefined && obj.metadata !== null) {
    rawData.metadata = objectRecord(obj.metadata, `rawData[${index}].metadata`)
  }
  if (obj.segment !== undefined && obj.segment !== null) {
    rawData.segment = objectRecord(obj.segment, `rawData[${index}].segment`)
  }
  return rawData
}

function assertRetrievalTraceView(value: unknown): RetrievalTraceView {
  const obj = objectRecord(value, 'trace')
  const trace: RetrievalTraceView = {
    stages: assertArray(obj.stages, 'trace.stages').map(assertStageView),
  }
  const traceId = optionalString(obj.traceId, 'trace.traceId')
  if (traceId !== undefined) trace.traceId = traceId
  const startedAt = optionalString(obj.startedAt, 'trace.startedAt')
  if (startedAt !== undefined) trace.startedAt = startedAt
  const completedAt = optionalString(obj.completedAt, 'trace.completedAt')
  if (completedAt !== undefined) trace.completedAt = completedAt
  const truncated = optionalBoolean(obj.truncated, 'trace.truncated')
  if (truncated !== undefined) trace.truncated = truncated
  if (obj.merge !== undefined && obj.merge !== null) trace.merge = assertMergeView(obj.merge)
  if (obj.finalResults !== undefined && obj.finalResults !== null) {
    trace.finalResults = assertFinalView(obj.finalResults)
  }
  return trace
}

function assertStageView(value: unknown, index: number): StageView {
  const obj = objectRecord(value, `trace.stages[${index}]`)
  const stage: StageView = {
    degraded:
      obj.degraded === undefined
        ? false
        : assertBoolean(obj.degraded, `trace.stages[${index}].degraded`),
    skipped:
      obj.skipped === undefined
        ? false
        : assertBoolean(obj.skipped, `trace.stages[${index}].skipped`),
  }
  for (const key of ['stage', 'tier', 'method', 'status', 'startedAt'] as const) {
    const value = optionalString(obj[key], `trace.stages[${index}].${key}`)
    if (value !== undefined) stage[key] = value
  }
  for (const key of ['inputCount', 'candidateCount', 'resultCount', 'durationMillis'] as const) {
    const value = optionalNumber(obj[key], `trace.stages[${index}].${key}`)
    if (value !== undefined) stage[key] = value
  }
  if (obj.attributes !== undefined && obj.attributes !== null) {
    stage.attributes = objectRecord(obj.attributes, `trace.stages[${index}].attributes`)
  }
  if (obj.candidates !== undefined && obj.candidates !== null) {
    stage.candidates = assertArray(obj.candidates, `trace.stages[${index}].candidates`).map(
      (candidate, candidateIndex) =>
        objectRecord(candidate, `trace.stages[${index}].candidates[${candidateIndex}]`),
    )
  }
  return stage
}

function assertMergeView(value: unknown): MergeView {
  const obj = objectRecord(value, 'trace.merge')
  const merge: MergeView = {
    inputCount:
      obj.inputCount === undefined ? 0 : assertNumber(obj.inputCount, 'trace.merge.inputCount'),
    outputCount:
      obj.outputCount === undefined ? 0 : assertNumber(obj.outputCount, 'trace.merge.outputCount'),
    deduplicatedCount:
      obj.deduplicatedCount === undefined
        ? 0
        : assertNumber(obj.deduplicatedCount, 'trace.merge.deduplicatedCount'),
    sourceCount:
      obj.sourceCount === undefined ? 0 : assertNumber(obj.sourceCount, 'trace.merge.sourceCount'),
  }
  const status = optionalString(obj.status, 'trace.merge.status')
  if (status !== undefined) merge.status = status
  return merge
}

function assertFinalView(value: unknown): FinalView {
  const obj = objectRecord(value, 'trace.finalResults')
  const finalResults: FinalView = {
    itemCount:
      obj.itemCount === undefined ? 0 : assertNumber(obj.itemCount, 'trace.finalResults.itemCount'),
    insightCount:
      obj.insightCount === undefined
        ? 0
        : assertNumber(obj.insightCount, 'trace.finalResults.insightCount'),
    rawDataCount:
      obj.rawDataCount === undefined
        ? 0
        : assertNumber(obj.rawDataCount, 'trace.finalResults.rawDataCount'),
    evidenceCount:
      obj.evidenceCount === undefined
        ? 0
        : assertNumber(obj.evidenceCount, 'trace.finalResults.evidenceCount'),
  }
  const strategy = optionalString(obj.strategy, 'trace.finalResults.strategy')
  if (strategy !== undefined) finalResults.strategy = strategy
  const status = optionalString(obj.status, 'trace.finalResults.status')
  if (status !== undefined) finalResults.status = status
  return finalResults
}
