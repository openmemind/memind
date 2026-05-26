# Agent Hook Journal Pipeline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Strengthen Memind's Claude Code and Codex integrations so coding-agent hook activity is durably captured, project-isolated by `agentId`, flushed as high-quality `agent_timeline` rawdata, and extracted by `rawdata-agent` without adding project/session concepts to Memind core.

**Architecture:** Claude Code and Codex remain adapter layers: they normalize host-specific hook payloads into Memind `agent_event` records, persist them in a local durable journal, and flush bounded turn timelines on reliable lifecycle boundaries. The `rawdata-agent` plugin remains the canonical parser/extractor: it accepts `agent_timeline`, assembles `agent_episode` segments, generates captions, and extracts memory items through existing Memind item, insight, and graph capabilities.

**Tech Stack:** Python 3.10+ hook scripts and unit tests, Java 21 rawdata-agent plugin, Maven, Jackson, Reactor, Memind RawData/Item/Insight/Graph APIs, Claude Code hooks, Codex hooks.

---

## Scope

This plan covers the next implementation phase only:

- Improve hook-to-memory extraction for both `memind-integrations/claude-code` and `memind-integrations/codex`.
- Make Claude Code and Codex identities always project-scoped through `agentId`.
- Keep session, turn, timeline, and event attribution in metadata/raw content only.
- Expand `rawdata-agent` to understand the richer normalized event stream.
- Preserve the current timeline-only ingestion policy for Claude Code and Codex.

This plan explicitly does not cover:

- Retrieval Context Compiler changes. Prompt-time memory formatting remains out of scope for this phase.
- A Memind core project model or session model.
- An `observation` core abstraction copied from claude-mem or agentmemory.
- Conversation rawdata ingestion from Claude Code or Codex transcripts.
- LLM gate / skip heuristics before extraction.

## Design Commitments

Memind should learn from agentmemory's broad lifecycle capture and claude-mem's product polish, but the implementation must stay idiomatic to Memind:

- `agent_event` is an adapter-level evidence record, not a memory item.
- `agent_timeline` is the rawdata submitted to Memind.
- `agent_episode` is the Memind rawdata segment used for captioning and item extraction.
- Project isolation uses the existing `userId + agentId` memory boundary. The project-specific suffix is part of `agentId`.
- Session and turn identifiers are metadata that improve attribution and segmentation; they do not become first-class Memind core entities.

## End-To-End Flow

```text
Claude Code hook payload
Codex hook payload
  -> adapter-specific normalization and privacy cleanup
  -> normalized agent_event
  -> local durable journal/state
  -> Stop / PreCompact / SessionEnd / supported lifecycle flush boundary
  -> rawContent.type = "agent_timeline"
  -> rawdata-agent plugin
  -> agent_episode segment(s)
  -> caption, vectorization, item extraction, graph hints, insight tree
```

For Claude Code, supported hook events in this phase are:

```text
SessionStart, UserPromptSubmit, PreToolUse, PostToolUse, Notification, Stop, SubagentStop, PreCompact, SessionEnd
```

For Codex, supported hook events in this phase are the current adapter-supported set:

```text
SessionStart, UserPromptSubmit, PreToolUse, PostToolUse, Stop
```

Do not register Claude Code-only events in Codex unless Codex explicitly supports them in the local adapter and tests.

## File Structure

### Claude Code Integration

- Modify `memind-integrations/claude-code/scripts/lib/identity.py`
  - Always resolve `agentId` to `<baseAgentId>__<projectSlug>`.
  - Keep `projectSlug` based on Git remote hash when available, else resolved root path hash.
- Modify `memind-integrations/claude-code/scripts/lib/config.py`
  - Remove `agentIdMode` from defaults and environment mappings.
- Modify `memind-integrations/claude-code/settings.json`
  - Remove `agentIdMode`.
- Modify `memind-integrations/claude-code/scripts/lib/agent_timeline.py`
  - Add normalization helpers for notification, subagent stop, compact boundary, session end, and generic lifecycle events.
  - Keep redaction, truncation, file/tool/command normalization, and stable event IDs.
- Modify `memind-integrations/claude-code/scripts/lib/state.py`
  - Improve journal semantics for boundary preservation, flushed-event deletion, empty-state cleanup, and buffer truncation metadata.
- Modify `memind-integrations/claude-code/scripts/retrieve.py`
  - Continue appending `USER_PROMPT` before retrieval.
  - Ensure turn metadata is written consistently.
- Modify `memind-integrations/claude-code/scripts/pre_tool_use.py`
  - Continue appending tool-start evidence.
- Modify `memind-integrations/claude-code/scripts/post_tool_use.py`
  - Continue appending tool-result evidence.
- Create `memind-integrations/claude-code/scripts/notification.py`
  - Append notification evidence where useful.
- Create `memind-integrations/claude-code/scripts/subagent_stop.py`
  - Append subagent completion evidence.
- Modify `memind-integrations/claude-code/scripts/pre_compact.py`
  - Append `compact_boundary` before flushing.
- Modify `memind-integrations/claude-code/scripts/session_end.py`
  - Append `session_end` before flushing.
- Modify `memind-integrations/claude-code/scripts/ingest.py`
  - Support explicit flush reasons and boundary events without duplicating stop handling.
- Modify `memind-integrations/claude-code/hooks/hooks.json`
  - Register `Notification` and `SubagentStop`.
  - Keep existing `SessionStart`, `UserPromptSubmit`, `PreToolUse`, `PostToolUse`, `PreCompact`, `Stop`, `SessionEnd`.
- Modify `memind-integrations/claude-code/README.md`
  - Document timeline-only ingestion, project-scoped `agentId`, supported hooks, and metadata-only sessions.
- Modify tests under `memind-integrations/claude-code/tests/`.

### Codex Integration

- Modify `memind-integrations/codex/scripts/lib/identity.py`
  - Always resolve `agentId` to `<baseAgentId>__<projectSlug>`.
- Modify `memind-integrations/codex/scripts/lib/config.py`
  - Remove `agentIdMode` from defaults and environment mappings.
- Modify `memind-integrations/codex/settings.json`
  - Remove `agentIdMode`.
- Modify `memind-integrations/codex/scripts/lib/agent_timeline.py`
  - Keep normalization behavior aligned with Claude Code for the event types Codex can emit.
  - Do not add Codex hook scripts or manifest entries for Claude Code-only lifecycle events.
  - Generic parsing helpers may exist only when they are used by Codex tests or by shared test fixtures; they are not a Codex hook support commitment.
- Modify `memind-integrations/codex/scripts/lib/state.py`
  - Align durable journal behavior with Claude Code while preserving Codex's `state_key(hook_input)` behavior.
- Modify `memind-integrations/codex/scripts/retrieve.py`
  - Continue appending `USER_PROMPT` before retrieval.
- Modify `memind-integrations/codex/scripts/pre_tool_use.py`
  - Continue appending tool-start evidence.
- Modify `memind-integrations/codex/scripts/post_tool_use.py`
  - Continue appending tool-result evidence.
- Modify `memind-integrations/codex/scripts/ingest.py`
  - Flush on `Stop` and preserve Codex-specific `sessionKey` retry payloads.
- Modify `memind-integrations/codex/hooks/hooks.json`
  - Keep only `SessionStart`, `UserPromptSubmit`, `PreToolUse`, `PostToolUse`, and `Stop` unless Codex support is explicitly verified in tests.
- Modify `memind-integrations/codex/scripts/install_codex_hooks.py`
  - Ensure installer tests still prove idempotent merging for the supported hook set.
- Modify `memind-integrations/codex/README.md`
  - Document project-scoped `agentId`, timeline-only ingestion, supported hook boundaries, and why unsupported Claude Code lifecycle hooks are not registered.
- Modify tests under `memind-integrations/codex/tests/`.

### rawdata-agent Plugin

- Modify `memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent/src/main/java/com/openmemind/ai/memory/plugin/rawdata/agent/model/AgentEventKind.java`
  - Add richer normalized event kinds.
- Modify `memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent/src/main/java/com/openmemind/ai/memory/plugin/rawdata/agent/content/AgentTimelineContent.java`
  - Ensure formatting handles new event kinds without dropping evidence.
- Modify `memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent/src/main/java/com/openmemind/ai/memory/plugin/rawdata/agent/chunk/AgentEpisodeAssembler.java`
  - Segment by `USER_PROMPT -> ... -> STOP` as the preferred turn boundary.
  - Treat compact/session boundaries as terminal boundaries.
  - Preserve phase split behavior for oversized episodes.
- Modify `memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent/src/main/java/com/openmemind/ai/memory/plugin/rawdata/agent/caption/AgentCaptionGenerator.java`
  - Include useful subagent, notification, compact, and session-end signals when present.
- Modify `memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent/src/main/java/com/openmemind/ai/memory/plugin/rawdata/agent/item/AgentMemoryItemFactory.java`
  - Improve deterministic tool/resolution extraction for richer event evidence.
- Modify `memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent/src/main/java/com/openmemind/ai/memory/plugin/rawdata/agent/item/AgentItemExtractionStrategy.java`
  - Ensure LLM items validate `evidenceEventIds` against the episode event IDs.
- Modify tests under `memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent/src/test/java/com/openmemind/ai/memory/plugin/rawdata/agent/`.

---

## Tasks

### Task 1: Remove `agentIdMode` And Make Agent Identity Always Project-Scoped

**Files:**
- Modify: `memind-integrations/claude-code/scripts/lib/identity.py`
- Modify: `memind-integrations/claude-code/scripts/lib/config.py`
- Modify: `memind-integrations/claude-code/settings.json`
- Modify: `memind-integrations/claude-code/tests/test_identity.py`
- Modify: `memind-integrations/claude-code/tests/test_manifest.py`
- Modify: `memind-integrations/claude-code/tests/test_config.py`
- Modify: `memind-integrations/codex/scripts/lib/identity.py`
- Modify: `memind-integrations/codex/scripts/lib/config.py`
- Modify: `memind-integrations/codex/settings.json`
- Modify: `memind-integrations/codex/tests/test_identity.py`
- Modify: `memind-integrations/codex/tests/test_manifest.py`
- Modify: `memind-integrations/codex/tests/test_config.py`

- [ ] **Step 1: Add failing identity tests for Claude Code**

In `memind-integrations/claude-code/tests/test_identity.py`, replace the `agentIdMode`-dependent test with assertions that `resolve_identity()` always appends the project slug:

```python
def test_resolve_identity_always_uses_project_slug(self):
    with tempfile.TemporaryDirectory() as tmp:
        identity = resolve_identity({"agentId": "claude-code"}, {"cwd": tmp})
    self.assertTrue(identity["userId"].startswith("local__"))
    self.assertTrue(identity["agentId"].startswith("claude-code__"))
    self.assertNotEqual(identity["agentId"], "claude-code")
    self.assertNotIn(":", identity["userId"])
    self.assertNotIn(":", identity["agentId"])


def test_agent_id_mode_is_ignored_for_backward_safety(self):
    with tempfile.TemporaryDirectory() as tmp:
        identity = resolve_identity(
            {"agentId": "claude-code", "agentIdMode": "global"},
            {"cwd": tmp},
        )
    self.assertTrue(identity["agentId"].startswith("claude-code__"))
```

- [ ] **Step 2: Add failing identity tests for Codex**

In `memind-integrations/codex/tests/test_identity.py`, mirror the Claude Code assertions with base agent `codex`:

```python
def test_resolve_identity_always_uses_project_slug(self):
    with tempfile.TemporaryDirectory() as tmp:
        identity = resolve_identity({"agentId": "codex", "userId": "u"}, {"cwd": tmp})
    self.assertEqual(identity["userId"], "u")
    self.assertTrue(identity["agentId"].startswith("codex__"))
    self.assertNotEqual(identity["agentId"], "codex")


def test_agent_id_mode_is_ignored_for_backward_safety(self):
    with tempfile.TemporaryDirectory() as tmp:
        identity = resolve_identity(
            {"agentId": "codex", "agentIdMode": "global", "userId": "u"},
            {"cwd": tmp},
        )
    self.assertTrue(identity["agentId"].startswith("codex__"))
```

- [ ] **Step 3: Add failing config and manifest tests**

In both `test_manifest.py` files, assert default settings do not expose `agentIdMode`:

```python
self.assertNotIn("agentIdMode", settings)
```

In both `test_config.py` files, assert loaded config does not include `agentIdMode` by default and `MEMIND_AGENT_ID_MODE` is no longer accepted as a documented override:

```python
self.assertNotIn("agentIdMode", config)
```

Run:

```bash
python3 -m unittest \
  memind-integrations/claude-code/tests/test_identity.py \
  memind-integrations/claude-code/tests/test_manifest.py \
  memind-integrations/claude-code/tests/test_config.py \
  memind-integrations/codex/tests/test_identity.py \
  memind-integrations/codex/tests/test_manifest.py \
  memind-integrations/codex/tests/test_config.py
```

Expected: tests fail because `agentIdMode` still exists and `resolve_identity()` can still return the base agent ID.

- [ ] **Step 4: Update identity implementation**

In both `identity.py` files, change `resolve_identity()` to:

```python
def resolve_identity(config, hook_input):
    cwd = hook_input.get("cwd") or os.getcwd()
    user_id = config.get("userId") or f"local{SEPARATOR}{getpass.getuser()}"
    base_agent = config.get("agentId") or "claude-code"  # use "codex" in the Codex file
    agent_id = f"{base_agent}{SEPARATOR}{project_slug(cwd)}"
    return {"userId": user_id, "agentId": agent_id}
```

Keep `_git_remote()`, `_hash()`, and `project_slug()` unchanged unless tests reveal a real bug.

- [ ] **Step 5: Remove configuration surface**

In both `config.py` files:

- Remove `"agentIdMode": "project"` from `DEFAULT_SETTINGS`.
- Remove `"MEMIND_AGENT_ID_MODE": ("agentIdMode", str)` from `ENV_MAP`.

In both `settings.json` files:

- Remove the `agentIdMode` property.

- [ ] **Step 6: Run tests**

Run:

```bash
python3 -m unittest \
  memind-integrations/claude-code/tests/test_identity.py \
  memind-integrations/claude-code/tests/test_manifest.py \
  memind-integrations/claude-code/tests/test_config.py \
  memind-integrations/codex/tests/test_identity.py \
  memind-integrations/codex/tests/test_manifest.py \
  memind-integrations/codex/tests/test_config.py
```

Expected: all selected tests pass.

- [ ] **Step 7: Commit**

```bash
git add \
  memind-integrations/claude-code/scripts/lib/identity.py \
  memind-integrations/claude-code/scripts/lib/config.py \
  memind-integrations/claude-code/settings.json \
  memind-integrations/claude-code/tests/test_identity.py \
  memind-integrations/claude-code/tests/test_manifest.py \
  memind-integrations/claude-code/tests/test_config.py \
  memind-integrations/codex/scripts/lib/identity.py \
  memind-integrations/codex/scripts/lib/config.py \
  memind-integrations/codex/settings.json \
  memind-integrations/codex/tests/test_identity.py \
  memind-integrations/codex/tests/test_manifest.py \
  memind-integrations/codex/tests/test_config.py
git commit -m "fix: make coding agent identities project scoped"
```

### Task 2: Normalize Session, Turn, Timeline, And Event Attribution

**Files:**
- Modify: `memind-integrations/claude-code/scripts/lib/agent_timeline.py`
- Modify: `memind-integrations/claude-code/scripts/retrieve.py`
- Modify: `memind-integrations/claude-code/scripts/pre_tool_use.py`
- Modify: `memind-integrations/claude-code/scripts/post_tool_use.py`
- Modify: `memind-integrations/claude-code/scripts/ingest.py`
- Modify: `memind-integrations/claude-code/tests/test_agent_timeline.py`
- Modify: `memind-integrations/claude-code/tests/test_hooks.py`
- Modify: `memind-integrations/codex/scripts/lib/agent_timeline.py`
- Modify: `memind-integrations/codex/scripts/retrieve.py`
- Modify: `memind-integrations/codex/scripts/pre_tool_use.py`
- Modify: `memind-integrations/codex/scripts/post_tool_use.py`
- Modify: `memind-integrations/codex/scripts/ingest.py`
- Modify: `memind-integrations/codex/tests/test_agent_timeline.py`
- Modify: `memind-integrations/codex/tests/test_hooks.py`

- [ ] **Step 1: Add failing tests for event metadata**

In both integration `test_agent_timeline.py` files, extend existing user prompt/tool/stop tests to assert metadata contains:

```python
self.assertEqual(event["metadata"]["sessionId"], "s")
self.assertEqual(event["metadata"]["sourceClient"], "claude-code")  # "codex" in Codex tests
self.assertEqual(event["metadata"]["turnId"], "s-turn-1")
self.assertEqual(event["metadata"]["turnSeq"], 1)
```

For `build_timeline_payload()`, add assertions:

```python
self.assertEqual(payload["metadata"]["sessionId"], "s")
self.assertEqual(payload["metadata"]["sourceClient"], "claude-code")  # "codex" in Codex tests
self.assertEqual(payload["metadata"]["turnId"], "s-turn-1")
self.assertEqual(payload["metadata"]["turnSeq"], 1)
self.assertEqual(payload["metadata"]["eventIds"], ["e1", "e2"])
self.assertEqual(payload["timelineId"], "s-turn-1-timeline")
```

Run:

```bash
python3 -m unittest \
  memind-integrations/claude-code/tests/test_agent_timeline.py \
  memind-integrations/codex/tests/test_agent_timeline.py
```

Expected: tests fail because session/source metadata is not consistently present on every event and timeline metadata.

- [ ] **Step 2: Centralize base event metadata**

In both `agent_timeline.py` files, update `_base_event()` so all events created through it include:

```python
metadata = {
    "hookEventName": hook_input.get("hook_event_name"),
    "sessionId": session_id,
    "sourceClient": source_client,
}
```

Keep `turnId` and `turnSeq` only when passed.

- [ ] **Step 3: Add session/source metadata to tool events**

In both `normalize_hook_event()` implementations, initialize metadata with:

```python
metadata = {
    "hookEventName": hook_input.get("hook_event_name"),
    "sessionId": session_id,
    "sourceClient": source_client,
}
```

Then merge normalization metadata, turn metadata, and redaction metadata as today.

- [ ] **Step 4: Add timeline metadata**

In both `build_timeline_payload()` implementations, include:

```python
"metadata": {
    "userId": identity.get("userId"),
    "agentId": identity.get("agentId"),
    "sessionId": session_id,
    "sourceClient": source_client,
    "eventIds": [event["eventId"] for event in events if event.get("eventId")],
}
```

Preserve existing `turnId`, `turnSeq`, and `project` behavior.

- [ ] **Step 5: Run tests**

Run:

```bash
python3 -m unittest \
  memind-integrations/claude-code/tests/test_agent_timeline.py \
  memind-integrations/claude-code/tests/test_hooks.py \
  memind-integrations/codex/tests/test_agent_timeline.py \
  memind-integrations/codex/tests/test_hooks.py
```

Expected: all selected tests pass.

- [ ] **Step 6: Commit**

```bash
git add \
  memind-integrations/claude-code/scripts/lib/agent_timeline.py \
  memind-integrations/claude-code/scripts/retrieve.py \
  memind-integrations/claude-code/scripts/pre_tool_use.py \
  memind-integrations/claude-code/scripts/post_tool_use.py \
  memind-integrations/claude-code/scripts/ingest.py \
  memind-integrations/claude-code/tests/test_agent_timeline.py \
  memind-integrations/claude-code/tests/test_hooks.py \
  memind-integrations/codex/scripts/lib/agent_timeline.py \
  memind-integrations/codex/scripts/retrieve.py \
  memind-integrations/codex/scripts/pre_tool_use.py \
  memind-integrations/codex/scripts/post_tool_use.py \
  memind-integrations/codex/scripts/ingest.py \
  memind-integrations/codex/tests/test_agent_timeline.py \
  memind-integrations/codex/tests/test_hooks.py
git commit -m "fix: normalize agent timeline attribution metadata"
```

### Task 3: Expand Normalized Agent Event Kinds

**Files:**
- Modify: `memind-integrations/claude-code/scripts/lib/agent_timeline.py`
- Modify: `memind-integrations/claude-code/tests/test_agent_timeline.py`
- Modify: `memind-integrations/codex/scripts/lib/agent_timeline.py`
- Modify: `memind-integrations/codex/tests/test_agent_timeline.py`
- Modify: `memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent/src/main/java/com/openmemind/ai/memory/plugin/rawdata/agent/model/AgentEventKind.java`
- Modify: `memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent/src/test/java/com/openmemind/ai/memory/plugin/rawdata/agent/content/AgentTimelineContentTest.java`

- [ ] **Step 1: Add failing Python normalization tests**

In Claude Code `test_agent_timeline.py`, add tests for:

```python
def test_normalizes_notification_event(self):
    event = normalize_notification_event(
        {
            "hook_event_name": "Notification",
            "session_id": "s",
            "message": "Claude needs permission to run Bash",
            "timestamp": "2026-05-24T10:00:00Z",
        },
        seq=1,
        turn_id="s-turn-1",
        turn_seq=1,
    )
    self.assertEqual(event["kind"], "notification")
    self.assertEqual(event["text"], "Claude needs permission to run Bash")
    self.assertEqual(event["status"], "success")


def test_normalizes_subagent_stop_event(self):
    event = normalize_subagent_stop_event(
        {
            "hook_event_name": "SubagentStop",
            "session_id": "s",
            "subagent_type": "explorer",
            "message": "Found failing resolver test",
            "timestamp": "2026-05-24T10:01:00Z",
        },
        seq=2,
        turn_id="s-turn-1",
        turn_seq=1,
    )
    self.assertEqual(event["kind"], "subagent_stop")
    self.assertEqual(event["operation"], "explorer")
    self.assertIn("Found failing resolver test", event["text"])
```

In both Claude Code and Codex `test_agent_timeline.py`, add tests for:

```python
def test_normalizes_compact_boundary_event(self):
    event = normalize_compact_boundary_event(
        {"hook_event_name": "PreCompact", "session_id": "s", "timestamp": "2026-05-24T10:02:00Z"},
        seq=3,
        turn_id="s-turn-1",
        turn_seq=1,
    )
    self.assertEqual(event["kind"], "compact_boundary")
    self.assertEqual(event["status"], "success")


def test_normalizes_session_end_event(self):
    event = normalize_session_end_event(
        {"hook_event_name": "SessionEnd", "session_id": "s", "timestamp": "2026-05-24T10:03:00Z"},
        seq=4,
        turn_id="s-turn-1",
        turn_seq=1,
    )
    self.assertEqual(event["kind"], "session_end")
    self.assertEqual(event["status"], "success")
```

Codex may not use compact/session-end hooks yet, but shared parsing should accept those event kinds if rawdata arrives from another adapter.

- [ ] **Step 2: Implement Python normalization helpers**

In Claude Code `agent_timeline.py`, add:

```python
def normalize_notification_event(hook_input, seq, turn_id=None, turn_seq=None):
    text, redaction_kinds = redact_text(
        hook_input.get("message")
        or hook_input.get("notification")
        or hook_input.get("text")
        or ""
    )
    event = _base_event(hook_input, seq, "notification", turn_id, turn_seq, text)
    event["text"] = text
    event["status"] = "success"
    metadata = dict(event["metadata"])
    if _looks_blocking_notification(text):
        metadata["notificationKind"] = "blocked"
        metadata["failureSignal"] = text
    else:
        metadata["notificationKind"] = "info"
    if redaction_kinds:
        metadata["redacted"] = True
        metadata["redactionKinds"] = sorted(set(redaction_kinds))
    event["metadata"] = metadata
    return {key: value for key, value in event.items() if value is not None and value != ""}
```

Add the small helper:

```python
def _looks_blocking_notification(text):
    lowered = (text or "").lower()
    return any(token in lowered for token in ["permission", "blocked", "denied", "failed", "error"])
```

Add:

```python
def normalize_subagent_stop_event(hook_input, seq, turn_id=None, turn_seq=None):
    subagent_type = hook_input.get("subagent_type") or hook_input.get("subagentType") or hook_input.get("type")
    text, redaction_kinds = redact_text(
        hook_input.get("message")
        or hook_input.get("summary")
        or hook_input.get("result")
        or ""
    )
    event = _base_event(hook_input, seq, "subagent_stop", turn_id, turn_seq, text)
    event["text"] = text
    event["operation"] = subagent_type
    event["status"] = "success"
    metadata = dict(event["metadata"])
    if subagent_type:
        metadata["subagentType"] = subagent_type
    if redaction_kinds:
        metadata["redacted"] = True
        metadata["redactionKinds"] = sorted(set(redaction_kinds))
    event["metadata"] = metadata
    return {key: value for key, value in event.items() if value is not None and value != ""}
```

Add:

```python
def normalize_compact_boundary_event(hook_input, seq, turn_id=None, turn_seq=None):
    event = _base_event(hook_input, seq, "compact_boundary", turn_id, turn_seq, "compact")
    event["status"] = "success"
    event["operation"] = hook_input.get("trigger") or hook_input.get("compact_reason") or "compact"
    return {key: value for key, value in event.items() if value is not None and value != ""}


def normalize_session_end_event(hook_input, seq, turn_id=None, turn_seq=None):
    event = _base_event(hook_input, seq, "session_end", turn_id, turn_seq, "session_end")
    event["status"] = "success"
    event["operation"] = hook_input.get("reason") or hook_input.get("session_end_reason") or "session_end"
    return {key: value for key, value in event.items() if value is not None and value != ""}
```

In Codex `agent_timeline.py`, add only the helpers that are exercised by Codex tests in this phase. Do not add Codex hook scripts, manifest entries, or README claims for `Notification`, `SubagentStop`, `PreCompact`, or `SessionEnd` unless Codex support is explicitly added and tested in the Codex adapter. If compact/session-end helper functions are added for parser compatibility, keep them private to normalization tests and document that they are accepted raw event shapes, not registered Codex hooks.

- [ ] **Step 3: Add failing Java enum parsing test**

In `AgentTimelineContentTest.java`, add a test that parses/uses these wire values:

```java
assertThat(AgentEventKind.fromWireValue("notification")).isEqualTo(AgentEventKind.NOTIFICATION);
assertThat(AgentEventKind.fromWireValue("subagent_stop")).isEqualTo(AgentEventKind.SUBAGENT_STOP);
assertThat(AgentEventKind.fromWireValue("compact_boundary")).isEqualTo(AgentEventKind.COMPACT_BOUNDARY);
assertThat(AgentEventKind.fromWireValue("synthetic_boundary")).isEqualTo(AgentEventKind.SYNTHETIC_BOUNDARY);
```

Run:

```bash
mvn -pl memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent test \
  -Dtest=AgentTimelineContentTest
```

Expected: test fails because enum constants are missing.

- [ ] **Step 4: Add Java event kinds**

In `AgentEventKind.java`, add:

```java
TOOL_START,
TOOL_FAILURE,
SUBAGENT_START,
SUBAGENT_STOP,
NOTIFICATION,
COMPACT_BOUNDARY,
SYNTHETIC_BOUNDARY,
```

Keep existing constants. `TASK_COMPLETED` remains a general rawdata-agent kind even if Claude Code/Codex do not register a `TaskCompleted` hook in this phase.

- [ ] **Step 5: Run selected tests**

Run:

```bash
python3 -m unittest \
  memind-integrations/claude-code/tests/test_agent_timeline.py \
  memind-integrations/codex/tests/test_agent_timeline.py
mvn -pl memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent test \
  -Dtest=AgentTimelineContentTest
```

Expected: all selected tests pass.

- [ ] **Step 6: Commit**

```bash
git add \
  memind-integrations/claude-code/scripts/lib/agent_timeline.py \
  memind-integrations/claude-code/tests/test_agent_timeline.py \
  memind-integrations/codex/scripts/lib/agent_timeline.py \
  memind-integrations/codex/tests/test_agent_timeline.py \
  memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent/src/main/java/com/openmemind/ai/memory/plugin/rawdata/agent/model/AgentEventKind.java \
  memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent/src/test/java/com/openmemind/ai/memory/plugin/rawdata/agent/content/AgentTimelineContentTest.java
git commit -m "feat: expand normalized agent event kinds"
```

### Task 4: Add Claude Code Notification And Subagent Hook Capture

**Files:**
- Create: `memind-integrations/claude-code/scripts/notification.py`
- Create: `memind-integrations/claude-code/scripts/subagent_stop.py`
- Modify: `memind-integrations/claude-code/hooks/hooks.json`
- Modify: `memind-integrations/claude-code/tests/test_hooks.py`
- Modify: `memind-integrations/claude-code/tests/test_manifest.py`

- [ ] **Step 1: Add failing manifest tests**

In `memind-integrations/claude-code/tests/test_manifest.py`, add `Notification` and `SubagentStop` to the expected hook list:

```python
for event in [
    "SessionStart",
    "UserPromptSubmit",
    "PreToolUse",
    "PostToolUse",
    "Notification",
    "SubagentStop",
    "PreCompact",
    "Stop",
    "SessionEnd",
]:
    self.assertIn(event, hooks)
```

Assert both new hook commands are async and fail-open sized:

```python
self.assertTrue(hooks["Notification"][0]["hooks"][0]["async"])
self.assertTrue(hooks["SubagentStop"][0]["hooks"][0]["async"])
self.assertEqual(hooks["Notification"][0]["hooks"][0]["timeout"], 5)
self.assertEqual(hooks["SubagentStop"][0]["hooks"][0]["timeout"], 5)
```

- [ ] **Step 2: Add failing hook execution tests**

In `memind-integrations/claude-code/tests/test_hooks.py`, add:

```python
def test_notification_buffers_event(self):
    with tempfile.TemporaryDirectory() as tmp:
        state_dir = Path(tmp) / "state"
        env = {
            "CLAUDE_PLUGIN_ROOT": str(ROOT),
            "PYTHONPATH": str(ROOT),
            "MEMIND_CLAUDE_STATE_ROOT": str(state_dir),
        }
        output = self.run_hook(
            "notification.py",
            {
                "hook_event_name": "Notification",
                "cwd": tmp,
                "session_id": "s1",
                "message": "Permission required for Bash",
            },
            env=env,
        )
        self.assertEqual(output, {"continue": True})
        event = json.loads(next(state_dir.glob("*.json")).read_text())["agentEvents"][0]
        self.assertEqual(event["kind"], "notification")
        self.assertEqual(event["metadata"]["notificationKind"], "blocked")


def test_subagent_stop_buffers_event(self):
    with tempfile.TemporaryDirectory() as tmp:
        state_dir = Path(tmp) / "state"
        env = {
            "CLAUDE_PLUGIN_ROOT": str(ROOT),
            "PYTHONPATH": str(ROOT),
            "MEMIND_CLAUDE_STATE_ROOT": str(state_dir),
        }
        output = self.run_hook(
            "subagent_stop.py",
            {
                "hook_event_name": "SubagentStop",
                "cwd": tmp,
                "session_id": "s1",
                "subagent_type": "explorer",
                "message": "Found failing resolver test",
            },
            env=env,
        )
        self.assertEqual(output, {"continue": True})
        event = json.loads(next(state_dir.glob("*.json")).read_text())["agentEvents"][0]
        self.assertEqual(event["kind"], "subagent_stop")
        self.assertEqual(event["operation"], "explorer")
```

Run:

```bash
python3 -m unittest \
  memind-integrations/claude-code/tests/test_manifest.py \
  memind-integrations/claude-code/tests/test_hooks.py
```

Expected: tests fail because scripts and hook entries do not exist.

- [ ] **Step 3: Implement `notification.py`**

Create `memind-integrations/claude-code/scripts/notification.py` using the same fail-open pattern as `pre_tool_use.py`:

```python
#!/usr/bin/env python3
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from ingest import state_root
from lib.agent_timeline import normalize_notification_event
from lib.config import load_config
from lib.logging_utils import debug_log
from lib.state import SessionStateStore


def main():
    try:
        hook_input = json.loads(sys.stdin.read() or "{}")
        config = load_config()
        session_id = hook_input.get("session_id") or "unknown-session"
        hook_input["source_client"] = config.get("sourceClient") or "claude-code"
        with SessionStateStore(state_root()).locked(session_id) as state:
            turn_id, turn_seq = state.ensure_agent_turn(session_id)
            seq = state.next_agent_seq()
            state.append_agent_event(
                normalize_notification_event(
                    hook_input, seq, turn_id=turn_id, turn_seq=turn_seq
                )
            )
    except Exception as exc:
        try:
            debug_log(load_config(), "notification_failed", {"error": str(exc)})
        except Exception:
            pass
    print(json.dumps({"continue": True}))


if __name__ == "__main__":
    main()
```

Add the standard Apache license header before imports to match repository style.

- [ ] **Step 4: Implement `subagent_stop.py`**

Create `memind-integrations/claude-code/scripts/subagent_stop.py` with the same structure, calling `normalize_subagent_stop_event()` and logging `"subagent_stop_failed"`.

- [ ] **Step 5: Register hooks**

In `memind-integrations/claude-code/hooks/hooks.json`, add:

```json
"Notification": [
  {
    "hooks": [
      {
        "type": "command",
        "command": "python3 \"${CLAUDE_PLUGIN_ROOT}/scripts/notification.py\"",
        "timeout": 5,
        "async": true
      }
    ]
  }
],
"SubagentStop": [
  {
    "hooks": [
      {
        "type": "command",
        "command": "python3 \"${CLAUDE_PLUGIN_ROOT}/scripts/subagent_stop.py\"",
        "timeout": 5,
        "async": true
      }
    ]
  }
]
```

- [ ] **Step 6: Run tests**

Run:

```bash
python3 -m unittest \
  memind-integrations/claude-code/tests/test_manifest.py \
  memind-integrations/claude-code/tests/test_hooks.py
```

Expected: all selected tests pass.

- [ ] **Step 7: Commit**

```bash
git add \
  memind-integrations/claude-code/scripts/notification.py \
  memind-integrations/claude-code/scripts/subagent_stop.py \
  memind-integrations/claude-code/hooks/hooks.json \
  memind-integrations/claude-code/tests/test_hooks.py \
  memind-integrations/claude-code/tests/test_manifest.py
git commit -m "feat: capture claude code notification and subagent hooks"
```

### Task 5: Preserve Codex Hook Boundaries Without Simulating Unsupported Hooks

**Files:**
- Modify: `memind-integrations/codex/hooks/hooks.json`
- Modify: `memind-integrations/codex/scripts/install_codex_hooks.py`
- Modify: `memind-integrations/codex/tests/test_manifest.py`
- Modify: `memind-integrations/codex/tests/test_installer.py`
- Modify: `memind-integrations/codex/README.md`

- [ ] **Step 1: Add explicit Codex supported-hook test**

In `memind-integrations/codex/tests/test_manifest.py`, keep the supported set strict:

```python
self.assertEqual(
    set(hooks),
    {"SessionStart", "UserPromptSubmit", "PreToolUse", "PostToolUse", "Stop"},
)
self.assertNotIn("PreCompact", hooks)
self.assertNotIn("SessionEnd", hooks)
self.assertNotIn("Notification", hooks)
self.assertNotIn("SubagentStop", hooks)
```

Run:

```bash
python3 -m unittest memind-integrations/codex/tests/test_manifest.py
```

Expected: pass against current manifest. This test protects against accidental Claude Code hook leakage.

- [ ] **Step 2: Add installer regression assertion**

In `memind-integrations/codex/tests/test_installer.py`, add assertions after install that the installed hooks contain exactly the supported set plus any unrelated pre-existing hooks:

```python
memind_events = {
    event
    for event, groups in hooks["hooks"].items()
    for group in groups
    for hook in group.get("hooks", [])
    if "memind-integrations/codex" in hook.get("command", "") or "/memind/codex/" in hook.get("command", "")
}
self.assertEqual(
    memind_events,
    {"SessionStart", "UserPromptSubmit", "PreToolUse", "PostToolUse", "Stop"},
)
```

- [ ] **Step 3: Update Codex README**

In `memind-integrations/codex/README.md`, add a short note under Hook Events:

```markdown
Codex currently registers only the hook events listed above. Memind does not simulate Claude Code-only lifecycle
events such as `PreCompact`, `SessionEnd`, `Notification`, or `SubagentStop` in the Codex adapter. If Codex adds
native support for additional lifecycle events, they should be added as explicit hooks with tests.
```

- [ ] **Step 4: Run Codex tests**

Run:

```bash
python3 -m unittest \
  memind-integrations/codex/tests/test_manifest.py \
  memind-integrations/codex/tests/test_installer.py
```

Expected: all selected tests pass.

- [ ] **Step 5: Commit**

```bash
git add \
  memind-integrations/codex/hooks/hooks.json \
  memind-integrations/codex/scripts/install_codex_hooks.py \
  memind-integrations/codex/tests/test_manifest.py \
  memind-integrations/codex/tests/test_installer.py \
  memind-integrations/codex/README.md
git commit -m "test: keep codex hook support explicit"
```

### Task 6: Make Durable Journal Flush Boundaries Explicit

**Files:**
- Modify: `memind-integrations/claude-code/scripts/lib/state.py`
- Modify: `memind-integrations/claude-code/scripts/ingest.py`
- Modify: `memind-integrations/claude-code/scripts/pre_compact.py`
- Modify: `memind-integrations/claude-code/scripts/session_end.py`
- Modify: `memind-integrations/claude-code/tests/test_state.py`
- Modify: `memind-integrations/claude-code/tests/test_hooks.py`
- Modify: `memind-integrations/codex/scripts/lib/state.py`
- Modify: `memind-integrations/codex/scripts/ingest.py`
- Modify: `memind-integrations/codex/tests/test_state.py`
- Modify: `memind-integrations/codex/tests/test_hooks.py`

- [ ] **Step 1: Add state cleanup tests**

In both `test_state.py` files, add tests for empty state cleanup behavior through a new `is_empty()` helper:

```python
def test_state_reports_empty_after_all_events_are_cleared_and_turn_closed(self):
    with tempfile.TemporaryDirectory() as tmp:
        store = SessionStateStore(Path(tmp))
        with store.locked("session-1") as state:
            turn_id, _turn_seq = state.start_agent_turn("session-1")
            state.append_agent_event({"eventId": "e1", "seq": 1})
            state.clear_agent_events(["e1"])
            state.close_agent_turn(turn_id)
            self.assertTrue(state.is_empty())
```

For Codex, use the same test shape but name the local variable `session_key = "session-1"` before calling `store.locked(session_key)`. The expected behavior is identical because Codex's state store already converts hook payloads to a stable session key before opening the store.

- [ ] **Step 2: Add boundary preservation test**

In both `test_state.py` files, update or add a soft cap test so important boundaries survive truncation:

```python
def test_agent_event_buffer_soft_cap_preserves_current_turn_boundaries(self):
    with tempfile.TemporaryDirectory() as tmp:
        store = SessionStateStore(Path(tmp))
        with store.locked("session-1") as state:
            state.append_agent_event({"eventId": "prompt", "seq": 1, "kind": "user_prompt"})
            for index in range(600):
                state.append_agent_event({"eventId": f"e{index}", "seq": index + 2, "kind": "tool_result"})
            state.append_agent_event({"eventId": "stop", "seq": 700, "kind": "stop"})
    with store.locked("session-1") as state:
        events = state.agent_events()
        self.assertLessEqual(len(events), 500)
        self.assertEqual(events[-1]["eventId"], "stop")
        self.assertTrue(state.data["agentEventsTruncated"])
        self.assertIn("agentEventsDropped", state.data)
```

This test does not require preserving the oldest `USER_PROMPT` forever if the session has exceeded the cap before flush. It does require explicit truncation metadata and preserving the newest terminal boundary.

- [ ] **Step 3: Implement state helpers**

In both `state.py` files, add:

```python
def is_empty(self):
    return (
        not self.data.get("agentEvents")
        and not self.data.get("currentAgentTurnId")
        and not self.data.get("currentAgentTurnSeq")
    )
```

When soft cap truncates, increment a counter:

```python
dropped = len(events) - MAX_AGENT_EVENTS
events = events[-MAX_AGENT_EVENTS:]
self.data["agentEventsTruncated"] = True
self.data["agentEventsDropped"] = int(self.data.get("agentEventsDropped", 0)) + dropped
```

- [ ] **Step 4: Add explicit Claude Code boundary event tests**

In Claude Code `test_hooks.py`, add:

```python
def test_pre_compact_appends_compact_boundary_before_flush(self):
    sys.path.insert(0, str(ROOT / "scripts"))
    import ingest
    from scripts.lib.state import SessionStateStore

    config = {
        "memindApiUrl": "http://127.0.0.1:8366",
        "memindApiToken": None,
        "autoIngestAgentTimeline": True,
        "ingestRetrySpool": False,
        "sourceClient": "claude-code",
        "agentId": "claude-code",
        "userId": "u",
    }
    with tempfile.TemporaryDirectory() as tmp:
        state_root = Path(tmp) / "state"
        with SessionStateStore(state_root).locked("s1") as state:
            state.append_agent_event(
                {
                    "eventId": "e1",
                    "seq": 1,
                    "kind": "user_prompt",
                    "text": "Continue before compaction",
                    "metadata": {"turnId": "s1-turn-1", "turnSeq": 1},
                }
            )
        with mock.patch.object(ingest, "state_root", return_value=state_root):
            with mock.patch.object(ingest, "retry_root", return_value=Path(tmp) / "retry"):
                with mock.patch.object(ingest, "MemindClient") as client_cls:
                    client = client_cls.return_value
                    client.extract = mock.AsyncMock(return_value=types.SimpleNamespace(status="SUCCESS"))
                    result = ingest.ingest_messages(
                        config,
                        {
                            "hook_event_name": "PreCompact",
                            "session_id": "s1",
                            "cwd": tmp,
                            "timestamp": "2026-05-24T10:04:00Z",
                        },
                    )
    self.assertEqual(result["agentEventsSubmitted"], 2)
    raw_content = client.extract.await_args.args[2]
    self.assertEqual(raw_content["events"][-1]["kind"], "compact_boundary")
```

And:

```python
def test_session_end_appends_session_end_before_flush(self):
    sys.path.insert(0, str(ROOT / "scripts"))
    import ingest
    from scripts.lib.state import SessionStateStore

    config = {
        "memindApiUrl": "http://127.0.0.1:8366",
        "memindApiToken": None,
        "autoIngestAgentTimeline": True,
        "ingestRetrySpool": False,
        "sourceClient": "claude-code",
        "agentId": "claude-code",
        "userId": "u",
    }
    with tempfile.TemporaryDirectory() as tmp:
        state_root = Path(tmp) / "state"
        with SessionStateStore(state_root).locked("s1") as state:
            state.append_agent_event(
                {
                    "eventId": "e1",
                    "seq": 1,
                    "kind": "command",
                    "command": "npm test payment",
                    "metadata": {"turnId": "s1-turn-1", "turnSeq": 1},
                }
            )
        with mock.patch.object(ingest, "state_root", return_value=state_root):
            with mock.patch.object(ingest, "retry_root", return_value=Path(tmp) / "retry"):
                with mock.patch.object(ingest, "MemindClient") as client_cls:
                    client = client_cls.return_value
                    client.extract = mock.AsyncMock(return_value=types.SimpleNamespace(status="SUCCESS"))
                    result = ingest.ingest_messages(
                        config,
                        {
                            "hook_event_name": "SessionEnd",
                            "session_id": "s1",
                            "cwd": tmp,
                            "timestamp": "2026-05-24T10:05:00Z",
                        },
                    )
    self.assertEqual(result["agentEventsSubmitted"], 2)
    raw_content = client.extract.await_args.args[2]
    self.assertEqual(raw_content["events"][-1]["kind"], "session_end")
```

These tests intentionally call `ingest.ingest_messages()` directly instead of shelling out to `pre_compact.py` and `session_end.py`, because the behavior under test is the shared flush path and boundary append logic.

- [ ] **Step 5: Refactor Claude Code flush entrypoint**

In `ingest.py`, add a helper:

```python
def _append_boundary_event(state, session_id, hook_input):
    hook_name = hook_input.get("hook_event_name") or ""
    if hook_name == "Stop":
        return _append_stop_events(state, session_id, hook_input)
    if hook_name == "PreCompact":
        turn_id, turn_seq = state.ensure_agent_turn(session_id)
        seq = state.next_agent_seq()
        state.append_agent_event(
            normalize_compact_boundary_event(hook_input, seq, turn_id=turn_id, turn_seq=turn_seq)
        )
        return turn_id
    if hook_name == "SessionEnd":
        turn_id, turn_seq = state.ensure_agent_turn(session_id)
        seq = state.next_agent_seq()
        state.append_agent_event(
            normalize_session_end_event(hook_input, seq, turn_id=turn_id, turn_seq=turn_seq)
        )
        return turn_id
    return None
```

Use this helper where `_append_stop_events()` is called today.

After successful flush and `state.close_agent_turn(submitted_turn_id)`, keep the empty state file behavior conservative: clearing events is enough for this phase. Do not add `SessionStateStore.delete_if_empty(...)` in this phase. The new `is_empty()` helper is a testable state invariant and a future cleanup hook, not a requirement to delete journal files now. Never delete a state file that still contains unflushed events.

- [ ] **Step 6: Keep Codex Stop boundary behavior**

In Codex `ingest.py`, preserve the existing Stop-only behavior. Add assertions to existing Codex Stop tests that the final event is `stop` and the turn closes after successful flush.

- [ ] **Step 7: Run tests**

Run:

```bash
python3 -m unittest \
  memind-integrations/claude-code/tests/test_state.py \
  memind-integrations/claude-code/tests/test_hooks.py \
  memind-integrations/codex/tests/test_state.py \
  memind-integrations/codex/tests/test_hooks.py
```

Expected: all selected tests pass.

- [ ] **Step 8: Commit**

```bash
git add \
  memind-integrations/claude-code/scripts/lib/state.py \
  memind-integrations/claude-code/scripts/ingest.py \
  memind-integrations/claude-code/scripts/pre_compact.py \
  memind-integrations/claude-code/scripts/session_end.py \
  memind-integrations/claude-code/tests/test_state.py \
  memind-integrations/claude-code/tests/test_hooks.py \
  memind-integrations/codex/scripts/lib/state.py \
  memind-integrations/codex/scripts/ingest.py \
  memind-integrations/codex/tests/test_state.py \
  memind-integrations/codex/tests/test_hooks.py
git commit -m "fix: make agent journal flush boundaries explicit"
```

### Task 7: Update rawdata-agent Episode Assembly For Richer Events

**Files:**
- Modify: `memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent/src/main/java/com/openmemind/ai/memory/plugin/rawdata/agent/chunk/AgentEpisodeAssembler.java`
- Modify: `memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent/src/main/java/com/openmemind/ai/memory/plugin/rawdata/agent/content/AgentTimelineContent.java`
- Modify: `memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent/src/test/java/com/openmemind/ai/memory/plugin/rawdata/agent/chunk/AgentEpisodeAssemblerTest.java`
- Modify: `memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent/src/test/java/com/openmemind/ai/memory/plugin/rawdata/agent/content/AgentTimelineContentTest.java`

- [ ] **Step 1: Add failing episode boundary test**

In `AgentEpisodeAssemblerTest.java`, add a test with:

- `USER_PROMPT` seq 1.
- `FILE_READ` seq 2.
- `SUBAGENT_STOP` seq 3.
- `STOP` seq 4.
- `USER_PROMPT` seq 5.
- `COMMAND` seq 6.
- `COMPACT_BOUNDARY` seq 7.

Assert:

```java
assertThat(episodes).hasSize(2);
assertThat(episodes.get(0).eventIds()).containsExactly("e1", "e2", "e3", "e4");
assertThat(episodes.get(1).eventIds()).containsExactly("e5", "e6", "e7");
assertThat(episodes.get(0).phase()).isEqualTo("full");
assertThat(episodes.get(1).phase()).isEqualTo("full");
```

- [ ] **Step 2: Add failing phase classification test**

In `AgentEpisodeAssemblerTest.java`, add assertions that:

- `SUBAGENT_STOP` is not an implementation event by itself.
- `NOTIFICATION` with failed/cancelled status contributes to failure signals.
- `COMPACT_BOUNDARY`, `SESSION_END`, `SYNTHETIC_BOUNDARY`, and `STOP` are terminal events.

- [ ] **Step 3: Implement terminal boundaries**

In `AgentEpisodeAssembler.isTerminal()`, include:

```java
return event.kind() == AgentEventKind.STOP
        || event.kind() == AgentEventKind.SESSION_END
        || event.kind() == AgentEventKind.COMPACT_BOUNDARY
        || event.kind() == AgentEventKind.SYNTHETIC_BOUNDARY
        || event.kind() == AgentEventKind.TASK_COMPLETED;
```

- [ ] **Step 4: Update phase classification**

In `phase(AgentEvent event)`, treat:

- `FILE_EDIT` as `implementation`.
- successful `COMMAND` and `TEST_RESULT` as `validation`.
- terminal events and `ASSISTANT_MESSAGE` as `handoff`.
- `SUBAGENT_STOP`, `NOTIFICATION`, `FILE_READ`, `TOOL_RESULT`, and unknown tool evidence as `investigation` unless stronger local evidence indicates otherwise.

- [ ] **Step 5: Ensure formatter does not drop new events**

In `AgentTimelineContent` formatting tests, add a sample event for `notification`, `subagent_stop`, and `compact_boundary`. Assert formatted text contains the kind and meaningful text/operation.

- [ ] **Step 6: Run tests**

Run:

```bash
mvn -pl memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent test \
  -Dtest=AgentEpisodeAssemblerTest,AgentTimelineContentTest
```

Expected: selected tests pass.

- [ ] **Step 7: Commit**

```bash
git add \
  memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent/src/main/java/com/openmemind/ai/memory/plugin/rawdata/agent/chunk/AgentEpisodeAssembler.java \
  memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent/src/main/java/com/openmemind/ai/memory/plugin/rawdata/agent/content/AgentTimelineContent.java \
  memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent/src/test/java/com/openmemind/ai/memory/plugin/rawdata/agent/chunk/AgentEpisodeAssemblerTest.java \
  memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent/src/test/java/com/openmemind/ai/memory/plugin/rawdata/agent/content/AgentTimelineContentTest.java
git commit -m "feat: segment agent episodes across lifecycle boundaries"
```

### Task 8: Improve Deterministic Tool And Resolution Evidence

**Files:**
- Modify: `memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent/src/main/java/com/openmemind/ai/memory/plugin/rawdata/agent/item/AgentMemoryItemFactory.java`
- Modify: `memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent/src/main/java/com/openmemind/ai/memory/plugin/rawdata/agent/item/AgentItemExtractionStrategy.java`
- Modify: `memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent/src/test/java/com/openmemind/ai/memory/plugin/rawdata/agent/item/AgentItemExtractionStrategyTest.java`
- Modify: `memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent/src/test/java/com/openmemind/ai/memory/plugin/rawdata/agent/item/AgentItemExtractionStrategyLlmTest.java`

- [ ] **Step 1: Add failing deterministic resolution test**

In `AgentItemExtractionStrategyTest.java`, add a segment metadata fixture with:

- `failureSignals = ["payment rounding mismatch"]`
- `commandEvents` containing failed `npm test payment` at seq 3 and successful `npm test payment` at seq 8.
- `fileEvents` for `src/payment/calc.ts` between seq 3 and seq 8.
- `eventIds` containing all evidence IDs.

Assert extracted deterministic `resolution` metadata:

```java
assertThat(resolution.metadata().get("validatedBy")).isEqualTo("npm test payment");
assertThat((List<String>) resolution.metadata().get("evidenceEventIds"))
        .containsExactly("failed-test", "edit-calc", "passed-test");
```

This test makes the "later successful validation" rule precise: later means `candidate.seq > failed.seq`, successful means `status == success`, and matching means `sameCommandFamily(failed.command, candidate.command)`.

- [ ] **Step 2: Add weak notification test**

In `AgentItemExtractionStrategyTest.java`, add a segment metadata fixture with:

```java
Map.of(
        "segmentType", "agent_episode",
        "episodeId", "episode-notification",
        "eventIds", List.of("notice-1"),
        "files", List.of(),
        "commands", List.of(),
        "toolNames", List.of(),
        "failureSignals", List.of(),
        "outcome", "unknown")
```

The segment text should mention a harmless notification, such as `Claude is waiting for input.`. Assert:

```java
assertThat(strategy.extract(List.of(segment), List.of(), config).block()).isEmpty();
```

If the test uses `AgentMemoryItemFactory` directly, assert `deterministicEntries(segment)` is empty. This prevents low-value notifications from becoming memory items by themselves.

- [ ] **Step 3: Add subagent evidence test**

In `AgentItemExtractionStrategyLlmTest.java`, add a segment whose metadata includes:

```java
Map.of(
        "segmentType", "agent_episode",
        "episodeId", "episode-subagent",
        "eventIds", List.of("prompt-1", "subagent-1", "stop-1"),
        "files", List.of("src/payment/calc.ts"),
        "commands", List.of("npm test payment"),
        "toolNames", List.of("Task"),
        "failureSignals", List.of(),
        "outcome", "success")
```

Mock the structured chat client to return an extracted item:

```java
new MemoryItemExtractionResponse.ExtractedItem(
        "When payment test failures are unclear, ask an explorer subagent to inspect the failing resolver before editing calc.ts.",
        0.86f,
        null,
        null,
        List.of("playbooks"),
        Map.of("evidenceEventIds", List.of("subagent-1")),
        "playbook")
```

Assert the item is accepted and its metadata keeps `evidenceEventIds = ["subagent-1"]`. Add a companion response with `evidenceEventIds = ["outside-event"]` and assert it is rejected.

- [ ] **Step 4: Tighten deterministic resolution logic**

In `AgentMemoryItemFactory`, keep the existing validation scan but ensure:

- failed command event must have `failed() == true`;
- validation candidate must have `candidate.seq() > failed.seq()`;
- validation candidate must have `success() == true`;
- `sameCommandFamily(failed.command(), candidate.command())` must be true;
- file evidence is included only when `failed.seq < file.seq < candidate.seq`;
- all evidence IDs are non-blank and de-duplicated in event order.

- [ ] **Step 5: Validate LLM evidence IDs**

In `AgentItemExtractionStrategy.isValidItem()`, preserve the existing evidence validation and add a targeted test that rejects an LLM item with:

```json
{"metadata": {"evidenceEventIds": ["outside-event"]}}
```

when `outside-event` is not in the segment metadata `eventIds`.

- [ ] **Step 6: Run tests**

Run:

```bash
mvn -pl memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent test \
  -Dtest=AgentItemExtractionStrategyTest,AgentItemExtractionStrategyLlmTest
```

Expected: selected tests pass.

- [ ] **Step 7: Commit**

```bash
git add \
  memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent/src/main/java/com/openmemind/ai/memory/plugin/rawdata/agent/item/AgentMemoryItemFactory.java \
  memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent/src/main/java/com/openmemind/ai/memory/plugin/rawdata/agent/item/AgentItemExtractionStrategy.java \
  memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent/src/test/java/com/openmemind/ai/memory/plugin/rawdata/agent/item/AgentItemExtractionStrategyTest.java \
  memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent/src/test/java/com/openmemind/ai/memory/plugin/rawdata/agent/item/AgentItemExtractionStrategyLlmTest.java
git commit -m "fix: strengthen agent item evidence validation"
```

### Task 9: Update Captions For Lifecycle-Aware Episodes

**Files:**
- Modify: `memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent/src/main/java/com/openmemind/ai/memory/plugin/rawdata/agent/caption/AgentCaptionGenerator.java`
- Modify: `memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent/src/test/java/com/openmemind/ai/memory/plugin/rawdata/agent/caption/AgentCaptionGeneratorTest.java`

- [ ] **Step 1: Add failing caption tests**

In `AgentCaptionGeneratorTest.java`, add tests for:

- An episode with `goal = "Fix payment tests"`, `SUBAGENT_STOP`, file edit, failed test, passed test, and stop.
- An episode ending with `COMPACT_BOUNDARY`.

Assert captions include:

```java
assertThat(caption.text()).contains("Fix payment tests");
assertThat(caption.text()).contains("src/payment/calc.ts");
assertThat(caption.text()).contains("npm test payment");
```

For compact boundary:

```java
assertThat(caption.text()).contains("compact");
```

- [ ] **Step 2: Update caption logic**

Keep the caption deterministic and concise:

- Prefer goal/user prompt.
- Include top files and commands.
- Include outcome.
- Mention compact/session boundary only when it is the terminal reason and useful for later continuation.
- Do not include raw tool output unless it is already present as a concise failure signal.

- [ ] **Step 3: Run tests**

Run:

```bash
mvn -pl memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent test \
  -Dtest=AgentCaptionGeneratorTest
```

Expected: selected tests pass.

- [ ] **Step 4: Commit**

```bash
git add \
  memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent/src/main/java/com/openmemind/ai/memory/plugin/rawdata/agent/caption/AgentCaptionGenerator.java \
  memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent/src/test/java/com/openmemind/ai/memory/plugin/rawdata/agent/caption/AgentCaptionGeneratorTest.java
git commit -m "feat: improve lifecycle-aware agent captions"
```

### Task 10: Update Documentation For Both Integrations

**Files:**
- Modify: `memind-integrations/claude-code/README.md`
- Modify: `memind-integrations/codex/README.md`
- Modify: `docs/superpowers/specs/2026-05-24-rawdata-agent-design.md` only if the implementation has made the existing spec materially stale.

- [ ] **Step 1: Update Claude Code README**

Document:

- Timeline-only ingestion.
- Project-scoped `agentId` is always enabled.
- `agentIdMode` no longer exists.
- Session/turn/timeline IDs are metadata, not Memind core project/session entities.
- Supported hooks: `SessionStart`, `UserPromptSubmit`, `PreToolUse`, `PostToolUse`, `Notification`, `SubagentStop`, `PreCompact`, `Stop`, `SessionEnd`.
- Failed extraction is spooled and retried on `SessionStart`.
- Retrieval Context Compiler is not part of this phase.

- [ ] **Step 2: Update Codex README**

Document:

- Timeline-only ingestion.
- Project-scoped `agentId` is always enabled.
- `agentIdMode` no longer exists.
- Supported hooks: `SessionStart`, `UserPromptSubmit`, `PreToolUse`, `PostToolUse`, `Stop`.
- Codex does not simulate Claude Code-only lifecycle hooks.
- Failed extraction is spooled and retried on `SessionStart`.
- Retrieval Context Compiler is not part of this phase.

- [ ] **Step 3: Remove stale config examples**

In both READMEs, remove examples like:

```json
"agentIdMode": "project"
```

and remove environment examples:

```bash
export MEMIND_AGENT_ID_MODE=project
```

- [ ] **Step 4: Run doc consistency search**

Run:

```bash
rg -n "agentIdMode|MEMIND_AGENT_ID_MODE|conversation rawdata|TaskCompleted" \
  memind-integrations/claude-code \
  memind-integrations/codex \
  docs/superpowers/specs/2026-05-24-rawdata-agent-design.md
```

Expected:

- No `agentIdMode` or `MEMIND_AGENT_ID_MODE` remains in Claude Code/Codex integration docs or settings.
- Any `TaskCompleted` reference is either in the rawdata-agent general schema or removed from Claude Code/Codex hook documentation. Do not remove `TASK_COMPLETED` from the Java rawdata-agent model solely because Claude Code and Codex do not register a `TaskCompleted` hook in this phase.
- Conversation rawdata is not described as default Claude Code/Codex ingestion.

- [ ] **Step 5: Commit**

```bash
git add \
  memind-integrations/claude-code/README.md \
  memind-integrations/codex/README.md \
  docs/superpowers/specs/2026-05-24-rawdata-agent-design.md
git commit -m "docs: clarify coding agent timeline ingestion"
```

If the spec file does not need changes, omit it from `git add`.

### Task 11: Full Verification

**Files:**
- No planned source edits.

- [ ] **Step 1: Run Claude Code integration tests**

```bash
python3 -m unittest discover -s memind-integrations/claude-code/tests -p "test_*.py"
```

Expected: all tests pass.

- [ ] **Step 2: Run Codex integration tests**

```bash
python3 -m unittest discover -s memind-integrations/codex/tests -p "test_*.py"
```

Expected: all tests pass.

- [ ] **Step 3: Run rawdata-agent plugin tests**

```bash
mvn -pl memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent test
```

Expected: all tests pass.

- [ ] **Step 4: Run broader affected Maven tests**

If the rawdata-agent plugin touched shared core APIs, run:

```bash
mvn -pl memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent -am test
```

Expected: all tests pass.

- [ ] **Step 5: Search for removed config and unsupported hook drift**

```bash
rg -n "agentIdMode|MEMIND_AGENT_ID_MODE" memind-integrations/claude-code memind-integrations/codex
```

Expected: no output.

```bash
python3 -m unittest \
  memind-integrations/claude-code/tests/test_manifest.py \
  memind-integrations/codex/tests/test_manifest.py
```

Expected: manifests match the supported hook sets.

- [ ] **Step 6: Inspect final diff**

```bash
git status --short
git diff --stat
git diff --check
```

Expected:

- Only planned files changed.
- No whitespace errors from `git diff --check`.

- [ ] **Step 7: Final commit if verification required fixes**

If verification required small fixes:

```bash
git add docs/superpowers/plans/2026-05-26-agent-hook-journal-pipeline.md
git commit -m "test: verify agent hook journal pipeline"
```

Replace the `git add` path with the actual source or test files fixed during verification. If no fixes were needed, do not create an empty commit.

## Acceptance Criteria

- Claude Code and Codex no longer expose `agentIdMode`; both always use project-scoped `agentId`.
- Claude Code captures `Notification` and `SubagentStop` as durable timeline events.
- Codex remains explicit about its supported hook set and does not simulate unsupported Claude Code lifecycle events.
- `USER_PROMPT`, tools, assistant message, stop, compact, session end, notification, and subagent evidence can be represented as normalized `agent_event` records.
- Local journal truncation is explicit and does not silently hide data loss.
- Stop/compact/session boundaries flush `agent_timeline` rawdata without reintroducing transcript conversation ingestion.
- `rawdata-agent` can parse, format, segment, caption, and extract from richer event kinds.
- Deterministic resolution extraction uses precise later-success validation semantics.
- LLM-extracted agent memories cannot cite evidence event IDs outside the episode.
- All changed behavior is covered by Python and Java tests.

## Notes For Implementation

- Keep the adapter layer boring and deterministic. It should clean and normalize evidence, not decide high-level memory meaning.
- Do not add project/session tables or core concepts to Memind.
- Do not add a pre-extraction LLM gate in this phase.
- Do not remove `rawdata-toolcall`; it remains a separate plugin with a different scope.
- Keep generated hook scripts fail-open. Memory capture must not block the user's coding session if Memind is unavailable.
- Prefer small commits after each task so review can isolate identity, hook capture, journal behavior, rawdata-agent parsing, and docs.
