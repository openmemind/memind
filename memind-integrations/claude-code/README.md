# Memind Claude Code Integration

Memind adds persistent project memory to Claude Code. The plugin retrieves relevant Memind context before each
user prompt and submits Claude Code coding-agent timelines through Memind's reliable extraction endpoint during
session lifecycle hooks.

Use this plugin when you want Claude Code to remember project facts, preferences, implementation decisions, and
previous discussions across sessions. The plugin connects Claude Code to an already-running Memind server; it
does not start the server itself.

The integration is intentionally small:

- Uses the official Memind Python client.
- No local daemon management.
- No MCP dependency.
- Captures coding-agent tool and command activity as Memind `agent_timeline` raw data.

## What It Does

- **Retrieval**: `UserPromptSubmit` calls `MemindClient.memory.retrieve(...)` and injects relevant memories into
  Claude Code as `<memind_memories>...</memind_memories>` additional context.
- **Ingestion**: `PreToolUse` and `PostToolUse` buffer normalized tool events locally. `Stop`, `PreCompact`, and
  `SessionEnd` flush buffered events as `rawContent.type = "agent_timeline"` through
  `AsyncMemindClient.memory.extract(...)`, so Memind can extract user and agent memories from the same agent turn.
- **Retry**: failed ingestion payloads are spooled under `~/.memind/claude-code/retry/` and replayed on later
  `SessionStart` hooks.
- **Source tagging**: all requests use `sourceClient = "claude-code"` by default, so Memind can distinguish
  Claude Code memory from Codex, OpenClaw, API calls, or future clients.

## Requirements

- Claude Code with plugin and hook support.
- Python 3.10+ available as `python3`.
- The official `memind` Python package installed in the same `python3` environment used by hooks.
- A running Memind server, usually at `http://127.0.0.1:8366`.

Install the official client before enabling hooks:

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

2. Add the Memind marketplace and install the plugin:

```bash
claude plugin marketplace add openmemind/memind --scope user
claude plugin install memind-memory@memind-plugins --scope user
```

From a local Memind repository checkout, add the local marketplace instead:

```bash
claude plugin marketplace add ./ --scope user
claude plugin install memind-memory@memind-plugins --scope user
```

Use `./` or an explicit path for local marketplace installation; a bare `.` is not accepted by Claude Code's
marketplace parser.

3. Start a new Claude Code session in your project.

Hooks are loaded by Claude Code at session startup, so sessions that were already running before installation
need to be restarted.

## What Claude Code Installs

The marketplace entry is defined at the repository root in `.claude-plugin/marketplace.json`. It points to this
plugin directory:

```text
memind-integrations/claude-code
```

Claude Code installs the plugin from that directory and loads:

- `.claude-plugin/plugin.json`: plugin metadata.
- `hooks/hooks.json`: lifecycle hook commands.
- `settings.json`: default Memind integration settings.
- `scripts/`: Python hook runtime using the official Memind Python client.

The plugin does not modify Memind server configuration. User overrides are read from
`~/.memind/claude-code.json` only when you create that file.

## Hook Events

The installed hooks are:

| Claude Code event | Script | Timeout | Purpose |
| --- | --- | ---: | --- |
| `SessionStart` | `scripts/session_start.py` | 5s | Health check, replay at most one failed retry payload, and clean old state. |
| `UserPromptSubmit` | `scripts/retrieve.py` | 12s | Retrieve relevant Memind context for the current user prompt. |
| `PreToolUse` | `scripts/pre_tool_use.py` | 5s | Buffer a redacted tool-start event in local session state. |
| `PostToolUse` | `scripts/post_tool_use.py` | 5s | Buffer a redacted tool-result event in local session state. |
| `PreCompact` | `scripts/pre_compact.py` | 30s | Flush buffered `agent_timeline` events before context compaction. |
| `Stop` | `scripts/ingest.py` | 15s | Flush buffered `agent_timeline` events after a turn. |
| `SessionEnd` | `scripts/session_end.py` | 10s | Flush remaining buffered `agent_timeline` events at session end. |

`Stop`, `PreToolUse`, and `PostToolUse` are configured as async so regular turn completion stays fast.

## Configuration

The default configuration works with a local Memind server at `http://127.0.0.1:8366`.

User configuration is optional. Save overrides as `~/.memind/claude-code.json`:

```json
{
  "memindApiUrl": "http://127.0.0.1:8366",
  "memindApiToken": null,
  "userId": "local__alice",
  "agentId": "claude-code",
  "agentIdMode": "project",
  "sourceClient": "claude-code",
  "autoIngestAgentTimeline": true,
  "retrieveContextTurns": 0
}
```

Settings are loaded in this order:

1. Built-in defaults from `scripts/lib/config.py`.
2. Plugin defaults from `settings.json`.
3. User config from `~/.memind/claude-code.json`.
4. Environment variables.

### Common Settings

| Setting | Default | Description |
| --- | --- | --- |
| `memindApiUrl` | `http://127.0.0.1:8366` | Memind server URL. |
| `memindApiToken` | `null` | Optional bearer token. |
| `userId` | `local__<system-user>` | Memind user identity. |
| `agentId` | `claude-code` | Base agent identity. |
| `agentIdMode` | `project` | `project` appends a stable project suffix; any other value uses `agentId` as-is. |
| `sourceClient` | `claude-code` | Source marker stored with Memind data. |
| `autoRetrieve` | `true` | Enables prompt-time memory retrieval. |
| `autoIngestAgentTimeline` | `true` | Enables `PreToolUse`/`PostToolUse` event buffering and `agent_timeline` rawdata flush. |
| `retrieveStrategy` | `SIMPLE` | Memind retrieval strategy. |
| `retrieveMaxEntries` | `8` | Maximum formatted memory entries injected into Claude Code. |
| `retrieveMaxChars` | `6000` | Maximum injected context characters. |
| `retrieveContextTurns` | `0` | Number of recent transcript turns to include in the retrieval query. |
| `ingestRetrySpool` | `true` | Enables file-backed retry for failed extraction payloads. |
| `debug` | `false` | Writes debug logs to `~/.memind/claude-code.log`. |

### Environment Overrides

Supported settings can be overridden with environment variables:

```bash
export MEMIND_API_URL=http://127.0.0.1:8366
export MEMIND_API_TOKEN=...
export MEMIND_USER_ID=local__alice
export MEMIND_AGENT_ID=claude-code
export MEMIND_AGENT_ID_MODE=project
export MEMIND_SOURCE_CLIENT=claude-code
export MEMIND_AUTO_INGEST_AGENT_TIMELINE=true
export MEMIND_RETRIEVE_CONTEXT_TURNS=0
export MEMIND_DEBUG=true
```

Additional environment variables include `MEMIND_AUTO_RETRIEVE`, `MEMIND_RETRIEVE_STRATEGY`,
`MEMIND_RETRIEVE_MAX_ENTRIES`, `MEMIND_RETRIEVE_MAX_CHARS`, `MEMIND_STATE_MAX_AGE_DAYS`,
`MEMIND_INGEST_RETRY_SPOOL`, `MEMIND_INGEST_RETRY_MAX_FILES`, and
`MEMIND_INGEST_RETRY_MAX_AGE_DAYS`.

## Identity Model

By default, Memind stores Claude Code memory under:

- `userId`: `local__<system-user>`
- `agentId`: `claude-code__<project-name>-<stable-hash>`

The project hash is based on the Git remote URL when available, otherwise the local project path. This keeps
different repositories separated while allowing memory to survive moving between Claude Code sessions.

To use one shared Claude Code memory across all projects:

```json
{
  "agentId": "claude-code",
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

Ingestion is timeline-only for Claude Code. The plugin does not submit transcript conversation rawdata. It buffers
tool and command events under `~/.memind/claude-code/state/` and flushes them through
`AsyncMemindClient.memory.extract(...)` as agent timeline rawdata. A typical timeline payload looks like:

```json
{
  "userId": "local__alice",
  "agentId": "claude-code__project_hash",
  "sourceClient": "claude-code",
  "rawContent": {
    "type": "agent_timeline",
    "sourceClient": "claude-code",
    "sessionId": "session-123",
    "agentTurnId": "session-123-agent-turn-1-1",
    "timelineId": "session-123-agent-1-2",
    "project": {"name": "payment-service", "rootPath": "/repo/payment-service"},
    "events": [
      {
        "eventId": "event-id",
        "seq": 1,
        "kind": "command",
        "toolName": "Bash",
        "command": "npm test payment",
        "status": "failed",
        "exitCode": 1,
        "output": "{\"stdout\": \"rounding mismatch\"}"
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

2. Confirm Claude Code sees the plugin:

```bash
claude plugin list
```

3. Confirm the marketplace and plugin manifests are valid from a repository checkout:

```bash
claude plugin validate .claude-plugin/marketplace.json
claude plugin validate memind-integrations/claude-code
```

4. Start a new Claude Code session in a project and ask a project-specific question.
5. Check Memind data for entries whose `sourceClient` is `claude-code`.

### End-to-End Smoke Test

For a deterministic first test, use a fixed Memind identity. If you already have
`~/.memind/claude-code.json`, merge these fields instead of replacing the file:

```bash
mkdir -p ~/.memind
cat > ~/.memind/claude-code.json <<'JSON'
{
  "userId": "local__memind-smoke",
  "agentId": "claude-code-smoke",
  "agentIdMode": "fixed",
  "sourceClient": "claude-code",
  "debug": true
}
JSON
```

Then start a new Claude Code session and say something specific:

```text
Please remember: the Memind Claude Code smoke test topic is blue-lake-42.
```

After Claude Code responds, the `Stop` hook should submit the turn to Memind through
`AsyncMemindClient.memory.extract(...)`.
No manual commit is needed for the default reliable path.

Finally, verify retrieval:

```bash
curl -fsSL -X POST http://127.0.0.1:8366/open/v1/memory/retrieve \
  -H 'Content-Type: application/json' \
  -d '{
    "userId": "local__memind-smoke",
    "agentId": "claude-code-smoke",
    "query": "blue-lake-42",
    "strategy": "SIMPLE",
    "trace": false
  }'
```

The response should include matching `items` or `insights` under `data`. If raw conversation data exists but
`items` and `insights` are empty, the plugin has ingested the conversation but Memind has not produced retrievable
memory entries yet.

For local debugging, enable logs:

```bash
export MEMIND_DEBUG=true
```

Then inspect:

```bash
tail -f ~/.memind/claude-code.log
```

## Upgrade

Update the marketplace and plugin:

```bash
claude plugin marketplace update memind-plugins
claude plugin update memind-memory
```

If you installed from a local checkout, update the checkout first and then run the same commands.

Restart Claude Code after upgrading so updated hooks and settings are loaded.

## Uninstall

Remove the plugin:

```bash
claude plugin uninstall memind-memory
```

Optionally remove the Memind marketplace entry:

```bash
claude plugin marketplace remove memind-plugins
```

Uninstalling the plugin preserves local Memind runtime data such as:

- `~/.memind/claude-code.json`
- `~/.memind/claude-code/state/`
- `~/.memind/claude-code/retry/`
- `~/.memind/claude-code.log`

Delete those files manually only if you no longer need local configuration, retry payloads, or debugging logs.

## Troubleshooting

### Plugin is not found

- Add the marketplace before installing the plugin.
- Use the marketplace-qualified name if multiple marketplaces are configured:

```bash
claude plugin install memind-memory@memind-plugins --scope user
```

- For local marketplace installation, run the command from the repository root and use `./`, not a bare `.`:

```bash
claude plugin marketplace add ./ --scope user
```

### Hooks do not fire

- Start a new Claude Code session after installing or upgrading.
- Confirm `claude plugin list` shows `memind-memory`.
- Confirm `hooks/hooks.json` is present in the installed plugin.

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
- Confirm Claude Code is emitting `PreToolUse` and `PostToolUse` hooks.
- Confirm `~/.memind/claude-code/state/` is writable.
- Enable `MEMIND_DEBUG=true` and inspect `~/.memind/claude-code.log`.

### New memories are not immediately retrieved

The default reliable mode submits agent timelines through `AsyncMemindClient.memory.extract(...)`, so a `SUCCESS`
response means extraction finished for that timeline payload. If retrieval still does not surface the expected
memory, confirm the same `userId` and `agentId` are used for ingestion and retrieval, then inspect
`~/.memind/claude-code.log` with `MEMIND_DEBUG=true`.

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
- The plugin does not start or configure the Memind server.
