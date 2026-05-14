# @openmemind/memind-openclaw

Memind memory backend for OpenClaw.

The plugin retrieves relevant Memind memories before each prompt and submits successful
user/assistant OpenClaw conversation text to Memind extraction after each agent turn. It connects
to an already-running Memind server and does not start the server.

Use this plugin when you want OpenClaw to remember project facts, preferences, implementation
decisions, and previous discussions across sessions.

The integration is intentionally small:

- Uses the official `@openmemind/memind` TypeScript client.
- Activates only when selected as OpenClaw's memory slot.
- Does not manage a local Memind daemon.
- Does not ingest tool calls or media in v0.1.

## Requirements

- OpenClaw with plugin and memory slot support.
- Node.js 20+.
- A running Memind server, usually at `http://127.0.0.1:8366`.

Check the Memind server before enabling the plugin:

```bash
curl -fsSL http://127.0.0.1:8366/open/v1/health
```

## Quick Start

Install the plugin:

```bash
openclaw plugins install @openmemind/memind-openclaw
```

Install alone does not activate the plugin. Select it as the OpenClaw memory backend:

```json
{
  "plugins": {
    "slots": {
      "memory": "memind-openclaw"
    },
    "entries": {
      "memind-openclaw": {
        "enabled": true,
        "config": {
          "memindApiUrl": "http://127.0.0.1:8366",
          "memindApiToken": {
            "source": "env",
            "id": "MEMIND_API_TOKEN"
          }
        }
      }
    }
  }
}
```

Restart the OpenClaw gateway or session after changing plugin configuration.

## What It Does

- **Retrieval**: `before_prompt_build` calls `MemindClient.memory.retrieve(...)` and injects
  relevant memories into OpenClaw as `<memind_memories>...</memind_memories>`.
- **Ingestion**: `agent_end` filters successful user/assistant messages and submits a caller-owned
  conversation payload through `MemindClient.memory.extract(...)`.
- **Retry**: failed extraction payloads are spooled under OpenClaw's plugin state directory and
  replayed on later `agent_end` hooks.
- **Source tagging**: all requests use `sourceClient = "openclaw"` by default, so Memind can
  distinguish OpenClaw memory from Claude Code, Codex, API calls, or future clients.

The plugin is fail-open: Memind retrieval or extraction failures are logged but do not block the
agent turn.

## Configuration

The default configuration works with a local Memind server at `http://127.0.0.1:8366`.

### Common Settings

| Setting                       | Default                 | Description                                                                             |
| ----------------------------- | ----------------------- | --------------------------------------------------------------------------------------- |
| `memindApiUrl`                | `http://127.0.0.1:8366` | Memind server URL.                                                                      |
| `memindApiToken`              | unset                   | Optional bearer token. Use a string token or an env SecretRef.                          |
| `userId`                      | `local__<system-user>`  | Memind user identity.                                                                   |
| `agentId`                     | `openclaw`              | Base agent identity.                                                                    |
| `agentIdMode`                 | `project`               | `project`, `fixed`, or `session`.                                                       |
| `sourceClient`                | `openclaw`              | Source marker stored with Memind data.                                                  |
| `autoRetrieve`                | `true`                  | Enables prompt-time memory retrieval.                                                   |
| `autoIngest`                  | `true`                  | Enables post-turn conversation extraction.                                              |
| `retrieveStrategy`            | `SIMPLE`                | Memind retrieval strategy.                                                              |
| `retrieveMaxEntries`          | `8`                     | Maximum formatted memory entries injected into OpenClaw.                                |
| `retrieveMaxChars`            | `6000`                  | Maximum injected memory body characters, excluding the fixed XML envelope and preamble. |
| `retrieveContextTurns`        | `1`                     | Number of recent turns included in the retrieval query.                                 |
| `retrievePromptPreamble`      | built in                | Text placed at the start of the injected memory block.                                  |
| `recallInjectionPosition`     | `context`               | `context`, `system-prepend`, or `system-append`.                                        |
| `ingestionRoles`              | `["user", "assistant"]` | Conversation roles eligible for ingestion.                                              |
| `ingestionMaxMessagesPerHook` | `20`                    | Maximum new messages sent during one capture hook.                                      |
| `ingestRetrySpool`            | `true`                  | Enables file-backed retry for failed extraction payloads.                               |
| `stateMaxAgeDays`             | `14`                    | Retention window for submitted-message fingerprints.                                    |
| `ingestRetryMaxFiles`         | `20`                    | Maximum retry payload files to keep.                                                    |
| `ingestRetryMaxAgeDays`       | `7`                     | Retention window for retry payloads.                                                    |
| `captureToolCalls`            | `false`                 | Reserved for a future release; v0.1 rejects `true`.                                     |
| `captureMedia`                | `false`                 | Reserved for a future release; v0.1 rejects `true`.                                     |
| `skipNonInteractiveTriggers`  | `true`                  | Skips cron, heartbeat, automation, and schedule sessions.                               |
| `captureSubagents`            | `false`                 | Captures subagent sessions when enabled.                                                |
| `debug`                       | `false`                 | Enables debug logs through the OpenClaw plugin logger.                                  |

### Token SecretRef

The plugin resolves this SecretRef form from environment variables:

```json
{
  "memindApiToken": {
    "source": "env",
    "id": "MEMIND_API_TOKEN"
  }
}
```

A plain string token is also accepted:

```json
{
  "memindApiToken": "memind-token"
}
```

## Identity Model

By default, Memind stores OpenClaw memory under:

- `userId`: `local__<system-user>`
- `agentId`: `openclaw__<project-name>-<stable-hash>`

The project hash is based on the OpenClaw workspace directory. This keeps different repositories
separated while allowing memory to survive across OpenClaw sessions in the same project.

To use one shared OpenClaw memory across all projects:

```json
{
  "agentId": "openclaw",
  "agentIdMode": "fixed"
}
```

To isolate memory by OpenClaw session:

```json
{
  "agentId": "openclaw",
  "agentIdMode": "session"
}
```

## Retrieval Behavior

Retrieval runs before each user prompt when `autoRetrieve = true` and the plugin is selected through
`plugins.slots.memory = "memind-openclaw"`.

The injected context format is:

```text
<memind_memories>
Relevant memories from Memind. Use only when directly helpful:
## Insights
- [insight:42] ...

## Memory Items
- [item:101] ...
</memind_memories>
```

Insights are formatted before memory items. Higher-level insights (`ROOT`, then `BRANCH`) are
preferred; `LEAF` insights are omitted by default unless no higher-level insights are available.

`retrieveContextTurns` defaults to `1`, so retrieval can use the current prompt plus the most recent
conversation turn. Set it to `0` if you want retrieval queries to use only the current prompt.

## Ingestion Behavior

Ingestion runs after successful OpenClaw agent turns when `autoIngest = true`.

The capture hook:

1. Reads messages from the OpenClaw hook payload.
2. Extracts text from user and assistant messages.
3. Strips previously injected `<memind_memories>` blocks to avoid feedback loops.
4. Skips tool calls, media, unsupported roles, non-interactive triggers, and subagent sessions by
   default.
5. Keeps the final user turn and following assistant messages.
6. Computes stable fingerprints and sends only messages that have not already been submitted.
7. Builds one caller-owned conversation raw-content payload and submits it through
   `MemindClient.memory.extract(...)`.

The local retry spool stores the full extraction payload plus the covered message fingerprints.
Fingerprints are marked submitted only after Memind returns `SUCCESS`; `PARTIAL_SUCCESS` and
failures keep the payload available for later replay.

## Troubleshooting

- If no memories are injected, confirm `plugins.slots.memory` is set to `memind-openclaw`.
- If capture does not run, confirm the turn completed successfully and is not a cron, heartbeat,
  automation, schedule, or subagent session.
- If authenticated Memind requests fail, confirm `MEMIND_API_TOKEN` is present in the OpenClaw
  process environment or configure `memindApiToken` as a plain string.
- If Memind is unavailable, OpenClaw continues normally and extraction retries later when retry
  spooling is enabled.
