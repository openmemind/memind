# rawdata-agent Design Spec

Date: 2026-05-24

Status: proposed design

Target project: `/Users/zhengyate/dev/openmemind/memind`

Target location after review: `docs/superpowers/specs/2026-05-24-rawdata-agent-design.md`

## Summary

`rawdata-agent` is a new Memind RawData plugin that lets coding agents and other tool-using agents submit their work process as structured agent timelines. The plugin turns raw agent activity into Memind's existing AGENT memory categories: `TOOL`, `RESOLUTION`, `PLAYBOOK`, and `DIRECTIVE`.

The design deliberately reuses Memind core instead of creating a parallel observation database. Agent timelines enter the existing pipeline:

```text
  AgentTimelineContent
  -> RawData / Segment / ParsedSegment
  -> ExtractedMemoryEntry
  -> MemoryItem
  -> Insight Tree / Graph / Retrieval
```

This gives Memind the coding-agent capabilities that make agentmemory and claude-mem useful, while keeping Memind's broader architecture: multi-source RawData, USER and AGENT scopes, Insight Tree, deep retrieval, graph support, and reusable Spring Boot plugin wiring.

## Motivation

Memind already has strong general memory architecture: RawData, MemoryItem, Insight Tree, Simple/Deep retrieval, USER and AGENT scopes, item graph support, and plugin-based raw data handling. Its current Claude Code and Codex integrations, however, intentionally focus on prompt-time retrieval and transcript ingestion. They do not ingest tool calls or agent lifecycle events in v0.1.

That leaves a gap in coding-agent scenarios. Coding agents learn from things that are often not present in final dialogue:

- Which files were inspected or edited.
- Which commands failed and then passed.
- Which tool usage patterns are reliable.
- Which bug was resolved and how.
- Which repeated successful workflow should become a procedural playbook.
- Which durable project or collaboration rule should be reused.

agentmemory and claude-mem address this gap with observations and tool-call history. The Memind design should borrow that lesson without copying their storage model. In Memind, the equivalent "observation layer" should be represented as agent episode segments derived from RawData. Those segments remain traceable to raw events and feed the existing MemoryItem and Insight Tree machinery.

## Goals

1. Add a canonical RawData plugin for agent work process data.
2. Support Claude Code, Codex, OpenClaw, Hermes Agent, Cursor, Gemini CLI, and custom agents through a common schema.
3. Capture tool usage, command results, file interactions, permissions, errors, user goals, and task outcomes.
4. Chunk event streams into coherent agent episodes rather than isolated log lines.
5. Extract AGENT memory items:
   - `TOOL`: tool and command usage experience.
   - `RESOLUTION`: resolved problem and fix knowledge.
   - `PLAYBOOK`: reusable procedural workflows learned from successful episodes.
   - `DIRECTIVE`: durable agent behavior rules and project constraints.
6. Reuse Memind core persistence, deduplication, vector indexing, text search, item graph, Insight Tree, and retrieval.
7. Preserve privacy and safety by redacting secrets and avoiding raw large-output storage in memory items.
8. Provide a path for Memind's Claude Code and Codex integrations to capture tool events without binding core to those hosts.
9. Close the coding-agent memory capability gap with agentmemory and claude-mem while keeping Memind more general.

## Non-Goals

1. Do not add a new standalone memory database for agent observations.
2. Do not hard-code Claude Code or Codex semantics into `memind-core`.
3. Do not require every tool event to trigger LLM extraction.
4. Do not replace `rawdata-toolcall`; keep it as a narrow compatibility path for pure tool-call logs.
5. Do not make the first version depend on a new UI.
6. Do not make `Observation` a new first-class Memind model in the first version.
7. Do not store full sensitive tool outputs or file contents as long-term memory by default.

## Comparison With agentmemory and claude-mem

### claude-mem

claude-mem is strong as a coding-memory product. It has lifecycle hooks, generated observations, a worker process, SQLite/Chroma-backed search, search/timeline/detail progressive disclosure, viewer UI, and many product skills. It is particularly polished for "what happened before in this project?" workflows.

Memind should borrow:

- Tool/lifecycle capture as a first-class integration concern.
- Progressive disclosure for coding history retrieval.
- Compact generated episode summaries with evidence IDs.
- Product-level Claude/Codex hook reliability and retry behavior.

Memind should not copy:

- A separate observation-first storage model when RawData/Segment/MemoryItem already cover most needs.
- A Claude-centric design.

### agentmemory

agentmemory is strong as a coding-agent runtime memory system. It has rich observations, hybrid search, graph features, lessons, procedural skill extraction, retention/decay, actions, signals, checkpoints, and many MCP/REST endpoints.

Memind should borrow:

- Episode-level coding observations rather than only transcript summaries.
- Procedural skill extraction from completed successful sessions.
- Lessons/playbooks as reusable agent knowledge.
- Core-compatible entity hints for files, commands, errors, tools, and modules.
- Delayed extraction after episode completion instead of per-event extraction.

Memind should not copy:

- A coding-only architecture.
- Runtime orchestration features such as leases/actions/checkpoints as part of `rawdata-agent` v1.
- A parallel state engine outside Memind core.

### Memind Differentiation

With `rawdata-agent`, Memind can become a general agent experience memory layer:

```text
conversation + document + image + audio + toolcall + agent timeline
  -> MemoryItem
  -> Insight Tree
  -> Retrieval
```

The differentiator is not just saving coding observations. It is turning agent work process into structured AGENT memory that participates in Memind's long-term knowledge evolution.

## Architecture

### Module Layout

Add:

```text
memind-plugins/
  memind-plugin-rawdatas/
    memind-plugin-rawdata-agent/
      src/main/java/com/openmemind/ai/memory/plugin/rawdata/agent/
        AgentRawContentTypeRegistrar.java
        content/
          AgentTimelineContent.java
          AgentSkillDocContent.java        # optional v1.1, not required for v1
        model/
          AgentEvent.java
          AgentEventKind.java
          AgentTimeline.java
          AgentEpisode.java
          AgentCommand.java
          AgentFileReference.java
          AgentToolCall.java
          AgentOutcome.java
        config/
          AgentRawDataOptions.java
          AgentChunkingOptions.java
          AgentExtractionOptions.java
          AgentPrivacyOptions.java
        chunk/
          AgentTimelineChunker.java
          AgentEpisodeAssembler.java
          AgentSegmentFormatter.java
        processor/
          AgentTimelineContentProcessor.java
        caption/
          AgentCaptionGenerator.java
        item/
          AgentItemExtractionStrategy.java
          AgentItemPrompts.java
        privacy/
          AgentEventRedactor.java
          SecretPatternRedactor.java
        plugin/
          AgentRawDataPlugin.java

  memind-plugin-spring-boot-starters/
    memind-plugin-rawdata-agent-starter/
      src/main/java/com/openmemind/ai/memory/plugin/rawdata/agent/autoconfigure/
        AgentRawDataAutoConfiguration.java
        AgentRawDataProperties.java
```

Register the modules and starter in the same places as existing rawdata plugins:

- `memind-plugins/memind-plugin-rawdatas/pom.xml`
- `memind-plugins/memind-plugin-spring-boot-starters/pom.xml`
- the parent/root module list if the repository root POM enumerates plugin modules
- `memind-server/pom.xml`
- `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` in the starter

### Runtime Loading

Memind server already collects `RawDataPlugin` beans through `ObjectProvider<RawDataPlugin>` and passes them into `Memory.builder().rawDataPlugin(...)`. `rawdata-agent` should follow the same path.

The plugin should not require special server code for its core extraction behavior. Optional REST convenience endpoints can be added later, but v1 can use the existing extraction endpoint with `rawContent.type = "agent_timeline"`.

### Integration Boundary

Agent integrations are adapters. They capture host-specific events and submit normalized payloads to Memind.

```text
Claude Code hook payload
Codex hook payload
OpenClaw event bus
Hermes trace log
Cursor transcript
  -> integration adapter
  -> AgentTimelineContent JSON
  -> Memind server
  -> rawdata-agent plugin
```

`rawdata-agent` owns schema validation, privacy cleanup, chunking, episode assembly, and item extraction. It does not own hook installation or host-specific event parsing.

## Raw Content Types

### `agent_timeline` v1

Primary input type. It represents a time-ordered sequence of events from one agent session, turn, task, or partial episode.

Example:

```json
{
  "type": "agent_timeline",
  "sourceClient": "claude-code",
  "sourceVersion": "1.0",
  "userId": "local__alice",
  "agentId": "claude-code__project_hash",
  "project": {
    "name": "payments-api",
    "rootHash": "sha256:...",
    "git": {
      "branch": "fix/payment-rounding",
      "commit": "abc123"
    }
  },
  "sessionId": "session-123",
  "agentTurnId": "session-123-agent-turn-1-6",
  "timelineId": "timeline-123",
  "events": [
    {
      "eventId": "e1",
      "seq": 1,
      "kind": "user_prompt",
      "text": "修复 payment test",
      "occurredAt": "2026-05-24T10:00:00Z"
    },
    {
      "eventId": "e2",
      "seq": 2,
      "kind": "tool_call",
      "toolName": "Read",
      "input": "src/payment/calc.ts",
      "status": "success",
      "occurredAt": "2026-05-24T10:00:12Z"
    },
    {
      "eventId": "e3",
      "seq": 3,
      "kind": "command",
      "toolName": "Bash",
      "command": "npm test payment",
      "status": "failed",
      "output": "rounding mismatch",
      "durationMs": 3200,
      "occurredAt": "2026-05-24T10:01:00Z"
    },
    {
      "eventId": "e4",
      "seq": 4,
      "kind": "file_edit",
      "path": "src/payment/calc.ts",
      "operation": "modify",
      "occurredAt": "2026-05-24T10:03:00Z"
    },
    {
      "eventId": "e5",
      "seq": 5,
      "kind": "command",
      "toolName": "Bash",
      "command": "npm test payment",
      "status": "success",
      "durationMs": 4100,
      "occurredAt": "2026-05-24T10:05:00Z"
    },
    {
      "eventId": "e6",
      "seq": 6,
      "kind": "stop",
      "occurredAt": "2026-05-24T10:06:00Z"
    }
  ]
}
```

### Content Type Contract

Memind currently separates external JSON discriminator names from Java internal content type constants. `rawdata-agent` must follow that convention explicitly:

```text
External JSON rawContent.type: "agent_timeline"
RawContentTypeRegistrar subtype: "agent_timeline" -> AgentTimelineContent.class
AgentTimelineContent.TYPE: "AGENT_TIMELINE"
AgentTimelineContent.contentType(): "AGENT_TIMELINE"
RawData.contentType stored value: "AGENT_TIMELINE"
```

Rationale:

- JSON subtype names use lower snake case, consistent with existing plugin subtype `tool_call`.
- Java content type constants use upper snake case, consistent with existing `ConversationContent.TYPE = "CONVERSATION"` and `ToolCallContent.TYPE = "TOOL_CALL"`.
- Client libraries can submit the feature immediately through map-based raw content without waiting for typed client models.

### `agent_skilldoc` v1.1

Optional future input type for importing existing `SKILL.md`, procedure docs, and team playbooks. This is not required for historical-session procedural skill extraction. Historical skill extraction should come from `agent_timeline` episodes.

## Event Schema

Supported event kinds in v1:

- `user_prompt`
- `assistant_message`
- `tool_call`
- `tool_result`
- `command`
- `file_read`
- `file_edit`
- `test_result`
- `permission_request`
- `error`
- `stop`
- `session_end`
- `task_completed`

Required fields for all events:

- `eventId`: stable event ID from adapter, or deterministic hash.
- `seq`: monotonic sequence number within timeline. If the host event stream does not provide one, the adapter must synthesize a stable sequence number from the normalized event order before submission.
- `kind`: event kind.
- `occurredAt`: event time, or adapter receipt time.

Common optional fields:

- `toolName`
- `input`
- `output`
- `status`: `success`, `failed`, `error`, `cancelled`, `unknown`
- `durationMs`
- `path`
- `operation`
- `command`
- `exitCode`
- `metadata`

The plugin must tolerate missing optional fields and should avoid rejecting useful partial timelines unless core required fields are missing.

## Processor Contract

`AgentTimelineContentProcessor` must make the content behavior explicit:

```java
contentClass()       -> AgentTimelineContent.class
contentType()        -> AgentTimelineContent.TYPE
allowedCategories()  -> MemoryCategory.agentCategories()
usesSourceIdentity() -> true
supportsInsight()    -> true
```

`usesSourceIdentity()` is required because two different agents, repositories, or sessions can produce identical event text. The RawData content ID must include a source identity through `resourceId`, `sourceUri`, `storageUri`, request metadata, or the content's own stable timeline fingerprint. Integration adapters should include at least:

```json
{
  "sourceUri": "agent://claude-code/payments-api/session-123/timeline-123",
  "sourceClient": "claude-code",
  "sessionId": "session-123",
  "timelineId": "timeline-123",
  "projectRootHash": "sha256:..."
}
```

Current Memind open extraction requests only expose `sourceClient` as a top-level convenience field. They do not automatically promote arbitrary `rawContent` JSON fields such as `sessionId` or `timelineId` into extraction request metadata before RawData content ID calculation. Therefore `rawdata-agent` must make source identity deterministic through one of these supported paths:

- preferred v1 path: `AgentTimelineContent.getContentId()` hashes a canonical identity payload that includes `sourceClient`, `sessionId`, `timelineId`, ordered event IDs, and a normalized event-content hash, so exact duplicate complete timeline windows produce the same RawData content ID even through the existing open extraction endpoint;
- optional API/client enhancement: extend the open extraction request and client wrappers to pass `sourceUri`, `sessionId`, `timelineId`, and `projectRootHash` as extraction metadata before `RawDataLayer.resolveRawDataContentId(...)` runs.

If the optional metadata path is added, it must be additive and backward-compatible. The plugin must not depend on raw JSON payload fields being visible as request metadata unless that wiring is explicitly implemented.

`supportsInsight()` should stay `true`. Disabling insight for this content type would make AGENT playbooks, resolutions, and directives persist as items only, which would weaken the main reason to add `rawdata-agent`.

`allowedCategories()` protects the USER memory tree. Even if extraction uses `ExtractionConfig.defaults()` with USER scope, AGENT categories should be emitted and `MemoryItemLayer` should persist them under their category-defined AGENT scope. Callers should still prefer `ExtractionConfig.agentOnly()` or the equivalent server/client option when available to make intent explicit.

## Chunking and Episode Assembly

### Principle

Chunking must preserve causality. A coding-agent memory chunk should describe a coherent task episode, not an arbitrary token window.

Bad chunking:

```text
Segment 1: Bash npm test failed.
Segment 2: Edit calc.ts.
Segment 3: Bash npm test passed.
```

Good chunking:

```text
Segment: User asked to fix payment tests. npm test payment failed with rounding mismatch. The agent edited src/payment/calc.ts. npm test payment then passed.
```

### Episode Boundaries

Primary boundaries:

- `user_prompt` starts a candidate episode.
- `stop`, `session_end`, or `task_completed` ends a candidate episode.
- A new `user_prompt` closes the previous open episode if no stop event was seen.

Secondary boundaries:

- Time gap larger than `maxEventGap`, default 30 minutes.
- Token estimate larger than `targetEpisodeTokens`, default 2000.
- Event count larger than `maxEventsPerEpisode`, default 80.
- Explicit adapter-provided `taskId` or `subtaskId` changes.

### Phase Splitting

If an episode exceeds budget, split by phase while preserving shared context:

- `investigation`: reads, searches, failed commands, diagnostics.
- `implementation`: edits, patches, refactors.
- `validation`: tests, builds, checks, final verification.
- `handoff`: summary, stop, session end.

Each phase segment must include:

- `episodeId`
- `goal`
- `phase`
- `outcome`
- important files
- important commands
- evidence event IDs

### Segment Text Format

`AgentSegmentFormatter` should produce compact, deterministic text:

```text
Goal: Fix payment tests.
Outcome: success
Project: payments-api
Files: src/payment/calc.ts
Commands:
- npm test payment -> failed: rounding mismatch
- npm test payment -> success
Actions:
- Read src/payment/calc.ts
- Modified src/payment/calc.ts
Evidence:
- e3: failed test output mentioned rounding mismatch
- e5: npm test payment passed
```

### Segment Metadata

Each segment metadata should include:

```json
{
  "segmentType": "agent_episode",
  "sourceClient": "claude-code",
  "sessionId": "session-123",
  "timelineId": "timeline-123",
  "episodeId": "episode-123",
  "phase": "full",
  "goal": "Fix payment tests",
  "outcome": "success",
  "projectName": "payments-api",
  "projectRootHash": "sha256:...",
  "gitBranch": "fix/payment-rounding",
  "files": ["src/payment/calc.ts"],
  "commands": ["npm test payment"],
  "toolNames": ["Read", "Bash", "Edit"],
  "failureSignals": ["rounding mismatch"],
  "eventIds": ["e1", "e2", "e3", "e4", "e5", "e6"],
  "windowStart": "2026-05-24T10:00:00Z",
  "windowEnd": "2026-05-24T10:06:00Z"
}
```

Do not persist absolute local project roots by default. They can expose usernames, customer names, or private filesystem layout through durable RawData metadata. If an integration needs raw absolute paths for a local-only deployment, make it an explicit opt-in field such as `projectRootRaw`, disabled by default.

This metadata is the Memind equivalent of an observation evidence layer. It avoids adding a new `Observation` table while preserving traceability.

## Observation Layer Decision

Do not add a first-class `Observation` model in v1.

Memind already has:

- RawData for original input and traceability.
- Segment / ParsedSegment for chunked evidence.
- MemoryItem for durable atomic memory.
- Insight for higher-level synthesis.

`rawdata-agent` should use `segmentType = "agent_episode"` metadata to represent observation-like units. If future UI, citation, replay, or manual-edit workflows require a durable first-class episode projection, add `AgentEpisode` later as a projection derived from RawData, not as a replacement for MemoryItem.

Future optional projection:

```text
AgentEpisode:
  id
  memoryId
  rawDataId
  sessionId
  sourceClient
  goal
  outcome
  summary
  files
  commands
  eventIds
  createdAt
```

## Item Extraction

### Strategy

`AgentItemExtractionStrategy` should be content-type-specific. The default conversation item extractor is not enough because agent timelines need coding-domain semantics.

The strategy should combine deterministic extraction and LLM extraction:

Deterministic extraction:

- Tool and command usage facts.
- Files touched.
- Test commands and status.
- Outcome.
- Event IDs and evidence metadata.
- Core-compatible entity hints for files, commands, tools, errors, and modules.

LLM extraction:

- Resolved problem and fix summaries.
- Reusable playbooks.
- Durable directives.
- Normalized failure modes.
- Concise content suitable for long-term memory.

### Output Contract

The strategy outputs `ExtractedMemoryEntry` objects.

Required fields:

- `content`
- `confidence`
- `observedAt`
- `rawDataId`
- `insightTypes`
- `metadata`
- `type`
- `category`

Structured LLM extraction should use a constrained response schema before conversion to `ExtractedMemoryEntry`:

```json
{
  "items": [
    {
      "category": "resolution",
      "content": "Payment tests failed because rounding behavior in src/payment/calc.ts did not match the expected policy; editing calc.ts and rerunning npm test payment resolved the failure.",
      "confidence": 0.86,
      "insightTypes": ["resolutions"],
      "entities": [
        {"entityType": "object", "name": "src/payment/calc.ts", "salience": 0.9},
        {"entityType": "object", "name": "npm test payment", "salience": 0.8},
        {"entityType": "concept", "name": "rounding mismatch", "salience": 0.8}
      ],
      "metadata": {
        "episodeId": "episode-123",
        "outcome": "success",
        "files": ["src/payment/calc.ts"],
        "commands": ["npm test payment"],
        "toolNames": ["Bash", "Read", "Edit"],
        "failureSignals": ["rounding mismatch"],
        "evidenceEventIds": ["e3", "e4", "e5"],
        "codingEntities": {
          "files": ["src/payment/calc.ts"],
          "commands": ["npm test payment"],
          "errors": ["rounding mismatch"],
          "tools": ["Bash", "Read", "Edit"]
        }
      }
    }
  ]
}
```

This schema intentionally uses the existing `MemoryItemExtractionResponse.ExtractedItem` shape so the agent extractor can share the same structured response vocabulary as the default item extractor. Coding-specific typed details stay in metadata. Do not introduce a plugin-owned graph payload with custom relation names in v1.

Implementation note: `AgentItemExtractionStrategy` is content-type-specific and ultimately returns `ExtractedMemoryEntry` objects. Therefore it must not assume that core will automatically convert its intermediate LLM response into graph hints unless it routes through shared conversion code. The implementation must either:

- extract a small core support converter from the existing default item extractor path and reuse it, or
- explicitly convert `ExtractedItem.entities` and `ExtractedItem.causalRelations` into `ExtractedGraphHints` when building each `ExtractedMemoryEntry`.

Do not call private methods or duplicate hidden behavior through reflection. The graph hint conversion should be a normal, testable code path.

If a single episode produces multiple memory items and there is a true item-to-item causal relationship, the extractor may emit current-core-compatible causal relations:

```json
{
  "items": [
    {
      "category": "resolution",
      "content": "Payment tests failed because rounding behavior did not match policy.",
      "confidence": 0.82,
      "insightTypes": ["resolutions"],
      "entities": [{"entityType": "concept", "name": "rounding mismatch", "salience": 0.8}],
      "metadata": {"evidenceEventIds": ["e3"]}
    },
    {
      "category": "resolution",
      "content": "Editing src/payment/calc.ts and rerunning npm test payment resolved the failure.",
      "confidence": 0.86,
      "insightTypes": ["resolutions"],
      "entities": [{"entityType": "object", "name": "src/payment/calc.ts", "salience": 0.9}],
      "causalRelations": [
        {"causeIndex": 0, "effectIndex": 1, "relationType": "enabled_by", "strength": 0.7}
      ],
      "metadata": {"evidenceEventIds": ["e4", "e5"]}
    }
  ]
}
```

`causeIndex` and `effectIndex` reference memory item indexes in the same response, not entity indexes. Relation types must be limited to the current Memind causal vocabulary: `caused_by`, `enabled_by`, and `motivated_by`.

Custom coding relation names should not be emitted as graph hints in v1. Semantics such as "fixed error", "touched file", or "validated module" should be represented through item content and metadata fields such as `failureSignals`, `files`, `commands`, `toolNames`, and `codingEntities`.

Rules:

- Every LLM-produced item must reference at least one `metadata.evidenceEventIds` value present in the segment metadata.
- The extractor must drop items whose category is not one of `tool`, `resolution`, `playbook`, or `directive`.
- The extractor must drop playbooks without trigger, steps, and expected outcome.
- The extractor must drop resolutions without both problem and fix/conclusion.
- Deterministic metadata such as `episodeId`, `sessionId`, `timelineId`, `sourceClient`, files, commands, and event IDs should be merged into every emitted item before persistence.
- Core-compatible graph hints are optional. When present, entity types must map to current Memind graph entity types, and causal relations must use current Memind causal relation codes. A custom agent extraction strategy must populate `ExtractedMemoryEntry.graphHints()` explicitly or through a shared core converter.

`MemoryItemLayer` then handles:

- category validation against `allowedCategories`
- insight type normalization
- self-verification if enabled
- deduplication
- vectorization
- persistence
- graph materialization

### Allowed Categories

`AgentTimelineContentProcessor.allowedCategories()` must return:

```java
MemoryCategory.agentCategories()
```

This prevents agent execution traces from being misclassified as USER profile/event memory.

### Category to Insight Type Mapping

`rawdata-agent` should assign insight types deterministically from category unless a specialized extractor has a stronger reason:

```text
category=directive  -> insightTypes=["directives"]
category=playbook   -> insightTypes=["playbooks"]
category=resolution -> insightTypes=["resolutions"]
category=tool       -> insightTypes=["tools"]
```

### Category Mapping

#### TOOL

Use when the memory describes tool, command, or execution behavior.

Required signal:

- `toolName` or `command`

Examples:

```text
Use npm test payment to validate changes in src/payment/calc.ts.
```

```text
Bash command npm test payment failed with rounding mismatch before the fix and passed afterward.
```

Metadata:

```json
{
  "toolName": "Bash",
  "command": "npm test payment",
  "files": ["src/payment/calc.ts"],
  "successCount": 1,
  "failCount": 1
}
```

#### RESOLUTION

Use when the memory describes a resolved problem pattern with usable fix or conclusion.

Required signals:

- problem/failure mode
- fix/conclusion
- outcome or evidence

Example:

```text
Payment tests failed because rounding behavior in src/payment/calc.ts did not match the expected policy; editing calc.ts and rerunning npm test payment resolved the failure.
```

Metadata:

```json
{
  "problem": "rounding mismatch",
  "fix": "updated payment calculation logic",
  "outcome": "success",
  "files": ["src/payment/calc.ts"],
  "commands": ["npm test payment"],
  "evidenceEventIds": ["e3", "e4", "e5"]
}
```

#### PLAYBOOK

Use when the memory describes a reusable procedural workflow.

Required signals:

- trigger condition
- at least two concrete steps
- expected outcome
- preferably success outcome

Example:

```text
When payment tests fail with rounding mismatch, inspect the payment calculation policy, edit src/payment/calc.ts if needed, then run npm test payment until it passes.
```

Metadata:

```json
{
  "trigger": "payment tests fail with rounding mismatch",
  "steps": [
    "Inspect payment calculation policy",
    "Check src/payment/calc.ts",
    "Run npm test payment after edits"
  ],
  "expectedOutcome": "payment tests pass",
  "sourceEpisodeIds": ["episode-123"],
  "strength": 0.6
}
```

#### DIRECTIVE

Use when the memory describes a durable rule, collaboration boundary, or project constraint.

Example:

```text
Do not change the public payment calculation API without updating payment integration tests.
```

Metadata:

```json
{
  "scope": "project",
  "files": ["src/payment/calc.ts"],
  "sourceEpisodeIds": ["episode-123"]
}
```

## Insight Type Mapping

Memind already has AGENT insight types:

- `directives`
- `playbooks`
- `resolutions`

Mapping:

```text
category=directive  -> insightTypes=["directives"]
category=playbook   -> insightTypes=["playbooks"]
category=resolution -> insightTypes=["resolutions"]
```

Current default insight types do not include `tools`, and current `rawdata-toolcall` disables insight building. For `rawdata-agent`, tool usage is not secondary evidence; it is one of the main coding-agent memory surfaces. The v1 implementation must add a default AGENT insight type:

```text
name: tools
categories: ["tool"]
scope: AGENT
mode: BRANCH
description: Tool and command usage patterns, grouped by toolName or command family.
```

Map:

```text
category=tool -> insightTypes=["tools"]
```

This is a small core-level enhancement, not a separate graph engine. It requires updating `DefaultInsightTypes.all()`, store initialization tests, prompt expectations where default insight sets are asserted, and any docs that list built-in insight types.

Existing stores need special handling. Current store implementations seed `DefaultInsightTypes.all()` when the insight type table is first created; adding `tools` only to the default list is not enough for already-initialized deployments. The implementation must add a migration-safe, idempotent default insight type reconciliation path so existing SQLite, MySQL, PostgreSQL, and in-memory stores can receive the new built-in `tools` type without overwriting user-customized insight types.

If TOOL items persist with `insightTypes=[]`, the v1 implementation should be considered incomplete because tool usage knowledge would not participate in Insight Tree synthesis.

This makes tool usage knowledge more discoverable without relying only on item retrieval.

## Procedural Skill Extraction

Procedural skill extraction should not be a separate `rawdata-skills` path for historical sessions. Historical procedural skills are outputs of successful agent episodes.

In Memind terminology, these should be `AGENT / PLAYBOOK` memory items.

Extraction policy:

- Only attempt playbook extraction from successful or partially successful episodes by default.
- Require at least `minEventsForPlaybook`, default 5.
- Require a clear trigger, steps, and expected outcome.
- Do not extract playbooks from exploratory sessions without a reusable procedure.
- Avoid duplicate playbooks through deterministic content, existing deduplication, and source metadata.
- Reinforce existing similar playbooks only when an explicit merge/update path exists.

Example:

```text
Trigger: JWT expiry tests fail around token TTL or fake timer behavior.
Steps:
1. Inspect token TTL configuration in auth/session.ts.
2. Check fake timer setup in auth tests.
3. Run npm test auth after changes.
Expected outcome: Auth tests pass and JWT expiry behavior matches project policy.
```

Store as:

```text
category = playbook
insightTypes = ["playbooks"]
metadata.sourceEpisodeIds = [...]
metadata.steps = [...]
metadata.trigger = ...
metadata.expectedOutcome = ...
```

The first implementation should not overclaim automatic reinforcement. Existing item deduplication can skip exact or semantically duplicate items depending on the configured deduplicator, but it does not by itself update `strength`, `sourceEpisodeIds`, or other metadata on an existing item. If v1 needs reinforcement semantics, add a dedicated playbook consolidation step that updates the existing item or Insight Buffer explicitly; otherwise treat reinforcement as Phase 5 tuning.

## Extraction Timing

Do not extract long-term memory on every tool event.

Recommended lifecycle:

```text
PostToolUse:
  collect event into integration-local timeline buffer or retry spool
  no LLM item extraction required per event

Stop:
  submit or flush the completed timeline window to Memind
  assemble episode
  extract TOOL
  extract RESOLUTION only when resolved-problem evidence is present
  optionally extract PLAYBOOK candidate if success and complexity thresholds pass

PreCompact:
  flush open episode before host context compaction

SessionEnd:
  flush remaining events

Background / scheduled consolidation:
  build Insight Tree
  optionally merge duplicate playbooks if an explicit consolidation job exists
  optionally strengthen repeated procedures if an explicit update path exists
```

Default config:

```properties
memind.rawdata.agent.enabled=true
memind.rawdata.agent.extract-on-stop=true
memind.rawdata.agent.extract-on-session-end=true
memind.rawdata.agent.extract-on-every-tool=false
memind.rawdata.agent.min-events-for-extraction=3
memind.rawdata.agent.min-events-for-playbook=5
memind.rawdata.agent.require-success-for-playbook=true
memind.rawdata.agent.max-event-gap=PT30M
memind.rawdata.agent.consolidation-interval=PT2H
```

The plugin itself can process whatever payload is submitted. The integration decides when to submit. For Claude Code and Codex, the preferred first implementation is submit on `Stop`, `PreCompact`, and `SessionEnd`, with optional lightweight per-tool event spooling.

v1 should not add server-side append or event-buffer semantics. The payload submitted to Memind should be a complete timeline window. Local adapters own partial event buffering, retry, and replay. This keeps the server aligned with the existing RawData extraction model and avoids introducing a second stateful ingestion protocol before there is a clear need.

## Privacy and Redaction

`rawdata-agent` must redact sensitive data before segment formatting, RawData persistence, vectorization, and item extraction.

This is a hard requirement because the current RawData pipeline persists the `Segment` produced by `chunk(...)`. If the plugin only redacts inside the item extraction prompt, the original sensitive tool output can still be stored in RawData and vectors. Therefore redaction must happen before `AgentTimelineChunker` returns durable segments, and integrations should also redact obvious secrets before writing local retry spool files.

Default redaction:

- API keys and bearer tokens.
- Common secret env vars.
- Database URLs with credentials.
- SSH/private key material.
- Cloud credentials.
- Long command outputs beyond configured limit.
- File contents unless explicitly allowed.

Config:

```properties
memind.rawdata.agent.privacy.redact-secrets=true
memind.rawdata.agent.privacy.max-output-chars=4000
memind.rawdata.agent.privacy.max-input-chars=2000
memind.rawdata.agent.privacy.capture-file-content=false
memind.rawdata.agent.privacy.allow-path-patterns=
memind.rawdata.agent.privacy.deny-path-patterns=.env,*.pem,*.key
```

If redaction changes content, metadata should include:

```json
{
  "redacted": true,
  "redactionKinds": ["api_key", "database_url"]
}
```

Raw event payloads submitted by integrations should follow the same rule. Default v1 behavior should not persist full command output or file contents. Store bounded summaries, status, exit code, hashes, file paths, and short redacted snippets instead.

## Deduplication and Idempotency

Integrations may retry submissions. Hooks may fire repeatedly. The plugin must be idempotency-friendly.

Identity fields:

- `timelineId`
- `sessionId`
- `event.eventId`
- `event.seq`
- `sourceClient`
- `contentHash`

Episode ID should be deterministic:

```text
hash(sourceClient + sessionId + firstEventId + lastEventId + eventIds)
```

Item content should be deterministic enough for existing MemoryItem deduplication to work. Metadata should preserve source IDs so future consolidation can identify repeated procedures and related source episodes.

Exact duplicate submissions of the same complete timeline window should be treated as idempotent: same `agentTurnId`, same `timelineId`, same ordered event IDs, same content hash, and same deterministic episode IDs should not produce duplicate durable items. Overlapping or partial windows are harder: the adapter must keep a watermark or window identity so retries and later flushes can be distinguished. The plugin should normalize and skip duplicate events within one submitted payload by `sourceClient + sessionId + agentTurnId + timelineId + event.eventId`; it should not claim universal deduplication or server-side merging for arbitrarily overlapping windows without adapter identity.

Item-level idempotency must be explicit. Current Memind RawData idempotency can replay existing segments for an already-seen content ID, and item extraction may still run after RawData replay. Existing item deduplication is content-hash based and cannot guarantee duplicate suppression if an LLM produces slightly different wording on retry.

For `agent_timeline`, implement at least one of these v1 safeguards:

- skip item extraction when the RawData result is an exact duplicate replay for `AGENT_TIMELINE`, if the core extraction flow exposes that state to the processor or item extraction layer; or
- make `AgentItemExtractionStrategy` produce deterministic canonical content and/or deterministic item-level dedup metadata based on `episodeId + phase + category + evidenceEventIds + normalized payload`, so exact duplicate complete timeline windows yield the same item content hashes; or
- add a plugin-compatible item idempotency key that is checked before persistence.

The acceptance target is exact duplicate complete-window idempotency. Arbitrary overlapping windows remain an adapter responsibility unless a future server-side append/window protocol is introduced.

## Graph Hints

`rawdata-agent` must reuse Memind's current item graph path. It should not introduce `AgentGraphHintsBuilder`, a plugin-owned graph schema, or coding-specific relation types in v1.

The supported path is:

```text
AgentItemExtractionStrategy
  -> optional MemoryItemExtractionResponse.ExtractedItem.entities as intermediate schema
  -> shared converter or explicit conversion
  -> ExtractedMemoryEntry.graphHints().entities
  -> existing item graph materialization
```

and, only when there is a true item-to-item causal link:

```text
AgentItemExtractionStrategy
  -> optional MemoryItemExtractionResponse.ExtractedItem.causalRelations as intermediate schema
  -> shared converter or explicit conversion
  -> ExtractedMemoryEntry.graphHints().causalRelations
  -> CausalHintNormalizer
```

The `MemoryItemExtractionResponse.ExtractedItem` shape is an intermediate LLM response contract, not a persistence contract. The persisted handoff to the existing graph layer is `ExtractedMemoryEntry.graphHints()`.

Coding-domain objects should be represented with the current `GraphEntityType` vocabulary:

```text
file path        -> OBJECT
command          -> OBJECT
tool name        -> OBJECT
module/package   -> CONCEPT or OBJECT, depending on context
failure signal   -> CONCEPT
framework/library -> ORGANIZATION, OBJECT, or CONCEPT, depending on how the source names it
```

The original typed coding semantics stay in item metadata:

```json
{
  "files": ["src/payment/calc.ts"],
  "commands": ["npm test payment"],
  "toolNames": ["Bash", "Read", "Edit"],
  "failureSignals": ["rounding mismatch"],
  "codingEntities": {
    "files": ["src/payment/calc.ts"],
    "commands": ["npm test payment"],
    "errors": ["rounding mismatch"],
    "tools": ["Bash", "Read", "Edit"],
    "modules": ["payment"]
  }
}
```

Do not emit these as graph relation types:

```text
FIXES
TOUCHES_FILE
VALIDATES
APPLIES_TO
EXECUTES
```

Those are useful coding semantics, but the current Memind graph layer only supports generic entities plus bounded causal item links. Express the coding semantics in memory content and metadata first. Use `caused_by`, `enabled_by`, or `motivated_by` only for causal item-to-item links that meet existing `CausalHintNormalizer` constraints.

## Retrieval and Prompt Injection

`rawdata-agent` becomes valuable only if integrations retrieve and format AGENT memory effectively.

Claude Code and Codex retrieval hooks should format AGENT memory separately from USER/project memories:

```text
<memind_memories>
Relevant memories from Memind. Use only when directly helpful:

## Agent Playbooks
- When JWT expiry tests fail, inspect fake timers, check token TTL config, then run npm test auth.

## Resolved Problems
- Payment rounding mismatch was fixed in src/payment/calc.ts and validated with npm test payment.

## Tool Notes
- Use npm test payment to validate payment calculation changes.

## Directives
- Do not change public payment API without updating integration tests.
</memind_memories>
```

Retrieval should support the filters Memind already exposes well:

- by AGENT scope
- by category
- by time range when available

The item content and metadata should still include project name, project root hash, source client, session, files, modules, and commands so lexical/vector retrieval, graph expansion, admin tooling, and integration-side grouping can use those signals. Do not require new core metadata-filter semantics for v1 unless they are implemented as a separate retrieval enhancement.

If core retrieval does not yet expose enough category-aware formatting or metadata filtering, integration formatting should use returned item metadata to group results and should rely on query text plus AGENT/category filters for the first release.

## API and Client Changes

### Preferred v1 Path

Use existing extraction endpoint with a new raw content type:

```text
POST /open/v1/memory/async/extract
rawContent.type = "agent_timeline"
```

This keeps the feature aligned with Memind's RawData abstraction.

### Optional Convenience Endpoint

Add later if needed:

```text
POST /open/v1/agent/timeline
```

This endpoint should translate request body into `AgentTimelineContent` and call the same memory extraction service. It must not bypass core extraction.

### Client Libraries

Python client should expose:

```python
await client.memory.extract_agent_timeline(
    user_id=...,
    agent_id=...,
    timeline=...,
    source_client="claude-code",
)
```

This should be a convenience wrapper around `extract(raw_content=MapRawContent(type="agent_timeline", properties={...}))`.

Typed models can be added after the server plugin lands:

- Python: `AgentTimelineContent`, `AgentEvent`, and `RawContentValue = ConversationContent | MapRawContent | AgentTimelineContent`.
- Java: `AgentTimelineContent` client model or a documented `MapRawContent.of("agent_timeline", properties)` example.
- TypeScript integrations: plain JSON is acceptable in v1 as long as tests validate the exact serialized payload.

The compatibility path matters because existing Python and Java clients already support arbitrary map-backed raw content. `rawdata-agent` should not require a client release before early adopters can test the server plugin.

This compatibility still depends on server-side registration. Map-backed clients can serialize the payload immediately, but the Memind server must load `AgentRawContentTypeRegistrar` through the rawdata plugin or starter so Jackson can resolve `rawContent.type = "agent_timeline"` into `AgentTimelineContent`. Without the registrar, clients should expect a normal unsupported raw content type error rather than a silent fallback.

## Claude Code and Codex Integration Changes

### Current Behavior

Current Memind integrations retrieve before user prompts and ingest transcript user/assistant messages after turns. They intentionally do not ingest tool calls in v0.1.

### Required Additions

Claude Code integration:

- Add `PreToolUse` and `PostToolUse` hook scripts.
- Optionally add `PermissionRequest`.
- Keep `Stop`, `PreCompact`, and `SessionEnd` as flush points.
- Maintain retry spool under `~/.memind/claude-code/retry/`.
- Avoid blocking hot tool paths; per-tool scripts should be best-effort and short-timeout.

Codex integration:

- Add supported hook scripts for `PreToolUse`, `PostToolUse`, and optionally `PermissionRequest` where available.
- Keep `Stop` as the primary flush point.
- Maintain retry spool under `~/.memind/codex/retry/`.

Adapter responsibilities:

- Normalize host payload into agent timeline events.
- Generate stable event IDs and sequence numbers.
- Redact obvious local secrets before writing retry spool when possible.
- Submit complete timeline windows on flush.
- Fail open so agent workflows are not blocked by Memind downtime.

## Relationship With `rawdata-toolcall`

Keep `rawdata-toolcall`.

Roles:

```text
rawdata-toolcall
  narrow path for systems that only have tool-call logs

rawdata-agent
  canonical path for complete agent work process timelines
```

`rawdata-toolcall` remains a separate raw data type for pure tool-call logs. `rawdata-agent` may reuse deterministic tool telemetry ideas from `rawdata-toolcall`, but must not run the toolcall LLM extraction path by default and must not double-ingest Claude Code or Codex tool activity.

Long-term, `rawdata-toolcall` can internally adapt pure tool-call records into `agent_timeline` episodes to reuse the same item extraction logic. This avoids duplicate tool extraction prompts and inconsistent TOOL memories.

## Configuration

Recommended properties:

```properties
memind.rawdata.agent.enabled=true

memind.rawdata.agent.chunking.target-episode-tokens=2000
memind.rawdata.agent.chunking.hard-max-tokens=4000
memind.rawdata.agent.chunking.max-events-per-episode=80
memind.rawdata.agent.chunking.max-event-gap=PT30M

memind.rawdata.agent.extraction.extract-tool=true
memind.rawdata.agent.extraction.extract-resolution=true
memind.rawdata.agent.extraction.extract-playbook=true
memind.rawdata.agent.extraction.extract-directive=true
memind.rawdata.agent.extraction.extract-on-every-tool=false
memind.rawdata.agent.extraction.min-events-for-extraction=3
memind.rawdata.agent.extraction.min-events-for-playbook=5
memind.rawdata.agent.extraction.require-success-for-playbook=true

memind.rawdata.agent.privacy.redact-secrets=true
memind.rawdata.agent.privacy.max-input-chars=2000
memind.rawdata.agent.privacy.max-output-chars=4000
memind.rawdata.agent.privacy.capture-file-content=false
memind.rawdata.agent.privacy.deny-path-patterns=.env,*.pem,*.key
```

## Testing Strategy

### Unit Tests

Add tests for:

- `AgentTimelineContent` JSON serialization/deserialization.
- `AgentRawContentTypeRegistrar`.
- `RawContentJackson.registerPluginSubtypes(...)` maps JSON `agent_timeline` to `AgentTimelineContent`.
- `AgentTimelineChunker`.
- Episode boundary detection.
- Phase splitting for large episodes.
- Secret redaction.
- Deterministic episode IDs.
- Category mapping.
- Insight type mapping.
- Core-compatible entity and causal graph hint construction.
- Tool-only timeline behavior.
- Unsuccessful episode behavior.
- Playbook extraction gating.
- Absolute project roots are not persisted by default; `projectRootRaw` requires explicit opt-in.

### Integration Tests

Add plugin integration tests:

- `Memory.builder().rawDataPlugin(new AgentRawDataPlugin(...))` accepts `agent_timeline`.
- Extracting a successful coding timeline produces TOOL items.
- Extracting a coding timeline with explicit failure, fix/conclusion, and later validation evidence produces RESOLUTION items.
- Successful complex episode can produce PLAYBOOK.
- Failed/unresolved episode does not produce PLAYBOOK by default.
- Items are stored under AGENT scope categories only.
- Exact duplicate complete timeline window submission does not duplicate durable memory items.
- Insight building includes playbooks/resolutions/directives.
- `tools` insight type is used for TOOL items.
- JDBC JSON codec round-trips `AgentTimelineContent` when plugin subtype registration is active.
- SQLite/MySQL/Postgres store integration tests continue to read existing RawData rows after the new subtype is registered.

### Client/Hook Tests

Claude Code and Codex integration tests should verify:

- Hook payload normalization.
- Retry spool persistence and replay.
- Stop flush builds one timeline payload.
- Existing transcript ingestion still works.
- Tool event ingestion can be disabled.
- Missing transcript/tool fields fail open.
- Secrets are not written to debug logs in plain text.

### Evaluation

Create coding-agent memory evaluation fixtures:

- Auth/JWT failing test fixed and reused later.
- Payment rounding bug resolved and later queried.
- Project directive learned and enforced later.
- Command validation memory retrieved for related file.
- Repeated workflows consolidated into playbook.

Metrics:

- recall relevance for coding queries
- answer correctness with retrieved memory
- token cost per session
- duplicate item rate
- secret redaction false negatives
- extraction latency

## Rollout Plan

### Phase 1: Core Plugin

- Add `memind-plugin-rawdata-agent`.
- Add `AgentTimelineContent`.
- Add registrar and processor.
- Implement redaction.
- Implement episode chunker.
- Implement deterministic TOOL extraction baseline.
- Implement conservative deterministic RESOLUTION candidates only when the episode contains an explicit failure signal, a later successful validation signal, and evidence tying the fix or conclusion to the outcome.
- Register starter in server.
- Add `tools` default insight type and migration-safe store initialization coverage.
- Add tests.

### Phase 2: LLM Agent Item Extraction

- Add `AgentItemExtractionStrategy`.
- Add structured prompt/response for TOOL, RESOLUTION, PLAYBOOK, DIRECTIVE.
- Add playbook gating.
- Add core-compatible entity hints and causal item hints.
- Add tests with mocked LLM.

### Phase 3: Claude Code and Codex Ingestion

- Add optional tool-event hooks.
- Add timeline buffer/spool.
- Submit timeline on Stop/PreCompact/SessionEnd.
- Keep fail-open behavior.
- Add config flags and docs.

### Phase 4: Retrieval Formatting

- Update Claude/Codex retrieval formatting to group AGENT memories.
- Add category-aware display.
- Add examples and troubleshooting docs.

### Phase 5: Evaluation and Tuning

- Tune Insight Tree grouping for playbooks/resolutions.
- Add coding-agent benchmark fixtures.

### Phase 6: Optional SkillDoc Input

- Add `agent_skilldoc` if importing existing `SKILL.md` and team playbooks becomes a priority.

## Acceptance Criteria

1. Memind server can ingest `rawContent.type = "agent_timeline"` through the normal extraction endpoint.
2. The plugin is loaded through Spring Boot starter and `RawDataPlugin`, not through special-case server code.
3. A successful coding timeline produces AGENT memory items for TOOL.
4. A coding timeline with explicit failure, fix/conclusion, and later validation evidence produces AGENT RESOLUTION memory items.
5. A successful, sufficiently complex coding timeline can produce PLAYBOOK.
6. DIRECTIVE extraction is possible but conservative.
7. No USER categories are emitted by `rawdata-agent`.
8. Exact duplicate complete timeline window submissions do not create duplicate durable memory items.
9. Secret-like values are redacted before RawData persistence, vectorization, segment text, and item extraction.
10. Existing conversation/document/image/audio/toolcall extraction remains compatible.
11. Claude Code and Codex integrations can enable tool/timeline capture without breaking current transcript ingestion.
12. Retrieved AGENT memories are formatted so coding agents can use playbooks, resolutions, tool notes, and directives directly.
13. TOOL memories can participate in AGENT insight building through the `tools` insight type.
14. The plugin works with `MapRawContent` clients before typed client models are released when the server-side `AgentRawContentTypeRegistrar` is loaded.
15. Each extracted item preserves source evidence metadata: `sourceClient`, `sessionId`, `timelineId`, `episodeId`, and `evidenceEventIds`.
16. Default metadata and RawData do not persist absolute local project roots unless `projectRootRaw` capture is explicitly enabled.

## Open Design Decisions

### First-Class AgentEpisode Projection

Recommendation: do not add in v1.

Use RawData + `agent_episode` segment metadata first. Add a first-class projection later only if Memind needs UI browsing, citation, replay, manual curation, or timeline-detail retrieval equivalent to claude-mem.

### `rawdata-toolcall` Internal Adapter

Recommendation: defer.

Keep `rawdata-toolcall` working as-is for compatibility. After `rawdata-agent` is stable, adapt tool-call-only records into `agent_timeline` internally so both paths share the same TOOL extraction and insight mapping.

### Dedicated `/open/v1/agent/timeline` Endpoint

Recommendation: not required for v1.

Use `memory/extract` with `rawContent.type = "agent_timeline"` first. Add endpoint later as a convenience wrapper if client ergonomics demand it.

## Risks and Mitigations

### Risk: Too Much Noise

Mitigation:

- Extract on episode end, not every event.
- Require success for playbooks.
- Use confidence thresholds.
- Use deduplication and source episode metadata.

### Risk: Token Cost

Mitigation:

- Deterministic compact segment formatter.
- Truncate large tool outputs.
- Rule-based TOOL extraction.
- LLM extraction only for episode summaries/playbooks.

### Risk: Secret Leakage

Mitigation:

- Redact before formatting segments.
- Avoid file-content capture by default.
- Add tests with common secret formats.
- Preserve redaction metadata.

### Risk: Host-Specific Complexity Leaks Into Core

Mitigation:

- Keep Claude/Codex/OpenClaw/Hermes conversion in integrations.
- Plugin accepts normalized schema only.
- Core only sees RawContent/Segment/MemoryItem.

### Risk: Duplicates From Hook Retries

Mitigation:

- Stable event IDs.
- Deterministic episode IDs.
- Existing MemoryItem deduplication.
- Retry spool only marks submitted after success.

## Final Recommendation

Implement `memind-plugin-rawdata-agent` as the canonical agent work-process input plugin. Keep the first version focused on `agent_timeline`, episode chunking, AGENT item extraction, privacy, idempotency, `tools` insight support, and Claude/Codex ingestion. Do not add a first-class Observation model yet; represent observation-like evidence as `agent_episode` segments and metadata.

This design gives Memind a coding-agent memory path comparable to agentmemory and claude-mem while preserving Memind's strongest advantage: a general, extensible memory kernel that can evolve agent experience into structured long-term understanding.
