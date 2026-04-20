# Memind Unified Thread Core Design

**Date:** 2026-04-20
**Status:** Proposed
**Scope:** `memind-core` unified thread object model, persistence contract, intake and materialization pipeline, query contract, lifecycle semantics, and migration from the current legacy memory-thread implementation

## 1. Context

`memind` already has a meaningful multi-layer memory foundation:

- `item` persists the raw memory unit and extraction result
- `graph` captures typed structural relations between items, entities, and evidence
- `insight` is evolving toward higher-level understanding and synthesis

The current `memory thread` implementation sits awkwardly between those layers. It is useful as a bounded retrieval assist, but it is not yet a true persistent object-tracking subsystem. It behaves more like related-item grouping than like:

- a GitHub issue
- a case file
- a project thread
- a relationship thread
- a topic thread

The target direction is to make thread a first-class memory object that tracks the evolution of a durable thing over time.

This design defines that thread core.

## 2. Problem Statement

The current thread implementation is directionally useful but structurally too limited for the intended product.

### 2.1 Current thread is retrieval-first, not object-first

Today thread is mainly a derivation and assist layer around stored items. It helps retrieval admit related members from a selected seed thread, but it does not model an object with identity, state, timeline, or durable lifecycle semantics.

### 2.2 Current thread is too dependent on item grouping semantics

The current design is close to "items that belong together". That is not enough for long-lived memory use cases where the system must answer:

- what is this thing
- what is its current state
- what happened to it over time
- what decisions, blockers, and open questions define it now

### 2.3 Current persistence model is too narrow

The current storage contract is effectively:

- thread row
- thread membership row

That cannot express append-only thread events, structured current snapshot, source evidence for thread changes, or robust rebuild semantics.

### 2.4 Current thread invariants are too restrictive

The current implementation enforces single-thread-per-item. That is too weak for real object tracking. A single memory item may legitimately contribute to multiple durable objects:

- a project thread and an issue thread
- a relationship thread and a topic thread
- a case thread and a work thread

### 2.5 Current lifecycle semantics are underdeveloped

The existing design hints at thread status but does not yet operate as a true lifecycle-aware state machine with reopen, dormant, resolution, and current snapshot reduction.

## 3. Goals

This design has seven primary goals.

### 3.1 Make thread a first-class memory object

Thread must no longer be defined as a retrieval-side grouping artifact. It must become a durable object abstraction with explicit identity and evolution.

### 3.2 Model "evolution record + current snapshot"

Every formal thread must support both:

- append-only evolution history
- a current reduced state for fast consumption

### 3.3 Keep thread separate from graph and insight

Thread must enrich the memory stack without collapsing into either:

- graph storage and relation reasoning
- insight synthesis and abstraction

### 3.4 Support broad object coverage with one core

The design must cover the essential durable-object scenarios with one high-quality framework rather than multiple semi-duplicated systems.

### 3.5 Preserve operational reliability

Thread materialization must be durable, replayable, rebuildable, and not dependent on fragile one-shot LLM decisions.

### 3.6 Enable later consumption without coupling it into v1

The core must be designed so retrieval, context assembly, summary generation, and insight can consume it later, but v1 does not need to fully wire those consumers yet.

### 3.7 Favor long-term quality over compatibility shortcuts

This is a new subsystem direction. The design should optimize for correctness, clarity, and extensibility rather than preserving the current lightweight thread model.

## 4. Non-Goals

This design intentionally does not attempt to do the following.

### 4.1 No thread-as-clustering redesign

The new thread core is not a better similarity clusterer. It is an object-tracking framework.

### 4.2 No fully separate per-scenario object systems

V1 will not create independent storage and pipelines for issue threads, project threads, relationship threads, and topic threads.

### 4.3 No deep retrieval/context integration in v1

This spec defines the core object model and query contract. Full retrieval/context consumption is a later integration phase.

### 4.4 No durable formal latent-thread object subsystem in v1

Persistent `memory_thread_candidate` storage as a formal latent thread-object store is explicitly postponed. V1 can still compute anchor candidates and decide `attach / create / defer / ignore`, but it does not persist weak thread candidates as first-class thread objects.

V1 may persist lightweight expiring `thread_accumulation_hint` records so slow-forming `TOPIC` and `RELATIONSHIP` objects can accumulate bounded discovery cues across multiple weak items. These hints are advisory infrastructure, not formal latent thread objects, and never become correctness dependencies.

V1 may persist lightweight deferred-intake revisit state for ambiguity handling. That revisit state is infrastructure, not a latent formal thread object.

### 4.5 No LLM-only write-path decisions

Thread creation and attachment must not depend on opaque end-to-end model output. LLM assistance may help normalization or labeling later, but core decisions must remain evidence-driven and deterministic.

## 5. Design Principles

### 5.1 Thread tracks durable objects, not just related text

A formal thread exists only when the system can reasonably say there is a persistent object worth tracking across time.

### 5.2 Identity, timeline, and snapshot are all required

If a structure has only identity, it is just an object card. If it has only history, it is just a log. If it has only current summary, it is just a cache. Thread requires all three.

### 5.3 Core is shared, specialization is layered

Broad thread types share the same core schema and lifecycle. Type-specific behavior lives in reducers, facets, and policies, not separate object systems.

### 5.4 Source facts vs thread projection

`item` and `graph` remain the global source-of-truth fact layers.

Within thread core, events are the authoritative current projection and snapshot is a reduction over that projection.

If source facts are deleted, revised, invalidated, or re-extracted, thread projection may be rebuilt into a new projection generation. Correctness must never depend on in-place mutation of previously materialized snapshot state.

### 5.5 Membership is many-to-many

Items may contribute to multiple threads. Primary membership, when present, is thread-local emphasis rather than a memory-global exclusivity rule.

### 5.6 Write path favors durable facts first, thread second

Items and graph facts commit first. Thread materialization is then driven through a durable outbox so failures can be retried without losing source information.

### 5.7 Broad coverage wins over premature detail

V1 should support the necessary durable-object scenarios with a small number of broad types and a high-quality shared core.

## 6. Position in the Memory Stack

The memory stack responsibilities are fixed as follows.

### 6.1 Graph provides structure

Graph answers questions such as:

- what entities or items are related
- what type of relation exists
- what evidence connects them

Graph is the structural evidence layer.

### 6.2 Thread provides tracking

Thread answers questions such as:

- what durable object this is
- what state it is in now
- how it evolved over time
- which items and evidence matter to it

Thread is the persistent object-tracking layer.

### 6.3 Insight provides understanding

Insight answers questions such as:

- what this object means
- what pattern or conclusion matters
- what higher-level strategy or takeaway follows

Insight is the abstraction and synthesis layer.

### 6.4 Boundary rule

Some information may appear in more than one layer, but responsibility must not overlap:

- graph does not own current object state
- thread does not own global relation reasoning
- insight does not own lifecycle storage

## 7. Candidate Approaches Considered

Three approaches were considered.

### 7.1 Lightweight retrieval-assist thread

This is close to the current implementation.

Pros:

- lowest implementation cost
- useful bounded recall assist

Cons:

- remains item-grouping-first
- cannot express durable object evolution
- not suitable for issue/case/project/relationship tracking

### 7.2 Unified object-type thread core

One shared thread core models durable objects with type, anchor, timeline, snapshot, members, and typed facets.

Pros:

- aligns with the target product shape
- unifies broad durable-object scenarios
- cleanly composes with graph and insight
- supports future retrieval/context consumption without redesign

Cons:

- more substantial redesign
- requires stronger persistence and write-path contracts

### 7.3 Separate per-scenario thread systems

Dedicated systems for issue, project, relationship, and topic tracking.

Pros:

- scenario-specific behavior can be very expressive

Cons:

- duplicated pipelines
- blurry boundaries
- higher long-term maintenance cost
- much harder to keep semantics consistent

### 7.4 Recommendation

This design adopts approach 7.2.

The unified object-type thread core is the strongest long-term architecture and best matches the product goal.

## 8. Thread Definition

The canonical definition is:

`thread = 围绕一个持续对象的演化记录 + 当前快照`

In English:

`thread = an evolution record plus current snapshot around a durable object`

It is not:

- a similarity cluster
- a bag of related items
- a graph neighborhood alias
- a retrieval-only expansion structure

## 9. Core Object Model

Every formal thread consists of eight required parts plus optional typed facets.

### 9.1 Identity

Identity answers what object the thread is about.

Required fields:

- `threadId`
- `threadKey`
- `threadType`
- `anchorKind`
- `anchorKey`
- `displayLabel`
- `normalizedAliases[]`
- `anchorConfidence`

Design decisions:

- `threadId` is the internal durable numeric identifier.
- `threadKey` is the immutable public identifier. It is synthetic and stable, not derived from a mutable anchor string.
- `threadType` is the current broad classification of the durable object, not an immutable public identity token.
- `anchorKind + anchorKey` expresses the canonical object anchor the thread tracks.
- `displayLabel` is the current best human-facing label.
- `normalizedAliases[]` stores stable alternate labels for matching and display.
- `anchorConfidence` expresses how strongly the system believes the anchor is canonical.
- `threadKey` remains stable even if anchor normalization or display labeling later improves.
- `threadKey` is minted exactly once when a formal thread row is first created; rebuild must preserve that existing key whenever rebuilt object continuity maps back to the same thread identity.
- `threadType` may change only through a projection-changing `RETYPE` repair or rebuild cutover that preserves the same `threadId` and `threadKey` while correcting the broad classification.
- rebuild is projection repair over persistent thread identities, not a license to resynthesize public thread identifiers from scratch inside a non-empty memory.

### 9.2 Lifecycle and Lineage

Lifecycle answers whether the thread is actively tracked and whether it remains canonical.

Required fields:

- `lifecycleStatus`
- `openedAt`
- `lastEventAt`
- `lastMeaningfulUpdateAt`
- `closedAt`
- `closureReason`
- `parentThreadId`
- `supersededByThreadId`

Canonical lifecycle states:

- `ACTIVE`
- `DORMANT`
- `CLOSED`

Lifecycle rules:

- new formal threads start as `ACTIVE`
- lack of meaningful updates may transition `ACTIVE -> DORMANT`
- resolution or explicit closure may transition `ACTIVE|DORMANT -> CLOSED`
- rebuild may transition any prior lifecycle state to `CLOSED` with `closureReason = INVALIDATED` when authoritative facts no longer support a surviving current object for that thread
- a qualifying new update may transition `DORMANT|CLOSED -> ACTIVE` and append `REOPENED` only if the thread was not closed by lineage-replacing repair
- only threads closed with `closureReason = RESOLVED | EXPLICIT` may reopen in place
- threads closed with `closureReason = INVALIDATED` are not reopened in place; a future object with the same anchor may create a new thread after the old active anchor binding is retired
- threads closed with `closureReason = MERGED | SPLIT` are lineage-terminal and must not be reopened in place
- a superseded thread is never reopened in place; callers must follow lineage to the surviving or child thread set

Canonical closure reasons:

- `RESOLVED`
- `EXPLICIT`
- `INVALIDATED`
- `MERGED`
- `SPLIT`

### 9.3 Object State

Object state answers the coarse current condition of the tracked object itself.

Canonical cross-type states:

- `UNCERTAIN`
- `ONGOING`
- `BLOCKED`
- `STABLE`
- `RESOLVED`

Rules:

- this is a cross-type coarse state for filtering and shared reasoning
- type-specific nuance lives in snapshot facets, not in this top-level enum

### 9.4 Projection Head

Projection head answers which materialized thread generation is current and how far ordered intake has been applied.

Required fields:

- `projectionGeneration`
- `projectionStatus`
- `lastAppliedIntakeSeq`
- `generationCutoverJobId`

Canonical projection statuses:

- `HEALTHY`
- `STALE_UNSAFE`
- `REBUILDING`
- `FAILED`

Rules:

- only one projection generation is current for query and retrieval purposes
- additive updates may append within the current generation
- every projection generation must also have one immutable generation-header record that freezes that generation's type, lifecycle, anchor/display cache, lineage, reducer policy, and cutover metadata
- source invalidation or source-fact repair may mark the thread `STALE_UNSAFE` and require rebuild into the next generation
- rebuild and projection-changing explicit repair cutover atomically advance `projectionGeneration`
- any generation cutover transaction must also advance `lastAppliedIntakeSeq` to the ordered intake prefix incorporated by that cutover
- `STALE_UNSAFE` means the last materialized snapshot is no longer safe to serve as current truth
- when `projectionStatus = STALE_UNSAFE`, root-row lifecycle, object-state, and recency timestamps are last-known caches, not authoritative current state
- `REBUILDING` means replacement projection work is in progress under rebuild control; default serving must follow degraded non-authoritative semantics until cutover completes
- `FAILED` means the thread could not be materialized into a safe current projection and requires operator repair or successful rebuild before default current serving resumes
- `lastAppliedIntakeSeq` on the root row is per-thread projection progress, not the memory-global exactly-once checkpoint
- `generationCutoverJobId` identifies only the job that most recently cut over the current `projectionGeneration`
- incremental additive updates within the same generation must not overwrite `generationCutoverJobId`; their idempotency comes from ordered outbox finalization plus event and membership dedup rules rather than generation-cutover metadata

### 9.5 Generation Header

Generation header answers what exactly was true for one materialized projection generation.

Properties:

- immutable per `(threadId, projectionGeneration)`
- stores generation-scoped type, lifecycle, anchor/display cache, lineage, reducer policy, and cutover metadata
- is the authoritative contract for historical audit and historical snapshot reconstruction
- current root-row caches are mirrors of the active generation header, not substitutes for historical generation records

### 9.6 Timeline

Timeline is the append-only sequence of normalized thread events.

Properties:

- append-only within one projection generation
- ordered by per-thread sequence and effective event time
- derived from items and graph evidence, but not equal to them
- durable authoritative history for the current thread projection

### 9.7 Members

Members are the items and other evidence-bearing objects that support the thread.

Properties:

- item membership is many-to-many
- primary membership is thread-local emphasis, not a memory-global exclusivity truth
- membership does not replace event source links; event source links explain why a specific event exists

### 9.8 Snapshot

Snapshot is the current reduced view of the thread.

Properties:

- rebuilt deterministically from thread events plus memberships
- structured first, textual second
- optimized for fast query and future consumption

### 9.9 Facets

Facets are typed extensions attached to the shared core snapshot.

Rules:

- core fields exist for every thread
- facets are optional and type-sensitive
- facets must not replace the shared thread contract

## 10. Broad Thread Types

V1 uses four broad thread types.

### 10.1 `WORK`

Tracks ongoing work-like objects:

- project
- initiative
- workstream
- task bundle

### 10.2 `CASE`

Tracks issue-like objects:

- bug
- incident
- support case
- problem investigation

### 10.3 `RELATIONSHIP`

Tracks ongoing relational objects:

- person-person relationship
- person-organization relationship
- person-project relationship

### 10.4 `TOPIC`

Tracks ongoing thematic objects:

- subject of interest
- long-running discussion topic
- learning topic
- research theme

### 10.5 Type strategy

These types are intentionally broad. V1 does not create narrower hard-coded object systems. Finer semantics are expressed through:

- anchor kind
- snapshot facets
- reducer policy
- query filtering

### 10.6 Type evolution

The broad type may be corrected over time when the same durable object is better understood.

Rules:

- `threadType` correction is a first-class projection-changing repair called `RETYPE`
- `RETYPE` preserves `threadId`, `threadKey`, and object continuity
- `RETYPE` must not be used to hide a merge, split, or anchor-identity mistake
- `RETYPE` must cut over a new projection generation with the corrected `threadType` and reducer policy
- `RETYPE` must append exactly one `THREAD_RETYPED` event naming previous and new type

## 11. Anchor Model

Formal threads are anchored to a durable object reference.

### 11.1 Anchor fields

The canonical anchor contract is:

- `anchorKind`
- `anchorKey`
- `displayLabel`
- `normalizedAliases[]`
- `anchorConfidence`

### 11.2 Anchor semantics

`anchorKind` describes the class of durable object anchor, for example:

- `project`
- `issue`
- `case`
- `topic`
- `person_pair`
- `person_org`

`anchorKey` is the canonical normalized identity for that object within a memory.

Examples:

- a stable project slug
- a canonical issue key
- a normalized topic key
- a canonical relationship pair key

### 11.3 Uniqueness invariant

Within one `memoryId`, there must be at most one active canonical anchor binding for a given `anchorKind + anchorKey`.

Implications:

- the system should reopen the existing thread rather than create a duplicate when new evidence arrives
- anchor normalization quality matters because anchor identity drives thread continuity
- superseded threads or split parents must not continue to hold the active canonical binding
- implementation may enforce this through a partial unique index or an equivalent active-binding mechanism

### 11.4 Anchor is object-level, not entity-level

Anchor does not mean "the most related entity". It means "the durable object this thread is about".

Examples:

- a bug case thread may be anchored by issue identity, not just a service name
- a relationship thread may be anchored by a canonical pair, not by one person entity alone
- a topic thread may be anchored by the normalized topic, not by the latest mentioning item

### 11.5 Anchor evolution and repair

Anchor quality is expected to improve over time. V1 must support the following first-class anchor and lineage repair operations.

All first-class repairs are projection-changing thread mutations.

Rules:

- explicit repair must not mutate thread-core rows through an unordered side path
- every explicit repair must execute either through a derived ordered `thread_intake_outbox` repair trigger with a fresh `intakeSeq`, or as rebuild cutover under the same memory-scoped lease and fencing model
- projection-changing explicit repair may cut over the next safe current projection directly; it does not require an intermediate `STALE_UNSAFE` period unless the system must fall back to rebuild
- every impacted thread must cut over a new `projectionGeneration` in the same atomic repair transaction that updates lifecycle, bindings, memberships, events, and snapshot state

`RETYPE`

- same durable object, better broad classification
- same `threadId`
- same stable `threadKey`
- must cut over a new projection generation for the impacted thread
- must update the generation header to the corrected `threadType`, `reducerPolicyId`, and `reducerVersion`
- must fully recompute snapshot under the corrected reducer policy in the same cutover transaction
- must preserve active canonical anchor binding unless the repair plan also includes an explicit `ANCHOR_REKEY` in that same generation cutover
- must preserve lineage fields unless the repair plan also includes an explicit lineage-changing repair
- must append exactly one `THREAD_RETYPED` event in the repaired thread generation
- must not be used when authoritative evidence indicates the old and new interpretations are different durable objects; those cases require `MERGE`, `SPLIT`, or fresh object creation instead

`ANCHOR_REKEY`

- same durable object, better canonical anchor
- same `threadId`
- same stable `threadKey`
- must cut over a new projection generation for the impacted thread
- old active anchor bindings for the thread must become `REDIRECTED` to that same thread atomically
- append exactly one `ANCHOR_REKEYED` event in the repaired thread generation
- update active canonical anchor binding atomically

`MERGE`

- two or more threads are determined to represent the same durable object
- one survivor thread remains canonical
- survivor keeps its existing `threadId` and `threadKey`
- survivor must cut over a new projection generation that reflects the absorbed current state
- survivor merge generation must append exactly one `MERGE_ABSORBED` event naming the absorbed losing thread ids
- losing thread transitions to `CLOSED` with `closureReason = MERGED`
- losing thread sets `supersededByThreadId`
- each losing thread must cut over a terminal projection generation
- every active canonical anchor binding on each losing thread must become `REDIRECTED` to the survivor thread atomically
- each losing thread terminal generation must append exactly one `MERGED_INTO` event naming the survivor thread
- losing thread must not retain active canonical bindings or `ACTIVE` current-membership rows after the repair transaction commits
- survivor absorbs members through migration or generation rebuild

`SPLIT`

- one thread is determined to contain multiple durable objects
- parent thread transitions to `CLOSED` with `closureReason = SPLIT`
- parent keeps its existing `threadId` and `threadKey` as historical lineage identity
- parent must cut over a terminal projection generation
- child threads are created with `parentThreadId`
- each child receives a newly minted `threadId` and `threadKey`
- each child must cut over an opening projection generation
- each child opening generation must append exactly one `SPLIT_FROM` event naming the parent thread
- parent must not retain `ACTIVE` current-membership rows after the split cutover commits
- parent anchor bindings become `RETIRED`, not `REDIRECTED`, unless a later one-to-one repair proves that one retired anchor now has a single canonical successor
- split is performed as a rebuild or explicit repair operation, not as opportunistic incremental mutation

### 11.6 Anchor binding history

V1 must preserve anchor binding history rather than storing only the latest active anchor on the thread root row.

Rules:

- every canonical anchor is represented as an explicit binding record
- old anchors are retained as historical bindings after `ANCHOR_REKEY`
- old active anchors from `ANCHOR_REKEY` must be retained as `REDIRECTED` bindings to the same canonical thread
- active canonical bindings from a merged losing thread must be retained as `REDIRECTED` bindings to the canonical survivor thread
- historical bindings may redirect to the current canonical thread only when there is exactly one canonical successor
- `resolveThreadByAnchor` must resolve both active bindings and redirected historical bindings
- `REDIRECTED` is valid only for one-to-one canonical successor resolution such as rekey or merge
- split-origin anchors that no longer map to one canonical child must remain `RETIRED`; `resolveThreadByAnchor` must not silently choose one child
- `normalizedAliases[]` are display and match aliases, not a substitute for anchor binding history

## 12. Persistence Model

V1 persists eight required thread-core objects.

In addition, the design requires three non-core infrastructure objects:

- `thread_intake_outbox`
- `thread_intake_revisit`
- `thread_materialization_state`

These infrastructure objects are required for reliability and ambiguity management, but they are not part of the formal thread object model itself.

### 12.1 `memory_thread`

This is the thread root row.

Required responsibilities:

- identity and type
- hold current active-generation cache fields
- lifecycle status
- coarse object state
- core timestamps
- versioning and reducer metadata

Recommended fields:

- `biz_id`
- `memory_id`
- `thread_key`
- `thread_type`
- `anchor_kind`
- `anchor_key`
- `display_label`
- `normalized_aliases_json`
- `anchor_confidence`
- `lifecycle_status`
- `object_state`
- `opened_at`
- `last_event_at`
- `last_meaningful_update_at`
- `closed_at`
- `closure_reason`
- `parent_thread_id`
- `superseded_by_thread_id`
- `projection_generation`
- `projection_status`
- `last_applied_intake_seq`
- `generation_cutover_job_id`
- `snapshot_version`
- `created_at`
- `updated_at`

Invariants:

- unique `(memory_id, thread_key)`
- `thread_type`, `anchor_kind`, `anchor_key`, and cached label/alias fields on the root row are current-generation cache fields: if the thread currently has an active healthy generation they must mirror that generation header; otherwise they retain last-known cache values only
- canonical anchor resolution and uniqueness must always consult `memory_thread_anchor_binding`; root-row anchor caches are not themselves authoritative binding state
- `superseded_by_thread_id` threads are never canonical
- `lifecycle_status`, `object_state`, `last_event_at`, `last_meaningful_update_at`, `closed_at`, `closure_reason`, and `snapshot_version` on the root row are projection-derived cache fields
- if `projection_status = STALE_UNSAFE | REBUILDING | FAILED`, projection-derived cache fields and cached anchor/display fields on the root row must not be served under their normal current-state names as authoritative truth
- historical audit must use `memory_thread_generation` for generation-scoped type, lifecycle, object-state, lineage, reducer-policy, snapshot-version, and cutover semantics rather than consulting mutable root-row caches
- `generation_cutover_job_id` must identify the job that last cut over the current projection generation; replay of the same cutover job must not advance the same thread to a second new generation
- incremental writes inside one projection generation must not rewrite `generation_cutover_job_id`

### 12.2 `memory_thread_generation`

This stores immutable per-generation projection headers.

Required responsibilities:

- freeze generation-scoped thread semantics for audit
- preserve the exact reducer contract used by that generation
- provide stable cutover metadata for historical queries and rebuild explainability

Recommended fields:

- `biz_id`
- `memory_id`
- `thread_id`
- `projection_generation`
- `thread_type`
- `anchor_kind`
- `anchor_key`
- `display_label`
- `normalized_aliases_json`
- `anchor_confidence`
- `lifecycle_status`
- `object_state`
- `opened_at`
- `last_event_at`
- `last_meaningful_update_at`
- `closed_at`
- `closure_reason`
- `parent_thread_id`
- `superseded_by_thread_id`
- `reducer_policy_id`
- `reducer_version`
- `snapshot_version`
- `cutover_kind`
- `cutover_trigger_type`
- `cutover_job_id`
- `cutover_intake_seq`
- `created_at`

Canonical cutover kinds:

- `CREATION`
- `EXPLICIT_REPAIR`
- `REBUILD`
- `INVALIDATION_CLOSURE`

Invariants:

- unique `(memory_id, thread_id, projection_generation)`
- exactly one generation header exists for every materialized projection generation
- current root-row cache fields must mirror the active generation header when one active healthy generation exists
- historical audit and historical snapshot reconstruction must use the requested generation header rather than mutable current root-row caches or latest reducer defaults
- if one preserved thread identity changes broad type, the next generation header must carry the new `thread_type` and reducer policy while retaining the same `thread_id` and `thread_key`
- non-current generation headers are audit records and follow the configured history retention contract

### 12.3 `memory_thread_anchor_binding`

This stores canonical anchor bindings and redirected anchor history.

Required responsibilities:

- enforce active canonical anchor uniqueness
- preserve old anchor bindings after rekey
- resolve historical anchors to the current canonical thread

Recommended fields:

- `biz_id`
- `memory_id`
- `thread_id`
- `anchor_kind`
- `anchor_key`
- `binding_status`
- `redirect_thread_id`
- `bound_at`
- `unbound_at`
- `created_at`
- `updated_at`

Canonical binding statuses:

- `ACTIVE`
- `RETIRED`
- `REDIRECTED`

Invariants:

- unique active `(memory_id, anchor_kind, anchor_key)` where `binding_status = ACTIVE`
- redirected bindings must point at the canonical destination thread through `redirect_thread_id`
- retired bindings that do not have one canonical successor must leave `redirect_thread_id` null and rely on thread lineage for follow-up navigation
- threads closed by `MERGED | SPLIT | INVALIDATED`, and any thread carrying `superseded_by_thread_id`, must not retain `ACTIVE` canonical anchor bindings
- threads closed as `INVALIDATED` must not retain active canonical anchor bindings; their previously active bindings must be retired unless an explicit repair redirects them elsewhere

### 12.4 `memory_thread_membership`

This stores immutable per-generation membership history.

Required responsibilities:

- item-to-thread membership history by projection generation
- reducer input and audit history
- generation-level explanation of object context

Recommended fields:

- `biz_id`
- `memory_id`
- `thread_id`
- `projection_generation`
- `item_id`
- `membership_role`
- `is_primary`
- `relevance_weight`
- `contribution_tags_json`
- `created_at`

Canonical membership roles:

- `SUPPORTING`
- `REFERENCE`
- `CONTRADICTING`

Invariants:

- unique `(memory_id, thread_id, projection_generation, item_id)`
- `is_primary = true` may be set only when `membership_role = SUPPORTING`
- at most one primary membership row may exist per `(memory_id, thread_id, projection_generation)`

### 12.5 `memory_thread_current_membership`

This stores the current serveable membership projection.

Required responsibilities:

- power default current-only member reads
- carry current thread-local primary emphasis when present
- separate current serving state from historical generation rows

Recommended fields:

- `biz_id`
- `memory_id`
- `thread_id`
- `projection_generation`
- `item_id`
- `membership_role`
- `is_primary`
- `relevance_weight`
- `contribution_tags_json`
- `visibility_status`
- `created_at`
- `updated_at`

Canonical visibility statuses:

- `ACTIVE`
- `SUPPRESSED`

Invariants:

- unique `(memory_id, thread_id, item_id)`
- `is_primary` is thread-local emphasis, not a memory-global uniqueness contract
- `is_primary = true` may be set only when `membership_role = SUPPORTING`
- at most one active primary membership row may exist per `(memory_id, thread_id)` where `visibility_status = ACTIVE`
- current-membership rows for a thread are atomically replaced or suppressed at projection cutover; they are not incrementally patched as historical truth
- when a thread enters `STALE_UNSAFE | REBUILDING | FAILED`, every `memory_thread_current_membership` row for that thread must be atomically moved to `SUPPRESSED`
- threads closed by `MERGED | SPLIT | INVALIDATED`, and any thread carrying `superseded_by_thread_id`, must not retain `ACTIVE` current-membership rows
- only threads that remain canonical current objects may retain `ACTIVE` current-membership rows in default serving
- degraded threads must never retain `ACTIVE` current-membership rows after the degrading transaction commits

### 12.6 `memory_thread_event`

This stores append-only normalized thread events.

Required responsibilities:

- durable timeline
- event typing
- reducer input
- event-level confidence and meaning

Recommended fields:

- `biz_id`
- `memory_id`
- `thread_id`
- `projection_generation`
- `event_seq`
- `normalized_event_key`
- `event_time`
- `event_type`
- `event_semantic_slot`
- `event_payload_json`
- `payload_schema_version`
- `normalization_version`
- `is_meaningful`
- `confidence`
- `created_at`

Invariants:

- unique `(memory_id, thread_id, projection_generation, event_seq)`
- unique `(memory_id, thread_id, projection_generation, normalized_event_key)`
- append-only semantics within one projection generation
- `normalized_event_key` must be derived deterministically from the normalized `event_type`, stable `event_semantic_slot`, and a stable origin-revision fingerprint
- the origin-revision fingerprint must be computed from the minimal sorted set of source tuples `(source_type, source_biz_id, source_revision, source_role)` whose loss would cause the event not to exist; support-only or contradiction-only evidence that merely enriches interpretation must not change the fingerprint
- `normalized_event_key` must not depend on `intakeSeq`, `event_seq`, timestamps, job ids, confidence, or other execution-local metadata

### 12.7 `memory_thread_event_source`

This maps thread events to their source evidence.

Required responsibilities:

- explain why the event exists
- preserve event-to-source traceability
- support later audit, debugging, and trust controls

Recommended fields:

- `biz_id`
- `memory_id`
- `thread_event_id`
- `source_type`
- `source_biz_id`
- `source_revision`
- `source_role`
- `confidence`
- `created_at`

Canonical source types:

- `ITEM`
- `GRAPH_NODE`
- `GRAPH_EDGE`
- `OBSERVATION`
- `THREAD`

Canonical source roles:

- `TRIGGER`
- `SUPPORT`
- `ANCHOR_EVIDENCE`
- `CONTRADICTION`

Invariants:

- unique `(thread_event_id, source_type, source_biz_id, source_revision, source_role)`

### 12.8 `memory_thread_snapshot`

This stores the current reduced thread state.

Required responsibilities:

- serve fast thread reads
- store structured current state
- store optional canonical text for later consumers

Recommended fields:

- `thread_id`
- `memory_id`
- `projection_generation`
- `snapshot_version`
- `payload_schema_version`
- `reducer_policy_id`
- `reducer_version`
- `reduced_from_event_seq`
- `snapshot_payload_json`
- `snapshot_text`
- `updated_at`

Invariants:

- exactly one current snapshot row per thread
- snapshot version must match or trail the current thread reducer version
- snapshot reducer policy and reducer version must mirror the active generation header
- snapshot must identify which `projection_generation` it represents
- persisted snapshot rows are required only for the active current projection; historical snapshot audit must not depend on storing one snapshot row per historical generation

### 12.9 `thread_materialization_state`

This stores memory-scoped materialization fencing, ordered-intake checkpoint state, and blocked-memory safety state.

Required responsibilities:

- hold the canonical per-memory ordered-intake checkpoint
- fence the active materializer lease
- coordinate rebuild prefix cutover
- expose explicit memory-scope blocked state when a poison intake cannot be safely finalized
- allow `IGNORE`, `COMPLETED_DEFERRED`, terminal-failure, and quarantine finalization even when no thread root row changes

Recommended fields:

- `memory_id`
- `materialization_status`
- `last_finalized_intake_seq`
- `lease_token`
- `lease_expires_at`
- `active_rebuild_job_id`
- `active_rebuild_cutover_seq`
- `active_rebuild_started_at`
- `blocked_intake_seq`
- `blocked_trigger_biz_id`
- `blocked_trigger_revision`
- `blocked_reason`
- `blocked_at`
- `updated_at`

Canonical materialization statuses:

- `HEALTHY`
- `BLOCKED_QUARANTINED`

Invariants:

- unique `memory_id`
- `last_finalized_intake_seq` is monotonic per memory
- only the active lease holder may advance `last_finalized_intake_seq` or finalize a rebuild cutover prefix
- the same `active_rebuild_job_id` must be reused when resuming an interrupted rebuild for the same cutover prefix
- if `materialization_status = BLOCKED_QUARANTINED`, no later `intakeSeq` for that memory may finalize until operator repair or rebuild clears the block
- memory-scope quarantine has higher precedence than per-thread healthy caches; default current-serving queries for that memory must fail closed or degrade to explicit memory-blocked metadata
- `memory_thread.last_applied_intake_seq` may trail or differ across threads and must never be used as the memory-global exactly-once fence

### 12.10 `thread_accumulation_hint`

This stores lightweight, expiring weak-signal accumulation state for object patterns that are not yet strong enough to justify a formal thread.

Required responsibilities:

- accumulate bounded multi-item support for slow-forming `TOPIC` and `RELATIONSHIP` candidates
- accelerate later weak-signal candidate discovery or bounded authoritative reconsideration without creating latent thread objects
- remain safely discardable and rebuildable because it is advisory infrastructure, not correctness-critical state

Recommended fields:

- `memory_id`
- `hint_kind`
- `hint_key`
- `candidate_thread_type`
- `support_count`
- `support_weight`
- `sample_sources_json`
- `first_seen_at`
- `last_seen_at`
- `expires_at`
- `updated_at`

Invariants:

- unique `(memory_id, hint_kind, hint_key, candidate_thread_type)`
- hints must never reserve anchors, appear in query APIs, or participate in canonical thread uniqueness
- hint expiry or loss must not change correctness; at worst it delays weak-signal thread creation until later evidence or full rebuild
- rebuild may recompute, refresh, or ignore hints, but authoritative thread existence must never depend on them
- hint updates are advisory and are not part of the thread-core exactly-once correctness boundary; they may be repaired or recomputed asynchronously
- hints may narrow which historical evidence slice to reconsider, but they must never contribute independent semantic evidence or raise a below-threshold object directly into `CREATE_NEW`
- any hint-assisted `ATTACH_EXISTING`, `REOPEN_EXISTING`, `CREATE_NEW`, or `DEFER_AMBIGUOUS` outcome must still be justified entirely from authoritative items, graph facts, and deterministic rules available without the hint

## 13. Objects Explicitly Deferred

The following objects are intentionally deferred beyond v1:

- `memory_thread_link`
- `memory_thread_candidate`
- `memory_thread_watch`

Reasoning:

- `thread_link` is valuable, but thread-to-thread semantics can remain out of core scope initially
- `thread_candidate` as a formal latent thread object remains deferred; v1 uses only lightweight expiring `thread_accumulation_hint` infrastructure for weak accumulation
- `thread_watch` belongs to subscription and proactive workflows, not core object tracking

## 14. Intake and Materialization Pipeline

V1 uses synchronous source-fact commit plus asynchronous thread materialization.

### 14.1 Synchronous path

The main write path persists:

- raw input and normalized item data
- extraction result
- graph facts
- `thread_intake_outbox` envelope

The synchronous path does not directly create or mutate formal thread state.

The synchronous path must not persist semantic thread decisions or normalized thread-intake signals. Those are computed asynchronously from committed source facts.

Source-fact commit rules:

- item, extraction, graph, and `thread_intake_outbox` must be committed in one atomic logical transaction boundary
- an outbox envelope must never become durable if the source facts it references are not committed
- committed source facts that should affect thread must never be durable without a corresponding outbox envelope
- if one source-fact subsystem cannot participate in the same transaction boundary, it is not eligible to trigger thread materialization in v1

### 14.2 Durable outbox and revisit buffer

Every item commit that could affect threads must persist a durable outbox record containing at least:

- `memoryId`
- `triggerType`
- `triggerBizId`
- `triggerRevision`
- `intakeSeq`
- `status`
- `attemptCount`
- enqueue time

`triggerType` must distinguish at least:

- source-derived item intake
- derived explicit repair intake

When `triggerType` is source-derived item intake:

- `triggerBizId` and `triggerRevision` identify the committed source item revision that entered the thread pipeline

When `triggerType` is derived explicit repair intake:

- `triggerBizId` and `triggerRevision` identify one deterministic repair directive revision, not an item revision
- the outbox record must also persist `repairOperation`
- the outbox record must also persist `repairDirectiveKey`
- the outbox record must also persist `repairPayloadJson`
- `repairPayloadJson` must name the impacted thread ids and the canonical successor, anchor, or split-child targets needed to execute the repair deterministically without consulting any unordered side channel

Explicit repair operations that affect canonical bindings, lineage, or current projection must not write thread-core rows directly. They must either:

- enqueue a derived `thread_intake_outbox` repair trigger with a fresh `intakeSeq` under the same memory-scoped ordering model, or
- execute as rebuild cutover under the same memory-scoped rebuild lease

If the enclosing memory is already `BLOCKED_QUARANTINED`, an operator repair that is intended to clear that quarantine must not enqueue a later repair trigger behind the blocked checkpoint. It must instead update the already blocked outbox row at `blocked_intake_seq` with deterministic recovery directive metadata, including rewriting `triggerType` to the derived explicit repair form and filling `repairOperation`, `repairDirectiveKey`, and `repairPayloadJson` as needed, while preserving that row's original `intakeSeq` and resuming it under the same lease and fencing model.

No second unordered repair write path is allowed in v1.

Canonical outbox statuses:

- `PENDING`
- `COMPLETED`
- `COMPLETED_DEFERRED`
- `FAILED_RETRYABLE`
- `FAILED_TERMINAL`
- `QUARANTINED_BLOCKING`

Outbox is required so thread work is retryable and rebuildable.

It is infrastructure, not thread domain state:

- it must be persisted durably
- it must survive process restart
- it must be safe to replay
- it must not be treated as a formal thread object
- it must not store semantically normalized thread hints as durable write-path truth
- terminal finalization metadata must remain on the outbox row so operators can diagnose and replay safely

`FAILED_TERMINAL` eligibility rules:

- terminal failure must record `terminalFailureClass` and reason
- `terminalFailureClass = NOOP_CONFIRMED` means the intake would not mutate any thread projection
- `terminalFailureClass = BOUNDED_THREADS_DEGRADED` means all deterministically impacted existing threads are atomically marked `FAILED` before checkpoint advancement
- a derived explicit repair intake whose deterministic preconditions no longer hold must not silently retarget through fresh semantic search; it may finalize as `NOOP_CONFIRMED` only if the requested canonical end state is already true, otherwise it may use `BOUNDED_THREADS_DEGRADED` only when every impacted existing thread is explicitly named in `repairPayloadJson` and degraded atomically before checkpoint advancement
- `FAILED_TERMINAL` must not be used if the unresolved intake could create a new thread or mutate an unknown or unbounded thread set

`QUARANTINED_BLOCKING` rules:

- if retries are exhausted and an intake still cannot safely qualify for `FAILED_TERMINAL`, the system must atomically mark that outbox row `QUARANTINED_BLOCKING` and set `thread_materialization_state.materialization_status = BLOCKED_QUARANTINED`
- `QUARANTINED_BLOCKING` is operator-visible and must not be auto-retried in place
- `QUARANTINED_BLOCKING` does not advance `thread_materialization_state.last_finalized_intake_seq`
- while one memory is `BLOCKED_QUARANTINED`, later outbox entries for that memory must not finalize through the normal incremental path
- ordered operator recovery of a blocked intake must reuse the same `blocked_intake_seq`; it may attach or replace deterministic repair directive metadata on that blocked row and rewrite that row into the derived explicit repair form, but it must not change that row's ordered position or clear quarantine before finalization
- clearing a memory-scope quarantine requires operator repair or rebuild that reprocesses from the blocked intake or an earlier safe ordered prefix; the system must not clear quarantine by metadata flip alone
- quarantine recovery must atomically either finalize the blocked intake itself or cut over a rebuild prefix that fully subsumes it, and in the same transaction clear `materialization_status`, clear every `blocked_*` field, and restore default current-serving eligibility for that memory

When an intake is finalized as `COMPLETED_DEFERRED`, the system must also persist a `thread_intake_revisit` record containing at least:

- `memoryId`
- `triggerBizId`
- `triggerRevision`
- `intakeSeq`
- unresolved anchor or thread alternatives
- revisit reason
- created time

`thread_intake_revisit` rules:

- it is a lightweight ambiguity and revisit buffer, not a latent thread object
- it must not block ordered intake progression
- it must be created or updated atomically with the same finalization transaction that marks the outbox entry `COMPLETED_DEFERRED`
- later compatible evidence may schedule targeted reconsideration only by enqueuing a fresh derived `thread_intake_outbox` record with a new `intakeSeq`
- `thread_intake_revisit` itself must never mutate thread-core state and must never act as a second unordered write path
- full rebuild must not depend on revisit records for correctness; rebuild decisions must be reproducible from authoritative items, graph facts, and versioned deterministic rules alone
- rebuild or successful targeted reconsideration may discard revisit records after the ambiguity is resolved

### 14.3 Ordering and exclusivity model

V1 explicitly prioritizes correctness over maximum parallelism.

Rules:

- at most one active thread materializer lease may process one `memoryId` at a time
- the active lease and ordered checkpoint are fenced through `thread_materialization_state`
- outbox entries for one `memoryId` must be finalized in strictly increasing `intakeSeq`
- explicit repair triggers enqueued into `thread_intake_outbox` must obey the same `intakeSeq` ordering, lease ownership, retry, and finalization rules as item-derived intake
- rebuild work acquires the same memory-scoped lease as incremental work
- rebuild establishes a cutover `intakeSeq`; newer entries are applied only after the rebuild job finalizes that ordered prefix
- rebuild consistency is per-thread, not memory-wide snapshot consistent in v1
- rebuild may atomically cut over one thread at a time under the memory-scoped lease; different threads in the same memory may temporarily sit at different rebuilt generations during one rebuild job
- per-thread `event_seq` must be allocated monotonically inside the thread-core transaction using row lock or equivalent compare-and-swap protection
- `COMPLETED`, `COMPLETED_DEFERRED`, and `FAILED_TERMINAL` are finalized statuses and advance `thread_materialization_state.last_finalized_intake_seq`
- `FAILED_RETRYABLE` is not final, does not advance the ordered checkpoint, and blocks later intake finalization until it succeeds or is promoted to `FAILED_TERMINAL`
- `QUARANTINED_BLOCKING` is a non-checkpoint-advancing blocked state, not a retryable loop; it freezes later incremental finalization for that memory until quarantine is cleared
- if one memory is `BLOCKED_QUARANTINED`, the only non-rebuild row that may resume finalization is the blocked row itself at `blocked_intake_seq`; later repair triggers or source-derived rows must remain pending behind it
- deferred entries are not retried in place; targeted reconsideration must re-enter the same ordered outbox path through a newly enqueued derived intake, or be handled by full rebuild
- if `FAILED_TERMINAL` uses `BOUNDED_THREADS_DEGRADED`, those impacted threads must be atomically moved into query-safe degraded serving before the checkpoint advances

### 14.4 Asynchronous thread worker

The thread worker processes outbox entries per `memoryId`.

Worker stages:

1. load the next outbox entry in `intakeSeq` order together with current thread projection heads
2. branch by `triggerType`
3. for source-derived item intake, extract `ThreadIntakeSignal` from committed item and graph facts
4. for source-derived item intake, generate canonical anchor candidates and, if present, consult any matching `thread_accumulation_hint` only to prioritize bounded historical evidence reinspection
5. for source-derived item intake, decide `attach / reopen / create / defer / ignore`
6. for source-derived item intake, if the signal is hint-worthy but still below formal creation threshold, refresh or merge the matching `thread_accumulation_hint`
7. for derived explicit repair intake, load the deterministic repair directive from `repairOperation`, `repairDirectiveKey`, and `repairPayloadJson`, validate that the referenced impacted threads and successor targets still satisfy the repair preconditions, and build the repair mutation plan without running `attach / reopen / create / defer / ignore` or retargeting through fresh semantic search
   This same deterministic repair flow is also used when operator recovery re-drives a previously blocked outbox row at `blocked_intake_seq`.
8. normalize one or more thread events
9. update thread root row
10. upsert memberships
11. append thread events and event-source links
12. reduce and write snapshot
13. update outbox status to `COMPLETED`, `COMPLETED_DEFERRED`, `FAILED_RETRYABLE`, `FAILED_TERMINAL`, or `QUARANTINED_BLOCKING`

### 14.5 Thread-core transaction boundary

Within the thread worker, including repair-trigger processing that runs through the ordered outbox path, the following writes must commit atomically:

- `memory_thread`
- `memory_thread_generation`
- `memory_thread_anchor_binding`
- `memory_thread_membership`
- `memory_thread_current_membership`
- `memory_thread_event`
- `memory_thread_event_source`
- `memory_thread_snapshot`
- `thread_materialization_state`

If snapshot reduction fails, the thread-core transaction fails and the outbox entry remains retryable.

If finalization outcome is `COMPLETED_DEFERRED`, no thread-core rows are required to change. In that case the atomic finalization boundary consists of outbox finalization, `thread_intake_revisit`, and `thread_materialization_state.last_finalized_intake_seq` advancement only.

If finalization outcome is `FAILED_TERMINAL`, no thread-core rows are required to change only for `NOOP_CONFIRMED`. For `BOUNDED_THREADS_DEGRADED`, the same atomic boundary must also mark every deterministically impacted existing thread `FAILED` and suppress all of their current-membership rows.

If finalization outcome is `QUARANTINED_BLOCKING`, no thread-core rows are required to change. In that case the atomic boundary consists of the outbox quarantine status plus the memory-scoped blocked fields on `thread_materialization_state`, without advancing `last_finalized_intake_seq`.

The transaction must also advance:

- `projectionGeneration` when a rebuild, explicit repair, or invalidation-closure generation is cut over
- `generationCutoverJobId` when a rebuild, explicit repair, or invalidation-closure generation is cut over
- `lastAppliedIntakeSeq`
- canonical `object_state`
- the final outbox status for the processed `intakeSeq`, or an equivalent exactly-once fencing record
- `thread_materialization_state.last_finalized_intake_seq`
- the `thread_intake_revisit` record when finalizing `COMPLETED_DEFERRED`

For explicit repair generation cutover, `generationCutoverJobId` must identify the ordered repair trigger that produced that generation.

`thread_accumulation_hint` is intentionally excluded from this exactly-once correctness boundary. It may be updated in the same transaction when convenient, or repaired asynchronously later, because correctness must not depend on its freshness.

Exactly-once protocol rules:

- success commit must atomically persist thread-core writes, final outbox status, the new `lastAppliedIntakeSeq = intakeSeq` on affected threads, and `thread_materialization_state.last_finalized_intake_seq = intakeSeq`
- deferred success commit must atomically persist the final outbox status, `thread_materialization_state.last_finalized_intake_seq = intakeSeq`, and the corresponding `thread_intake_revisit` write
- terminal failure commit must atomically persist the final outbox status, terminal failure metadata, and `thread_materialization_state.last_finalized_intake_seq = intakeSeq`
- quarantine commit must atomically persist the final outbox quarantine status together with memory-scoped blocked metadata and must not advance `thread_materialization_state.last_finalized_intake_seq`
- successful same-seq recovery of a previously blocked outbox row must atomically persist the repaired thread-core writes or deterministic no-op/degraded finalization, the final outbox status for that blocked row, clearing of every `blocked_*` field, and `thread_materialization_state.last_finalized_intake_seq = blocked_intake_seq`
- retry logic must treat `(memoryId, intakeSeq)` as already finalized only by consulting the final outbox status or `thread_materialization_state.last_finalized_intake_seq`, never by inferring from any thread root row alone
- rebuild cutover must finalize only the ordered intake prefix it has fully incorporated
- later retries of an already finalized `intakeSeq` must no-op rather than reapply thread changes
- event replay or targeted reconsideration must be deduplicated at source-revision and normalized-event-key level within one active projection generation; a new `intakeSeq` alone is not enough to justify duplicate events

### 14.6 Rebuild path

The subsystem must support full rebuild per `memoryId`.

Rebuild semantics:

- compute replacement thread-core state from authoritative items and graph facts
- rescan committed items and graph evidence in stable order
- rerun the same thread materialization logic
- rebuild in a non-empty memory must preserve surviving thread identities rather than dropping all thread rows and minting new public ids wholesale
- hold a memory-scoped rebuild lease while processing the chosen rebuild prefix
- choose one rebuild cutover `intakeSeq` prefix and exclude newer intake until that prefix is fully finalized
- atomically cut over each rebuilt thread independently: root row, generation header, active anchor binding cache, current membership projection, events, and snapshot for that thread move to the new `projectionGeneration` in one transaction
- v1 does not guarantee one memory-wide snapshot-consistent rebuild epoch across all threads; the guarantee is atomic cutover per thread
- threads not yet rebuilt during that job must remain suppressed from default current projection serving if they are `STALE_UNSAFE` or marked `REBUILDING`
- rebuild must ignore `thread_intake_revisit` as a semantic input; revisit is operational ambiguity memory, not rebuild source-of-truth
- a rebuild must use one stable `active_rebuild_job_id` for the chosen cutover prefix and must resume with that same job id after restart until the job is completed or explicitly abandoned
- if the memory is `BLOCKED_QUARANTINED`, the recovery rebuild must choose a cutover prefix at or before `blocked_intake_seq` and must not clear blocked state unless that rebuild fully incorporates or invalidates the blocked intake
- for every rebuilt surviving object, rebuild must choose an identity-preservation target in the following strict precedence order: active canonical binding target, then one-to-one redirected canonical binding target, then explicit lineage metadata that names exactly one eligible canonical current target
- lower-precedence lineage metadata must not override a higher-precedence binding-derived target
- threads closed by `MERGED | SPLIT | INVALIDATED`, and threads carrying `superseded_by_thread_id`, are never direct identity-preservation targets; they may only contribute evidence that points to an eligible canonical target
- a new `threadId` and `threadKey` may be minted during rebuild only when authoritative rescan concludes a surviving object has no eligible existing thread identity to preserve
- per-thread rebuild cutover must compare-and-swap on `generation_cutover_job_id`; if the thread already carries the current `active_rebuild_job_id`, retry must no-op rather than bump `projectionGeneration` again
- deterministic rebuild must not leave a previously tracked thread in indefinite degraded limbo just because no surviving rebuilt object was produced
- if authoritative rescan concludes a previously existing thread has no surviving rebuilt object and no explicit merge or split successor applies, rebuild must cut over an explicit closure generation on that same thread with `projectionStatus = HEALTHY`, `lifecycleStatus = CLOSED`, `closureReason = INVALIDATED`, `objectState = UNCERTAIN`, an `INVALIDATED` event, empty current membership, retired active anchor bindings, and a corresponding authoritative snapshot
- that `INVALIDATED` closure generation counts as a successful rebuild cutover for idempotency and checkpoint-finalization purposes
- rebuild prefix finalization is allowed only after deterministic rescan confirms that every thread in scope for the cutover prefix is either already cut over by the current job id, newly created by that job, or explicitly invalidated by that job
- successful quarantine-clearing rebuild must atomically clear `thread_materialization_state.materialization_status`, clear every `blocked_*` field, and advance `last_finalized_intake_seq` to the fully incorporated ordered prefix in the same commit that finalizes the recovery cutover

This replaces reliance on legacy thread rows for correctness.

### 14.7 Invalidation, deletion, and repair semantics

V1 does not rely on in-place event retraction.

The following source-fact changes must mark impacted threads `STALE_UNSAFE` and schedule generation rebuild:

- item deletion
- item revision or content replacement
- extraction-version change that alters normalized meaning
- graph evidence invalidation or repair

Rules:

- default query traffic must not continue to expose stale derived thread state once a thread is marked `STALE_UNSAFE`
- first-class explicit `RETYPE`, `ANCHOR_REKEY`, `MERGE`, and `SPLIT` repairs may cut over a new generation directly through the ordered repair-trigger path; they do not require an intermediate `STALE_UNSAFE` state when the repair transaction itself produces the next safe current projection
- successful rebuild or projection-changing explicit repair advances `projectionGeneration`
- default v1 retention policy must retain all generation headers, historical events, historical memberships, and anchor-binding history unless an operator explicitly enables compaction
- operator-enabled compaction may evict non-current historical events and memberships only behind an explicit retention floor; it must not evict current generation state, `memory_thread_generation` headers, or lineage/audit-availability metadata needed to explain what history remains
- snapshot and membership correctness after invalidation always comes from rebuild, not ad hoc patching
- `memory_thread_current_membership` rows for a `STALE_UNSAFE` thread must be atomically moved to `SUPPRESSED` as part of the invalidation transaction
- the same suppression invariant applies while the thread remains `REBUILDING` or `FAILED`
- successful rebuild that finds no surviving current object must cut over an explicit `INVALIDATED` closure generation rather than deleting the thread or leaving it degraded forever
- detail reads for `STALE_UNSAFE`, `REBUILDING`, and `FAILED` threads must degrade to safe metadata only: immutable identity, lineage, projection head, explicit projection status, and optional `lastKnown*` cache fields
- stale default detail must not expose `lifecycleStatus`, `objectState`, `lastEventAt`, or `lastMeaningfulUpdateAt` as authoritative current-state fields
- default member, event, and item-to-thread reads for a `STALE_UNSAFE`, `REBUILDING`, or `FAILED` thread must return no current projection content
- serving the pre-rebuild snapshot or stale current members/events requires explicit audit or stale-read opt-in and must never be the default
- if a terminalized intake degraded bounded impacted threads, those threads must follow the same default serving suppression contract as `FAILED`

## 15. Intake Signal and Decision Model

The write path distinguishes signal extraction from final decision.

### 15.1 `ThreadIntakeSignal`

Each item contributes a normalized intake signal that may include:

- candidate anchor mentions
- explicit object reference
- graph-neighborhood evidence
- temporal continuation clues
- state change clues
- blocker clues
- decision clues
- relationship change clues
- topic continuity clues

### 15.2 Candidate generation sources

Anchor and thread candidates should be generated from:

- explicit reference in the item itself
- graph neighborhood around the item
- anchor alias matches
- recent active threads in the same memory
- exact anchor-key match against existing threads

Candidate eligibility rules:

- exact anchor-key and alias matches must resolve through `memory_thread_anchor_binding`, not by scanning root-row anchor caches alone
- direct `ATTACH_EXISTING` candidates must be canonical current targets resolved from an `ACTIVE` binding or a one-to-one `REDIRECTED` binding
- retired non-redirected bindings, invalidated threads, split parents, and superseded merge losers must never be direct `ATTACH_EXISTING` targets
- if an old anchor resolves by redirect, the attach target is the canonical destination thread, not the retired source thread

### 15.3 Decision outputs

The decision stage must return exactly one of:

- `ATTACH_EXISTING`
- `REOPEN_EXISTING`
- `CREATE_NEW`
- `DEFER_AMBIGUOUS`
- `IGNORE`

### 15.4 Decision dimensions

The decision should score at least these dimensions:

- `anchorability`
- `continuity`
- `statefulness`
- `existing-anchor-match`
- `existing-thread-compatibility`
- `ambiguity-penalty`

Definitions:

- `anchorability`: whether the item points to a durable identifiable object
- `continuity`: whether the object plausibly persists across time and deserves tracking
- `statefulness`: whether the object has trackable state, progress, decision, blocker, or resolution semantics

### 15.5 Required decision rules

The worker must apply the following logic.

1. If a canonical existing thread is matched strongly enough, prefer `ATTACH_EXISTING`.
2. If the chosen existing thread is `DORMANT`, or is `CLOSED` with `closureReason = RESOLVED | EXPLICIT`, and the new signal restores continuity, prefer `REOPEN_EXISTING`.
3. If multiple existing threads or object interpretations remain plausible and ambiguity stays above threshold, use `DEFER_AMBIGUOUS`.
4. If no existing thread is strong enough, ambiguity is low, and `anchorability + continuity + statefulness` all exceed creation threshold, use `CREATE_NEW`.
5. Otherwise use `IGNORE`.

`ATTACH_EXISTING` must target only a canonical current thread selected from the candidate-eligibility rules above.

`REOPEN_EXISTING` must never target a thread closed by `MERGED` or `SPLIT`, or any thread that already points at `supersededByThreadId`.

`REOPEN_EXISTING` must never target a thread closed by `INVALIDATED`; if authoritative facts support a new object after invalidation, that object must be evaluated for `CREATE_NEW` against the current canonical binding set.

### 15.6 No formal latent candidate object in v1

When a signal is promising but below creation threshold, v1 does not persist a `memory_thread_candidate` row.

Instead:

- the worker may upsert an expiring `thread_accumulation_hint` keyed by a deterministic weak object cue such as topic phrase, relationship pair, or other anchor precursor
- hints may accumulate only bounded support count, support weight, timestamps, and small source samples; they are not full latent object records
- hints are advisory only: they may help the system rediscover which evidence slice to re-read, but they must never reserve anchors, appear in thread queries, prove that a formal thread already exists, or by themselves push a decision across a creation threshold
- hint expiry, loss, or corruption must not compromise correctness; at worst it delays weak-signal thread creation until later evidence or full rebuild
- the item remains available to graph and later rebuild
- future items may cause creation during later incremental materialization or full rebuild
- `DEFER_AMBIGUOUS` remains an outbox finalization outcome, not a formal thread object
- unresolved ambiguity is retained through `thread_intake_revisit`, not through `memory_thread_candidate`
- targeted revisit may later reprocess the deferred source together with new evidence only by enqueuing a fresh derived outbox intake with a new `intakeSeq`
- full rebuild does not read revisit state as semantic input; unresolved items are reconsidered from authoritative source facts only
- full rebuild may recompute or ignore `thread_accumulation_hint`, but correctness and eventual object existence must never depend on it

## 16. Event Model

Timeline events are normalized thread facts, not raw items.

### 16.1 Canonical event types

V1 supports the following event types:

- `OBSERVATION`
- `UPDATE`
- `STATE_CHANGE`
- `BLOCKER_ADDED`
- `BLOCKER_CLEARED`
- `NEXT_ACTION_SET`
- `NEXT_ACTION_DONE`
- `DECISION_MADE`
- `DECISION_REVISED`
- `RELATION_CHANGE`
- `RESOLUTION_DECLARED`
- `REOPENED`
- `INVALIDATED`
- `THREAD_RETYPED`
- `ANCHOR_REKEYED`
- `MERGE_ABSORBED`
- `MERGED_INTO`
- `SPLIT_FROM`

### 16.2 Event normalization rules

Rules:

- one item may generate zero, one, or multiple thread events
- one thread event may cite multiple evidence sources
- event payload must be structured JSON, not only summary text
- event payload must carry explicit payload schema and normalization versions
- event type is reducer-critical and must not be hidden only inside metadata
- current timeline queries read only the active `projectionGeneration` unless audit mode explicitly asks for historical generations
- event normalization must emit a stable `eventSemanticSlot` that names the semantic slot within its event type; if one source revision yields multiple events of the same type, their semantic slots must differ
- event normalization must emit a deterministic `normalizedEventKey` for each thread event
- `normalizedEventKey` must be derived from at least `eventType`, `eventSemanticSlot`, and the stable origin-revision fingerprint described in the event storage contract
- replay, explicit reprocessing, or targeted revisit of the same source revision into the same thread generation must upsert or no-op on `normalizedEventKey` rather than append a duplicate event
- if new evidence would reinterpret an already materialized source revision into a materially different event set, the correction must occur through rebuild or new projection generation, not by duplicating conflicting events in the same generation
- `RETYPE`, `ANCHOR_REKEY`, `MERGE`, `SPLIT`, and `INVALIDATED` closure are projection-changing repair outcomes and must be represented in the cutover generation rather than as ad hoc side mutations
- `RETYPE` must emit exactly one `THREAD_RETYPED` event in the repaired thread generation naming previous and new broad type
- `ANCHOR_REKEY` must emit exactly one `ANCHOR_REKEYED` event in the repaired thread generation
- `MERGE` must emit exactly one `MERGE_ABSORBED` event in the survivor merge generation naming the absorbed losing threads
- `MERGE` must emit exactly one `MERGED_INTO` event in each losing thread terminal generation
- `SPLIT` must emit exactly one `SPLIT_FROM` event in each child opening generation; parent-side split closure is represented by `closureReason = SPLIT` plus terminal generation cutover, not by a second split-specific parent event in v1

### 16.3 Meaningful update semantics

`is_meaningful` indicates whether an event should affect dormancy heuristics and "last meaningful update" timestamps.

Examples:

- a state change is meaningful
- a substantive decision is meaningful
- a weak mention-only observation may be non-meaningful

## 17. Snapshot Model

Snapshot is the current reduced object view.

### 17.1 Required shared fields

Every thread snapshot must include at least:

- `currentState`
- `headline`
- `latestUpdate`
- `salientFacts[]`
- `lastMeaningfulUpdateAt`
- `confidence`

The following structures are no longer mandatory shared fields and instead live in typed facets:

- blockers
- next actions
- resolution detail
- open questions

### 17.2 Snapshot reduction contract

The reducer must be deterministic over:

- ordered thread events
- current memberships
- thread type and reducer policy

Historical audit snapshot reconstruction, when the requested generation is retained, must run the same reducer over that generation's events, that generation's memberships, and the applicable reducer policy for that generation.

Historical reconstruction must use the requested `memory_thread_generation` header for `threadType`, `reducerPolicyId`, `reducerVersion`, lifecycle, object-state, lineage, and cutover semantics rather than mutable current root-row caches or latest reducer defaults.

Historical audit is a first-class capability, not best-effort logging. The default v1 retention posture must be retain-all for generation headers, historical events, historical memberships, and anchor-binding history unless operator-configured compaction is explicitly enabled.

`memory_thread.object_state` is the canonical coarse state for filtering and indexing.

`snapshot.currentState` is an embedded copy written in the same transaction and must match `memory_thread.object_state` exactly.

The persisted `memory_thread_snapshot` row is the active-generation cache only. Historical snapshot reads may reconstruct on demand from retained historical events and memberships and must not require persisted historical snapshot rows.

### 17.3 Structured first, textual second

The source-of-truth snapshot representation is structured JSON.

`snapshot_text` is allowed as a derived renderable summary for later retrieval/context consumers, but it is not the canonical state representation.

### 17.4 Facet families

V1 supports typed facet payloads on top of the shared snapshot core.

Recommended facet families:

- `progress`
- `decision`
- `blocker`
- `relationship`
- `topic`
- `resolution`

### 17.5 Default facet emphasis by thread type

Default emphasis rules:

- `WORK`: `progress`, `blocker`, `decision`
- `CASE`: `resolution`, `blocker`, `decision`
- `RELATIONSHIP`: `relationship`, `decision`
- `TOPIC`: `topic`, `decision`, `resolution`

These are defaults, not hard walls. A thread may carry any facet that its evidence justifies.

## 18. Membership Model

Membership answers which items contribute to thread understanding.

The design distinguishes between:

- `memory_thread_membership`: immutable per-generation membership history
- `memory_thread_current_membership`: current serveable membership state

### 18.1 Why membership exists separately from event source

Event source explains why a particular event exists.

Membership answers whether an item belongs in the broader case file or object context.

Membership history is projection history rather than global source-of-truth fact. Current serveable membership is maintained separately from historical generations. If source facts are invalidated, current membership is withdrawn from default serving and recomputed during generation rebuild.

An item may:

- be a member without being the main trigger of the latest event
- trigger an event without being the only member that matters to the thread

### 18.2 Primary membership rule

Primary membership is a thread-local emphasis signal, not a memory-global exclusivity rule.

Purpose:

- allow one item to act as owner-like evidence for multiple durable objects when that is semantically true
- still let consumers derive one preferred owner-like thread later when a specific read path needs it

Rules:

- core storage must not enforce memory-global uniqueness on `is_primary`
- one item may be primary in multiple current threads when each thread has a defensible owner-like claim
- consumers that need one preferred thread per item must derive that preference in a view-specific ranking layer rather than treating it as thread-core truth

### 18.3 Secondary membership examples

Examples:

- a meeting note may be primary to a `WORK` thread and secondary to a `RELATIONSHIP` thread
- an incident update may be primary to a `CASE` thread and secondary to a `WORK` thread
- a research note may be primary to a `TOPIC` thread and secondary to a `WORK` thread

## 19. Query Contract

V1 includes a formal thread query surface even though deep retrieval integration is deferred.

### 19.1 Required reads

The thread query layer must support:

- `getThread(threadId | threadKey)`
- `listThreads(memoryId, filters, paging)`
- `listThreadEvents(threadId, paging)`
- `listThreadMembers(threadId, filters, paging)`
- `listThreadsByItem(itemId)`
- `listThreadGenerations(threadId, paging)`
- `getThreadGeneration(threadId, projectionGeneration)`
- `getThreadMaterializationState(memoryId)`
- `resolveThreadByAnchor(memoryId, anchorKind, anchorKey)`

`resolveThreadByAnchor` must consult `memory_thread_anchor_binding`, not only the cached root-row anchor.

`resolveThreadByAnchor` must distinguish between:

- active canonical resolution
- one-to-one redirected canonical resolution
- retired anchor with no direct canonical successor

`listThreadGenerations` must return immutable generation headers ordered by `projectionGeneration`.

`getThreadGeneration` must return the requested immutable generation header and may optionally include reconstructed historical snapshot content if the caller explicitly opts into audit detail and the requested generation is retained.

`getThreadMaterializationState` must return the memory-scoped ordered checkpoint, rebuild metadata, and any `BLOCKED_QUARANTINED` status together with the blocked intake identifiers and reason when present.

### 19.2 Minimum list filters

Thread lists should filter by:

- `threadType`
- `lifecycleStatus`
- `objectState`
- anchor label or alias
- updated time range

If `projectionStatus = STALE_UNSAFE`, default list filtering must not treat cached projection-derived fields as authoritative current state. Stale threads may be filtered by `projectionStatus`, identity, lineage, or explicit opt-in `lastKnown*` filters only if the API exposes them.

If `projectionStatus = REBUILDING | FAILED`, default list filtering must apply the same non-authoritative rule as `STALE_UNSAFE`.

If `thread_materialization_state.materialization_status = BLOCKED_QUARANTINED` for the enclosing memory, default current-serving list filtering must fail closed or expose explicit memory-blocked metadata rather than serving current-state filters from stale thread caches.

Default filter closure rules:

- `lifecycleStatus` and `objectState` filters apply only to `HEALTHY` threads by default
- threads in `STALE_UNSAFE | REBUILDING | FAILED` must be excluded from `lifecycleStatus` and `objectState` filter matches unless the API explicitly exposes opt-in `lastKnownLifecycleStatus` or `lastKnownObjectState` filters
- `updated time range` on degraded threads must be interpreted only from source-safe or explicitly labeled `lastKnown*` timestamps, never from unlabeled projection-derived cache fields
- if no safe timestamp is available for a degraded thread, it must not match `updated time range` filters by default

### 19.3 Return-shape requirement

The default thread detail response should include:

- thread identity
- lifecycle and coarse state
- lineage metadata
- projection head metadata
- current snapshot
- summary counts for events and members

Timeline and member rows should be queried separately.

Default summary counts must be computed from the same current-serving projection used by default member and event queries.

If a thread has been superseded by merge, the default detail query should either redirect to the canonical survivor or expose `supersededByThreadId` explicitly.

If `resolveThreadByAnchor` hits a retired split-origin anchor with no direct canonical successor, the default anchor-resolution response must not pick a child implicitly. It should return no canonical direct match together with lineage or last-owning-thread hints if available.

If `projectionStatus = STALE_UNSAFE | REBUILDING | FAILED`, default detail response must omit:

- `lifecycleStatus`
- `objectState`
- `lastEventAt`
- `lastMeaningfulUpdateAt`
- cached snapshot payload
- current event counts
- current membership counts

Instead, degraded default detail may expose only:

- immutable identity fields
- lineage metadata
- projection head metadata
- explicit projection-status marker or reason
- optional `lastKnownLifecycleStatus`
- optional `lastKnownObjectState`
- optional `lastKnownLastEventAt`
- optional `lastKnownLastMeaningfulUpdateAt`

If the enclosing memory is `BLOCKED_QUARANTINED`, default detail response must also expose explicit memory-blocked metadata and must not claim any thread in that memory is current beyond the blocked intake prefix.

### 19.4 Default serving rules

Default thread queries are current-projection reads, not historical audit reads.

Rules:

- `getThread`, `listThreads`, `listThreadMembers`, and `listThreadsByItem` operate on the current active projection only by default
- `listThreads` must not expose stale threads with normal current-state fields populated from projection-derived caches; it must either omit those fields or expose them only through `lastKnown*` names
- `listThreadMembers` reads from `memory_thread_current_membership` and returns only rows where `visibility_status = ACTIVE` by default
- `listThreadsByItem` resolves only `memory_thread_current_membership` rows where `visibility_status = ACTIVE` by default
- transitions into `STALE_UNSAFE | REBUILDING | FAILED` must suppress `memory_thread_current_membership.visibility_status = ACTIVE` atomically, so default member and item-to-thread queries can rely on current-membership visibility without a second degraded-state join
- `listThreadEvents` returns only the active `projectionGeneration` by default
- `resolveThreadByAnchor` follows active bindings first and redirected bindings second by default; retired non-redirected anchors must not be auto-resolved
- historical generations, retired memberships, and stale pre-rebuild projections are available only through explicit audit or history opt-in
- if explicit audit or history opt-in requests a historical snapshot and the requested generation is still retained, v1 may reconstruct it on demand from that generation's historical events and memberships; it must not require a persisted historical snapshot row
- if `projectionStatus = STALE_UNSAFE | REBUILDING | FAILED`, default detail, member, event, and item-to-thread reads must suppress current projection content and expose explicit projection-status metadata instead
- degraded default detail must use `lastKnown*` cache field names or omission, never the normal current-state field names for projection-derived values
- degraded threads must not match default `lifecycleStatus` or `objectState` filters unless the caller explicitly opts into `lastKnown*` filter semantics
- if `thread_materialization_state.materialization_status = BLOCKED_QUARANTINED`, default list/detail/member/event/item-to-thread reads for that memory must fail closed or expose explicit memory-blocked metadata rather than serving current projection content from any thread in that memory

### 19.5 Audit retention contract

Audit and history are first-class thread capabilities, not best-effort leftovers.

Rules:

- default v1 retention policy must be retain-all for `memory_thread_generation`, historical events, historical memberships, and anchor-binding history
- operator-enabled compaction may evict non-current historical events and memberships only behind an explicit retention floor; it must not evict current generation state or `memory_thread_generation` rows
- audit-capable responses must expose enough retention metadata to explain history availability, including at least `historyAvailability` and `oldestRetainedGeneration` or an equivalent explicit floor
- requests for a generation or historical snapshot that is older than the retained floor must return explicit `HISTORY_NOT_RETAINED` style metadata rather than an empty success response
- current-only default queries remain independent from historical retention beyond the active generation

## 20. Consistency, Failure Handling, and Operations

### 20.1 Source-fact priority

Item and graph commits remain the first durable fact layer. Thread materialization failure must never lose those source facts.

### 20.2 Retry model

If thread materialization fails:

- retryable failures leave the outbox entry non-final as `PENDING` or `FAILED_RETRYABLE`
- no partial thread-core transaction is committed
- the worker may retry safely
- `thread_materialization_state.last_finalized_intake_seq` must not advance

If repeated failure crosses the terminal-failure policy or the error is classified as non-retryable and the intake safely qualifies for terminal finalization:

- the outbox entry transitions to `FAILED_TERMINAL`
- terminal finalization advances `thread_materialization_state.last_finalized_intake_seq` so later intake is not permanently blocked
- the source facts remain durable and available for later rebuild or explicit replay by enqueuing a new derived intake
- terminal failure must be surfaced as an operator-visible condition, never as a silent drop
- `FAILED_TERMINAL` must not be used to silently skip a potentially thread-creating or unbounded-impact intake
- if `terminalFailureClass = BOUNDED_THREADS_DEGRADED`, every deterministically impacted existing thread must be atomically degraded to `FAILED` and have its current-membership rows suppressed before checkpoint advancement

If retries are exhausted and the intake still cannot safely qualify for `FAILED_TERMINAL`:

- the outbox entry must transition to `QUARANTINED_BLOCKING`
- the memory must transition to `thread_materialization_state.materialization_status = BLOCKED_QUARANTINED`
- later incremental intake for that memory must stop until operator repair or rebuild clears the blocked state
- operator repair that clears the quarantine must resume and finalize the blocked row itself at `blocked_intake_seq`; it must not bypass the block by enqueueing a later repair row
- default current-serving thread queries for that memory must fail closed or expose explicit memory-blocked metadata rather than claiming current truth from stale caches

### 20.3 Idempotency requirement

Materialization must be idempotent for repeated outbox processing and full rebuild.

Required techniques include:

- deterministic anchor normalization
- monotonic `intakeSeq` per memory
- stable event normalization
- single active materializer lease per memory
- uniqueness constraints for memberships and event sequence allocation
- stable `normalized_event_key` generation and uniqueness
- immutable per-generation headers that freeze reducer contract and generation semantics for audit
- projection-generation cutover instead of partial in-place repair
- one ordered mutation path for both normal intake and revisit-driven reconsideration
- deterministic repair directives persisted on ordered repair-trigger rows rather than reconstructed from unordered operator state
- explicit memory-scope quarantine state for poison intake that cannot safely retry or terminalize
- per-thread atomic rebuild cutover under a memory-scoped rebuild lease
- explicit memory-scoped checkpoint fencing through `thread_materialization_state`
- restart-safe rebuild job identity through `generation_cutover_job_id` and `active_rebuild_job_id`

### 20.4 Operational controls

The subsystem should expose at least:

- outbox backlog size
- oldest pending outbox age
- revisit backlog size
- terminal outbox failure count
- quarantined blocking-memory count
- oldest blocking quarantine age
- per-memory checkpoint lag between newest enqueued `intakeSeq` and `last_finalized_intake_seq`
- attach/reopen/create/defer/ignore counts
- explicit repair counts by `RETYPE | ANCHOR_REKEY | MERGE | SPLIT`
- deferred ambiguous count
- ambiguous decision rate
- stale-unsafe, rebuilding, and failed thread counts
- snapshot reduction failures
- rebuild success and failure counts

## 21. Testing Strategy

The thread core must be tested as a stateful subsystem, not just as row writes.

### 21.1 Unit tests

Required unit coverage:

- anchor normalization
- intake signal extraction
- attach/create/defer/ignore decision logic
- canonical attach-target eligibility and redirect handling
- repair-target selection for retype, rekey, and merge
- `RETYPE` contract enforcement that preserves identity while updating reducer contract and forbids implicit anchor or lineage mutation
- event normalization
- event semantic-slot and origin-fingerprint derivation
- normalized-event-key dedup semantics
- hint-assisted decision equivalence against hint-free authoritative evaluation
- repair event emission rules for `THREAD_RETYPED`, `ANCHOR_REKEYED`, `MERGE_ABSORBED`, `MERGED_INTO`, and `SPLIT_FROM`
- snapshot reduction
- historical snapshot reconstruction determinism from generation headers, generation-scoped events, and memberships
- lifecycle transitions

### 21.2 Store tests

Required store coverage:

- thread uniqueness invariants
- generation-header uniqueness and root-row mirror semantics
- many-to-many membership behavior
- current membership cutover behavior
- degraded current-membership suppression behavior
- lineage-terminal current-membership suppression behavior for `MERGED | SPLIT | INVALIDATED`
- mandatory redirect persistence for `ANCHOR_REKEY` and `MERGE`
- non-exclusive primary membership semantics
- single-source primary encoding semantics between `membership_role` and `is_primary`
- append-only event semantics
- normalized-event-key uniqueness
- event-source dedup uniqueness
- repair-trigger envelope persistence for `repairOperation`, `repairDirectiveKey`, and `repairPayloadJson`
- snapshot replacement semantics
- generation-header retention under historical compaction
- current-only snapshot cache semantics and historical-snapshot independence from persisted historical snapshot rows
- `thread_materialization_state` monotonic checkpoint behavior
- memory-scope quarantine persistence and clearing semantics

### 21.3 Integration tests

Required integration coverage:

- source facts and outbox envelope commit atomically
- item commit writes durable outbox
- worker materializes thread core from outbox
- rebuild reproduces expected thread state
- item deletion and source invalidation trigger generation rebuild
- dormant and reopen scenarios
- multi-thread membership scenarios
- ambiguous defer scenarios
- deferred entries do not block later ordered intake processing
- `COMPLETED_DEFERRED` finalization atomically persists the revisit record
- deferred revisit can later attach or create with new evidence only by re-entering `thread_intake_outbox` with a new `intakeSeq`
- retryable poison intake blocks later finalization until success, promotion to `FAILED_TERMINAL`, or transition into `QUARANTINED_BLOCKING`
- `FAILED_TERMINAL` finalization advances the memory checkpoint without silently losing rebuildability of the source facts
- `FAILED_TERMINAL` is rejected when the unresolved intake could still create a new thread or mutate an unknown thread set
- bounded-impact `FAILED_TERMINAL` atomically degrades all deterministically impacted existing threads before checkpoint advancement
- unbounded poison intake transitions to `QUARANTINED_BLOCKING` plus memory-scope blocked state rather than retrying forever or falsely terminalizing
- blocked memory cannot finalize later incremental intake until operator repair or rebuild clears quarantine
- successful quarantine recovery clears blocked state only together with blocked-intake finalization or rebuild-prefix cutover
- blocked-row operator recovery reuses `blocked_intake_seq` and does not bypass ordered finalization with a later repair trigger
- concurrent outbox ordering and lease behavior
- explicit repair trigger persists deterministic repair directive metadata and uses the same ordered intake path and fencing model as normal intake
- projection-changing explicit repair cutover stamps `generationCutoverJobId` with the ordered repair trigger identity
- repair trigger whose preconditions no longer hold does not retarget through fresh semantic search and resolves only as deterministic no-op or bounded degraded fallback
- crash-and-retry does not duplicate an already finalized `intakeSeq`
- replay or targeted reconsideration of the same source revision does not duplicate events in the same thread generation
- retype, anchor rekey, redirect resolution, merge, and split scenarios
- `RETYPE` preserves active anchor binding and lineage unless a same-generation repair plan explicitly includes `ANCHOR_REKEY` or another lineage-changing repair
- `ANCHOR_REKEY` and `MERGE` preserve one-to-one redirect resolution for old anchors
- split-origin anchors stay retired and do not auto-resolve to an arbitrary child
- exact anchor match never attaches directly to a retired, invalidated, split-parent, or superseded thread
- stale-unsafe thread detail suppression scenarios
- stale-unsafe member and event suppression scenarios
- `REBUILDING` and `FAILED` threads follow the same safe-serving suppression contract as `STALE_UNSAFE`
- per-thread rebuild cutover is atomic while same-memory threads may temporarily be on different rebuilt generations
- interrupted rebuild resumes with the same rebuild job id and does not double-bump `projectionGeneration` on already cut-over threads
- rebuild that finds no surviving object materializes an `INVALIDATED` closure generation instead of leaving the thread degraded
- rebuild preserves `threadId` and `threadKey` for surviving object continuity and mints new ones only for truly new surviving objects
- rebuild identity-preservation precedence is `ACTIVE` binding, then `REDIRECTED` binding, then exact one-to-one lineage target
- repair-trigger and rebuild cutover emit `THREAD_RETYPED`, `ANCHOR_REKEYED`, survivor `MERGE_ABSORBED`, loser `MERGED_INTO`, and `SPLIT_FROM` in the required impacted-thread generations
- historical snapshot audit reconstructs from retained generation headers, historical events, and memberships without persisted historical snapshot rows
- query surface exposes immutable generation headers and memory materialization state, including blocked-memory metadata and retained-history availability
- hint loss or recomputation does not change eventual thread existence when the same authoritative evidence is reconsidered
- slow-forming `TOPIC` and `RELATIONSHIP` signals can accumulate through `thread_accumulation_hint` without introducing latent formal threads

### 21.4 Behavioral fixtures

V1 should include representative fixtures for:

- project/work tracking
- issue/case tracking
- relationship evolution
- long-running topic accumulation

### 21.5 Semantic quality evaluation

V1 must be evaluated not only for liveness but for semantic value.

Required evaluation dimensions:

- duplicate-thread creation rate
- false merge rate
- false split rate
- false reopen rate
- unnecessary or wrong `RETYPE` rate
- defer-to-resolution rate for ambiguous intake
- operator-repair rate across `RETYPE | ANCHOR_REKEY | MERGE | SPLIT`

## 22. Migration from the Current Memory Thread

The current implementation should be treated as a legacy retrieval-assist thread layer.

### 22.1 Legacy assessment

Current thread provides:

- seed-thread selection from retrieved items
- bounded member admission for retrieval assist
- simple thread row plus membership row persistence

It does not provide:

- durable object identity
- append-only timeline
- structured snapshot
- many-to-many membership
- full lifecycle-aware object tracking

### 22.2 Migration decision

This design does not attempt semantic backfill from existing legacy thread rows into the new thread core.

Reasoning:

- the current rows do not encode enough object-level truth
- current memberships were created under single-thread-per-item assumptions
- current thread keys are not reliable canonical durable-object identities

### 22.3 Migration strategy

Migration should proceed as follows.

1. Introduce the new thread-core schema and outbox path.
2. Replace `memory_thread_item` with `memory_thread_membership`.
3. Extend or recreate `memory_thread` with the new core fields.
4. Add `memory_thread_generation`, `memory_thread_event`, `memory_thread_event_source`, `memory_thread_snapshot`, and `thread_materialization_state`.
5. Roll out incremental materialization for new item writes.
6. Rebuild thread core from committed items and graph facts per memory.
7. Remove legacy retrieval-thread assumptions from storage and admin views.

### 22.4 Compatibility posture

Compatibility is not the primary design driver here. Correctness of the new object model is more important than preserving lightweight legacy thread semantics.

## 23. V1 Boundary

V1 is complete when all of the following are true:

- formal thread core object model exists
- thread identity, lifecycle, timeline, membership, and snapshot are persisted
- intake signal and decision pipeline is implemented
- thread materialization runs through durable outbox plus async worker
- source facts and outbox envelope share one atomic commit boundary
- source invalidation and deletion trigger generation rebuild correctly
- memory-scoped ordering and exclusivity semantics are enforced
- memory-scoped materialization fencing and ordered checkpoint are implemented explicitly
- memory-scope quarantine exists for poison intake that cannot safely retry or terminalize
- retype, anchor rekey, merge, and split are first-class repair operations
- explicit repairs share the same ordered and fenced mutation model as intake or rebuild; no unordered repair write path exists
- explicit repair triggers persist deterministic repair directives on the ordered outbox path
- immutable `memory_thread_generation` headers exist and historical audit semantics read from them rather than mutable current caches
- anchor binding history and redirect resolution are implemented
- one-to-one rekey and merge successor anchors are preserved as mandatory redirected bindings
- repair generations emit the required canonical repair events, including `THREAD_RETYPED`, survivor-side `MERGE_ABSORBED`, and loser-side `MERGED_INTO`
- a lightweight weak-signal accumulation path exists for slow-forming `TOPIC` and `RELATIONSHIP` objects without introducing formal latent threads
- weak-signal accumulation remains advisory only and does not alter eventual authoritative rebuild results
- thread query contract is available
- generation-level audit reads and memory materialization-state reads are available
- default query paths read current active projection only
- explicit audit or history opt-in can reconstruct historical snapshots from retained generation headers, events, and memberships without persisted historical snapshot rows
- history retention and `HISTORY_NOT_RETAINED` style semantics are explicit rather than best-effort
- current membership is served from an explicit current-membership model
- primary membership is non-exclusive at thread-core truth level
- semantic quality evaluation exists for duplicate creation, retype, merge, split, reopen, and defer outcomes
- boundaries with graph and insight are explicit

V1 does not require:

- deep retrieval-thread consumption
- deep context assembly integration
- durable thread-candidate persistence
- thread-to-thread link graph
- watch/subscription workflows

## 24. Why This Design Is the Right Level

This design is the right v1 because it is:

- broader than the current retrieval assist
- much more coherent than multiple specialized thread systems
- reliable enough to become a real memory substrate
- narrow enough to implement without collapsing into a general workflow engine

Most importantly, it matches the actual product goal:

thread should feel like a durable case file around a living object, not like a related-item bundle.
