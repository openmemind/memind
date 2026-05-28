# RawData Agent ToolCall Telemetry Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Absorb the useful deterministic parts of `rawdata-toolcall` into `rawdata-agent` so Claude Code and Codex timelines produce richer file/tool-aware evidence without adding extra LLM calls or duplicating raw data paths.

**Architecture:** Claude Code and Codex continue to ingest only `agent_timeline` raw data. Tool-call telemetry is normalized at hook time, preserved as typed `AgentEvent` fields, aggregated into agent episode segment metadata, and used to improve deterministic `tool` items. `rawdata-toolcall` remains a separate compatibility plugin for pure tool logs.

**Tech Stack:** Python integration hooks, Java 21 records, JUnit 5, AssertJ, Maven, Memind rawdata plugin architecture.

---

## Scope And Non-Goals

This plan implements only deterministic tool telemetry absorption into `rawdata-agent`.

In scope:

- Capture `durationMs`, `inputTokens`, `outputTokens`, and `contentHash` in Claude Code and Codex normalized tool events when hook payloads expose them.
- Use redacted/canonicalized semantic tool input and output to compute `contentHash`; exclude volatile telemetry fields such as duration and token usage from the hash.
- Add `inputTokens`, `outputTokens`, and `contentHash` to `AgentEvent`; reuse existing `occurredAt` as the tool call timestamp and existing `durationMs` as duration.
- Add capped `toolRecords`, `toolStats`, and `toolGroups` metadata to formatted `agent_episode` segments.
- Copy useful tool telemetry metadata into deterministic `category=tool` and `category=resolution` memory items.
- Keep the existing `agent_timeline` caption and item extraction LLM flow unchanged.

Out of scope:

- No `rawdata-toolcall` LLM extraction inside `rawdata-agent`.
- No `ToolCallChunker` by-tool segmentation for `agent_timeline`.
- No Claude Code/Codex double-ingestion into both `rawdata-agent` and `rawdata-toolcall`.
- No PreToolUse file context query/injection implementation in this plan.
- No OpenAPI metadata filter changes in this plan.

## File Map

- Modify `memind-integrations/claude-code/scripts/lib/agent_timeline.py`
  - Extract optional telemetry fields from hook payloads.
  - Compute `contentHash` from redacted canonical tool content.
  - Keep redaction before hashing.

- Modify `memind-integrations/codex/scripts/lib/agent_timeline.py`
  - Mirror Claude Code normalization behavior.

- Modify `memind-integrations/claude-code/tests/test_agent_timeline.py`
  - Cover telemetry normalization and secret-safe hash behavior.

- Modify `memind-integrations/codex/tests/test_agent_timeline.py`
  - Mirror Claude Code tests with Codex source client.

- Modify `memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent/src/main/java/com/openmemind/ai/memory/plugin/rawdata/agent/model/AgentEvent.java`
  - Add typed `inputTokens`, `outputTokens`, and `contentHash` fields.

- Modify `memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent/src/main/java/com/openmemind/ai/memory/plugin/rawdata/agent/content/AgentTimelineContent.java`
  - Include new typed telemetry fields in canonical event identity.

- Modify `memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent/src/main/java/com/openmemind/ai/memory/plugin/rawdata/agent/privacy/AgentEventRedactor.java`
  - Preserve the new typed fields after redaction.

- Create `memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent/src/main/java/com/openmemind/ai/memory/plugin/rawdata/agent/chunk/AgentToolTelemetry.java`
  - Build capped `toolRecords`, `toolStats`, and `toolGroups` metadata from episode events.

- Modify `memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent/src/main/java/com/openmemind/ai/memory/plugin/rawdata/agent/chunk/AgentSegmentFormatter.java`
  - Add telemetry metadata to agent episode segments.

- Modify `memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent/src/main/java/com/openmemind/ai/memory/plugin/rawdata/agent/item/AgentMemoryItemFactory.java`
  - Copy telemetry metadata into deterministic item metadata.
  - Improve deterministic tool item text when validation failed and then passed.

- Modify Java tests under `memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent/src/test/java/...`
  - Add serialization, redaction, formatter, and deterministic item tests.

- Modify `memind-integrations/claude-code/README.md` and `memind-integrations/codex/README.md`
  - Document the strengthened `rawdata-agent` tool telemetry path and clarify that `rawdata-toolcall` remains pure-tool-log compatibility.

---

### Task 1: Add Hook Telemetry Tests For Claude Code And Codex

**Files:**
- Modify: `memind-integrations/claude-code/tests/test_agent_timeline.py`
- Modify: `memind-integrations/codex/tests/test_agent_timeline.py`

- [ ] **Step 1: Add a Claude Code telemetry normalization test**

Add this test method to `AgentTimelineTest` in `memind-integrations/claude-code/tests/test_agent_timeline.py`:

```python
def test_normalizes_tool_telemetry_and_content_hash(self):
    event = normalize_hook_event(
        {
            "hook_event_name": "PostToolUse",
            "session_id": "s",
            "tool_name": "Bash",
            "tool_input": {"command": "npm test payment"},
            "tool_response": {
                "exit_code": 0,
                "stdout": "passed",
                "duration_ms": 1234,
                "usage": {"input_tokens": 11, "output_tokens": 22},
            },
            "timestamp": "2026-05-24T10:00:00Z",
        },
        seq=1,
    )

    self.assertEqual(event["durationMs"], 1234)
    self.assertEqual(event["inputTokens"], 11)
    self.assertEqual(event["outputTokens"], 22)
    self.assertTrue(event["contentHash"].startswith("sha256:"))
    self.assertEqual(len(event["contentHash"]), len("sha256:") + 64)
    self.assertEqual(event["output"], '{"stdout": "passed"}')
    self.assertEqual(event["metadata"]["normalizationVersion"], 1)
```

- [ ] **Step 2: Add a Claude Code secret-safe hash test**

Add this test method to the same class:

```python
def test_content_hash_uses_redacted_payload(self):
    first = normalize_hook_event(
        {
            "hook_event_name": "PostToolUse",
            "session_id": "s",
            "tool_name": "CustomTool",
            "tool_input": {"token": "Bearer first-secret-value"},
            "tool_response": {"result": "ok"},
            "timestamp": "2026-05-24T10:00:00Z",
        },
        seq=1,
    )
    second = normalize_hook_event(
        {
            "hook_event_name": "PostToolUse",
            "session_id": "s",
            "tool_name": "CustomTool",
            "tool_input": {"token": "Bearer second-secret-value"},
            "tool_response": {"result": "ok"},
            "timestamp": "2026-05-24T10:00:01Z",
        },
        seq=2,
    )

    self.assertEqual(first["contentHash"], second["contentHash"])
    self.assertIn("[REDACTED:bearer_token]", first["input"])
    self.assertIn("[REDACTED:bearer_token]", second["input"])
```

- [ ] **Step 3: Add a Claude Code telemetry-stable hash test**

Add this test method to the same class:

```python
def test_content_hash_ignores_volatile_telemetry_fields(self):
    first = normalize_hook_event(
        {
            "hook_event_name": "PostToolUse",
            "session_id": "s",
            "tool_name": "Bash",
            "tool_input": {"command": "npm test payment"},
            "tool_response": {
                "exit_code": 0,
                "stdout": "passed",
                "duration_ms": 100,
                "metadata": {"duration_ms": 100},
                "usage": {"input_tokens": 11, "output_tokens": 22},
            },
            "timestamp": "2026-05-24T10:00:00Z",
        },
        seq=1,
    )
    second = normalize_hook_event(
        {
            "hook_event_name": "PostToolUse",
            "session_id": "s",
            "tool_name": "Bash",
            "tool_input": {"command": "npm test payment"},
            "tool_response": {
                "exit_code": 0,
                "stdout": "passed",
                "duration_ms": 999,
                "metadata": {"duration_ms": 999},
                "usage": {"input_tokens": 100, "output_tokens": 200},
            },
            "timestamp": "2026-05-24T10:00:01Z",
        },
        seq=2,
    )

    self.assertEqual(first["contentHash"], second["contentHash"])
    self.assertNotIn("duration_ms", first.get("output", ""))
    self.assertNotIn("metadata", first.get("output", ""))
    self.assertNotIn("usage", first.get("output", ""))
```

- [ ] **Step 4: Add equivalent Codex tests**

Add the same three tests to `memind-integrations/codex/tests/test_agent_timeline.py`, with `"source_client": "codex"` in each hook input. Assert `event["metadata"]["sourceClient"] == "codex"` in the first test.

- [ ] **Step 5: Run Python tests and verify failure**

Run:

```bash
python3 -m unittest memind-integrations/claude-code/tests/test_agent_timeline.py memind-integrations/codex/tests/test_agent_timeline.py
```

Expected: FAIL because `durationMs`, `inputTokens`, `outputTokens`, or `contentHash` are not emitted yet.

---

### Task 2: Implement Hook Telemetry Normalization

**Files:**
- Modify: `memind-integrations/claude-code/scripts/lib/agent_timeline.py`
- Modify: `memind-integrations/codex/scripts/lib/agent_timeline.py`

- [ ] **Step 1: Add telemetry helpers to Claude Code normalizer**

Add these helpers near `_json_text` in `memind-integrations/claude-code/scripts/lib/agent_timeline.py`:

```python
def _number_value(*values):
    for value in values:
        if isinstance(value, bool):
            continue
        if isinstance(value, int):
            return value
        if isinstance(value, float):
            return int(value)
        if isinstance(value, str) and value.strip().isdigit():
            return int(value.strip())
    return None


def _nested_number(mapping, *path):
    current = mapping
    for key in path:
        if not isinstance(current, dict):
            return None
        current = current.get(key)
    return _number_value(current)


def _tool_telemetry(hook_input, tool_response):
    usage = tool_response.get("usage") if isinstance(tool_response, dict) else {}
    return {
        "durationMs": _number_value(
            hook_input.get("duration_ms"),
            hook_input.get("durationMs"),
            tool_response.get("duration_ms") if isinstance(tool_response, dict) else None,
            tool_response.get("durationMs") if isinstance(tool_response, dict) else None,
            _nested_number(tool_response, "metadata", "duration_ms"),
            _nested_number(tool_response, "metadata", "durationMs"),
        ),
        "inputTokens": _number_value(
            hook_input.get("input_tokens"),
            hook_input.get("inputTokens"),
            tool_response.get("input_tokens") if isinstance(tool_response, dict) else None,
            tool_response.get("inputTokens") if isinstance(tool_response, dict) else None,
            usage.get("input_tokens") if isinstance(usage, dict) else None,
            usage.get("inputTokens") if isinstance(usage, dict) else None,
        ),
        "outputTokens": _number_value(
            hook_input.get("output_tokens"),
            hook_input.get("outputTokens"),
            tool_response.get("output_tokens") if isinstance(tool_response, dict) else None,
            tool_response.get("outputTokens") if isinstance(tool_response, dict) else None,
            usage.get("output_tokens") if isinstance(usage, dict) else None,
            usage.get("outputTokens") if isinstance(usage, dict) else None,
        ),
    }


VOLATILE_TOOL_OUTPUT_KEYS = {
    "exit_code",
    "exitCode",
    "duration_ms",
    "durationMs",
    "input_tokens",
    "inputTokens",
    "output_tokens",
    "outputTokens",
    "usage",
}


def _semantic_tool_output(raw_tool_response):
    if isinstance(raw_tool_response, list):
        return [_semantic_tool_output(value) for value in raw_tool_response]
    if not isinstance(raw_tool_response, dict):
        return raw_tool_response
    result = {}
    for key, value in raw_tool_response.items():
        if key in VOLATILE_TOOL_OUTPUT_KEYS or value is None:
            continue
        normalized = _semantic_tool_output(value)
        if normalized not in (None, {}, []):
            result[key] = normalized
    return result


def _content_hash(tool_name, normalized_input, normalized_output):
    stable = json.dumps(
        {
            "toolName": tool_name or "",
            "input": normalized_input or "",
            "output": normalized_output or "",
        },
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
    )
    return "sha256:" + hashlib.sha256(stable.encode("utf-8")).hexdigest()
```

- [ ] **Step 2: Emit telemetry in `normalize_hook_event`**

In `normalize_hook_event`, use `_semantic_tool_output(raw_tool_response)` when building `output` so typed telemetry does not duplicate into output text or destabilize `contentHash`:

```python
    output = _semantic_tool_output(raw_tool_response)
```

After redacted input/output values are computed and before metadata is assigned, set top-level telemetry fields:

```python
    telemetry = _tool_telemetry(hook_input, tool_response)
    if telemetry.get("durationMs") is not None:
        event["durationMs"] = telemetry["durationMs"]
    if telemetry.get("inputTokens") is not None:
        event["inputTokens"] = telemetry["inputTokens"]
    if telemetry.get("outputTokens") is not None:
        event["outputTokens"] = telemetry["outputTokens"]
    event["contentHash"] = _content_hash(
        tool_name,
        event.get("command") if event.get("command") is not None else event.get("input"),
        event.get("output"),
    )
```

Keep `contentHash` based on redacted values already stored on `event`; do not hash raw input/output.

- [ ] **Step 3: Mirror the same changes in Codex normalizer**

Apply the same helper methods and `normalize_hook_event` update to `memind-integrations/codex/scripts/lib/agent_timeline.py`.

- [ ] **Step 4: Run Python tests and verify pass**

Run:

```bash
python3 -m unittest memind-integrations/claude-code/tests/test_agent_timeline.py memind-integrations/codex/tests/test_agent_timeline.py
```

Expected: PASS.

- [ ] **Step 5: Commit**

Run:

```bash
git add memind-integrations/claude-code/scripts/lib/agent_timeline.py memind-integrations/codex/scripts/lib/agent_timeline.py memind-integrations/claude-code/tests/test_agent_timeline.py memind-integrations/codex/tests/test_agent_timeline.py
git commit -m "feat(agent): capture tool telemetry in timeline hooks"
```

---

### Task 3: Add AgentEvent Typed Telemetry Fields

**Files:**
- Modify: `memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent/src/main/java/com/openmemind/ai/memory/plugin/rawdata/agent/model/AgentEvent.java`
- Modify: `memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent/src/main/java/com/openmemind/ai/memory/plugin/rawdata/agent/content/AgentTimelineContent.java`
- Modify: `memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent/src/main/java/com/openmemind/ai/memory/plugin/rawdata/agent/privacy/AgentEventRedactor.java`
- Modify Java tests constructing `new AgentEvent(...)`
- Test: `memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent/src/test/java/com/openmemind/ai/memory/plugin/rawdata/agent/content/AgentTimelineContentTest.java`
- Test: `memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent/src/test/java/com/openmemind/ai/memory/plugin/rawdata/agent/privacy/AgentEventRedactorTest.java`

- [ ] **Step 1: Add a failing Jackson preservation test**

Add this test to `AgentTimelineContentTest`:

```java
@Test
void jacksonShouldPreserveToolTelemetryFields() throws Exception {
    String json =
            """
            {
              "type": "agent_timeline",
              "sourceClient": "claude-code",
              "sessionId": "session-1",
              "agentTurnId": "turn-1",
              "timelineId": "timeline-1",
              "events": [
                {
                  "eventId": "event-tool",
                  "seq": 1,
                  "kind": "command",
                  "toolName": "Bash",
                  "command": "npm test payment",
                  "status": "success",
                  "durationMs": 1234,
                  "inputTokens": 11,
                  "outputTokens": 22,
                  "contentHash": "sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
                }
              ]
            }
            """;

    RawContent decoded = OBJECT_MAPPER.readValue(json, RawContent.class);

    AgentEvent event = ((AgentTimelineContent) decoded).events().getFirst();
    assertThat(event.durationMs()).isEqualTo(1234L);
    assertThat(event.inputTokens()).isEqualTo(11);
    assertThat(event.outputTokens()).isEqualTo(22);
    assertThat(event.contentHash())
            .isEqualTo("sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
}
```

- [ ] **Step 2: Add a failing canonical identity test**

Add this test to `AgentTimelineContentTest`:

```java
@Test
void contentIdShouldIncludeTypedToolTelemetryFields() {
    AgentProject project = new AgentProject("payment-service", "/repo/payment", null, Map.of());
    AgentEvent first =
            new AgentEvent(
                    "e1",
                    1,
                    AgentEventKind.COMMAND,
                    Instant.parse("2026-05-24T10:00:00Z"),
                    null,
                    "Bash",
                    null,
                    "passed",
                    AgentEventStatus.SUCCESS,
                    1234L,
                    11,
                    22,
                    "sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                    null,
                    "run",
                    "npm test payment",
                    0,
                    Map.of());
    AgentEvent second =
            new AgentEvent(
                    "e1",
                    1,
                    AgentEventKind.COMMAND,
                    Instant.parse("2026-05-24T10:00:00Z"),
                    null,
                    "Bash",
                    null,
                    "passed",
                    AgentEventStatus.SUCCESS,
                    1234L,
                    11,
                    23,
                    "sha256:fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210",
                    null,
                    "run",
                    "npm test payment",
                    0,
                    Map.of());

    AgentTimelineContent firstContent =
            new AgentTimelineContent(
                    "claude-code",
                    "1.0",
                    "session-123",
                    "turn-1",
                    "timeline-1",
                    project,
                    List.of(first));
    AgentTimelineContent secondContent =
            new AgentTimelineContent(
                    "claude-code",
                    "1.0",
                    "session-123",
                    "turn-1",
                    "timeline-1",
                    project,
                    List.of(second));

    assertThat(firstContent.getContentId()).isNotEqualTo(secondContent.getContentId());
}
```

- [ ] **Step 3: Run the Java test and verify failure**

Run:

```bash
mvn -pl memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent -Dtest=AgentTimelineContentTest test
```

Expected: FAIL because `inputTokens`, `outputTokens`, and `contentHash` are not typed fields yet.

- [ ] **Step 4: Update `AgentEvent` record**

Change `AgentEvent` to include the new fields immediately after `durationMs`:

```java
public record AgentEvent(
        String eventId,
        Integer seq,
        AgentEventKind kind,
        Instant occurredAt,
        String text,
        String toolName,
        String input,
        String output,
        AgentEventStatus status,
        Long durationMs,
        Integer inputTokens,
        Integer outputTokens,
        String contentHash,
        String path,
        String operation,
        String command,
        Integer exitCode,
        Map<String, Object> metadata) {
```

- [ ] **Step 5: Include telemetry fields in canonical event identity**

In `AgentTimelineContent.canonicalEvent`, include the new fields immediately after `durationMs`:

```java
                normalized(event.durationMs()),
                normalized(event.inputTokens()),
                normalized(event.outputTokens()),
                normalized(event.contentHash()),
                normalized(event.path()),
```

- [ ] **Step 6: Preserve fields in `AgentEventRedactor`**

Update the `new AgentEvent(...)` call in `AgentEventRedactor.redact` to pass the new fields:

```java
                event.durationMs(),
                event.inputTokens(),
                event.outputTokens(),
                event.contentHash(),
                event.path(),
```

- [ ] **Step 7: Update all test constructors**

Every existing `new AgentEvent(...)` call that already passes `durationMs` must insert the three new fields immediately after that `durationMs` value:

```java
null,
null,
null,
```

For telemetry-specific constructors, keep the existing duration value and then pass concrete telemetry values:

```java
1234L,
11,
22,
"sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
```

- [ ] **Step 8: Run Java tests and verify pass**

Run:

```bash
mvn -pl memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent -Dtest=AgentTimelineContentTest,AgentEventRedactorTest test
```

Expected: PASS.

- [ ] **Step 9: Commit**

Run:

```bash
git add memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent/src/main/java/com/openmemind/ai/memory/plugin/rawdata/agent/model/AgentEvent.java memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent/src/main/java/com/openmemind/ai/memory/plugin/rawdata/agent/content/AgentTimelineContent.java memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent/src/main/java/com/openmemind/ai/memory/plugin/rawdata/agent/privacy/AgentEventRedactor.java memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent/src/test/java/com/openmemind/ai/memory/plugin/rawdata/agent
git commit -m "feat(agent): add typed tool telemetry fields"
```

---

### Task 4: Add Deterministic Tool Telemetry Aggregation

**Files:**
- Create: `memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent/src/main/java/com/openmemind/ai/memory/plugin/rawdata/agent/chunk/AgentToolTelemetry.java`
- Modify: `memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent/src/main/java/com/openmemind/ai/memory/plugin/rawdata/agent/chunk/AgentSegmentFormatter.java`
- Test: `memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent/src/test/java/com/openmemind/ai/memory/plugin/rawdata/agent/chunk/AgentSegmentFormatterTest.java`

- [ ] **Step 1: Add a failing segment metadata test**

Extend `shouldFormatEpisodeTextAndMetadataDeterministically` in `AgentSegmentFormatterTest` with assertions:

```java
assertThat(formatted.metadata().get("toolRecords"))
        .asList()
        .contains(
                Map.of(
                        "eventId",
                        "e2",
                        "seq",
                        2,
                        "toolName",
                        "Bash",
                        "kind",
                        "command",
                        "status",
                        "failed",
                        "durationMs",
                        10L,
                        "command",
                        "npm test payment",
                        "outputPreview",
                        "rounding mismatch"));
assertThat(formatted.metadata().get("toolStats"))
        .isEqualTo(
                Map.of(
                        "Bash",
                        Map.of(
                                "callCount",
                                2,
                                "successCount",
                                1,
                                "failCount",
                                1,
                                "avgDurationMs",
                                10L),
                        "Edit",
                        Map.of(
                                "callCount",
                                1,
                                "successCount",
                                1,
                                "failCount",
                                0,
                                "avgDurationMs",
                                10L)));
assertThat(formatted.metadata().get("toolGroups"))
        .asList()
        .contains(
                Map.of(
                        "toolName",
                        "Bash",
                        "callCount",
                        2,
                        "successCount",
                        1,
                        "failCount",
                        1,
                        "commands",
                        List.of("npm test payment")));
```

- [ ] **Step 2: Run formatter test and verify failure**

Run:

```bash
mvn -pl memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent -Dtest=AgentSegmentFormatterTest test
```

Expected: FAIL because `toolRecords`, `toolStats`, and `toolGroups` are absent.

- [ ] **Step 3: Create `AgentToolTelemetry`**

Create `AgentToolTelemetry.java`:

```java
package com.openmemind.ai.memory.plugin.rawdata.agent.chunk;

import com.openmemind.ai.memory.plugin.rawdata.agent.model.AgentEvent;
import com.openmemind.ai.memory.plugin.rawdata.agent.model.AgentEventStatus;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

final class AgentToolTelemetry {

    private static final int MAX_TOOL_RECORDS = 40;
    private static final int MAX_TOOL_STATS = 20;
    private static final int MAX_TOOL_GROUPS = 20;
    private static final int MAX_GROUP_VALUES = 20;
    private static final int MAX_OUTPUT_PREVIEW_CHARS = 240;

    private AgentToolTelemetry() {}

    static Map<String, Object> metadata(List<AgentEvent> events) {
        List<AgentEvent> toolEvents =
                events == null
                        ? List.of()
                        : events.stream().filter(AgentToolTelemetry::hasToolEvidence).toList();
        if (toolEvents.isEmpty()) {
            return Map.of();
        }
        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("toolRecords", toolRecords(toolEvents));
        metadata.put("toolStats", toolStats(toolEvents));
        metadata.put("toolGroups", toolGroups(toolEvents));
        return Map.copyOf(metadata);
    }

    private static boolean hasToolEvidence(AgentEvent event) {
        return event != null && event.toolName() != null && !event.toolName().isBlank();
    }

    private static List<Map<String, Object>> toolRecords(List<AgentEvent> events) {
        var records = new ArrayList<Map<String, Object>>();
        for (AgentEvent event : events.stream().limit(MAX_TOOL_RECORDS).toList()) {
            var record = new LinkedHashMap<String, Object>();
            put(record, "eventId", event.eventId());
            put(record, "seq", event.seq());
            put(record, "toolName", event.toolName());
            put(record, "kind", event.kind() == null ? null : event.kind().wireValue());
            put(record, "status", event.status() == null ? null : event.status().wireValue());
            put(record, "durationMs", event.durationMs());
            put(record, "inputTokens", event.inputTokens());
            put(record, "outputTokens", event.outputTokens());
            put(record, "contentHash", event.contentHash());
            put(record, "path", event.path());
            put(record, "operation", event.operation());
            put(record, "command", event.command());
            put(record, "outputPreview", concise(event.output()));
            records.add(Map.copyOf(record));
        }
        return List.copyOf(records);
    }

    private static Map<String, Map<String, Object>> toolStats(List<AgentEvent> events) {
        var grouped = new LinkedHashMap<String, List<AgentEvent>>();
        events.forEach(event -> grouped.computeIfAbsent(event.toolName(), key -> new ArrayList<>()).add(event));
        var stats = new LinkedHashMap<String, Map<String, Object>>();
        grouped.entrySet().stream()
                .limit(MAX_TOOL_STATS)
                .forEach(groupEntry -> {
                    String toolName = groupEntry.getKey();
                    List<AgentEvent> toolEvents = groupEntry.getValue();
                    int success = countStatus(toolEvents, AgentEventStatus.SUCCESS);
                    int failed = countStatus(toolEvents, AgentEventStatus.FAILED);
                    var durations =
                            toolEvents.stream()
                                    .map(AgentEvent::durationMs)
                                    .filter(value -> value != null)
                                    .mapToLong(Long::longValue)
                                    .summaryStatistics();
                    var stat = new LinkedHashMap<String, Object>();
                    stat.put("callCount", toolEvents.size());
                    stat.put("successCount", success);
                    stat.put("failCount", failed);
                    if (durations.getCount() > 0) {
                        stat.put("avgDurationMs", Math.round(durations.getAverage()));
                    }
                    sum(toolEvents, AgentEvent::inputTokens).ifPresent(value -> stat.put("inputTokens", value));
                    sum(toolEvents, AgentEvent::outputTokens).ifPresent(value -> stat.put("outputTokens", value));
                    stats.put(toolName, Map.copyOf(stat));
                });
        return Map.copyOf(stats);
    }

    private static List<Map<String, Object>> toolGroups(List<AgentEvent> events) {
        var grouped = new LinkedHashMap<String, List<AgentEvent>>();
        events.forEach(event -> grouped.computeIfAbsent(event.toolName(), key -> new ArrayList<>()).add(event));
        var groups = new ArrayList<Map<String, Object>>();
        grouped.entrySet().stream()
                .limit(MAX_TOOL_GROUPS)
                .forEach(entry -> {
                    String toolName = entry.getKey();
                    List<AgentEvent> toolEvents = entry.getValue();
                    var commands = new LinkedHashSet<String>();
                    var paths = new LinkedHashSet<String>();
                    toolEvents.stream()
                            .map(AgentEvent::command)
                            .filter(AgentToolTelemetry::hasText)
                            .limit(MAX_GROUP_VALUES)
                            .forEach(commands::add);
                    toolEvents.stream()
                            .map(AgentEvent::path)
                            .filter(AgentToolTelemetry::hasText)
                            .limit(MAX_GROUP_VALUES)
                            .forEach(paths::add);
                    var group = new LinkedHashMap<String, Object>();
                    group.put("toolName", toolName);
                    group.put("callCount", toolEvents.size());
                    group.put("successCount", countStatus(toolEvents, AgentEventStatus.SUCCESS));
                    group.put("failCount", countStatus(toolEvents, AgentEventStatus.FAILED));
                    if (!commands.isEmpty()) {
                        group.put("commands", List.copyOf(commands));
                    }
                    if (!paths.isEmpty()) {
                        group.put("paths", List.copyOf(paths));
                    }
                    groups.add(Map.copyOf(group));
                });
        return List.copyOf(groups);
    }

    private static int countStatus(List<AgentEvent> events, AgentEventStatus status) {
        return (int) events.stream().filter(event -> event.status() == status).count();
    }

    private static java.util.Optional<Integer> sum(
            List<AgentEvent> events, java.util.function.Function<AgentEvent, Integer> getter) {
        var values = events.stream().map(getter).filter(value -> value != null).toList();
        if (values.isEmpty()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(values.stream().mapToInt(Integer::intValue).sum());
    }

    private static void put(Map<String, Object> target, String key, Object value) {
        if (value != null && (!(value instanceof String text) || !text.isBlank())) {
            target.put(key, value);
        }
    }

    private static String concise(String value) {
        if (!hasText(value)) {
            return null;
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= MAX_OUTPUT_PREVIEW_CHARS
                ? normalized
                : normalized.substring(0, MAX_OUTPUT_PREVIEW_CHARS);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
```

- [ ] **Step 4: Add telemetry metadata in `AgentSegmentFormatter`**

In `metadata(AgentTimelineContent timeline, AgentEpisode episode)`, after `fileEvents` is added, merge telemetry metadata:

```java
metadata.putAll(AgentToolTelemetry.metadata(episode.events()));
```

- [ ] **Step 5: Run formatter test and verify pass**

Run:

```bash
mvn -pl memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent -Dtest=AgentSegmentFormatterTest test
```

Expected: PASS.

- [ ] **Step 6: Commit**

Run:

```bash
git add memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent/src/main/java/com/openmemind/ai/memory/plugin/rawdata/agent/chunk memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent/src/test/java/com/openmemind/ai/memory/plugin/rawdata/agent/chunk/AgentSegmentFormatterTest.java
git commit -m "feat(agent): aggregate tool telemetry in episode metadata"
```

---

### Task 5: Improve Deterministic Tool Items With Telemetry

**Files:**
- Modify: `memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent/src/main/java/com/openmemind/ai/memory/plugin/rawdata/agent/item/AgentMemoryItemFactory.java`
- Test: `memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent/src/test/java/com/openmemind/ai/memory/plugin/rawdata/agent/item/AgentItemExtractionStrategyTest.java`

- [ ] **Step 1: Add a failing deterministic item metadata test**

In `AgentItemExtractionStrategyTest`, add these entries to the metadata returned by `successfulEpisode()`:

```java
Map.entry(
        "toolStats",
        Map.of(
                "Bash",
                Map.of(
                        "callCount",
                        2,
                        "successCount",
                        1,
                        "failCount",
                        1,
                        "avgDurationMs",
                        10L))),
Map.entry(
        "toolRecords",
        List.of(
                Map.of(
                        "eventId",
                        "e2",
                        "seq",
                        2,
                        "toolName",
                        "Bash",
                        "status",
                        "failed",
                        "command",
                        "npm test payment",
                        "outputPreview",
                        "rounding mismatch"),
                Map.of(
                        "eventId",
                        "e4",
                        "seq",
                        4,
                        "toolName",
                        "Bash",
                        "status",
                        "success",
                        "command",
                        "npm test payment",
                        "outputPreview",
                        "passed"))),
Map.entry(
        "toolGroups",
        List.of(
                Map.of(
                        "toolName",
                        "Bash",
                        "callCount",
                        2,
                        "successCount",
                        1,
                        "failCount",
                        1,
                        "commands",
                        List.of("npm test payment"))))
```

Then assert those keys are copied to the produced `tool` item metadata:

```java
assertThat(tool.metadata())
        .containsKeys("toolStats", "toolRecords", "toolGroups")
        .containsEntry("command", "npm test payment")
        .containsEntry("successCount", 1)
        .containsEntry("failCount", 1);
```

- [ ] **Step 2: Add a failing deterministic tool content test**

Assert that failed-then-passed validation produces a more informative tool item:

```java
assertThat(tool.content())
        .contains("npm test payment")
        .contains("failed once")
        .contains("passed once")
        .contains("src/payment/calc.ts");
```

- [ ] **Step 3: Run item extraction test and verify failure**

Run:

```bash
mvn -pl memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent -Dtest=AgentItemExtractionStrategyTest test
```

Expected: FAIL because telemetry metadata is not copied and the current tool content uses the simpler validation text.

- [ ] **Step 4: Copy telemetry metadata into deterministic item metadata**

In `baseMetadata(EpisodeMetadata episode)`, add:

```java
copy(episode.raw(), metadata, "toolStats");
copy(episode.raw(), metadata, "toolRecords");
copy(episode.raw(), metadata, "toolGroups");
```

- [ ] **Step 5: Improve `toolContent`**

Replace the first `command != null && !episode.files().isEmpty()` branch with:

```java
if (command != null && !episode.files().isEmpty()) {
    String fileList = String.join(", ", episode.files());
    if (failCount > 0 || successCount > 0) {
        return "Use %s to validate changes touching %s; it failed %s and passed %s in this agent episode."
                .formatted(command, fileList, countWord(failCount), countWord(successCount));
    }
    return "Use %s to validate changes touching %s.".formatted(command, fileList);
}
```

Keep existing branches for command-without-files and tool-without-command.

- [ ] **Step 6: Run item extraction test and verify pass**

Run:

```bash
mvn -pl memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent -Dtest=AgentItemExtractionStrategyTest test
```

Expected: PASS.

- [ ] **Step 7: Commit**

Run:

```bash
git add memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent/src/main/java/com/openmemind/ai/memory/plugin/rawdata/agent/item/AgentMemoryItemFactory.java memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent/src/test/java/com/openmemind/ai/memory/plugin/rawdata/agent/item/AgentItemExtractionStrategyTest.java
git commit -m "feat(agent): enrich deterministic tool memories with telemetry"
```

---

### Task 6: Protect Privacy, Size, And Compatibility

**Files:**
- Modify: `memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent/src/test/java/com/openmemind/ai/memory/plugin/rawdata/agent/privacy/AgentEventRedactorTest.java`
- Modify: `memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent/src/test/java/com/openmemind/ai/memory/plugin/rawdata/agent/chunk/AgentSegmentFormatterTest.java`
- Modify: `memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent/src/main/java/com/openmemind/ai/memory/plugin/rawdata/agent/chunk/AgentToolTelemetry.java`

- [ ] **Step 1: Add redactor preservation test**

Add this test to `AgentEventRedactorTest`:

```java
@Test
void shouldPreserveToolTelemetryWhenRedactingText() {
    AgentEvent event =
            new AgentEvent(
                    "e1",
                    1,
                    AgentEventKind.COMMAND,
                    Instant.parse("2026-05-24T10:00:00Z"),
                    null,
                    "Bash",
                    null,
                    "Bearer secret-token-value",
                    AgentEventStatus.SUCCESS,
                    1234L,
                    11,
                    22,
                    "sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                    null,
                    null,
                    "npm test payment",
                    0,
                    Map.of());

    AgentEvent redacted = new AgentEventRedactor().redact(event);

    assertThat(redacted.durationMs()).isEqualTo(1234L);
    assertThat(redacted.inputTokens()).isEqualTo(11);
    assertThat(redacted.outputTokens()).isEqualTo(22);
    assertThat(redacted.contentHash()).isEqualTo(event.contentHash());
    assertThat(redacted.output()).contains("[REDACTED:bearer_token]");
}
```

- [ ] **Step 2: Add capped telemetry metadata tests**

Add a test to `AgentSegmentFormatterTest` that builds 45 tool events and asserts:

```java
assertThat(formatted.metadata().get("toolRecords")).asList().hasSize(40);
```

Build events with `AgentEpisodeTestSupport.event(...)` and distinct event IDs.

Add a second test that builds 25 distinct tool names and asserts:

```java
assertThat(((Map<?, ?>) formatted.metadata().get("toolStats"))).hasSize(20);
assertThat(formatted.metadata().get("toolGroups")).asList().hasSize(20);
```

In the same test or a focused helper test, include one tool with 25 distinct commands/paths and assert the first group caps both values:

```java
Map<?, ?> group = (Map<?, ?>) ((List<?>) formatted.metadata().get("toolGroups")).getFirst();
assertThat(group.get("commands")).asList().hasSize(20);
assertThat(group.get("paths")).asList().hasSize(20);
```

- [ ] **Step 3: Run privacy and formatter tests**

Run:

```bash
mvn -pl memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent -Dtest=AgentEventRedactorTest,AgentSegmentFormatterTest test
```

Expected: PASS.

- [ ] **Step 4: Commit**

Run:

```bash
git add memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent/src/test/java/com/openmemind/ai/memory/plugin/rawdata/agent/privacy/AgentEventRedactorTest.java memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent/src/test/java/com/openmemind/ai/memory/plugin/rawdata/agent/chunk/AgentSegmentFormatterTest.java memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent/src/main/java/com/openmemind/ai/memory/plugin/rawdata/agent/chunk/AgentToolTelemetry.java
git commit -m "test(agent): cover tool telemetry privacy and caps"
```

---

### Task 7: Document The RawData-Agent / RawData-ToolCall Boundary

**Files:**
- Modify: `memind-integrations/claude-code/README.md`
- Modify: `memind-integrations/codex/README.md`
- Modify: `docs/superpowers/specs/2026-05-24-rawdata-agent-design.md`

- [ ] **Step 1: Update Claude Code README**

In the `rawdata-toolcall` relationship section, include this text:

```markdown
`rawdata-agent` absorbs deterministic tool telemetry from the `rawdata-toolcall` design: duration, token counts,
content hashes, per-episode tool records, and per-tool success/failure stats. Claude Code still submits one canonical
`agent_timeline` per turn; it does not submit duplicate `tool_call` raw data. `rawdata-toolcall` remains the correct
entry point for pure tool-call logs that do not have user prompts, agent turns, or Stop boundaries.
```

- [ ] **Step 2: Update Codex README**

Add the same boundary text to `memind-integrations/codex/README.md`.

- [ ] **Step 3: Update design document relationship section**

In `docs/superpowers/specs/2026-05-24-rawdata-agent-design.md`, refine the `rawdata-toolcall` relationship section to include:

```markdown
`rawdata-toolcall` remains a separate raw data type for pure tool-call logs. `rawdata-agent` may reuse deterministic
tool telemetry ideas from `rawdata-toolcall`, but must not run the toolcall LLM extraction path by default and must not
double-ingest Claude Code or Codex tool activity.
```

- [ ] **Step 4: Commit**

Run:

```bash
git add memind-integrations/claude-code/README.md memind-integrations/codex/README.md docs/superpowers/specs/2026-05-24-rawdata-agent-design.md
git commit -m "docs(agent): clarify tool telemetry boundary"
```

---

### Task 8: Full Verification

**Files:**
- No new files.

- [ ] **Step 1: Run Python integration unit tests**

Run:

```bash
python3 -m unittest memind-integrations/claude-code/tests/test_agent_timeline.py memind-integrations/codex/tests/test_agent_timeline.py
```

Expected: PASS.

- [ ] **Step 2: Run rawdata-agent Maven tests**

Run:

```bash
mvn -pl memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent -am test
```

Expected: PASS.

- [ ] **Step 3: Run formatting checks for touched Java module**

Run:

```bash
mvn -pl memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent spotless:check
```

Expected: PASS.

- [ ] **Step 4: Inspect diff for accidental double ingestion**

Run:

```bash
git diff -- memind-integrations/claude-code/hooks/hooks.json memind-integrations/codex/hooks/hooks.json memind-integrations/claude-code/scripts memind-integrations/codex/scripts
```

Expected: no change that submits `tool_call` raw data from Claude Code or Codex.

- [ ] **Step 5: Inspect rawdata-toolcall for unintended edits**

Run:

```bash
git diff -- memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-toolcall
```

Expected: empty diff unless documentation references were intentionally changed outside that module.

- [ ] **Step 6: Commit verification-only fixes if formatting required changes**

If formatting changes were applied by `spotless:apply`, commit them:

```bash
git add memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent
git commit -m "style(agent): format tool telemetry changes"
```

If no formatting changes were made, do not create a verification commit.

---

## Expected Outcome

After this plan is implemented:

- Claude Code and Codex tool events carry stable tool telemetry into `agent_timeline`.
- `rawdata-agent` episode segments expose file/tool-aware evidence through `toolRecords`, `toolStats`, and `toolGroups`.
- Deterministic tool memories become more useful without increasing LLM calls.
- `rawdata-toolcall` remains valuable for pure tool logs and is not duplicated by Claude Code/Codex.
- The new metadata creates a stronger foundation for a later PreToolUse file-history context feature.

## Self-Review Notes

- Spec coverage: covers deterministic fields, redacted `contentHash`, episode metadata, deterministic item enrichment, documentation boundary, and verification.
- Placeholder scan: no deferred implementation markers are used.
- Type consistency: hook payload emits `durationMs`, `inputTokens`, `outputTokens`, `contentHash`; Java `AgentEvent` uses the same names; segment metadata uses `toolRecords`, `toolStats`, and `toolGroups`.
- Scope check: PreToolUse file context and OpenAPI file queries are explicitly excluded to keep this implementation focused and independently testable.
