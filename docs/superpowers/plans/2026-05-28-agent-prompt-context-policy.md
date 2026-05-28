# Agent Prompt Context Policy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make prompt-time memory injection opt-in by default, and when enabled, retrieve with project-first ranking plus global fallback instead of broad unfiltered recall.

**Architecture:** Keep Memind core, OpenAPI, rawdata-agent storage, SessionStart context, PreToolUse context, and Stop-time extraction unchanged. Add an integration-local `autoPromptContext` switch for `UserPromptSubmit`; `UserPromptSubmit` always buffers the prompt event, but only retrieves and injects `<memind_memories>` when this switch is enabled. The enabled path performs a project-filtered retrieve first, then a bounded unfiltered fallback for reusable cross-project/global memories, dedupes and labels source provenance before rendering.

**Tech Stack:** Python hook integrations for Claude Code and Codex, Memind Python client, existing retrieve metadata filters, unittest, JSON hook settings, Markdown integration docs.

---

## Scope And Non-Goals

In scope:

- Add `autoPromptContext=false` to Claude Code and Codex default settings.
- Add `MEMIND_AUTO_PROMPT_CONTEXT` env override.
- Keep `autoRetrieve` as a backward-compatible broad retrieval gate for now, but stop using it as the only prompt-injection switch.
- Ensure `UserPromptSubmit` still appends a normalized `user_prompt` event even when prompt context is disabled.
- Add project-first prompt retrieval using current `metadata.projectSlug`.
- Add bounded global fallback using the same `userId + agentId` memory space without forcing `projectSlug == currentProject`.
- Merge project and fallback results with stable dedupe and project-first precedence.
- Add source labels to rendered prompt memories: `project`, `global`, or `shared`.
- Update Claude Code and Codex docs so users understand prompt-time context is opt-in and separate from SessionStart/PreToolUse.

Out of scope:

- No Memind core changes.
- No OpenAPI schema changes.
- No new server-side metadata operators.
- No new `contextLevel` or context-scope field on items.
- No per-prompt LLM gate.
- No per-tool LLM extraction.
- No change to rawdata-agent item extraction.
- No removal of `autoRetrieve` in this plan; it remains a compatibility gate because existing PreToolUse code still checks it.

## Current Behavior

Claude Code and Codex both currently do this in `scripts/retrieve.py`:

1. Read `UserPromptSubmit` hook JSON.
2. Append a `user_prompt` event to local durable state.
3. If `autoRetrieve` is false, return `{"continue": true}`.
4. Build a query from the prompt plus optional recent transcript turns.
5. Call `client.retrieve(userId, agentId, query, strategy, false)` without project metadata filtering.
6. Render `<memind_memories>` and inject it into the next model turn.

This makes prompt-time injection default-on and broad across the whole shared `userId + agentId` memory space. That is useful when precise, but it can spend tokens every turn and can surface memories from unrelated projects.

## Target Behavior

Claude Code and Codex `UserPromptSubmit` should behave like this:

```text
UserPromptSubmit
  -> append USER_PROMPT event to local timeline state
  -> if autoRetrieve=false: return continue
  -> if autoPromptContext=false: return continue
  -> build prompt query
  -> resolve projectSlug from cwd
  -> retrieve current-project memories first
  -> if project hits are sparse, retrieve unfiltered fallback memories
  -> dedupe project + fallback results
  -> render <memind_memories project="..." mode="project-first">
  -> inject additionalContext
```

Default install behavior:

```json
{
  "autoPromptContext": false,
  "autoSessionContext": true,
  "autoToolContext": true,
  "autoIngestAgentTimeline": true
}
```

Enabled prompt context behavior:

- Project retrieve uses `metadataFilter.all = [{ "path": "projectSlug", "op": "eq", "value": currentProjectSlug }]`.
- Global fallback uses no project filter, but only fills remaining budget.
- Current-project results always beat fallback duplicates.
- Fallback entries are retained only when they are sufficiently relevant or clearly reusable.
- Rendered context explicitly marks provenance so the agent can treat old cross-project information cautiously.

## File Map

Claude Code:

- Modify `memind-integrations/claude-code/settings.json`
  - Add prompt context settings with default disabled.

- Modify `memind-integrations/claude-code/scripts/lib/config.py`
  - Add defaults and env overrides.

- Create `memind-integrations/claude-code/scripts/lib/prompt_context.py`
  - Build project-first and fallback retrieve calls.
  - Convert Memind response objects to dictionaries.
  - Mark provenance and dedupe.

- Modify `memind-integrations/claude-code/scripts/retrieve.py`
  - Keep buffering behavior.
  - Check `autoPromptContext`.
  - Use `build_prompt_context(...)`.

- Modify `memind-integrations/claude-code/scripts/lib/context_compiler.py`
  - Render source labels and wrapper attrs for prompt context.

- Modify tests:
  - `memind-integrations/claude-code/tests/test_config.py`
  - `memind-integrations/claude-code/tests/test_manifest.py`
  - `memind-integrations/claude-code/tests/test_context_compiler.py`
  - `memind-integrations/claude-code/tests/test_hooks.py`
  - Create `memind-integrations/claude-code/tests/test_prompt_context.py`

- Modify docs:
  - `memind-integrations/claude-code/README.md`

Codex:

- Modify `memind-integrations/codex/settings.json`
- Modify `memind-integrations/codex/scripts/lib/config.py`
- Create `memind-integrations/codex/scripts/lib/prompt_context.py`
- Modify `memind-integrations/codex/scripts/retrieve.py`
- Modify `memind-integrations/codex/scripts/lib/context_compiler.py`
- Modify tests:
  - `memind-integrations/codex/tests/test_config.py`
  - `memind-integrations/codex/tests/test_manifest.py`
  - `memind-integrations/codex/tests/test_context_compiler.py`
  - `memind-integrations/codex/tests/test_hooks.py`
  - Create `memind-integrations/codex/tests/test_prompt_context.py`
- Modify docs:
  - `memind-integrations/codex/README.md`

## Config Contract

Add these settings to both integrations:

```json
{
  "autoPromptContext": false,
  "promptContextProjectMinEntries": 4,
  "promptContextGlobalFallbackEntries": 3,
  "promptContextGlobalFallbackMinScore": 0.65
}
```

Add these env vars to both `ENV_MAP` dictionaries:

```python
"MEMIND_AUTO_PROMPT_CONTEXT": ("autoPromptContext", "bool"),
"MEMIND_PROMPT_CONTEXT_PROJECT_MIN_ENTRIES": ("promptContextProjectMinEntries", "int_allow_zero"),
"MEMIND_PROMPT_CONTEXT_GLOBAL_FALLBACK_ENTRIES": ("promptContextGlobalFallbackEntries", "int_allow_zero"),
"MEMIND_PROMPT_CONTEXT_GLOBAL_FALLBACK_MIN_SCORE": ("promptContextGlobalFallbackMinScore", "float_allow_zero"),
```

Add float parsing support:

```python
def parse_float(value, name, allow_zero=False):
    parsed = float(value)
    if parsed < 0 or (parsed == 0 and not allow_zero):
        raise ValueError(f"{name} must be positive")
    return parsed
```

Extend `_coerce(...)`:

```python
if kind == "float_allow_zero":
    return parse_float(value, name, allow_zero=True)
```

`autoRetrieve` remains supported:

- `autoRetrieve=false` disables prompt retrieve and tool retrieve fallback, as today.
- `autoPromptContext=false` disables only UserPromptSubmit context injection.
- `autoToolContext=false` disables only PreToolUse context injection.
- `autoSessionContext=false` disables only SessionStart context injection.

## Prompt Context Helper Contract

Create `scripts/lib/prompt_context.py` in both integrations.

Public function:

```python
def build_prompt_context(client, identity, query, project_slug, config):
    ...
```

Input:

- `client`: existing local `MemindClient`.
- `identity`: `{"userId": "...", "agentId": "..."}`.
- `query`: prompt plus optional recent context.
- `project_slug`: result of `lib.identity.project_slug(cwd)`.
- `config`: loaded integration config.

Output:

Dictionary suitable for `compile_prompt_retrieval_context(...)`:

```python
{
    "projectSlug": project_slug,
    "mode": "project-first",
    "items": [...],
    "insights": [...],
    "rawData": [...],
}
```

Each item/insight should carry a local provenance field:

```python
{
    "id": "it-1",
    "text": "Use metadata.projectSlug for project isolation.",
    "category": "directive",
    "metadata": {"projectSlug": "memind-a1b2c3"},
    "finalScore": 0.91,
    "memindContextSource": "project"
}
```

Allowed `memindContextSource` values:

- `project`: `metadata.projectSlug == currentProjectSlug`
- `global`: no `metadata.projectSlug`
- `shared`: `metadata.projectSlug` exists and differs from current project

Fallback retrieve should not be called when project retrieve already returns enough usable entries.

Implementation detail:

- Always call `_dump_response()` first and then `_mark_sources(...)` on dumped dictionaries.
- Do not attach `memindContextSource` to Python client Pydantic model instances before dump; `MemindModel` ignores extra fields, so the provenance label can be lost during `model_dump()`.
- Pass `default_source="project"` when marking the project-filtered retrieve response. Current `RetrievedInsight` responses do not carry metadata, so project-pass insights must inherit provenance from the retrieve pass rather than from entry metadata.

## Dedupe And Ranking Rules

Use a stable key:

```python
def _entry_key(entry):
    entry_id = _field(entry, "id")
    if entry_id:
        return f"id:{entry_id}"
    text = " ".join(str(_field(entry, "text") or "").lower().split())
    return f"text:{text[:260]}"
```

Merge order:

1. Project items.
2. Project insights.
3. Fallback items.
4. Fallback insights.

When duplicate keys exist, keep the first entry. This guarantees project entries win over fallback duplicates.

Fallback score filter:

- Always drop fallback entries whose source is `project`; they are duplicates or project memories already covered by pass one.
- If a fallback entry has `finalScore`, `final_score`, `vectorScore`, `vector_score`, or `score`, keep it only when the score is at least `promptContextGlobalFallbackMinScore`.
- If a fallback insight has no score field, keep it and rely on Memind's returned order plus `promptContextGlobalFallbackEntries`.
- Do not treat missing insight scores as `0`; the Python client `RetrievedInsight` model does not currently expose score fields, so score-gating missing-score insights would drop valid fallback insights.
- Items should normally have scores, but the implementation should use the same score-present check so model/client shape differences do not accidentally remove all fallback data.

Fallback size:

- `promptContextGlobalFallbackEntries` applies separately to items and insights after filtering and dedupe.
- Default `3` keeps fallback compact.

Project hit count:

- Count usable project `items + insights`.
- If count >= `promptContextProjectMinEntries`, skip fallback.
- Default `4`.

## Rendered Context Contract

Update `compile_prompt_retrieval_context(...)` to read these optional fields:

- `projectSlug`
- `mode`
- `memindContextSource`

Wrapper:

```xml
<memind_memories project="memind-main-abc123" mode="project-first">
```

Entry labels:

```text
- [item:dir-1 directive, project, 2026-05-27] Keep userId and agentId stable.
- [item:profile-1 behavior, global, 2026-05-20] User prefers Chinese replies.
- [item:tool-9 tool, shared, 2026-05-18] After hook changes, run both Claude Code and Codex tests.
```

Use `shared` instead of another project's slug in the label to avoid leaking unrelated local repo names into the prompt context. The actual `metadata.projectSlug` stays in memory metadata, but not in the injected text.

Preamble should remain cautious:

```text
Relevant memories from Memind. Use only when directly helpful:
```

No new in-app explanatory text should be inserted beyond the existing preamble and labels.

---

## Task 1: Add Prompt Context Config Defaults

**Files:**

- Modify `memind-integrations/claude-code/settings.json`
- Modify `memind-integrations/codex/settings.json`
- Modify `memind-integrations/claude-code/scripts/lib/config.py`
- Modify `memind-integrations/codex/scripts/lib/config.py`
- Test `memind-integrations/claude-code/tests/test_config.py`
- Test `memind-integrations/codex/tests/test_config.py`
- Test `memind-integrations/claude-code/tests/test_manifest.py`
- Test `memind-integrations/codex/tests/test_manifest.py`

- [ ] **Step 1: Write failing Claude Code config tests**

Add assertions to `memind-integrations/claude-code/tests/test_config.py`:

```python
def test_prompt_context_defaults_are_opt_in(self):
    from scripts.lib.config import DEFAULT_SETTINGS

    self.assertFalse(DEFAULT_SETTINGS["autoPromptContext"])
    self.assertEqual(DEFAULT_SETTINGS["promptContextProjectMinEntries"], 4)
    self.assertEqual(DEFAULT_SETTINGS["promptContextGlobalFallbackEntries"], 3)
    self.assertEqual(DEFAULT_SETTINGS["promptContextGlobalFallbackMinScore"], 0.65)


def test_prompt_context_env_overrides(self):
    from scripts.lib.config import load_config

    config = load_config(
        plugin_root=ROOT,
        user_config_path=Path("/no/such/file"),
        env={
            "CLAUDE_PLUGIN_ROOT": str(ROOT),
            "MEMIND_AUTO_PROMPT_CONTEXT": "true",
            "MEMIND_PROMPT_CONTEXT_PROJECT_MIN_ENTRIES": "2",
            "MEMIND_PROMPT_CONTEXT_GLOBAL_FALLBACK_ENTRIES": "1",
            "MEMIND_PROMPT_CONTEXT_GLOBAL_FALLBACK_MIN_SCORE": "0.5",
        },
    )

    self.assertTrue(config["autoPromptContext"])
    self.assertEqual(config["promptContextProjectMinEntries"], 2)
    self.assertEqual(config["promptContextGlobalFallbackEntries"], 1)
    self.assertEqual(config["promptContextGlobalFallbackMinScore"], 0.5)
```

- [ ] **Step 2: Write failing Codex config tests**

Add equivalent assertions to `memind-integrations/codex/tests/test_config.py`, using `CODEX_PLUGIN_ROOT` where the existing tests use it:

```python
def test_prompt_context_defaults_are_opt_in(self):
    from scripts.lib.config import DEFAULT_SETTINGS

    self.assertFalse(DEFAULT_SETTINGS["autoPromptContext"])
    self.assertEqual(DEFAULT_SETTINGS["promptContextProjectMinEntries"], 4)
    self.assertEqual(DEFAULT_SETTINGS["promptContextGlobalFallbackEntries"], 3)
    self.assertEqual(DEFAULT_SETTINGS["promptContextGlobalFallbackMinScore"], 0.65)


def test_prompt_context_env_overrides(self):
    from scripts.lib.config import load_config

    root = Path(__file__).resolve().parents[1]
    config = load_config(
        plugin_root=root,
        user_config_path=Path("/no/such/file"),
        env={
            "CODEX_PLUGIN_ROOT": str(root),
            "MEMIND_AUTO_PROMPT_CONTEXT": "true",
            "MEMIND_PROMPT_CONTEXT_PROJECT_MIN_ENTRIES": "2",
            "MEMIND_PROMPT_CONTEXT_GLOBAL_FALLBACK_ENTRIES": "1",
            "MEMIND_PROMPT_CONTEXT_GLOBAL_FALLBACK_MIN_SCORE": "0.5",
        },
    )

    self.assertTrue(config["autoPromptContext"])
    self.assertEqual(config["promptContextProjectMinEntries"], 2)
    self.assertEqual(config["promptContextGlobalFallbackEntries"], 1)
    self.assertEqual(config["promptContextGlobalFallbackMinScore"], 0.5)
```

- [ ] **Step 3: Run config tests and verify they fail**

Run:

```bash
python3 -m unittest \
  memind-integrations/claude-code/tests/test_config.py \
  memind-integrations/codex/tests/test_config.py
```

Expected: failures because `autoPromptContext` and float parsing do not exist yet.

- [ ] **Step 4: Implement config defaults**

In both `scripts/lib/config.py`, add to `DEFAULT_SETTINGS` after `autoRetrieve`:

```python
"autoPromptContext": False,
```

Add after `retrieveContextTurns` or near prompt retrieval options:

```python
"promptContextProjectMinEntries": 4,
"promptContextGlobalFallbackEntries": 3,
"promptContextGlobalFallbackMinScore": 0.65,
```

Add to `ENV_MAP` after `MEMIND_AUTO_RETRIEVE`:

```python
"MEMIND_AUTO_PROMPT_CONTEXT": ("autoPromptContext", "bool"),
```

Add near retrieval env vars:

```python
"MEMIND_PROMPT_CONTEXT_PROJECT_MIN_ENTRIES": ("promptContextProjectMinEntries", "int_allow_zero"),
"MEMIND_PROMPT_CONTEXT_GLOBAL_FALLBACK_ENTRIES": ("promptContextGlobalFallbackEntries", "int_allow_zero"),
"MEMIND_PROMPT_CONTEXT_GLOBAL_FALLBACK_MIN_SCORE": ("promptContextGlobalFallbackMinScore", "float_allow_zero"),
```

Add float parsing:

```python
def parse_float(value, name, allow_zero=False):
    parsed = float(value)
    if parsed < 0 or (parsed == 0 and not allow_zero):
        raise ValueError(f"{name} must be positive")
    return parsed
```

Add to `_coerce(...)`:

```python
if kind == "float_allow_zero":
    return parse_float(value, name, allow_zero=True)
```

- [ ] **Step 5: Update default settings JSON**

Add these keys to both `settings.json` files:

```json
"autoPromptContext": false,
"promptContextProjectMinEntries": 4,
"promptContextGlobalFallbackEntries": 3,
"promptContextGlobalFallbackMinScore": 0.65,
```

Keep `autoRetrieve` unchanged in this task.

- [ ] **Step 6: Update manifest/default settings tests**

In both manifest tests, assert that bundled settings include:

```python
self.assertFalse(settings["autoPromptContext"])
self.assertEqual(settings["promptContextProjectMinEntries"], 4)
self.assertEqual(settings["promptContextGlobalFallbackEntries"], 3)
self.assertEqual(settings["promptContextGlobalFallbackMinScore"], 0.65)
```

- [ ] **Step 7: Run config and manifest tests**

Run:

```bash
python3 -m unittest \
  memind-integrations/claude-code/tests/test_config.py \
  memind-integrations/claude-code/tests/test_manifest.py \
  memind-integrations/codex/tests/test_config.py \
  memind-integrations/codex/tests/test_manifest.py
```

Expected: all pass.

- [ ] **Step 8: Commit**

```bash
git add \
  memind-integrations/claude-code/settings.json \
  memind-integrations/claude-code/scripts/lib/config.py \
  memind-integrations/claude-code/tests/test_config.py \
  memind-integrations/claude-code/tests/test_manifest.py \
  memind-integrations/codex/settings.json \
  memind-integrations/codex/scripts/lib/config.py \
  memind-integrations/codex/tests/test_config.py \
  memind-integrations/codex/tests/test_manifest.py
git commit -m "feat(agent): make prompt context opt-in"
```

## Task 2: Add Project-First Prompt Retrieval Helper

**Files:**

- Create `memind-integrations/claude-code/scripts/lib/prompt_context.py`
- Create `memind-integrations/codex/scripts/lib/prompt_context.py`
- Test `memind-integrations/claude-code/tests/test_prompt_context.py`
- Test `memind-integrations/codex/tests/test_prompt_context.py`

- [ ] **Step 1: Write failing Claude Code prompt context tests**

Create `memind-integrations/claude-code/tests/test_prompt_context.py`:

```python
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
sys.path.insert(0, str(ROOT / "scripts"))


class PromptContextTests(unittest.TestCase):
    def test_project_hits_skip_global_fallback(self):
        from scripts.lib.prompt_context import build_prompt_context

        class FakeResponse:
            def __init__(self, items=None, insights=None):
                self.items = items or []
                self.insights = insights or []
                self.raw_data = []

            def model_dump(self, by_alias=True):
                return {
                    "items": self.items,
                    "insights": self.insights,
                    "rawData": self.raw_data,
                }

        class FakeClient:
            def __init__(self):
                self.calls = []

            def retrieve(self, *args, **kwargs):
                self.calls.append(kwargs)
                return FakeResponse(
                    items=[
                        {"id": "p1", "text": "Project directive", "category": "directive", "metadata": {"projectSlug": "memind-main"}, "finalScore": 0.9},
                        {"id": "p2", "text": "Project resolution", "category": "resolution", "metadata": {"projectSlug": "memind-main"}, "finalScore": 0.8},
                    ],
                    insights=[
                        {"id": "i1", "text": "Project insight", "tier": "root"},
                        {"id": "i2", "text": "Project branch", "tier": "branch"},
                    ],
                )

        client = FakeClient()
        result = build_prompt_context(
            client,
            {"userId": "u", "agentId": "a"},
            "fix retrieval",
            "memind-main",
            {
                "retrieveStrategy": "SIMPLE",
                "promptContextProjectMinEntries": 4,
                "promptContextGlobalFallbackEntries": 3,
                "promptContextGlobalFallbackMinScore": 0.65,
            },
        )

        self.assertEqual(len(client.calls), 1)
        self.assertEqual(client.calls[0]["metadata_filter"]["all"][0]["path"], "projectSlug")
        self.assertEqual(client.calls[0]["metadata_filter"]["all"][0]["value"], "memind-main")
        self.assertEqual(result["mode"], "project-first")
        self.assertEqual(result["projectSlug"], "memind-main")
        self.assertTrue(all(item["memindContextSource"] == "project" for item in result["items"]))
        self.assertTrue(all(insight["memindContextSource"] == "project" for insight in result["insights"]))

    def test_sparse_project_hits_use_bounded_global_fallback(self):
        from scripts.lib.prompt_context import build_prompt_context

        class FakeResponse:
            def __init__(self, items=None, insights=None):
                self.items = items or []
                self.insights = insights or []
                self.raw_data = []

            def model_dump(self, by_alias=True):
                return {
                    "items": self.items,
                    "insights": self.insights,
                    "rawData": self.raw_data,
                }

        class FakeClient:
            def __init__(self):
                self.calls = []

            def retrieve(self, *args, **kwargs):
                self.calls.append(kwargs)
                if len(self.calls) == 1:
                    return FakeResponse(
                        items=[
                            {"id": "same", "text": "Project directive", "category": "directive", "metadata": {"projectSlug": "memind-main"}, "finalScore": 0.9}
                        ],
                    )
                return FakeResponse(
                    items=[
                        {"id": "same", "text": "Duplicate project directive", "category": "directive", "metadata": {"projectSlug": "memind-main"}, "finalScore": 0.95},
                        {"id": "g1", "text": "User prefers Chinese replies", "category": "behavior", "metadata": {}, "finalScore": 0.91},
                        {"id": "s1", "text": "Run both integration tests after hook edits", "category": "tool", "metadata": {"projectSlug": "other-project"}, "finalScore": 0.8},
                        {"id": "low", "text": "Weak unrelated memory", "category": "event", "metadata": {}, "finalScore": 0.2},
                    ],
                    insights=[
                        {"id": "gi1", "text": "Shared testing insight", "tier": "root", "metadata": {}}
                    ],
                )

        client = FakeClient()
        result = build_prompt_context(
            client,
            {"userId": "u", "agentId": "a"},
            "fix retrieval",
            "memind-main",
            {
                "retrieveStrategy": "SIMPLE",
                "promptContextProjectMinEntries": 4,
                "promptContextGlobalFallbackEntries": 3,
                "promptContextGlobalFallbackMinScore": 0.65,
            },
        )

        self.assertEqual(len(client.calls), 2)
        self.assertIsNone(client.calls[1].get("metadata_filter"))
        item_ids = [item["id"] for item in result["items"]]
        self.assertEqual(item_ids, ["same", "g1", "s1"])
        sources = {item["id"]: item["memindContextSource"] for item in result["items"]}
        self.assertEqual(sources["same"], "project")
        self.assertEqual(sources["g1"], "global")
        self.assertEqual(sources["s1"], "shared")
        self.assertNotIn("low", item_ids)
        self.assertEqual(result["insights"][0]["memindContextSource"], "global")
```

- [ ] **Step 2: Write equivalent Codex prompt context tests**

Copy the same test file to `memind-integrations/codex/tests/test_prompt_context.py`, changing only `ROOT` resolution if the existing Codex tests use a different convention. The assertions should remain identical.

- [ ] **Step 3: Run new tests and verify they fail**

Run:

```bash
python3 -m unittest \
  memind-integrations/claude-code/tests/test_prompt_context.py \
  memind-integrations/codex/tests/test_prompt_context.py
```

Expected: import failure because `scripts.lib.prompt_context` does not exist.

- [ ] **Step 4: Implement `prompt_context.py` in Claude Code**

Create `memind-integrations/claude-code/scripts/lib/prompt_context.py`:

```python
def project_metadata_filter(project_slug):
    return {"all": [{"path": "projectSlug", "op": "eq", "value": project_slug}]}


def build_prompt_context(client, identity, query, project_slug, config):
    project_data = _retrieve(
        client,
        identity,
        query,
        config,
        metadata_filter=project_metadata_filter(project_slug) if project_slug else None,
    )
    _mark_sources(project_data, project_slug, default_source="project")

    project_count = len(project_data.get("items") or []) + len(project_data.get("insights") or [])
    min_entries = int(config.get("promptContextProjectMinEntries", 4))
    fallback_limit = int(config.get("promptContextGlobalFallbackEntries", 3))

    if project_count >= min_entries or fallback_limit <= 0:
        return _shape(project_data, project_slug)

    fallback_data = _retrieve(client, identity, query, config, metadata_filter=None)
    _mark_sources(fallback_data, project_slug)
    fallback_data = _filter_fallback(fallback_data, config, fallback_limit)

    return _shape(_merge(project_data, fallback_data), project_slug)


def _retrieve(client, identity, query, config, metadata_filter=None):
    response = client.retrieve(
        identity["userId"],
        identity["agentId"],
        query,
        config.get("retrieveStrategy", "SIMPLE"),
        False,
        metadata_filter=metadata_filter,
        include={"raw_data_metadata": True},
    )
    return _dump_response(response)


def _dump_response(response):
    if hasattr(response, "model_dump"):
        data = response.model_dump(by_alias=True)
    else:
        data = {
            "items": list(getattr(response, "items", []) or []),
            "insights": list(getattr(response, "insights", []) or []),
            "rawData": list(getattr(response, "raw_data", []) or getattr(response, "rawData", []) or []),
        }

    return {
        "items": [_dump_entry(entry) for entry in data.get("items", []) or []],
        "insights": [_dump_entry(entry) for entry in data.get("insights", []) or []],
        "rawData": [_dump_entry(entry) for entry in data.get("rawData", []) or data.get("raw_data", []) or []],
    }


def _dump_entry(entry):
    if isinstance(entry, dict):
        return dict(entry)
    if hasattr(entry, "model_dump"):
        return entry.model_dump(by_alias=True)
    return {
        key: value
        for key, value in vars(entry).items()
        if not key.startswith("_")
    }


def _shape(data, project_slug):
    return {
        "projectSlug": project_slug,
        "mode": "project-first",
        "items": data.get("items") or [],
        "insights": data.get("insights") or [],
        "rawData": data.get("rawData") or data.get("raw_data") or [],
    }


def _merge(project_data, fallback_data):
    return {
        "items": _dedupe((project_data.get("items") or []) + (fallback_data.get("items") or [])),
        "insights": _dedupe((project_data.get("insights") or []) + (fallback_data.get("insights") or [])),
        "rawData": _dedupe((project_data.get("rawData") or []) + (fallback_data.get("rawData") or [])),
    }


def _filter_fallback(data, config, limit):
    min_score = float(config.get("promptContextGlobalFallbackMinScore", 0.65))
    return {
        "items": [
            entry
            for entry in data.get("items", [])
            if entry.get("memindContextSource") != "project" and _passes_fallback_score(entry, min_score)
        ][:limit],
        "insights": [
            entry
            for entry in data.get("insights", [])
            if entry.get("memindContextSource") != "project" and _passes_fallback_score(entry, min_score)
        ][:limit],
        "rawData": [],
    }


def _mark_sources(data, project_slug, default_source=None):
    # Mark only dumped dictionaries, not Pydantic model objects. MemindModel uses
    # extra="ignore", so annotating model instances before model_dump can lose
    # memindContextSource.
    for key in ("items", "insights", "rawData", "raw_data"):
        for entry in data.get(key) or []:
            entry["memindContextSource"] = _source_for(entry, project_slug, default_source)


def _source_for(entry, project_slug, default_source=None):
    metadata = _field(entry, "metadata") or {}
    entry_project = metadata.get("projectSlug") if isinstance(metadata, dict) else None
    if entry_project and project_slug and entry_project == project_slug:
        return "project"
    if entry_project:
        return "shared"
    if default_source:
        return default_source
    return "global"


def _dedupe(entries):
    result = []
    seen = set()
    for entry in entries:
        key = _entry_key(entry)
        if not key or key in seen:
            continue
        seen.add(key)
        result.append(entry)
    return result


def _entry_key(entry):
    entry_id = _field(entry, "id") or _field(entry, "rawDataId") or _field(entry, "raw_data_id")
    if entry_id:
        return f"id:{entry_id}"
    text = " ".join(str(_field(entry, "text") or _field(entry, "caption") or "").lower().split())
    return f"text:{text[:260]}" if text else ""


def _passes_fallback_score(entry, min_score):
    score = _score(entry)
    if score is None:
        return True
    return score >= min_score


def _score(entry):
    for key in ("finalScore", "final_score", "vectorScore", "vector_score", "score"):
        value = _field(entry, key)
        if value is None:
            continue
        try:
            return float(value)
        except (TypeError, ValueError):
            continue
    return None


def _field(value, name):
    if isinstance(value, dict):
        return value.get(name)
    return getattr(value, name, None)


```

- [ ] **Step 5: Copy helper to Codex**

Copy the same implementation into `memind-integrations/codex/scripts/lib/prompt_context.py`.

- [ ] **Step 6: Run prompt context tests**

Run:

```bash
python3 -m unittest \
  memind-integrations/claude-code/tests/test_prompt_context.py \
  memind-integrations/codex/tests/test_prompt_context.py
```

Expected: pass.

- [ ] **Step 7: Commit**

```bash
git add \
  memind-integrations/claude-code/scripts/lib/prompt_context.py \
  memind-integrations/claude-code/tests/test_prompt_context.py \
  memind-integrations/codex/scripts/lib/prompt_context.py \
  memind-integrations/codex/tests/test_prompt_context.py
git commit -m "feat(agent): retrieve prompt context project first"
```

## Task 3: Render Prompt Context Provenance

**Files:**

- Modify `memind-integrations/claude-code/scripts/lib/context_compiler.py`
- Modify `memind-integrations/codex/scripts/lib/context_compiler.py`
- Test `memind-integrations/claude-code/tests/test_context_compiler.py`
- Test `memind-integrations/codex/tests/test_context_compiler.py`

- [ ] **Step 1: Write failing compiler tests**

Add to both `test_context_compiler.py` files:

```python
def test_prompt_context_renders_project_first_attrs_and_source_labels(self):
    from scripts.lib.context_compiler import compile_prompt_retrieval_context

    rendered = compile_prompt_retrieval_context(
        {
            "projectSlug": "memind-main",
            "mode": "project-first",
            "items": [
                {
                    "id": "dir-1",
                    "text": "Keep userId and agentId stable.",
                    "category": "directive",
                    "createdAt": "2026-05-27T10:00:00Z",
                    "finalScore": 0.9,
                    "memindContextSource": "project",
                },
                {
                    "id": "beh-1",
                    "text": "User prefers Chinese replies.",
                    "category": "behavior",
                    "createdAt": "2026-05-20T10:00:00Z",
                    "finalScore": 0.88,
                    "memindContextSource": "global",
                },
            ],
            "insights": [
                {
                    "id": "ins-1",
                    "text": "Run both Claude Code and Codex tests after hook edits.",
                    "tier": "root",
                    "createdAt": "2026-05-18T10:00:00Z",
                    "memindContextSource": "shared",
                }
            ],
        },
        {
            "retrieveMaxEntries": 8,
            "retrieveMaxChars": 6000,
            "retrievePromptPreamble": "Relevant memories from Memind.",
        },
    )

    self.assertIn('<memind_memories project="memind-main" mode="project-first">', rendered)
    self.assertIn("[item:dir-1 directive, project, 2026-05-27]", rendered)
    self.assertIn("[item:beh-1 behavior, global, 2026-05-20]", rendered)
    self.assertIn("[insight:ins-1 root, shared, 2026-05-18]", rendered)
```

- [ ] **Step 2: Run compiler tests and verify failure**

Run:

```bash
python3 -m unittest \
  memind-integrations/claude-code/tests/test_context_compiler.py \
  memind-integrations/codex/tests/test_context_compiler.py
```

Expected: failure because wrapper attrs and source labels are not rendered yet.

- [ ] **Step 3: Update normalizers**

In both `context_compiler.py`, add `source` to `_normalize_retrieved_item(...)`:

```python
"source": _field(item, "memindContextSource"),
```

Add `source` to `_normalize_insight(...)`:

```python
"source": _field(insight, "memindContextSource"),
```

No source field is needed for session or tool contexts.

- [ ] **Step 4: Update prompt wrapper attrs**

In `compile_prompt_retrieval_context(...)`, replace:

```python
attrs={},
```

with:

```python
attrs=_prompt_attrs(data),
```

for both normal and notice-only render paths.

Add helper:

```python
def _prompt_attrs(data):
    attrs = {}
    if data.get("projectSlug"):
        attrs["project"] = data["projectSlug"]
    if data.get("mode"):
        attrs["mode"] = data["mode"]
    return attrs
```

- [ ] **Step 5: Update entry label rendering**

In `_render_entry(...)`, after date handling, include source before date:

```python
source = entry.get("source")
label_parts = [label]
if source:
    label_parts.append(source)
if date:
    label_parts.append(date)
label = ", ".join(label_parts)
```

Replace the existing:

```python
if date:
    label = f"{label}, {date}"
```

with the new `label_parts` logic.

- [ ] **Step 6: Run compiler tests**

Run:

```bash
python3 -m unittest \
  memind-integrations/claude-code/tests/test_context_compiler.py \
  memind-integrations/codex/tests/test_context_compiler.py
```

Expected: pass.

- [ ] **Step 7: Commit**

```bash
git add \
  memind-integrations/claude-code/scripts/lib/context_compiler.py \
  memind-integrations/claude-code/tests/test_context_compiler.py \
  memind-integrations/codex/scripts/lib/context_compiler.py \
  memind-integrations/codex/tests/test_context_compiler.py
git commit -m "feat(agent): label prompt memory provenance"
```

## Task 4: Wire Prompt Context Into UserPromptSubmit

**Files:**

- Modify `memind-integrations/claude-code/scripts/retrieve.py`
- Modify `memind-integrations/codex/scripts/retrieve.py`
- Test `memind-integrations/claude-code/tests/test_hooks.py`
- Test `memind-integrations/codex/tests/test_hooks.py`

- [ ] **Step 1: Write failing Claude Code hook tests**

Add to `memind-integrations/claude-code/tests/test_hooks.py`:

```python
def test_retrieve_default_does_not_call_memind_but_buffers_prompt(self):
    sys.path.insert(0, str(ROOT / "scripts"))
    import retrieve

    retrieve = importlib.reload(retrieve)

    config = {
        "sourceClient": "claude-code",
        "autoRetrieve": True,
        "autoPromptContext": False,
        "retrieveContextTurns": 0,
    }

    with tempfile.TemporaryDirectory() as tmp:
        state_dir = Path(tmp) / "state"
        with mock.patch.object(retrieve, "state_root", return_value=state_dir):
            with mock.patch.object(retrieve, "load_config", return_value=config):
                with mock.patch.object(retrieve, "MemindClient") as client_cls:
                    result = retrieve.handle_user_prompt_submit(
                        {
                            "hook_event_name": "UserPromptSubmit",
                            "cwd": tmp,
                            "session_id": "s1",
                            "prompt": "Fix payment tests",
                        }
                    )

    self.assertEqual(result, {"continue": True})
    client_cls.assert_not_called()
    state_file = next(state_dir.glob("*.json"))
    event = json.loads(state_file.read_text())["agentEvents"][0]
    self.assertEqual(event["kind"], "user_prompt")
    self.assertEqual(event["text"], "Fix payment tests")


def test_retrieve_prompt_context_enabled_uses_project_first_context(self):
    sys.path.insert(0, str(ROOT / "scripts"))
    import retrieve

    retrieve = importlib.reload(retrieve)

    config = {
        "sourceClient": "claude-code",
        "memindApiUrl": "http://127.0.0.1:8366",
        "memindApiToken": None,
        "autoRetrieve": True,
        "autoPromptContext": True,
        "retrieveContextTurns": 0,
        "retrieveStrategy": "SIMPLE",
        "retrieveMaxEntries": 8,
        "retrieveMaxChars": 6000,
        "retrievePromptPreamble": "Relevant memories from Memind.",
        "promptContextProjectMinEntries": 4,
        "promptContextGlobalFallbackEntries": 3,
        "promptContextGlobalFallbackMinScore": 0.65,
    }

    class FakeClient:
        pass

    with tempfile.TemporaryDirectory() as tmp:
        state_dir = Path(tmp) / "state"
        with mock.patch.object(retrieve, "state_root", return_value=state_dir):
            with mock.patch.object(retrieve, "load_config", return_value=config):
                with mock.patch.object(retrieve, "resolve_identity", return_value={"userId": "u", "agentId": "a"}):
                    with mock.patch.object(retrieve, "project_slug", return_value="memind-main"):
                        with mock.patch.object(retrieve, "MemindClient", return_value=FakeClient()):
                            with mock.patch.object(
                                retrieve,
                                "build_prompt_context",
                                return_value={
                                    "projectSlug": "memind-main",
                                    "mode": "project-first",
                                    "items": [
                                        {
                                            "id": "dir-1",
                                            "text": "Keep ids stable.",
                                            "category": "directive",
                                            "memindContextSource": "project",
                                        }
                                    ],
                                    "insights": [],
                                },
                            ) as build_context:
                                result = retrieve.handle_user_prompt_submit(
                                    {
                                        "hook_event_name": "UserPromptSubmit",
                                        "cwd": tmp,
                                        "session_id": "s1",
                                        "prompt": "Fix payment tests",
                                    }
                                )

    build_context.assert_called_once()
    args = build_context.call_args.args
    self.assertEqual(args[3], "memind-main")
    context = result["hookSpecificOutput"]["additionalContext"]
    self.assertIn('<memind_memories project="memind-main" mode="project-first">', context)
    self.assertIn("[item:dir-1 directive, project]", context)
```

If `retrieve.py` does not yet expose `handle_user_prompt_submit(...)`, the test should fail until Step 3 introduces it.

- [ ] **Step 2: Write equivalent Codex hook tests**

Add equivalent tests to `memind-integrations/codex/tests/test_hooks.py` with:

- `sourceClient`: `"codex"`
- prompt field can be `"prompt"` or `"user_prompt"`; include one test using `"user_prompt"` to preserve Codex behavior.
- Use Codex `state_key(...)` behavior in assertions if existing tests use it.
- Include the same `build_context.call_args.args[3] == "memind-main"` assertion so the test verifies current-project attribution is passed into the helper.

- [ ] **Step 3: Run hook tests and verify failure**

Run:

```bash
python3 -m unittest \
  memind-integrations/claude-code/tests/test_hooks.py \
  memind-integrations/codex/tests/test_hooks.py
```

Expected: failure because `handle_user_prompt_submit(...)` and prompt helper wiring do not exist.

- [ ] **Step 4: Refactor Claude Code `retrieve.py`**

Modify imports:

```python
from pathlib import Path
from lib.identity import project_slug, resolve_identity
from lib.prompt_context import build_prompt_context
```

Extract the current `main()` body into:

```python
def handle_user_prompt_submit(hook_input):
    config = load_config()
    session_id = hook_input.get("session_id") or "unknown-session"
    prompt = hook_input.get("prompt") or ""
    hook_input["source_client"] = config.get("sourceClient") or "claude-code"
    with SessionStateStore(state_root()).locked(session_id) as state:
        turn_id, turn_seq = state.start_agent_turn(session_id)
        seq = state.next_agent_seq()
        state.append_agent_event(
            normalize_user_prompt_event(
                hook_input, seq, turn_id=turn_id, turn_seq=turn_seq
            )
        )

    if not config.get("autoRetrieve", True):
        return {"continue": True}
    if not config.get("autoPromptContext", False):
        return {"continue": True}

    identity = resolve_identity(config, hook_input)
    context_turns = int(config.get("retrieveContextTurns", 0))
    recent_context = read_recent_context(hook_input.get("transcript_path"), context_turns)
    query = prompt if not recent_context else f"{recent_context}\ncurrent: {prompt}"
    cwd = hook_input.get("cwd") or os.getcwd()
    slug = project_slug(Path(cwd))
    client = MemindClient(config["memindApiUrl"], config.get("memindApiToken"), timeout=12, max_retries=0)
    result = build_prompt_context(client, identity, query, slug, config)
    context = _format_context(result, config)
    if not context:
        return {"continue": True}
    return {"hookSpecificOutput": {"hookEventName": "UserPromptSubmit", "additionalContext": context}}
```

Then simplify `main()`:

```python
def main():
    try:
        hook_input = json.loads(sys.stdin.read() or "{}")
        print(json.dumps(handle_user_prompt_submit(hook_input)))
    except Exception as exc:
        try:
            debug_log(load_config(), "retrieve_failed", {"error": str(exc)})
        except Exception:
            pass
        print(json.dumps({"continue": True}))
```

- [ ] **Step 5: Refactor Codex `retrieve.py`**

Modify imports:

```python
from pathlib import Path
from lib.identity import project_slug, resolve_identity
from lib.prompt_context import build_prompt_context
```

Extract the current `main()` body into:

```python
def handle_user_prompt_submit(hook_input):
    config = load_config()
    prompt = hook_input.get("prompt") or hook_input.get("user_prompt") or ""
    hook_input["source_client"] = config.get("sourceClient") or "codex"
    session_key = state_key(hook_input)
    with SessionStateStore(state_root()).locked(session_key) as state:
        turn_id, turn_seq = state.start_agent_turn(session_key)
        seq = state.next_agent_seq()
        state.append_agent_event(
            normalize_user_prompt_event(
                hook_input, seq, turn_id=turn_id, turn_seq=turn_seq
            )
        )

    if not config.get("autoRetrieve", True):
        return {"continue": True}
    if not config.get("autoPromptContext", False):
        return {"continue": True}

    identity = resolve_identity(config, hook_input)
    context_turns = int(config.get("retrieveContextTurns", 0))
    recent_context = read_recent_context(hook_input.get("transcript_path"), context_turns)
    query = prompt if not recent_context else f"{recent_context}\ncurrent: {prompt}"
    cwd = hook_input.get("cwd") or os.getcwd()
    slug = project_slug(Path(cwd))
    client = MemindClient(config["memindApiUrl"], config.get("memindApiToken"), timeout=12, max_retries=0)
    result = build_prompt_context(client, identity, query, slug, config)
    context = _format_context(result, config)
    if not context:
        return {"continue": True}
    return {"hookSpecificOutput": {"hookEventName": "UserPromptSubmit", "additionalContext": context}}
```

Then simplify `main()`:

```python
def main():
    try:
        hook_input = json.loads(sys.stdin.read() or "{}")
        print(json.dumps(handle_user_prompt_submit(hook_input)))
    except Exception as exc:
        try:
            debug_log(load_config(), "retrieve_failed", {"error": str(exc)})
        except Exception:
            pass
        print(json.dumps({"continue": True}))
```

- [ ] **Step 6: Run hook tests**

Run:

```bash
python3 -m unittest \
  memind-integrations/claude-code/tests/test_hooks.py \
  memind-integrations/codex/tests/test_hooks.py
```

Expected: pass.

- [ ] **Step 7: Commit**

```bash
git add \
  memind-integrations/claude-code/scripts/retrieve.py \
  memind-integrations/claude-code/tests/test_hooks.py \
  memind-integrations/codex/scripts/retrieve.py \
  memind-integrations/codex/tests/test_hooks.py
git commit -m "feat(agent): gate prompt context injection"
```

## Task 5: Update Documentation

**Files:**

- Modify `memind-integrations/claude-code/README.md`
- Modify `memind-integrations/codex/README.md`

- [ ] **Step 1: Update capability summary**

In both READMEs, change text that currently says retrieval happens before each prompt by default. Use this wording:

```markdown
- **Prompt context (optional)**: `UserPromptSubmit` always buffers the user prompt into the local agent timeline. If `autoPromptContext=true`, it also retrieves project-first Memind memories with a bounded global fallback and injects them as `<memind_memories>...</memind_memories>`.
```

- [ ] **Step 2: Update hook table**

Change `UserPromptSubmit` description to:

```markdown
| `UserPromptSubmit` | `scripts/retrieve.py` | 12s | Buffer the user prompt event. Optionally inject project-first prompt memory when `autoPromptContext=true`. |
```

- [ ] **Step 3: Update settings table**

Add rows:

```markdown
| `autoPromptContext` | `false` | Enables prompt-time `<memind_memories>` retrieval and injection on `UserPromptSubmit`. Off by default to avoid token cost and unrelated cross-project recall. |
| `promptContextProjectMinEntries` | `4` | Minimum current-project entries before global fallback is skipped. |
| `promptContextGlobalFallbackEntries` | `3` | Maximum fallback entries from the shared memory space when current-project results are sparse. |
| `promptContextGlobalFallbackMinScore` | `0.65` | Minimum score for fallback entries. |
```

Update `autoRetrieve` row:

```markdown
| `autoRetrieve` | `true` | Backward-compatible broad retrieval gate used by prompt and tool retrieval paths. Leave enabled unless you want to disable retrieval-assisted contexts entirely. |
```

- [ ] **Step 4: Update examples**

Add an example:

```json
{
  "autoPromptContext": true,
  "promptContextProjectMinEntries": 4,
  "promptContextGlobalFallbackEntries": 3,
  "promptContextGlobalFallbackMinScore": 0.65
}
```

Add example rendered context:

```xml
<memind_memories project="memind-main-a1b2c3" mode="project-first">
Relevant memories from Memind. Use only when directly helpful:

## Directives
- [item:dir-1 directive, project, 2026-05-27] Keep userId and agentId stable across Claude Code, Codex, and API clients.
- [item:beh-1 behavior, global, 2026-05-20] User prefers Chinese replies for technical discussions.

## Tool Notes
- [item:tool-9 tool, shared, 2026-05-18] After hook integration changes, run both Claude Code and Codex integration tests.
</memind_memories>
```

- [ ] **Step 5: Run docs grep sanity**

Run:

```bash
rg -n "autoPromptContext|promptContextProjectMinEntries|Prompt context \\(optional\\)|project-first" \
  memind-integrations/claude-code/README.md \
  memind-integrations/codex/README.md
```

Expected: both READMEs mention the new settings and opt-in behavior.

- [ ] **Step 6: Commit**

```bash
git add \
  memind-integrations/claude-code/README.md \
  memind-integrations/codex/README.md
git commit -m "docs(agent): document opt-in prompt context"
```

## Task 6: Full Verification

**Files:**

- No new source files beyond previous tasks.

- [ ] **Step 1: Run Claude Code integration tests**

Run:

```bash
python3 -m unittest discover memind-integrations/claude-code/tests
```

Expected: all Claude Code tests pass.

- [ ] **Step 2: Run Codex integration tests**

Run:

```bash
python3 -m unittest discover memind-integrations/codex/tests
```

Expected: all Codex tests pass.

- [ ] **Step 3: Run targeted prompt context tests**

Run:

```bash
python3 -m unittest \
  memind-integrations/claude-code/tests/test_prompt_context.py \
  memind-integrations/claude-code/tests/test_hooks.py \
  memind-integrations/claude-code/tests/test_context_compiler.py \
  memind-integrations/codex/tests/test_prompt_context.py \
  memind-integrations/codex/tests/test_hooks.py \
  memind-integrations/codex/tests/test_context_compiler.py
```

Expected: all pass.

- [ ] **Step 4: Check whitespace**

Run:

```bash
git diff --check
```

Expected: no output.

- [ ] **Step 5: Confirm no generated caches are staged**

Run:

```bash
git status --short
```

Expected:

- Source/test/doc changes are staged or committed.
- `__pycache__` directories are not staged.

- [ ] **Step 6: Push branch**

Run:

```bash
git push
```

Expected: remote branch updates successfully.

## Risks And Mitigations

Risk: `autoRetrieve` naming remains confusing.

Mitigation: Keep it only for compatibility in this plan, document it as a broad legacy gate, and use `autoPromptContext`, `autoSessionContext`, and `autoToolContext` for precise user-facing behavior.

Risk: Global fallback may surface unrelated project memories.

Mitigation: Fallback is skipped when project results are sufficient, capped to three entries, score-filtered, deduped, and rendered with `global`/`shared` provenance labels.

Risk: Some retrieve responses may lack score fields.

Mitigation: Apply score thresholds only when a score field is present. Missing-score fallback entries keep Memind's returned order and are still capped by `promptContextGlobalFallbackEntries`. This preserves `RetrievedInsight` fallback results because the current Python client insight model does not expose scores.

Risk: Code duplication between Claude Code and Codex.

Mitigation: Keep files intentionally mirrored because the integrations are currently packaged separately. Tests must be added to both sides so future divergence is visible.

Risk: Prompt context disabled by default may surprise users who read older docs.

Mitigation: Update README language and settings tables to make SessionStart and PreToolUse the default automatic context paths, while prompt context is an explicit opt-in for users who want query-aware recall on every prompt.

## Expected User-Facing Result

Default behavior:

- New session receives `<memind_session_context>` when available.
- Tool calls receive `<memind_tool_context>` when exact file/tool context exists.
- Each user prompt is buffered into rawdata-agent timeline.
- No `<memind_memories>` is injected on every user prompt unless enabled.

Opt-in behavior:

- `autoPromptContext=true` enables query-aware prompt recall.
- The first retrieve is current-project constrained.
- If current-project memory is sparse, Memind adds high-score reusable memories from the shared `userId + agentId` memory space.
- Injected memories carry provenance labels so the coding agent can prioritize project memory and treat shared/global memory as reusable guidance, not current-repo fact.

## Self-Review

Spec coverage:

- Default prompt injection disabled: Task 1 and Task 4.
- UserPromptSubmit still buffers prompt: Task 4 tests and implementation.
- Project-aware exact filtering: Task 2 helper.
- Project-first plus global fallback: Task 2 helper and tests.
- Not project-only: Task 2 fallback uses no project metadata filter.
- Source labels: Task 3.
- Claude Code and Codex parity: every task touches both integrations.
- No core/OpenAPI changes: file map and non-goals constrain the work to integration code.

Placeholder scan:

- No TODO/TBD placeholders.
- Every implementation task names concrete files, commands, and expected outcomes.

Type consistency:

- New setting name is consistently `autoPromptContext`.
- New helper name is consistently `build_prompt_context`.
- New provenance field is consistently `memindContextSource`.
- Existing wrapper remains `memind_memories`.
