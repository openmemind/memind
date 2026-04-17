# Graph Memory Phase 4A Deep Retrieval Design

Date: 2026-04-17

## Status

Proposed design. Derived from the approved phase 4A direction. Pending final user review before implementation planning.

## Summary

This document defines phase 4A of `memind` graph memory: bounded graph-assisted retrieval inside `DeepRetrievalStrategy`.

The design extends the graph retrieval foundation established in phase 3, but it does not yet enter the second half of phase 4. In particular:

- `DeepRetrievalStrategy` gains graph-assisted item enrichment
- the graph channel remains bounded, additive, and one-hop only
- graph work is inserted only into the deep slow path after direct candidate formation
- the existing phase 3 graph assistant is generalized into a truly strategy-neutral runtime collaborator

Phase 4A explicitly does **not** include:

- derived thread/story persistence
- thread/story retrieval
- coarse-to-fine retrieval over derived story structures
- query-first graph routing

This keeps the phase focused and allows `DeepRetrievalStrategy` to benefit from the typed item graph before the larger story-layer work of phase 4B begins.

## Goals

- Add graph support to `DeepRetrievalStrategy` without weakening the current deep retrieval baseline.
- Reuse the existing phase 3 typed item graph and bounded one-hop graph expansion model.
- Keep graph retrieval additive rather than replacing deep retrieval's direct channels, query expansion, or reranking.
- Preserve the current sufficiency fast path and keep graph work out of it.
- Make the graph assistant genuinely strategy-neutral so both simple and deep retrieval use the same bounded graph engine.
- Maintain disabled-mode, timeout, and degraded-mode behavior consistent with phase 3.

## Non-Goals

- Changing the semantics of `SufficiencyGate`.
- Feeding graph-assisted candidates into sufficiency input in this phase.
- Changing `TypedQueryExpander` routing behavior in this phase.
- Adding multi-hop traversal, path search, or graph-primary retrieval.
- Introducing derived thread/story storage or retrieval.
- Replacing reranking with graph scoring.
- Adding new graph storage tables or new graph query contracts beyond what phase 3 already requires.

## Existing Context

`DeepRetrievalStrategy` already has two distinct operating modes:

1. `fast path`
   If `SufficiencyGate` decides that the current insight plus item evidence is sufficient, the strategy returns immediately.
2. `slow path`
   If the initial evidence is insufficient, the strategy continues with BM25 probing, vector retrieval, typed query expansion, multi-channel routing, weighted RRF merge, rerank, and final result shaping.

Today, phase 3 graph-assisted retrieval is implemented only for `SimpleRetrievalStrategy`. The existing runtime assistant boundary is not yet actually strategy-neutral because the interface is still typed against `SimpleStrategyConfig`.

That mismatch must be corrected as part of phase 4A. Otherwise deep retrieval would either:

- duplicate the graph engine under a second interface, or
- incorrectly depend on simple-specific config types

Neither is acceptable for the project architecture.

## Phase Boundary

Phase 4 of the graph-memory roadmap was defined as:

- graph support in deep retrieval
- derived thread/story storage and derivation
- coarse-to-fine retrieval over story threads

This document covers only the first of those three items. It should therefore be treated as `phase 4A`, while the story-layer work remains for `phase 4B`.

## Approach Comparison

### Recommended: graph assist after direct deep candidate formation and before rerank

The recommended design is:

1. keep the existing deep retrieval pipeline up to direct merged item candidates
2. build a bounded direct candidate window
3. run bounded graph expansion from that direct window
4. fuse graph candidates into the direct window
5. pass the fused window into rerank

This is the correct integration point because:

- it preserves direct retrieval as the backbone
- graph enriches recall only after direct evidence already exists
- rerank can see graph-admitted candidates instead of receiving a graph-blind pool
- graph logic stays outside sufficiency semantics

### Rejected: run graph assist per deep retrieval channel

This would run graph expansion separately for BM25 probe, vector retrieval, and each expanded query channel.

This is rejected because it would:

- multiply graph I/O by channel count
- blur the meaning of direct score versus graph score
- complicate weighting and deduplication
- create a larger latency surface with weak quality guarantees

### Rejected: run graph assist only after rerank

This is rejected because rerank would never see the graph-admitted items. That would either:

- force graph candidates to bypass rerank entirely, or
- require a second rerank stage

Both are worse than inserting graph assist before rerank.

## Design Principles

### 1. Deep retrieval remains direct-first

Graph candidates are introduced only after deep retrieval has already built a bounded direct item list.

### 2. Sufficiency remains graph-blind in phase 4A

The fast path should preserve today's latency and semantics. Graph expansion begins only after `SufficiencyGate` returns `insufficient`.

### 3. One shared graph engine

`SimpleRetrievalStrategy` and `DeepRetrievalStrategy` should use the same bounded graph assistant implementation, with strategy-specific policy differences expressed only through config.

### 4. One hop only

Phase 4A inherits phase 3's one-hop expansion rule exactly. No multi-hop chaining is allowed.

### 5. Graph stays weaker than direct

Direct candidates remain primary. Graph candidates may enrich recall and improve context reconstruction, but they must not become the dominant scoring channel.

### 6. Rerank remains authoritative for final ordering

If rerank is enabled, it should receive the graph-enriched candidate pool. Graph assist does not replace rerank.

## Target Architecture

### Shared graph settings contract

Phase 4A must generalize the retrieval graph assistant contract so it is no longer typed against `SimpleStrategyConfig`.

The recommended design is:

- add a shared runtime interface such as `RetrievalGraphSettings`
- let `SimpleStrategyConfig.GraphAssistConfig` implement that interface
- add `DeepStrategyConfig.GraphAssistConfig` implementing the same interface
- change `RetrievalGraphAssistant.assist(...)` to accept `RetrievalGraphSettings`

The shared settings contract should expose only the fields the graph assistant actually needs:

- `enabled`
- `maxSeedItems`
- `maxExpandedItems`
- `maxSemanticNeighborsPerSeed`
- `maxTemporalNeighborsPerSeed`
- `maxCausalNeighborsPerSeed`
- `maxEntitySiblingItemsPerSeed`
- `maxItemsPerEntity`
- `graphChannelWeight`
- `minLinkStrength`
- `minMentionConfidence`
- `protectDirectTopK`
- `timeout`

This keeps the graph assistant strategy-neutral while leaving each strategy free to own its own config type.

### Shared implementation

`DefaultRetrievalGraphAssistant` remains the concrete bounded graph engine used by both strategies.

Phase 4A should not create a separate deep-specific graph assistant implementation. The current phase 3 behavior should continue to define the graph engine:

- materialize direct seeds as real `MemoryItem`s
- re-apply `ItemRetrievalGuard`
- expand one hop through `item_links`
- expand one hop through reverse `item_entity_mentions`
- treat overlap with existing direct hits as metadata-only
- score graph candidates conservatively
- pin a direct prefix before fusing graph results into the tail
- degrade to direct-only results on timeout or error

### Assembly

`MemoryRetrievalAssembler` should construct one traced retrieval graph assistant and inject it into both:

- `SimpleRetrievalStrategy`
- `DeepRetrievalStrategy`

The assembler should not decide whether graph assist is active for a request. Request-level config remains authoritative.

## Retrieval Pipeline Design

### Step 1: keep Tier 1, Tier 2 init, and sufficiency input unchanged

The following behavior remains unchanged in phase 4A:

- initial insight retrieval
- initial item vector retrieval
- BM25 probe
- sufficiency input construction
- `SufficiencyGate` semantics

Graph assist is intentionally excluded from sufficiency input so the fast path keeps today's behavior and cost profile.

### Step 2: fast path remains graph-free

If `SufficiencyGate` returns `sufficient`, `DeepRetrievalStrategy` should return immediately using the current fast path.

Phase 4A must not:

- invoke graph assist on the fast path
- change sufficiency thresholds to compensate for graph
- append graph-only evidence to a sufficient result

This is an explicit design choice, not an omission.

### Step 3: graph assist lives in the slow path only

If `SufficiencyGate` returns `insufficient`, the strategy continues with today's deep slow path:

- determine strong versus non-strong signal
- optionally execute typed query expansion
- execute routed retrieval channels
- build ranked channel lists
- perform weighted RRF merge
- backfill `occurredAt` for BM25-only results
- apply BM25-only time decay

Graph assist begins only after those direct candidate formation steps are complete.

### Step 3A: slow-path degradation semantics are explicit

Phase 4A fixes one explicit degradation rule here so implementation does not drift:

- if typed query expansion itself fails, the strategy must degrade to the best available
  direct slow-path candidate formation built from the already available initial direct channels
  only
- in that fallback, the strategy must still continue through the normal downstream stages:
  direct merge, `occurredAt` backfill, BM25-only time decay, direct candidate window bounding,
  graph assist eligibility, rerank, and final result assembly
- individual expanded channel failures remain best-effort and should drop only the failed
  channel rather than collapsing the whole slow path
- if the strategy cannot build any bounded direct candidate window after fallback, graph assist
  is skipped and the best available direct-only result is returned

For clarity, the phase 4A fallback target is not a vector-only shortcut. It is the normal deep
slow path with the unavailable expanded channels removed.

### Step 4: build a bounded direct candidate window for graph assist

Before graph assist runs, deep retrieval should derive a graph input window from the already merged direct item candidates.

The direct graph-input window must be:

- sorted by final direct score after time decay
- bounded to `tier2.topK`
- item-only
- based on the direct merged list before rerank

If the slow path used strong BM25 signal and skipped typed query expansion, the same rule still applies: graph assist runs on the final direct merged window produced by that path.

This bounded direct candidate window is the only source of graph seeds in phase 4A.

### Step 5: materialize graph-eligible seeds

The graph assistant must treat the direct window as a source of potential seeds, not as automatically valid graph seeds.

Before any direct result becomes a graph seed, it must be:

- materialized as a concrete `MemoryItem`
- verified to still exist
- re-checked through `ItemRetrievalGuard`

If a direct candidate cannot be materialized or fails the guard, it remains a valid direct retrieval result but cannot seed graph expansion.

### Step 6: perform bounded one-hop graph expansion

Graph expansion rules remain the same as phase 3:

- only one hop is allowed
- supported link families are `semantic`, `temporal`, `causal`, and shared-entity sibling expansion
- fanout limits are applied per seed and per relation family
- per-entity reverse mention expansion is capped by `maxItemsPerEntity`
- special entities are excluded from default sibling expansion
- overlap with an existing direct hit does not add score

Phase 4A must not introduce multi-hop deep retrieval.

### Step 7: fuse graph candidates before rerank

The graph assistant returns a fused item candidate pool.

The fusion policy should remain the same as phase 3:

- preserve a pinned direct prefix of size `protectDirectTopK`
- fuse graph candidates only into the unpinned tail
- keep `graphChannelWeight < 1.0`
- admit graph candidates conservatively

Phase 4A fixes one explicit cardinality invariant here:

- let `directWindow` be the bounded pre-graph candidate list produced in step 4
- the graph-enriched candidate pool passed into rerank must contain at most `directWindow.size()`
  items
- because `directWindow` is already bounded to `tier2.topK`, the rerank input after graph fusion
  is therefore also bounded to `tier2.topK`

Graph assist may replace candidates inside the bounded direct window, but it must not increase the
window size.

### Step 8: rerank sees the graph-enriched pool

If rerank is enabled, rerank should operate on the graph-enriched candidate pool rather than the graph-blind direct pool.

This is a key requirement of phase 4A. Without it, graph-admitted candidates would either:

- bypass rerank, or
- be invisible to the final ranking stage

If rerank is disabled, the graph-enriched pool becomes the final item candidate pool for the remaining deep pipeline.

### Step 9: raw-data aggregation and result assembly stay unchanged

After rerank, the rest of the deep pipeline should stay the same:

- aggregate items to raw data when enabled
- build final item and raw-data results
- keep insight results and expanded insight results unchanged

Phase 4A does not alter the deep result envelope.

## Configuration Design

### Builder-level config

`DeepRetrievalOptions` should gain a new `graphAssist` field.

The recommended builder type is a dedicated `DeepRetrievalGraphOptions` rather than reusing `SimpleRetrievalGraphOptions` directly. That keeps the public builder API explicit while still allowing both builder types to map to the same runtime settings shape.

The builder-level defaults should leave graph assist disabled.

### Runtime strategy config

`DeepStrategyConfig` should gain a new nested `GraphAssistConfig`.

That config should:

- implement the shared `RetrievalGraphSettings` runtime interface
- use the same validation rules as simple retrieval graph assist
- provide defaults with `enabled=false`
- support JSON deserialization that preserves compatibility with legacy deep strategy payloads that do not yet contain `graphAssist`

### Recommended default values

Phase 4A should start with conservative deep defaults:

- `enabled = false`
- `maxSeedItems = 8`
- `maxExpandedItems = 16`
- `maxSemanticNeighborsPerSeed = 2`
- `maxTemporalNeighborsPerSeed = 2`
- `maxCausalNeighborsPerSeed = 2`
- `maxEntitySiblingItemsPerSeed = 4`
- `maxItemsPerEntity = 8`
- `graphChannelWeight = 0.30`
- `minLinkStrength = 0.55`
- `minMentionConfidence = 0.70`
- `protectDirectTopK = 5`
- `timeout = 300ms`

These values are slightly roomier than the simple defaults because deep retrieval already builds a larger candidate pool and typically has rerank available, but they remain clearly subordinate to direct retrieval.

## Error Handling and Degradation

Phase 4A should preserve phase 3 degradation behavior:

- if graph assist is disabled, return the direct deep candidate window unchanged
- if the graph store is unavailable, return the direct deep candidate window unchanged
- if the graph channel times out, return the direct deep candidate window unchanged and mark the graph assist result as timed out
- if the graph channel fails for any other reason, return the direct deep candidate window unchanged and mark the graph assist result as degraded

Graph errors must not fail the retrieval request.

`GraphQueryBudgetContext` should continue to scope only the graph work, not the full deep retrieval pipeline.

## Observability

Phase 4A should reuse the same tracing shape already introduced for phase 3 graph assist:

- graph enabled
- seed count
- link expansion count
- entity expansion count
- deduped candidate count
- admitted graph candidate count
- displaced direct count
- overlap count
- skipped over-fanout entity count
- timeout
- degraded

`TracingRetrievalGraphAssistant` should be updated to read from the shared graph settings contract rather than from `SimpleStrategyConfig`.

No new tracing vocabulary is required in phase 4A.

## Compatibility and Migration

### Runtime compatibility

Existing deep retrieval requests without graph assist configuration must continue to work unchanged.

In particular:

- legacy `DeepStrategyConfig` JSON without `graphAssist` must deserialize successfully
- the missing field must default to disabled graph assist
- existing deep retrieval behavior must remain unchanged unless graph assist is explicitly enabled

### Data compatibility

Phase 4A does not require any new storage migration beyond the persistent graph backend already required by phase 3.

Deep retrieval must continue to function correctly when:

- graph storage exists but is partially populated
- graph storage is disabled
- graph storage is available only for some items

## Testing Requirements

Phase 4A implementation must include at least the following tests.

### Deep strategy behavior tests

Add a dedicated `DeepRetrievalStrategyTest` covering:

- sufficient fast path does not invoke graph assist
- insufficient slow path invokes graph assist before rerank
- strong-signal slow path still invokes graph assist after direct candidate formation
- query expansion failure degrades to the base direct slow path rather than a vector-only shortcut
- graph assist receives post-decay, post-sort, bounded direct candidates
- graph assist timeout degrades to direct-only deep candidates
- graph assist failure degrades to direct-only deep candidates
- rerank receives graph-enriched candidates when graph assist is enabled
- rerank input after graph fusion never exceeds the bounded direct window size

### Config and compatibility tests

Extend strategy config tests to cover:

- legacy deep strategy JSON without `graphAssist`
- invalid deep graph assist config fails fast
- deep graph assist defaults preserve deep strategy name and existing non-graph fields

### Builder and assembly tests

Extend builder tests to cover:

- `DeepRetrievalOptions.graphAssist` maps into runtime `DeepStrategyConfig.GraphAssistConfig`
- `MemoryRetrievalAssembler` injects the traced retrieval graph assistant into deep retrieval
- simple retrieval behavior remains unchanged after the shared graph settings refactor

### Tracing tests

Update tracing tests so they verify that:

- the tracing decorator still emits the existing graph attributes
- the decorator works for both simple and deep strategy config paths through the shared graph settings contract

## Out of Scope for Phase 4A

The following work is explicitly deferred to phase 4B or later:

- derived thread/story persistence
- story derivation jobs
- thread-level recall
- candidate narrowing to thread members
- coarse-to-fine retrieval over story structures
- query-time use of `entity_cooccurrences`
- direction-sensitive graph scoring specialized for deep retrieval prompts

## Final Decisions

The following decisions are intentionally fixed by this document:

- graph assist is added to `DeepRetrievalStrategy` only in the insufficient slow path
- sufficiency input and sufficiency fast path stay graph-free
- typed query expansion failure degrades to the base direct slow path, not a vector-only shortcut
- graph assist runs after direct deep candidate formation and before rerank
- graph assist may replace candidates inside the bounded direct window, but may not enlarge it
- the phase 3 graph engine is reused rather than duplicated
- the graph assistant contract is generalized away from `SimpleStrategyConfig`
- phase 4A does not include any story/thread retrieval work

## Scope for Planning

Implementation planning for phase 4A should target only the deep retrieval graph integration described here plus the minimal config, assembly, compatibility, and test changes required to support it cleanly.

The implementation plan should explicitly exclude phase 4B story-layer work.
