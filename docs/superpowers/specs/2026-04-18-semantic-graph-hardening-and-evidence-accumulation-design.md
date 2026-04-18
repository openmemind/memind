# Semantic Graph Hardening and Evidence Accumulation Design

> This document supersedes the direction proposed in `2026-04-18-semantic-graph-evolution-design.md`.
> Goal: evolve memind's semantic item graph in a way that is consistent with the current architecture, implementation facts, and open-source maintenance requirements.

## Scope

This design only covers the `SEMANTIC` item-link family.

It does not change:

- entity extraction or entity sibling expansion
- temporal link construction or retrieval
- causal link construction or retrieval
- `whenToUse` semantics for tool memories
- memory thread derivation

## Problem Statement

The current semantic graph implementation is structurally simple and generally aligned with memind's architecture:

- semantic links are created from vector similarity after item persistence
- retrieval uses semantic links as a bounded one-hop graph-assist channel
- graph expansion is secondary to direct retrieval, not a standalone graph-first retrieval path

However, one weakness is real and worth addressing:

1. semantic graph candidates from multiple direct-retrieval seeds are merged by `max`, which discards convergence evidence

Several other proposed changes are **not** sufficiently justified for memind at this stage:

- replacing the canonical embedding text contract with augmented text
- adding explicit reverse semantic edges
- redesigning the linker around intra-batch pairwise similarity by default
- adding link-rebuild APIs before a rebuild strategy actually exists

## Current Implementation Facts

### Fact 1: Semantic linking happens after vector storage and item persistence

Current write path:

1. `MemoryItemLayer.vectorizeAndPersist(...)` calls `vector.storeBatch(...)`
2. the same method constructs `MemoryItem` objects and persists them through `itemOperations.insertItems(...)`
3. only then does it invoke `graphMaterializer.materialize(...)`
4. `DefaultItemGraphMaterializer.materialize(...)` persists structured graph hints, then calls `SemanticItemLinker.link(...)`

Implication:

- the current core pipeline does **not** prove that same-batch items are invisible to semantic linking
- if a backend has write-after-read lag, that is a backend consistency concern and should be treated as such

### Fact 2: The canonical semantic embedding text is currently `content`

`ItemEmbeddingTextResolver.resolve(...)` currently returns raw `content` for both:

- item vectorization
- semantic link query construction

Implication:

- the codebase currently enforces a single canonical text rule
- any embedding augmentation proposal is a retrieval-contract change, not a local graph-only tweak

### Fact 3: Retrieval already reads adjacency as bidirectional

`GraphOperations.listAdjacentItemLinks(...)` and current store implementations treat a link as adjacent when:

- `seed == source`, or
- `seed == target`

Implication:

- storing both `A -> B` and `B -> A` is not required to make retrieval traverse both directions
- explicit reverse-edge storage must justify itself by measurable gain, not by connectivity claims alone

### Fact 4: Simple retrieval is intentionally bounded

Graph assist is bounded by:

- seed count
- per-family neighbor caps
- max expanded items
- graph channel weight below direct channel

Implication:

- memind is not trying to be hindsight's dense graph-first retrieval system
- denser semantic graphs must be justified under bounded one-hop expansion, not by analogy to hindsight

## Design Principles

1. **Preserve the current semantic contract unless evidence proves it is wrong**

   The default semantic embedding text remains `content`.

2. **Treat semantic links as retrieval hints, not durable facts**

   They may be weak, sparse, and asymmetric in storage as long as retrieval semantics remain correct and bounded.

3. **Prefer retrieval-side evidence fusion over storage-side graph inflation**

   If the problem is lost convergence signal, solve it where the loss occurs.

4. **Do not introduce new graph maintenance APIs without a concrete lifecycle**

   Rebuild, replacement, and deletion primitives should follow a proven rebuild workflow, not precede it.

5. **Keep the open-source default backend-neutral and easy to reason about**

   The baseline design must remain understandable across in-memory and relational/vector-store implementations.

## Non-Goals

This design deliberately does **not** do the following:

- augment semantic embeddings with `whenToUse`
- augment semantic embeddings with category or entity names by default
- store synthetic reverse semantic edges
- lower semantic thresholds before retrieval-side evidence fusion is measured
- add asynchronous semantic-link workers or queues
- introduce a semantic-edge rebuild pipeline

## Proposed Architecture

The recommended evolution has three stages.

### Stage 1: Correctness and Observability Hardening

This stage does not change semantic ranking behavior. It makes the current behavior explicit, testable, and measurable.

#### 1.1 Linker determinism

`SemanticItemLinker` should explicitly normalize search results before truncation:

- drop invalid vector ids
- de-duplicate by vector id
- resolve vector ids to items
- drop self-links
- sort candidates by descending similarity score
- keep at most `maxSemanticLinksPerItem`

Rationale:

- current behavior implicitly depends on backend return order
- a deterministic linker is easier to test across backends

#### 1.2 Same-batch visibility verification

Add tests that verify whether same-batch items can be linked under the current supported write path.

Expected interpretation:

- if current backends already expose same-batch items after `storeBatch(...)`, no architectural change is required
- if a specific backend exhibits write-after-read lag, handle it as a backend compatibility concern rather than redesigning the global semantic pipeline

#### 1.3 Semantic graph metrics

Add tracing/metrics around semantic graph construction and graph-assist retrieval:

- semantic links created per item
- semantic search hits before truncation
- same-batch hit count, if observable in tests/instrumentation
- graph-assist admitted candidates
- graph candidates discarded due to overlap with direct retrieval

Rationale:

- later parameter changes should be driven by observed behavior, not intuition

### Stage 2: Retrieval-Side Semantic Evidence Accumulation

This is the core functional change.

#### 2.1 Problem to solve

Current graph assist merges multiple graph paths to the same candidate by `max`.

That is appropriate for some relation families, but it is weak for `SEMANTIC` because semantic convergence from multiple direct seeds is meaningful signal.

Example:

- seed A semantically points to candidate X
- seed B also semantically points to candidate X
- current logic keeps only the stronger of the two paths

That loses the fact that X is semantically close to multiple independently retrieved seeds.

#### 2.2 Scope of accumulation

Only the `SEMANTIC` family changes.

The following families keep existing `max` merge behavior:

- `TEMPORAL`
- `CAUSAL`
- `ENTITY_SIBLING`

Rationale:

- this design is explicitly scoped to semantic graph evolution
- it avoids unintentionally changing the semantics of other graph channels

#### 2.3 Accumulation model

For each candidate item, maintain semantic evidence keyed by `seedItemId`.

Rules:

1. if the same seed produces multiple semantic paths to the same candidate, keep only the strongest contribution for that seed
2. sort per-seed semantic contributions in descending order
3. apply diminishing returns by rank
4. compress the accumulated semantic score to a bounded range
5. compare that accumulated semantic score against the best non-semantic path and keep the stronger graph-side result

Reference formula:

```java
Map<Long, Double> semanticEvidenceBySeed;

semanticEvidenceBySeed.merge(seedItemId, incomingScore, Math::max);

List<Double> contributions =
        semanticEvidenceBySeed.values().stream()
                .sorted(Comparator.reverseOrder())
                .toList();

double raw = 0.0d;
for (int i = 0; i < contributions.size(); i++) {
    double decay = 1.0d / (1.0d + i * evidenceDecayFactor);
    raw += contributions.get(i) * decay;
}

double semanticAccumulatedScore = Math.tanh(raw);
```

Where:

- the first semantic seed contributes fully
- later semantic seeds still help, but less
- score remains bounded and comparable inside graph fusion

#### 2.4 Why this is the right place to solve the problem

Because the real loss currently happens in retrieval, not during link construction.

memind already stores enough semantic hints for bounded graph assist. The missing capability is that the retrieval layer cannot reward multi-seed semantic convergence.

This stage:

- solves that exact problem
- does not alter the storage model
- does not force a rebuild
- stays fully backend-neutral

#### 2.5 Data structure change

`DefaultRetrievalGraphAssistant` should evolve `GraphCandidate` from a single-path container into a small aggregation container that can represent:

- best non-semantic path score
- semantic evidence per seed
- final provisional graph score
- resolved item payload

This is a contained refactor inside the retrieval assistant, not a cross-cutting graph model rewrite.

### Stage 3: Optional Bounded Linker Concurrency

This stage is optional and only for throughput.

If semantic linking latency becomes noticeable, `SemanticItemLinker.link(...)` may switch from sequential linking to bounded parallelism with a configurable concurrency cap.

Requirements:

- no correctness change
- no same-batch pairwise phase
- no explicit reverse-edge generation
- preserve best-effort behavior and bounded resource usage

Rationale:

- this is a performance optimization, not a semantic-model change
- it should not be mixed with retrieval behavior changes during evaluation

## Explicit Rejections

### Rejection 1: Embedding augmentation as the new default

Not accepted for the baseline design.

Reasons:

- current repo intentionally uses `content` as the canonical embedding contract
- changing the contract changes both direct retrieval and semantic linking semantics
- there is currently no offline evaluation or rebuild plan proving that the gain outweighs the complexity
- category and entity augmentation may help some short-text cases while hurting general query-text alignment

Future reconsideration is allowed, but only behind an explicit strategy boundary and with measurement.

### Rejection 2: Intra-batch pairwise semantic linking as a default phase

Not accepted for the baseline design.

Reasons:

- the current core pipeline already stores vectors and items before semantic linking runs
- the claimed batch invisibility problem is not established as a memind architectural fact
- pairwise in-memory similarity adds new algorithmic complexity and duplicate-merge requirements
- this is the wrong abstraction if the actual issue is backend consistency lag

If a backend-specific visibility issue is confirmed, solve it as a compatibility layer or fallback path.

### Rejection 3: Synthetic reverse semantic edges

Not accepted for the baseline design.

Reasons:

- retrieval already reads adjacency bidirectionally
- storing both directions inflates link count and can consume bounded neighbor budgets without new information
- it complicates future merge, rebuild, and deletion semantics

Reverse-edge storage should only be reconsidered if retrieval semantics change to directional traversal, which is not part of this design.

### Rejection 4: Preemptive link-rebuild APIs

Not accepted now.

Reasons:

- there is no approved semantic-edge rebuild lifecycle yet
- source-only replacement is insufficient once synthetic reverse edges or multi-origin links exist
- maintenance APIs should follow a concrete rebuild requirement, not speculative future needs

## Parameters

The baseline parameter recommendation is conservative.

### Item graph construction

- `maxSemanticLinksPerItem = 5`
- `semanticMinScore = 0.82`

### Graph-assisted retrieval

Keep existing defaults initially, including:

- semantic neighbor caps
- expanded item caps
- graph channel weight

Add:

- `semanticEvidenceDecayFactor = 0.5`

Rationale:

- do not increase graph density before proving that semantic accumulation improves outcomes
- separate "better fusion" from "more edges" in evaluation

## Validation Plan

### Unit tests

1. semantic candidates reached from two different seeds should outrank an otherwise similar candidate reached from one semantic seed
2. repeated semantic evidence from the same seed should not stack beyond the strongest path
3. non-semantic families should preserve current `max` merge behavior
4. semantic accumulated score should remain bounded
5. linker truncation should be deterministic after score ordering

### Integration tests

1. verify `insertItems(...)` happens before semantic linking
2. verify semantic linker uses the same canonical text rule as vectorization
3. verify current supported backend path does not require a separate intra-batch pass for correctness

### Evaluation metrics

Evaluate before any threshold or density change:

- admitted graph candidate count
- overlap count with direct retrieval
- displacement of direct-retrieval top items
- query-set win/loss analysis on semantic-convergence cases

Success criteria:

- semantic graph assist improves retrieval on multi-seed semantic-convergence queries
- no clear regression on precision-sensitive queries
- no substantial latency regression under default settings

## Backward Compatibility

This design is backward compatible by default because:

- it does not change stored item vectors
- it does not require semantic-edge rebuild
- it does not change stored link identity
- it does not introduce backend-specific storage requirements

The only externally visible change is retrieval ranking behavior for graph-assisted semantic candidates.

## Rollout Order

1. Stage 1: correctness and observability hardening
2. Stage 2: retrieval-side semantic evidence accumulation
3. Stage 3: optional bounded linker concurrency

This order is intentional:

- first make current behavior explicit and measurable
- then improve semantic fusion
- only then consider throughput optimization

## Comparison with Hindsight

Hindsight remains useful as a reference, but only selectively.

Borrow:

- the idea that semantic convergence from multiple seeds is valuable
- the idea that graph-assisted retrieval should make convergence visible in ranking

Do not directly borrow:

- dense graph assumptions
- graph-first retrieval posture
- embedding augmentation as the default contract
- symmetric semantic edge materialization

Reason:

memind's current architecture is a bounded direct-retrieval-first system. The semantic graph should support that design, not pull the project into a different retrieval model by accident.

## Implementation Summary

The correct next implementation target is:

- harden and instrument the current semantic linker
- implement semantic-only evidence accumulation in `DefaultRetrievalGraphAssistant`
- keep semantic embedding text as `content`
- keep semantic link storage sparse and asymmetric
- avoid speculative rebuild and reverse-edge work

This gives memind a cleaner, more defensible semantic graph evolution path that is better aligned with the current codebase and more suitable as an open-source baseline.
