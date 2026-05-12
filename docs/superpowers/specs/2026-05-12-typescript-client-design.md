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

The published `package.json` must include:

```json
{
  "name": "@openmemind/memind",
  "version": "<release-version>",
  "description": "Official TypeScript client for the Memind memory engine API",
  "license": "Apache-2.0",
  "type": "module",
  "main": "./dist/index.cjs",
  "module": "./dist/index.js",
  "types": "./dist/index.d.ts",
  "exports": {
    ".": {
      "types": "./dist/index.d.ts",
      "import": "./dist/index.js",
      "require": "./dist/index.cjs"
    }
  },
  "files": ["dist", "README.md", "LICENSE"],
  "sideEffects": false,
  "engines": {
    "node": ">=20"
  },
  "repository": {
    "type": "git",
    "url": "git+https://github.com/openmemind/memind.git",
    "directory": "memind-clients/typescript"
  },
  "bugs": {
    "url": "https://github.com/openmemind/memind/issues"
  },
  "homepage": "https://github.com/openmemind/memind#readme",
  "publishConfig": {
    "access": "public",
    "provenance": true
  }
}
```

`<release-version>` should match the client release version used by Java and Python clients unless
a future release policy explicitly decouples client versions.

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
  extraHeaders?: Record<string, string>
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
type RequestOptions = {
  signal?: AbortSignal
  timeoutMs?: number
  maxRetries?: number
}

client.health(options?: RequestOptions): Promise<HealthResponse>

client.memory.extract(
  request: ExtractMemoryRequest,
  options?: RequestOptions,
): Promise<ExtractMemoryResponse>
client.memory.addMessage(request: AddMessageRequest, options?: RequestOptions): Promise<void>
client.memory.commit(request: CommitMemoryRequest, options?: RequestOptions): Promise<void>
client.memory.retrieve(
  request: RetrieveMemoryRequest,
  options?: RequestOptions,
): Promise<RetrieveMemoryResponse>
```

`addMessage` and `commit` return `void`, matching the existing Java and Python clients. The HTTP
response data is currently an implementation detail for these operations.

Per-request `timeoutMs` and `maxRetries` override client defaults for that call only. `signal` lets
callers cancel a request. If both a caller signal and an SDK timeout are active, either one may abort
the underlying `fetch`; the error mapping must distinguish which source triggered the abort.

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
type JsonPrimitive = string | number | boolean | null
type JsonValue = JsonPrimitive | JsonValue[] | { [key: string]: JsonValue }

type ConversationContent = {
  type: 'conversation'
  messages: Message[]
}

type JsonObjectRawContent = {
  type: string
  [key: string]: JsonValue
}

type RawContent = ConversationContent | JsonObjectRawContent
```

`JsonObjectRawContent` intentionally supports flat extension fields such as:

```ts
{ type: 'document', title: 'Test', body: 'Content' }
```

Implementation must still validate raw content before sending:

- `type` must be a non-blank string.
- `type === 'conversation'` requires `messages` to be a non-empty array of valid `Message` values.
- Non-conversation raw content must contain only JSON-serializable values.
- `undefined`, functions, symbols, and cyclic objects are invalid request payload values.
- `RawContent.map()` must reject `type === 'conversation'` at runtime because TypeScript cannot
  exclude one literal from arbitrary `string` values.
- `RawContent.map()` must reject a `fields` object that contains its own `type` key.

Provide helper factories so users do not have to remember the exact object shape:

```ts
const RawContent = {
  conversation(messages: Message[]): ConversationContent,
  map(type: string, fields?: Record<string, JsonValue>): JsonObjectRawContent,
} as const
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

Because TypeScript types do not exist at runtime, the client must perform lightweight response data
validation before returning public response objects. This must not depend on a runtime schema
library, but it must validate enough shape to avoid returning arbitrary malformed server payloads as
typed data.

Required validators:

- `assertHealthResponse(data): HealthResponse`
- `assertExtractMemoryResponse(data): ExtractMemoryResponse`
- `assertAddMessageResponse(data): AddMessageResponse`
- `assertRetrieveMemoryResponse(data): RetrieveMemoryResponse`
- `assertOperationAccepted(data): OperationAccepted` for async endpoints if they are added later

Validator rules:

- Required scalar fields must have the expected primitive type.
- Optional scalar fields may be absent or `null`.
- Arrays that the public response type exposes as arrays must be normalized to `[]` when absent or
  `null`, matching the Python client defaults.
- If a present field has the wrong type, raise `MemindAPIError` with `errorCode = 'parse_error'`.
- Unknown response fields are ignored.
- ISO date/time fields such as `occurredAt`, `startedAt`, and `completedAt` remain strings in the
  TypeScript client. The client does not convert them to `Date`, preserving server JSON and avoiding
  timezone surprises.

## HTTP Layer

The HTTP layer is internal and should be small:

- Normalize `baseUrl` by trimming whitespace and removing trailing slashes.
- Resolve all request paths under `/open/v1`.
- Serialize request bodies with a helper that recursively drops `undefined` object properties and
  rejects non-JSON values before calling `JSON.stringify`.
- Parse response bodies as JSON.
- Validate the response envelope shape at runtime.
- Validate public response data with the lightweight validators described above.

Headers:

- `Accept: application/json`
- `Content-Type: application/json` when a body is present
- `Authorization: Bearer <token>` when `apiToken` is present
- `User-Agent: memind-typescript/<version>` only when the runtime allows setting it
- Additional caller headers from `options.extraHeaders`

`extraHeaders` may add application-specific headers, but it must not override SDK-owned headers. The
client must reject case-insensitive attempts to set these names through `extraHeaders`:

- `accept`
- `content-type`
- `authorization`
- `user-agent`

This keeps authentication, content negotiation, and browser-forbidden headers deterministic. If
callers need a different token, they must use `apiToken`. If they need a different runtime fetch,
they must use the `fetch` option.

The client should support a custom `fetch` implementation for tests and advanced runtimes.

Timeout and cancellation behavior:

- The SDK implements timeouts with `AbortController`.
- A caller-provided `signal` is composed with the SDK timeout signal.
- If the SDK timeout fires first, raise `MemindTimeoutError`.
- If the caller signal aborts first, raise `MemindConnectionError` with a message that identifies
  caller cancellation.
- If the runtime reports another network-level fetch failure, raise `MemindConnectionError`.
- Timeout cleanup must clear timers after every request attempt.

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
- SDK timeout aborts become `MemindTimeoutError`.
- Caller-triggered aborts become `MemindConnectionError`.
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
      validate.ts
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
published artifact. The package should not depend on `memind-ui` tooling or lockfiles.

## Tests

Minimum test coverage:

- Public exports exist and match expected values.
- `baseUrl` is required unless `MEMIND_BASE_URL` is set.
- Constructor options override environment variables.
- Blank `baseUrl` is rejected.
- Blank `apiToken` is ignored.
- URLs are built under `/open/v1`.
- Auth and JSON headers are sent correctly.
- `extraHeaders` can add custom headers.
- `extraHeaders` rejects SDK-owned headers case-insensitively.
- Per-request `timeoutMs` overrides the client default.
- Per-request `maxRetries` overrides the client default.
- Caller-provided `AbortSignal` cancels the request without being reported as an SDK timeout.
- `Message.user` and `Message.assistant` produce expected payloads.
- `RawContent.conversation` and `RawContent.map` produce expected payloads.
- Invalid raw content payloads are rejected before network calls.
- Success envelopes return typed data.
- Malformed success `data` is rejected with a parse error.
- Missing or null response arrays are normalized to `[]` where the public response type exposes
  arrays.
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
cd memind-clients/typescript
pnpm install --frozen-lockfile
pnpm format:check
pnpm lint
pnpm typecheck
pnpm test:coverage
pnpm build
pnpm pack --dry-run
```

The workflow must set `working-directory: memind-clients/typescript` for every package command
instead of assuming a root pnpm workspace.

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
- Publish to npm with provenance enabled.
- Run every package command with `working-directory: memind-clients/typescript`.

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
