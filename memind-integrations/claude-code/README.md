# Memind Claude Code Integration

Memind adds persistent project memory to Claude Code. The plugin injects project continuity context at session
start, can inject exact file/tool context before high-value tools, and submits Claude Code coding-agent timelines
through Memind's reliable extraction endpoint during session lifecycle hooks.

Use this plugin when you want Claude Code to remember project facts, preferences, implementation decisions, and
previous discussions across sessions. The plugin connects Claude Code to an already-running Memind server; it
does not start the server itself.

The integration is intentionally small:

- Uses the official Memind Python client.
- No local daemon management.
- No MCP dependency.
- Captures each coding-agent turn as Memind `agent_timeline` raw data: user prompt, tool/command activity,
  optional final assistant text, and stop boundary.

## What It Does

- **Prompt context (optional)**: `UserPromptSubmit` always buffers the user prompt into the local agent timeline.
  If `autoPromptContext=true`, it also retrieves project-first Memind memories with a bounded global fallback and
  injects them as `<memind_memories>...</memind_memories>`.
- **Ingestion**: `PreToolUse` and `PostToolUse` buffer normalized tool events locally. `Stop` flushes the complete
  turn as `rawContent.type = "agent_timeline"` through `AsyncMemindClient.memory.extract(...)`, so Memind can extract
  user and agent memories from the same agent turn. `PreCompact` only records a local `compact_boundary` checkpoint;
  it is submitted with the next `Stop` or `SessionEnd` flush. `Notification` and `SubagentStop` are buffered as
  lifecycle evidence when Claude Code emits them.
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
| `SessionStart` | `scripts/session_start.py` | 5s | Health check, replay at most one failed retry payload, clean old state, and inject project continuity context when available. |
| `UserPromptSubmit` | `scripts/retrieve.py` | 12s | Buffer the user prompt event. Optionally inject project-first prompt memory when `autoPromptContext=true`. |
| `PreToolUse` | `scripts/pre_tool_use.py` | 5s | Buffer a redacted tool-start event and, for high-value file edits or commands, inject compact file/tool memory context. |
| `PostToolUse` | `scripts/post_tool_use.py` | 5s | Buffer a redacted tool-result event in local session state. |
| `Notification` | `scripts/notification.py` | 5s | Buffer permission, blocking, and other user-visible lifecycle notifications. |
| `SubagentStop` | `scripts/subagent_stop.py` | 5s | Buffer subagent completion evidence for later playbook and handoff extraction. |
| `PreCompact` | `scripts/pre_compact.py` | 30s | Record a local `compact_boundary` checkpoint before context compaction. |
| `Stop` | `scripts/ingest.py` | 15s | Flush buffered `agent_timeline` events after a turn. |
| `SessionEnd` | `scripts/session_end.py` | 10s | Flush remaining buffered `agent_timeline` events at session end. |

`PostToolUse`, `Stop`, `Notification`, and `SubagentStop` are configured as async so regular turn execution is not
blocked by ingestion work. `PreToolUse` is intentionally synchronous because it may inject a small context block before
the tool executes; it still fails open and skips retrieval for low-value tools.

## Configuration

The default configuration works with a local Memind server at `http://127.0.0.1:8366`.

User configuration is optional. Save overrides as `~/.memind/claude-code.json`:

```json
{
  "memindApiUrl": "http://127.0.0.1:8366",
  "memindApiToken": null,
  "userId": "local__alice",
  "agentId": "coding-agent",
  "sourceClient": "claude-code",
  "autoIngestAgentTimeline": true,
  "autoPromptContext": false,
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
| `agentId` | `coding-agent` | Shared Memind agent identity. Use the same value from Claude Code, Codex, and API clients to share one coding-agent memory space. |
| `sourceClient` | `claude-code` | Source marker stored with Memind data. |
| `autoRetrieve` | `true` | Backward-compatible broad retrieval gate used by prompt and tool retrieval paths. Leave enabled unless you want to disable retrieval-assisted contexts entirely. |
| `autoPromptContext` | `false` | Enables prompt-time `<memind_memories>` retrieval and injection on `UserPromptSubmit`. Off by default to avoid token cost and unrelated cross-project recall. |
| `autoSessionContext` | `true` | Enables SessionStart project continuity context injection. |
| `autoIngestAgentTimeline` | `true` | Enables user prompt, tool/result, assistant message, and stop event buffering plus `agent_timeline` rawdata flush. |
| `autoToolContext` | `true` | Enables compact PreToolUse context for high-value file edits and commands. |
| `retrieveStrategy` | `SIMPLE` | Memind retrieval strategy. |
| `retrieveMaxEntries` | `8` | Maximum formatted memory entries injected into Claude Code. |
| `retrieveMaxChars` | `6000` | Maximum injected context characters. |
| `retrieveContextTurns` | `0` | Number of recent transcript turns to include in the retrieval query. |
| `promptContextProjectMinEntries` | `4` | Minimum current-project entries before global fallback is skipped. |
| `promptContextGlobalFallbackEntries` | `3` | Maximum fallback entries from the shared memory space when current-project results are sparse. |
| `promptContextGlobalFallbackMinScore` | `0.65` | Minimum score for fallback entries. |
| `toolContextMaxChars` | `3500` | Maximum injected PreToolUse context characters. |
| `toolContextEntryMaxChars` | `520` | Maximum characters per PreToolUse context entry. |
| `toolContextMaxItems` | `6` | Maximum exact or fallback items considered for PreToolUse context. |
| `toolContextMinExactItems` | `2` | Minimum exact item hits before semantic retrieve fallback is skipped. |
| `sessionContextRecentSessions` | `3` | Maximum recent `agent_timeline` captions shown at SessionStart. |
| `sessionContextMaxItems` | `6` | Maximum items fetched for each SessionStart context section. |
| `sessionContextMaxChars` | `6000` | Maximum SessionStart context characters. |
| `ingestRetrySpool` | `true` | Enables file-backed retry for failed extraction payloads. |
| `debug` | `false` | Writes debug logs to `~/.memind/claude-code.log`. |

### Environment Overrides

Supported settings can be overridden with environment variables:

```bash
export MEMIND_API_URL=http://127.0.0.1:8366
export MEMIND_API_TOKEN=...
export MEMIND_USER_ID=local__alice
export MEMIND_AGENT_ID=coding-agent
export MEMIND_SOURCE_CLIENT=claude-code
export MEMIND_AUTO_PROMPT_CONTEXT=false
export MEMIND_AUTO_SESSION_CONTEXT=true
export MEMIND_AUTO_TOOL_CONTEXT=true
export MEMIND_AUTO_INGEST_AGENT_TIMELINE=true
export MEMIND_TOOL_CONTEXT_MAX_CHARS=3500
export MEMIND_SESSION_CONTEXT_MAX_CHARS=6000
export MEMIND_RETRIEVE_CONTEXT_TURNS=0
export MEMIND_DEBUG=true
```

Additional environment variables include `MEMIND_AUTO_RETRIEVE`, `MEMIND_RETRIEVE_STRATEGY`,
`MEMIND_RETRIEVE_MAX_ENTRIES`, `MEMIND_RETRIEVE_MAX_CHARS`, `MEMIND_STATE_MAX_AGE_DAYS`,
`MEMIND_SESSION_CONTEXT_RECENT_SESSIONS`, `MEMIND_SESSION_CONTEXT_MAX_ITEMS`,
`MEMIND_PROMPT_CONTEXT_PROJECT_MIN_ENTRIES`, `MEMIND_PROMPT_CONTEXT_GLOBAL_FALLBACK_ENTRIES`,
`MEMIND_PROMPT_CONTEXT_GLOBAL_FALLBACK_MIN_SCORE`,
`MEMIND_TOOL_CONTEXT_ENTRY_MAX_CHARS`, `MEMIND_TOOL_CONTEXT_MAX_ITEMS`,
`MEMIND_TOOL_CONTEXT_MIN_EXACT_ITEMS`, `MEMIND_INGEST_RETRY_SPOOL`,
`MEMIND_INGEST_RETRY_MAX_FILES`, and `MEMIND_INGEST_RETRY_MAX_AGE_DAYS`.

## Identity Model

By default, Memind stores Claude Code memory under:

- `userId`: `local__<system-user>`
- `agentId`: `coding-agent`

Claude Code, Codex, and direct Memind API clients can share memory by using the same `userId` and `agentId`.
`sourceClient` records where a memory came from; it is not an isolation boundary.

Project information is stored as rawdata and item metadata, including a stable `projectSlug` based on the Git
remote URL when available, otherwise the local project path. Project metadata supports ranking, diagnostics, and
future context compilation without creating separate Memind core project or session entities. `sessionId`,
`agentTurnId`, `timelineId`, and per-event turn metadata are also stored only inside raw content and item metadata.

## SessionStart Context

When `autoSessionContext = true`, the `SessionStart` hook reads existing Memind data for the current `userId`,
`agentId`, and project `metadata.projectSlug`. It does not write rawdata and does not trigger memory extraction.

The injected context is compiled from generic OpenAPI query results:

```text
<memind_session_context project="payment-service-<stable-hash>">
Historical Memind project memory. Use only when directly helpful. Current user instructions and repository files take precedence. Verify old implementation details against the working tree before relying on them.

## Continue From
- [rawdata:rd-1, 2026-05-27] Completed SessionStart context injection for Claude Code and Codex.

## Must Follow
- [item:101 directive, 2026-05-27] Keep userId and agentId stable; use metadata.projectSlug for project isolation.

## Watch Outs
- [item:102 resolution, 2026-05-27] Codex tests must run with Python 3.12; older Python can fail on modern type syntax.

## Reusable Playbooks
- [item:103 playbook, 2026-05-27] After changing Claude Code or Codex hooks, run both integration unittest suites and git diff --check.

## Useful Facts
- [item:104 event, 2026-05-27] SessionStart is read-only: it queries memory and injects context without writing rawdata.
</memind_session_context>
```

This project-continuity context is separate from prompt-time retrieval. It helps a new Claude Code session know what
recently happened in this project before the first user prompt is handled.

## Prompt Context

Prompt context is disabled by default. `UserPromptSubmit` still buffers the user prompt into the local
`agent_timeline` state, but it does not inject `<memind_memories>` unless `autoPromptContext = true`.

Enable prompt-time recall only when you want query-aware memory on every prompt:

```json
{
  "autoPromptContext": true,
  "promptContextProjectMinEntries": 4,
  "promptContextGlobalFallbackEntries": 3,
  "promptContextGlobalFallbackMinScore": 0.65
}
```

When enabled, Memind first retrieves memories constrained by the current project's `metadata.projectSlug`. If those
project hits are sparse, it adds a bounded global fallback from the same `userId + agentId` memory space. The injected
context marks source provenance as `project`, `global`, or `shared`.

The injected context format is:

```text
<memind_memories project="memind-main-a1b2c3" mode="project-first">
Relevant memories from Memind. Use only when directly helpful:

## Directives
- [item:201 directive, project, 2026-05-27] Do not default Claude Code or Codex to conversation rawdata.
- [item:206 behavior, global, 2026-05-20] User prefers Chinese replies for technical discussions.

## Resolved Problems
- [item:202 resolution, project, 2026-05-27] Retry spool events are cleared only after successful agent_timeline extraction.

## Agent Playbooks
- [item:203 playbook, project, 2026-05-27] When hooks change, run both integration test suites and git diff --check.

## Tool Notes
- [item:204 tool, shared, 2026-05-18] Use Python 3.12 for the Codex integration test suite.

## Insights
- [insight:301 root, project, 2026-05-27] Coding-agent integrations share memory through stable userId and agentId.

## Memory Items
- [item:205 event, project, 2026-05-27] rawdata-agent emits agent_episode segment metadata.
</memind_memories>
```

The adapter compiles retrieved Memind results into execution-oriented sections. Directives, resolved problems,
playbooks, and tool notes get independent caps so a high-scoring generic item cannot crowd out coding-agent memory.
Higher-level insights (`ROOT`, then `BRANCH`) are preferred; `LEAF` insights are omitted by default unless no
higher-level insights are available.

`retrieveContextTurns` defaults to `0`, so retrieval uses only the current prompt and does not read large
transcripts. Set it to `1` or `2` if your prompts are often short, such as "fix this" or "continue".

Agent memory items are grouped separately when returned by Memind:

```text
## Directives
## Resolved Problems
## Agent Playbooks
## Tool Notes
```

The compiler deduplicates per section, applies section budgets, and preserves the closing XML-style wrapper when the
context must be truncated.

## PreToolUse Context

For high-value tools such as `Edit`, `Write`, `MultiEdit`, and validation `Bash` commands, Memind may inject a compact
tool-specific context block:

```text
<memind_tool_context tool="Edit" file="src/payment/calc.ts">
Use only if directly relevant to this exact tool call. Current user instructions and repository files take precedence.

## Prior Resolutions
- [item:res-1 resolution] rounding mismatch was resolved in src/payment/calc.ts and validated with npm test payment.

## Validation Notes
- [item:tool-1 tool] Use npm test payment to validate changes touching src/payment/calc.ts.
</memind_tool_context>
```

The context is built from existing Memind items and `agent_episode` metadata. It does not add extra LLM calls and does
not submit duplicate `tool_call` raw data. Setting `autoToolContext` to `false` disables only this PreToolUse context
injection; tool-start events are still buffered into the local `agent_timeline` state for later Stop-time extraction.

## Ingestion Behavior

Ingestion is timeline-only for Claude Code. The plugin does not submit transcript conversation-style raw data. It buffers
one turn timeline under `~/.memind/claude-code/state/`: the submitted user prompt, tool and command events, optional
compact checkpoints, the latest assistant message when available from the transcript, and a stop boundary. It flushes
the turn through `AsyncMemindClient.memory.extract(...)` as agent timeline rawdata on `Stop`; `SessionEnd` flushes any
remaining buffered events as a cleanup path. A typical timeline payload looks like:

```json
{
  "userId": "local__alice",
  "agentId": "coding-agent",
  "sourceClient": "claude-code",
  "rawContent": {
    "type": "agent_timeline",
    "sourceClient": "claude-code",
    "sessionId": "session-123",
    "agentTurnId": "session-123-turn-1",
    "timelineId": "session-123-turn-1-timeline",
    "project": {
      "name": "payment-service",
      "rootPath": "/repo/payment-service",
      "metadata": {"projectSlug": "payment-service-<stable-hash>"}
    },
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
hook stores normalized tool metadata, commands, paths, statuses, and bounded outputs.

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
  "agentId": "coding-agent-smoke",
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
    "agentId": "coding-agent-smoke",
    "query": "blue-lake-42",
    "strategy": "SIMPLE",
    "trace": false
  }'
```

The response should include matching `items` or `insights` under `data`. Project metadata such as `projectSlug`
is stored on the rawdata/item metadata, not in `agentId`. If agent timelines exist but `items` and `insights` are
empty, the server-side `rawdata-agent` extractor did not produce retrievable memory entries yet.

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
- Confirm `autoPromptContext` is `true` for prompt-time `<memind_memories>` injection. SessionStart and PreToolUse context use separate switches.
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
- `rawdata-agent` absorbs deterministic tool telemetry from the `rawdata-toolcall` design: duration, token counts,
  content hashes, per-episode tool records, and per-tool success/failure stats. Claude Code still submits one
  canonical `agent_timeline` per turn; it does not submit duplicate `tool_call` raw data. `rawdata-toolcall`
  remains the correct entry point for pure tool-call logs that do not have user prompts, agent turns, or Stop
  boundaries.
- Retrieval quality depends on existing extracted Memind items and insights.
- The plugin does not start or configure the Memind server.
