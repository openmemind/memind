# TypeScript Client Design

Date: 2026-05-12
Branch: `feat/typescript-memind-client`

## Goal

Provide an official TypeScript client for the Memind open API so TypeScript users can interact with
Memind without hand-writing HTTP calls or duplicating the Java/Python client contracts.

The first version covers only the stable public integration API under `/open/v1`:

- `GET /open/v1/health`
- `POST /open/v1/memory/sync/extract`
- `POST /open/v1/memory/sync/add-message`
- `POST /open/v1/memory/sync/commit`
- `POST /open/v1/memory/retrieve`

Admin APIs under `/admin/v1` are intentionally out of scope. They are UI and operations focused,
broader, and more likely to change. If they become necessary later, they should be designed as a
separate `admin` resource or a separate package.

## Existing Contracts

The TypeScript client must align with the current server and official clients:

- The server response envelope is always `{ "data": ... }` for success or `{ "error": ... }` for
  failure.
- Response errors contain `error.code`, `error.message`, and optional `error.details`.
- Server JSON uses camelCase field names.
- Existing Python and Java clients expose the same public memory operations.
- Python client behavior is the best behavioral reference for TypeScript because it includes
  environment-based configuration, typed models, retry policy, and clear error mapping.
- Mutating memory operations are not retried by default. This protects callers from duplicated
  extraction, buffered messages, or commits after transient failures.

## Package Shape

Add a new package at:

```text
memind-clients/typescript/
```

Recommended npm package name:

```text
@openmemind/memind
```

If the npm scope is not available at release time, use a temporary package name only after an
explicit release decision. The package should still be structured so it can move to the scoped name.

The package is a pure TypeScript library with no required runtime dependencies. It uses the runtime's
native `fetch` implementation.

Supported runtimes:

- Node.js 20 or newer
- Modern browsers
- Fetch-compatible serverless and edge runtimes

Build outputs:

- ESM
- CommonJS
- Type declarations
- Source maps

## Public API

Primary usage:

```ts
import { MemindClient, Message, Strategy } from '@openmemind/memind'

const client = new MemindClient({
  baseUrl: 'http://localhost:8366',
  apiToken: process.env.MEMIND_API_TOKEN,
})

await client.health()

await client.memory.extract({
  userId: 'user-1',
  agentId: 'agent-1',
  rawContent: {
    type: 'conversation',
    messages: [Message.user('Remember that I prefer concise answers.')],
  },
})

const result = await client.memory.retrieve({
  userId: 'user-1',
  agentId: 'agent-1',
  query: 'What does the user prefer?',
  strategy: Strategy.SIMPLE,
  trace: true,
})
```

The client exposes one main class:

```ts
new MemindClient(options)
```

Options:

```ts
type MemindClientOptions = {
  baseUrl?: string
  apiToken?: string
  timeoutMs?: number
  maxRetries?: number
  fetch?: typeof fetch
  headers?: Record<string, string>
}
```

Configuration precedence:

1. Constructor options
2. Node environment variables `MEMIND_BASE_URL` and `MEMIND_API_TOKEN`
3. Defaults for optional values

Defaults:

- `timeoutMs = 30000`
- `maxRetries = 2`

`baseUrl` is required after constructor and environment resolution. Blank strings are invalid.
`apiToken` is optional. Blank tokens are treated as absent.

The client has these methods and resources:

```ts
client.health(): Promise<HealthResponse>

client.memory.extract(request: ExtractMemoryRequest): Promise<ExtractMemoryResponse>
client.memory.addMessage(request: AddMessageRequest): Promise<void>
client.memory.commit(request: CommitMemoryRequest): Promise<void>
client.memory.retrieve(request: RetrieveMemoryRequest): Promise<RetrieveMemoryResponse>
```

`addMessage` and `commit` return `void`, matching the existing Java and Python clients. The HTTP
response data is currently an implementation detail for these operations.

## Type Model

Use camelCase TypeScript fields because they match the server JSON and are idiomatic for TypeScript.
Do not introduce snake_case aliases or runtime key conversion.

Core public types:

```ts
type Role = 'USER' | 'ASSISTANT'
type Strategy = 'SIMPLE' | 'DEEP'

type ApiResult<T> = { data: T | null } | { error: ApiError }

type ApiError = {
  code: string
  message: string
  details?: unknown
}
```

Provide value exports for convenient enum-like usage without requiring TypeScript enums:

```ts
const Role = {
  USER: 'USER',
  ASSISTANT: 'ASSISTANT',
} as const

const Strategy = {
  SIMPLE: 'SIMPLE',
  DEEP: 'DEEP',
} as const
```

Message and content model:

```ts
type UrlSource = {
  type: 'url'
  url: string
}

type Base64Source = {
  type: 'base64'
  media_type: string
  data: string
}

type Source = UrlSource | Base64Source

type TextBlock = {
  type: 'text'
  text: string
}

type ImageBlock = {
  type: 'image'
  source: Source
}

type AudioBlock = {
  type: 'audio'
  source: Source
}

type VideoBlock = {
  type: 'video'
  source: Source
}

type ContentBlock = TextBlock | ImageBlock | AudioBlock | VideoBlock

type Message = {
  role: Role
  content: ContentBlock[]
  timestamp?: string
  userName?: string
  sourceClient?: string
}
```

Provide helper factories:

```ts
Message.user(text: string, options?: { timestamp?: string }): Message
Message.assistant(text: string, options?: { timestamp?: string }): Message
```

Raw content model:

```ts
type ConversationContent = {
  type: 'conversation'
  messages: Message[]
}

type MapRawContent = {
  type: string
  [key: string]: unknown
}

type RawContent = ConversationContent | MapRawContent
```

`MapRawContent` intentionally supports flat extension fields such as:

```ts
{ type: 'document', title: 'Test', body: 'Content' }
```

Memory request and response types mirror the Java/Python clients and server records:

```ts
type ExtractMemoryRequest = {
  userId: string
  agentId: string
  rawContent: RawContent
  sourceClient?: string
}

type AddMessageRequest = {
  userId: string
  agentId: string
  message: Message
  sourceClient?: string
}

type CommitMemoryRequest = {
  userId: string
  agentId: string
  sourceClient?: string
}

type RetrieveMemoryRequest = {
  userId: string
  agentId: string
  query: string
  strategy: Strategy
  trace?: boolean
}
```

Response types should include all currently exposed fields and tolerate future fields at runtime.
The TypeScript type surface does not need to model unknown future fields.

## HTTP Layer

The HTTP layer is internal and should be small:

- Normalize `baseUrl` by trimming whitespace and removing trailing slashes.
- Resolve all request paths under `/open/v1`.
- Serialize request bodies with `JSON.stringify`, omitting `undefined` values.
- Parse response bodies as JSON.
- Validate only the response envelope shape at runtime.
- Leave deeper response data validation to TypeScript types and tests, not runtime schema libraries.

Headers:

- `Accept: application/json`
- `Content-Type: application/json` when a body is present
- `Authorization: Bearer <token>` when `apiToken` is present
- `User-Agent: memind-typescript/<version>` only when the runtime allows setting it
- Additional caller headers from `options.headers`

Caller headers may extend defaults, but they must not silently remove `Accept` or produce invalid
authorization behavior.

The client should support a custom `fetch` implementation for tests and advanced runtimes.

## Errors

Public error hierarchy:

```ts
class MemindError extends Error {}

class MemindAPIError extends MemindError {
  status: number
  errorCode?: string
  requestId?: string
  body?: unknown
}

class MemindAuthenticationError extends MemindAPIError {}

class MemindRateLimitError extends MemindAPIError {
  retryAfter?: number
}

class MemindConnectionError extends MemindError {}

class MemindTimeoutError extends MemindError {}
```

Error mapping:

- Network failures become `MemindConnectionError`.
- Abort/timeout failures become `MemindTimeoutError`.
- Non-JSON responses become `MemindAPIError` with `errorCode = 'parse_error'`.
- JSON responses without a valid `{ data }` or `{ error }` envelope become `MemindAPIError` with
  `errorCode = 'parse_error'`.
- Error envelopes with HTTP 401 become `MemindAuthenticationError`.
- Error envelopes with HTTP 429 become `MemindRateLimitError`.
- Other error envelopes become `MemindAPIError`.
- The `X-Request-Id` response header is captured on `MemindAPIError.requestId` when present.

## Retry Policy

Retry behavior follows the Python client:

- Retryable HTTP status codes: `408`, `429`, `500`, `502`, `503`, `504`
- Default `maxRetries = 2`
- Initial delay: 500 ms
- Maximum delay: 2000 ms
- Add bounded jitter
- Respect `Retry-After` seconds and HTTP-date values

Default retry behavior:

- `health`: retries enabled
- `memory.retrieve`: retries enabled
- `memory.extract`: retries disabled
- `memory.addMessage`: retries disabled
- `memory.commit`: retries disabled

This split is required because retrieval is read-like, while extraction, add-message, and commit can
create duplicate effects if retried after an ambiguous failure.

Retry logging should not include authorization headers or request bodies.

## File Layout

Recommended package layout:

```text
memind-clients/typescript/
  LICENSE
  README.md
  package.json
  tsconfig.json
  tsup.config.ts
  vitest.config.ts
  src/
    index.ts
    client.ts
    version.ts
    core/
      errors.ts
      http.ts
      retry.ts
      serialize.ts
    resources/
      memory.ts
    types/
      common.ts
      health.ts
      memory.ts
      message.ts
  tests/
    client.test.ts
    errors.test.ts
    models.test.ts
    public-api.test.ts
    retry.test.ts
```

## Tooling

Use pnpm, TypeScript, Vitest, and tsup. Keep the package independent from `memind-ui`.

Recommended scripts:

```json
{
  "build": "tsup",
  "typecheck": "tsc --noEmit",
  "test": "vitest run",
  "test:coverage": "vitest run --coverage",
  "lint": "eslint .",
  "format:check": "prettier --check .",
  "format": "prettier --write .",
  "pack:check": "pnpm pack --dry-run"
}
```

TypeScript settings should be strict. The package should include `.d.ts` and source maps in the
published artifact.

## Tests

Minimum test coverage:

- Public exports exist and match expected values.
- `baseUrl` is required unless `MEMIND_BASE_URL` is set.
- Constructor options override environment variables.
- Blank `baseUrl` is rejected.
- Blank `apiToken` is ignored.
- URLs are built under `/open/v1`.
- Auth and JSON headers are sent correctly.
- `Message.user` and `Message.assistant` produce expected payloads.
- `ConversationContent` and flat `MapRawContent` serialize correctly.
- Success envelopes return typed data.
- Error envelopes map to the correct error classes.
- `X-Request-Id` is captured.
- `Retry-After` seconds and HTTP-date values are respected.
- `retrieve` retries retryable responses.
- mutating memory calls do not retry by default.
- network failures map to `MemindConnectionError`.
- timeout failures map to `MemindTimeoutError`.
- non-JSON and malformed envelope responses map to parse errors.
- README examples typecheck or are covered by tests.

## CI

Add a GitHub Actions workflow for the TypeScript client:

```text
.github/workflows/typescript-client.yml
```

It should run on changes to:

- `memind-clients/typescript/**`
- `.github/workflows/typescript-client.yml`
- `.github/workflows/release-typescript-client.yml`

Required checks:

```bash
pnpm install --frozen-lockfile
pnpm format:check
pnpm lint
pnpm typecheck
pnpm test:coverage
pnpm build
pnpm pack --dry-run
```

## Release

Add a release workflow:

```text
.github/workflows/release-typescript-client.yml
```

Preferred publishing mechanism:

- npm trusted publishing through GitHub Actions

Fallback:

- `NPM_TOKEN`, only if trusted publishing is not available

The release workflow should:

- Accept an explicit `version` input.
- Refuse empty versions.
- Refuse snapshot/pre-release versions unless explicitly allowed in a future design.
- Verify the requested version matches `package.json`.
- Run the full verification suite.
- Build and pack-check the package.
- Publish to npm.

## Documentation

Add `memind-clients/typescript/README.md` with:

- Installation
- Node usage
- Browser usage
- Configuration and environment variables
- Message helpers
- Raw content examples
- Retrieval example with `trace`
- Error handling
- Retry behavior and mutating operation warning
- Development commands

Add a short link from the root README client/integration section after the package exists.

## Non-Goals

The first TypeScript client will not:

- Cover `/admin/v1` APIs.
- Generate code from OpenAPI.
- Add a server OpenAPI specification.
- Add runtime schema validation with zod or another dependency.
- Provide a React hook layer.
- Provide streaming APIs.
- Provide idempotency keys unless the server adds first-class support.

## Future Work

Potential follow-up work:

- Add a maintained OpenAPI specification and use it for contract tests.
- Generate low-level API types from OpenAPI while preserving a hand-designed public SDK.
- Add `admin` resource support if the admin API stabilizes as a supported integration surface.
- Add optional idempotency support if server endpoints accept idempotency keys.
- Add Deno-specific documentation if demand appears.
