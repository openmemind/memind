# Memind Hermes Agent Integration

Memind adds persistent agent memory to Hermes Agent through a native Hermes memory provider.
The provider retrieves relevant Memind context before each turn and submits completed Hermes
activity as Memind `agent_timeline` raw data after each response.

Use this integration when you want Hermes to remember user preferences, durable facts, decisions,
commitments, task outcomes, and prior discussions across sessions.

The integration is intentionally small:

- Uses the official Memind Python client.
- Connects to an already-running `memind-server`.
- Does not manage a local Memind daemon.
- Does not require MCP for automatic memory.
- Captures general-agent lifecycle events; it does not enable coding-specific file context.
- Does not ingest media in v0.1.

## Requirements

- Hermes Agent with memory provider support.
- Python 3.10+ in the environment used by Hermes plugins.
- The official `memind` Python package installed in that same Python environment.
- A running Memind server, usually at `http://127.0.0.1:8366`.

Check the Memind server before enabling the provider:

```bash
curl -fsSL http://127.0.0.1:8366/open/v1/health
```

Install the Python client in the Python environment Hermes uses. If Hermes has a dedicated
virtual environment, use that interpreter:

```bash
python3 -m pip install memind
```

## Quick Start

Copy the provider into Hermes' memory-provider plugin directory:

```bash
mkdir -p ~/.hermes/plugins/memory
cp -R memind-integrations/hermes ~/.hermes/plugins/memory/memind
```

Some older Hermes versions used a flat `~/.hermes/plugins/<name>` directory. If your Hermes
version still documents that layout, copy the provider to the path required by that version.

Configure Hermes to use Memind as the memory provider:

```bash
hermes config set memory.provider memind
```

Restart Hermes and check status:

```bash
hermes memory status
```

## Configuration

User overrides live in `~/.memind/hermes.json`:

```json
{
  "memindApiUrl": "http://127.0.0.1:8366",
  "memindApiToken": null,
  "userId": "local__alice",
  "agentId": "hermes",
  "agentIdMode": "project",
  "sourceClient": "hermes",
  "memoryMode": "hybrid",
  "autoRetrieve": true,
  "autoIngest": true,
  "autoIngestAgentTimeline": true,
  "retrieveStrategy": "SIMPLE"
}
```

Settings load in this order:

1. Built-in defaults from `settings.json`.
2. User overrides from `~/.memind/hermes.json`.
3. Environment variables.

Common environment overrides:

```bash
export MEMIND_API_URL=http://127.0.0.1:8366
export MEMIND_API_TOKEN=...
export MEMIND_USER_ID=local__alice
export MEMIND_AGENT_ID=hermes
export MEMIND_AGENT_ID_MODE=project
export MEMIND_SOURCE_CLIENT=hermes
export MEMIND_MEMORY_MODE=hybrid
export MEMIND_RETRIEVE_STRATEGY=SIMPLE
export MEMIND_AUTO_INGEST_AGENT_TIMELINE=true
export MEMIND_TIMELINE_MAX_EVENTS=500
export MEMIND_TIMELINE_MAX_FIELD_CHARS=8000
export MEMIND_TIMELINE_FLUSH_MIN_EVENTS=2
```

## Memory Modes

`memoryMode` controls how Hermes receives memory:

| Mode | Automatic recall | Explicit tools |
| --- | --- | --- |
| `hybrid` | Yes | Yes |
| `context` | Yes | No |
| `tools` | No | Yes |

`hybrid` is the default. Use `context` when you want invisible automatic memory without
additional tools. Use `tools` when you want the model to call memory deliberately.

## Tools

When `memoryMode` is `hybrid` or `tools`, the provider exposes:

- `memind_retrieve`: retrieve relevant Memind memory for the configured `userId` and `agentId`.
- `memind_extract_text`: extract memory from standalone text such as pasted notes or document excerpts.

Normal Hermes turns are captured automatically as `agent_timeline` raw data through the memory
provider.
Use `memind_extract_text` only for one-off text memory.

## Identity Model

By default, Memind stores Hermes memory under:

- `userId`: `local__<system-user>`
- `agentId`: `hermes__<project-name>-<stable-hash>`

The project hash is based on the Git remote URL when available, otherwise the local project path.
This keeps existing users from unexpectedly merging memories across workspaces.

For general-agent usage where the same assistant should remember across workspaces, channels, or
sessions, set `"agentIdMode": "fixed"` and choose a stable `"agentId"`. Runtime context such as
`workspaceDir` and `sessionKey` is stored in `agent_timeline.metadata`; it does not change the
Memind memory identity. Set `"agentIdMode": "session"` to isolate by Hermes session.

## Agent Timeline Ingestion

Automatic lifecycle capture is controlled by `autoIngest` and `autoIngestAgentTimeline`.

- `sync_turn` appends `user_prompt`, `assistant_message`, and `stop`, then flushes one timeline.
- `on_memory_write` appends a `notification` event with `metadata.memoryWrite = true`, appends
  `stop`, then flushes.
- `on_pre_compress` appends `compact_boundary` and still injects pre-compress retrieval context.
- `on_session_end` appends `session_end` and force-flushes remaining events.

The submitted payload uses `metadata.profile = "general"` and `metadata.runtime = "hermes"`.
The explicit `memind_extract_text` tool remains a standalone conversation/raw-text extraction path;
it is intentionally separate from automatic agent lifecycle capture.

## Relationship To HTTP MCP

`memind-server` also exposes an HTTP MCP endpoint at `/mcp`. Hermes users may connect that
HTTP MCP endpoint if their Hermes version supports remote HTTP MCP, but MCP is optional.

Use the Hermes memory provider for automatic recall and automatic ingestion. Use HTTP MCP only
when you want generic MCP tool access.

## Security

Keep the default loopback URL unless Memind is running behind an authenticated private deployment.
If `MEMIND_API_TOKEN` is set, use HTTPS or a trusted tunnel for non-loopback endpoints so bearer
tokens and memory payloads are not sent over plaintext HTTP.

Do not expose the `/mcp` endpoint publicly without authentication and network controls. MCP tools
can read and write scoped memory.

## Troubleshooting

- If the provider does not load, confirm the plugin was copied to `~/.hermes/plugins/memory/memind`.
- If `hermes memory status` cannot reach Memind, confirm `memind-server` is running.
- If imports fail, install the `memind` Python package into the Python environment Hermes uses.
- If automatic recall does not happen, confirm `memoryMode` is `hybrid` or `context` and `autoRetrieve` is `true`.
- If new memories do not appear immediately, ask again on a later turn. Extraction happens after the response.
- If authenticated requests fail, confirm `MEMIND_API_TOKEN` is present in the Hermes process environment.
