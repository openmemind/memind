# Memind Item Graph Evolution Design

**Date:** 2026-04-20
**Status:** Proposed
**Scope:** `memind-core` item graph extraction, relation materialization, graph store contract, retrieval graph usage, and MyBatis-backed graph execution path

## 1. Context

`memind` already has a stronger semantic item graph architecture than many retrieval-first memory systems:

- extraction-side graph hints are typed and bounded
- entity normalization is multilingual-aware and conservative
- alias evidence is modeled explicitly
- temporal and semantic relations are derived in dedicated stages
- retrieval can already consume semantic, temporal, causal, and entity-sibling signals

At the same time, the current implementation still has several maturity gaps:

- relation semantics such as temporal and causal subtype are still encoded in `metadata`
- graph computation and graph persistence are not fully separated
- graph pipeline stages are organized semantically, but not yet around explicit precompute / commit / post-commit boundaries
- the SQL-backed graph store still performs several effectively row-oriented upsert paths
- entity cooccurrence rebuild is correct but not store-native enough for long-term scale
- retrieval graph currently behaves as a one-hop enrichment layer, not as a first-class optional graph expansion path

This design proposes the next evolution step for item graph: keep the current semantic rigor, then raise execution quality to open-source production standards.

## 2. Problem Statement

The current item graph is directionally correct but still under-optimized along three dimensions.

### 2.1 Semantic representation is richer than storage representation

The pipeline distinguishes semantic, temporal, causal, and entity paths, but the persisted relation model is still too generic. Temporal relation subtype such as `before` and causal subtype such as `caused_by` are not yet first-class persisted fields.

### 2.2 Pipeline responsibilities are not fully isolated

Some classes still mix candidate lookup, relation planning, persistence, and stage-level degradation handling. This makes transaction boundary design, partial failure semantics, and testing harder than necessary.

### 2.3 Store and retrieval execution paths lag behind the semantic model

The core graph model already carries richer meaning than the store and retrieval execution paths fully exploit. The system needs stronger bulk persistence, explicit capability boundaries, family-level degradation, and an optional graph expansion mode for retrieval.

## 3. Goals

This design has six primary goals.

### 3.1 Preserve and strengthen semantic rigor

`memind` must keep its current strengths:

- conservative entity resolution
- typed entity categories
- multilingual normalization
- explicit alias evidence
- explicit special-anchor handling

The next phase must not regress toward fuzzy, name-dominant entity merging.

### 3.2 Make relation semantics explicit

Temporal and causal subtype must stop living only in generic metadata. The persisted relation model must expose normalized fields for subtype and evidence source.

### 3.3 Separate planning from persistence

Graph builders should compute a write plan. A dedicated persistence component should apply that plan. Post-commit derived maintenance should be a separate phase.

### 3.4 Improve execution quality without hard-coding one storage backend into core

The core graph model must remain backend-agnostic. SQL-optimized behavior must live behind store contracts and capabilities.

### 3.5 Upgrade retrieval graph from single-mode assist to dual-mode support

Retrieval graph should support both:

- one-hop assist over direct results
- bounded graph expansion from direct seeds

The default may remain conservative, but the architecture must support both cleanly.

### 3.6 Produce an open-source quality subsystem

The design must optimize for:

- clean boundaries
- explicit invariants
- partial failure containment
- deterministic tests
- future plugin evolution

## 4. Non-Goals

This design intentionally does not attempt to do the following.

### 4.1 No regression to fuzzy entity resolution

The system will not adopt name-similarity-first entity merging as the default strategy.

### 4.2 No multi-hop graph traversal in the first expansion version

The first retrieval graph expansion path remains bounded to one hop. Multi-hop expansion can be evaluated later with evidence.

### 4.3 No core-layer database specialization

Core graph APIs must not directly encode PostgreSQL-specific or MySQL-specific query behavior.

### 4.4 No persisted item-to-item entity link as canonical truth

Entity relations continue to be represented canonically as:

`item -> mention -> entity`

Entity-derived item siblings may be computed at query time. They must not replace mentions as the source of truth.

### 4.5 No compatibility-driven compromise

This is a new, unreleased feature area. The design prioritizes correctness and long-term quality over temporary compatibility preservation.

## 5. Design Principles

### 5.1 Model first, optimize second

When semantics and implementation convenience conflict, the semantic model wins. Execution optimizations must adapt to the model, not the reverse.

### 5.2 Source facts vs derived caches

The design distinguishes between:

- source-of-truth graph facts: entities, aliases, mentions, item relations
- derived caches: cooccurrence aggregates, bounded query helpers, optional retrieval accelerators

Derived caches may be rebuilt. Source facts may not be silently approximated.

### 5.3 Phase boundaries are explicit

Every graph operation belongs to one of three phases:

- precompute
- commit
- post-commit

Phase ownership must be reflected in classes, method names, diagnostics, and failure handling.

### 5.4 Core stays typed, stores may flatten

Core APIs should expose typed concepts. Store implementations may flatten those concepts into strings or columns if needed, but only behind validated adapters.

### 5.5 Best-effort does not mean undefined

Optional graph work may degrade or skip, but degradation behavior must be explicit, measured, and family-scoped.

## 6. Current-State Assessment

### 6.1 Existing strengths

The current design already has several excellent foundations:

- `GraphHintNormalizer` cleanly separates extraction-side normalization from persistence concerns.
- `EntityHintNormalizer` and conservative resolution provide a much stronger entity model than fuzzy text matching pipelines.
- `EntityAliasIndexPlanner` turns resolution evidence into reusable alias history.
- `TemporalItemLinker` models temporal relation family semantics instead of treating time as generic proximity.
- `SemanticItemLinker` is backend-agnostic and already supports batch search, embedding reuse, and fallback paths.
- retrieval graph already supports semantic, temporal, causal, and entity-sibling families with bounded fanout controls.

### 6.2 Existing gaps

The main gaps are architectural rather than conceptual:

- `ItemLink` is too generic for long-term relation semantics.
- linkers still both compute and persist.
- source-of-truth graph writes still inherit the current post-item best-effort stage semantics.
- cooccurrence rebuild is not sufficiently store-native in SQL-backed implementations.
- graph retrieval degrades at the whole-assistant level more often than at the per-family level.
- graph execution contracts are not yet strong enough to express bulk write quality and bounded read quality separately.

## 7. Target Architecture

The target architecture keeps the current semantic direction and reorganizes the subsystem into explicit layers.

### 7.1 Layer model

The item graph subsystem will be organized into the following conceptual layers:

1. `graph.hints`
   - normalize extraction-side entities and causal hints
2. `graph.plan`
   - compute a typed graph write plan from normalized inputs and historical candidates
3. `graph.commit`
   - atomically persist source-of-truth graph facts
4. `graph.derived`
   - rebuild or refresh derived caches after commit
5. `graph.retrieval`
   - consume persisted graph facts in assist or expansion mode

This is a logical layering requirement. Package names may reflect the existing codebase rather than exactly these labels, but the boundary itself is required.

### 7.2 Three execution phases

Every materialization request runs through these phases.

#### Phase 1: Precompute

Responsibilities:

- normalize graph hints
- resolve entities
- plan alias index updates
- compute causal relations
- compute temporal candidate matches and selected temporal relations
- compute semantic candidate matches and selected semantic relations
- assemble a typed graph write plan

Properties:

- no source-of-truth graph writes
- safe to degrade per family
- deterministic and testable in isolation

#### Phase 2: Commit

Responsibilities:

- persist entities
- persist mentions
- persist aliases
- persist item relations
- record affected entity keys and relation diagnostics

Properties:

- one atomic graph write operation from the perspective of the materializer
- idempotent upsert behavior
- no cache rebuild logic mixed into the write path

#### Phase 3: Post-Commit

Responsibilities:

- rebuild entity cooccurrence for affected entity keys
- refresh optional derived statistics or bounded lookup helpers
- finalize graph diagnostics

Properties:

- best-effort allowed
- source-of-truth graph remains valid even if this phase degrades
- failures are visible in diagnostics, not silent

### 7.3 System-Level Consistency Model

This design explicitly upgrades source-of-truth graph writes from best-effort behavior to commit-critical behavior.

The system-level consistency decision is:

- persisted items and persisted source-of-truth graph facts form one logical extraction commit
- source-of-truth graph facts are:
  - entities
  - mentions
  - aliases
  - typed item relations
- derived graph artifacts are not part of that logical commit

Required guarantees:

- an extraction request is not considered successfully committed until both item records and source-of-truth graph facts are durable
- source-of-truth graph writes are not optional and are not best-effort
- if item persistence succeeds but source-of-truth graph persistence fails, the batch must remain in an explicit incomplete state and must be retried or repaired before it is exposed as fully committed
- post-commit derived maintenance remains best-effort and may degrade independently

This means the current "post-item-persistence best-effort graph materializer" semantics will be narrowed to derived-maintenance semantics only. Source-of-truth graph persistence becomes part of the commit-critical path.

### 7.4 Extraction Commit Protocol

The logical extraction commit must be backed by an explicit protocol, not only by a high-level architectural statement.

Required protocol model:

- every extraction batch receives an `extractionBatchId`
- source-of-truth item rows and source-of-truth graph facts are associated with that batch
- the batch moves through an explicit visibility state machine:
  - `PENDING`
  - `COMMITTED`
  - `REPAIR_REQUIRED`

Required visibility rules:

- retrieval, listing, graph assist, graph expansion, and downstream derivation must only read `COMMITTED` batches
- `PENDING` batches are not externally visible
- `REPAIR_REQUIRED` batches are not externally visible and must not be treated as durable success

Required commit flow:

1. allocate `extractionBatchId`
2. write item source facts in `PENDING`
3. write graph source facts in `PENDING`
4. perform one final promote step to `COMMITTED`

Required failure behavior:

- if any source-of-truth write fails before promotion, the batch transitions to `REPAIR_REQUIRED`
- repair logic may either complete the missing source-of-truth writes and promote the batch or remove the failed batch
- callers must not report success until promotion to `COMMITTED` has completed

Implementation rule:

- stores that support one physical transaction across item and graph writes may implement this protocol as one transaction plus immediate promotion
- stores that do not support one physical transaction must implement hidden `PENDING` visibility plus final promotion
- a store that can do neither does not satisfy source-of-truth item graph requirements and must fail closed for this mode

## 8. Relation Model Evolution

### 8.1 Required model changes

The current single `ItemLink` model is insufficient for core domain logic because subtype semantics are hidden in `metadata` and invalid cross-family combinations remain constructible.

The relation model will evolve as follows:

- the core planning model uses family-specific typed relations:
  - `SemanticItemRelation`
  - `TemporalItemRelation`
  - `CausalItemRelation`
- the persisted representation uses a flattened relation record with explicit normalized fields:
  - `linkType`
  - `relationCode`
  - `evidenceSource`
  - `strength`
  - `metadata`
- `ItemLink` may remain as a store-facing flattened representation or be replaced by a dedicated persisted-link DTO, but it is no longer the canonical planning type
- `metadata` remains available only for non-core auxiliary attributes and diagnostics

### 8.2 Standardized relation codes

The following normalized relation codes are required.

#### Temporal relation codes

- `before`
- `overlap`
- `nearby`

#### Causal relation codes

- `caused_by`
- `enabled_by`
- `motivated_by`

#### Semantic evidence sources

- `vector_search`
- `same_batch_vector`
- `vector_search_fallback`

### 8.3 Validation rules

Validation must enforce the following invariants:

- `TEMPORAL` persisted relations require a temporal `relationCode` and must not carry a semantic-only `evidenceSource`
- `CAUSAL` persisted relations require a causal `relationCode` and must not carry a semantic-only `evidenceSource`
- `SEMANTIC` persisted relations may omit `relationCode`, but must carry a normalized `evidenceSource`
- `strength` remains normalized to `[0.0, 1.0]`.
- source and target item IDs must be distinct.

### 8.4 Typed construction path

Core relation construction must prevent invalid states by construction, not merely reject them after generic objects have already been built.

The required construction pattern is:

- a closed typed hierarchy such as a sealed interface plus family-specific relation records, or an equivalent compile-time-safe design
- family-specific enums for temporal and causal subtype
- family-specific constructors or factories that cannot create cross-family invalid combinations
- a commit-layer mapper that flattens typed relations into persisted `relationCode` / `evidenceSource`
- centralized validation during plan assembly and before persistence of the flattened representation

A generic builder that accepts arbitrary combinations of `linkType`, `relationCode`, and `evidenceSource` is not sufficient for the core domain model.

## 9. Graph Write Plan

### 9.1 Required plan object

The pipeline must introduce an explicit typed write plan, referred to in this design as `ItemGraphWritePlan`.

It must contain:

- entities to upsert
- mentions to upsert
- aliases to upsert
- semantic relations to persist
- temporal relations to persist
- causal relations to persist
- affected entity keys
- per-family diagnostics and degradation markers

The write plan is a domain object. It must carry typed relation collections, not only pre-flattened persistence records.

### 9.2 Required planner outputs

Each planner contributes only computed facts to the write plan.

#### Entity planning

Produces:

- resolved entities
- mentions
- alias candidates
- entity diagnostics

Does not:

- persist entities directly
- rebuild cooccurrence

#### Causal planning

Produces:

- normalized typed causal relations only

Does not:

- write links
- infer additional semantic or temporal behavior

#### Temporal planning

Produces:

- selected typed temporal relations with normalized temporal relation codes
- per-source candidate stats
- degradation markers if historical lookup partially fails

Does not:

- write links
- fetch unrelated graph families

#### Semantic planning

Produces:

- selected semantic relations with normalized evidence source
- same-batch and historical/vector diagnostics
- degradation markers if batch search or resolve fallback occurs

Does not:

- write links directly

### 9.3 Planner quality requirements

Planners must be:

- side-effect free with respect to graph persistence
- bounded in fanout
- deterministic under the same inputs
- individually unit-testable

## 10. Persistence Contract Evolution

### 10.1 Core contract direction

The graph store contract must explicitly distinguish between:

- source-of-truth write-plan application
- derived cache maintenance
- bounded query capabilities

The current table-shaped `GraphOperations` interface is not strong enough to remain the long-term primary orchestration contract.

### 10.2 New contract expectations

The graph store contract must support these guarantees.

#### Atomic source-of-truth apply

Core graph persistence must expose a primary `applyGraphWritePlan(...)` style contract for source-of-truth graph writes.

Required direction:

- write-plan application is the main core-facing persistence entrypoint
- write-plan application is atomic from the caller perspective, even if an implementation internally decomposes it
- write-plan application is idempotent for the same normalized inputs
- low-level table-shaped batch methods, if retained, are demoted to adapter-internal SPI or helper interfaces and are not the main orchestration surface

#### Logical extraction commit coordination

`applyGraphWritePlan(...)` is necessary but not sufficient for the overall consistency model.

The system also requires a higher-level extraction commit coordinator or equivalent orchestration contract that:

- coordinates item source writes and graph source-of-truth writes under one `extractionBatchId`
- enforces the `PENDING -> COMMITTED -> REPAIR_REQUIRED` visibility model
- guarantees that partially written batches are not externally readable
- exposes an explicit repair path for failed source-of-truth batches

This coordination may live above `GraphOperations`, but it is part of the required design and not an implementation detail left unspecified.

#### Store-native bounded reads

Stores should support bounded graph reads for:

- adjacent links by seed item IDs
- mentions by entity key with per-entity limit
- entity lookup by keys
- historical alias lookup by normalized alias

#### Derived rebuild isolation

Derived rebuild operations such as entity cooccurrence refresh must not be interleaved with source-of-truth writes.

### 10.3 Capability contract evolution

`GraphOperationsCapabilities` should evolve from a minimal pair of booleans into a small but meaningful contract.

Required capability directions:

- `supportsHistoricalAliasLookup()`
- `supportsBoundedEntityKeyLookup()`
- `supportsBoundedAdjacencyLookup()`
- `supportsStoreSideCooccurrenceRebuild()`

This should stay intentionally small. Capabilities must describe materially different execution quality, not every optional method.

## 11. SQL Store Requirements

### 11.1 Bulk upsert quality

The MyBatis-backed graph store must stop behaving like a row-oriented upsert wrapper in hot paths.

For SQL-backed implementations, the required direction is:

- bulk select existing identities in bounded sets
- bulk insert new rows
- bulk update existing rows where update semantics are required
- avoid per-record read-modify-write loops in graph hot paths

### 11.2 Store-side cooccurrence rebuild

Entity cooccurrence remains a derived cache, but SQL-backed stores must rebuild it in the database, not by loading the full mention universe into application memory.

Required behavior:

- rebuild only for affected entity keys
- aggregate from persisted mentions in the store
- keep rebuild idempotent for the same committed source facts

### 11.3 Query shaping requirements

SQL-backed graph reads must support:

- per-entity fanout limits
- bounded adjacency lookup by link family
- deterministic ordering for tie-breaking
- dialect-aware implementations hidden behind the plugin layer

## 12. Retrieval Graph Evolution

### 12.1 Dual-mode retrieval graph

Retrieval graph will support two modes.

#### Assist mode

This preserves the current philosophy:

- take direct retrieval candidates as seeds
- expand one hop
- merge graph candidates back into direct results

This remains the default conservative mode.

#### Expand mode

This adds a stronger graph path:

- take top-K direct seeds
- perform one-hop graph expansion by family
- score graph-derived candidates
- fuse graph and direct results with explicit weights

This is still bounded and one-hop only.

### 12.2 Family-level degradation

Graph retrieval must degrade per family, not only per request.

Required behavior:

- entity sibling expansion may be skipped independently
- semantic adjacency may be skipped independently
- temporal adjacency may be skipped independently
- causal adjacency may be skipped independently

If one family times out or fails, other families must remain usable unless the entire graph query budget is exhausted.

### 12.3 Entity graph usage policy

Entity-derived item expansion should continue to use mentions as the canonical source:

- seed items -> mentions
- mentions -> entity keys
- bounded reverse mention lookup -> sibling items

This design explicitly rejects persistent `ENTITY` item-to-item links as canonical truth.

### 12.4 Richer causal usage

The first retrieval graph upgrade does not require subtype-specific causal scoring, but it does require persisted causal subtype to be queryable and available to scoring.

Required rule for this design:

- causal family stays one retrieval family
- persisted subtype is still available to scoring and diagnostics

### 12.5 Fusion Contract

Retrieval graph fusion must be deterministic and explicitly specified.

Required rules:

- candidate identity is item ID; the final result set must not contain duplicates
- direct retrieval remains the primary base signal
- if an item appears in both direct and graph channels, it is merged into one candidate and graph evidence contributes a bounded graph bonus rather than creating a second entry
- in assist mode, configurable direct top-K protection is required and enabled by default
- graph-only candidates may displace only the unprotected direct tail
- graph family contributions are normalized inside the graph channel before the graph channel is fused with the direct channel
- if one family is skipped, degraded, or times out, that family contributes zero and must not penalize direct-channel scores
- identical inputs must produce identical fusion results, including tie-breaking behavior

## 13. Vector Capability Evolution

### 13.1 Motivation

The semantic linker is already backend-agnostic and correct, but it currently depends heavily on text-query search paths.

The next evolution should allow better semantic-link planning when a vector backend can natively support it.

### 13.2 Required direction

The vector contract should be extended in a later dedicated phase with optional advanced capabilities for:

- nearest-neighbor search by stored vector identity
- nearest-neighbor search by direct embedding vector
- ordered batch neighbor search over multiple vectors

These are optional advanced capabilities, not baseline requirements.

### 13.3 Fallback rule

If a vector backend does not support the advanced path, the current safe fallback remains valid:

- fetch stored embeddings when available
- otherwise rebuild embeddings
- otherwise use text-query search paths

Core logic must not assume the advanced vector path exists.

## 14. Package and Responsibility Reorganization

The package structure should follow semantic responsibility, not implementation history.

Recommended stable structure:

- `graph.pipeline`
- `graph.plan`
- `graph.commit`
- `graph.derived`
- `graph.entity.normalize`
- `graph.entity.resolve`
- `graph.entity.alias`
- `graph.relation.temporal`
- `graph.relation.semantic`
- `graph.relation.causal`
- `graph.retrieval`
- `graph.diagnostics`

This does not require large cosmetic churn. It does require that planner, persister, and derived-maintenance classes stop collapsing into the same files.

## 15. Diagnostics and Observability

Diagnostics are a first-class feature of the graph subsystem.

The evolved design must expose:

- per-phase success / degraded / failure markers
- per-family candidate counts and admitted counts
- explicit fallback counts
- explicit subtype normalization counts where useful
- post-commit derived maintenance degradation

The existing `ItemGraphMaterializationResult.Stats` direction is good and should be preserved, but the metrics must align with the new phase model and family-level degradation model.

## 16. Testing Strategy

The design requires a test strategy aligned to subsystem boundaries.

### 16.1 Planner tests

Planner tests must be pure and deterministic.

Required coverage:

- entity normalization and conservative resolution outcomes
- temporal relation classification and selection
- semantic candidate merge and bounded selection
- causal hint normalization and subtype validation

### 16.2 Persistence tests

Persistence tests must verify:

- idempotent upsert behavior
- explicit relation subtype persistence
- extraction batch visibility behavior for `PENDING`, `COMMITTED`, and `REPAIR_REQUIRED`
- partially written source-of-truth batches are not externally visible
- repair-path behavior for failed source-of-truth batches
- derived rebuild boundaries
- store capability fail-closed behavior

### 16.3 Retrieval tests

Retrieval graph tests must verify:

- assist mode fusion
- expand mode bounded expansion
- family-specific degradation
- high-fanout entity guardrails
- direct and graph overlap deduplication
- direct top-K protection behavior
- deterministic tie-breaking under identical inputs

### 16.4 Cross-store parity

The in-memory store remains the correctness oracle for semantics. SQL-backed implementations must add parity tests where behavior is supposed to match.

## 17. Rollout Strategy

There is no external compatibility requirement, but the implementation should still roll out in internal phases to reduce risk and preserve review quality.

### Phase A: Model hardening

- add normalized relation subtype fields
- add typed relation validation
- introduce write-plan objects

### Phase B: Pipeline restructuring

- split precompute, commit, post-commit responsibilities
- introduce extraction commit coordination and batch visibility gating
- move linkers from compute-and-write to compute-only planners

### Phase C: Store execution hardening

- upgrade SQL bulk paths
- implement store-side cooccurrence rebuild
- strengthen capabilities

### Phase D: Retrieval graph upgrade

- introduce assist and expand modes
- add family-level degradation

### Phase E: Optional vector capability enhancement

- add advanced vector capability path without breaking baseline implementations

## 18. Risks and Mitigations

### 18.1 Risk: relation model churn becomes broader than needed

Mitigation:

- keep top-level `ItemLinkType` family model
- add explicit subtype and evidence fields without redesigning the entire graph store around polymorphic tables

### 18.2 Risk: capability surface grows uncontrollably

Mitigation:

- only expose capabilities that materially change execution quality
- keep baseline contract complete and fail-closed

### 18.3 Risk: logical extraction commit becomes under-specified across stores

Mitigation:

- define one explicit batch visibility state machine
- require either one physical transaction or hidden pending visibility plus promotion
- fail closed on stores that cannot satisfy either mode

### 18.4 Risk: retrieval expansion complexity grows too quickly

Mitigation:

- first release supports one-hop expansion only
- keep per-family budgets and explicit fanout limits

### 18.5 Risk: SQL optimization leaks into core

Mitigation:

- core defines semantics and required bounded behavior
- plugin/store layer owns dialect-specific implementations

## 19. Rejected Alternatives

### 19.1 Rejected: keep subtype only in metadata

Reason:

- weak invariants
- harder query shaping
- harder scoring evolution
- poorer open-source readability

### 19.2 Rejected: persist entity-derived item links as canonical graph truth

Reason:

- duplicates information already represented by mentions
- introduces consistency burden
- weakens the semantic model

### 19.3 Rejected: pivot to fuzzy recall-first entity resolution

Reason:

- would regress the strongest part of the current design
- increases silent merge risk
- makes multilingual correctness harder to defend

### 19.4 Rejected: jump directly to multi-hop graph retrieval

Reason:

- complexity spike
- harder scoring semantics
- insufficient evidence that one-hop design is saturated

## 20. Acceptance Criteria

This design is considered successfully implemented when all of the following are true.

### 20.1 Semantic model

- persisted item relations expose explicit normalized subtype or evidence fields
- temporal and causal core semantics are no longer hidden only in metadata
- planners and write plans operate on family-specific typed relation models instead of one generic core relation record

### 20.2 Pipeline quality

- graph materialization has explicit precompute, commit, and post-commit boundaries
- planners no longer perform source-of-truth persistence directly
- source-of-truth graph persistence is no longer treated as a best-effort post-item stage
- an extraction batch is not considered successfully committed until both item records and source-of-truth graph facts are durable
- non-committed extraction batches are not externally visible
- failed source-of-truth batches transition into an explicit repairable state instead of remaining partially visible

### 20.3 Store quality

- the primary core-facing graph persistence entrypoint applies a full graph write plan
- SQL-backed graph operations use bulk-oriented persistence in graph hot paths
- SQL-backed cooccurrence rebuild is store-native

### 20.4 Retrieval quality

- retrieval graph supports both assist and expand mode
- graph family failures degrade independently where possible
- fusion is deterministic, duplicate-free, and preserves direct-channel primacy with configurable top-K protection

### 20.5 Open-source maintainability

- graph responsibilities are easier to locate by package and file purpose
- tests align to semantic boundaries instead of hidden side effects
- diagnostics clearly show what succeeded, degraded, or failed

## 21. Final Recommendation

`memind` should not become a copy of retrieval-first memory graph systems. Its current advantage is a stronger semantic and entity model. The correct next step is to preserve that advantage and raise execution maturity around it.

In short:

- keep the current entity and normalization philosophy
- harden relation semantics
- introduce explicit graph write plans
- split graph execution into three phases
- strengthen store contracts and SQL execution
- upgrade retrieval graph to dual-mode bounded usage

This path produces a better long-term open-source item graph than either a narrow performance-only patch set or a full retrieval-first redesign.
