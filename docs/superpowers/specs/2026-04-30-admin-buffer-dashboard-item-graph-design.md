# Admin Buffer, Dashboard, and Item Graph API Design

Date: 2026-04-30
Branch: optimize/memind-server

## Goal

Add three admin-facing API areas to `memind-server`:

- Buffer administration for conversation and insight buffers.
- A lightweight dashboard data API.
- Item graph inspection and correction APIs.

These APIs are intended for a management console and operational debugging. They should follow the existing server style: Spring MVC controllers under `/admin/v1`, `ApiResult<T>` responses, MyBatis Plus backed query services, existing pagination shapes, and existing exception handling.

## Scope

This design uses the "corrective management" scope:

- Provide read, paging, aggregation, and summary endpoints.
- Allow safe manual correction of existing persisted rows.
- Allow deletion of incorrect graph or buffer records.
- Do not trigger extraction replay, graph rebuild, graph batch retry, or automatic reprocessing.
- Do not add entity merge or hand-authored graph creation in the first version.

Out of scope:

- Graph rebuild APIs.
- Graph batch retry or discard APIs.
- Entity merge APIs.
- Manual creation of graph entities, aliases, mentions, links, or cooccurrences.
- A dashboard warning/rules engine.
- OpenAPI docs and auth changes.

## Existing Context

`memind-server` currently exposes:

- Open APIs under `/open/v1/memory`.
- Admin APIs under `/admin/v1/raw-data`, `/admin/v1/items`, `/admin/v1/insights`, `/admin/v1/memory-threads`, and `/admin/v1/config/memory-options`.

Admin APIs use:

- `ApiResult<T>` for response envelopes.
- `PageResult<T>`/`PageResponse<T>` for paging.
- Controller validation with `@Validated`, `@Min`, and `@Max`.
- Query service plus mapper patterns for DB reads.
- `ApiExceptionHandler` for 400, 404, 409, 503, and 500 responses.

The relevant persisted tables already exist in the MyBatis starter:

- `memory_conversation_buffer`
- `memory_insight_buffer`
- `memory_graph_entity`
- `memory_graph_entity_alias`
- `memory_item_entity_mention`
- `memory_item_link`
- `memory_entity_cooccurrence`
- `memory_item_graph_batch`

## Architecture

Add three controller groups:

- `AdminBufferController`
- `AdminDashboardController`
- `AdminItemGraphController`

Add domain records under new server domain packages:

- `domain.buffer`
- `domain.dashboard`
- `domain.itemgraph`

Add query and management services:

- `BufferQueryService`
- `BufferManagementService`
- `DashboardQueryService`
- `ItemGraphQueryService`
- `ItemGraphManagementService`

Add admin query mappers that depend on the existing MyBatis Plus mappers and data objects from `memind-plugin-mybatis-plus-starter`.

Add a small server-local scope helper for admin queries:

- Accept `memoryId` in public APIs for a compact management-console contract.
- Resolve `memoryId` into `userId` and `agentId` using the existing `DefaultMemoryId.toIdentifier()` convention: no colon means user-only memory, and the first colon separates user and agent.
- Prefer `user_id` and `agent_id` predicates in MyBatis queries when the scope is available, because the existing tables and indexes are mostly keyed by `(user_id, agent_id)`.
- Fall back to `memory_id` predicates only for graph tables or cases where that is already the strongest indexed lookup.
- Do not use `agent_id IS NULL` for user-only identifiers. The current SQL schema declares `agent_id` as `NOT NULL`; when a user-only memory is filtered through `(user_id, agent_id)`, use the stored empty-string agent key.

This keeps the API simple without forcing full table scans for common dashboard and buffer queries.

All list endpoints keep existing server paging behavior:

- `pageNo`, default `1`, minimum `1`.
- `pageSize`, default `20`, minimum `1`, maximum `100`.

Delete and patch endpoints use request bodies. Patch endpoints return `AdminUpdateResult(updatedCount, affectedMemoryIds)`. Delete endpoints return the existing `BatchDeleteResult(deletedCount, affectedMemoryIds)` unless the operation needs extra correction details. Entity deletion returns `AdminGraphEntityDeleteResult` because it reports the cascade counts and the number of surviving `entity_overlap` item links that may need explicit review.

Delete semantics must match each table's identity model:

- Conversation buffer rows do not have a business identity unique index and may be deleted through the normal MyBatis Plus logical-delete behavior.
- Insight buffer and item graph tables have unique identity indexes that do not include the `deleted` marker. Admin correction deletes for those tables must use explicit physical-delete mapper methods, or a documented revive/upsert path, so future extraction can recreate the same identity without hitting a tombstoned unique row.
- The first implementation should use physical deletes for insight buffer and item graph correction deletes. This keeps the admin behavior simple and matches the user's intent to remove incorrect intermediate graph or buffer facts.
- Existing raw-data/item/insight delete services are not changed by this design.

All write endpoints that update more than one table must execute inside one service-level transaction. This is required for item graph entity deletion, where entity, alias, mention, and cooccurrence rows must not be partially corrected.

## Buffer APIs

Base path:

`/admin/v1/buffers`

### Conversation Buffer List

`GET /admin/v1/buffers/conversations`

Query parameters:

- `memoryId`, optional.
- `sessionId`, optional.
- `state`, optional, default `pending`. Allowed values: `pending`, `extracted`, `all`.
- `pageNo`, optional, default `1`.
- `pageSize`, optional, default `20`, maximum `100`.

Sorting:

- `createdAt DESC, id DESC`.

Response item fields:

- `id`
- `sessionId`
- `userId`
- `agentId`
- `memoryId`
- `role`
- `content`
- `userName`
- `sourceClient`
- `timestamp`
- `extracted`
- `createdAt`
- `updatedAt`

### Conversation Buffer Detail

`GET /admin/v1/buffers/conversations/{id}`

Returns a single conversation buffer row. If the row does not exist, return the existing `not_found` error shape.

### Mark Conversation Rows Extracted

`PATCH /admin/v1/buffers/conversations/extracted`

Request:

```json
{
  "ids": [1, 2, 3]
}
```

Behavior:

- Set `extracted=true` for the requested rows.
- Do not support setting `extracted=false` in the first version. Reopening conversation rows can cause duplicated extraction and should be handled by a separate replay/rebuild design.

Response:

```json
{
  "updatedCount": 3,
  "affectedMemoryIds": ["user:agent"]
}
```

### Delete Conversation Rows

`DELETE /admin/v1/buffers/conversations`

Request:

```json
{
  "ids": [1, 2, 3]
}
```

Behavior:

- Soft-delete requested rows through the mapped MyBatis Plus table logic.
- Intended for test data, bad session writes, or clearly incorrect buffer entries.

Response:

- `BatchDeleteResult`.

### Insight Buffer List

`GET /admin/v1/buffers/insights`

Query parameters:

- `memoryId`, optional.
- `insightTypeName`, optional.
- `state`, optional, default `unbuilt`. Allowed values: `unbuilt`, `ungrouped`, `grouped`, `built`, `all`.
- `pageNo`, optional, default `1`.
- `pageSize`, optional, default `20`, maximum `100`.

State mapping:

- `unbuilt`: `built=false`.
- `ungrouped`: `built=false AND group_name IS NULL`.
- `grouped`: `built=false AND group_name IS NOT NULL`.
- `built`: `built=true`.
- `all`: no state filter.

Response item fields:

- `id`
- `userId`
- `agentId`
- `memoryId`
- `insightTypeName`
- `itemId`
- `groupName`
- `built`
- `createdAt`
- `updatedAt`

### Insight Buffer Groups

`GET /admin/v1/buffers/insights/groups`

Query parameters:

- `memoryId`, optional.
- `insightTypeName`, optional.

Response:

- Group rows keyed by `memoryId`, `insightTypeName`, and `groupName`.
- Include `total`, `unbuilt`, and `built` counts.
- Represent ungrouped entries with JSON `null` for `groupName`.

### Update Insight Buffer Group

`PATCH /admin/v1/buffers/insights/group`

Request:

```json
{
  "ids": [10, 11],
  "groupName": "project-preferences"
}
```

Behavior:

- Set `groupName` for the requested rows.
- Allow `groupName=null` to move rows back to ungrouped state.

Response:

```json
{
  "updatedCount": 2,
  "affectedMemoryIds": ["user:agent"]
}
```

### Update Insight Buffer Built State

`PATCH /admin/v1/buffers/insights/built`

Request:

```json
{
  "ids": [10, 11],
  "built": true
}
```

Behavior:

- Set `built` to the requested value.
- Allow both `true` and `false`. This is a corrective action for insight construction and is less risky than reopening conversation extraction.

Response:

```json
{
  "updatedCount": 2,
  "affectedMemoryIds": ["user:agent"]
}
```

### Delete Insight Buffer Rows

`DELETE /admin/v1/buffers/insights`

Request:

```json
{
  "ids": [10, 11]
}
```

Response:

- `BatchDeleteResult`.

Behavior:

- Physically delete requested rows with an explicit mapper method.
- Do not use MyBatis Plus logical delete here unless the implementation also revives matching tombstoned rows before future `InsightBuffer.append()` inserts. The default implementation should avoid that extra state by physically deleting correction targets.

## Dashboard API

Base path:

`/admin/v1/dashboard`

### Dashboard Overview

`GET /admin/v1/dashboard`

Query parameters:

- `memoryId`, optional. If absent, the dashboard is global.
- `days`, optional, default `7`, maximum `30`.

The endpoint is a data board, not a diagnostics engine. It returns stable aggregate sections that a management UI can render directly.

Response shape:

```json
{
  "totals": {
    "rawData": 1200,
    "items": 980,
    "insights": 85,
    "memoryThreads": 42,
    "graphEntities": 310,
    "itemLinks": 760
  },
  "backlog": {
    "conversationPending": 18,
    "insightUnbuilt": 24,
    "insightUngrouped": 6,
    "threadOutboxPending": 3,
    "threadOutboxFailed": 1,
    "graphBatchRepairRequired": 0
  },
  "activity": {
    "days": 7,
    "rawDataCreated": [
      {"date": "2026-04-24", "count": 20}
    ],
    "itemsCreated": [],
    "insightsCreated": []
  },
  "breakdown": {
    "sourceClients": [
      {"name": "claude-code", "count": 300}
    ],
    "rawDataTypes": [],
    "itemTypes": [],
    "insightTypes": [],
    "graphLinkTypes": []
  },
  "healthSignals": {
    "graphEnabled": true,
    "retrievalGraphAssistEnabled": true,
    "threadProjectionStates": [
      {"state": "AVAILABLE", "count": 5}
    ]
  }
}
```

Metric sources:

- `totals.rawData`: `memory_raw_data`.
- `totals.items`: `memory_item`.
- `totals.insights`: `memory_insight`.
- `totals.memoryThreads`: memory thread table.
- `totals.graphEntities`: `memory_graph_entity`.
- `totals.itemLinks`: `memory_item_link`.
- `backlog.conversationPending`: `memory_conversation_buffer.extracted=false`.
- `backlog.insightUnbuilt`: `memory_insight_buffer.built=false`.
- `backlog.insightUngrouped`: `memory_insight_buffer.built=false AND group_name IS NULL`.
- `backlog.threadOutboxPending` and `threadOutboxFailed`: memory thread outbox table.
- `backlog.graphBatchRepairRequired`: `memory_item_graph_batch.state='REPAIR_REQUIRED'`.
- `activity`: daily counts by `created_at` for raw data, items, and insights within the requested `days`.
- `breakdown.sourceClients`: source client counts from `memory_raw_data.source_client`, with null or blank values grouped under `unknown`.
- `breakdown.rawDataTypes`: raw data type counts.
- `breakdown.itemTypes`: item type counts.
- `breakdown.insightTypes`: insight type counts.
- `breakdown.graphLinkTypes`: `memory_item_link.link_type` counts.
- `healthSignals.graphEnabled`: current memory options `extraction.item.graph.enabled`.
- `healthSignals.retrievalGraphAssistEnabled`: true if simple or deep retrieval graph assist is enabled in current memory options.
- `healthSignals.threadProjectionStates`: grouped counts from memory thread runtime/projection state.

Dashboard implementation should not query external metric systems. It reads persisted DB state and the current memory option snapshot.

## Item Graph APIs

Base path:

`/admin/v1/item-graph`

### Summary

`GET /admin/v1/item-graph/summary`

Query parameters:

- `memoryId`, optional.

Response:

- Entity count.
- Alias count.
- Mention count.
- Item link count.
- Cooccurrence count.
- Graph batch count by state: `PENDING`, `COMMITTED`, `REPAIR_REQUIRED`.
- Item link count by type: `SEMANTIC`, `TEMPORAL`, `CAUSAL`.
- Entity count by type: `PERSON`, `ORGANIZATION`, `PLACE`, `OBJECT`, `CONCEPT`, `OTHER`, `SPECIAL`.

### Entities

`GET /admin/v1/item-graph/entities`

Query parameters:

- `memoryId`, optional.
- `entityType`, optional.
- `q`, optional. Match against `entityKey` and `displayName`.
- `pageNo`, optional, default `1`.
- `pageSize`, optional, default `20`, maximum `100`.

Response item fields:

- `id`
- `memoryId`
- `userId`
- `agentId`
- `entityKey`
- `displayName`
- `entityType`
- `metadata`
- `createdAt`
- `updatedAt`

`GET /admin/v1/item-graph/entities/{id}`

Query parameters:

- None.

Entity detail uses the numeric DB ID because graph `entityKey` values may contain path-unsafe characters and are only unique inside a memory. The list endpoint returns `id`, `memoryId`, and `entityKey`, so callers can navigate from list to detail without encoding ambiguity.

Response:

- Entity row.
- Aliases for the entity.
- Mention count.
- Top mentioned item IDs.
- Top cooccurrences for the entity.
- Entity-overlap semantic item link count. These links are item-to-item links whose `evidenceSource` is `entity_overlap` and may have been derived from the entity's mentions. Count links by looking at item links connected to items that mention this entity; this is intentionally a conservative "needs review" count, not proof that every link is invalid.

`DELETE /admin/v1/item-graph/entities`

Request:

```json
{
  "memoryId": "user:agent",
  "entityKeys": ["person:alice"]
}
```

Behavior:

- Delete matching entities.
- Also delete aliases for those entities.
- Also delete item mentions for those entities.
- Also delete cooccurrences where either side is one of those entities.
- Execute the cascade inside one transaction.
- Physically delete these graph rows with explicit mapper methods. Graph unique indexes do not include `deleted`, and core graph upserts should be able to recreate the same identity after an admin correction.
- Do not delete item links by default, because item links are item-to-item relations and may have independent semantic, temporal, or causal evidence.
- Before deleting mentions, compute the count of surviving `entity_overlap` semantic item links connected to items that mention the target entities. Return that count so administrators can inspect those links through the item link list and delete them explicitly if they are incorrect.
- Do not rebuild cooccurrences automatically.

Response:

```json
{
  "deletedCount": 1,
  "affectedMemoryIds": ["user:agent"],
  "deletedAliases": 2,
  "deletedMentions": 8,
  "deletedCooccurrences": 5,
  "possiblyStaleEntityOverlapLinks": 3
}
```

### Aliases

`GET /admin/v1/item-graph/aliases`

Query parameters:

- `memoryId`, optional.
- `entityKey`, optional.
- `q`, optional. Match against `normalizedAlias`.
- `pageNo`, optional, default `1`.
- `pageSize`, optional, default `20`, maximum `100`.

Response item fields:

- `id`
- `memoryId`
- `userId`
- `agentId`
- `entityKey`
- `entityType`
- `normalizedAlias`
- `evidenceCount`
- `metadata`
- `createdAt`
- `updatedAt`

`DELETE /admin/v1/item-graph/aliases`

Request:

```json
{
  "ids": [1, 2, 3]
}
```

Behavior:

- Physically delete requested alias rows.

### Item Mentions

`GET /admin/v1/item-graph/mentions`

Query parameters:

- `memoryId`, optional.
- `itemId`, optional.
- `entityKey`, optional.
- `pageNo`, optional, default `1`.
- `pageSize`, optional, default `20`, maximum `100`.

Response item fields:

- `id`
- `memoryId`
- `userId`
- `agentId`
- `itemId`
- `entityKey`
- `confidence`
- `metadata`
- `createdAt`
- `updatedAt`

`DELETE /admin/v1/item-graph/mentions`

Request:

```json
{
  "ids": [1, 2, 3]
}
```

Behavior:

- Physically delete requested mention rows.
- Do not rebuild cooccurrences automatically in the first version.

### Item Links

`GET /admin/v1/item-graph/item-links`

Query parameters:

- `memoryId`, optional.
- `itemId`, optional. Match source or target item ID.
- `linkType`, optional.
- `evidenceSource`, optional. Useful for finding links generated from `entity_overlap`.
- `pageNo`, optional, default `1`.
- `pageSize`, optional, default `20`, maximum `100`.

Response item fields:

- `id`
- `memoryId`
- `userId`
- `agentId`
- `sourceItemId`
- `targetItemId`
- `linkType`
- `relationCode`
- `evidenceSource`
- `strength`
- `metadata`
- `createdAt`
- `updatedAt`

`DELETE /admin/v1/item-graph/item-links`

Request:

```json
{
  "ids": [1, 2, 3]
}
```

Behavior:

- Physically delete requested item link rows.

### Cooccurrences

`GET /admin/v1/item-graph/cooccurrences`

Query parameters:

- `memoryId`, optional.
- `entityKey`, optional. Match left or right entity key.
- `pageNo`, optional, default `1`.
- `pageSize`, optional, default `20`, maximum `100`.

Response item fields:

- `id`
- `memoryId`
- `userId`
- `agentId`
- `leftEntityKey`
- `rightEntityKey`
- `cooccurrenceCount`
- `metadata`
- `createdAt`
- `updatedAt`

`DELETE /admin/v1/item-graph/cooccurrences`

Request:

```json
{
  "ids": [1, 2, 3]
}
```

Behavior:

- Physically delete requested cooccurrence rows.

### Graph Batches

`GET /admin/v1/item-graph/batches`

Query parameters:

- `memoryId`, optional.
- `state`, optional. Allowed values: `PENDING`, `COMMITTED`, `REPAIR_REQUIRED`.
- `pageNo`, optional, default `1`.
- `pageSize`, optional, default `20`, maximum `100`.

Response item fields:

- `id`
- `memoryId`
- `userId`
- `agentId`
- `extractionBatchId`
- `state`
- `errorMessage`
- `retryPromotionSupported`
- `createdAt`
- `updatedAt`

Behavior:

- Read-only in the first version.
- Do not expose retry or discard actions.

## Validation

Validation rules:

- Empty ID lists are invalid for patch/delete requests.
- `pageNo` must be at least `1`.
- `pageSize` must be between `1` and `100`.
- Enum query parameters must match supported values.
- `memoryId` is required for entity delete operations.
- `days` for dashboard must be between `1` and `30`.
- `memoryId` must follow the existing identifier convention used by `DefaultMemoryId.toIdentifier()`. The server resolves it to `userId` and `agentId` for index-friendly queries.

Errors should flow through the existing `ApiExceptionHandler`:

- Invalid parameters: `400 bad_request`.
- Missing rows in detail endpoints: `404 not_found`.
- Unexpected runtime/DB failures: `500 internal_error`.

## Data Consistency

The APIs deliberately avoid hidden reprocessing.

Conversation buffer:

- Rows can be marked extracted.
- Rows cannot be marked pending again in the first version.
- Correction delete may use logical delete because the table has no business identity unique index.

Insight buffer:

- Rows can be regrouped.
- Rows can be marked built or unbuilt.
- Correction delete physically removes rows so the same `(userId, agentId, insightTypeName, itemId)` can be appended later.

Item graph:

- Entity delete cascades only through entity-owned graph tables: aliases, mentions, cooccurrences.
- Item links are not deleted by entity delete.
- Entity detail and entity delete surface potentially stale `entity_overlap` semantic links so administrators can inspect and explicitly delete them through `/admin/v1/item-graph/item-links`.
- Mention delete and cooccurrence delete do not trigger cooccurrence rebuild.
- Graph batches are read-only.
- Multi-table graph corrections run in one transaction.
- Graph correction deletes physically remove rows so core graph upserts can recreate the same unique identities later.

This keeps admin correction explicit and avoids surprising extraction or graph side effects.

## Testing

Add focused tests at three levels:

- Standalone controller tests for request mapping, validation, and response envelopes.
- Query mapper/service tests for filtering, paging, summary counts, and dashboard aggregation.
- Integration tests using the existing SQLite test setup to verify end-to-end Admin API behavior against real tables.

Minimum coverage:

- Conversation buffer list defaults to pending.
- Conversation buffer mark extracted updates only requested IDs.
- Insight buffer list state filters work for `unbuilt`, `ungrouped`, `grouped`, `built`, and `all`.
- Insight buffer group and built updates return updated counts.
- Dashboard returns totals, backlog counts, breakdowns, and respects `memoryId`.
- Dashboard and buffer queries resolve `memoryId` to indexed `user_id` and `agent_id` predicates where applicable.
- Item graph summary returns grouped counts.
- Entity delete cascades to aliases, mentions, and cooccurrences but does not delete item links.
- Entity delete returns the count of potentially stale `entity_overlap` item links.
- Item link list can filter by `evidenceSource`.
- Insight buffer delete allows a later `InsightBuffer.append()` for the same identity to succeed.
- Graph alias, mention, item link, cooccurrence, and entity deletes allow later core graph upserts for the same identity to succeed.
- Graph batch list is read-only and filters by state.

## Implementation Notes

Prefer small, separate domain records for each response instead of maps for core API payloads. Use maps only where the data is naturally dynamic, such as grouped counts by state or type.

Follow the existing package and file style:

- Controllers under `controller/admin/...`.
- Services under `service/...`.
- Query mappers under `mapper/...`.
- Domain records under `domain/...`.

Use existing MyBatis Plus wrappers and mapper patterns. Avoid raw SQL unless grouped dashboard aggregation becomes too awkward or inefficient with wrappers.
