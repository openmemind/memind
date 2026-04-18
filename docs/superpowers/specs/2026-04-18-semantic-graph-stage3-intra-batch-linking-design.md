# Semantic Graph Stage 3 Intra-Batch Linking Design

Status: Draft

> Goal: add explicit intra-batch semantic item-link construction to memind so the semantic graph can deterministically capture same-batch relationships that are currently only surfaced opportunistically through external vector search, without changing the completed Stage 2 external semantic-link contract, expanding the public runtime configuration surface, or introducing embedding-text augmentation into the primary vector contract.
>
> This document defines the next implementation stage after the completed Stage 2 throughput work.

## Scope

This design only covers write-time explicit same-batch construction for the `SEMANTIC` item-link family.

Required scope includes:

- batch-scoped same-batch semantic candidate construction for newly written items
- in-memory similarity comparison using embeddings that correspond to the current batch's stored vectors
- deterministic merging of same-batch candidates with the completed Stage 2 external-search candidates
- observability additions needed to explain Stage 3 behavior and cost

It does not change:

- the semantic embedding text contract, which remains pure `content`
- `MemoryItemLayer` vector-write text shaping
- retrieval-side semantic evidence accumulation
- Stage 2 external `searchBatch(...)` semantics
- candidate resolution chunking or bulk semantic-link upsert semantics from Stages 1 and 2
- entity extraction or entity sibling expansion
- temporal or causal link construction
- public item-graph options
- admin config projection, codec, or controller surfaces
- rebuild APIs, async workers, queues, or deferred semantic-link lifecycles
- historical backfill or graph rebuild requirements

## Problem Statement

Stage 2 already improved semantic-link throughput by processing source windows sequentially, using ordered batch search where available, resolving external candidates in chunks, and bulk-upserting semantic links per window.

However, the semantic stage still has one structural blind spot:

1. newly written items are linked to existing items through vector search
2. same-batch relationships are only observed if normal external search happens to surface another item from the same batch
3. those same-batch hits must still compete with all previously indexed items for the same `topK` budget

That means a batch containing several mutually related facts can still yield an incomplete local semantic subgraph even though every item has already been persisted before graph materialization begins.

The Stage 3 goal is therefore not to make same-batch items visible for the first time. They are already persisted before semantic linking starts. The goal is to make same-batch semantic relationships explicit, deterministic, and backend-neutral instead of relying on opportunistic external ANN hits.

## Current Implementation Facts

### Fact 1: Semantic graph materialization is still post-persistence and best-effort

`MemoryItemLayer.vectorizeAndPersist(...)` stores vectors, inserts items, and only then invokes `graphMaterializer.materialize(...)`.

Implication:

- Stage 3 must preserve item durability when same-batch linking fails
- same-batch linking must remain part of the post-persistence best-effort graph stage

### Fact 2: The semantic embedding text contract is still pure `content`

`ItemEmbeddingTextResolver` currently resolves semantic vector text from `content` only.

Implication:

- Stage 3 must not assume any category/entity/time augmentation is available in the stored vectors
- same-batch fallback embedding recomputation must use the same pure-`content` contract

### Fact 3: `MemoryVector` already exposes the capabilities Stage 3 needs

`MemoryVector` already provides:

- `searchBatch(...)`
- `fetchEmbeddings(...)`
- `embedAll(...)`

The default `fetchEmbeddings(...)` implementation returns an empty map, which naturally drives a fallback path.

Implication:

- Stage 3 does not require a new core vector API
- Stage 3 can prefer embedding fetch while remaining correct on backends that only support `embedAll(...)`

### Fact 4: Stage 2 already defined the external-search observability contract

Current semantic stats already distinguish:

- logical search requests
- lower-level search invocations
- search hits
- same-batch hits
- resolve/upsert failures

Implication:

- Stage 3 must preserve the Stage 2 meaning of external-search counters
- Stage 3 needs only the minimum new observability required to explain explicit same-batch work

## Design Principles

1. **Add explicit same-batch linking only**

   Stage 3 should solve the same-batch blind spot without reopening the embedding-text contract or retrieval design.

2. **Preserve the Stage 2 external semantic contract**

   Stage 3 may add candidates, but it must not change Stage 2 search, resolve, normalization, fallback, or persistence semantics for external candidates.

3. **Keep the default path backend-neutral**

   The required baseline must work even when `fetchEmbeddings(...)` is not implemented and the backend falls back to `embedAll(...)`.

4. **Use one unified normalization and quota path**

   Same-batch candidates and external candidates must compete in one shared normalization pipeline and one shared `maxSemanticLinksPerItem` budget.

5. **Degrade gracefully**

   Same-batch embedding fetch/recompute or pairwise comparison failure must not drop the entire semantic stage when the external Stage 2 path can still succeed.

6. **Do not expand the public configuration surface**

   Stage 3 should reuse existing knobs and internal constants rather than expose new intra-batch tuning options.

## Non-Goals

This design deliberately does not:

- add embedding augmentation
- add a second vector representation for graph-only use
- reserve same-batch links a separate per-item quota
- add reverse semantic edges
- add plugin-specific native vector optimizations as required scope
- change search result text or retrieval display contracts
- add server-side Stage 3 runtime options

## Proposed Architecture

### 1. Preserve The Current Embedding Text Contract

Stage 3 intentionally does not change the semantic embedding text contract.

The required Stage 3 assumption remains:

- vectors stored by `store(...)` and `storeBatch(...)` are derived from pure `content`
- `embed(...)` and `embedAll(...)` operate in the same vector space as those stored vectors for the same input text

Rationale:

- keeps Stage 3 focused on same-batch graph completeness
- avoids reopening rollout and compatibility questions that belong to a separate embedding-augmentation project
- lets Stage 3 reuse current write-time vectorization without any admin/runtime config changes

### 2. Add A Batch-Scoped Same-Batch Candidate Stage

`SemanticItemLinker.link(...)` should gain a batch-scoped same-batch candidate stage ahead of the existing per-window external search flow.

At a high level, Stage 3 should do the following:

1. keep the current full pre-window input batch as the authoritative same-batch scope
2. acquire embeddings for the entire batch once per `link(...)` invocation
3. process source windows sequentially, as Stage 2 already does
4. for each source window, compute explicit same-batch candidates against the full batch scope
5. collect Stage 2 external candidates for that same source window
6. merge the two candidate sets
7. run the existing deterministic normalization and shared quota path
8. bulk-upsert the final links through the existing Stage 2 persistence path

This means `semanticSourceWindowSize` still controls source processing and memory bounds, but it does not reduce the semantic scope of same-batch linking. Same-batch comparison always targets the full pre-window batch for comparability and completeness.

### 2.1 Batch Embedding Acquisition

Stage 3 should build one batch embedding cache per `SemanticItemLinker.link(...)` invocation.

Required behavior:

1. collect the batch's non-blank `vectorId`s in input order
2. call `vector.fetchEmbeddings(...)` once for the batch's vector IDs
3. treat the returned map as partial: some vector IDs may be absent
4. recompute embeddings only for the missing batch items via `vector.embedAll(...)`, using each item's canonical semantic text, which in Stage 3 remains `content`
5. assemble one ordered `vectorId -> embedding` view aligned to the full input batch

Recommended fallback policy:

- if `fetchEmbeddings(...)` fails for the whole batch, retry Stage 3 embedding acquisition by recomputing the entire batch with `embedAll(...)`
- if `fetchEmbeddings(...)` succeeds partially, only the missing items should be recomputed

Rationale:

- makes stored-vector reuse the preferred path when available
- keeps the default backend-neutral path correct
- avoids making duplicate embedding work the default cost on capable backends

### 2.2 In-Memory Same-Batch Similarity

For each source window, Stage 3 should compute same-batch semantic candidates between the source items in that window and the full pre-window input batch.

Required behavior:

- exclude self-links
- use cosine similarity
- explicitly normalize vectors when computing cosine similarity in memory; the implementation must not assume embeddings are pre-normalized
- apply `semanticMinScore` before candidate emission
- candidate generation must be deterministic for the same embeddings and inputs

Implementation notes:

- source windows remain controlled by `semanticSourceWindowSize`
- implementations may internally tile the full-batch target set with an internal constant to control temporary memory usage
- recommended initial internal target-tile value: `512`
- internal tiling is not externally observable and must not change final normalized output

Rationale:

- preserves the Stage 2 source-window lifecycle
- avoids a new public intra-batch window or tile-size knob
- gives Stage 3 true full-batch same-batch coverage instead of a weaker window-local approximation

### 2.3 Merge Same-Batch And External Candidates Before Normalization

Stage 3 must not create a separate same-batch normalization or persistence pipeline.

For each source item, the candidate pool should be the union of:

- explicit same-batch candidates generated by Stage 3
- external candidates produced by the completed Stage 2 search/resolve path

Required merge semantics:

- merge by `vectorId`
- use `Float::max` when the same candidate appears from both origins
- same-batch candidates do not receive a reserved quota or priority lane
- all merged candidates continue through the same deterministic normalization path

This preserves the current compatibility rules:

- drop unresolved candidates
- drop self-links
- stable sort by:
  - `score desc`
  - `vectorId asc`
  - `item.id asc`
- truncate to `maxSemanticLinksPerItem`

The same-batch and external candidates therefore compete in one shared budget.

### 2.4 Candidate Resolution

Stage 3 does not change external candidate resolution.

Required behavior:

- same-batch candidates must resolve directly through the current batch's in-memory `MemoryItem` objects; no item-store round trip is needed
- external candidates must continue to use the Stage 2 batched `getItemsByVectorIds(...)` path
- the final `vectorId -> MemoryItem` map used by normalization is the union of:
  - same-batch in-memory targets
  - externally resolved targets

Rationale:

- avoids redundant item-store reads for same-batch targets that are already present in memory
- keeps the Stage 2 external resolution contract unchanged

### 2.5 Persistence

Stage 3 does not create a second write path.

Required behavior:

- after same-batch and external candidates are merged and normalized, the resulting `ItemLink`s for that source window must be aggregated into one list
- persistence continues through the existing Stage 2 bulk-upsert chunking path
- write ordering, chunk sizing, idempotence, and retry semantics remain unchanged from Stage 2

This prevents Stage 3 from reintroducing write amplification by emitting separate same-batch and external upsert phases for the same window.

## Failure Semantics

### 3.1 Batch-Scoped Embedding Acquisition Failure

If Stage 3 cannot obtain the batch embedding cache:

- item persistence is unaffected
- the semantic stage must continue with the external Stage 2 path only
- the Stage 3 same-batch candidate stage is skipped for the entire `link(...)` invocation
- the resulting stats must mark the semantic stage as degraded

Recommended degradation order:

1. try `fetchEmbeddings(...)`
2. if that fails, retry with `embedAll(...)` for the full batch
3. if full-batch `embedAll(...)` also fails, skip Stage 3 same-batch candidate construction entirely

### 3.2 Window-Scoped Same-Batch Comparison Failure

If the batch embedding cache exists but same-batch comparison fails for one source window:

- skip only the same-batch candidate contribution for that window
- continue the external Stage 2 path for that window
- continue subsequent source windows
- mark the stage degraded

This keeps Stage 3 failure blast radius aligned with the existing Stage 2 window model.

### 3.3 External Stage 2 Failure

External search, fallback replay, resolve chunking, and upsert failure semantics remain exactly as defined by the completed Stage 2 design.

Stage 3 must not weaken or override those contracts.

## Observability

Stage 3 must preserve the Stage 2 meanings of the existing external-search counters:

- `searchRequestCount`
- `searchInvocationCount`
- `searchHitCount`
- `searchFallbackCount`

These counters remain external-search-only. They do not count same-batch embedding fetches, `embedAll(...)` recomputation, or explicit same-batch candidates.

### 4.1 `sameBatchHitCount`

`sameBatchHitCount` remains part of the operator-facing tracing surface, but Stage 3 upgrades its meaning.

Stage 3 definition:

- the number of final normalized semantic candidates that belong to the full pre-window input batch, regardless of whether they originated from:
  - explicit Stage 3 same-batch comparison, or
  - normal external vector search

This makes the field a real same-batch coverage metric instead of a Stage 2-only observational artifact.

### 4.2 New Required Counters

Stage 3 should add exactly two new observability fields:

- `intraBatchCandidateCount`
- `intraBatchPhaseDurationMs`

Definitions:

- `intraBatchCandidateCount`
  counts explicit Stage 3 same-batch candidates that passed self-filtering and `semanticMinScore`, before merge with the external candidate set and before final per-source truncation

- `intraBatchPhaseDurationMs`
  counts the total wall-clock time spent on Stage 3 batch embedding acquisition and same-batch candidate construction for the full `link(...)` invocation

These two fields are sufficient to answer the operator-facing questions:

1. did Stage 3 actually surface explicit same-batch candidates
2. how much additional work did Stage 3 add
3. did same-batch final coverage improve

### 4.3 Duration Semantics

Stage 3 duration accounting must remain non-overlapping and explainable:

- `searchPhaseDurationMs`
  external Stage 2 search only

- `resolvePhaseDurationMs`
  external candidate resolution only

- `upsertPhaseDurationMs`
  final semantic link persistence only

- `intraBatchPhaseDurationMs`
  Stage 3 batch embedding acquisition and same-batch comparison only

`intraBatchPhaseDurationMs` must be counted once per `link(...)` invocation, not once per source window.

## File Structure

### Required

- `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/item/graph/SemanticItemLinker.java`
  Add Stage 3 batch embedding acquisition, same-batch candidate construction, merge logic, degradation handling, and stats.

- `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/item/graph/DefaultItemGraphMaterializer.java`
  Propagate the expanded Stage 3 semantic stats into materialization output.

- `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/item/graph/ItemGraphMaterializationResult.java`
  Extend the semantic stats surface with `semanticIntraBatchCandidateCount` and `semanticIntraBatchPhaseDurationMs`.

- `memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/item/graph/SemanticItemLinkerTest.java`
  Cover same-batch candidate construction, `fetchEmbeddings(...)` preference, `embedAll(...)` fallback, unified quota competition, and degradation behavior.

- `memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/item/graph/DefaultItemGraphMaterializerTest.java`
  Cover Stage 3 stats propagation and degraded same-batch behavior at the materializer boundary.

### Conditional Follow-Ups

- any `MemoryVector` plugin-specific `fetchEmbeddings(...)` override

These are optional Stage 3 follow-ups, not required deliverables. The required baseline must remain correct when `fetchEmbeddings(...)` falls back to the default empty map.

### Explicitly Not Required

- `ItemGraphOptions`
- `MemoryVector` public API shape
- server config projection / codec / controller files
- embedding-text resolver changes

## Acceptance Criteria

Stage 3 is complete only when all of the following are true:

1. same-batch semantic scope is the full pre-window input batch, not the current source window only
2. the Stage 3 baseline prefers `fetchEmbeddings(...)` and recomputes only missing embeddings through `embedAll(...)`
3. full failure of `fetchEmbeddings(...)` cleanly falls back to full-batch `embedAll(...)`
4. failure of both Stage 3 embedding acquisition paths skips explicit same-batch linking while preserving the external Stage 2 path
5. same-batch candidates and external candidates merge into one shared normalization and one shared `maxSemanticLinksPerItem` budget
6. the in-memory same-batch path computes cosine similarity without assuming pre-normalized embeddings
7. Stage 2 external-search counter meanings remain unchanged
8. `sameBatchHitCount` reflects final normalized same-batch coverage regardless of candidate origin
9. `intraBatchCandidateCount` and `intraBatchPhaseDurationMs` are surfaced through `ItemGraphMaterializationResult`
10. no new public runtime option is introduced

## Rationale Versus Hindsight

This design intentionally borrows hindsight's most valuable Stage 3 idea:

- explicit same-batch semantic linking should be computed directly rather than inferred opportunistically from external ANN hits

But it deliberately does not adopt hindsight's broader retain lifecycle choices:

- no embedding augmentation
- no separate retain-mode lifecycle
- no streaming-only final ANN pass
- no heavier multi-phase graph construction model

The Stage 3 memind design therefore keeps the completed Stage 2 lifecycle intact and adds only the smallest new mechanism required to make same-batch semantic relationships explicit.
