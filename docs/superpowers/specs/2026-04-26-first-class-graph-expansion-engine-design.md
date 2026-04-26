# First-Class GraphExpansionEngine Design

## 1. Background

The current SIMPLE retrieval graph phase already exposes a `GraphItemChannel`, but `GraphExpansionEngine` is still a compatibility wrapper around `RetrievalGraphAssistant`:

```text
GraphItemChannel
  -> GraphExpansionEngine
    -> RetrievalGraphAssistant.assist(...)
      -> graph expansion + assist/expand fusion
    -> filter seed ids
    -> return graph-only items
```

This design is stable and low risk, but the boundary is not ideal. `GraphExpansionEngine` looks like a graph expansion core, while in practice it depends on assistant fusion output. Long term, that makes SIMPLE graph retrieval less independent and makes graph behavior harder to explain, test, tune, and trace.

This spec turns `GraphExpansionEngine` into a first-class graph-only expansion core. `DefaultGraphItemChannel` and `DefaultRetrievalGraphAssistant` will both depend on it.

## 2. Goals

- Make `GraphExpansionEngine` an independent graph-only expansion core.
- Stop `DefaultGraphItemChannel` from calling `RetrievalGraphAssistant.assist()` indirectly.
- Keep `DefaultRetrievalGraphAssistant` public behavior unchanged while making it reuse `GraphExpansionEngine` internally.
- Make SIMPLE graph retrieval semantically clear: direct channel results and graph-only channel results are independently fused.
- Avoid public API changes.
- Avoid LLM calls and external dependencies.
- Preserve current graph retrieval quality and scoring behavior in the first version.

## 3. Non-Goals

The first version will not:

- Copy Hindsight's PostgreSQL CTE implementation.
- Introduce `fact_type` routing.
- Add observation-specific graph expansion.
- Redesign graph scoring.
- Add user-facing graph configuration switches.
- Change `RetrievalGraphSettings` user-visible semantics.
- Add final `RetrievalResult` graph metadata unless existing structures naturally support it.

## 4. Recommended Approach

Use an **Engine Core + Assistant Fusion Adapter** architecture:

```text
GraphExpansionEngine
  ├─ materialize seeds
  ├─ load graph links
  ├─ load entity mentions
  ├─ apply fanout guards
  ├─ apply ItemRetrievalGuard
  ├─ compute graph-only scores
  └─ return GraphExpansionResult

DefaultGraphItemChannel
  └─ call GraphExpansionEngine.expand(...)
     return graph-only items

DefaultRetrievalGraphAssistant
  └─ call GraphExpansionEngine.expand(...)
     apply ASSIST / EXPAND fusion
```

The dependency direction should become:

```text
RetrievalGraphAssistant -> GraphExpansionEngine
GraphItemChannel       -> GraphExpansionEngine
```

It should no longer be:

```text
GraphExpansionEngine -> RetrievalGraphAssistant
```

## 5. Core Architecture

```text
                    ┌──────────────────────────┐
                    │  GraphExpansionEngine    │
                    │                          │
                    │  pure graph-only core    │
                    └────────────┬─────────────┘
                                 │
              ┌──────────────────┴──────────────────┐
              │                                     │
              ▼                                     ▼
┌────────────────────────────┐       ┌──────────────────────────────┐
│ DefaultGraphItemChannel    │       │ DefaultRetrievalGraphAssistant │
│                            │       │                              │
│ SIMPLE graph channel       │       │ legacy assist / expand       │
│ graph-only result          │       │ fusion behavior              │
└────────────────────────────┘       └──────────────────────────────┘
```

`GraphExpansionEngine` only produces graph expansion results. It does not protect direct top-K items, perform ASSIST / EXPAND fusion, or perform final SIMPLE RRF fusion.

## 6. GraphExpansionEngine Responsibilities

`GraphExpansionEngine.expand(context, config, settings, seeds)` will follow this flow:

```text
expand(context, config, settings, seeds)
  │
  ├─ validate enabled / seeds
  ├─ build directIds
  ├─ materializeEligibleSeeds()
  ├─ collectGraphCandidates()
  │   ├─ adjacent item links
  │   │   ├─ SEMANTIC
  │   │   ├─ TEMPORAL
  │   │   └─ CAUSAL
  │   ├─ entity sibling expansion
  │   ├─ fanout guard
  │   └─ candidate item loading
  ├─ filter candidates by ItemRetrievalGuard
  ├─ rankGraphCandidates()
  └─ return GraphExpansionResult
```

`GraphExpansionEngine` should own the low-level graph expansion logic that currently lives inside `DefaultRetrievalGraphAssistant`.

### Candidate Exclusion Rule

For SIMPLE Graph Channel semantics, `GraphExpansionResult.graphItems()` should exclude all direct input ids. This makes the graph channel represent only incremental graph expansion contribution.

Assistant fusion can still account for overlap internally if it needs to preserve existing assist semantics, but graph channel output should remain graph-only.

## 7. GraphExpansionResult Semantics

`GraphExpansionResult` will continue to carry graph candidates and diagnostics:

```text
GraphExpansionResult
  ├─ graphItems
  ├─ graphEnabled
  ├─ degraded
  ├─ timedOut
  ├─ seedCount
  ├─ linkExpansionCount
  ├─ entityExpansionCount
  ├─ dedupedCandidateCount
  ├─ overlapCount
  └─ skippedOverFanoutEntityCount
```

The first-class engine must define these semantics explicitly:

- `graphItems` contains ranked graph-only candidates and excludes direct input ids.
- `graphEnabled` mirrors the effective graph setting.
- `degraded` means expansion failed or timed out at the caller boundary.
- `timedOut` is set by the reactive caller layer, not by synchronous engine logic.
- `seedCount` is the number of materialized eligible seeds.
- `linkExpansionCount` counts admitted non-overlap adjacent-link expansions before final item filtering.
- `entityExpansionCount` counts admitted non-overlap entity sibling expansions before final item filtering.
- `dedupedCandidateCount` is the number of graph candidates after deduplication and item guard filtering, before ranking output is limited by downstream consumers.
- `overlapCount` is diagnostic only and does not imply overlap items are returned by the graph channel.
- `skippedOverFanoutEntityCount` counts entity keys skipped by fanout protection.

## 8. DefaultGraphItemChannel Behavior

`DefaultGraphItemChannel` keeps its current reactive boundary:

```text
retrieve(context, config, settings, seeds)
  ├─ disabled / no seeds -> empty
  ├─ Mono.fromCallable(engine.expand)
  ├─ boundedElastic
  ├─ timeout
  ├─ TimeoutException -> degraded(timedOut=true)
  └─ other error -> degraded(timedOut=false)
```

The only behavioral change is that `engine.expand()` no longer calls `graphAssistant.assist()`.

This makes SIMPLE graph retrieval a true independent channel:

```text
directItems from vector / BM25 / temporal / thread
graphItems from GraphExpansionEngine
ResultMerger.merge(directItems, graphItems, 1.0, graphChannelWeight)
```

## 9. DefaultRetrievalGraphAssistant Behavior

`DefaultRetrievalGraphAssistant` public behavior remains unchanged:

```text
assist(context, config, graphSettings, directItems)
  ├─ disabled / empty -> direct only
  ├─ timeout/error degradation
  └─ expandAndFuse(...)
```

Internally it should become:

```text
expandAndFuse(...)
  ├─ graphResult = graphExpansionEngine.expand(...)
  ├─ rankedGraph = graphResult.graphItems()
  ├─ ASSIST -> protect direct topK + fuse tail
  ├─ EXPAND -> allow graph candidates to displace direct tail
  └─ build RetrievalGraphAssistResult stats from graphResult
```

This keeps existing assistant behavior while removing low-level graph candidate collection from the assistant class.

## 10. Scoring Policy

The first version must preserve existing scoring semantics:

```text
baseScore = seedRelevance × relationWeight × relationStrength

relation weights:
  SEMANTIC       1.00
  TEMPORAL       0.90
  CAUSAL         0.95
  ENTITY_SIBLING 0.85

semantic:
  accumulate multi-seed evidence with decay, then apply tanh

non-semantic:
  use the best temporal / causal / entity score

candidate score:
  max(semanticScore, nonSemanticScore) × recencyAdjustment
```

The first version should not switch to Hindsight-style additive scoring. That would mix a structural refactor with a ranking-policy change and increase risk.

## 11. SIMPLE Mode Flow After Refactor

```text
SIMPLE Phase A
  ├─ Insight Tier
  ├─ ItemVector Channel
  ├─ ItemBM25 Channel
  └─ TemporalItem Channel
        │
        ▼
RRF / TimeDecay / sort
        │
        ▼
MemoryThreadAssist
        │
        ▼
candidatePool
        │
        ▼
┌─────────────────────────────────────────────────────┐
│ Graph Phase                                          │
│                                                     │
│ DefaultGraphItemChannel                              │
│   ├─ timeout/error degradation                       │
│   └─ GraphExpansionEngine.expand(...)                │
│        ├─ materialize seeds                          │
│        ├─ semantic / temporal / causal links          │
│        ├─ entity sibling expansion                   │
│        ├─ fanout guard                               │
│        ├─ ItemRetrievalGuard                         │
│        └─ graph-only ranked candidates               │
│                                                     │
│ ResultMerger.merge(                                  │
│   directItems, graphItems, 1.0, graphChannelWeight    │
│ )                                                    │
└─────────────────────────────────────────────────────┘
        │
        ▼
finalizeResult()
```

## 12. Relationship to Hindsight

The design borrows Hindsight's retrieval principles, not its implementation:

```text
Hindsight principles:
  seed-based graph retrieval
  entity / semantic / causal expansion
  high-fanout protection
  timeout degradation
  graph channel participates in fusion

Memind implementation:
  seed-based graph expansion
  entity / semantic / temporal / causal relations
  GraphOperations abstraction
  ItemRetrievalGuard
  TimeDecay
  assistant and channel share one engine
```

Memind should not copy Hindsight's `fact_type`, PostgreSQL CTE, or observation-specific path in this refactor because memind has a different domain model, store abstraction, and Java API boundary.

## 13. Compatibility

Public API compatibility:

- Do not change `MemoryRetriever`.
- Do not change `RetrievalRequest`.
- Do not change `RetrievalResult`.
- Do not change `SimpleRetrievalOptions` public user entry points.
- Do not change `RetrievalGraphSettings` user-visible configuration.

Internal compatibility:

- Keep `DefaultRetrievalGraphAssistant(MemoryStore store)`.
- Add an internal constructor that accepts `GraphExpansionEngine` for tests and assembly.
- Let `MemoryRetrievalAssembler` create one shared `GraphExpansionEngine` and pass it to assistant and channel.
- Preserve no-op and legacy assistant behavior.

## 14. Error Handling

Graph channel behavior:

```text
timeout -> GraphExpansionResult.degraded(graphEnabled=true, timedOut=true)
error   -> GraphExpansionResult.degraded(graphEnabled=true, timedOut=false)
```

Graph assistant behavior:

```text
timeout -> RetrievalGraphAssistResult.degraded(directItems, enabled=true, timedOut=true)
error   -> RetrievalGraphAssistResult.degraded(directItems, enabled=true, timedOut=false)
```

Engine behavior:

- Do not swallow exceptions.
- Do not implement reactive timeout inside the engine.
- Do not return partial results in the first version.
- Return empty for disabled settings, no seeds, or no eligible graph operations.

Partial degradation, such as dropping entity expansion while retaining semantic and causal expansion, is a future enhancement. The current store abstraction performs multiple Java-level graph operations rather than one cancellable SQL CTE, so partial degradation needs a separate design.

## 15. Testing Strategy

### GraphExpansionEngineTest

Cover low-level graph expansion behavior:

- Disabled settings returns empty.
- Empty seeds returns empty.
- Direct seed ids are excluded from graph output.
- Semantic links expand candidates.
- Temporal links expand candidates.
- Causal links expand candidates.
- Entity siblings expand candidates.
- High-fanout entity guard is applied.
- `ItemRetrievalGuard` is applied.
- Semantic convergence across seeds keeps current ordering semantics.
- Non-semantic families keep max-score semantics.
- Stats match expansion counts.

### DefaultGraphItemChannelTest

Cover channel boundary behavior:

- Normal expansion returns graph-only candidates.
- Timeout returns degraded timed-out result.
- Engine error returns degraded non-timeout result.
- Disabled settings or no seeds does not call the engine.

### DefaultRetrievalGraphAssistantTest

Keep assistant tests focused on fusion behavior:

- ASSIST protects direct top-K.
- EXPAND allows graph candidates to displace direct tail.
- Degraded result preserves direct items.
- Stats are copied from `GraphExpansionResult`.
- Low-level candidate collection tests move to `GraphExpansionEngineTest`.

## 16. Migration Plan

Implement in small steps:

```text
Step 1: Change GraphExpansionEngine dependencies
  from RetrievalGraphAssistant
  to MemoryStore

Step 2: Move low-level expansion logic
  from DefaultRetrievalGraphAssistant
  to GraphExpansionEngine

Step 3: Refactor DefaultRetrievalGraphAssistant
  remove duplicated low-level methods
  call GraphExpansionEngine

Step 4: Update MemoryRetrievalAssembler
  create a shared GraphExpansionEngine
  pass it to assistant and channel

Step 5: Migrate tests
  low-level graph tests -> GraphExpansionEngineTest
  assistant tests -> fusion semantics only

Step 6: Verify
  mvn -pl memind-core test
  mvn test
```

## 17. Risks and Controls

| Risk | Control |
| --- | --- |
| Candidate ordering changes unexpectedly | Preserve existing scoring and ordering logic exactly in the first version. |
| Stats become inconsistent | Add explicit tests for every `GraphExpansionResult` diagnostic field. |
| Assistant fusion behavior regresses | Keep focused `DefaultRetrievalGraphAssistantTest` coverage for ASSIST and EXPAND modes. |
| SIMPLE graph channel returns direct duplicates | Enforce direct input id exclusion in `GraphExpansionEngineTest`. |
| Refactor becomes too broad | Do not change scoring, public API, settings, or final result metadata in this refactor. |

## 18. Design Conclusion

This refactor is valuable because it fixes the dependency direction rather than adding abstraction for its own sake.

Current direction:

```text
GraphExpansionEngine -> RetrievalGraphAssistant
```

Target direction:

```text
RetrievalGraphAssistant -> GraphExpansionEngine
GraphItemChannel       -> GraphExpansionEngine
```

After this change, SIMPLE mode's graph phase becomes a true first-class channel. `DefaultRetrievalGraphAssistant` also becomes cleaner: it adapts graph expansion results into ASSIST / EXPAND fusion instead of owning both low-level graph traversal and fusion policy.

This design is aligned with memind's long-term open-source architecture goals: clear boundaries, explainable retrieval behavior, focused tests, and conservative incremental evolution.
