# Memind UI Design

Date: 2026-04-30

## Goal

Build a local-first Memind management UI as a new independent frontend module under the existing Memind repository. The UI lets a developer inspect and manage memory data produced by a local `memind-server` instance without login, authorization, or multi-user SaaS workflows.

The first version focuses on admin management and read/debug workflows. It does not include manual memory ingestion forms beyond a lightweight retrieval debug page.

## Repository Placement

Create a new top-level directory:

```text
memind-ui/
```

`memind-ui` is not a Maven module and must not be added to the root `pom.xml`. It is a standalone Vite frontend project managed by `pnpm`.

The implementation starts from the existing template at:

```text
/Users/zhengyate/dev/git-project/shadcn-admin
```

The template is copied or migrated into `memind-ui`, then cleaned into a Memind-specific app. The UI should keep the useful foundation from the template:

- Vite
- React
- TanStack Router
- React Query
- shadcn/ui components
- Tailwind CSS
- sidebar, header, theme, dialogs, drawers, and data table patterns

Remove or replace template sample business surfaces:

- login and sign-up pages
- Clerk integration
- mock auth store and sign-out behavior
- users, tasks, apps, chats, and demo settings pages
- sample dashboard metrics

The existing `_authenticated` route group can remain as an app layout container if useful, but it must not enforce or imply authentication.

## Backend Integration

The UI talks to a separately running Memind server, normally:

```text
http://127.0.0.1:8366
```

During development, configure Vite proxy rules:

```text
/admin -> http://127.0.0.1:8366
/open  -> http://127.0.0.1:8366
```

This lets the frontend call same-origin paths such as `/admin/v1/items` and `/open/v1/memory/retrieve`, avoiding an initial CORS change in `memind-server`.

Production packaging remains a separate frontend build in the first version. Embedding `memind-ui/dist` into `memind-server` static resources is a later packaging step, not part of this design.

## API Surface Used

The first UI version uses these server APIs:

- `GET /open/v1/health`
- `POST /open/v1/memory/retrieve`
- `GET /admin/v1/dashboard`
- `GET /admin/v1/raw-data`
- `GET /admin/v1/raw-data/{rawDataId}`
- `DELETE /admin/v1/raw-data`
- `GET /admin/v1/items`
- `GET /admin/v1/items/{itemId}`
- `GET /admin/v1/items/{itemId}/memory-threads`
- `DELETE /admin/v1/items`
- `GET /admin/v1/insights`
- `GET /admin/v1/insights/{insightId}`
- `DELETE /admin/v1/insights`
- `GET /admin/v1/buffers/conversations`
- `GET /admin/v1/buffers/conversations/{id}`
- `PATCH /admin/v1/buffers/conversations/extracted`
- `DELETE /admin/v1/buffers/conversations`
- `GET /admin/v1/buffers/insights`
- `GET /admin/v1/buffers/insights/groups`
- `PATCH /admin/v1/buffers/insights/group`
- `PATCH /admin/v1/buffers/insights/built`
- `DELETE /admin/v1/buffers/insights`
- `GET /admin/v1/memory-threads`
- `GET /admin/v1/memory-threads/{threadKey}`
- `GET /admin/v1/memory-threads/{threadKey}/items`
- `GET /admin/v1/memory-threads/status`
- `POST /admin/v1/memory-threads/rebuild`
- `GET /admin/v1/item-graph/summary`
- `GET /admin/v1/item-graph/entities`
- `GET /admin/v1/item-graph/entities/{id}`
- `DELETE /admin/v1/item-graph/entities`
- `GET /admin/v1/item-graph/aliases`
- `DELETE /admin/v1/item-graph/aliases`
- `GET /admin/v1/item-graph/mentions`
- `DELETE /admin/v1/item-graph/mentions`
- `GET /admin/v1/item-graph/item-links`
- `DELETE /admin/v1/item-graph/item-links`
- `GET /admin/v1/item-graph/cooccurrences`
- `DELETE /admin/v1/item-graph/cooccurrences`
- `GET /admin/v1/item-graph/batches`
- `GET /admin/v1/config/memory-options`
- `PUT /admin/v1/config/memory-options`

The open ingestion APIs exist but are not first-class UI pages in version one:

- `POST /open/v1/memory/extract`
- `POST /open/v1/memory/add-message`
- `POST /open/v1/memory/commit`

## Response Contract

The server wraps responses as:

```ts
type ApiResult<T> = {
  code: string
  message?: string
  data?: T
  timestamp: string
  traceId?: string
}
```

Successful data endpoints use `code: "success"`. Async open write endpoints may use `code: "200"` with no data.

Paginated admin responses use:

```ts
type PageResult<T> = {
  total: number
  list: T[]
  current: number
}
```

Frontend code should unwrap `ApiResult<T>` in one API client layer. Pages should consume typed data and should not duplicate response unwrapping.

## App Navigation

Use a compact admin navigation:

- Dashboard
- Memory Items
- Raw Data
- Insights
- Buffers
- Memory Threads
- Item Graph
- Config
- Retrieve

The sidebar should be renamed for Memind. Suggested branding:

- app name: `Memind UI`
- subtitle: `Local Memory Admin`

The sidebar footer should not show a user profile or sign-out. It can show server connection status instead.

## Global Memory Scope

Add a global memory scope control in the header or sidebar. It stores a preferred scope in URL/localStorage and applies it as default filters where supported.

Use a `memoryId` text field as the primary input. The default memory identifier format is:

```text
userId:agentId
```

When an API requires `userId` and `agentId`, parse `memoryId` into those fields. If the value has no colon, treat it as `userId` with an empty or omitted `agentId` where the backend allows it.

For APIs that require explicit `userId`, such as memory thread status, rebuild, and retrieve, the UI must validate that `userId` is present before sending the request.

## Pages

### Dashboard

API: `GET /admin/v1/dashboard`

Query:

- `memoryId?`
- `days`, default 7, selectable up to 30

Display:

- totals: raw data, items, insights, memory threads, graph entities, item links
- backlog: pending conversations, unbuilt insights, ungrouped insights, thread outbox pending/failed, graph batches needing repair
- activity charts for raw data, items, and insights
- breakdown lists for source clients, raw data types, item types, insight types, graph link types
- health signals for graph enabled, retrieval graph assist enabled, and thread projection states

Use cards for individual metric summaries and chart/table sections. Avoid marketing hero layout.

### Memory Items

API:

- `GET /admin/v1/items`
- `GET /admin/v1/items/{itemId}`
- `GET /admin/v1/items/{itemId}/memory-threads`
- `DELETE /admin/v1/items`

Filters:

- `userId`
- `agentId`
- `scope`
- `category`
- `type`
- `rawDataId`

List columns:

- item id
- content preview
- type
- category
- scope
- raw data id
- source client
- observed at
- created at

Row detail drawer:

- full content
- metadata JSON
- raw data type
- vector id
- content hash
- occurred/observed timestamps
- associated memory threads

Bulk delete requires confirmation and displays `deletedCount` and `affectedMemoryIds`.

### Raw Data

API:

- `GET /admin/v1/raw-data`
- `GET /admin/v1/raw-data/{rawDataId}`
- `DELETE /admin/v1/raw-data`

Filters:

- `userId`
- `agentId`
- `startTimeFrom`
- `startTimeTo`

List columns:

- raw data id
- type
- caption preview
- source client
- content id
- start time
- created at

Detail drawer:

- caption
- segment JSON
- metadata JSON
- content id
- caption vector id
- start/end timestamps

Bulk delete requires confirmation. The dialog must state that deleting raw data also deletes associated memory items via server runtime behavior. After success, display `deletedRawDataCount`, `deletedItemCount`, `affectedMemoryIds`, and `insightCleanupRequired`.

### Insights

API:

- `GET /admin/v1/insights`
- `GET /admin/v1/insights/{insightId}`
- `DELETE /admin/v1/insights`

Filters:

- `userId`
- `agentId`
- `scope`
- `type`
- `tier`

List columns:

- insight id
- name/content preview
- type
- tier
- scope
- group name
- updated at

Detail drawer:

- content
- points JSON/tree view
- categories
- parent/child insight ids
- summary embedding collapsed by default
- version and timestamps

Bulk delete requires confirmation and displays `deletedCount` and `affectedMemoryIds`.

### Buffers

Use tabs:

- Conversations
- Insights
- Insight Groups

Conversation APIs:

- `GET /admin/v1/buffers/conversations`
- `GET /admin/v1/buffers/conversations/{id}`
- `PATCH /admin/v1/buffers/conversations/extracted`
- `DELETE /admin/v1/buffers/conversations`

Conversation filters:

- `memoryId`
- `sessionId`
- `state`, default `pending`

Conversation actions:

- mark selected as extracted
- delete selected

Insight buffer APIs:

- `GET /admin/v1/buffers/insights`
- `PATCH /admin/v1/buffers/insights/group`
- `PATCH /admin/v1/buffers/insights/built`
- `DELETE /admin/v1/buffers/insights`

Insight buffer filters:

- `memoryId`
- `insightTypeName`
- `state`, default `unbuilt`

Insight buffer actions:

- update group name
- mark built/unbuilt
- delete selected

Insight group API:

- `GET /admin/v1/buffers/insights/groups`

Group view displays `memoryId`, `insightTypeName`, `groupName`, `total`, `unbuilt`, and `built`.

### Memory Threads

API:

- `GET /admin/v1/memory-threads`
- `GET /admin/v1/memory-threads/{threadKey}`
- `GET /admin/v1/memory-threads/{threadKey}/items`
- `GET /admin/v1/memory-threads/status`
- `POST /admin/v1/memory-threads/rebuild`

Filters:

- `userId`
- `agentId`
- `status`

List columns:

- thread key
- display label/headline
- thread type
- lifecycle status
- object state
- member count
- event count
- last event at

Detail drawer:

- snapshot JSON
- anchor kind/key
- opened/closed timestamps
- thread item memberships

Status panel:

- projection state
- pending count
- failed count
- rebuild in progress
- last processed item id
- materialization policy version
- invalidation reason

Rebuild requires explicit confirmation and valid `userId`. After success, refresh status and list.

### Item Graph

Use internal tabs:

- Summary
- Entities
- Aliases
- Mentions
- Item Links
- Cooccurrences
- Batches

Summary API:

- `GET /admin/v1/item-graph/summary`

Entities:

- `GET /admin/v1/item-graph/entities`
- `GET /admin/v1/item-graph/entities/{id}`
- `DELETE /admin/v1/item-graph/entities`

Entity filters:

- `memoryId`
- `entityType`
- `q`

Entity delete request requires `memoryId` and `entityKeys`. The confirmation must display that aliases, mentions, and cooccurrences can be removed. Success displays `deletedAliases`, `deletedMentions`, `deletedCooccurrences`, and `possiblyStaleEntityOverlapLinks`.

Aliases:

- `GET /admin/v1/item-graph/aliases`
- `DELETE /admin/v1/item-graph/aliases`

Filters: `memoryId`, `entityKey`, `q`

Mentions:

- `GET /admin/v1/item-graph/mentions`
- `DELETE /admin/v1/item-graph/mentions`

Filters: `memoryId`, `itemId`, `entityKey`

Item links:

- `GET /admin/v1/item-graph/item-links`
- `DELETE /admin/v1/item-graph/item-links`

Filters: `memoryId`, `itemId`, `linkType`, `evidenceSource`

Cooccurrences:

- `GET /admin/v1/item-graph/cooccurrences`
- `DELETE /admin/v1/item-graph/cooccurrences`

Filters: `memoryId`, `entityKey`

Batches:

- `GET /admin/v1/item-graph/batches`

Filters: `memoryId`, `state`

Graph batch rows are read-only in version one.

### Config

API:

- `GET /admin/v1/config/memory-options`
- `PUT /admin/v1/config/memory-options`

Display config grouped by section key. Each option row shows:

- key
- value editor
- type
- default value
- description
- constraints JSON

Updates must send the current `expectedVersion`. If the server returns `409 conflict`, show a conflict message and ask the user to refresh before saving again.

### Retrieve

API:

- `POST /open/v1/memory/retrieve`

Inputs:

- `userId`
- `agentId`
- query
- strategy: `SIMPLE` or `DEEP`
- trace toggle

Display:

- retrieved items
- insights
- raw data
- evidences
- strategy and normalized query
- trace details if returned

If the server is not configured with retrieval trace enabled, the trace area should show an empty state rather than an error.

## Frontend Structure

Suggested structure:

```text
memind-ui/src/
  lib/
    api-client.ts
    format.ts
    memory-scope.ts
  features/
    memind/
      types.ts
      api/
        dashboard.ts
        items.ts
        raw-data.ts
        insights.ts
        buffers.ts
        memory-threads.ts
        item-graph.ts
        config.ts
        retrieve.ts
      components/
        memory-scope-picker.tsx
        json-viewer.tsx
        confirm-result-dialog.tsx
        server-status.tsx
      dashboard/
      items/
      raw-data/
      insights/
      buffers/
      memory-threads/
      item-graph/
      config/
      retrieve/
```

Keep reusable table and UI primitives in existing template component folders when they are generic. Put Memind-specific code under `features/memind`.

## Data Fetching

Use React Query for all server calls.

Query keys should include:

- resource name
- pagination params
- filters
- active memory scope

Mutation success should invalidate the narrow affected resource keys. For broad operations such as raw data deletion and memory thread rebuild, also invalidate dashboard queries because counts and health signals can change.

Do not implement optimistic deletion in version one. Wait for server success, then refresh.

## Error Handling

Remove template behavior that redirects to `/sign-in` on `401`. This app has no login flow.

The API client should map server errors into a displayable error object:

```ts
type ApiError = {
  status?: number
  code?: string
  message: string
  traceId?: string
  details?: unknown
}
```

UI behavior:

- show `message` in toast or inline alert
- show `traceId` when present
- keep the user on the current page
- for `bad_request`, surface field/parameter context where possible
- for `not_found`, show an empty/detail-not-found state
- for `conflict`, guide the user to refresh stale data
- for `service_unavailable`, show that the Memind runtime is unavailable

## Testing

Verification for the first implementation:

- `pnpm build` in `memind-ui`
- API client unit tests for success, async ok-without-data, and server error responses
- memory scope parsing unit tests
- focused component tests for delete confirmation, config conflict handling, and server status display
- manual integration with a running `memind-server` at `http://127.0.0.1:8366`

The first version does not need end-to-end browser automation unless the implementation changes core routing or table abstractions significantly.

## Non-Goals

- no login or authentication
- no Clerk
- no user/team management
- no manual extract/add-message/commit forms in the first version
- no Maven integration
- no embedded server static-resource packaging
- no custom backend API additions unless existing APIs prove insufficient during implementation

## Open Implementation Notes

- The server currently exposes no dedicated "list memory scopes" API. The UI should rely on user-entered `memoryId` and observed IDs from dashboard/list responses.
- Some APIs accept `memoryId`, while memory thread status/rebuild and retrieve require `userId`/`agentId`. The UI must centralize parsing to avoid inconsistent behavior.
- The shadcn-admin generated `routeTree.gen.ts` should be regenerated by the TanStack Router Vite plugin after routes are changed.
- Since graph entity deletion requires `memoryId`, the UI should disable entity deletion until a memory scope is selected.
