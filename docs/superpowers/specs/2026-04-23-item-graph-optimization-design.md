# Memind Item Graph Optimization Design

**Date:** 2026-04-23
**Status:** Proposed
**Scope:** `memind-core` item-graph planning, temporal/causal relation quality, graph-assisted retrieval expansion, and `ItemGraphOptions` maintainability

## 1. Context

`memind`'s `item graph` is already a real subsystem, not a placeholder:

- extraction can emit entity and causal graph hints
- structured graph hints are normalized and resolved before commit
- temporal and semantic links are derived post-persistence
- retrieval can expand one hop through graph relations
- tracing already exposes substantial temporal and semantic graph metrics

The current implementation is directionally correct and bounded, but several parts are still coarse:

- structured graph failures can silently disappear behind empty results
- planner execution still serializes independent temporal and semantic work
- temporal link strength does not distinguish overlap from distant "before"
- graph-assisted retrieval performs avoidable per-seed adjacent-link reads
- causal hint normalization accepts low-confidence edges too readily
- `ItemGraphOptions` has reached the point where manual copy-through updates are easy to miss

None of these are blocking correctness failures. They are quality, observability, and efficiency gaps in a subsystem that is already worth hardening.

## 2. Problem Statement

The current implementation has six concrete shortcomings.

### 2.1 Structured graph degradation is invisible

`DefaultItemGraphPlanner.resolveStructuredBatch(...)` catches runtime failures and degrades to `ResolvedGraphBatch.empty()`, but does not log, emit a dedicated stat, or surface the degradation to tracing.

This means operators can observe "no structured graph output" without being able to distinguish:

- a legitimate empty structured batch
- a failed structured batch stage

### 2.2 Planner latency is higher than necessary

Temporal and semantic linking are independent planner stages, but `DefaultItemGraphPlanner.plan(...)` currently executes them sequentially.

### 2.3 Temporal link strength is too coarse

All temporal links are currently written with strength `1.0`, even though:

- `overlap` should be stronger than distant `before`
- retrieval and thread scoring already consume link strength

The graph therefore stores temporal adjacency shape, but not temporal confidence shape.

### 2.4 Retrieval graph expansion does unnecessary N+1 reads

`DefaultRetrievalGraphAssistant.collectGraphCandidates(...)` currently calls `listAdjacentItemLinks(...)` once per seed, even though the store interface already supports batch seed lookup.

### 2.5 Weak causal hints are stored too eagerly

`CausalHintNormalizer` validates causal direction and type, but does not reject low-confidence causal hints before persistence.

### 2.6 `ItemGraphOptions` is becoming easy to update incorrectly

`ItemGraphOptions` already has 16 fields, but its handwritten `withXxx()` methods still reconstruct the full record field-by-field.

That style is still workable today, but it increases the chance of omission every time new fields are added.

## 3. Design Goals

This design has five goals.

### 3.1 Improve structured-graph observability

Structured graph failure should be visible in logs, stats, and tracing without changing fail-open behavior.

### 3.2 Reduce avoidable planner and retrieval overhead

Independent planner work should no longer be serialized, and retrieval graph expansion should avoid redundant adjacent-link round-trips.

### 3.3 Improve graph edge quality conservatively

Temporal and causal edges should better reflect confidence and distance, but without destabilizing the graph subsystem.

### 3.4 Preserve current bounded architecture

This iteration must not:

- introduce new graph storage tables
- redesign item-graph commit flow
- add configuration sprawl across all new thresholds

### 3.5 Keep rollout risk explicit

Low-risk hardening work should be separable from behavior-changing scoring work.

## 4. Non-Goals

This iteration intentionally does not do the following.

### 4.1 No graph schema redesign

No new graph table, materialized view, or storage family is introduced.

### 4.2 No broad threshold-config surface expansion

New temporal and causal thresholds remain internal constants in this iteration unless later evidence justifies externalizing them.

### 4.3 No rewrite of item-graph architecture

Planner, materializer, and retrieval graph assistant remain the existing subsystem boundaries.

### 4.4 No retrieval scoring redesign beyond necessary downstream compatibility

This design does not attempt a broad rewrite of retrieval/thread scoring weights. It only accounts for the fact that those systems already consume graph link strength.

## 5. Recommended Design

## 5.1 Structured batch degradation must become explicit

`DefaultItemGraphPlanner` should stop inferring structured-stage success from the shape of the resolved batch.

Recommended change:

- add an internal structured batch result wrapper such as `StructuredBatchResolutionResult`
- return both `ResolvedGraphBatch` and `structuredBatchDegraded`
- preserve the current fail-open behavior by still degrading to `ResolvedGraphBatch.empty()` on runtime failure
- add a `warn` log with exception context
- thread the degradation flag into `ItemGraphMaterializationResult.Stats`
- expose the new stat through tracing

Important constraint:

- do not infer structured-stage degradation from "resolved batch is empty"

That inference is invalid because a structured batch can legitimately be empty when extraction emits no durable entity or causal hints.

Representative behavior:

- structured stage succeeds with no graph hints: `structuredBatchDegraded=false`, empty structured outputs
- structured stage throws: `structuredBatchDegraded=true`, empty structured outputs

This preserves runtime resilience while finally making the difference observable.

## 5.2 Temporal and semantic planning should execute in real parallelism

`DefaultItemGraphPlanner.plan(...)` should stop sequencing temporal and semantic planning through a nested `flatMap`.

Recommended change:

- switch to `Mono.zip(...)`
- explicitly schedule synchronous or blocking branches so the work can actually overlap
- preserve family-scoped degrade behavior through the existing `stageFailure()` fallbacks

Important constraint:

- changing `flatMap` to `zip` alone is not enough if both branches still execute synchronously on the same calling thread

The implementation should therefore use explicit scheduling where needed rather than relying on subscription shape alone.

## 5.3 Temporal strength should become distance-aware, but with a cautious floor

`TemporalItemLinker` should stop assigning `1.0` to all temporal relations.

Recommended change:

- add a `computeTemporalStrength(...)` helper
- keep `overlap` at `1.0`
- apply distance-aware decay to `nearby` and `before`
- write the computed value into the persisted `ItemLink`

However, the rollout should be conservative.

Reason:

- retrieval graph expansion already filters links by minimum strength
- thread and insight scoring already consume strongest temporal link strength

A low floor such as `0.3` would materially change downstream behavior by causing many temporal links to stop participating in retrieval at all.

Recommended first-rollout policy:

- use a moderate decay curve
- keep the floor at or above current retrieval thresholds, or only slightly below them with dedicated regression coverage

This still improves temporal quality without turning the change into an uncontrolled retrieval experiment.

## 5.4 Retrieval adjacent-link expansion should batch seeds

`DefaultRetrievalGraphAssistant.collectGraphCandidates(...)` should read adjacent links once for the full seed set instead of once per seed.

Recommended change:

- collect `seedIds`
- perform one `listAdjacentItemLinks(memoryId, seedIds, SUPPORTED_LINK_TYPES)` call
- group returned links by seed membership
- keep per-seed family caps and overlap handling unchanged

Important constraint:

- do not implement grouping as an O(number_of_links * number_of_seeds) nested scan

The grouping should instead map each adjacent link directly to whichever seed ids it touches.

This change is low-risk because the store abstraction and MyBatis implementation already support batched seed adjacency lookup.

## 5.5 Causal normalization should reject weak hints

`CausalHintNormalizer.collectCausalLinks(...)` should add a minimum-strength filter before retaining causal candidates.

Recommended change:

- add `CAUSAL_MIN_STRENGTH = 0.5f`
- filter retained causal hints by `clampScore(relation.strength()) >= CAUSAL_MIN_STRENGTH`

Expected semantics:

- `strength < 0.5` is dropped
- `strength == 0.5` is retained
- `strength == null` remains retained because null currently clamps to `1.0`

This is consistent with the current prompt contract that causal hints should be emitted only for strong backward-looking links.

## 5.6 `ItemGraphOptions` should gain a builder while preserving current APIs

`ItemGraphOptions` should add:

- `toBuilder()`
- an internal `Builder`
- `build()` that still uses the record constructor and existing validation

Current `withXxx()` methods should remain public, but be simplified to delegate through the builder.

This is a maintainability change only. It does not alter behavior, serialization, or current external API shape.

## 6. Implementation Details

### 6.1 Files in scope

- `DefaultItemGraphPlanner`
- `ItemGraphMaterializationResult`
- `TracingItemGraphMaterializer`
- `TemporalItemLinker`
- `DefaultRetrievalGraphAssistant`
- `CausalHintNormalizer`
- `ItemGraphOptions`

### 6.2 Metrics and tracing additions

Add one new graph-materialization stat and trace attribute:

- `structuredBatchDegraded`

No other new metrics are required for this iteration.

### 6.3 Compatibility expectations

This design preserves:

- fail-open planner behavior for structured graph resolution
- current public `ItemGraphOptions` construction paths
- current retrieval graph API surface

This design intentionally changes:

- temporal link strength distribution
- which causal hints survive normalization

## 7. Testing Strategy

### 7.1 Structured batch observability

Add planner tests that force structured normalization or resolution failure and verify:

- `plan()` still completes successfully
- temporal and semantic results still surface
- `structuredBatchDegraded` is true

Add tracing coverage for the new attribute.

### 7.2 Planner parallelism

Existing planner tests must still pass.

Where practical, add a latency-oriented test or deterministic scheduler test showing that temporal and semantic work can overlap rather than serialize.

### 7.3 Temporal strength

Add temporal-linker tests that cover:

- overlap strength
- short-gap nearby/before
- medium-gap nearby/before
- floor behavior

Also run retrieval and thread regression tests because temporal strength is already consumed downstream.

### 7.4 Retrieval batching

Add retrieval-graph tests that verify:

- multi-seed adjacent-link lookup calls the graph store once
- ranking output remains unchanged relative to current behavior

### 7.5 Causal strength filtering

Add causal-normalizer tests that verify:

- `0.3` is dropped
- `0.5` is kept
- `null` is kept

### 7.6 `ItemGraphOptions` builder

Add builder round-trip coverage verifying:

- `toBuilder().build()` reproduces the source object exactly
- legacy `withXxx()` methods still behave identically

## 8. Rollout Plan

To keep risk proportional to change type, implementation should be split into four batches.

### 8.1 Batch 1: low-risk high-value hardening

- structured batch observability
- retrieval adjacent-link batching
- causal minimum-strength filtering

### 8.2 Batch 2: planner latency reduction

- temporal and semantic parallel planning

### 8.3 Batch 3: temporal strength refinement

- distance-aware temporal strength with cautious floor and downstream regression checks

### 8.4 Batch 4: maintainability cleanup

- `ItemGraphOptions` builder

## 9. Risk Assessment

### 9.1 Low risk

- structured batch observability
- retrieval adjacent-link batching
- causal minimum-strength filtering
- `ItemGraphOptions` builder

### 9.2 Medium risk

- planner parallelism

The main failure mode is not correctness but fake parallelism: code that looks concurrent while still executing effectively serially.

### 9.3 Higher behavior risk

- temporal strength decay

This is still worth doing, but only with explicit downstream regression coverage because retrieval, thread, and insight all already consume graph link strength.

## 10. Final Recommendation

All six optimizations are worth implementing on balance. The overall complexity increase is modest and does not meaningfully burden the architecture.

The main caveat is that the six changes are not equally risky:

- items 1, 4, and 5 are straightforward positive-ROI hardening work
- item 2 is worthwhile if implemented as real parallelism rather than cosmetic `zip` conversion
- item 3 is worthwhile but should be rolled out conservatively because it changes downstream scoring behavior
- item 6 is low-impact but cheap maintainability work

The recommended path is therefore to implement all six, but in staged batches rather than as one undifferentiated "small cleanup" change set.
