# Graph Memory History-Level Temporal Linking Design

Date: 2026-04-19

## Status

Proposed design. Derived from the approved direction to keep `memind` temporal linking, but upgrade it from batch-local construction to bounded history-level construction before retrieval-side temporal exploitation is expanded.

## Summary

This document defines the next evolution of `memind` temporal item linking:

- preserve the existing temporal relation semantics: `before`, `overlap`, and `nearby`
- preserve the current graph model where temporal edges are directed item-to-item links
- upgrade temporal candidate scope from same-batch only to history-level bounded lookup
- make historical lookup overlap-aware rather than anchor-only
- keep same-batch temporal linking as part of the same stage rather than splitting the logic
- keep the public configuration surface unchanged
- make the result immediately consumable by the existing graph retrieval and memory-thread derivation paths

The key architectural decision is:

- temporal relation classification remains a `memind` semantic responsibility
- historical candidate lookup becomes a storage responsibility
- temporal link construction moves out of batch-local normalization and into a dedicated post-persistence linker stage

This design does not try to build a global timeline engine. It builds a conservative, bounded, history-aware temporal linking stage that materially improves graph usefulness without expanding into open-ended temporal reasoning or backfill workflows.

## Goals

- Upgrade temporal linking from batch-local scope to history-level scope for newly written items.
- Preserve `memind`'s stronger temporal semantics instead of collapsing to pure time-proximity edges.
- Keep temporal linking bounded, deterministic, and index-friendly.
- Preserve best-effort post-persistence graph materialization behavior.
- Avoid public configuration growth unless a requirement cannot be expressed through existing options.
- Let existing graph consumers benefit immediately from denser temporal structure.

## Non-Goals

- Building an all-pairs temporal graph across the entire memory corpus.
- Adding a temporal rebuild API or historical backfill requirement in this phase.
- Replacing `MemoryThread` with a temporal-graph-first retrieval model.
- Adding query-time ephemeral temporal edges.
- Making retrieval subtype-aware for `before` versus `overlap` versus `nearby` in this phase.
- Introducing LLM-based temporal judgment into item graph construction.
- Reserving a separate temporal quota for historical links versus same-batch links.

## Problem Statement

Current `memind` temporal linking is materially weaker in scope than its semantic design.

Today:

- temporal links are constructed only from the current batch of `MemoryItem`s
- the implementation lives inside `GraphHintNormalizer`, before temporal reasoning can see the historical corpus
- retrieval already expands through the `TEMPORAL` relation family
- memory-thread derivation already treats temporal links as one of the bridge signals

That means the system can persist temporal relations only when the related items happen to arrive together.

This misses the main operational value of temporal structure:

- a new fact should connect to relevant earlier or nearby historical facts
- retrieval should be able to traverse those historical temporal edges
- thread derivation should be able to observe process continuity across historical writes rather than only within one ingestion batch

If temporal is expected to matter for retrieval and episodic continuity, batch-local-only temporal linking is too narrow.

## Existing Implementation Facts

### Fact 1: Temporal links are currently batch-local

`GraphHintNormalizer.buildTemporalLinks(...)` constructs temporal edges by sorting only the current `items` list and never consulting persisted historical items.

Implication:

- the current implementation cannot connect new items to older items unless they are reintroduced in the same batch

### Fact 2: `memind` already has stronger temporal semantics than pure proximity

Current temporal relation classification distinguishes:

- `before`
- `overlap`
- `nearby`

These semantics are stronger than a pure "close in time" relation and should be preserved.

### Fact 3: Current graph consumers already read `TEMPORAL`

`DefaultRetrievalGraphAssistant` already consumes `ItemLinkType.TEMPORAL` as a graph expansion family, and `RuleBasedMemoryThreadDeriver` already uses temporal links as one of the bridge signals.

Implication:

- more complete temporal edges can improve existing graph consumers even before subtype-aware retrieval logic is added

### Fact 4: `ItemOperations` has no bounded temporal-neighbor lookup API today

Current `ItemOperations` exposes:

- item insertion
- point lookups
- full listing

It does not expose a temporal neighbor query.

Implication:

- a history-level temporal design needs an explicit store-side query contract

### Fact 5: Official stores do not currently expose temporal indexes for nearest-neighbor lookup

The current item schema includes temporal fields such as:

- `occurred_at`
- `occurred_start`
- `occurred_end`
- `observed_at`

but official store schema does not currently define an indexable temporal lookup contract for:

- anchor-neighbor lookup
- overlap-aware interval lookup

Implication:

- this phase must define how official stores support both bounded temporal-neighbor lookup and bounded overlap-aware lookup without degrading into full scans

## Approach Comparison

### Recommended: dedicated post-persistence temporal linker with history-level bounded lookup

This design introduces a dedicated `TemporalItemLinker` stage after item persistence. The linker performs:

- same-batch temporal candidate construction
- bounded historical temporal candidate lookup
- unified relation classification
- unified top-K pairing selection
- one bulk temporal upsert path

This is the recommended design because it:

- keeps temporal logic in one place
- preserves the existing temporal relation semantics
- makes historical temporal linking possible without overloading normalization
- matches the existing post-persistence semantic-linking architecture
- keeps failure semantics easy to explain

### Rejected: keep temporal inside `GraphHintNormalizer` and append historical edges later

This would leave same-batch temporal in the normalizer and bolt a second historical temporal pass onto materialization.

This is rejected because it would:

- split temporal behavior across two stages
- create ambiguous ownership of `maxTemporalLinksPerItem`
- make observability and failure semantics harder to reason about

### Rejected: hindsight-style pure temporal-proximity links

This would connect items by time-distance only and avoid relation classification beyond proximity.

This is rejected because it would:

- discard `memind`'s stronger temporal semantics
- collapse `before`, `overlap`, and `nearby` into one weaker relation family
- make the graph less expressive right before retrieval is expected to consume relations more deeply

## Design Principles

### 1. Preserve semantic strength while expanding operational scope

The right evolution is not to weaken `memind` temporal semantics. It is to keep those semantics and expand candidate scope to the historical corpus.

### 2. Keep temporal construction as one stage

Same-batch temporal linking and historical temporal linking must be part of one unified stage with one quota contract.

### 3. Prefer bounded store-side lookup over in-memory full scans

The required official implementation must fetch bounded temporal neighbors from storage. It must not materialize the full memory corpus into process memory for routine temporal linking.

### 4. Keep public configuration growth at zero for this phase

This phase should reuse `maxTemporalLinksPerItem` and internal constants. It should not add public knobs for before-window size, after-window size, temporal query batch size, or temporal probe width.

### 5. Preserve deterministic, canonical edge direction

Temporal edges are directed. The implementation must continue to write one canonical directed edge per selected pairing rather than force a symmetric temporal graph.

### 6. Degrade conservatively

If historical lookup fails, the stage should degrade to same-batch-only temporal linking instead of dropping temporal linking entirely.

## Proposed Architecture

### 1. Move Temporal Linking Out Of `GraphHintNormalizer`

`GraphHintNormalizer` should stop owning temporal edge construction.

After this phase:

- `GraphHintNormalizer` remains responsible for structured batch-local normalization such as entities and causal hints
- temporal edge construction moves to a dedicated post-persistence linker stage

Rationale:

- temporal linking now needs historical store access
- the semantic linker already proved that post-persistence graph stages are the right place for cross-item linking that depends on persisted state

### 2. Add `TemporalRelationClassifier`

The current temporal window resolution and relation classification logic should be extracted into a dedicated reusable component, tentatively named `TemporalRelationClassifier`.

Responsibilities:

- resolve a `TemporalWindow` from a `MemoryItem`
- classify a temporal relation between two resolved windows
- expose canonical temporal ordering logic used during edge direction decisions

The classifier preserves the current relation family:

- `before`
- `overlap`
- `nearby`

This phase does not add new temporal relation types.

### 3. Add `TemporalItemLinker`

`TemporalItemLinker` becomes the single owner of temporal edge materialization.

High-level flow:

1. receive the persisted incoming batch
2. resolve temporal windows for incoming items
3. build same-batch temporal candidates
4. fetch bounded historical temporal candidates for the incoming items
5. merge and deduplicate candidates per incoming item
6. classify relations and rank pairings
7. select top pairings under the existing temporal quota
8. canonicalize edge direction
9. bulk upsert temporal links

This stage is best-effort and runs after item persistence, like the semantic linker.

### 4. Update `DefaultItemGraphMaterializer` Stage Order

After this phase, the item-graph materialization pipeline should be:

1. normalize and persist structured graph state
2. run temporal linking
3. run semantic linking

Rationale:

- temporal and semantic are both post-persistence derived link stages
- temporal should not remain hidden inside structured normalization once it depends on the historical store

## Temporal Window Contract

This phase preserves the current temporal window resolution contract.

For each `MemoryItem`:

- `start = firstNonNull(occurredStart, occurredAt, observedAt)`
- `end = occurredEnd`
- `anchor = firstNonNull(occurredStart, occurredAt, observedAt)`

Items without a resolvable `start` and `anchor` are non-temporal for this stage and are skipped.

The phase deliberately does not introduce:

- a new public `temporalAnchor` field on `MemoryItem`
- fuzzy inferred durations
- LLM-derived missing end times

## Candidate Scope Contract

### 1. Same-Batch Candidates Remain In Scope

Same-batch temporal linking remains part of the design.

This phase upgrades temporal linking from:

- same-batch only

to:

- same-batch plus bounded historical candidates

It does not replace same-batch linking with historical linking.

### 2. Historical Candidates Are Scope-Restricted

Historical temporal candidate lookup is restricted to items that satisfy all of the following:

- same `memoryId`
- not deleted
- not in the current incoming batch item-id set
- resolvable temporal anchor
- same `MemoryItemType`
- same `MemoryCategory`

This is a deliberate conservative restriction.

Rationale:

- history-level temporal linking without semantic scope restrictions would create too many low-value "merely co-timed" edges
- `hindsight` already applies a same-fact-type restriction
- `memind` should use a similarly conservative scoping rule

This phase does not attempt broader cross-category temporal discovery.

### 3. Candidate Generation Is Per Incoming Item

The temporal selection budget is defined per incoming item, not per global graph source node.

That means:

- each incoming item is allowed to select up to `maxTemporalLinksPerItem` temporal pairings
- a selected pairing may canonicalize into an edge where the historical item is the graph `source`

This distinction is essential.

Historical temporal linking is incremental. If the quota were defined as a global outgoing-edge cap on the canonical `source`, then writing a new item could require retroactively pruning historical nodes' existing outgoing temporal edges. That is not acceptable for this phase.

Therefore, this phase defines `maxTemporalLinksPerItem` as:

- the maximum number of temporal pairings each incoming item may contribute to the graph stage

not as:

- the maximum canonical temporal out-degree of every graph node across history

Compatibility note:

- this is an intentional behavior change relative to the current batch-local implementation in `GraphHintNormalizer.buildTemporalLinks(...)`, which caps temporal emission by canonical graph `source`
- after this phase, both same-batch pairings and historical pairings obey the same per-incoming-item pairing cap
- this compatibility change is deliberate because preserving the old same-batch source-cap while introducing a different historical pairing-cap would create two temporal quota regimes inside one stage
- the phase prefers one explicit quota contract over partial backward-compatibility with split semantics

## Historical Lookup Contract

### 1. `ItemOperations` Gains A Bounded Temporal Lookup API

This phase adds a new core item-store contract for temporal candidate lookup.

Tentative shape:

```java
default List<TemporalCandidateMatch> listTemporalCandidateMatches(
        MemoryId memoryId,
        List<TemporalCandidateRequest> requests,
        Collection<Long> excludeItemIds) {
    return TemporalCandidateLookupSupport.correctnessFirstLookup(
            this, memoryId, requests, excludeItemIds);
}
```

Supporting records:

```java
public record TemporalCandidateRequest(
        Long sourceItemId,
        Instant sourceStart,
        Instant sourceEndOrAnchor,
        Instant sourceAnchor,
        MemoryItemType itemType,
        MemoryCategory category,
        int overlapLimit,
        int beforeLimit,
        int afterLimit) {}

public record TemporalCandidateMatch(
        Long sourceItemId,
        MemoryItem candidateItem) {}
```

Required contract:

- the method returns bounded candidate matches for each request
- candidates must already satisfy the scope restrictions defined above
- `excludeItemIds` must be respected
- results may be empty for any request
- returned candidates may come from up to three bounded probe families:
  - overlap-aware interval matches
  - earlier anchor-neighbor matches
  - later anchor-neighbor matches
- a single candidate item must appear at most once per source request after store-side or linker-side deduplication
- deterministic ordering is required, but final semantic priority remains the linker's responsibility rather than the store contract

### 2. Core Default Behavior Versus Official Plugin Requirements

The core interface must provide a default correctness-first fallback implementation.

Required behavior for the inherited default path:

- it must return correct bounded history-level temporal candidates even when the store does not override the method
- it may use `listItems(memoryId)` plus in-memory filtering, overlap detection, ranking, deduplication, and limit enforcement
- it is allowed to be slower than native implementations
- it must still obey the same candidate-scope, overlap-aware, deduplication, and probe-limit contracts defined by this document

The required official store deliverable is stronger:

- third-party stores inherit correctness even if they do not override the method
- official maintained stores must override with bounded store-native lookup

This avoids silent feature loss in custom stores while still keeping maintained backends operationally sound.

### 3. Internal Query Batching

Historical lookup requests should be grouped into internal batches for store efficiency.

Recommended initial internal constant:

- `TEMPORAL_QUERY_BATCH_SIZE = 128`

This is an internal constant, not a public knob.

### 4. Internal Probe Limit Derivation

This phase fixes the historical temporal probe policy as an internal contract so the maintained official stores do not drift in candidate volume or behavior.

Recommended initial internal constants:

- `TEMPORAL_OVERLAP_PROBE_MIN = 4`
- `TEMPORAL_OVERLAP_PROBE_MAX = 16`
- `TEMPORAL_NEIGHBOR_PROBE_MULTIPLIER = 2`
- `TEMPORAL_NEIGHBOR_PROBE_MIN_PER_SIDE = 8`
- `TEMPORAL_NEIGHBOR_PROBE_MAX_PER_SIDE = 32`

Required derivation:

- `overlapLimit = clamp(maxTemporalLinksPerItem, TEMPORAL_OVERLAP_PROBE_MIN, TEMPORAL_OVERLAP_PROBE_MAX)`
- `beforeLimit = clamp(maxTemporalLinksPerItem * TEMPORAL_NEIGHBOR_PROBE_MULTIPLIER, TEMPORAL_NEIGHBOR_PROBE_MIN_PER_SIDE, TEMPORAL_NEIGHBOR_PROBE_MAX_PER_SIDE)`
- `afterLimit = beforeLimit`

This derivation is part of the required official implementation contract even though it does not surface as a public runtime option.

## Official Store Requirements

### 1. Add Internal Persisted Temporal Query Columns

To make both nearest-neighbor lookup and overlap-aware lookup portable and index-friendly across official stores, this phase introduces internal persisted item-store columns:

- `temporal_start`
- `temporal_end_or_anchor`
- `temporal_anchor`

Definition:

- `temporal_start = firstNonNull(occurred_start, occurred_at, observed_at)`
- `temporal_end_or_anchor = firstNonNull(occurred_end, occurred_start, occurred_at, observed_at)`
- `temporal_anchor = firstNonNull(occurred_start, occurred_at, observed_at)`

Properties:

- nullable only for non-temporal items
- internal storage concern only
- not exposed as a new `MemoryItem` API field in this phase

Rationale:

- official stores need stable, indexable fields for bounded temporal neighbor queries and overlap-aware interval queries
- relying on per-dialect `COALESCE(...)` expression indexes would complicate portability and maintenance
- `temporal_start` and `temporal_anchor` currently resolve to the same value under the preserved window contract, but they represent different logical roles and keep the query contract stable if anchor resolution evolves later

### 2. Official Store Query Shape

Official stores should perform up to three bounded probes per source request:

- one overlap-aware interval probe
- one backward probe for earlier anchors
- one forward probe for later anchors

Then they should return the combined nearest candidates to the linker.

The query must:

- filter on store scope and `memoryId`
- filter on `type`
- filter on `category`
- exclude deleted items
- exclude `excludeItemIds`
- ignore rows with null `temporal_anchor`

Required overlap-aware probe semantics:

- return up to `overlapLimit` historical items whose stored temporal windows overlap the source window
- overlap matching uses the persisted interval columns rather than anchor approximation alone
- recommended overlap predicate:
  - `candidate.temporal_start < source.sourceEndOrAnchor`
  - `candidate.temporal_end_or_anchor > source.sourceStart`
- overlap results are ordered by:
  - `abs(candidate.temporal_anchor - source.sourceAnchor)` ascending
  - `candidate item id` ascending

Required anchor-neighbor probe semantics:

- the backward probe returns up to `beforeLimit` earlier historical candidates ordered by anchor distance ascending
- the forward probe returns up to `afterLimit` later historical candidates ordered by anchor distance ascending
- candidates already returned by the overlap probe must not be emitted again as duplicate matches

### 3. Official Store Index Requirements

Official maintained stores must add the indexes needed to support the bounded lookup path.

Required index intent:

- scoped lookup by memory and temporal neighbor order
- scoped lookup by memory, type, category, and temporal anchor
- scoped overlap-aware lookup using `temporal_start` and `temporal_end_or_anchor`, or a dialect-equivalent interval strategy with the same bounded-behavior guarantee

Exact index syntax may vary per dialect, but the maintained backend must not implement the required path as an unbounded scan over all items in the memory.

## Pair Construction And Relation Classification

### 1. Candidate Sources

For each incoming temporal item, the linker should combine:

- same-batch temporal candidates
- historical temporal candidates returned by `listTemporalCandidateMatches(...)`

Historical candidates are expected to come from:

- overlap-aware historical probes
- earlier anchor-neighbor probes
- later anchor-neighbor probes

Candidates are merged by candidate item id.

### 2. Relation Classification

Each candidate pairing must be classified using the shared `TemporalRelationClassifier`.

Required behavior:

- `overlap` when the windows provably intersect
- `before` when the earlier window provably ends before or exactly when the later window starts
- `nearby` when overlap is not provable, strong `before` is not provable, and the absolute anchor gap is within the nearby threshold

Recommended initial nearby threshold:

- `24 hours`

This threshold remains an internal contract in this phase.

If classification returns no valid relation, the candidate is dropped.

### 3. Canonical Edge Direction

Temporal edges remain directed.

For every selected pairing, the implementation must write exactly one canonical directed edge.

Canonical ordering rule:

- sort the two windows by `(start, anchor, itemId)`
- the earlier side becomes the graph `source`
- the later side becomes the graph `target`

This means:

- the incoming item may be the canonical `target`
- the historical item may be the canonical `source`

This is expected and correct.

### 4. Metadata And Strength

This phase preserves the current temporal link payload shape:

- `linkType = TEMPORAL`
- `strength = 1.0`
- `metadata.relationType = before | overlap | nearby`

This phase deliberately does not add temporal-proximity weighting.

Rationale:

- retrieval does not yet consume temporal subtype-specific scores
- introducing pseudo-precise weight gradients now would add complexity without stable downstream use

## Materialization Stats And Tracing Contract

This phase changes the production boundary of temporal links, so it must also make the observability contract explicit.

Current code reports:

- `structuredItemLinkCount` in `ItemGraphMaterializationResult.Stats`
- `EXTRACTION_GRAPH_STRUCTURED_LINK_COUNT` in tracing

After temporal link construction moves out of `GraphHintNormalizer`, those fields must no longer silently carry historical temporal links.

Required contract after this phase:

- `structuredItemLinkCount` continues to mean links persisted during the structured graph stage only
- after temporal extraction from `GraphHintNormalizer`, `structuredItemLinkCount` excludes temporal links and typically counts only non-temporal structured links such as causal links
- `EXTRACTION_GRAPH_STRUCTURED_LINK_COUNT` follows the same non-temporal structured-stage meaning
- temporal link production is reported exclusively through dedicated temporal-stage stats and tracing attributes

This phase therefore requires `ItemGraphMaterializationResult.Stats` and `TracingItemGraphMaterializer` to add temporal counters that mirror the observability section below.

This explicit split is necessary so operators do not interpret the post-change drop in `structuredItemLinkCount` as a regression.

## Pair Ranking And Quota

### 1. Ranking

For each incoming item, classified candidates are ranked deterministically by:

1. relation priority
2. absolute anchor gap ascending
3. candidate item id ascending

Recommended relation priority:

1. `overlap`
2. `nearby`
3. `before`

Rationale:

- overlapping or immediately adjacent events usually provide stronger contextual continuation than earlier but more weakly related temporal neighbors

### 2. Quota

After ranking, the linker selects at most:

- `maxTemporalLinksPerItem`

pairings for that incoming item.

The same quota applies to the unified candidate pool. There is no reserved sub-quota for:

- same-batch candidates
- historical candidates

### 3. Duplicate Pairings

The same logical item pair may be discovered:

- from same-batch processing of both incoming items
- from historical lookup plus same-batch processing

Required handling:

- deduplicate selected pairs per incoming item by candidate item id before canonicalization
- deduplicate final links across the whole stage by item-link identity before upsert
- rely on `GraphOperations.upsertItemLinks(...)` idempotence to tolerate retries or repeats

## Failure And Degradation Semantics

### 1. Historical Lookup Failure

If one internal historical lookup batch fails:

- only that lookup batch degrades
- affected incoming items fall back to same-batch-only temporal linking
- other internal lookup batches continue
- the temporal stage is marked degraded

This phase does not permit one failed historical lookup batch to drop the entire temporal stage.

### 2. Candidate Classification Failure

If one item cannot resolve a valid temporal window:

- that item is skipped for temporal linking
- this is not a stage failure

### 3. Upsert Failure

If temporal link upsert fails:

- the temporal stage is best-effort failed
- item persistence is not rolled back
- structured graph state already persisted by earlier stages remains intact

### 4. Stage Isolation

Temporal stage failure must not prevent the later semantic stage from running.

This matches the general best-effort philosophy already used by item-graph materialization.

## Observability

This phase should add dedicated temporal-stage observability rather than burying history-level behavior inside structured-graph counters.

Recommended stats:

- `temporalSourceCount`
- `temporalHistoryQueryBatchCount`
- `temporalHistoryCandidateCount`
- `temporalIntraBatchCandidateCount`
- `temporalSelectedPairCount`
- `temporalCreatedLinkCount`
- `temporalQueryDurationMs`
- `temporalBuildDurationMs`
- `temporalUpsertDurationMs`
- `temporalDegraded`

Semantics:

- `temporalSelectedPairCount` is counted before canonical edge deduplication
- `temporalCreatedLinkCount` is counted as the number of unique temporal links submitted to `upsertItemLinks(...)` after final deduplication
- `temporalDegraded` is true whenever any historical lookup batch or final upsert path falls back or fails

## Examples

### Example 1: Historical Nearby Link

Historical item `A`:

- type: `FACT`
- category: `EVENT`
- anchor: `2026-04-10T09:00:00Z`

Incoming item `B`:

- type: `FACT`
- category: `EVENT`
- anchor: `2026-04-10T11:00:00Z`

Classification:

- `nearby`

Written link:

- `A -> B`
- `TEMPORAL`
- `metadata.relationType = nearby`

### Example 2: Historical Overlap Link

Historical item `A`:

- `occurredStart = 2026-04-10T13:00:00Z`
- `occurredEnd = 2026-04-10T15:30:00Z`

Incoming item `B`:

- `occurredAt = 2026-04-10T15:00:00Z`

Classification:

- `overlap`

Written link:

- `A -> B`

### Example 3: Incremental Write Where Incoming Item Is The Canonical Target

Historical item `A` is earlier than incoming item `B`.

The incoming item selects the pairing under its per-item quota, but after canonical ordering the stored edge is still:

- `A -> B`

This is correct because the quota is defined per incoming item contribution, not as a promise that the incoming item must be the graph `source`.

### Example 4: Historical Lookup Failure

For a batch of 144 incoming items:

- the first lookup sub-batch of 128 succeeds
- the second lookup sub-batch of 16 fails

Required result:

- the first 128 items use same-batch plus historical candidates
- the remaining 16 items use same-batch-only temporal linking
- the stage reports degraded operation

## Compatibility And Follow-On Work

This phase is intentionally retrieval-compatible but retrieval-incomplete.

It immediately improves:

- temporal graph density across historical writes
- thread-derivation bridge coverage
- graph expansion availability for temporal neighbors

It also intentionally changes:

- same-batch temporal quota semantics from canonical-source-cap behavior to the unified per-incoming-item pairing-cap behavior defined above

It does not yet deliver:

- retrieval-time weighting differences between `before`, `overlap`, and `nearby`
- direction-aware temporal reranking
- temporal-thread or timeline-specific query planning

Those belong to a later retrieval-focused phase.

## Acceptance Criteria

- newly written temporal items can link to bounded relevant historical temporal items rather than only to same-batch items
- same-batch temporal linking remains supported and is implemented through the same temporal linker stage
- `before`, `overlap`, and `nearby` semantics are preserved
- historical candidate lookup remains overlap-aware rather than anchor-only, so long-running overlapping events are eligible historical candidates
- canonical temporal edge direction is explicitly defined and deterministic
- `maxTemporalLinksPerItem` is defined as a per-incoming-item pairing cap, not as a global canonical out-degree cap
- the same per-incoming-item pairing cap intentionally applies to same-batch temporal construction as well as historical temporal construction, replacing the old canonical-source-cap batch behavior
- historical temporal lookup is provided through an explicit store contract
- official implementations derive `overlapLimit`, `beforeLimit`, and `afterLimit` from one shared internal contract rather than backend-specific heuristics
- the inherited `ItemOperations` default implementation remains correctness-preserving even for stores that do not override the temporal lookup method
- official maintained stores implement bounded native lookup and do not rely on `listItems(memoryId)` full scans for the required path
- official maintained stores persist and index the internal temporal query columns needed for both bounded lookup and overlap-aware lookup
- historical lookup failure degrades to same-batch-only temporal linking for the affected subset instead of dropping the entire temporal stage
- `structuredItemLinkCount` and tracing explicitly exclude temporal links after the phase boundary, and dedicated temporal-stage stats replace the old implicit accounting
- the design adds temporal-stage observability sufficient to explain historical lookup cost, candidate volume, and degraded operation

## Final Recommendation

`memind` should keep temporal linking, but it should stop being batch-local.

The correct next step is not to copy `hindsight`'s proximity-only temporal model. The correct next step is:

- keep `memind`'s stronger temporal semantics
- upgrade temporal candidate scope to bounded history-level lookup
- make temporal linking a dedicated post-persistence stage with one unified quota and one unified failure model

That gives `memind` the main operational benefit of history-aware temporal structure without forcing it into a larger temporal-graph platform before retrieval is ready for that next layer.
