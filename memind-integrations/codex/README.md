# Memind Codex Integration

Memind adds persistent project memory to Codex CLI. The integration retrieves relevant Memind context before
each user prompt and submits Codex coding-agent timelines through Memind's reliable extraction endpoint after
each turn.

Use this integration when you want Codex to remember project facts, preferences, and previous decisions across
sessions. The plugin connects Codex to an already-running Memind server; it does not start the server itself.

The integration is intentionally small:

- Uses the official Memind Python client.
- No Codex marketplace dependency.
- No overwrite of existing Codex hooks or config.
- Captures each coding-agent turn as Memind `agent_timeline` raw data: user prompt, tool/command activity,
  optional final assistant text, and stop boundary.

## What It Does

- **Retrieval**: `UserPromptSubmit` calls `MemindClient.memory.retrieve(...)` and injects relevant memories into
  the Codex prompt as `<memind_memories>...</memind_memories>`.
- **Ingestion**: `PreToolUse` and `PostToolUse` buffer normalized tool events locally. The next `Stop` hook
  submits them as `rawContent.type = "agent_timeline"` so Memind can extract user and agent memories from the
  same agent turn.
- **Retry**: failed timeline extraction payloads are spooled under `~/.memind/codex/retry/` and replayed on the next
  `SessionStart`.
- **Source tagging**: all requests use `sourceClient = "codex"` by default, so Memind can distinguish Codex
  memory from Claude Code, OpenClaw, API calls, or future clients.

## Requirements

- Codex CLI with hook support.
- Python 3.10+ available as `python3`.
- The official `memind` Python package installed in the same `python3` environment used by hooks.
- A running Memind server, usually at `http://127.0.0.1:8366`.

Install the official client before running `install.sh`:

```bash
python3 -m pip install memind
```

The official `memind` package installs its runtime dependencies, including `httpx` and `pydantic`.

Check the Memind server before installing:

```bash
curl -fsSL http://127.0.0.1:8366/open/v1/health
```

## Quick Start

1. Start Memind server.

2. From the Memind repository root, install the Codex integration:

```bash
bash memind-integrations/codex/install.sh
```

The installer validates that `python3` can import the official `memind` package and that the package exposes the
client APIs used by the hooks. Install or upgrade `memind` before running the installer if validation fails.

Published installs use the same script directly from the repository:

```bash
curl -fsSL https://raw.githubusercontent.com/openmemind/memind/main/memind-integrations/codex/install.sh | bash
```

3. Start a new Codex session in your project.

Hooks are loaded by Codex at session startup, so sessions that were already running before installation need to
be restarted.

## Safer Install Options

Preview changes without writing files:

```bash
bash memind-integrations/codex/install.sh --dry-run
```

Dry-run the remote installer:

```bash
curl -fsSL https://raw.githubusercontent.com/openmemind/memind/main/memind-integrations/codex/install.sh | bash -s -- --dry-run
```

Install from a specific local checkout:

```bash
bash memind-integrations/codex/install.sh --source-root memind-integrations/codex
```

Use custom paths for tests or development:

```bash
bash memind-integrations/codex/install.sh \
  --install-root /tmp/memind-codex \
  --hooks-path /tmp/codex-hooks.json \
  --config-path /tmp/codex-config.toml
```

## What The Installer Changes

The installer writes only these locations by default:

- `~/.memind/codex/`: installed hook scripts, default settings, plugin metadata, and this README.
- `~/.codex/hooks.json`: Memind hook entries are merged into the existing hooks file.
- `~/.codex/config.toml`: `[features] hooks = true` is added only when it is missing.

The installer does **not** overwrite unrelated Codex hooks or settings. Re-running the installer is idempotent:
old Memind-owned hook entries are removed before fresh entries are added.

The installer does not write `~/.memind/codex.json`. Create that file only when you need to override defaults.

## Hook Events

The installed hooks are:

| Codex event | Script | Timeout | Purpose |
| --- | --- | ---: | --- |
| `SessionStart` | `scripts/session_start.py` | 5s | Replay at most one failed timeline payload and clean old state. |
| `UserPromptSubmit` | `scripts/retrieve.py` | 12s | Buffer the user prompt event and retrieve relevant Memind context. |
| `PreToolUse` | `scripts/pre_tool_use.py` | 5s | Buffer a redacted tool-start event in local session state. |
| `PostToolUse` | `scripts/post_tool_use.py` | 5s | Buffer a redacted tool-result event in local session state. |
| `Stop` | `scripts/ingest.py` | 15s | Flush buffered `agent_timeline` events after a turn. |

## Configuration

The default configuration works with a local Memind server at `http://127.0.0.1:8366`.

User configuration is optional. Save overrides as `~/.memind/codex.json`:

```json
{
  "memindApiUrl": "http://127.0.0.1:8366",
  "memindApiToken": null,
  "userId": "local__alice",
  "agentId": "codex",
  "agentIdMode": "project",
  "sourceClient": "codex",
  "autoIngestAgentTimeline": true,
  "retrieveContextTurns": 0
}
```

Settings are loaded in this order:

1. Built-in defaults from `settings.json`.
2. User config from `~/.memind/codex.json`.
3. Environment variables.

### Common Settings

| Setting | Default | Description |
| --- | --- | --- |
| `memindApiUrl` | `http://127.0.0.1:8366` | Memind server URL. |
| `memindApiToken` | `null` | Optional bearer token. |
| `userId` | `local__<system-user>` | Memind user identity. |
| `agentId` | `codex` | Base agent identity. |
| `agentIdMode` | `project` | `project` appends a stable project suffix; any other value uses `agentId` as-is. |
| `sourceClient` | `codex` | Source marker stored with Memind data. |
| `autoRetrieve` | `true` | Enables prompt-time memory retrieval. |
| `autoIngestAgentTimeline` | `true` | Enables user prompt, tool/result, assistant message, and stop event buffering plus `agent_timeline` rawdata flush. |
| `retrieveStrategy` | `SIMPLE` | Memind retrieval strategy. |
| `retrieveMaxEntries` | `8` | Maximum formatted memory entries injected into Codex. |
| `retrieveMaxChars` | `6000` | Maximum injected context characters. |
| `retrieveContextTurns` | `0` | Number of recent transcript turns to include in the retrieval query. |
| `ingestRetrySpool` | `true` | Enables file-backed retry for failed ingestion. |
| `debug` | `false` | Writes debug logs to `~/.memind/codex.log`. |

### Environment Overrides

Every common setting can be overridden with an environment variable:

```bash
export MEMIND_API_URL=http://127.0.0.1:8366
export MEMIND_API_TOKEN=...
export MEMIND_USER_ID=local__alice
export MEMIND_AGENT_ID=codex
export MEMIND_AGENT_ID_MODE=project
export MEMIND_SOURCE_CLIENT=codex
export MEMIND_AUTO_INGEST_AGENT_TIMELINE=true
export MEMIND_RETRIEVE_CONTEXT_TURNS=0
export MEMIND_DEBUG=true
```

Additional environment variables include `MEMIND_AUTO_RETRIEVE`,
`MEMIND_RETRIEVE_STRATEGY`, `MEMIND_RETRIEVE_MAX_ENTRIES`, `MEMIND_RETRIEVE_MAX_CHARS`,
`MEMIND_STATE_MAX_AGE_DAYS`,
`MEMIND_INGEST_RETRY_SPOOL`, `MEMIND_INGEST_RETRY_MAX_FILES`, and `MEMIND_INGEST_RETRY_MAX_AGE_DAYS`.

## Identity Model

By default, Memind stores Codex memory under:

- `userId`: `local__<system-user>`
- `agentId`: `codex__<project-name>-<stable-hash>`

The project hash is based on the Git remote URL when available, otherwise the local project path. This keeps
different repositories separated while allowing memory to survive moving between Codex sessions.

To use one shared Codex memory across all projects:

```json
{
  "agentId": "codex",
  "agentIdMode": "fixed"
}
```

## Retrieval Behavior

Retrieval runs before each user prompt when `autoRetrieve = true`.

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

Insights are formatted before memory items. Higher-level insights (`ROOT`, then `BRANCH`) are preferred; `LEAF`
insights are omitted by default unless no higher-level insights are available.

`retrieveContextTurns` defaults to `0`, so retrieval uses only the current prompt and does not read large
transcripts. Set it to `1` or `2` if your prompts are often short, such as "fix this" or "continue".

Agent memory items are grouped separately when returned by Memind:

```text
## Agent Playbooks
## Resolved Problems
## Tool Notes
## Directives
```

## Ingestion Behavior

Ingestion is timeline-only for Codex. The plugin does not submit transcript conversation rawdata. It buffers one
turn timeline under `~/.memind/codex/state/`: the submitted user prompt, tool and command events, the latest
assistant message when available from the transcript, and a stop boundary. It flushes the turn through
`AsyncMemindClient.memory.extract(...)` as agent timeline rawdata. A typical timeline payload looks like:

```json
{
  "userId": "local__alice",
  "agentId": "codex__project_hash",
  "sourceClient": "codex",
  "rawContent": {
    "type": "agent_timeline",
    "sourceClient": "codex",
    "sessionId": "session-123",
    "agentTurnId": "session-123-turn-1",
    "timelineId": "session-123-turn-1-timeline",
    "project": {"name": "payment-service", "rootPath": "/repo/payment-service"},
    "events": [
      {
        "eventId": "event-id",
        "seq": 1,
        "kind": "user_prompt",
        "text": "Fix payment tests",
        "status": "success",
        "metadata": {"turnId": "session-123-turn-1", "turnSeq": 1}
      },
      {
        "eventId": "event-id",
        "seq": 2,
        "kind": "command",
        "toolName": "Bash",
        "command": "npm test payment",
        "status": "failed",
        "exitCode": 1,
        "output": "{\"stdout\": \"rounding mismatch\"}",
        "metadata": {"turnId": "session-123-turn-1", "turnSeq": 1}
      },
      {
        "eventId": "event-id",
        "seq": 3,
        "kind": "assistant_message",
        "text": "Updated calc.ts and payment tests now pass.",
        "status": "success",
        "metadata": {"turnId": "session-123-turn-1", "turnSeq": 1}
      },
      {
        "eventId": "event-id",
        "seq": 4,
        "kind": "stop",
        "status": "success",
        "metadata": {"turnId": "session-123-turn-1", "turnSeq": 1}
      }
    ]
  }
}
```

Secrets are redacted before events are written to local state. File content capture is disabled by default; the
hook stores normalized tool metadata, commands, paths, statuses, and compact outputs.

On `SUCCESS`, the covered events are removed from local state. `PARTIAL_SUCCESS` and failures keep the events
available and spool the full timeline payload for later `SessionStart` replay.

## Server RawData Agent Settings

Enable the Memind server-side rawdata-agent plugin when deploying the coding-agent memory path:

```properties
memind.rawdata.agent.enabled=true
memind.rawdata.agent.privacy.redact-secrets=true
memind.rawdata.agent.extraction.extract-on-every-tool=false
```

## Verify Installation

After installation, verify the integration in this order:

1. Confirm Memind server is reachable:

```bash
curl -fsSL http://127.0.0.1:8366/open/v1/health
```

2. Confirm Codex hooks are enabled:

```toml
[features]
hooks = true
```

3. Confirm `~/.codex/hooks.json` contains Memind commands pointing to `~/.memind/codex/scripts/...`.
4. Start a new Codex session in a project and ask a project-specific question.
5. Check Memind data for entries whose `sourceClient` is `codex`.

For local debugging, enable logs:

```bash
export MEMIND_DEBUG=true
```

Then inspect:

```bash
tail -f ~/.memind/codex.log
```

## Upgrade

Re-run the installer:

```bash
curl -fsSL https://raw.githubusercontent.com/openmemind/memind/main/memind-integrations/codex/install.sh | bash
```

From a repository checkout, you can also run:

```bash
bash memind-integrations/codex/install.sh
```

The installer replaces only Memind's installed runtime files under `~/.memind/codex/` and refreshes Memind-owned
hook entries.

## Uninstall

From a repository checkout:

```bash
bash memind-integrations/codex/install.sh --uninstall
```

Published uninstall uses the same script directly from the repository:

```bash
curl -fsSL https://raw.githubusercontent.com/openmemind/memind/main/memind-integrations/codex/install.sh | bash -s -- --uninstall
```

If the integration is already installed, you can also uninstall through the installed copy:

```bash
bash ~/.memind/codex/install.sh --uninstall
```

Uninstall removes only Memind hook entries from `~/.codex/hooks.json`. It preserves:

- `~/.memind/codex.json`
- `~/.memind/codex/state/`
- `~/.memind/codex/retry/`
- `[features] hooks = true`

The feature flag is preserved because other Codex hooks may depend on it.

## Troubleshooting

### Hooks do not fire

- Start a new Codex session after installing.
- Confirm `~/.codex/config.toml` contains:

```toml
[features]
hooks = true
```

- Confirm `~/.codex/hooks.json` contains Memind entries pointing to `~/.memind/codex/scripts/...`.

### Memind context is not injected

- Confirm Memind server is reachable:

```bash
curl -fsSL http://127.0.0.1:8366/open/v1/health
```

- Confirm `autoRetrieve` is `true`.
- Confirm existing memories are stored under the same `userId` and `agentId`.
- Try setting `retrieveContextTurns` to `1` or `2` if the current prompt is very short.

### Agent timeline events are not ingested

- Confirm `autoIngestAgentTimeline` is `true`.
- Confirm Codex is emitting `PreToolUse` and `PostToolUse` hooks.
- Confirm `~/.memind/codex/state/` is writable.
- Enable `MEMIND_DEBUG=true` and inspect `~/.memind/codex.log`.

### Stop hook times out

- Confirm Memind server responds quickly.
- Reduce tool output volume before it reaches the hook if your Codex setup allows it.
- Inspect `~/.memind/codex/retry/` for repeatedly failing timeline payloads.

## Limitations

- Exact duplicate complete timeline windows are idempotent.
- Arbitrary overlapping partial windows are adapter responsibility in v1.
- File content capture is disabled by default.
- `rawdata-toolcall` remains supported.
- If `rawdata-toolcall` and `rawdata-agent` ingest the same tool activity, v1 may create semantically overlapping
  TOOL items. This is acceptable compatibility behavior; do not add cross-plugin suppression in v1. Users who
  want one canonical coding-agent path should enable `rawdata-agent` for full agent timelines and keep
  `rawdata-toolcall` for pure legacy tool-call logs.
- Retrieval quality depends on existing extracted Memind items and insights.
