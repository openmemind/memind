# Agent PreToolUse Context Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Inject compact file/tool-aware Memind context immediately before high-value Claude Code and Codex tool calls.

**Architecture:** Keep the existing `rawdata-agent` storage and extraction model unchanged. `PreToolUse` continues to buffer normalized tool-start events, then optionally performs a small retrieval against existing `tool`, `resolution`, `playbook`, `directive`, and `agent_episode` data and compiles a bounded `<memind_tool_context>` block. No additional LLM calls, no `rawdata-toolcall` double-ingestion, and no OpenAPI schema changes are required for v1.

**Tech Stack:** Python integration hooks, Memind Python client, OpenAPI item/rawdata query endpoints, existing metadata filter operators, unittest, JSON hook manifests.

---

## Scope And Non-Goals

In scope:

- Add a PreToolUse context path for Claude Code and Codex.
- Keep PreToolUse ingestion behavior: every PreToolUse still appends a normalized `agent_timeline` event to local durable state.
- Query existing Memind data by `userId + agentId`, current project slug, current file path, current command, and current tool name.
- Compile a small context block with:
  - `Prior Resolutions`
  - `Validation Notes`
  - `Relevant Playbooks`
  - `Directives`
  - `Recent Evidence`
- Use `toolRecords`, `toolStats`, and `toolGroups` only as internal evidence for ranking and concise summaries.
- Default token budget target: roughly 500-800 tokens, with `toolContextMaxChars=3500`.
- Fail open if Memind is unavailable, no high-value target exists, or no useful context is found.

Out of scope:

- No `rawdata-agent` Java storage model changes.
- No `rawdata-toolcall` ingestion or LLM extraction inside PreToolUse.
- No new OpenAPI endpoints or metadata-filter operators.
- No per-tool LLM observation extraction.
- No blocking or denying tool execution.
- No injection for low-value read/search/list tools in v1.

## Key Design Decisions

1. **PreToolUse is a local, exact-context compiler, not another broad memory retrieval.**  
   `UserPromptSubmit` already injects task-level memories. PreToolUse should only inject context specific to the imminent file edit or command.

2. **Claude Code PreToolUse must become synchronous.**  
   The current Claude Code manifest marks `PreToolUse` as async. An async hook is suitable for telemetry buffering but cannot reliably inject `additionalContext` before the tool executes. This plan removes `async` only from Claude Code `PreToolUse`; `PostToolUse`, `Stop`, `Notification`, and `SubagentStop` stay async.

3. **Codex already keeps hooks synchronous in the current manifest.**  
   Codex PreToolUse will call the new context compiler from its existing synchronous hook path, while preserving its
   current manifest contract.

4. **No new Memind core API is required for v1.**  
   Existing top-level metadata fields (`files`, `commands`, `toolNames`, `projectSlug`) are enough for exact filters. Nested `toolRecords` and `toolStats` are read from returned metadata for evidence summaries, not used as required server-side filters.

5. **Structured query first, semantic retrieve fallback second.**  
   Exact `query_items` / `query_raw_data` calls are preferred for file/command/tool matches. A bounded `retrieve` fallback is used only when exact queries do not return enough usable items.

6. **The context compiler hides raw telemetry.**  
   `durationMs`, `inputTokens`, and `outputTokens` should not be rendered. `toolStats` and `toolRecords` are summarized as validation evidence only.

## File Map

- Modify `memind-integrations/claude-code/hooks/hooks.json`
  - Make `PreToolUse` synchronous by removing `"async": true`.

- Modify `memind-integrations/claude-code/settings.json`
  - Add default PreToolUse context settings.

- Modify `memind-integrations/claude-code/scripts/lib/config.py`
  - Add config defaults and env vars for PreToolUse context.

- Modify `memind-integrations/claude-code/scripts/lib/client.py`
  - Extend the local `retrieve(...)` wrapper to pass structured filters and include options.

- Create `memind-integrations/claude-code/scripts/lib/tool_context.py`
  - Extract the tool target, query Memind, rank hits, and return compiler input.

- Modify `memind-integrations/claude-code/scripts/lib/context_compiler.py`
  - Add `compile_tool_context(...)`.

- Modify `memind-integrations/claude-code/scripts/pre_tool_use.py`
  - Buffer the event first, then optionally inject compiled tool context.

- Modify Claude Code tests:
  - `memind-integrations/claude-code/tests/test_config.py`
  - `memind-integrations/claude-code/tests/test_client.py`
  - `memind-integrations/claude-code/tests/test_context_compiler.py`
  - `memind-integrations/claude-code/tests/test_hooks.py`
  - `memind-integrations/claude-code/tests/test_manifest.py`
  - `memind-integrations/claude-code/tests/test_installer.py`

- Apply Codex-specific changes with the Codex source client, plugin root, state key, and installer layout:
  - `memind-integrations/codex/settings.json`
  - `memind-integrations/codex/scripts/lib/config.py`
  - `memind-integrations/codex/scripts/lib/client.py`
  - `memind-integrations/codex/scripts/lib/tool_context.py`
  - `memind-integrations/codex/scripts/lib/context_compiler.py`
  - `memind-integrations/codex/scripts/pre_tool_use.py`
  - `memind-integrations/codex/tests/*`
  - `memind-integrations/codex/install.sh`

- Modify documentation:
  - `memind-integrations/claude-code/README.md`
  - `memind-integrations/codex/README.md`
  - `docs/superpowers/specs/2026-05-24-rawdata-agent-design.md`

---

### Task 1: Add PreToolUse Context Configuration And Manifest Contract

**Files:**
- Modify: `memind-integrations/claude-code/settings.json`
- Modify: `memind-integrations/claude-code/scripts/lib/config.py`
- Modify: `memind-integrations/claude-code/hooks/hooks.json`
- Modify: `memind-integrations/claude-code/tests/test_config.py`
- Modify: `memind-integrations/claude-code/tests/test_manifest.py`
- Modify: `memind-integrations/codex/settings.json`
- Modify: `memind-integrations/codex/scripts/lib/config.py`
- Modify: `memind-integrations/codex/tests/test_config.py`
- Modify: `memind-integrations/codex/tests/test_manifest.py`

- [ ] **Step 1: Add failing Claude Code config assertions**

Add these assertions to `test_default_settings` in `memind-integrations/claude-code/tests/test_config.py`:

```python
self.assertTrue(DEFAULT_SETTINGS["autoToolContext"])
self.assertEqual(DEFAULT_SETTINGS["toolContextMaxChars"], 3500)
self.assertEqual(DEFAULT_SETTINGS["toolContextEntryMaxChars"], 520)
self.assertEqual(DEFAULT_SETTINGS["toolContextMaxItems"], 6)
self.assertEqual(DEFAULT_SETTINGS["toolContextMinExactItems"], 2)
```

Add this env override test:

```python
def test_tool_context_env_overrides(self):
    config = load_config(
        plugin_root=ROOT,
        user_config_path=Path("/no/such/file"),
        env={
            "CLAUDE_PLUGIN_ROOT": str(ROOT),
            "MEMIND_AUTO_TOOL_CONTEXT": "false",
            "MEMIND_TOOL_CONTEXT_MAX_CHARS": "2500",
            "MEMIND_TOOL_CONTEXT_ENTRY_MAX_CHARS": "400",
            "MEMIND_TOOL_CONTEXT_MAX_ITEMS": "4",
            "MEMIND_TOOL_CONTEXT_MIN_EXACT_ITEMS": "1",
        },
    )

    self.assertFalse(config["autoToolContext"])
    self.assertEqual(config["toolContextMaxChars"], 2500)
    self.assertEqual(config["toolContextEntryMaxChars"], 400)
    self.assertEqual(config["toolContextMaxItems"], 4)
    self.assertEqual(config["toolContextMinExactItems"], 1)
```

- [ ] **Step 2: Add failing Claude Code manifest assertions**

In `memind-integrations/claude-code/tests/test_manifest.py`, change the PreToolUse assertion from async to synchronous:

```python
pre_tool_hook = hooks["PreToolUse"][0]["hooks"][0]
self.assertNotIn("async", pre_tool_hook)
self.assertLessEqual(pre_tool_hook["timeout"], 5)
self.assertTrue(hooks["PostToolUse"][0]["hooks"][0]["async"])
```

Keep the existing assertions for `PostToolUse`, `Notification`, `SubagentStop`, and `Stop` async behavior.

- [ ] **Step 3: Add Codex config assertions**

Add these default assertions to `memind-integrations/codex/tests/test_config.py`:

```python
self.assertTrue(DEFAULT_SETTINGS["autoToolContext"])
self.assertEqual(DEFAULT_SETTINGS["toolContextMaxChars"], 3500)
self.assertEqual(DEFAULT_SETTINGS["toolContextEntryMaxChars"], 520)
self.assertEqual(DEFAULT_SETTINGS["toolContextMaxItems"], 6)
self.assertEqual(DEFAULT_SETTINGS["toolContextMinExactItems"], 2)
```

Add this Codex env override test, using `CODEX_PLUGIN_ROOT`:

```python
def test_tool_context_env_overrides(self):
    config = load_config(
        plugin_root=ROOT,
        user_config_path=Path("/no/such/file"),
        env={
            "CODEX_PLUGIN_ROOT": str(ROOT),
            "MEMIND_AUTO_TOOL_CONTEXT": "false",
            "MEMIND_TOOL_CONTEXT_MAX_CHARS": "2500",
            "MEMIND_TOOL_CONTEXT_ENTRY_MAX_CHARS": "400",
            "MEMIND_TOOL_CONTEXT_MAX_ITEMS": "4",
            "MEMIND_TOOL_CONTEXT_MIN_EXACT_ITEMS": "1",
        },
    )

    self.assertFalse(config["autoToolContext"])
    self.assertEqual(config["toolContextMaxChars"], 2500)
    self.assertEqual(config["toolContextEntryMaxChars"], 400)
    self.assertEqual(config["toolContextMaxItems"], 4)
    self.assertEqual(config["toolContextMinExactItems"], 1)
```

- [ ] **Step 4: Keep Codex manifest synchronous**

In `memind-integrations/codex/tests/test_manifest.py`, add an explicit assertion:

```python
self.assertNotIn("async", hooks["PreToolUse"][0]["hooks"][0])
```

- [ ] **Step 5: Run config and manifest tests to verify failure**

Run:

```bash
python3 -m unittest \
  memind-integrations/claude-code/tests/test_config.py \
  memind-integrations/claude-code/tests/test_manifest.py \
  memind-integrations/codex/tests/test_config.py \
  memind-integrations/codex/tests/test_manifest.py
```

Expected: FAIL because the new config keys are missing and Claude Code PreToolUse is still async.

- [ ] **Step 6: Add settings defaults**

Add these keys to both `settings.json` files:

```json
"autoToolContext": true,
"toolContextMaxChars": 3500,
"toolContextEntryMaxChars": 520,
"toolContextMaxItems": 6,
"toolContextMinExactItems": 2,
```

Place them near `retrieveContextTurns` so all retrieval-related settings stay together.

- [ ] **Step 7: Add config defaults and env vars**

In both `scripts/lib/config.py` files, add to `DEFAULT_SETTINGS`:

```python
"autoToolContext": True,
"toolContextMaxChars": 3500,
"toolContextEntryMaxChars": 520,
"toolContextMaxItems": 6,
"toolContextMinExactItems": 2,
```

Add to `ENV_MAP` in both files:

```python
"MEMIND_AUTO_TOOL_CONTEXT": ("autoToolContext", "bool"),
"MEMIND_TOOL_CONTEXT_MAX_CHARS": ("toolContextMaxChars", "int"),
"MEMIND_TOOL_CONTEXT_ENTRY_MAX_CHARS": ("toolContextEntryMaxChars", "int"),
"MEMIND_TOOL_CONTEXT_MAX_ITEMS": ("toolContextMaxItems", "int"),
"MEMIND_TOOL_CONTEXT_MIN_EXACT_ITEMS": ("toolContextMinExactItems", "int_allow_zero"),
```

- [ ] **Step 8: Make Claude Code PreToolUse synchronous**

In `memind-integrations/claude-code/hooks/hooks.json`, remove only this line from the `PreToolUse` hook:

```json
"async": true
```

Do not change `PostToolUse`, `Stop`, `Notification`, or `SubagentStop`.

- [ ] **Step 9: Run tests and verify pass**

Run:

```bash
python3 -m unittest \
  memind-integrations/claude-code/tests/test_config.py \
  memind-integrations/claude-code/tests/test_manifest.py \
  memind-integrations/codex/tests/test_config.py \
  memind-integrations/codex/tests/test_manifest.py
```

Expected: PASS.

- [ ] **Step 10: Commit**

```bash
git add \
  memind-integrations/claude-code/settings.json \
  memind-integrations/claude-code/scripts/lib/config.py \
  memind-integrations/claude-code/hooks/hooks.json \
  memind-integrations/claude-code/tests/test_config.py \
  memind-integrations/claude-code/tests/test_manifest.py \
  memind-integrations/codex/settings.json \
  memind-integrations/codex/scripts/lib/config.py \
  memind-integrations/codex/tests/test_config.py \
  memind-integrations/codex/tests/test_manifest.py
git commit -m "feat(agent): configure pre-tool context"
```

---

### Task 2: Extend Local Client Wrappers For Structured Retrieval

**Files:**
- Modify: `memind-integrations/claude-code/scripts/lib/client.py`
- Modify: `memind-integrations/claude-code/tests/test_client.py`
- Modify: `memind-integrations/codex/scripts/lib/client.py`
- Modify: `memind-integrations/codex/tests/test_client.py`

- [ ] **Step 1: Add failing Claude Code structured retrieve wrapper test**

In `memind-integrations/claude-code/tests/test_client.py`, first extend the fake model helpers near `_MetadataFilter`:

```python
class _MetadataFilter:
    def __init__(self, all=None, any=None, not_=None, **kwargs):
        excluded = kwargs.get("not", not_)
        self.all = [_MetadataCondition(**item) for item in (all or [])]
        self.any = [_MetadataCondition(**item) for item in (any or [])]
        self.not_ = [_MetadataCondition(**item) for item in (excluded or [])]


class _RetrieveIncludeOptions:
    def __init__(
        self,
        raw_data_metadata=None,
        rawDataMetadata=None,
        raw_data_segment=None,
        rawDataSegment=None,
    ):
        self.raw_data_metadata = (
            raw_data_metadata if raw_data_metadata is not None else rawDataMetadata
        )
        self.raw_data_segment = (
            raw_data_segment if raw_data_segment is not None else rawDataSegment
        )


class _TimeRange:
    def __init__(self, field=None, from_=None, to=None, **kwargs):
        self.field = field
        self.from_ = kwargs.get("from", from_)
        self.to = to
```

Then add these exports to `_fake_memind_module()`:

```python
module.MetadataFilter = _MetadataFilter
module.RetrieveIncludeOptions = _RetrieveIncludeOptions
module.TimeRange = _TimeRange
```

Add this test method to `ClientTest`:

```python
def test_retrieve_passes_structured_filters(self):
    with mock.patch.dict(sys.modules, {"memind": _fake_memind_module()}):
        MemindClient = _load_client_class()
        client = MemindClient("http://memind", "token", timeout=1, max_retries=0)
        result = client.retrieve(
            "u",
            "a",
            "payment context",
            "SIMPLE",
            False,
            scope="AGENT",
            categories=["resolution", "tool"],
            metadata_filter={
                "all": [{"path": "projectSlug", "op": "eq", "value": "payment"}],
                "any": [{"path": "files", "op": "contains", "value": "src/payment/calc.ts"}],
            },
            include={"rawDataMetadata": True},
        )

    self.assertIsNotNone(result)
    instance = _FakeSyncMemindClient.instances[0]
    retrieve_call = instance.memory.calls[0][1]
    self.assertEqual(retrieve_call["scope"], "AGENT")
    self.assertEqual(retrieve_call["categories"], ["resolution", "tool"])
    self.assertEqual(retrieve_call["metadata_filter"].all[0].path, "projectSlug")
    self.assertEqual(retrieve_call["metadata_filter"].any[0].path, "files")
    self.assertTrue(retrieve_call["include"].raw_data_metadata)
```

- [ ] **Step 2: Add Codex structured retrieve wrapper test**

In `memind-integrations/codex/tests/test_client.py`, add these fake model helpers because the Codex wrapper imports
official `memind` Python client types:

```python
class _MetadataFilter:
    def __init__(self, all=None, any=None, not_=None, **kwargs):
        excluded = kwargs.get("not", not_)
        self.all = [_MetadataCondition(**item) for item in (all or [])]
        self.any = [_MetadataCondition(**item) for item in (any or [])]
        self.not_ = [_MetadataCondition(**item) for item in (excluded or [])]


class _RetrieveIncludeOptions:
    def __init__(
        self,
        raw_data_metadata=None,
        rawDataMetadata=None,
        raw_data_segment=None,
        rawDataSegment=None,
    ):
        self.raw_data_metadata = (
            raw_data_metadata if raw_data_metadata is not None else rawDataMetadata
        )
        self.raw_data_segment = (
            raw_data_segment if raw_data_segment is not None else rawDataSegment
        )


class _TimeRange:
    def __init__(self, field=None, from_=None, to=None, **kwargs):
        self.field = field
        self.from_ = kwargs.get("from", from_)
        self.to = to
```

Export them from the Codex `_fake_memind_module()`:

```python
module.MetadataFilter = _MetadataFilter
module.RetrieveIncludeOptions = _RetrieveIncludeOptions
module.TimeRange = _TimeRange
```

Add this test method to the Codex client test class:

```python
def test_retrieve_passes_structured_filters(self):
    with mock.patch.dict(sys.modules, {"memind": _fake_memind_module()}):
        MemindClient = _load_client_class()
        client = MemindClient("http://memind", "token", timeout=1, max_retries=0)
        result = client.retrieve(
            "u",
            "a",
            "payment context",
            "SIMPLE",
            False,
            scope="AGENT",
            categories=["resolution", "tool"],
            metadata_filter={
                "all": [{"path": "projectSlug", "op": "eq", "value": "payment"}],
                "any": [{"path": "files", "op": "contains", "value": "src/payment/calc.ts"}],
            },
            include={"rawDataMetadata": True},
        )

    self.assertIsNotNone(result)
    instance = _FakeSyncMemindClient.instances[0]
    retrieve_call = instance.memory.calls[0][1]
    self.assertEqual(retrieve_call["scope"], "AGENT")
    self.assertEqual(retrieve_call["categories"], ["resolution", "tool"])
    self.assertEqual(retrieve_call["metadata_filter"].all[0].path, "projectSlug")
    self.assertEqual(retrieve_call["metadata_filter"].any[0].path, "files")
    self.assertTrue(retrieve_call["include"].raw_data_metadata)
```

- [ ] **Step 3: Run tests to verify failure**

Run:

```bash
python3 -m unittest \
  memind-integrations/claude-code/tests/test_client.py \
  memind-integrations/codex/tests/test_client.py
```

Expected: FAIL because the local wrapper does not accept structured retrieve parameters yet.

- [ ] **Step 4: Extend the Claude Code wrapper**

Change the `retrieve` signature in `memind-integrations/claude-code/scripts/lib/client.py` to:

```python
def retrieve(
    self,
    user_id,
    agent_id,
    query,
    strategy="SIMPLE",
    trace=False,
    scope=None,
    categories=None,
    time_range=None,
    metadata_filter=None,
    include=None,
):
```

Inside the method import these types:

```python
from memind import (
    MemindClient as OfficialMemindClient,
    MetadataFilter,
    RetrieveIncludeOptions,
    TimeRange,
)
```

Build typed optional objects:

```python
metadata_filter_obj = (
    MetadataFilter(**metadata_filter)
    if isinstance(metadata_filter, dict)
    else metadata_filter
)
include_obj = (
    RetrieveIncludeOptions(**include)
    if isinstance(include, dict)
    else include
)
time_range_obj = TimeRange(**time_range) if isinstance(time_range, dict) else time_range
```

Pass them to the official client:

```python
return client.memory.retrieve(
    user_id=user_id,
    agent_id=agent_id,
    query=query,
    strategy=strategy,
    trace=trace,
    scope=scope,
    categories=categories,
    time_range=time_range_obj,
    metadata_filter=metadata_filter_obj,
    include=include_obj,
)
```

- [ ] **Step 5: Extend the Codex wrapper**

Change the `retrieve` signature in `memind-integrations/codex/scripts/lib/client.py` to accept structured retrieval
parameters:

```python
def retrieve(
    self,
    user_id,
    agent_id,
    query,
    strategy="SIMPLE",
    trace=False,
    scope=None,
    categories=None,
    time_range=None,
    metadata_filter=None,
    include=None,
):
```

Inside the method import these official client model types:

```python
from memind import (
    MemindClient as OfficialMemindClient,
    MetadataFilter,
    RetrieveIncludeOptions,
    TimeRange,
)
```

Convert dict inputs to official typed objects:

```python
metadata_filter_obj = (
    MetadataFilter(**metadata_filter)
    if isinstance(metadata_filter, dict)
    else metadata_filter
)
include_obj = (
    RetrieveIncludeOptions(**include)
    if isinstance(include, dict)
    else include
)
time_range_obj = TimeRange(**time_range) if isinstance(time_range, dict) else time_range
```

Pass all optional retrieval controls through to `client.memory.retrieve(...)`:

```python
return client.memory.retrieve(
    user_id=user_id,
    agent_id=agent_id,
    query=query,
    strategy=strategy,
    trace=trace,
    scope=scope,
    categories=categories,
    time_range=time_range_obj,
    metadata_filter=metadata_filter_obj,
    include=include_obj,
)
```

- [ ] **Step 6: Run client tests and verify pass**

Run:

```bash
python3 -m unittest \
  memind-integrations/claude-code/tests/test_client.py \
  memind-integrations/codex/tests/test_client.py
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add \
  memind-integrations/claude-code/scripts/lib/client.py \
  memind-integrations/claude-code/tests/test_client.py \
  memind-integrations/codex/scripts/lib/client.py \
  memind-integrations/codex/tests/test_client.py
git commit -m "feat(agent): support structured retrieve in integrations"
```

---

### Task 3: Add Tool Context Target Extraction And Query Planning

**Files:**
- Create: `memind-integrations/claude-code/scripts/lib/tool_context.py`
- Create: `memind-integrations/claude-code/tests/test_tool_context.py`
- Create: `memind-integrations/codex/scripts/lib/tool_context.py`
- Create: `memind-integrations/codex/tests/test_tool_context.py`

- [ ] **Step 1: Add failing Claude Code target extraction tests**

Create `memind-integrations/claude-code/tests/test_tool_context.py`:

```python
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

from scripts.lib.tool_context import (
    build_metadata_filter,
    current_turn_prompt,
    extract_tool_context_target,
    should_query_tool_context,
)


class ToolContextTest(unittest.TestCase):
    def test_extracts_file_edit_target(self):
        target = extract_tool_context_target(
            {
                "kind": "file_edit",
                "toolName": "Edit",
                "path": "src/payment/calc.ts",
                "operation": "edit",
                "metadata": {"turnId": "s-turn-1"},
            },
            {"cwd": "/repo/payment"},
            "payment-service-abc",
        )

        self.assertEqual(target["toolName"], "Edit")
        self.assertEqual(target["kind"], "file_edit")
        self.assertEqual(target["path"], "src/payment/calc.ts")
        self.assertEqual(target["projectSlug"], "payment-service-abc")

    def test_extracts_command_target(self):
        target = extract_tool_context_target(
            {
                "kind": "test_result",
                "toolName": "Bash",
                "command": "npm test payment",
                "operation": "run",
                "metadata": {"validationType": "test"},
            },
            {"cwd": "/repo/payment"},
            "payment-service-abc",
        )

        self.assertEqual(target["command"], "npm test payment")
        self.assertEqual(target["validationType"], "test")

    def test_skips_low_value_tools(self):
        target = extract_tool_context_target(
            {"kind": "file_read", "toolName": "Read", "path": "README.md"},
            {"cwd": "/repo/payment"},
            "payment-service-abc",
        )

        self.assertFalse(should_query_tool_context(target, {"autoToolContext": True}))

    def test_skips_when_disabled_or_no_target(self):
        self.assertFalse(should_query_tool_context({}, {"autoToolContext": True}))
        self.assertFalse(
            should_query_tool_context(
                {"kind": "file_edit", "path": "src/a.ts"}, {"autoToolContext": False}
            )
        )

    def test_builds_top_level_metadata_filter(self):
        metadata_filter = build_metadata_filter(
            {
                "projectSlug": "payment-service-abc",
                "path": "src/payment/calc.ts",
                "command": "npm test payment",
                "toolName": "Bash",
            },
            include_project=True,
        )

        self.assertEqual(
            metadata_filter["all"],
            [{"path": "projectSlug", "op": "eq", "value": "payment-service-abc"}],
        )
        self.assertIn(
            {"path": "files", "op": "contains", "value": "src/payment/calc.ts"},
            metadata_filter["any"],
        )
        self.assertIn(
            {"path": "commands", "op": "contains", "value": "npm test payment"},
            metadata_filter["any"],
        )
        self.assertIn(
            {"path": "toolNames", "op": "contains", "value": "Bash"},
            metadata_filter["any"],
        )

    def test_current_turn_prompt_uses_matching_turn_id(self):
        prompt = current_turn_prompt(
            [
                {"kind": "user_prompt", "text": "older", "metadata": {"turnId": "t0"}},
                {"kind": "user_prompt", "text": "Fix payment tests", "metadata": {"turnId": "t1"}},
                {"kind": "file_edit", "metadata": {"turnId": "t1"}},
            ],
            "t1",
        )

        self.assertEqual(prompt, "Fix payment tests")


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Add failing Codex target extraction tests**

Create `memind-integrations/codex/tests/test_tool_context.py` with the Codex scripts path and these expected normalized
target behaviors:

```python
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

from scripts.lib.tool_context import (
    build_metadata_filter,
    current_turn_prompt,
    extract_tool_context_target,
    should_query_tool_context,
)


class ToolContextTest(unittest.TestCase):
    def test_extracts_file_edit_target(self):
        target = extract_tool_context_target(
            {
                "kind": "file_edit",
                "toolName": "Edit",
                "path": "src/payment/calc.ts",
                "operation": "edit",
                "metadata": {"turnId": "s-turn-1"},
            },
            {"cwd": "/repo/payment"},
            "payment-service-abc",
        )

        self.assertEqual(target["toolName"], "Edit")
        self.assertEqual(target["kind"], "file_edit")
        self.assertEqual(target["path"], "src/payment/calc.ts")
        self.assertEqual(target["projectSlug"], "payment-service-abc")

    def test_extracts_command_target(self):
        target = extract_tool_context_target(
            {
                "kind": "test_result",
                "toolName": "Bash",
                "command": "npm test payment",
                "operation": "run",
                "metadata": {"validationType": "test"},
            },
            {"cwd": "/repo/payment"},
            "payment-service-abc",
        )

        self.assertEqual(target["command"], "npm test payment")
        self.assertEqual(target["validationType"], "test")

    def test_skips_low_value_tools(self):
        target = extract_tool_context_target(
            {"kind": "file_read", "toolName": "Read", "path": "README.md"},
            {"cwd": "/repo/payment"},
            "payment-service-abc",
        )

        self.assertFalse(should_query_tool_context(target, {"autoToolContext": True}))

    def test_skips_when_disabled_or_no_target(self):
        self.assertFalse(should_query_tool_context({}, {"autoToolContext": True}))
        self.assertFalse(
            should_query_tool_context(
                {"kind": "file_edit", "path": "src/a.ts"}, {"autoToolContext": False}
            )
        )

    def test_builds_top_level_metadata_filter(self):
        metadata_filter = build_metadata_filter(
            {
                "projectSlug": "payment-service-abc",
                "path": "src/payment/calc.ts",
                "command": "npm test payment",
                "toolName": "Bash",
            },
            include_project=True,
        )

        self.assertEqual(
            metadata_filter["all"],
            [{"path": "projectSlug", "op": "eq", "value": "payment-service-abc"}],
        )
        self.assertIn(
            {"path": "files", "op": "contains", "value": "src/payment/calc.ts"},
            metadata_filter["any"],
        )
        self.assertIn(
            {"path": "commands", "op": "contains", "value": "npm test payment"},
            metadata_filter["any"],
        )
        self.assertIn(
            {"path": "toolNames", "op": "contains", "value": "Bash"},
            metadata_filter["any"],
        )

    def test_current_turn_prompt_uses_matching_turn_id(self):
        prompt = current_turn_prompt(
            [
                {"kind": "user_prompt", "text": "older", "metadata": {"turnId": "t0"}},
                {"kind": "user_prompt", "text": "Fix payment tests", "metadata": {"turnId": "t1"}},
                {"kind": "file_edit", "metadata": {"turnId": "t1"}},
            ],
            "t1",
        )

        self.assertEqual(prompt, "Fix payment tests")


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 3: Run tests to verify failure**

Run:

```bash
python3 -m unittest \
  memind-integrations/claude-code/tests/test_tool_context.py \
  memind-integrations/codex/tests/test_tool_context.py
```

Expected: FAIL because `scripts/lib/tool_context.py` does not exist.

- [ ] **Step 4: Implement Claude Code target extraction**

Create `memind-integrations/claude-code/scripts/lib/tool_context.py`:

```python
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

HIGH_VALUE_KINDS = {"file_edit", "command", "test_result"}


def extract_tool_context_target(event, hook_input, project_slug):
    metadata = event.get("metadata") or {}
    target = {
        "toolName": event.get("toolName"),
        "kind": event.get("kind"),
        "path": event.get("path"),
        "command": event.get("command"),
        "operation": event.get("operation"),
        "validationType": metadata.get("validationType"),
        "projectSlug": project_slug,
        "cwd": hook_input.get("cwd"),
        "turnId": metadata.get("turnId"),
        "turnSeq": metadata.get("turnSeq"),
    }
    return {key: value for key, value in target.items() if value not in (None, "", [])}


def should_query_tool_context(target, config):
    if not config.get("autoToolContext", True) or not config.get("autoRetrieve", True):
        return False
    if not target or target.get("kind") not in HIGH_VALUE_KINDS:
        return False
    return bool(target.get("path") or target.get("command"))


def build_metadata_filter(target, include_project=True):
    all_conditions = []
    any_conditions = []
    if include_project and target.get("projectSlug"):
        all_conditions.append(
            {"path": "projectSlug", "op": "eq", "value": target["projectSlug"]}
        )
    if target.get("path"):
        any_conditions.append({"path": "files", "op": "contains", "value": target["path"]})
    if target.get("command"):
        any_conditions.append(
            {"path": "commands", "op": "contains", "value": target["command"]}
        )
    if target.get("toolName"):
        any_conditions.append(
            {"path": "toolNames", "op": "contains", "value": target["toolName"]}
        )
    return {
        "all": all_conditions,
        "any": any_conditions,
        "not": [],
    }


def current_turn_prompt(events, turn_id):
    if not turn_id:
        return ""
    for event in reversed(events or []):
        metadata = event.get("metadata") or {}
        if event.get("kind") == "user_prompt" and metadata.get("turnId") == turn_id:
            return event.get("text") or ""
    return ""
```

- [ ] **Step 5: Implement Codex target extraction**

Create `memind-integrations/codex/scripts/lib/tool_context.py` with the integration-neutral target schema below, so
downstream retrieval and compilation receive consistent fields across Codex and Claude Code:

```python
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

HIGH_VALUE_KINDS = {"file_edit", "command", "test_result"}


def extract_tool_context_target(event, hook_input, project_slug):
    metadata = event.get("metadata") or {}
    target = {
        "toolName": event.get("toolName"),
        "kind": event.get("kind"),
        "path": event.get("path"),
        "command": event.get("command"),
        "operation": event.get("operation"),
        "validationType": metadata.get("validationType"),
        "projectSlug": project_slug,
        "cwd": hook_input.get("cwd"),
        "turnId": metadata.get("turnId"),
        "turnSeq": metadata.get("turnSeq"),
    }
    return {key: value for key, value in target.items() if value not in (None, "", [])}


def should_query_tool_context(target, config):
    if not config.get("autoToolContext", True) or not config.get("autoRetrieve", True):
        return False
    if not target or target.get("kind") not in HIGH_VALUE_KINDS:
        return False
    return bool(target.get("path") or target.get("command"))


def build_metadata_filter(target, include_project=True):
    all_conditions = []
    any_conditions = []
    if include_project and target.get("projectSlug"):
        all_conditions.append(
            {"path": "projectSlug", "op": "eq", "value": target["projectSlug"]}
        )
    if target.get("path"):
        any_conditions.append({"path": "files", "op": "contains", "value": target["path"]})
    if target.get("command"):
        any_conditions.append(
            {"path": "commands", "op": "contains", "value": target["command"]}
        )
    if target.get("toolName"):
        any_conditions.append(
            {"path": "toolNames", "op": "contains", "value": target["toolName"]}
        )
    return {
        "all": all_conditions,
        "any": any_conditions,
        "not": [],
    }


def current_turn_prompt(events, turn_id):
    if not turn_id:
        return ""
    for event in reversed(events or []):
        metadata = event.get("metadata") or {}
        if event.get("kind") == "user_prompt" and metadata.get("turnId") == turn_id:
            return event.get("text") or ""
    return ""
```

- [ ] **Step 6: Run tests and verify pass**

Run:

```bash
python3 -m unittest \
  memind-integrations/claude-code/tests/test_tool_context.py \
  memind-integrations/codex/tests/test_tool_context.py
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add \
  memind-integrations/claude-code/scripts/lib/tool_context.py \
  memind-integrations/claude-code/tests/test_tool_context.py \
  memind-integrations/codex/scripts/lib/tool_context.py \
  memind-integrations/codex/tests/test_tool_context.py
git commit -m "feat(agent): extract pre-tool context targets"
```

---

### Task 4: Add Tool Context Retrieval And Ranking

**Files:**
- Modify: `memind-integrations/claude-code/scripts/lib/tool_context.py`
- Modify: `memind-integrations/claude-code/tests/test_tool_context.py`
- Modify: `memind-integrations/codex/scripts/lib/tool_context.py`
- Modify: `memind-integrations/codex/tests/test_tool_context.py`

- [ ] **Step 1: Add failing retrieval test for exact item and rawdata queries**

In both `test_tool_context.py` files, add `SimpleNamespace` to the imports:

```python
from types import SimpleNamespace
```

Then add this fake client class above `ToolContextTest`:

```python
class FakeClient:
    def __init__(self):
        self.item_queries = []
        self.raw_queries = []
        self.retrieve_queries = []

    def query_items(self, **kwargs):
        self.item_queries.append(kwargs)
        if kwargs.get("metadata_filter", {}).get("all"):
            return SimpleNamespace(
                items=[
                    SimpleNamespace(
                        id="res-1",
                        text="rounding mismatch was resolved in src/payment/calc.ts and validated with npm test payment.",
                        category="resolution",
                        created_at="2026-05-27T10:00:00Z",
                        metadata={
                            "projectSlug": "payment-service-abc",
                            "files": ["src/payment/calc.ts"],
                            "commands": ["npm test payment"],
                        },
                    )
                ]
            )
        return SimpleNamespace(items=[])

    def query_raw_data(self, **kwargs):
        self.raw_queries.append(kwargs)
        return SimpleNamespace(
            raw_data=[
                SimpleNamespace(
                    id="rd-1",
                    caption="Edited src/payment/calc.ts and validated npm test payment.",
                    type="agent_timeline",
                    created_at="2026-05-27T10:05:00Z",
                    metadata={
                        "projectSlug": "payment-service-abc",
                        "files": ["src/payment/calc.ts"],
                        "commands": ["npm test payment"],
                        "toolStats": {"Bash": {"successCount": 1, "failCount": 1}},
                    },
                )
            ]
        )

    def retrieve(self, *args, **kwargs):
        self.retrieve_queries.append(kwargs)
        return SimpleNamespace(items=[], insights=[], raw_data=[])
```

Add this method inside `ToolContextTest`:

```python

def test_load_tool_context_uses_exact_queries_first(self):
    from scripts.lib.tool_context import load_tool_context

    client = FakeClient()
    context = load_tool_context(
        client,
        "u",
        "a",
        {
            "toolName": "Edit",
            "kind": "file_edit",
            "path": "src/payment/calc.ts",
            "projectSlug": "payment-service-abc",
            "prompt": "Fix payment tests",
        },
        {"toolContextMaxItems": 6, "toolContextMinExactItems": 2},
    )

    self.assertEqual(len(client.item_queries), 2)
    self.assertEqual(len(client.raw_queries), 1)
    self.assertEqual(client.item_queries[0]["categories"], ["resolution", "tool", "playbook", "directive"])
    self.assertEqual(client.item_queries[0]["metadata_filter"]["all"][0]["path"], "projectSlug")
    self.assertEqual(client.raw_queries[0]["types"], ["agent_timeline"])
    self.assertEqual(context["target"]["path"], "src/payment/calc.ts")
    self.assertEqual(context["items"][0]["category"], "resolution")
    self.assertEqual(context["rawData"][0]["id"], "rd-1")
```

- [ ] **Step 2: Add failing semantic fallback test**

Add this method inside `ToolContextTest` in both `test_tool_context.py` files:

```python
def test_load_tool_context_uses_retrieve_fallback_when_exact_hits_are_sparse(self):
    from scripts.lib.tool_context import load_tool_context

    class SparseClient(FakeClient):
        def query_items(self, **kwargs):
            self.item_queries.append(kwargs)
            return SimpleNamespace(items=[])

        def query_raw_data(self, **kwargs):
            self.raw_queries.append(kwargs)
            return SimpleNamespace(raw_data=[])

        def retrieve(self, *args, **kwargs):
            self.retrieve_queries.append(kwargs)
            return SimpleNamespace(
                items=[
                    SimpleNamespace(
                        id="tool-1",
                        text="Use npm test payment after editing payment calculation files.",
                        category="tool",
                        created_at="2026-05-27T10:00:00Z",
                        metadata={"commands": ["npm test payment"]},
                    )
                ],
                insights=[],
                raw_data=[],
            )

    client = SparseClient()
    context = load_tool_context(
        client,
        "u",
        "a",
        {
            "toolName": "Bash",
            "kind": "test_result",
            "command": "npm test payment",
            "projectSlug": "payment-service-abc",
            "prompt": "Fix payment tests",
        },
        {"toolContextMaxItems": 6, "toolContextMinExactItems": 1},
    )

    self.assertEqual(len(client.retrieve_queries), 1)
    self.assertEqual(client.retrieve_queries[0]["categories"], ["resolution", "tool", "playbook", "directive"])
    self.assertEqual(context["items"][0]["id"], "tool-1")
```

- [ ] **Step 3: Run tests to verify failure**

Run:

```bash
python3 -m unittest \
  memind-integrations/claude-code/tests/test_tool_context.py \
  memind-integrations/codex/tests/test_tool_context.py
```

Expected: FAIL because `load_tool_context` does not exist.

- [ ] **Step 4: Implement query loading in Claude Code**

Add these functions to `memind-integrations/claude-code/scripts/lib/tool_context.py`:

```python
TOOL_CONTEXT_CATEGORIES = ["resolution", "tool", "playbook", "directive"]


def load_tool_context(client, user_id, agent_id, target, config):
    max_items = int(config.get("toolContextMaxItems", 6))
    min_exact = int(config.get("toolContextMinExactItems", 2))
    exact_items = []

    for include_project in [True, False]:
        metadata_filter = build_metadata_filter(target, include_project=include_project)
        if not metadata_filter["any"]:
            continue
        response = client.query_items(
            user_id=user_id,
            agent_id=agent_id,
            scope=None,
            categories=TOOL_CONTEXT_CATEGORIES,
            source_clients=None,
            raw_data_types=["agent_timeline"],
            metadata_filter=metadata_filter,
            limit=max(10, max_items * 3),
        )
        exact_items.extend(_normalize_items(getattr(response, "items", []) or []))
        if len(exact_items) >= max_items:
            break

    raw_data = []
    metadata_filter = build_metadata_filter(target, include_project=bool(target.get("projectSlug")))
    if metadata_filter["any"]:
        response = client.query_raw_data(
            user_id=user_id,
            agent_id=agent_id,
            types=["agent_timeline"],
            source_clients=None,
            metadata_filter=metadata_filter,
            include={"metadata": True, "segment": False},
            limit=max(6, max_items),
        )
        raw_data = _normalize_raw_data(getattr(response, "raw_data", []) or [])

    fallback_items = []
    if len(exact_items) < min_exact:
        retrieve_response = client.retrieve(
            user_id,
            agent_id,
            _semantic_query(target),
            config.get("retrieveStrategy", "SIMPLE"),
            False,
            scope=None,
            categories=TOOL_CONTEXT_CATEGORIES,
            metadata_filter=(
                {"all": [{"path": "projectSlug", "op": "eq", "value": target["projectSlug"]}]}
                if target.get("projectSlug")
                else None
            ),
            include={"rawDataMetadata": True},
        )
        fallback_items = _normalize_items(getattr(retrieve_response, "items", []) or [])

    items = _dedupe_by_id(exact_items + fallback_items)
    return {
        "target": dict(target),
        "items": rank_items(items, target)[:max_items],
        "rawData": rank_raw_data(raw_data, target)[:max_items],
    }


def _semantic_query(target):
    parts = []
    if target.get("prompt"):
        parts.append("task: " + target["prompt"])
    if target.get("path"):
        parts.append("file: " + target["path"])
    if target.get("command"):
        parts.append("command: " + target["command"])
    if target.get("toolName"):
        parts.append("tool: " + target["toolName"])
    return "\n".join(parts) or "coding agent tool context"


def _normalize_items(items):
    result = []
    for item in items:
        result.append(
            {
                "id": _field(item, "id"),
                "text": _field(item, "text"),
                "category": str(_field(item, "category") or "memory").lower(),
                "createdAt": _field(item, "createdAt") or _field(item, "created_at"),
                "metadata": _field(item, "metadata") or {},
            }
        )
    return [item for item in result if item.get("text")]


def _normalize_raw_data(raw_data):
    result = []
    for raw in raw_data:
        result.append(
            {
                "id": _field(raw, "rawDataId") or _field(raw, "raw_data_id") or _field(raw, "id"),
                "caption": _field(raw, "caption"),
                "type": _field(raw, "type"),
                "createdAt": _field(raw, "createdAt") or _field(raw, "created_at"),
                "metadata": _field(raw, "metadata") or {},
            }
        )
    return [raw for raw in result if raw.get("caption") or raw.get("metadata")]


def rank_items(items, target):
    return sorted(
        items,
        key=lambda item: (
            _match_score(item.get("metadata") or {}, target),
            _category_score(item.get("category")),
            item.get("createdAt") or "",
            item.get("id") or "",
        ),
        reverse=True,
    )


def rank_raw_data(raw_data, target):
    return sorted(
        raw_data,
        key=lambda raw: (
            _match_score(raw.get("metadata") or {}, target),
            raw.get("createdAt") or "",
            raw.get("id") or "",
        ),
        reverse=True,
    )


def _match_score(metadata, target):
    score = 0
    if target.get("projectSlug") and metadata.get("projectSlug") == target["projectSlug"]:
        score += 3
    if target.get("path") and target["path"] in metadata.get("files", []):
        score += 10
    if target.get("command") and target["command"] in metadata.get("commands", []):
        score += 8
    if target.get("toolName") and target["toolName"] in metadata.get("toolNames", []):
        score += 3
    stats = metadata.get("toolStats") or {}
    if target.get("toolName") in stats:
        tool_stats = stats[target["toolName"]]
        score += int(tool_stats.get("successCount") or 0)
    return score


def _category_score(category):
    return {"resolution": 5, "tool": 4, "playbook": 3, "directive": 2}.get(category or "", 1)


def _dedupe_by_id(items):
    result = []
    seen = set()
    for item in items:
        key = item.get("id") or item.get("text")
        if key in seen:
            continue
        seen.add(key)
        result.append(item)
    return result


def _field(value, name):
    if isinstance(value, dict):
        return value.get(name)
    return getattr(value, name, None)
```

- [ ] **Step 5: Implement Codex query loading**

Add the retrieval, normalization, ranking, and helper functions below to
`memind-integrations/codex/scripts/lib/tool_context.py`. They use the shared top-level metadata contract:
`projectSlug`, `files`, `commands`, and `toolNames`.

```python
TOOL_CONTEXT_CATEGORIES = ["resolution", "tool", "playbook", "directive"]


def load_tool_context(client, user_id, agent_id, target, config):
    max_items = int(config.get("toolContextMaxItems", 6))
    min_exact = int(config.get("toolContextMinExactItems", 2))
    exact_items = []

    for include_project in [True, False]:
        metadata_filter = build_metadata_filter(target, include_project=include_project)
        if not metadata_filter["any"]:
            continue
        response = client.query_items(
            user_id=user_id,
            agent_id=agent_id,
            scope=None,
            categories=TOOL_CONTEXT_CATEGORIES,
            source_clients=None,
            raw_data_types=["agent_timeline"],
            metadata_filter=metadata_filter,
            limit=max(10, max_items * 3),
        )
        exact_items.extend(_normalize_items(getattr(response, "items", []) or []))
        if len(exact_items) >= max_items:
            break

    raw_data = []
    metadata_filter = build_metadata_filter(target, include_project=bool(target.get("projectSlug")))
    if metadata_filter["any"]:
        response = client.query_raw_data(
            user_id=user_id,
            agent_id=agent_id,
            types=["agent_timeline"],
            source_clients=None,
            metadata_filter=metadata_filter,
            include={"metadata": True, "segment": False},
            limit=max(6, max_items),
        )
        raw_data = _normalize_raw_data(getattr(response, "raw_data", []) or [])

    fallback_items = []
    if len(exact_items) < min_exact:
        retrieve_response = client.retrieve(
            user_id,
            agent_id,
            _semantic_query(target),
            config.get("retrieveStrategy", "SIMPLE"),
            False,
            scope=None,
            categories=TOOL_CONTEXT_CATEGORIES,
            metadata_filter=(
                {"all": [{"path": "projectSlug", "op": "eq", "value": target["projectSlug"]}]}
                if target.get("projectSlug")
                else None
            ),
            include={"rawDataMetadata": True},
        )
        fallback_items = _normalize_items(getattr(retrieve_response, "items", []) or [])

    items = _dedupe_by_id(exact_items + fallback_items)
    return {
        "target": dict(target),
        "items": rank_items(items, target)[:max_items],
        "rawData": rank_raw_data(raw_data, target)[:max_items],
    }


def _semantic_query(target):
    parts = []
    if target.get("prompt"):
        parts.append("task: " + target["prompt"])
    if target.get("path"):
        parts.append("file: " + target["path"])
    if target.get("command"):
        parts.append("command: " + target["command"])
    if target.get("toolName"):
        parts.append("tool: " + target["toolName"])
    return "\n".join(parts) or "coding agent tool context"


def _normalize_items(items):
    result = []
    for item in items:
        result.append(
            {
                "id": _field(item, "id"),
                "text": _field(item, "text"),
                "category": str(_field(item, "category") or "memory").lower(),
                "createdAt": _field(item, "createdAt") or _field(item, "created_at"),
                "metadata": _field(item, "metadata") or {},
            }
        )
    return [item for item in result if item.get("text")]


def _normalize_raw_data(raw_data):
    result = []
    for raw in raw_data:
        result.append(
            {
                "id": _field(raw, "rawDataId") or _field(raw, "raw_data_id") or _field(raw, "id"),
                "caption": _field(raw, "caption"),
                "type": _field(raw, "type"),
                "createdAt": _field(raw, "createdAt") or _field(raw, "created_at"),
                "metadata": _field(raw, "metadata") or {},
            }
        )
    return [raw for raw in result if raw.get("caption") or raw.get("metadata")]


def rank_items(items, target):
    return sorted(
        items,
        key=lambda item: (
            _match_score(item.get("metadata") or {}, target),
            _category_score(item.get("category")),
            item.get("createdAt") or "",
            item.get("id") or "",
        ),
        reverse=True,
    )


def rank_raw_data(raw_data, target):
    return sorted(
        raw_data,
        key=lambda raw: (
            _match_score(raw.get("metadata") or {}, target),
            raw.get("createdAt") or "",
            raw.get("id") or "",
        ),
        reverse=True,
    )


def _match_score(metadata, target):
    score = 0
    if target.get("projectSlug") and metadata.get("projectSlug") == target["projectSlug"]:
        score += 3
    if target.get("path") and target["path"] in metadata.get("files", []):
        score += 10
    if target.get("command") and target["command"] in metadata.get("commands", []):
        score += 8
    if target.get("toolName") and target["toolName"] in metadata.get("toolNames", []):
        score += 3
    stats = metadata.get("toolStats") or {}
    if target.get("toolName") in stats:
        tool_stats = stats[target["toolName"]]
        score += int(tool_stats.get("successCount") or 0)
    return score


def _category_score(category):
    return {"resolution": 5, "tool": 4, "playbook": 3, "directive": 2}.get(category or "", 1)


def _dedupe_by_id(items):
    result = []
    seen = set()
    for item in items:
        key = item.get("id") or item.get("text")
        if key in seen:
            continue
        seen.add(key)
        result.append(item)
    return result


def _field(value, name):
    if isinstance(value, dict):
        return value.get(name)
    return getattr(value, name, None)
```

- [ ] **Step 6: Run tests and verify pass**

Run:

```bash
python3 -m unittest \
  memind-integrations/claude-code/tests/test_tool_context.py \
  memind-integrations/codex/tests/test_tool_context.py
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add \
  memind-integrations/claude-code/scripts/lib/tool_context.py \
  memind-integrations/claude-code/tests/test_tool_context.py \
  memind-integrations/codex/scripts/lib/tool_context.py \
  memind-integrations/codex/tests/test_tool_context.py
git commit -m "feat(agent): query pre-tool memory context"
```

---

### Task 5: Add The PreToolUse Context Compiler

**Files:**
- Modify: `memind-integrations/claude-code/scripts/lib/context_compiler.py`
- Modify: `memind-integrations/claude-code/tests/test_context_compiler.py`
- Modify: `memind-integrations/codex/scripts/lib/context_compiler.py`
- Modify: `memind-integrations/codex/tests/test_context_compiler.py`

- [ ] **Step 1: Add failing compiler test for file edit context**

Append to both `test_context_compiler.py` files:

```python
def test_tool_context_compiler_renders_bounded_file_context(self):
    from scripts.lib.context_compiler import compile_tool_context

    rendered = compile_tool_context(
        {
            "target": {
                "toolName": "Edit",
                "kind": "file_edit",
                "path": "src/payment/calc.ts",
                "projectSlug": "payment-service-abc",
            },
            "items": [
                {
                    "id": "res-1",
                    "category": "resolution",
                    "text": "rounding mismatch was resolved in src/payment/calc.ts and validated with npm test payment.",
                    "metadata": {
                        "files": ["src/payment/calc.ts"],
                        "commands": ["npm test payment"],
                    },
                },
                {
                    "id": "tool-1",
                    "category": "tool",
                    "text": "Use npm test payment to validate changes touching src/payment/calc.ts; it failed once and passed once in this agent episode.",
                    "metadata": {
                        "files": ["src/payment/calc.ts"],
                        "commands": ["npm test payment"],
                        "toolStats": {"Bash": {"successCount": 1, "failCount": 1}},
                    },
                },
                {
                    "id": "pb-1",
                    "category": "playbook",
                    "text": "When payment calculation logic changes, update focused tests first, then run npm test payment.",
                    "metadata": {},
                },
            ],
            "rawData": [
                {
                    "id": "rd-1",
                    "caption": "Edited src/payment/calc.ts and validated npm test payment.",
                    "metadata": {
                        "toolStats": {"Bash": {"successCount": 1, "failCount": 1}},
                    },
                }
            ],
        },
        {"toolContextMaxChars": 3500, "toolContextEntryMaxChars": 520},
    )

    self.assertIn('<memind_tool_context tool="Edit" file="src/payment/calc.ts"', rendered)
    self.assertIn("Use only if directly relevant to this exact tool call", rendered)
    self.assertIn("## Prior Resolutions", rendered)
    self.assertIn("[item:res-1 resolution]", rendered)
    self.assertIn("## Validation Notes", rendered)
    self.assertIn("[item:tool-1 tool]", rendered)
    self.assertIn("## Relevant Playbooks", rendered)
    self.assertIn("[item:pb-1 playbook]", rendered)
    self.assertIn("## Recent Evidence", rendered)
    self.assertIn("[rawdata:rd-1]", rendered)
    self.assertNotIn("durationMs", rendered)
    self.assertNotIn("inputTokens", rendered)
    self.assertNotIn("outputTokens", rendered)
    self.assertTrue(rendered.endswith("</memind_tool_context>"))
```

- [ ] **Step 2: Add failing compiler test for command context and truncation**

Append to both `test_context_compiler.py` files:

```python
def test_tool_context_compiler_renders_command_context_with_budget(self):
    from scripts.lib.context_compiler import compile_tool_context

    rendered = compile_tool_context(
        {
            "target": {
                "toolName": "Bash",
                "kind": "test_result",
                "command": "npm test payment",
                "projectSlug": "payment-service-abc",
            },
            "items": [
                {
                    "id": "tool-1",
                    "category": "tool",
                    "text": "Use npm test payment after editing payment calculation files. " + "x" * 900,
                    "metadata": {"commands": ["npm test payment"]},
                },
                {
                    "id": "dir-1",
                    "category": "directive",
                    "text": "Do not skip focused payment validation after touching calculation code.",
                    "metadata": {},
                },
            ],
            "rawData": [],
        },
        {"toolContextMaxChars": 900, "toolContextEntryMaxChars": 260},
    )

    self.assertLessEqual(len(rendered), 900)
    self.assertIn('<memind_tool_context tool="Bash" command="npm test payment"', rendered)
    self.assertIn("## Validation Notes", rendered)
    self.assertIn("## Directives", rendered)
    self.assertTrue(rendered.endswith("</memind_tool_context>"))
```

- [ ] **Step 3: Run compiler tests to verify failure**

Run:

```bash
python3 -m unittest \
  memind-integrations/claude-code/tests/test_context_compiler.py \
  memind-integrations/codex/tests/test_context_compiler.py
```

Expected: FAIL because `compile_tool_context` does not exist.

- [ ] **Step 4: Add compiler constants and entrypoint**

In both `scripts/lib/context_compiler.py` files, add after `PROMPT_SECTION_LIMITS`:

```python
TOOL_SECTION_ORDER = [
    ("priorResolutions", "## Prior Resolutions"),
    ("validationNotes", "## Validation Notes"),
    ("relevantPlaybooks", "## Relevant Playbooks"),
    ("directives", "## Directives"),
    ("recentEvidence", "## Recent Evidence"),
]

TOOL_SECTION_BUDGETS = {
    "priorResolutions": 900,
    "validationNotes": 700,
    "relevantPlaybooks": 700,
    "directives": 500,
    "recentEvidence": 700,
}
```

Update `SECTION_FIT_PRIORITY`:

```python
"memind_tool_context": [
    "priorResolutions",
    "validationNotes",
    "directives",
    "relevantPlaybooks",
    "recentEvidence",
],
```

Add this function before `_prepare_sections`:

```python
def compile_tool_context(context, config):
    target = context.get("target") or {}
    items = [_normalize_tool_item(item) for item in context.get("items") or [] if _field(item, "text")]
    raw_data = [_normalize_tool_rawdata(raw) for raw in context.get("rawData") or []]
    sections = {
        "priorResolutions": _top_category(items, "resolution", 2),
        "validationNotes": _top_category(items, "tool", 3),
        "relevantPlaybooks": _top_category(items, "playbook", 2),
        "directives": _top_category(items, "directive", 2),
        "recentEvidence": raw_data[:2],
    }

    attrs = {"tool": target.get("toolName") or "unknown"}
    if target.get("path"):
        attrs["file"] = target["path"]
    if target.get("command"):
        attrs["command"] = target["command"]
    if target.get("projectSlug"):
        attrs["project"] = target["projectSlug"]

    return _render_context(
        wrapper="memind_tool_context",
        attrs=attrs,
        preamble=(
            "Use only if directly relevant to this exact tool call. "
            "Current user instructions and repository files take precedence. "
            "Verify old details against the working tree before relying on them."
        ),
        sections=_prepare_sections(sections, "tool_context"),
        order=TOOL_SECTION_ORDER,
        budgets=TOOL_SECTION_BUDGETS,
        max_chars=int(config.get("toolContextMaxChars", 3500)),
        entry_max_chars=int(config.get("toolContextEntryMaxChars", 520)),
    )
```

- [ ] **Step 5: Keep total-budget fitting order stable for tool context**

In both `context_compiler.py` files, add this constant after `TOOL_SECTION_ORDER`:

```python
ALL_SECTION_ORDER = SESSION_SECTION_ORDER + PROMPT_SECTION_ORDER + TOOL_SECTION_ORDER
```

In `_fit_sections_to_total_budget(...)`, replace the final `selected_sections.sort(...)` line with:

```python
order_index = {key: index for index, (key, _title) in enumerate(ALL_SECTION_ORDER)}
selected_sections.sort(key=lambda item: order_index.get(item[0], 999))
```

This keeps `<memind_tool_context>` section ordering stable even when `toolContextMaxChars` forces lower-priority
sections to be omitted.

- [ ] **Step 6: Add tool item/rawdata normalizers**

Add these helpers before `_rank_entries` in both `context_compiler.py` files:

```python
def _normalize_tool_item(item):
    category = str(_field(item, "category") or "memory").strip().lower()
    return {
        "kind": "item",
        "id": _field(item, "id"),
        "category": category,
        "text": _clean(_field(item, "text")),
        "createdAt": _field(item, "createdAt") or _field(item, "created_at"),
        "score": _number(_field(item, "score"), _field(item, "finalScore"), 0),
    }


def _normalize_tool_rawdata(raw):
    text = _field(raw, "caption") or _recent_evidence_from_metadata(_field(raw, "metadata") or {})
    return {
        "kind": "rawdata",
        "id": _field(raw, "id") or _field(raw, "rawDataId") or _field(raw, "raw_data_id"),
        "category": "agent_timeline",
        "text": _clean(text),
        "createdAt": _field(raw, "createdAt") or _field(raw, "created_at"),
        "score": 0,
    }


def _recent_evidence_from_metadata(metadata):
    stats = metadata.get("toolStats") or {}
    parts = []
    for tool_name, stat in stats.items():
        success = int(stat.get("successCount") or 0)
        failed = int(stat.get("failCount") or 0)
        if success or failed:
            parts.append(f"{tool_name} failed {failed} time(s) and passed {success} time(s)")
    return "; ".join(parts)
```

Do not render token counts or durations.

- [ ] **Step 7: Run compiler tests and verify pass**

Run:

```bash
python3 -m unittest \
  memind-integrations/claude-code/tests/test_context_compiler.py \
  memind-integrations/codex/tests/test_context_compiler.py
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add \
  memind-integrations/claude-code/scripts/lib/context_compiler.py \
  memind-integrations/claude-code/tests/test_context_compiler.py \
  memind-integrations/codex/scripts/lib/context_compiler.py \
  memind-integrations/codex/tests/test_context_compiler.py
git commit -m "feat(agent): compile pre-tool memory context"
```

---

### Task 6: Wire Claude Code PreToolUse Injection

**Files:**
- Modify: `memind-integrations/claude-code/scripts/pre_tool_use.py`
- Modify: `memind-integrations/claude-code/tests/test_hooks.py`

- [ ] **Step 1: Add failing Claude Code hook injection test**

In `memind-integrations/claude-code/tests/test_hooks.py`, add:

```python
def test_pre_tool_use_injects_tool_context_when_memind_returns_matches(self):
    sys.path.insert(0, str(ROOT / "scripts"))
    import pre_tool_use
    from scripts.lib.state import SessionStateStore

    config = {
        "memindApiUrl": "http://127.0.0.1:8366",
        "memindApiToken": None,
        "sourceClient": "claude-code",
        "agentId": "coding-agent",
        "userId": "u",
        "autoRetrieve": True,
        "autoToolContext": True,
        "toolContextMaxItems": 6,
        "toolContextMinExactItems": 1,
        "toolContextMaxChars": 3500,
        "toolContextEntryMaxChars": 520,
        "retrieveStrategy": "SIMPLE",
    }

    with tempfile.TemporaryDirectory() as tmp:
        state_dir = Path(tmp) / "state"
        store = SessionStateStore(state_dir)
        with store.locked("s1") as state:
            turn_id, turn_seq = state.start_agent_turn("s1")
            state.append_agent_event(
                {
                    "eventId": "prompt",
                    "seq": 1,
                    "kind": "user_prompt",
                    "text": "Fix payment tests",
                    "metadata": {"turnId": turn_id, "turnSeq": turn_seq},
                }
            )

        class FakeClient:
            def query_items(self, **kwargs):
                return types.SimpleNamespace(
                    items=[
                        types.SimpleNamespace(
                            id="res-1",
                            text="rounding mismatch was resolved in src/payment/calc.ts and validated with npm test payment.",
                            category="resolution",
                            created_at="2026-05-27T10:00:00Z",
                            metadata={
                                "projectSlug": "tmp-project",
                                "files": ["src/payment/calc.ts"],
                            },
                        )
                    ]
                )

            def query_raw_data(self, **kwargs):
                return types.SimpleNamespace(raw_data=[])

            def retrieve(self, *args, **kwargs):
                return types.SimpleNamespace(items=[], insights=[], raw_data=[])

        with mock.patch.object(pre_tool_use, "state_root", return_value=state_dir):
            with mock.patch.object(pre_tool_use, "load_config", return_value=config):
                with mock.patch.object(pre_tool_use, "resolve_identity", return_value={"userId": "u", "agentId": "coding-agent"}):
                    with mock.patch.object(pre_tool_use, "MemindClient", return_value=FakeClient()):
                        output = pre_tool_use.handle_pre_tool_use(
                            {
                                "hook_event_name": "PreToolUse",
                                "cwd": tmp,
                                "session_id": "s1",
                                "tool_name": "Edit",
                                "tool_input": {"file_path": "src/payment/calc.ts"},
                                "timestamp": "2026-05-28T10:00:00Z",
                            }
                        )

    self.assertIn("hookSpecificOutput", output)
    context = output["hookSpecificOutput"]["additionalContext"]
    self.assertIn("<memind_tool_context", context)
    self.assertIn("## Prior Resolutions", context)
    self.assertIn("rounding mismatch", context)
```

- [ ] **Step 2: Add failing Claude Code disabled/fail-open test**

Add:

```python
def test_pre_tool_use_context_disabled_still_buffers_event(self):
    sys.path.insert(0, str(ROOT / "scripts"))
    import pre_tool_use

    config = {
        "sourceClient": "claude-code",
        "autoRetrieve": True,
        "autoToolContext": False,
    }

    with tempfile.TemporaryDirectory() as tmp:
        state_dir = Path(tmp) / "state"
        with mock.patch.object(pre_tool_use, "state_root", return_value=state_dir):
            with mock.patch.object(pre_tool_use, "load_config", return_value=config):
                output = pre_tool_use.handle_pre_tool_use(
                    {
                        "hook_event_name": "PreToolUse",
                        "cwd": tmp,
                        "session_id": "s1",
                        "tool_name": "Edit",
                        "tool_input": {"file_path": "src/payment/calc.ts"},
                    }
                )

    self.assertEqual(output, {"continue": True})
    state_file = next(state_dir.glob("*.json"))
    event = json.loads(state_file.read_text())["agentEvents"][0]
    self.assertEqual(event["kind"], "file_edit")
```

- [ ] **Step 3: Run hook test to verify failure**

Run:

```bash
python3 -m unittest memind-integrations/claude-code/tests/test_hooks.py
```

Expected: FAIL because `handle_pre_tool_use` and context injection are not implemented.

- [ ] **Step 4: Refactor Claude Code PreToolUse into a testable handler**

Replace `main()` body in `memind-integrations/claude-code/scripts/pre_tool_use.py` with a handler-based implementation:

```python
from lib.client import MemindClient
from lib.context_compiler import compile_tool_context
from lib.identity import project_slug, resolve_identity
from lib.tool_context import (
    current_turn_prompt,
    extract_tool_context_target,
    load_tool_context,
    should_query_tool_context,
)
```

Add:

```python
def handle_pre_tool_use(hook_input):
    config = load_config()
    session_id = hook_input.get("session_id") or "unknown-session"
    hook_input["source_client"] = config.get("sourceClient") or "claude-code"
    events = []
    event = None
    with SessionStateStore(state_root()).locked(session_id) as state:
        turn_id, turn_seq = state.ensure_agent_turn(session_id)
        seq = state.next_agent_seq()
        event = normalize_hook_event(hook_input, seq, turn_id=turn_id, turn_seq=turn_seq)
        state.append_agent_event(event)
        events = state.agent_events()

    cwd = hook_input.get("cwd")
    slug = project_slug(Path(cwd)) if cwd else None
    target = extract_tool_context_target(event, hook_input, slug)
    target["prompt"] = current_turn_prompt(events, target.get("turnId"))
    if not should_query_tool_context(target, config):
        return {"continue": True}

    identity = resolve_identity(config, hook_input)
    client = MemindClient(
        config["memindApiUrl"],
        config.get("memindApiToken"),
        timeout=2,
        max_retries=0,
    )
    context_input = load_tool_context(
        client,
        identity["userId"],
        identity["agentId"],
        target,
        config,
    )
    context = compile_tool_context(context_input, config)
    if not context:
        return {"continue": True}
    return {
        "hookSpecificOutput": {
            "hookEventName": "PreToolUse",
            "additionalContext": context,
        }
    }
```

Change `main()` to:

```python
def main():
    try:
        hook_input = json.loads(sys.stdin.read() or "{}")
        print(json.dumps(handle_pre_tool_use(hook_input)))
    except Exception as exc:
        try:
            debug_log(load_config(), "pre_tool_use_failed", {"error": str(exc)})
        except Exception:
            pass
        print(json.dumps({"continue": True}))
```

Add `from pathlib import Path` at the top.

- [ ] **Step 5: Run Claude Code hook tests and verify pass**

Run:

```bash
python3 -m unittest memind-integrations/claude-code/tests/test_hooks.py
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add \
  memind-integrations/claude-code/scripts/pre_tool_use.py \
  memind-integrations/claude-code/tests/test_hooks.py
git commit -m "feat(agent): inject Claude Code pre-tool context"
```

---

### Task 7: Wire Codex PreToolUse Injection

**Files:**
- Modify: `memind-integrations/codex/scripts/pre_tool_use.py`
- Modify: `memind-integrations/codex/tests/test_hooks.py`

- [ ] **Step 1: Add failing Codex hook injection test**

In `memind-integrations/codex/tests/test_hooks.py`, add a Codex-specific injection test:

```python
def test_pre_tool_use_injects_tool_context_when_memind_returns_matches(self):
    sys.path.insert(0, str(ROOT / "scripts"))
    import pre_tool_use
    from scripts.lib.state import SessionStateStore, state_key

    config = {
        "memindApiUrl": "http://127.0.0.1:8366",
        "memindApiToken": None,
        "sourceClient": "codex",
        "agentId": "coding-agent",
        "userId": "u",
        "autoRetrieve": True,
        "autoToolContext": True,
        "toolContextMaxItems": 6,
        "toolContextMinExactItems": 1,
        "toolContextMaxChars": 3500,
        "toolContextEntryMaxChars": 520,
        "retrieveStrategy": "SIMPLE",
    }

    with tempfile.TemporaryDirectory() as tmp:
        state_dir = Path(tmp) / "state"
        store = SessionStateStore(state_dir)
        session_key = state_key({"session_id": "s1"})
        with store.locked(session_key) as state:
            turn_id, turn_seq = state.start_agent_turn(session_key)
            state.append_agent_event(
                {
                    "eventId": "prompt",
                    "seq": 1,
                    "kind": "user_prompt",
                    "text": "Fix payment tests",
                    "metadata": {"turnId": turn_id, "turnSeq": turn_seq},
                }
            )

        class FakeClient:
            def query_items(self, **kwargs):
                return types.SimpleNamespace(
                    items=[
                        types.SimpleNamespace(
                            id="res-1",
                            text="rounding mismatch was resolved in src/payment/calc.ts and validated with npm test payment.",
                            category="resolution",
                            created_at="2026-05-27T10:00:00Z",
                            metadata={
                                "projectSlug": "tmp-project",
                                "files": ["src/payment/calc.ts"],
                            },
                        )
                    ]
                )

            def query_raw_data(self, **kwargs):
                return types.SimpleNamespace(raw_data=[])

            def retrieve(self, *args, **kwargs):
                return types.SimpleNamespace(items=[], insights=[], raw_data=[])

        with mock.patch.object(pre_tool_use, "state_root", return_value=state_dir):
            with mock.patch.object(pre_tool_use, "load_config", return_value=config):
                with mock.patch.object(pre_tool_use, "resolve_identity", return_value={"userId": "u", "agentId": "coding-agent"}):
                    with mock.patch.object(pre_tool_use, "MemindClient", return_value=FakeClient()):
                        output = pre_tool_use.handle_pre_tool_use(
                            {
                                "hook_event_name": "PreToolUse",
                                "cwd": tmp,
                                "session_id": "s1",
                                "tool_name": "Edit",
                                "tool_input": {"file_path": "src/payment/calc.ts"},
                                "timestamp": "2026-05-28T10:00:00Z",
                            }
                        )

    self.assertIn("hookSpecificOutput", output)
    context = output["hookSpecificOutput"]["additionalContext"]
    self.assertIn("<memind_tool_context", context)
    self.assertIn("## Prior Resolutions", context)
    self.assertIn("rounding mismatch", context)
```

- [ ] **Step 2: Add failing Codex disabled/fail-open test**

Add this disabled/fail-open test to `memind-integrations/codex/tests/test_hooks.py`:

```python
def test_pre_tool_use_context_disabled_still_buffers_event(self):
    sys.path.insert(0, str(ROOT / "scripts"))
    import pre_tool_use

    config = {
        "sourceClient": "codex",
        "autoRetrieve": True,
        "autoToolContext": False,
    }

    with tempfile.TemporaryDirectory() as tmp:
        state_dir = Path(tmp) / "state"
        with mock.patch.object(pre_tool_use, "state_root", return_value=state_dir):
            with mock.patch.object(pre_tool_use, "load_config", return_value=config):
                output = pre_tool_use.handle_pre_tool_use(
                    {
                        "hook_event_name": "PreToolUse",
                        "cwd": tmp,
                        "session_id": "s1",
                        "tool_name": "Edit",
                        "tool_input": {"file_path": "src/payment/calc.ts"},
                    }
                )

    self.assertEqual(output, {"continue": True})
    state_file = next(state_dir.glob("*.json"))
    event = json.loads(state_file.read_text())["agentEvents"][0]
    self.assertEqual(event["kind"], "file_edit")
```

- [ ] **Step 3: Run Codex hook tests to verify failure**

Run:

```bash
/opt/homebrew/bin/python3.12 -m unittest memind-integrations/codex/tests/test_hooks.py
```

Expected: FAIL because Codex `pre_tool_use.py` has not been wired.

- [ ] **Step 4: Refactor Codex PreToolUse into a testable handler**

In `memind-integrations/codex/scripts/pre_tool_use.py`, add these imports:

```python
from pathlib import Path
from lib.client import MemindClient
from lib.context_compiler import compile_tool_context
from lib.identity import project_slug, resolve_identity
from lib.state import SessionStateStore, state_key
from lib.tool_context import (
    current_turn_prompt,
    extract_tool_context_target,
    load_tool_context,
    should_query_tool_context,
)
```

Handler body:

```python
def handle_pre_tool_use(hook_input):
    config = load_config()
    hook_input["source_client"] = config.get("sourceClient") or "codex"
    session_key = state_key(hook_input)
    with SessionStateStore(state_root()).locked(session_key) as state:
        turn_id, turn_seq = state.ensure_agent_turn(session_key)
        seq = state.next_agent_seq()
        event = normalize_hook_event(hook_input, seq, turn_id=turn_id, turn_seq=turn_seq)
        state.append_agent_event(event)
        events = state.agent_events()

    cwd = hook_input.get("cwd")
    slug = project_slug(Path(cwd)) if cwd else None
    target = extract_tool_context_target(event, hook_input, slug)
    target["prompt"] = current_turn_prompt(events, target.get("turnId"))
    if not should_query_tool_context(target, config):
        return {"continue": True}

    identity = resolve_identity(config, hook_input)
    client = MemindClient(
        config["memindApiUrl"],
        config.get("memindApiToken"),
        timeout=2,
        max_retries=0,
    )
    context_input = load_tool_context(
        client,
        identity["userId"],
        identity["agentId"],
        target,
        config,
    )
    context = compile_tool_context(context_input, config)
    if not context:
        return {"continue": True}
    return {
        "hookSpecificOutput": {
            "hookEventName": "PreToolUse",
            "additionalContext": context,
        }
    }
```

Change `main()` to fail open and return `{"continue": True}` on unexpected errors:

```python
def main():
    try:
        hook_input = json.loads(sys.stdin.read() or "{}")
        print(json.dumps(handle_pre_tool_use(hook_input)))
    except Exception as exc:
        try:
            debug_log(load_config(), "pre_tool_use_failed", {"error": str(exc)})
        except Exception:
            pass
        print(json.dumps({"continue": True}))
```

- [ ] **Step 5: Run Codex hook tests and verify pass**

Run:

```bash
/opt/homebrew/bin/python3.12 -m unittest memind-integrations/codex/tests/test_hooks.py
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add \
  memind-integrations/codex/scripts/pre_tool_use.py \
  memind-integrations/codex/tests/test_hooks.py
git commit -m "feat(agent): inject Codex pre-tool context"
```

---

### Task 8: Update Installers And Packaging File Lists

**Files:**
- Modify: `memind-integrations/claude-code/install.sh`
- Modify: `memind-integrations/claude-code/tests/test_installer.py`
- Modify: `memind-integrations/codex/install.sh`
- Modify: `memind-integrations/codex/tests/test_installer.py`

- [ ] **Step 1: Add failing installer file-list assertions**

In both installer tests, add assertions that the install file list includes:

```python
self.assertIn('"scripts/lib/tool_context.py"', install_script)
```

Use the existing test that already checks `context_compiler.py`.

- [ ] **Step 2: Run installer tests to verify failure**

Run:

```bash
python3 -m unittest memind-integrations/claude-code/tests/test_installer.py
/opt/homebrew/bin/python3.12 -m unittest memind-integrations/codex/tests/test_installer.py
```

Expected: FAIL because `tool_context.py` is not listed in the installer scripts yet.

- [ ] **Step 3: Add `tool_context.py` to install file lists**

In both install scripts, add:

```bash
"scripts/lib/tool_context.py"
```

Place it next to:

```bash
"scripts/lib/context_compiler.py"
```

- [ ] **Step 4: Run installer tests and verify pass**

Run:

```bash
python3 -m unittest memind-integrations/claude-code/tests/test_installer.py
/opt/homebrew/bin/python3.12 -m unittest memind-integrations/codex/tests/test_installer.py
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add \
  memind-integrations/claude-code/install.sh \
  memind-integrations/claude-code/tests/test_installer.py \
  memind-integrations/codex/install.sh \
  memind-integrations/codex/tests/test_installer.py
git commit -m "chore(agent): install pre-tool context helpers"
```

---

### Task 9: Documentation

**Files:**
- Modify: `memind-integrations/claude-code/README.md`
- Modify: `memind-integrations/codex/README.md`
- Modify: `docs/superpowers/specs/2026-05-24-rawdata-agent-design.md`

- [ ] **Step 1: Update Claude Code README**

In the hook table, change `PreToolUse` description to:

```markdown
| `PreToolUse` | `scripts/pre_tool_use.py` | 5s | Buffer a redacted tool-start event and, for high-value file edits or commands, inject compact file/tool memory context. |
```

Update the async note to say:

```markdown
`PostToolUse`, `Stop`, `Notification`, and `SubagentStop` are configured as async so regular turn execution is not
blocked by ingestion work. `PreToolUse` is intentionally synchronous because it may inject a small context block before
the tool executes; it still fails open and skips retrieval for low-value tools.
```

Add a new section after retrieval behavior:

````markdown
### PreToolUse Context

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
not submit duplicate `tool_call` raw data.
````

Document settings:

```markdown
| `autoToolContext` | `true` | Enable compact PreToolUse context for high-value file edits and commands. |
| `toolContextMaxChars` | `3500` | Maximum injected PreToolUse context characters. |
| `toolContextEntryMaxChars` | `520` | Maximum characters per PreToolUse context entry. |
| `toolContextMaxItems` | `6` | Maximum exact or fallback items considered for PreToolUse context. |
| `toolContextMinExactItems` | `2` | Minimum exact item hits before semantic retrieve fallback is skipped. |
```

- [ ] **Step 2: Update Codex README**

In the Codex hook table, change `PreToolUse` description to:

```markdown
| `PreToolUse` | `scripts/pre_tool_use.py` | 5s | Buffer a redacted tool-start event and, for high-value file edits or commands, inject compact file/tool memory context. |
```

Add or update the synchronous hook note:

```markdown
`PreToolUse` is intentionally synchronous because it may inject a small context block before the tool executes. The hook
fails open and skips retrieval for low-value tools. `PostToolUse` and `Stop` keep their existing ingestion behavior.
```

Add this section after the Codex retrieval behavior section:

````markdown
### PreToolUse Context

For high-value tools such as `Edit`, `Write`, `MultiEdit`, and validation shell commands, Memind may inject a compact
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
not submit duplicate `tool_call` raw data.
````

Document the Codex settings:

```markdown
| `autoToolContext` | `true` | Enable compact PreToolUse context for high-value file edits and commands. |
| `toolContextMaxChars` | `3500` | Maximum injected PreToolUse context characters. |
| `toolContextEntryMaxChars` | `520` | Maximum characters per PreToolUse context entry. |
| `toolContextMaxItems` | `6` | Maximum exact or fallback items considered for PreToolUse context. |
| `toolContextMinExactItems` | `2` | Minimum exact item hits before semantic retrieve fallback is skipped. |
```

- [ ] **Step 3: Update the rawdata-agent design spec**

In `docs/superpowers/specs/2026-05-24-rawdata-agent-design.md`, add a short section:

```markdown
### PreToolUse Context

`rawdata-agent` stores enough deterministic file/tool metadata to support a retrieval-time PreToolUse context compiler.
The compiler should use existing `tool`, `resolution`, `playbook`, `directive`, and `agent_episode` data. It must not
change the rawdata storage model, must not run `rawdata-toolcall` extraction, and must not add per-tool LLM calls.
```

- [ ] **Step 4: Commit**

```bash
git add \
  memind-integrations/claude-code/README.md \
  memind-integrations/codex/README.md \
  docs/superpowers/specs/2026-05-24-rawdata-agent-design.md
git commit -m "docs(agent): describe pre-tool context"
```

---

### Task 10: Full Verification

**Files:**
- No source changes unless verification exposes a required fix.

- [ ] **Step 1: Run Claude Code integration tests**

Run:

```bash
python3 -m unittest discover -s memind-integrations/claude-code/tests
```

Expected: PASS.

- [ ] **Step 2: Run Codex integration tests**

Run:

```bash
/opt/homebrew/bin/python3.12 -m unittest discover -s memind-integrations/codex/tests
```

Expected: PASS.

- [ ] **Step 3: Run focused timeline tests**

Run:

```bash
python3 -m unittest \
  memind-integrations/claude-code/tests/test_agent_timeline.py \
  memind-integrations/codex/tests/test_agent_timeline.py
```

Expected: PASS. This confirms the PreToolUse event-buffering behavior still works with the existing `agent_timeline` flow.

- [ ] **Step 4: Check whitespace**

Run:

```bash
git diff --check
```

Expected: no output.

- [ ] **Step 5: Inspect hook manifest diff**

Run:

```bash
git diff -- memind-integrations/claude-code/hooks/hooks.json memind-integrations/codex/hooks/hooks.json
```

Expected:

- Claude Code `PreToolUse` no longer has `"async": true`.
- Claude Code `PostToolUse`, `Stop`, `Notification`, and `SubagentStop` still have `"async": true`.
- Codex remains synchronous and still has only `SessionStart`, `UserPromptSubmit`, `PreToolUse`, `PostToolUse`, and `Stop`.

- [ ] **Step 6: Inspect rawdata-agent and rawdata-toolcall diffs**

Run:

```bash
git diff -- memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-toolcall
```

Expected: no Java plugin changes for this plan.

- [ ] **Step 7: Commit verification-only fixes if needed**

If formatting or docs fixes were required:

```bash
git add <fixed-files>
git commit -m "chore(agent): finalize pre-tool context"
```

- [ ] **Step 8: Push branch**

Run:

```bash
git push origin feat/rawdata-agent-memory
```

Expected: branch pushed successfully.

---

## Acceptance Checklist

- [ ] Claude Code and Codex still buffer PreToolUse events into the same local `agent_timeline` state.
- [ ] Claude Code PreToolUse is synchronous only because it may inject context.
- [ ] PostToolUse/Stop ingestion paths remain async where they were async before.
- [ ] PreToolUse skips low-value read/search/list tools.
- [ ] PreToolUse fails open when Memind is unavailable.
- [ ] PreToolUse exact retrieval uses top-level metadata fields: `projectSlug`, `files`, `commands`, `toolNames`.
- [ ] PreToolUse fallback retrieval is bounded and category-filtered.
- [ ] The injected context is wrapped in `<memind_tool_context>`.
- [ ] Injected context defaults to about 500-800 tokens and is hard-capped by `toolContextMaxChars`.
- [ ] Injected context does not render raw `durationMs`, `inputTokens`, or `outputTokens`.
- [ ] No rawdata-agent storage changes are required.
- [ ] No rawdata-toolcall changes are required.
- [ ] No new LLM calls are added.
- [ ] Existing UserPromptSubmit and SessionStart context compilers keep their current behavior.

## Self-Review Notes

- Spec coverage: The plan implements PreToolUse context from existing storage, exact metadata retrieval, semantic fallback, context compilation, Claude Code/Codex wiring, settings, docs, installers, and verification.
- Placeholder scan: No TODO/TBD placeholders are present. Code snippets define the functions and assertions needed by later steps.
- Type consistency: The plan consistently uses `autoToolContext`, `toolContextMaxChars`, `toolContextEntryMaxChars`, `toolContextMaxItems`, `toolContextMinExactItems`, `compile_tool_context`, `load_tool_context`, and `<memind_tool_context>`.
- Scope check: This is a retrieval-time integration feature. It deliberately avoids Memind core schema/API changes and rawdata-agent storage changes.
