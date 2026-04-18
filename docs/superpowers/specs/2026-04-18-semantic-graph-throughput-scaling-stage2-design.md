# Semantic Graph Throughput Scaling Stage 2 Design

Status: Draft

> Goal: extend semantic item-link construction with ordered batch-search capability so memind can reduce search-side amplification on capable backends without weakening the completed Stage 1 deterministic semantic contract, expanding the public runtime configuration surface, or introducing backend-specific assumptions into the default path.
>
> This document follows the completed Stage 1 inline windowed semantic-link implementation and supersedes the earlier Stage 2 sketch embedded in `2026-04-18-semantic-graph-throughput-scaling-v2-design.md`.

## Scope

This design only covers the search phase of write-time `SEMANTIC` item-link materialization.

Required scope includes:

- a core ordered batch-search capability on `MemoryVector`
- `SemanticItemLinker` adoption of that capability at source-window granularity
- one official plugin implementation in `SpringAiMemoryVector`

It does not change:

- the semantic embedding text contract (`content`)
- semantic candidate normalization, ranking, deduplication, or truncation
- resolve chunking or bulk semantic-link upsert semantics from Stage 1
- retrieval-side semantic evidence accumulation
- entity extraction or entity sibling expansion
- temporal or causal link construction
- `whenToUse` semantics for tool memories
- rebuild APIs, async workers, task queues, or deferred semantic-link lifecycles
- public item-graph options

## Problem Statement

Stage 1 already reduced candidate-resolution and graph-write amplification by restructuring semantic linking around source windows, batched candidate resolution, and bulk semantic-link upsert.

However, the search phase remains fundamentally one logical request per source item and, in the current implementation, one physical vector-backend invocation per logical request.

That means the remaining search-side cost still scales directly with item count:

1. vector-backend round trips still scale with the number of source items
2. retry overhead still scales with the number of source items
3. search observability can distinguish logical versus physical search counts, but the current implementation has no path for those counts to diverge

The next throughput improvement should therefore target the search phase only, while preserving the Stage 1 resolve, normalization, and persistence contracts.

## Current Implementation Facts

### Fact 1: Stage 1 semantics are already explicit and must remain stable

`SemanticItemLinker` now processes source windows sequentially, keeps `semanticLinkConcurrency` scoped to a single window, resolves candidates in internal chunks, normalizes deterministically, and bulk-upserts semantic links in bounded chunks.

Implication:

- Stage 2 must change only how raw search hits are collected
- Stage 2 must not change normalized semantic-link output for the same raw search results

### Fact 2: `MemoryVector` still exposes only single-query search

`MemoryVector` currently exposes:

- `search(memoryId, query, topK)`
- `search(memoryId, query, topK, filter)`
- `search(memoryId, query, topK, minScore, filter)` via default method

It does not expose batch search.

Implication:

- if memind wants batch-search capability, it should extend `MemoryVector` itself
- introducing a semantic-only capability interface would fragment the vector abstraction unnecessarily

### Fact 3: `searchRequestCount` and `searchInvocationCount` already have distinct meanings

Stage 1 tracing and materialization stats already separate:

- logical semantic search requests
- physical vector-backend search invocations

Implication:

- Stage 2 must preserve those semantics
- the new design must not let the two counters silently drift in meaning

### Fact 4: Spring AI's generic `VectorStore` abstraction is single-query oriented

`SpringAiMemoryVector` currently delegates search to `VectorStore.similaritySearch(SearchRequest)`.

The generic Spring AI `VectorStore` contract does not provide a portable ordered multi-query search primitive.

Implication:

- Stage 2 cannot truthfully require generic Spring AI `VectorStore` backends to collapse multiple queries into one physical call
- the required Spring AI plugin implementation should align the official plugin with the new core contract while remaining honest about that limitation

## Design Principles

1. **Optimize the search phase only**

   Stage 1 already addressed resolve and upsert amplification. Stage 2 should stay focused on search.

2. **Expand the vector abstraction once**

   Batch-search capability belongs on `MemoryVector`, not on a semantic-graph-specific side interface.

3. **Preserve the operator-facing concurrency contract**

   `semanticLinkConcurrency` already has defined Stage 1 meaning. Stage 2 must not accidentally bypass or weaken that contract on fallback paths.

4. **Keep the default path backend-neutral**

   The default implementation must work correctly for every existing `MemoryVector` implementation, even when the backend only supports single-query search.

5. **Do not overpromise physical invocation collapse**

   Required plugin implementations may align behavior with the batch-search contract even when their underlying backend still executes one physical search per logical request.

6. **Degrade at source-window granularity**

   If an optimized batch-search attempt fails, the linker should fall back for that window only and then continue subsequent windows.

## Non-Goals

This design deliberately does not:

- add new item-graph runtime options
- change the `content`-only semantic query contract
- change semantic min-score or headroom semantics
- add plugin-specific native-client branching in the required baseline
- add intra-batch semantic linking
- add async semantic-link workers
- add semantic-edge rebuild or repair workflows

## Proposed Architecture

## 1. Add Ordered Batch Search To `MemoryVector`

Stage 2 should add two new core value objects and one new `MemoryVector` method.

### 1.1 `VectorSearchRequest`

Add:

```java
public record VectorSearchRequest(
        String query,
        int topK,
        double minScore,
        Map<String, Object> filter) {}
```

Required constructor semantics:

- `query` must be non-null
- `topK` must be positive
- `minScore` must be in `[0,1]`
- `filter` must be normalized to an immutable map
- `filter == null` must become `Map.of()`

Rationale:

- keeps request semantics stable after construction
- avoids callers mutating shared maps after submitting batch requests
- preserves the same search parameters Stage 1 already uses per source item

### 1.2 `VectorBatchSearchResult`

Add:

```java
public record VectorBatchSearchResult(
        List<List<VectorSearchResult>> results,
        int invocationCount) {}
```

Required constructor semantics:

- `results` must be non-null
- the outer list must be defensively copied
- each inner result list must be defensively copied
- `invocationCount` must be non-negative

`invocationCount` means:

- the number of completed physical backend search invocations represented by the returned ordered batch result bundle
- not the number of logical requests
- not the number of attempted but failed optimistic calls that were later discarded

### 1.3 `MemoryVector.searchBatch(...)`

Add:

```java
default Mono<VectorBatchSearchResult> searchBatch(
        MemoryId memoryId,
        List<VectorSearchRequest> requests,
        int maxConcurrency)
```

Required semantics:

- `requests` must be non-null
- `maxConcurrency` must be positive
- an empty request list must return `results = List.of()` and `invocationCount = 0`
- output order must exactly match input request order
- `results.size()` must equal `requests.size()`
- each request remains logically independent
- the method boundary is all-or-error: it must either return a complete ordered result bundle or error
- implementations that decompose the batch into multiple physical searches must not exceed `maxConcurrency` concurrent backend invocations
- implementations that issue one true native batch search may treat the concurrency requirement as satisfied by construction

Rationale:

- preserving order lets `SemanticItemLinker` map results back to source items without changing normalization logic
- the all-or-error boundary gives the linker one clear degradation hook
- `maxConcurrency` preserves the caller's resource contract for fallback or adapter implementations

## 2. Provide A Backend-Neutral Default Implementation

The default `MemoryVector.searchBatch(...)` implementation should:

1. return an empty `VectorBatchSearchResult` immediately for `requests.isEmpty()`
2. iterate the input requests in order
3. execute single-query `search(memoryId, query, topK, minScore, filter)` per request
4. use `flatMapSequential(..., maxConcurrency, 1)` so decomposed execution never exceeds the caller's concurrency bound
5. collect each request's hits into a `List<VectorSearchResult>`
6. collect the ordered per-request lists into the final `VectorBatchSearchResult`
7. set `invocationCount = requests.size()`

This default implementation must not silently swallow individual request failures into empty results. Any request failure must error the whole batch method so the caller can decide whether and how to degrade.

Rationale:

- keeps the baseline backend-neutral
- avoids making non-batch-capable backends slower than Stage 1
- provides immediate correctness for all existing plugins

## 3. Adopt Batch Search In `SemanticItemLinker`

`SemanticItemLinker` should keep the Stage 1 window pipeline and replace only raw hit collection.

### 3.1 Window search flow

For each source window:

1. preserve source-item order
2. build `VectorSearchRequest` objects for source items whose canonical semantic query text is non-blank
3. keep blank-query items in the window, but map them directly to empty `SourceSearchOutcome`
4. call `vector.searchBatch(memoryId, requests, options.semanticLinkConcurrency())`
5. map ordered batch results back onto the original source-item order
6. continue with the existing Stage 1 resolve, normalize, and persist phases unchanged

Stage 2 does not change:

- `ItemEmbeddingTextResolver.resolve(...)`
- `maxSemanticLinksPerItem + semanticSearchHeadroom + 1`
- `semanticMinScore`
- deterministic normalization

### 3.2 Fallback behavior

If `searchBatch(...)` fails for a window, `SemanticItemLinker` must:

1. mark that window as using search fallback
2. replay the non-blank source items in that window through the existing single-request search path
3. preserve Stage 1 behavior for any source item whose fallback search still fails
4. continue with later windows regardless of that window's degradation result

The fallback boundary is the current source window only. Stage 2 must not drop the entire semantic stage when one batch-search attempt fails.

### 3.3 Invalid batch result handling

If an implementation returns a malformed `VectorBatchSearchResult`, including:

- `results.size() != requests.size()`
- null result lists
- any other violation of the ordered bundle contract

`SemanticItemLinker` must treat that as a batch-search failure and fall back for that window.

Rationale:

- keeps the linker defensive against buggy plugin implementations
- protects deterministic per-source normalization from misaligned result bundles

## 4. Preserve And Clarify Observability Semantics

Stage 2 must keep the Stage 1 stats surface and make the search counters more explicit.

### 4.1 Counter semantics

- `searchRequestCount`
  Counts logical semantic search requests and remains one-per-source-item with a non-blank canonical query, even if that logical request is later replayed during fallback.

- `searchInvocationCount`
  Counts completed physical vector-backend search invocations on the execution path the linker ultimately commits to after any window-level fallback. It includes committed per-source searches that return hits or fail into empty results, and excludes abandoned optimistic batch-search attempts that were discarded before fallback completed.

- `searchFallbackCount`
  Counts logical requests replayed after a window-level batch-search failure.

Implications:

- on the default `MemoryVector.searchBatch(...)` path, `searchInvocationCount` will typically equal `searchRequestCount`
- on a future native batch-search backend, `searchInvocationCount` may drop below `searchRequestCount`
- on the required generic Spring AI plugin implementation, `searchInvocationCount` is expected to remain equal to `searchRequestCount`

### 4.2 Duration semantics

`searchPhaseDurationMs` remains a wall-clock duration measured once per window at the search-phase boundary and summed across windows.

If a window first attempts `searchBatch(...)` and then falls back to single-request search, that window's `searchPhaseDurationMs` must include:

- the failed batch-search attempt
- the fallback replay

This preserves the Stage 1 duration definition and keeps degradation cost visible.

## 5. Required Official Plugin Implementation: `SpringAiMemoryVector`

`SpringAiMemoryVector` is part of the required Stage 2 scope.

Required implementation behavior:

- override `searchBatch(...)`
- preserve the ordered bundle contract
- honor `maxConcurrency`
- reuse the same request-shaping semantics as single-query search:
  - same `memoryId` filter
  - same caller-provided metadata filters
  - same `topK`
  - same `minScore`
- preserve the existing retry policy semantics for each physical search invocation
- return all-or-error, not partial success
- report `invocationCount = requests.size()` on successful completion

Required implementation strategy:

- adapt the current single-query `VectorStore.similaritySearch(SearchRequest)` capability into the new batch method
- do not require native-client branching in the baseline implementation

Explicit non-promise:

- this required Spring AI implementation does **not** promise physical invocation collapse under the generic `VectorStore` abstraction
- its value is contract alignment, ordered batch behavior, and future upgrade readiness

Rationale:

- keeps the first official plugin aligned with the new core API
- avoids hard-coding assumptions about particular Spring AI vector-store clients into the baseline design
- leaves room for a later backend-specific fast path without changing `SemanticItemLinker`

## Failure And Degradation Semantics

This section is mandatory.

1. **Default batch-search decomposition failure**

   If any decomposed single-request search fails inside the default `MemoryVector.searchBatch(...)`, the method errors and returns no partial ordered bundle.

2. **Plugin batch-search adapter failure**

   If the Spring AI batch adapter cannot complete the ordered bundle, it errors and returns no partial ordered bundle.

3. **Semantic linker batch-search failure**

   If `SemanticItemLinker` receives an error from `searchBatch(...)`, it falls back for that source window only.

4. **Fallback single-source failure**

   If a single source item still fails during fallback replay, Stage 1 behavior remains unchanged: that source contributes empty results and the window continues.

5. **Whole semantic stage failure**

   Batch-search capability must not compromise item persistence or structured graph persistence.

## Public API Surface

### Add

- `MemoryVector.searchBatch(...)`
- `VectorSearchRequest`
- `VectorBatchSearchResult`

### Keep

- existing `search(...)` overloads
- existing item-graph runtime options
- existing tracing attribute schema

### Do not add in Stage 2

- `semanticBatchSearchEnabled`
- `semanticBatchSearchSize`
- `semanticBatchSearchConcurrency`
- plugin-specific native capability flags

Rationale:

- Stage 2 should exploit existing `semanticLinkConcurrency` instead of adding new operator-facing knobs
- backend-specific acceleration should remain an implementation concern unless durable product value justifies exposure later

## File Structure

### Required

- `memind-core/src/main/java/com/openmemind/ai/memory/core/vector/MemoryVector.java`
  Add the default ordered batch-search capability.
- `memind-core/src/main/java/com/openmemind/ai/memory/core/vector/VectorSearchRequest.java`
  Add the immutable batch-search request value object.
- `memind-core/src/main/java/com/openmemind/ai/memory/core/vector/VectorBatchSearchResult.java`
  Add the immutable ordered batch-search result bundle.
- `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/item/graph/SemanticItemLinker.java`
  Adopt batch search at source-window granularity while preserving Stage 1 normalization and degradation behavior.
- `memind-plugins/memind-plugin-ai-spring-ai/src/main/java/com/openmemind/ai/memory/plugin/ai/spring/SpringAiMemoryVector.java`
  Override `searchBatch(...)` and align the official Spring AI plugin with the new contract.

### Required Tests

- `memind-core/src/test/java/com/openmemind/ai/memory/core/vector/MemoryVectorDefaultBatchSearchTest.java`
  Verify the default implementation preserves request order, honors `maxConcurrency`, defensively copies filters, and reports `invocationCount = requests.size()`.
- `memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/item/graph/SemanticItemLinkerTest.java`
  Verify successful ordered batch-search mapping, malformed bundle fallback, batch-search failure fallback, and stable `searchRequestCount` / `searchInvocationCount` / `searchFallbackCount` semantics.
- `memind-plugins/memind-plugin-ai-spring-ai/src/test/java/com/openmemind/ai/memory/plugin/ai/spring/SpringAiMemoryVectorTest.java`
  Verify the official plugin's batch method preserves order, respects `maxConcurrency`, and reports the expected invocation count under the generic Spring AI adapter path.

### Explicitly Not Required

- changes to persisted runtime config codecs
- changes to admin config endpoints
- changes to `ItemGraphOptions`
- changes to tracing schema names

## Acceptance Criteria

1. For the same raw search results, Stage 2 produces the same normalized semantic links as Stage 1.
2. `SemanticItemLinker` calls `searchBatch(...)` once per source window that contains at least one non-blank semantic query on the happy path.
3. A failed `searchBatch(...)` attempt degrades only the affected source window and falls back to the existing single-request path.
4. `searchRequestCount` remains a logical-request metric, and `searchInvocationCount` remains a physical-invocation metric, across both success and fallback paths.
5. The default `MemoryVector.searchBatch(...)` implementation never exceeds the caller-provided `maxConcurrency` when decomposing into single-request searches.
6. The required Spring AI plugin implementation passes the ordered batch-search contract without claiming generic `VectorStore` physical invocation collapse it cannot guarantee.
7. Stage 2 adds no new public item-graph runtime knobs.

## Comparison To Hindsight

This stage borrows one idea from hindsight:

- capable vector backends should be allowed to reduce search-side round trips through a batch-capable interface

It deliberately does not adopt:

- backend-specific query planning assumptions in the baseline path
- queue-based semantic-link workers
- mixing search throughput work with semantic graph completeness work

Rationale:

- memind still treats semantic links as bounded, best-effort retrieval hints
- Stage 2 should finish the synchronous write-path throughput story before considering heavier lifecycle changes

## Out Of Scope Follow-Ups

If Stage 2 proves insufficient under real workloads, the next steps should be separate specs:

- backend-specific native batch-search fast paths for selected vector plugins
- deferred semantic-link materialization
- optional intra-batch semantic linking
- semantic-edge rebuild or repair workflows

These should not be mixed into the current Stage 2 search-capability scope.
