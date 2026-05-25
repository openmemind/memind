# @openmemind/memind

Official TypeScript client for the [Memind](https://github.com/openmemind/memind) memory engine API.

## Installation

```bash
npm install @openmemind/memind
# or
pnpm add @openmemind/memind
```

## Quick Start

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

## Configuration

| Option         | Env Variable       | Default            | Description                     |
| -------------- | ------------------ | ------------------ | ------------------------------- |
| `baseUrl`      | `MEMIND_BASE_URL`  | Required           | Server URL                      |
| `apiToken`     | `MEMIND_API_TOKEN` | None               | Bearer token                    |
| `timeoutMs`    | None               | `30000`            | Request timeout in ms           |
| `maxRetries`   | None               | `2`                | Max retries for read operations |
| `fetch`        | None               | `globalThis.fetch` | Custom fetch implementation     |
| `extraHeaders` | None               | `{}`               | Additional request headers      |

Constructor options take precedence over environment variables.

## Browser Usage

The client works in any environment with a native `fetch` implementation:

```ts
const client = new MemindClient({
  baseUrl: 'https://your-memind-server.example.com',
  apiToken: 'your-token',
})
```

## Message Helpers

```ts
import { Message } from '@openmemind/memind'

Message.user('Hello')
Message.assistant('Hi there', { timestamp: '2026-01-01T00:00:00Z' })
```

## Raw Content

```ts
import { Message, RawContent, type AgentTimelineContent } from '@openmemind/memind'

RawContent.conversation([Message.user('hi'), Message.assistant('hello')])
RawContent.map('document', { title: 'Notes', body: 'Content here' })

const timeline: AgentTimelineContent = {
  type: 'agent_timeline',
  sourceClient: 'claude-code',
  sessionId: 'session-123',
  agentTurnId: 'session-123-agent-turn-1-1',
  timelineId: 'session-123-agent-1-2',
  events: [
    {
      eventId: 'event-id',
      seq: 1,
      kind: 'command',
      toolName: 'Bash',
      command: 'npm test payment',
      status: 'failed',
      exitCode: 1,
      output: '{"stdout": "rounding mismatch"}',
    },
  ],
}

await client.memory.extract({
  userId: 'local__alice',
  agentId: 'claude-code__project_hash',
  sourceClient: 'claude-code',
  rawContent: timeline,
})
```

## Error Handling

```ts
import { MemindAPIError, MemindTimeoutError } from '@openmemind/memind'

try {
  await client.memory.retrieve({
    userId: 'user-1',
    agentId: 'agent-1',
    query: 'What does the user prefer?',
    strategy: 'SIMPLE',
  })
} catch (err) {
  if (err instanceof MemindTimeoutError) {
    // Request timed out.
  } else if (err instanceof MemindAPIError) {
    console.error(err.status, err.errorCode, err.message)
  }
}
```

## Retry Behavior

Read operations (`health`, `memory.retrieve`) retry automatically on transient failures
(`408`, `429`, `500`, `502`, `503`, `504`) up to `maxRetries` times.

Mutating operations (`memory.extract`, `memory.addMessage`, `memory.commit`) do not retry by default.
This prevents duplicated extraction, buffered messages, or commits after ambiguous network failures.

To opt in to retries for a mutating operation:

```ts
await client.memory.extract(request, { maxRetries: 1 })
```

Only set `maxRetries` on mutating operations if your application can tolerate duplicate side effects.

## Per-Request Options

Every method accepts optional `RequestOptions`:

```ts
await client.health({ timeoutMs: 5000, signal: abortController.signal })
```

## Development

```bash
pnpm install
pnpm test          # Run tests
pnpm build         # Build ESM + CJS
pnpm typecheck     # Type check
pnpm lint          # Lint
pnpm format:check  # Check formatting
```

## License

Apache-2.0
