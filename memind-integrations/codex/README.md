# Memind Codex Integration

Memind adds persistent project memory to Codex CLI. The integration retrieves relevant Memind context before
each user prompt and appends Codex conversation messages to Memind's conversation buffer after each turn.

Use this integration when you want Codex to remember project facts, preferences, and previous decisions across
sessions. The plugin connects Codex to an already-running Memind server; it does not start the server itself.

The integration is intentionally small:

- No Python third-party packages.
- No Codex marketplace dependency.
- No overwrite of existing Codex hooks or config.
- No tool-call ingestion in v0.1.

## What It Does

- **Retrieval**: `UserPromptSubmit` calls Memind `/open/v1/memory/retrieve` and injects relevant memories into
  the Codex prompt as `<memind_memories>...</memind_memories>`.
- **Ingestion**: `Stop` reads the Codex transcript, filters user/assistant messages, and sends new messages to
  Memind `/open/v1/memory/add-message`.
- **Retry**: failed ingestion batches are spooled under `~/.memind/codex/retry/` and replayed on the next
  `SessionStart`.
- **Source tagging**: all requests use `sourceClient = "codex"` by default, so Memind can distinguish Codex
  memory from Claude Code, OpenClaw, API calls, or future clients.

## Requirements

- Codex CLI with hook support.
- Python 3.9+ available as `python3`.
- A running Memind server, usually at `http://127.0.0.1:8366`.

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
- `~/.codex/config.toml`: `[features] codex_hooks = true` is added only when it is missing.

The installer does **not** overwrite unrelated Codex hooks or settings. Re-running the installer is idempotent:
old Memind-owned hook entries are removed before fresh entries are added.

The installer does not write `~/.memind/codex.json`. Create that file only when you need to override defaults.

## Hook Events

The installed hooks are:

| Codex event | Script | Timeout | Purpose |
| --- | --- | ---: | --- |
| `SessionStart` | `scripts/session_start.py` | 5s | Replay at most one failed ingestion batch and clean old state. |
| `UserPromptSubmit` | `scripts/retrieve.py` | 12s | Retrieve relevant Memind context for the current user prompt. |
| `Stop` | `scripts/ingest.py` | 15s | Append new Codex transcript messages to Memind. |

`PreToolUse`, `PostToolUse`, and `PermissionRequest` are intentionally unused in v0.1. Tool-call memory can be
added later after the data model and privacy behavior are explicitly designed.

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
  "commitOnStop": false,
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
| `autoIngest` | `true` | Enables transcript ingestion after Codex turns. |
| `commitOnStop` | `false` | When true, commits after each successful Stop ingestion. |
| `retrieveStrategy` | `SIMPLE` | Memind retrieval strategy. |
| `retrieveMaxEntries` | `8` | Maximum formatted memory entries injected into Codex. |
| `retrieveMaxChars` | `6000` | Maximum injected context characters. |
| `retrieveContextTurns` | `0` | Number of recent transcript turns to include in the retrieval query. |
| `ingestionRoles` | `["user", "assistant"]` | Transcript roles eligible for ingestion. |
| `ingestionMaxMessagesPerHook` | `20` | Maximum new messages sent during one Stop hook. |
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
export MEMIND_COMMIT_ON_STOP=false
export MEMIND_RETRIEVE_CONTEXT_TURNS=0
export MEMIND_DEBUG=true
```

Additional environment variables include `MEMIND_AUTO_RETRIEVE`, `MEMIND_AUTO_INGEST`,
`MEMIND_RETRIEVE_STRATEGY`, `MEMIND_RETRIEVE_MAX_ENTRIES`, `MEMIND_RETRIEVE_MAX_CHARS`,
`MEMIND_INGESTION_ROLES`, `MEMIND_INGESTION_MAX_MESSAGES_PER_HOOK`, `MEMIND_STATE_MAX_AGE_DAYS`,
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

## Ingestion Behavior

Ingestion runs after each Codex turn when `autoIngest = true`.

The Stop hook:

1. Reads the Codex transcript.
2. Extracts final user and assistant message text.
3. Strips previously injected `<memind_memories>` blocks to avoid feedback loops.
4. Skips tool/event payloads and Codex control context blocks.
5. Computes stable fingerprints and sends only messages that have not already been submitted.
6. Retries each failed `add-message` once before writing a retry payload.

Accepted fingerprints are persisted immediately, so partial progress is preserved even if later messages fail.
Unsubmitted messages are retried by future hooks.

`commitOnStop` defaults to `false`. This lets Memind's own boundary detector decide when to extract memory.
Set it to `true` only when you want faster cross-session availability and accept more frequent extraction work.

## Verify Installation

After installation, verify the integration in this order:

1. Confirm Memind server is reachable:

```bash
curl -fsSL http://127.0.0.1:8366/open/v1/health
```

2. Confirm Codex hooks are enabled:

```toml
[features]
codex_hooks = true
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
- `[features] codex_hooks = true`

The feature flag is preserved because other Codex hooks may depend on it.

## Troubleshooting

### Hooks do not fire

- Start a new Codex session after installing.
- Confirm `~/.codex/config.toml` contains:

```toml
[features]
codex_hooks = true
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

### Messages are not ingested

- Confirm `autoIngest` is `true`.
- Confirm Codex provides `transcript_path` in hook payloads.
- Confirm `~/.memind/codex/state/` is writable.
- Enable `MEMIND_DEBUG=true` and inspect `~/.memind/codex.log`.

### Stop hook times out

- Confirm Memind server responds quickly.
- Reduce `ingestionMaxMessagesPerHook`.
- Keep `commitOnStop = false` unless immediate extraction is required.

### Duplicate messages appear

The integration uses per-session fingerprints stored under `~/.memind/codex/state/`. If duplicates appear:

- Confirm the state directory is writable.
- Check whether Codex transcript identifiers changed across sessions.
- Remove stale local state only if you accept that old transcript messages may be re-submitted.

## Limitations

- v0.1 supports conversation memory only; tool calls are not ingested.
- Retrieval quality depends on existing extracted Memind items and insights.
- `commitOnStop = false` means newly appended messages may not become retrievable until Memind naturally commits
  and extracts them.
