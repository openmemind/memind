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

Clean template metadata and dependencies so the new module looks and behaves like a first-class Memind project:

- rename `package.json` from `shadcn-admin` to `memind-ui`
- keep the package private unless there is a later explicit publishing decision
- remove unused auth/demo dependencies such as `@clerk/react` after deleting their import sites
- remove obsolete auth/demo tests, assets, and route entries
- keep or add license files and notices required by reused template code and Memind's Apache-2.0 licensing
- update README content so it documents Memind UI setup, local server assumptions, and development commands

Rename the template's `_authenticated` route group to a neutral app-shell route group such as `_app` during migration. Do not keep route directory names, component names, or navigation labels that imply login or authorization in the first version. Regenerate TanStack Router's generated route tree after route files are moved.

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

The first version is intended for local development use. Because it exposes delete, rebuild, and runtime configuration operations without authentication, project documentation must explicitly warn users not to expose the UI or proxied Memind server endpoints to public networks.

## Server Connection Status

Show a lightweight server status indicator in the sidebar footer or header. It replaces the template's user profile/sign-out footer.

Implementation:

- call `GET /open/v1/health` through the Vite proxy
- poll every 30 seconds with React Query `refetchInterval`
- show `connecting` before the first result, `connected` after a successful health response, and `disconnected` after network failure or non-2xx response
- keep a global API error handler that can also mark the server as disconnected when ordinary page requests fail with connection errors
- do not block page rendering only because health is disconnected; show inline retry/error states on the affected page

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

## API Parameter Conventions

Paginated admin list endpoints use one-based pagination:

- `pageNo`, default `1`, minimum `1`
- `pageSize`, default `20`, minimum `1`, maximum `100`

Use the shadcn-admin `use-table-url-state.ts` pattern for list pages. Configure the hook with `pageKey: "pageNo"` and `pageSizeKey: "pageSize"` so frontend URL state matches the server contract instead of introducing `page` or `limit`. Sync page number, page size, search/filter fields, and any client-side sort state that is useful for a bookmarkable view. Reset `pageNo` to `1` when filters change.

The following detail/relationship endpoints require query parameters beyond the path:

| Endpoint | Required query | Optional query | UI behavior when missing |
| --- | --- | --- | --- |
| `GET /admin/v1/memory-threads/{threadKey}` | `userId` | `agentId` | Disable the detail drawer action or show a prompt to set memory scope with a user id. |
| `GET /admin/v1/memory-threads/{threadKey}/items` | `userId` | `agentId` | Disable thread item loading or show the same scope prompt. |
| `GET /admin/v1/items/{itemId}/memory-threads` | `userId` | `agentId` | Show the item detail without associated threads, with an inline prompt to set memory scope. |

Destructive operations in version one only delete or update explicitly selected rows. They must never mean "delete every row matching the current filters".

| Operation | Request body | Selection source |
| --- | --- | --- |
| Delete memory items | `{ itemIds: number[] }` | selected item rows |
| Delete raw data | `{ rawDataIds: string[] }` | selected raw data rows |
| Delete insights | `{ insightIds: number[] }` | selected insight rows |
| Mark/delete conversation buffers | `{ ids: number[] }` | selected conversation buffer rows |
| Group/mark/delete insight buffers | `{ ids: number[], ... }` | selected insight buffer rows |
| Delete graph entities | `{ memoryId: string, entityKeys: string[] }` | selected entity rows plus active `memoryId` |
| Delete graph aliases, mentions, item links, cooccurrences | `{ ids: number[] }` | selected graph rows |

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

## Type Synchronization Strategy

The server currently does not expose an OpenAPI contract. The first implementation should define TypeScript request and response types manually, using the Java controller methods and DTO records as the source of truth. Keep those types centralized under the Memind API/type modules so page components do not invent local response shapes.

For later maintenance, consider adding `springdoc-openapi` to `memind-server` and generating frontend types with `openapi-typescript`. That is a follow-up improvement, not a blocker for the first UI version.

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

## Template Feature Decisions

Keep template infrastructure that directly improves a local admin tool, and remove preference/demo surfaces that do not serve Memind workflows.

| Template feature | Decision | Notes |
| --- | --- | --- |
| Dark/light/system theme switch | Keep | Keep the lightweight header theme switch and theme provider. |
| Layout config drawer | Remove | Do not expose sidebar variant, layout width, or other template layout preferences in version one. |
| Global search / command palette | Remove | There is no backend-backed global search in version one; sidebar navigation and page-level filters are enough. Keep `cmdk` only if still needed by reusable table filter components. |
| RTL support | Keep | Keep existing RTL-safe component styling and direction support where it has low maintenance cost. |
| Font selection | Remove | Use one project default font stack; do not keep font preference UI or font settings pages. |

## Global Memory Scope

Add a global memory scope control in the header or sidebar. It stores a preferred scope in URL/localStorage and applies it as default filters where supported.

Use a `memoryId` text field as the primary input. The default memory identifier format is:

```text
userId:agentId
```

When an API requires `userId` and `agentId`, parse `memoryId` into those fields. If the value has no colon, treat it as `userId` with an empty or omitted `agentId` only where the backend allows it.

For APIs that require explicit `userId`, such as memory thread status and rebuild, the UI must validate that `userId` is present before sending the request. Retrieve is stricter: `POST /open/v1/memory/retrieve` requires both non-empty `userId` and non-empty `agentId`, so the retrieve form must reject a scope that cannot supply both fields.

Use one centralized `memory-scope.ts` helper so parsing rules are identical across pages.

| API group | Accepted scope parameters | UI rule |
| --- | --- | --- |
| Dashboard | `memoryId?` | Pass the raw `memoryId` when present. |
| Buffers | `memoryId?` | Pass the raw `memoryId` when present. |
| Item Graph summary/lists/batches | `memoryId?` | Pass the raw `memoryId` when present. Entity deletion requires a non-empty `memoryId`. |
| Items list | `userId?`, `agentId?`, `scope?`, plus item filters | Split `memoryId` into `userId` and optional `agentId`; keep `scope` as its own filter field. |
| Raw Data list | `userId?`, `agentId?`, plus time filters | Split `memoryId`; omit `agentId` when absent. |
| Insights list | `userId?`, `agentId?`, `scope?`, plus insight filters | Split `memoryId`; keep `scope` as its own filter field. |
| Memory Threads list | `userId?`, `agentId?`, `status?` | Split `memoryId`; listing can run without a scope. |
| Memory Thread detail and thread items | `userId`, `agentId?` | Require parsed `userId`; disable or prompt when missing. |
| Item associated threads | `userId`, `agentId?` | Require parsed `userId`; render the rest of item detail without this relationship when missing. |
| Memory Thread status/rebuild | `userId`, `agentId?` | Require parsed `userId`; confirmation happens after validation. |
| Retrieve | `userId`, `agentId` | Require both parsed fields to be non-empty. |
| Config and health | no memory scope | Do not apply memory scope. |

Tables backed by DTOs that include `memoryId`, `userId`, and `agentId` should expose ownership clearly. The simplest version is to always show `memoryId` as a table column. If later screen density becomes a problem, the UI may hide `memoryId` only when a global memory scope is active and the page clearly displays that active scope elsewhere.

## Common UI States

Each page should define explicit loading, empty, and error states instead of relying on a blank table.

- First load: show skeleton rows or compact loading placeholders matching the table/card layout.
- Empty lists: show a short empty state such as `暂无 Memory Items，请先通过 API 写入数据` and keep filters visible so users can clear them.
- Empty dashboard: show zero-value metric cards and an inline note that no memory data was found for the selected scope.
- Detail drawers: show a drawer-level loading state, a not-found state for `not_found`, and a scope-required prompt when the requested relationship needs `userId`.
- Server unreachable: show a page-level alert with the current server URL assumption and a retry action; keep the sidebar status in `disconnected`.
- Mutations: disable submit buttons while pending and report server `traceId` when present.

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

Activity charts should use `AdminDashboardView.Activity.days` and the three `DailyCount` series: `rawDataCreated`, `itemsCreated`, and `insightsCreated`. Use either a compact multi-series line chart or grouped bars; the X axis uses the server-provided `DailyCount.date` string in `YYYY-MM-DD` format.

Backlog cards should be actionable where the current API surface supports a useful target:

- `conversationPending` links to Buffers > Conversations with `state=pending`
- `insightUnbuilt` links to Buffers > Insights with `state=unbuilt`
- `insightUngrouped` links to Buffers > Insights with `state=unbuilt` and preserves scope; do not invent an unsupported `groupName` server filter in version one
- `threadOutboxPending` and `threadOutboxFailed` link to Memory Threads and focus the status panel for the active scope
- `graphBatchRepairRequired` links to Item Graph > Batches with `state=REPAIR_REQUIRED`

Dashboard links must preserve the active `memoryId` scope in the target URL when applicable.

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
- memory id
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
- associated memory threads loaded from `GET /admin/v1/items/{itemId}/memory-threads?userId=...&agentId=...`

The associated memory threads section requires a parsed `userId` from the global memory scope. If no `userId` is available, keep the item detail usable and show an inline prompt to set scope before loading related threads.

Bulk delete means deleting selected rows only. The request body is `{ itemIds: number[] }`. The confirmation must show the selected count and must not imply deletion of all currently filtered results. After success, display `deletedCount` and `affectedMemoryIds`.

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
- memory id
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

Bulk delete means deleting selected rows only. The request body is `{ rawDataIds: string[] }`. The dialog must state that deleting raw data also deletes associated memory items via server runtime behavior and must not imply deletion of all currently filtered results. After success, display `deletedRawDataCount`, `deletedItemCount`, `affectedMemoryIds`, and `insightCleanupRequired`.

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
- memory id
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

Bulk delete means deleting selected rows only. The request body is `{ insightIds: number[] }`. The confirmation must show the selected count and must not imply deletion of all currently filtered results. After success, display `deletedCount` and `affectedMemoryIds`.

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

Conversation columns:

- id
- session id
- memory id
- role
- content preview
- user name
- source client
- extracted
- timestamp
- created at
- updated at

Conversation actions:

- mark selected rows as extracted with `{ ids: number[] }`
- delete selected rows with `{ ids: number[] }`

Insight buffer APIs:

- `GET /admin/v1/buffers/insights`
- `PATCH /admin/v1/buffers/insights/group`
- `PATCH /admin/v1/buffers/insights/built`
- `DELETE /admin/v1/buffers/insights`

Insight buffer filters:

- `memoryId`
- `insightTypeName`
- `state`, default `unbuilt`

Insight buffer columns:

- id
- memory id
- insight type name
- item id
- group name
- built
- created at
- updated at

Insight buffer actions:

- update group name for selected rows with `{ ids: number[], groupName }`
- mark selected rows built/unbuilt with `{ ids: number[], built }`
- delete selected rows with `{ ids: number[] }`

Insight group API:

- `GET /admin/v1/buffers/insights/groups`

Insight group columns:

- memory id
- insight type name
- group name
- total
- unbuilt
- built

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

- memory id
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
- thread item memberships with `itemId`, `role`, `primary`, `relevanceWeight`, `createdAt`, and `updatedAt`; keep `memoryId` and `threadKey` visible when useful for debugging

The detail drawer and thread item memberships call `GET /admin/v1/memory-threads/{threadKey}` and `GET /admin/v1/memory-threads/{threadKey}/items` with required `userId` and optional `agentId` query parameters from the global memory scope. If no `userId` is available, disable opening the drawer or show a scope-required prompt before issuing either request.

Status panel:

- projection state
- pending count
- failed count
- rebuild in progress
- last processed item id
- materialization policy version
- updated at
- invalidation reason

Status and rebuild require explicit `userId` and optional `agentId`. Rebuild requires explicit confirmation after scope validation. After success, refresh status and list.

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

Entity detail drawer:

- entity basic fields: id, memory id, user id, agent id, entity key, display name, entity type, metadata, created at, updated at
- aliases: id, memory id, entity key, normalized alias, evidence count, entity type, metadata, created at, updated at
- mention count
- top mentioned item ids
- top cooccurrences: id, memory id, left entity key, right entity key, cooccurrence count, metadata, created at, updated at
- entity overlap item link count

Entity delete means deleting selected entity rows only. The request body requires active `memoryId` and selected row `entityKeys`. The confirmation must display that aliases, mentions, and cooccurrences can be removed. Success displays `deletedAliases`, `deletedMentions`, `deletedCooccurrences`, and `possiblyStaleEntityOverlapLinks`.

Aliases:

- `GET /admin/v1/item-graph/aliases`
- `DELETE /admin/v1/item-graph/aliases`

Filters: `memoryId`, `entityKey`, `q`

Delete aliases by selected alias row ids only with `{ ids: number[] }`.

Mentions:

- `GET /admin/v1/item-graph/mentions`
- `DELETE /admin/v1/item-graph/mentions`

Filters: `memoryId`, `itemId`, `entityKey`

Delete mentions by selected mention row ids only with `{ ids: number[] }`.

Item links:

- `GET /admin/v1/item-graph/item-links`
- `DELETE /admin/v1/item-graph/item-links`

Filters: `memoryId`, `itemId`, `linkType`, `evidenceSource`

Delete item links by selected item-link row ids only with `{ ids: number[] }`.

Cooccurrences:

- `GET /admin/v1/item-graph/cooccurrences`
- `DELETE /admin/v1/item-graph/cooccurrences`

Filters: `memoryId`, `entityKey`

Delete cooccurrences by selected cooccurrence row ids only with `{ ids: number[] }`.

Batches:

- `GET /admin/v1/item-graph/batches`

Filters: `memoryId`, `state`

Graph batch rows are read-only in version one.

### Config

API:

- `GET /admin/v1/config/memory-options`
- `PUT /admin/v1/config/memory-options`

`GET /admin/v1/config/memory-options` returns `MemoryOptionsGetResponse`:

```ts
type MemoryOptionsGetResponse = {
  version: number
  config: Record<
    string,
    Array<{
      key: string
      value: unknown
      description: string
      type: string
      defaultValue: unknown
      constraints: Record<string, unknown>
    }>
  >
}
```

Display `config` grouped by section key. Each option row shows:

- key
- value editor
- type
- default value
- description
- constraints JSON

Value editor behavior:

- `boolean`: switch/toggle
- `integer` and `double`: number input, using `constraints` such as min/max when present for client-side validation
- `string`: text input by default; use select when `constraints` contains an enum/options list
- unknown or complex types: read-only JSON preview plus an explicit text/JSON edit fallback so unsupported types do not silently corrupt values

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

The response shape is:

```ts
type RetrieveMemoryResponse = {
  items: Array<{
    id: string
    text: string
    vectorScore: number
    finalScore: number
    occurredAt: string | null
  }>
  insights: Array<{ id: string; text: string; tier: string }>
  rawData: Array<{
    rawDataId: string
    caption: string
    maxScore: number
    itemIds: string[]
  }>
  evidences: string[]
  strategy: string
  query: string
  trace: RetrievalTraceView | null
}

type RetrievalTraceView = {
  traceId: string
  startedAt: string
  completedAt: string
  truncated: boolean
  stages: Array<{
    stage: string
    tier: string
    method: string
    status: string
    inputCount: number | null
    candidateCount: number | null
    resultCount: number | null
    degraded: boolean
    skipped: boolean
    startedAt: string
    durationMillis: number | null
    attributes: Record<string, unknown>
    candidates: unknown[]
  }>
  merge: {
    inputCount: number
    outputCount: number
    deduplicatedCount: number
    sourceCount: number
    status: string
  } | null
  finalResults: {
    strategy: string
    status: string
    itemCount: number
    insightCount: number
    rawDataCount: number
    evidenceCount: number
  } | null
}
```

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

Keep reusable table and UI primitives in existing template component folders when they are generic. Because this module is itself `memind-ui`, avoid an extra `features/memind/` nesting layer; put Memind feature domains directly under `src/features/`.

## Data Fetching

Use React Query for all server calls.

Query keys should include:

- resource name
- pagination params
- filters
- active memory scope

For list pages, React Query variables should use the same names as the server and URL state: `pageNo`, `pageSize`, and the documented filter keys.

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

- `pnpm lint` in `memind-ui`
- `pnpm test` in `memind-ui`
- `pnpm build` in `memind-ui`
- API client unit tests for success, async ok-without-data, and server error responses
- memory scope parsing unit tests
- table URL state integration tests for `pageNo`/`pageSize` and filters
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
- no public-hosting security model for the unauthenticated admin UI

## Open Implementation Notes

- The server currently exposes no dedicated "list memory scopes" API. The UI should rely on user-entered `memoryId` and observed IDs from dashboard/list responses.
- The memory scope parameter matrix above is the implementation source of truth for `memory-scope.ts`.
- The shadcn-admin generated `routeTree.gen.ts` should be regenerated by the TanStack Router Vite plugin after routes are changed, especially after renaming `_authenticated` to `_app`.
- Since graph entity deletion requires `memoryId`, the UI should disable entity deletion until a memory scope is selected.
