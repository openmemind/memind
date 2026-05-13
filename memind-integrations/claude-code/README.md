# Memind Claude Code Integration

Memind adds persistent project memory to Claude Code. The plugin retrieves relevant Memind context before each
user prompt and submits Claude Code conversation messages through Memind's reliable extraction endpoint during
session lifecycle hooks.

Use this plugin when you want Claude Code to remember project facts, preferences, implementation decisions, and
previous discussions across sessions. The plugin connects Claude Code to an already-running Memind server; it
does not start the server itself.

The integration is intentionally small:

- Uses the official Memind Python client.
- No local daemon management.
- No MCP dependency.
- No tool-call ingestion in v0.1.

## What It Does

- **Retrieval**: `UserPromptSubmit` calls `MemindClient.memory.retrieve(...)` and injects relevant memories into
  Claude Code as `<memind_memories>...</memind_memories>` additional context.
- **Ingestion**: `Stop`, `PreCompact`, and `SessionEnd` read the Claude Code transcript, filter
  user/assistant messages, and submit a caller-owned conversation payload through
  `AsyncMemindClient.memory.extract(...)`.
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
| `PreCompact` | `scripts/pre_compact.py` | 30s | Submit recent transcript messages through reliable extraction before context compaction. |
| `Stop` | `scripts/ingest.py` | 15s | Submit new transcript messages through reliable extraction after a turn. |
| `SessionEnd` | `scripts/session_end.py` | 10s | Submit remaining transcript messages through reliable extraction at session end. |

`Stop` is configured as async so regular turn completion stays fast. `PreToolUse` and `PostToolUse` are
intentionally unused in v0.1 because tool-call memory needs an explicit privacy and data-model design.

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
  "ingestionMode": "extract-sync",
  "preCompactCommit": true,
  "commitOnSessionEnd": true,
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
| `autoIngest` | `true` | Enables transcript ingestion during lifecycle hooks. |
| `retrieveStrategy` | `SIMPLE` | Memind retrieval strategy. |
| `retrieveMaxEntries` | `8` | Maximum formatted memory entries injected into Claude Code. |
| `retrieveMaxChars` | `6000` | Maximum injected context characters. |
| `retrieveContextTurns` | `0` | Number of recent transcript turns to include in the retrieval query. |
| `ingestionMode` | `extract-sync` | Default reliable ingestion mode. |
| `ingestionRoles` | `["user", "assistant"]` | Transcript roles eligible for ingestion. |
| `ingestionMaxMessagesPerHook` | `20` | Maximum new messages sent during one regular ingestion hook. |
| `preCompactCommit` | `true` | Compatibility flag for server-buffer ingestion mode; ignored by the default reliable mode. |
| `preCompactMaxMessages` | `20` | Maximum messages submitted during one `PreCompact` hook. |
| `commitOnSessionEnd` | `true` | Compatibility flag for server-buffer ingestion mode; ignored by the default reliable mode. |
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
export MEMIND_INGESTION_MODE=extract-sync
export MEMIND_PRE_COMPACT_COMMIT=true
export MEMIND_COMMIT_ON_SESSION_END=true
export MEMIND_RETRIEVE_CONTEXT_TURNS=0
export MEMIND_DEBUG=true
```

Additional environment variables include `MEMIND_AUTO_RETRIEVE`, `MEMIND_AUTO_INGEST`,
`MEMIND_RETRIEVE_STRATEGY`, `MEMIND_INGESTION_MODE`, `MEMIND_INGESTION_ROLES`,
`MEMIND_INGESTION_MAX_MESSAGES_PER_HOOK`, `MEMIND_PRE_COMPACT_MAX_MESSAGES`,
`MEMIND_STATE_MAX_AGE_DAYS`, `MEMIND_INGEST_RETRY_SPOOL`, `MEMIND_INGEST_RETRY_MAX_FILES`, and
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

## Ingestion Behavior

Ingestion reads Claude Code's transcript when `autoIngest = true`.

The ingestion flow:

1. Reads the Claude Code JSONL transcript.
2. Extracts user and assistant message text.
3. Strips previously injected `<memind_memories>` blocks to avoid feedback loops.
4. Skips tool/event payloads, unsupported roles, and Claude Code interruption placeholders.
5. Computes stable fingerprints and sends only messages that have not already been submitted.
6. Builds one caller-owned conversation raw-content payload and submits it through
   `AsyncMemindClient.memory.extract(...)`.

The local retry spool stores the full extraction payload plus the covered message fingerprints. Fingerprints are
marked submitted only after Memind returns `SUCCESS`; `PARTIAL_SUCCESS` and failures keep the payload available
for later replay.

Commit flags apply only to explicit server-buffer mode. In the default reliable mode, hooks do not issue an
additional `/commit` call after successful `/extract/sync`.

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

### Messages are not ingested

- Confirm `autoIngest` is `true`.
- Confirm Claude Code provides `transcript_path` in hook payloads.
- Confirm `~/.memind/claude-code/state/` is writable.
- Enable `MEMIND_DEBUG=true` and inspect `~/.memind/claude-code.log`.

### New memories are not immediately retrieved

The default reliable mode submits transcript batches through `AsyncMemindClient.memory.extract(...)`, so a
`SUCCESS` response means extraction finished for that batch. If retrieval still does not surface the expected
memory, confirm the same `userId` and `agentId` are used for ingestion and retrieval, then inspect
`~/.memind/claude-code.log` with `MEMIND_DEBUG=true`.

### Duplicate messages appear

The integration uses per-session fingerprints stored under `~/.memind/claude-code/state/`. If duplicates appear:

- Confirm the state directory is writable.
- Check whether Claude Code transcript identifiers changed across sessions.
- Remove stale local state only if you accept that old transcript messages may be re-submitted.

## Limitations

- v0.1 supports conversation memory only; tool calls are not ingested.
- Retrieval quality depends on existing extracted Memind items and insights.
- The plugin does not start or configure the Memind server.
