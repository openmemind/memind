# Memind UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a standalone `memind-ui/` Vite React application that lets local Memind users inspect, debug, and manage memory data through the existing `memind-server` admin and open APIs.

**Architecture:** Start from `/Users/zhengyate/dev/git-project/shadcn-admin`, migrate it into a new non-Maven `memind-ui/` module, and remove auth/demo surfaces. Use one typed API client, React Query, centralized memory scope parsing, URL-backed table state, and focused feature folders under `memind-ui/src/features/`.

**Tech Stack:** Vite 8, React 19, TypeScript, TanStack Router, TanStack Query, TanStack Table, shadcn/ui, Tailwind CSS 4, Vitest browser tests, Recharts.

---

## Source Inputs

- Design spec: `docs/superpowers/specs/2026-04-30-memind-ui-design.md`
- UI template: `/Users/zhengyate/dev/git-project/shadcn-admin`
- Server controllers: `memind-server/src/main/java/com/openmemind/ai/memory/server/controller`
- Server DTOs: `memind-server/src/main/java/com/openmemind/ai/memory/server/domain`

## File Structure

Create a new top-level module:

```text
memind-ui/
  README.md
  package.json
  pnpm-lock.yaml
  vite.config.ts
  components.json
  src/
    main.tsx
    routeTree.gen.ts
    routes/
      __root.tsx
      _app/
        route.tsx
        index.tsx
        items.tsx
        raw-data.tsx
        insights.tsx
        buffers.tsx
        memory-threads.tsx
        item-graph.tsx
        config.tsx
        retrieve.tsx
    lib/
      api-client.ts
      api-client.test.ts
      format.ts
      format.test.ts
      memory-scope.ts
      memory-scope.test.ts
      query-client.ts
      route-search.ts
    hooks/
      use-table-url-state.ts
      use-table-url-state.test.ts
    components/
      data-table/
      layout/
      ui/
      theme-switch.tsx
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
        confirm-result-dialog.tsx
        data-state.tsx
        json-viewer.tsx
        memory-scope-picker.tsx
        page-toolbar.tsx
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

Do not add `memind-ui` to the root `pom.xml`.

## Shared Rules

- Keep UI text direct and operational; do not add landing-page or marketing content.
- Use shadcn/ui components already present in the template before adding new UI primitives.
- Use semantic Tailwind tokens and shadcn variants; avoid raw color utility styling for status.
- Use `pageNo` and `pageSize` in URL state and API calls.
- Delete and update mutations operate on selected row ids only.
- Commit after each task that leaves tests and build checks in a coherent state.

## API Scope Contract

Use one memory scope input in the app shell, but pass parameters in the shape each server endpoint actually accepts:

| Area | Parameter shape | UI behavior |
| --- | --- | --- |
| Dashboard, Buffers, Item Graph | `memoryId` query param | Empty scope is allowed and means all local memory data returned by the server. |
| Items, Raw Data, Insights, Memory Threads list | `userId` required only when scope is set, `agentId` optional | Empty scope is allowed for broad list browsing. When scope is set, split `userId:agentId` into server query params. |
| `GET /admin/v1/memory-threads/{threadKey}` | `userId` required, `agentId` optional | Disable the detail drawer and show a scope prompt when the global scope has no `userId`. |
| `GET /admin/v1/memory-threads/{threadKey}/items` | `userId` required, `agentId` optional | Load memberships only after the same `userId` requirement is satisfied. |
| `GET /admin/v1/items/{itemId}/memory-threads` | `userId` required, `agentId` optional | Disable associated thread lookup from item detail when the global scope has no `userId`. |
| Memory Thread status and rebuild | `userId` required, `agentId` optional | Disable status/rebuild actions until `userId` is available. |
| Retrieve | `userId` and `agentId` required in the form | Prefill from global scope, but allow local form override without mutating the global scope. |

---

### Task 1: Scaffold `memind-ui` From Template

**Files:**
- Create: `memind-ui/`
- Modify: `memind-ui/package.json`
- Modify: `memind-ui/README.md`
- Modify: `memind-ui/vite.config.ts`
- Verify unchanged: `pom.xml`

- [ ] **Step 1: Copy the template into the new module**

Run:

```bash
mkdir -p memind-ui
rsync -a --exclude .git /Users/zhengyate/dev/git-project/shadcn-admin/ memind-ui/
```

Expected: `memind-ui/package.json`, `memind-ui/src`, and `memind-ui/components.json` exist.

- [ ] **Step 2: Update package metadata**

Set `memind-ui/package.json` top-level fields to:

```json
{
  "name": "memind-ui",
  "private": true,
  "version": "0.1.0",
  "type": "module",
  "engines": {
    "node": "^20.19.0 || ^22.12.0 || >=24.0.0",
    "pnpm": ">=9.0.0"
  },
  "packageManager": "pnpm@10.15.0"
}
```

Keep existing scripts initially. Remove dependencies only after their imports are deleted in Task 2.

- [ ] **Step 3: Configure Vite proxy**

Modify `memind-ui/vite.config.ts` so `server.proxy` contains:

```ts
server: {
  proxy: {
    '/admin': {
      target: 'http://127.0.0.1:8366',
      changeOrigin: true,
    },
    '/open': {
      target: 'http://127.0.0.1:8366',
      changeOrigin: true,
    },
  },
},
```

- [ ] **Step 4: Replace README content**

`memind-ui/README.md` must document:

- local-only unauthenticated admin UI warning
- Node and pnpm versions from `package.json`
- `pnpm install`, `pnpm dev`, `pnpm lint`, `pnpm test`, `pnpm build`
- local server assumption: `http://127.0.0.1:8366`
- Vite proxy paths: `/admin`, `/open`

- [ ] **Step 5: Verify root Maven isolation**

Run:

```bash
git diff -- pom.xml
```

Expected: no output.

- [ ] **Step 6: Commit scaffold**

Run:

```bash
git add memind-ui
git commit -m "feat: scaffold memind ui module"
```

---

### Task 2: Remove Auth, Demo Surfaces, and Template Preferences

**Files:**
- Delete: `memind-ui/src/routes/(auth)/`
- Delete: `memind-ui/src/routes/clerk/`
- Delete: `memind-ui/src/features/auth/`
- Delete: `memind-ui/src/features/apps/`
- Delete: `memind-ui/src/features/chats/`
- Delete: `memind-ui/src/features/tasks/`
- Delete: `memind-ui/src/features/users/`
- Delete: `memind-ui/src/features/settings/`
- Delete: `memind-ui/src/stores/auth-store.ts`
- Delete: `memind-ui/src/stores/auth-store.test.ts`
- Delete: `memind-ui/src/components/config-drawer.tsx`
- Delete: `memind-ui/src/components/config-drawer.test.tsx`
- Delete: `memind-ui/src/components/search.tsx`
- Delete: `memind-ui/src/components/command-menu.tsx`
- Delete: `memind-ui/src/components/profile-dropdown.tsx`
- Delete: `memind-ui/src/components/sign-out-dialog.tsx`
- Delete: `memind-ui/src/components/sign-out-dialog.test.tsx`
- Modify: `memind-ui/package.json`
- Move: `memind-ui/src/routes/_authenticated` to `memind-ui/src/routes/_app`
- Rename: `memind-ui/src/components/layout/authenticated-layout.tsx` to `memind-ui/src/components/layout/app-layout.tsx`
- Modify: `memind-ui/src/components/layout/app-sidebar.tsx`
- Modify: `memind-ui/src/components/layout/app-title.tsx`
- Modify: `memind-ui/src/components/layout/data/sidebar-data.ts`

- [ ] **Step 1: Rename route and layout names**

Move route folder and layout file:

```bash
mv memind-ui/src/routes/_authenticated memind-ui/src/routes/_app
mv memind-ui/src/components/layout/authenticated-layout.tsx memind-ui/src/components/layout/app-layout.tsx
```

Change all imports and identifiers from `AuthenticatedLayout` to `AppLayout`.

- [ ] **Step 2: Remove auth and demo imports**

Run:

```bash
rg -n "Clerk|auth|sign-in|sign-out|Authenticated|users|tasks|apps|chats|settings|ConfigDrawer|SearchProvider|CommandMenu" memind-ui/src memind-ui/package.json
```

Expected after edits: no auth, sign-in, sign-out, Clerk, or demo page imports remain. `settings` may only appear inside generic TypeScript or CSS terms unrelated to routes.

- [ ] **Step 3: Remove unused dependencies**

Remove these dependencies from `memind-ui/package.json` when no imports remain:

```text
@clerk/react
@faker-js/faker
```

Keep `cmdk` only if `memind-ui/src/components/data-table/faceted-filter.tsx` still imports `memind-ui/src/components/ui/command.tsx`. Remove global search files either way.

- [ ] **Step 4: Replace sidebar data**

`memind-ui/src/components/layout/data/sidebar-data.ts` should define these nav entries only:

```ts
export const sidebarData = {
  navGroups: [
    {
      title: 'Memind',
      items: [
        { title: 'Dashboard', url: '/', icon: LayoutDashboard },
        { title: 'Memory Items', url: '/items', icon: Database },
        { title: 'Raw Data', url: '/raw-data', icon: FileText },
        { title: 'Insights', url: '/insights', icon: Lightbulb },
        { title: 'Buffers', url: '/buffers', icon: Inbox },
        { title: 'Memory Threads', url: '/memory-threads', icon: GitBranch },
        { title: 'Item Graph', url: '/item-graph', icon: Network },
        { title: 'Config', url: '/config', icon: SlidersHorizontal },
        { title: 'Retrieve', url: '/retrieve', icon: Search },
      ],
    },
  ],
}
```

Use `lucide-react` icons because the template uses `lucide-react`.

- [ ] **Step 5: Verify route tree regenerates**

Run:

```bash
cd memind-ui
pnpm build
```

Expected: build either passes or fails only on missing Memind route components that are introduced in Task 6. If it fails for Clerk, auth store, demo routes, or `_authenticated`, finish cleanup before continuing.

- [ ] **Step 6: Commit cleanup**

Run:

```bash
git add memind-ui
git commit -m "refactor: remove auth and demo surfaces from memind ui"
```

---

### Task 3: Define API Types and API Client

**Files:**
- Create: `memind-ui/src/features/types.ts`
- Create: `memind-ui/src/lib/api-client.ts`
- Create: `memind-ui/src/lib/api-client.test.ts`
- Create: `memind-ui/src/features/api/dashboard.ts`
- Create: `memind-ui/src/features/api/items.ts`
- Create: `memind-ui/src/features/api/raw-data.ts`
- Create: `memind-ui/src/features/api/insights.ts`
- Create: `memind-ui/src/features/api/buffers.ts`
- Create: `memind-ui/src/features/api/memory-threads.ts`
- Create: `memind-ui/src/features/api/item-graph.ts`
- Create: `memind-ui/src/features/api/config.ts`
- Create: `memind-ui/src/features/api/retrieve.ts`

- [ ] **Step 1: Write API client tests**

Create tests covering:

```ts
it('unwraps success ApiResult data')
it('allows async success code 200 without data')
it('throws ApiError with traceId for server errors')
it('throws ApiError for network failures')
it('serializes query params without undefined values')
```

Run:

```bash
cd memind-ui
pnpm test -- src/lib/api-client.test.ts
```

Expected: fails because `api-client.ts` is not implemented yet.

- [ ] **Step 2: Implement API client**

`api-client.ts` exports:

```ts
export type ApiResult<T> = {
  code: string
  message?: string
  data?: T
  timestamp: string
  traceId?: string
}

export type PageResult<T> = {
  total: number
  list: T[]
  current: number
}

export type ApiError = {
  status?: number
  code?: string
  message: string
  traceId?: string
  details?: unknown
}

export async function apiGet<T>(path: string, query?: Record<string, unknown>): Promise<T>
export async function apiPost<T>(path: string, body?: unknown, query?: Record<string, unknown>): Promise<T>
export async function apiPatch<T>(path: string, body?: unknown, query?: Record<string, unknown>): Promise<T>
export async function apiPut<T>(path: string, body?: unknown, query?: Record<string, unknown>): Promise<T>
export async function apiDelete<T>(path: string, body?: unknown, query?: Record<string, unknown>): Promise<T>
```

Behavior:

- unwrap `ApiResult<T>`
- accept `code === "success"` as data success
- accept `code === "200"` as async success
- omit `undefined`, `null`, and empty-string query values
- keep users on the current page by never redirecting on `401`

- [ ] **Step 3: Define TypeScript DTOs**

`features/types.ts` must include manually maintained request and response types from the design spec:

- `AdminDashboardView`
- `AdminItemView`
- `AdminRawDataView`
- `AdminInsightView`
- `ConversationBufferView`
- `InsightBufferView`
- `InsightBufferGroupView`
- `AdminMemoryThreadView`
- `AdminMemoryThreadItemView`
- `AdminItemMemoryThreadView`
- `AdminMemoryThreadStatusView`
- `ItemGraphSummaryView`
- `GraphEntityView`
- `GraphEntityDetailView`
- `GraphAliasView`
- `GraphMentionView`
- `GraphItemLinkView`
- `GraphCooccurrenceView`
- `GraphBatchView`
- `MemoryOptionsGetResponse`
- `RetrieveMemoryResponse`
- delete/update result types

- [ ] **Step 4: Implement endpoint wrappers**

Each file in `features/api/` exports typed functions with server parameter names:

```ts
export type PageParams = { pageNo?: number; pageSize?: number }
export type MemoryIdFilter = { memoryId?: string }
export type UserAgentFilter = { userId?: string; agentId?: string }
```

Endpoint wrappers must not expose `page` or `limit`.

- [ ] **Step 5: Run API tests**

Run:

```bash
cd memind-ui
pnpm test -- src/lib/api-client.test.ts
```

Expected: pass.

- [ ] **Step 6: Commit API foundation**

Run:

```bash
git add memind-ui/src/features memind-ui/src/lib/api-client.ts memind-ui/src/lib/api-client.test.ts
git commit -m "feat: add memind ui api client and contracts"
```

---

### Task 4: Memory Scope, Formatting, and Query Client

**Files:**
- Create: `memind-ui/src/lib/memory-scope.ts`
- Create: `memind-ui/src/lib/memory-scope.test.ts`
- Create: `memind-ui/src/lib/format.ts`
- Create: `memind-ui/src/lib/format.test.ts`
- Create: `memind-ui/src/lib/query-client.ts`
- Create: `memind-ui/src/features/components/memory-scope-picker.tsx`
- Modify: `memind-ui/src/main.tsx`

- [ ] **Step 1: Write memory scope tests**

Test cases:

```ts
parseMemoryScope('alice:agent-a') -> { memoryId: 'alice:agent-a', userId: 'alice', agentId: 'agent-a', hasUserId: true, hasAgentId: true }
parseMemoryScope('alice') -> { memoryId: 'alice', userId: 'alice', agentId: '', hasUserId: true, hasAgentId: false }
parseMemoryScope('') -> { memoryId: '', userId: '', agentId: '', hasUserId: false, hasAgentId: false }
toUserAgentQuery('alice') -> { userId: 'alice' }
toUserAgentQuery('alice:agent-a') -> { userId: 'alice', agentId: 'agent-a' }
requireRetrieveScope('alice') -> validation error
requireRetrieveScope('alice:agent-a') -> userId and agentId
```

Run:

```bash
cd memind-ui
pnpm test -- src/lib/memory-scope.test.ts
```

Expected: fails before helper exists.

- [ ] **Step 2: Implement memory scope helper**

Exports:

```ts
export type ParsedMemoryScope = {
  memoryId: string
  userId: string
  agentId: string
  hasUserId: boolean
  hasAgentId: boolean
}

export function parseMemoryScope(value: string): ParsedMemoryScope
export function toMemoryIdQuery(value: string): { memoryId?: string }
export function toUserAgentQuery(value: string): { userId?: string; agentId?: string }
export function requireUserScope(value: string): ParsedMemoryScope
export function requireRetrieveScope(value: string): ParsedMemoryScope
```

- [ ] **Step 3: Implement format helpers**

Exports:

```ts
export function formatDateTime(value: string | null | undefined): string
export function formatNumber(value: number | null | undefined): string
export function compactJson(value: unknown): string
export function truncateText(value: string | null | undefined, maxLength: number): string
```

Tests cover null display as `-`, long text truncation, and JSON fallback for unknown values.

- [ ] **Step 4: Add QueryClient**

`query-client.ts` exports one `QueryClient` with:

- retry disabled for mutations
- stale time of 30 seconds for read queries
- global error handling that keeps errors displayable and does not redirect

- [ ] **Step 5: Implement scope picker**

`memory-scope-picker.tsx` stores `memoryId` in URL/localStorage and exposes a text input in the header. It must:

- show placeholder `userId:agentId`
- keep empty value valid
- update page filters through URL state where the current page supports scope

- [ ] **Step 6: Run tests**

Run:

```bash
cd memind-ui
pnpm test -- src/lib/memory-scope.test.ts src/lib/format.test.ts
```

Expected: pass.

- [ ] **Step 7: Commit scope and formatting**

Run:

```bash
git add memind-ui/src/lib memind-ui/src/features/components/memory-scope-picker.tsx memind-ui/src/main.tsx
git commit -m "feat: add memory scope and query foundation"
```

---

### Task 5: Shared UI Components and Data States

**Files:**
- Create: `memind-ui/src/features/components/json-viewer.tsx`
- Create: `memind-ui/src/features/components/data-state.tsx`
- Create: `memind-ui/src/features/components/confirm-result-dialog.tsx`
- Create: `memind-ui/src/features/components/server-status.tsx`
- Create: `memind-ui/src/features/components/server-status.test.tsx`
- Create: `memind-ui/src/features/components/confirm-result-dialog.test.tsx`
- Modify: `memind-ui/src/components/layout/app-sidebar.tsx`
- Modify: `memind-ui/src/components/layout/header.tsx`

- [ ] **Step 1: Write server status test**

Test states:

```ts
connecting before the first health result
connected after GET /open/v1/health returns success
disconnected after a network error
polls with a 30 second interval
```

Run:

```bash
cd memind-ui
pnpm test -- src/features/components/server-status.test.tsx
```

Expected: fails before component exists.

- [ ] **Step 2: Implement server status**

`server-status.tsx` uses React Query on `GET /open/v1/health` and renders a compact badge in the sidebar footer:

- `connecting`
- `connected`
- `disconnected`

- [ ] **Step 3: Implement common data state components**

`data-state.tsx` exports:

```ts
export function PageLoading()
export function TableLoading({ columns }: { columns: number })
export function EmptyState({ title, description }: { title: string; description?: string })
export function PageError({ message, traceId, onRetry }: { message: string; traceId?: string; onRetry?: () => void })
export function ScopeRequiredState({ message }: { message: string })
```

Use shadcn `Skeleton`, `Alert`, `Button`, and `Empty` if the template contains an `Empty` component. If no `Empty` component exists, use `Alert` plus a muted paragraph.

- [ ] **Step 4: Implement JSON viewer**

`json-viewer.tsx` renders collapsed JSON by default and has an expand/collapse trigger. It must handle `null`, arrays, strings, and objects without throwing.

- [ ] **Step 5: Implement confirm result dialog**

`confirm-result-dialog.tsx` supports:

- selected row count
- destructive action description
- cascading delete warning for raw data and graph entities
- result counts returned by the server

- [ ] **Step 6: Run component tests**

Run:

```bash
cd memind-ui
pnpm test -- src/features/components/server-status.test.tsx src/features/components/confirm-result-dialog.test.tsx
```

Expected: pass.

- [ ] **Step 7: Commit shared components**

Run:

```bash
git add memind-ui/src/features/components memind-ui/src/components/layout
git commit -m "feat: add memind ui shared status and state components"
```

---

### Task 6: Routes, App Shell, and URL State

**Files:**
- Modify: `memind-ui/src/routes/__root.tsx`
- Modify: `memind-ui/src/routes/_app/route.tsx`
- Create: `memind-ui/src/routes/_app/index.tsx`
- Create: `memind-ui/src/routes/_app/items.tsx`
- Create: `memind-ui/src/routes/_app/raw-data.tsx`
- Create: `memind-ui/src/routes/_app/insights.tsx`
- Create: `memind-ui/src/routes/_app/buffers.tsx`
- Create: `memind-ui/src/routes/_app/memory-threads.tsx`
- Create: `memind-ui/src/routes/_app/item-graph.tsx`
- Create: `memind-ui/src/routes/_app/config.tsx`
- Create: `memind-ui/src/routes/_app/retrieve.tsx`
- Modify: `memind-ui/src/hooks/use-table-url-state.ts`
- Modify: `memind-ui/src/hooks/use-table-url-state.test.ts`

- [ ] **Step 1: Update table URL state tests**

Add tests for:

```ts
pageKey "pageNo" maps URL pageNo to TanStack pageIndex
pageSizeKey "pageSize" maps URL pageSize to TanStack pageSize
filter changes clear pageNo
tab search param is preserved when table filters change
```

Run:

```bash
cd memind-ui
pnpm test -- src/hooks/use-table-url-state.test.ts
```

Expected: fail only for new cases until hook updates are complete.

- [ ] **Step 2: Wire app routes**

Each route file imports its feature page component and validates search params used by that page. Route search params must include:

- `memoryId`
- `pageNo`
- `pageSize`
- page-specific filters
- `tab` for Buffers and Item Graph

- [ ] **Step 3: Regenerate route tree**

Run:

```bash
cd memind-ui
pnpm build
```

Expected: `src/routeTree.gen.ts` regenerates and no route references `_authenticated`.

- [ ] **Step 4: Commit routing foundation**

Run:

```bash
git add memind-ui/src/routes memind-ui/src/routeTree.gen.ts memind-ui/src/hooks/use-table-url-state.ts memind-ui/src/hooks/use-table-url-state.test.ts
git commit -m "feat: add memind ui routes and url state"
```

---

### Task 7: Dashboard Page

**Files:**
- Create: `memind-ui/src/features/dashboard/index.tsx`
- Create: `memind-ui/src/features/dashboard/dashboard-page.test.tsx`
- Create: `memind-ui/src/features/dashboard/dashboard-cards.tsx`
- Create: `memind-ui/src/features/dashboard/dashboard-activity-chart.tsx`
- Create: `memind-ui/src/features/dashboard/dashboard-breakdown.tsx`
- Modify: `memind-ui/src/features/api/dashboard.ts`

- [ ] **Step 1: Write Dashboard tests**

Test:

```ts
renders total metric cards
renders zero-state copy when all counts are zero
links conversationPending to /buffers?tab=conversations&state=pending
links insightUngrouped to /buffers?tab=insights&state=ungrouped
links graphBatchRepairRequired to /item-graph?tab=batches&state=REPAIR_REQUIRED
preserves memoryId on backlog links
```

Run:

```bash
cd memind-ui
pnpm test -- src/features/dashboard/dashboard-page.test.tsx
```

Expected: fails before page exists.

- [ ] **Step 2: Implement Dashboard**

The page calls `GET /admin/v1/dashboard` with:

- `memoryId`
- `days`, default `7`

Render:

- totals
- backlog cards with deep links
- activity chart for raw data, items, insights
- breakdown lists
- health signals

- [ ] **Step 3: Verify Dashboard**

Run:

```bash
cd memind-ui
pnpm test -- src/features/dashboard/dashboard-page.test.tsx
pnpm lint
```

Expected: pass.

- [ ] **Step 4: Commit Dashboard**

Run:

```bash
git add memind-ui/src/features/dashboard memind-ui/src/features/api/dashboard.ts
git commit -m "feat: add memind dashboard page"
```

---

### Task 8: Items, Raw Data, and Insights Pages

**Files:**
- Create: `memind-ui/src/features/items/`
- Create: `memind-ui/src/features/raw-data/`
- Create: `memind-ui/src/features/insights/`
- Modify: `memind-ui/src/features/api/items.ts`
- Modify: `memind-ui/src/features/api/raw-data.ts`
- Modify: `memind-ui/src/features/api/insights.ts`
- Test: `memind-ui/src/features/items/items-page.test.tsx`
- Test: `memind-ui/src/features/raw-data/raw-data-page.test.tsx`
- Test: `memind-ui/src/features/insights/insights-page.test.tsx`

- [ ] **Step 1: Write selected-row delete tests**

For each page test:

```ts
selecting two rows sends only the selected ids
delete confirmation text does not claim filtered-result deletion
successful delete invalidates the list query and dashboard query
empty state is visible for an empty list
```

- [ ] **Step 2: Implement list tables**

Each table uses server columns from the design spec, always shows `memoryId`, uses `pageNo` and `pageSize`, and reads filters from URL state.

Items:

- detail drawer shows content, metadata, raw data type, vector id, content hash, timestamps
- associated thread section is disabled with a scope prompt when `userId` is missing
- delete body is `{ itemIds: number[] }`

Raw Data:

- detail drawer shows caption, segment JSON, metadata JSON, content id, caption vector id, timestamps
- delete body is `{ rawDataIds: string[] }`
- confirmation explains associated memory items are also deleted by server behavior

Insights:

- detail drawer shows content, points, categories, parent and child insight ids, summary embedding collapsed by default
- delete body is `{ insightIds: number[] }`

- [ ] **Step 3: Run page tests**

Run:

```bash
cd memind-ui
pnpm test -- src/features/items/items-page.test.tsx src/features/raw-data/raw-data-page.test.tsx src/features/insights/insights-page.test.tsx
```

Expected: pass.

- [ ] **Step 4: Commit memory list pages**

Run:

```bash
git add memind-ui/src/features/items memind-ui/src/features/raw-data memind-ui/src/features/insights memind-ui/src/features/api/items.ts memind-ui/src/features/api/raw-data.ts memind-ui/src/features/api/insights.ts
git commit -m "feat: add memory item raw data and insight pages"
```

---

### Task 9: Buffers Page

**Files:**
- Create: `memind-ui/src/features/buffers/index.tsx`
- Create: `memind-ui/src/features/buffers/buffers-page.test.tsx`
- Create: `memind-ui/src/features/buffers/conversations-table.tsx`
- Create: `memind-ui/src/features/buffers/insight-buffers-table.tsx`
- Create: `memind-ui/src/features/buffers/insight-groups-table.tsx`
- Modify: `memind-ui/src/features/api/buffers.ts`

- [ ] **Step 1: Write Buffers tests**

Test:

```ts
default tab is conversations
tab=insights opens insight buffers
tab=groups opens insight groups
conversation state default is pending
insight state default is unbuilt
state options include ungrouped and grouped
mark extracted sends selected conversation ids
mark built sends selected insight buffer ids and built flag
conversation row opens detail drawer with full content
```

- [ ] **Step 2: Implement tabs and tables**

Use URL search `tab` values:

- `conversations`
- `insights`
- `groups`

Conversation columns:

- id, session id, memory id, role, content preview, user name, source client, extracted, timestamp, created at, updated at

Insight buffer columns:

- id, memory id, insight type name, item id, group name, built, created at, updated at

Insight group columns:

- memory id, insight type name, group name, total, unbuilt, built

- [ ] **Step 3: Implement actions**

Actions:

- conversation mark extracted: `{ ids: number[] }`
- conversation delete: `{ ids: number[] }`
- insight group update: `{ ids: number[], groupName }`
- insight built update: `{ ids: number[], built }`
- insight buffer delete: `{ ids: number[] }`

- [ ] **Step 4: Run Buffers tests**

Run:

```bash
cd memind-ui
pnpm test -- src/features/buffers/buffers-page.test.tsx
```

Expected: pass.

- [ ] **Step 5: Commit Buffers**

Run:

```bash
git add memind-ui/src/features/buffers memind-ui/src/features/api/buffers.ts
git commit -m "feat: add memory buffer management page"
```

---

### Task 10: Memory Threads Page

**Files:**
- Create: `memind-ui/src/features/memory-threads/index.tsx`
- Create: `memind-ui/src/features/memory-threads/memory-threads-page.test.tsx`
- Create: `memind-ui/src/features/memory-threads/thread-detail-drawer.tsx`
- Create: `memind-ui/src/features/memory-threads/thread-status-panel.tsx`
- Modify: `memind-ui/src/features/api/memory-threads.ts`

- [ ] **Step 1: Write Memory Threads tests**

Test:

```ts
status filter default omits status query param
status options include ACTIVE DORMANT CLOSED
detail drawer is blocked when userId is missing
detail drawer loads thread and memberships when userId is present
membership table shows role primary and relevanceWeight
rebuild is blocked when userId is missing
rebuild confirmation refreshes status and list
```

- [ ] **Step 2: Implement list and detail**

List columns:

- memory id, thread key, display label/headline, thread type, lifecycle status, object state, member count, event count, last event at

Detail drawer:

- snapshot JSON
- anchor kind/key
- opened/closed timestamps
- memberships with item id, role, primary, relevance weight, created at, updated at

- [ ] **Step 3: Implement status and rebuild**

Status panel shows:

- projection state
- pending count
- failed count
- rebuild in progress
- last processed item id
- materialization policy version
- updated at
- invalidation reason

- [ ] **Step 4: Run Memory Threads tests**

Run:

```bash
cd memind-ui
pnpm test -- src/features/memory-threads/memory-threads-page.test.tsx
```

Expected: pass.

- [ ] **Step 5: Commit Memory Threads**

Run:

```bash
git add memind-ui/src/features/memory-threads memind-ui/src/features/api/memory-threads.ts
git commit -m "feat: add memory thread management page"
```

---

### Task 11: Item Graph Page

**Files:**
- Create: `memind-ui/src/features/item-graph/index.tsx`
- Create: `memind-ui/src/features/item-graph/item-graph-page.test.tsx`
- Create: `memind-ui/src/features/item-graph/summary-tab.tsx`
- Create: `memind-ui/src/features/item-graph/entities-tab.tsx`
- Create: `memind-ui/src/features/item-graph/entity-detail-drawer.tsx`
- Create: `memind-ui/src/features/item-graph/aliases-tab.tsx`
- Create: `memind-ui/src/features/item-graph/mentions-tab.tsx`
- Create: `memind-ui/src/features/item-graph/item-links-tab.tsx`
- Create: `memind-ui/src/features/item-graph/cooccurrences-tab.tsx`
- Create: `memind-ui/src/features/item-graph/batches-tab.tsx`
- Modify: `memind-ui/src/features/api/item-graph.ts`

- [ ] **Step 1: Write Item Graph tests**

Test:

```ts
default tab is summary
tab=batches opens batches
summary renders entity alias mention link and cooccurrence counts
entity delete is disabled without memoryId
entity detail drawer renders aliases mention count top items top cooccurrences and overlap count
alias delete sends selected row ids
mention delete sends selected row ids
item link delete sends selected row ids
cooccurrence delete sends selected row ids
batch state default omits state query param
```

- [ ] **Step 2: Implement Summary tab**

Show:

- metric cards for entity, alias, mention, item link, cooccurrence counts
- grouped breakdowns for batch state, item link type, entity type

- [ ] **Step 3: Implement graph list tabs**

Columns:

- Entities: id, memory id, entity key, display name, entity type, created at, updated at
- Aliases: id, memory id, entity key, normalized alias, entity type, evidence count, created at
- Mentions: id, memory id, item id, entity key, confidence, created at
- Item Links: id, memory id, source item id, target item id, link type, relation code, evidence source, strength, created at
- Cooccurrences: id, memory id, left entity key, right entity key, cooccurrence count, created at
- Batches: id, memory id, extraction batch id, state, error message, retry promotion supported, created at, updated at

- [ ] **Step 4: Implement graph deletion**

Requests:

- entities: `{ memoryId: string, entityKeys: string[] }`
- aliases, mentions, item links, cooccurrences: `{ ids: number[] }`

Do not add delete actions to Batches.

- [ ] **Step 5: Run Item Graph tests**

Run:

```bash
cd memind-ui
pnpm test -- src/features/item-graph/item-graph-page.test.tsx
```

Expected: pass.

- [ ] **Step 6: Commit Item Graph**

Run:

```bash
git add memind-ui/src/features/item-graph memind-ui/src/features/api/item-graph.ts
git commit -m "feat: add item graph management page"
```

---

### Task 12: Config Page

**Files:**
- Create: `memind-ui/src/features/config/index.tsx`
- Create: `memind-ui/src/features/config/config-page.test.tsx`
- Create: `memind-ui/src/features/config/config-value-editor.tsx`
- Modify: `memind-ui/src/features/api/config.ts`

- [ ] **Step 1: Write Config tests**

Test:

```ts
renders config grouped by section key
boolean option uses a switch
integer and double options use number input
string option uses text input
string option with enum constraints uses select
save button is disabled before edits
editing two fields sends one PUT with complete config object
dirty state is visible after an edit
conflict response shows refresh guidance
leaving with unsaved edits prompts for confirmation
```

- [ ] **Step 2: Implement value editor**

Map `MemoryOptionItemView.type`:

- `boolean` -> switch
- `integer` -> number input with integer parsing
- `double` -> number input with floating point parsing
- `string` -> text input, or select when constraints provide an enum/options array
- unknown type -> JSON preview plus text/JSON edit fallback

- [ ] **Step 3: Implement draft and save**

Rules:

- keep local draft
- show modified count
- global Save sends full config plus `expectedVersion`
- no per-row auto-save
- on `409`, keep draft visible and ask user to refresh

- [ ] **Step 4: Run Config tests**

Run:

```bash
cd memind-ui
pnpm test -- src/features/config/config-page.test.tsx
```

Expected: pass.

- [ ] **Step 5: Commit Config**

Run:

```bash
git add memind-ui/src/features/config memind-ui/src/features/api/config.ts
git commit -m "feat: add memory options config page"
```

---

### Task 13: Retrieve Debug Page

**Files:**
- Create: `memind-ui/src/features/retrieve/index.tsx`
- Create: `memind-ui/src/features/retrieve/retrieve-page.test.tsx`
- Create: `memind-ui/src/features/retrieve/retrieve-trace.tsx`
- Modify: `memind-ui/src/features/api/retrieve.ts`

- [ ] **Step 1: Write Retrieve tests**

Test:

```ts
prefills userId and agentId from global memory scope
allows local userId and agentId override without changing global scope
blocks submit when userId is empty
blocks submit when agentId is empty
renders retrieved items insights raw data and evidences
trace section is collapsed by default
expanded trace shows stage method status duration and counts
attributes and candidates render inside collapsed JSON viewer
```

- [ ] **Step 2: Implement Retrieve form**

Inputs:

- userId
- agentId
- query
- strategy: `SIMPLE` or `DEEP`
- trace toggle

Submit body:

```ts
{
  userId,
  agentId,
  query,
  strategy,
  trace,
}
```

- [ ] **Step 3: Implement trace renderer**

Trace UI:

- section collapsed by default
- stages as collapsible list or timeline
- badges for degraded and skipped
- merge and finalResults as summary cards
- attributes and candidates in JSON viewer

- [ ] **Step 4: Run Retrieve tests**

Run:

```bash
cd memind-ui
pnpm test -- src/features/retrieve/retrieve-page.test.tsx
```

Expected: pass.

- [ ] **Step 5: Commit Retrieve**

Run:

```bash
git add memind-ui/src/features/retrieve memind-ui/src/features/api/retrieve.ts
git commit -m "feat: add memory retrieve debug page"
```

---

### Task 14: Integration Polish and Documentation

**Files:**
- Modify: `memind-ui/README.md`
- Modify: `memind-ui/package.json`
- Modify: `memind-ui/src/components/layout/app-sidebar.tsx`
- Modify: `memind-ui/src/components/layout/header.tsx`
- Modify: `memind-ui/src/styles/index.css`
- Modify: `memind-ui/src/styles/theme.css`

- [ ] **Step 1: Remove remaining unused template code**

Run:

```bash
cd memind-ui
rg -n "Clerk|sign-in|sign-up|sign-out|Authenticated|shadcn-admin|Tasks|Users|Apps|Chats|ConfigDrawer|CommandMenu|SearchProvider|FontProvider" src package.json README.md
```

Expected: no output except references explaining removed template features in documentation.

- [ ] **Step 2: Run dependency cleanup**

Run:

```bash
cd memind-ui
pnpm install --lockfile-only
pnpm knip
```

Expected: lockfile updates and no unused runtime imports remain. If `knip` reports generated route files or shadcn component exports, configure `knip.config.ts` narrowly for those generated files.

- [ ] **Step 3: Verify visual states manually**

Start the dev server:

```bash
cd memind-ui
pnpm dev
```

Check:

- empty dashboard
- disconnected server status with server stopped
- list empty states
- detail drawer loading state
- mobile sidebar does not overlap content

- [ ] **Step 4: Update README screenshots section**

Add a text section listing first-run expectations:

- start `memind-server` separately
- open the Vite URL shown by `pnpm dev`
- set global memory scope when detail views require `userId`
- do not expose the unauthenticated UI publicly

- [ ] **Step 5: Commit polish**

Run:

```bash
git add memind-ui
git commit -m "docs: document memind ui setup and local usage"
```

---

### Task 15: Final Verification

**Files:**
- Verify: `memind-ui/`
- Verify: `pom.xml`

- [ ] **Step 1: Run full frontend checks**

Run:

```bash
cd memind-ui
pnpm lint
pnpm test
pnpm build
```

Expected: all commands exit `0`.

- [ ] **Step 2: Verify Maven isolation**

Run:

```bash
git diff -- pom.xml
```

Expected: no output.

- [ ] **Step 3: Verify no auth/demo residue**

Run:

```bash
cd memind-ui
rg -n "Clerk|sign-in|sign-up|sign-out|Authenticated|auth-store|shadcn-admin|Tasks|Users|Apps|Chats" src package.json README.md
```

Expected: no output.

- [ ] **Step 4: Verify implementation matches design**

Run:

```bash
rg -n "GET /admin/v1|DELETE /admin/v1|POST /open/v1|PUT /admin/v1|PATCH /admin/v1" docs/superpowers/specs/2026-04-30-memind-ui-design.md
```

For each endpoint in the output, confirm there is a typed wrapper in `memind-ui/src/features/api/` or a documented non-goal for open ingestion endpoints.

- [ ] **Step 5: Final commit**

Run:

```bash
git add memind-ui docs/superpowers/specs/2026-04-30-memind-ui-design.md
git commit -m "feat: add local memind ui"
```

Use this final commit only if Task 14 did not already leave a clean, complete commit. If all work is already committed task-by-task, skip this final commit and report the latest passing commit.

---

## Plan Self-Review

- Spec coverage: repository placement, template cleanup, Vite proxy, local-only security warning, API contracts, memory scope parsing, URL table state, loading and empty states, Dashboard, Items, Raw Data, Insights, Buffers, Memory Threads, Item Graph, Config, Retrieve, docs, and final verification are covered by Tasks 1 through 15.
- Type consistency: delete request bodies use `itemIds: number[]`, `rawDataIds: string[]`, `insightIds: number[]`, graph entity `memoryId + entityKeys: string[]`, and graph row `ids: number[]`, matching the server DTOs documented in the design spec.
- Routing consistency: route group is `_app`; tab state for Buffers and Item Graph uses the `tab` search parameter; table pagination uses `pageNo` and `pageSize`.
- Verification coverage: task-level tests run before implementation for core helpers and pages, and final verification runs `pnpm lint`, `pnpm test`, and `pnpm build`.
