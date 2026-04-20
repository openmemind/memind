# Memind Simplified Thread Core V1 Design

**Date:** 2026-04-20
**Status:** Proposed
**Scope:** `memind-core` experimental thread core for rebuildable semantic object tracking

## 1. Context

`memind` already has three relevant layers:

- `item` persists extracted memory units
- `graph` persists structural evidence across items, entities, and typed links
- `thread` currently exists only as a lightweight retrieval-assist grouping layer

The current thread implementation is intentionally lightweight and already behaves as a post-commit derivation layer rather than a source-of-truth subsystem. That makes it a poor fit for the heavyweight reliability model proposed in the earlier unified-thread-core design, but it makes it a strong fit for an experimental `rebuildable semantic projection` design.

This spec defines that narrower V1.

## 2. Problem Statement

The previous unified-thread-core design identified the correct product direction but mixed two different goals into one design:

- the product goal: track durable objects over time so the system can answer "what is happening with X now, and how did it get here"
- the infrastructure goal: build a highly hardened ordered materialization subsystem with recovery directives, quarantine, redirected anchors, and projection generations

For `memind` V1 thread, the first goal matters immediately. The second goal does not.

The main product gap is semantic, not infrastructural:

- the current thread model has identity-like fields but no true object-tracking semantics
- the current thread model has memberships but no structured evolution timeline
- the current thread model can help bounded retrieval but cannot explain object evolution
- the current thread implementation still enforces single-thread-per-item, which blocks real multi-object participation

The result is a system that can group related items, but cannot yet represent persistent objects such as projects, cases, relationships, and long-running topics.

## 3. Design Goal

Thread V1 is an experimental subsystem whose job is to produce a rebuildable semantic object view from authoritative `item + graph` facts.

Thread V1 must be able to answer three questions for each tracked object:

1. What durable object is this
2. What happened to it over time
3. What state is it in now

Thread V1 is successful if it can do that for four broad thread types:

- `WORK`
- `CASE`
- `RELATIONSHIP`
- `TOPIC`

## 4. Non-Goals

Thread V1 explicitly does not attempt to provide the following:

- exactly-once materialization semantics
- quarantine, blocked checkpoints, or recovery directives
- redirect chains or canonical successor resolution
- generation-scoped historical audit
- merge, split, anchor rekey, reopen, or manual thread repair
- thread-aware retrieval or context assembly
- any design that treats thread state as a correctness boundary for source-fact writes

The only repair mechanism in V1 is full rebuild per `memoryId`.

## 5. Core Principles

### 5.1 Thread is a derived semantic projection

`item` and `graph` remain the only authoritative fact layers.

`thread` is a derived semantic projection built from those facts. It is never the source of truth for memory correctness.

### 5.2 Thread tracks durable objects, not related text

A formal thread exists only when the system can identify a persistent object worth tracking over time.

### 5.3 Identity, timeline, and snapshot are all required

A thread is valid only if it has all three:

- stable object identity
- structured evolution events
- a current reduced snapshot

### 5.4 Rebuild is the recovery model

If incremental thread materialization is incomplete, stale, or damaged, the system repairs it by rebuilding from authoritative `item + graph` facts.

### 5.5 Many-to-many membership is required

One item may participate in multiple threads. This is a core semantic requirement, not an optimization.

### 5.6 Semantic quality matters more than infrastructure detail

The differentiator is not outbox exactness. The differentiator is high-quality object discovery, event normalization, and current-state reduction.

## 6. V1 System Definition

Thread V1 is a `rebuildable semantic projection subsystem` with three responsibilities:

1. Discover durable objects from committed `item + graph` facts
2. Materialize a structured thread-local evolution timeline
3. Reduce that timeline into a current serveable snapshot

Thread V1 is intentionally not a distributed state-machine design.

## 7. Thread Types

Thread V1 supports four broad object types.

### 7.1 `WORK`

Tracks ongoing work objects such as:

- projects
- refactors
- migrations
- initiatives
- delivery efforts

Expected semantics:

- stage progression
- blockers and resolutions
- milestones
- outcomes

### 7.2 `CASE`

Tracks bounded problem objects such as:

- bugs
- incidents
- support issues
- disputes
- investigations

Expected semantics:

- problem statement
- attempted fixes
- setbacks
- resolution or mitigation

### 7.3 `RELATIONSHIP`

Tracks durable relation objects between exactly two stable actors.

Expected semantics:

- collaboration patterns
- conflict and repair
- role shifts
- continuity across time

The object is the relationship itself, not either participant alone.

V1 intentionally narrows `RELATIONSHIP` to dyadic relationships only. N-ary relationship objects are out of scope for this experimental core.

### 7.4 `TOPIC`

Tracks persistent recurring topics that accumulate over time.

Expected semantics:

- repeated engagement
- gradual accumulation
- evolving stance or understanding
- incomplete but durable continuity

## 8. Core Persistence Model

Thread V1 uses five tables:

- `memory_thread`
- `memory_thread_event`
- `memory_thread_membership`
- `memory_thread_anchor`
- `thread_intake_outbox`

No other thread-core table is required in V1.

### 8.1 `memory_thread`

This is the current thread root row and cache.

Required responsibilities:

- stable identity
- current canonical anchor cache
- thread type
- lifecycle
- current object state
- current snapshot payload
- current serveable text fields

Recommended fields:

- `thread_id`
- `memory_id`
- `thread_key`
- `thread_type`
- `anchor_kind`
- `anchor_key`
- `display_label`
- `lifecycle_status`
- `object_state`
- `headline`
- `snapshot_json`
- `snapshot_version`
- `opened_at`
- `last_event_at`
- `last_meaningful_update_at`
- `closed_at`
- `created_at`
- `updated_at`
- `deleted`

`memory_thread` stores only the current serveable projection. It does not store projection generations, repair state, redirected identity metadata, or historical lineage.

### 8.2 `memory_thread_event`

This is the authoritative semantic evolution log for one thread.

Required responsibilities:

- append normalized thread-local events
- preserve idempotent event identity
- preserve thread-local event ordering
- preserve the reducer input needed to rebuild the current snapshot

Recommended fields:

- `id`
- `memory_id`
- `thread_id`
- `event_key`
- `event_seq`
- `event_time`
- `event_type`
- `event_payload_json`
- `is_meaningful`
- `confidence`
- `created_at`

`event_payload_json` includes embedded source references. V1 does not require a separate `event_source` table.

The unique constraint must be:

- `UNIQUE(memory_id, thread_id, event_key)`

### 8.3 `memory_thread_membership`

This is the many-to-many item-to-thread association table.

Required responsibilities:

- associate one item to one or more threads
- preserve role and relevance inside a thread
- support current thread queries and reducer context

Recommended fields:

- `id`
- `memory_id`
- `thread_id`
- `item_id`
- `role`
- `is_primary`
- `relevance_weight`
- `created_at`
- `updated_at`
- `deleted`

The unique constraint must be:

- `UNIQUE(memory_id, thread_id, item_id)`

The previous global single-thread-per-item invariant is explicitly removed.

### 8.4 `memory_thread_anchor`

This table stores active canonical anchor bindings.

Required responsibilities:

- resolve the current canonical anchor for a thread
- prevent duplicate ownership of the same exact anchor

Recommended fields:

- `id`
- `memory_id`
- `thread_id`
- `anchor_kind`
- `anchor_key`
- `bound_at`
- `created_at`

V1 anchor rows are current-projection rows only.

V1 does not support:

- in-place anchor replacement
- redirected bindings
- successor resolution
- retained historical anchor chains

Required uniqueness rule:

- `UNIQUE(memory_id, anchor_kind, anchor_key)`

The canonical anchor of a V1 thread is immutable. If rebuild discovers that a different canonical anchor should own the object, the correct V1 result is a different thread identity in the replacement projection, not in-place anchor mutation.

### 8.5 `thread_intake_outbox`

This table stores pending and finalized asynchronous intake work.

Required responsibilities:

- represent outstanding thread intake work
- provide one canonical intake row per committed item
- support retryable asynchronous processing
- support multi-instance worker claim safety
- support published-prefix consistency reporting
- support operational diagnosis

Recommended fields:

- `id`
- `memory_id`
- `intake_seq`
- `trigger_item_id`
- `status`
- `completion_mode`
- `attempt_count`
- `lease_token`
- `leased_at`
- `enqueued_at`
- `finalized_at`
- `failure_reason`

V1 outbox statuses:

- `PENDING`
- `PROCESSING`
- `COMPLETED`
- `FAILED`

V1 completion modes for `COMPLETED` rows:

- `APPLIED`
- `COVERED_BY_REBUILD`

Required uniqueness rules:

- `UNIQUE(memory_id, intake_seq)`
- `UNIQUE(memory_id, trigger_item_id)`

`COMPLETED` means the source item is already reflected in the visible published thread projection, either by direct incremental application or by successful rebuild coverage.

This table is infrastructure, not thread-domain state.

## 9. Source-Fact Boundary

The authoritative write path remains:

- raw input
- extracted `item`
- derived `graph`

Thread V1 does not participate in that correctness boundary.

Rules:

- `item + graph` commit first
- thread work starts only after those source facts are committed
- failure to enqueue thread intake must never roll back committed source facts

This is a deliberate design choice. Thread is downstream semantic projection work, not source-fact durability work.

For thread correctness, the authoritative source set is:

- non-deleted committed items
- cutoff-consistent graph facts attributable to those items
- thread semantic markers persisted inside the same source-fact boundary

### 9.1 Source retraction rule

V1 distinguishes between additive source changes and retractions.

Additive source changes:

- newly committed items
- newly committed graph facts for those items

Source retractions:

- item deletion
- item invalidation
- removal or correction of previously committed thread-relevant semantic markers
- graph fact retraction that can change existing thread identity, events, memberships, or snapshot state

V1 supports additive changes incrementally.

V1 does not attempt fine-grained incremental repair for source retractions. Any source retraction affecting a `memoryId` must mark the thread projection as `REBUILD_REQUIRED` and must be repaired only by `rebuild(memoryId)`.

### 9.2 Graph correctness boundary

Deterministic thread logic may depend only on graph evidence that is attributable to source items.

Global derived graph aggregates such as alias indexes or cooccurrence summaries may be used only if they are provably equivalent to recomputation from the same cutoff-bounded item set. Otherwise they are accelerators only and must not be part of the thread correctness path.

## 10. Intake and Compensation Model

### 10.1 Enqueue after source commit

After a successful `item + graph` commit, the system writes one or more `thread_intake_outbox` rows for newly committed items.

The outbox row does not need to be written in the same database transaction as `item + graph`.

### 10.2 Missing outbox compensation

Because outbox write is not part of the source-fact transaction boundary, V1 requires an explicit compensation mechanism.

The system must provide:

- `backfillMissingOutbox(memoryId)`

This operation scans committed items and existing outbox rows and inserts missing intake rows.

`backfillMissingOutbox(memoryId)` must be idempotent and must respect `UNIQUE(memory_id, trigger_item_id)`.

### 10.3 Rebuild covers the same gap

`rebuild(memoryId)` must also repair the case where an item exists in authoritative storage but never entered the thread intake pipeline.

If rebuild discovers authoritative items without outbox rows inside its cutoff, it must create the missing rows and finalize them as `COMPLETED` with `completion_mode = COVERED_BY_REBUILD`.

### 10.4 Retractions bypass incremental repair

Source retractions do not emit negative thread deltas in V1.

Rules:

- additive changes enqueue `thread_intake_outbox` rows
- source retractions mark the projection `REBUILD_REQUIRED`
- once `REBUILD_REQUIRED` is raised, no incremental worker may publish additional visible thread state for that `memoryId`
- the only way to recover from `REBUILD_REQUIRED` is successful `rebuild(memoryId)`

## 11. Intake Ordering, Publication, and Projector Lease

V1 uses a minimal but strict claim model.

### 11.1 Memory-scoped projector lease

Exactly one projector lease per `memoryId` may mutate visible thread state.

Rules:

- incremental intake publication and rebuild cutover share the same memory-scoped projector lease
- at most one worker may hold the projector lease for one `memoryId` at a time
- while rebuild holds the projector lease, no incremental row may finalize as `COMPLETED/APPLIED`
- at most one worker may claim one `PENDING` outbox row at a time
- `PROCESSING` rows are protected by `lease_token + leased_at`
- stale leases may be reclaimed after timeout
- intake rows are allocated with increasing `intake_seq` per `memoryId`
- the worker holding the projector lease must claim the lowest publishable pending row first
- the worker holding the projector lease must publish rows in increasing `intake_seq` order
- a `FAILED` row blocks further visible prefix advance until rebuild succeeds

### 11.2 Published-prefix model

Incremental correctness is defined over the published prefix.

Definitions:

- `servable_through_intake_seq`: the greatest `intake_seq = N` whose source facts are fully reflected in the visible current projection for that `memoryId`
- `projection gap`: either a missing canonical outbox row or a non-completed row at or below the next expected `intake_seq`
- `published projection`: the thread projection visible to query APIs

Rules:

- successful incremental processing finalizes a row as `COMPLETED` with `completion_mode = APPLIED`
- successful rebuild may finalize uncovered rows inside its cutoff as `COMPLETED` with `completion_mode = COVERED_BY_REBUILD`
- the published projection must be exactly the reduction over source facts up to `servable_through_intake_seq`, or the equivalent rebuild cutoff
- no row with `intake_seq > servable_through_intake_seq` may mutate visible projection state
- authoritative items without canonical outbox rows count as projection gaps until backfilled or rebuild-covered
- if a gap appears, visible projection stays pinned at the last published prefix
- formal equivalence to rebuild is claimed only for the same published prefix or the same rebuild cutoff
- `getThreadRuntimeStatus(memoryId)` must expose the publication boundary and whether publication is blocked

Thread V1 provides:

- `at-least-once` processing
- idempotent finalization

Thread V1 does not provide:

- exactly-once processing
- memory-global ordered checkpoint fencing
- blocking quarantine

## 12. Thread Intake Pipeline

For each claimed outbox row, the worker runs the following stages:

1. load the trigger item and committed graph neighborhood
2. extract `ThreadIntakeSignal`
3. decide `ATTACH | CREATE | IGNORE`
4. emit zero or more normalized thread events
5. upsert memberships
6. refresh anchors
7. reduce the new current snapshot
8. overwrite the current `memory_thread` root cache
9. finalize the outbox row as `COMPLETED` with `completion_mode = APPLIED` only after its effects are included in the visible published prefix, or as `FAILED`

V1 does not persist deferred ambiguity state. Unqualified items are simply ignored until later stronger evidence arrives or rebuild recomputes a different result.

## 13. `ThreadIntakeSignal`

`ThreadIntakeSignal` is the structured semantic input used by deterministic thread decisions.

Recommended structure:

```java
record ThreadIntakeSignal(
    MemoryItem triggerItem,
    List<String> candidateAnchors,
    double anchorability,
    double continuity,
    double statefulness,
    List<Long> supportingHistoricalItemIds,
    List<Map<String, Object>> semanticMarkers,
    Map<String, Object> semanticHints
) {}
```

Required properties:

- deterministic from committed `item + graph` facts
- no online LLM dependency
- stable enough for rebuild equivalence

### 13.1 Required upstream thread semantics

Current `item category + graph` data is not sufficient by itself to support the full V1 event taxonomy deterministically.

Thread V1 therefore requires the extraction path to persist thread-relevant structured semantics in source facts. At minimum, V1 must have deterministic access to:

- item semantic time
- normalized actor and object mentions
- typed item-to-item links relevant to continuity
- thread semantic markers persisted on the item or equivalent structured payload for:
  - state transition
  - blocker added or cleared
  - decision
  - question opened or closed
  - milestone
  - setback
  - resolution or mitigation

These markers are part of the source-fact boundary for thread V1. Thread materialization may use them, but may not infer them from raw text at materialization time with an online LLM.

### 13.2 Degradation rule

If required markers are absent for a given item, normalization must degrade to generic `OBSERVATION` or `UPDATE` events rather than inventing stronger event semantics.

## 14. Object Discovery and Creation Gate

Thread creation must not be a raw similarity threshold.

V1 uses a type-aware eligibility model:

```java
interface ThreadEligibility {
    double anchorability();
    double continuity();
    double statefulness();
}
```

Creation rules by type:

- `WORK`: strong `anchorability` and `statefulness`
- `CASE`: strong `anchorability`, clear problem or resolution semantics, and enough anchor specificity to identify one stable case object
- `RELATIONSHIP`: high `continuity` and stable dyadic actor evidence
- `TOPIC`: high `continuity` and repeated cumulative evidence

If an item does not satisfy the create gate and does not attach to an existing thread, the correct V1 decision is `IGNORE`.

V1 creates continuity threads, not repeated episode threads. If source facts do not identify one stable object identity, the system must prefer `IGNORE` over creating a weakly keyed thread.

## 15. Important V1 Rule: Create May Backfill Historical Members

When the worker creates a new thread, it may include a bounded set of older relevant items as supporting memberships.

Allowed evidence sources:

- graph neighborhood around the trigger item
- typed temporal or causal links
- bounded reverse entity lookup
- entity cooccurrence

Rules:

- backfilled historical items become memberships in the new thread
- backfilled historical items may be referenced in event payload sources
- backfilled items do not require separate synthetic outbox rows
- backfill is bounded and deterministic
- backfilled membership upserts and event emission must respect the same idempotence keys as ordinary trigger-item processing

This rule is necessary so later stronger evidence can assemble a coherent `RELATIONSHIP` or `TOPIC` thread without extra accumulation infrastructure.

## 16. Anchor Canonicalization

Thread identity is anchored by deterministic canonical anchors.

### 16.1 `WORK` anchors

Preferred anchor kinds:

- `project`
- `task`
- `repo`
- `initiative`

Example:

- `project:alpha-auth-refactor`

### 16.2 `CASE` anchors

Preferred anchor kinds:

- `issue`
- `incident`
- `ticket`
- `exception`

Constrained fallback anchor:

- a deterministic case key already persisted by extraction metadata with enough specificity to identify one stable case object

Examples:

- `issue:bug-123`
- `case:legacy-session-incompatibility@auth-session-bridge`

### 16.3 `RELATIONSHIP` anchors

Preferred anchor kind:

- normalized participant pair

Example:

- `relationship:person:user|person:zhangsan`

Participant ordering must be canonicalized so the same pair cannot produce two threads.

Participant count must be exactly two. If the evidence implies a broader group relation, V1 must not coerce it into a `RELATIONSHIP` thread.

### 16.4 `TOPIC` anchors

Preferred anchor kind:

- normalized topic key

Example:

- `topic:jwt-migration-strategy`

### 16.5 Determinism rule

Canonical anchor generation may use:

- extracted graph hints
- normalized entity mentions
- typed graph links
- entity cooccurrence
- item metadata already persisted in the extraction path

Canonical anchor generation must not depend on online LLM output.

## 17. Thread Identity

`thread_key` must be generated deterministically from:

- `thread_type`
- canonical active anchor

Rules:

- same `thread_type + canonical_anchor` must produce the same `thread_key`
- V1 thread identity is continuity-based, not episode-based
- `WORK`, `CASE`, `RELATIONSHIP`, and `TOPIC` all represent one persistent object identity per canonical anchor
- canonical anchor is immutable for the lifetime of one published V1 thread
- reopenings, regressions, repeated blockers, or renewed investigation under the same anchor remain part of the same thread
- V1 does not create multiple distinct historical threads under one unchanged canonical anchor
- if source facts cannot provide anchor specificity sufficient to distinguish one stable object, the system must not create the thread

This allows rebuild to preserve thread identity without introducing generation headers, redirected bindings, or explicit lineage state.

Because V1 does not support anchor rekey, a changed canonical anchor is treated as a different recognized thread identity in the replacement projection.

## 18. Event Normalization

Thread events are not raw item copies. They are normalized thread-local semantic events.

V1 event types:

- `OBSERVATION`
- `UPDATE`
- `STATE_CHANGE`
- `BLOCKER_ADDED`
- `BLOCKER_CLEARED`
- `DECISION_MADE`
- `QUESTION_OPENED`
- `QUESTION_CLOSED`
- `RESOLUTION_DECLARED`
- `SETBACK`
- `MILESTONE_REACHED`

Recommended event payload shape:

```json
{
  "content": "project moved from implementation into testing",
  "state": "TESTING",
  "facts": [
    "JWT migration completed",
    "regression testing started"
  ],
  "sources": [
    {"type": "ITEM", "itemId": 42}
  ]
}
```

Rules:

- one item may yield zero, one, or many thread events
- event types more specific than `OBSERVATION` or `UPDATE` require corresponding persisted semantic markers
- event normalization is thread-scoped semantic interpretation
- event source references are embedded in payload
- each event must have a deterministic `event_key` derived from `thread_key`, `event_type`, primary source item identity, and a normalized semantic fingerprint
- repeated processing, historical backfill, and rebuild must upsert by `event_key` and must not create duplicate events
- `event_seq` is unique only inside one thread
- `event_seq` ordering must be derived from a stable sort on `(event_time, event_key)` for the current event set

## 19. Reducer Design

Thread V1 uses a two-layer reducer.

### 19.1 Deterministic structural reducer

This reducer is correctness-critical and rebuild-safe.

```java
StructuredSnapshot structuralReduce(
    List<MemoryThreadEvent> events,
    List<MemoryThreadMembership> memberships
) {}
```

It is responsible for:

- `object_state`
- `lifecycle_status`
- `opened_at`
- `closed_at`
- `last_event_at`
- `last_meaningful_update_at`
- structured facets
- current-state payload in `snapshot_json`

All query, filter, and sort semantics depend only on structural reducer output.

### 19.2 Best-effort text enrich

This reducer is presentation-only.

```java
TextSnapshot textEnrich(
    StructuredSnapshot structured,
    List<MemoryThreadEvent> recentEvents
) {}
```

It may produce:

- `headline`
- improved `display_label`
- `salientFacts`
- additional render text

Rules:

- text enrich failure must not invalidate structural correctness
- rebuild must remain correct even when text enrich is absent
- V1 may implement text enrich with deterministic rules first
- later LLM-based enrich is allowed only as best-effort presentation work

## 20. Lifecycle Semantics

V1 lifecycle statuses:

- `ACTIVE`
- `DORMANT`
- `CLOSED`

Suggested rules:

- recent meaningful activity implies `ACTIVE`
- long inactivity without closure implies `DORMANT`
- structural resolution semantics imply `CLOSED`

Lifecycle is reducer output, not a manually controlled workflow.

V1 does not provide explicit reopen operations.

## 21. Query Contract

V1 exposes only published current-projection queries.

Required APIs:

- `getThread(memoryId, threadId | threadKey)`
- `listThreads(memoryId, filters, paging)`
- `listThreadEvents(memoryId, threadId, paging)`
- `listThreadMembers(memoryId, threadId, paging)`
- `listThreadsByItem(memoryId, itemId)`
- `resolveThreadByAnchor(memoryId, anchorKind, anchorKey)`

Query rules:

- queries read published projection only
- queries must never read unserved head work above `servable_through_intake_seq`
- queries do not read outbox rows as user-facing state
- `resolveThreadByAnchor` resolves only the current canonical anchor rows in the published projection
- V1 does not expose redirected or successor identity semantics

If runtime state is `REBUILD_REQUIRED`, thread query APIs for that `memoryId` must fail closed rather than serve projection state known to be invalidated by source retraction.

## 22. Admin Surface

V1 exposes a minimal operational surface.

Required operations:

- `rebuildThreads(memoryId)`
- `flushThreadIntake(memoryId)`
- `backfillMissingOutbox(memoryId)`
- `getThreadRuntimeStatus(memoryId)`
- `listThreadOutbox(memoryId, filters)`

V1 must not expose:

- replay-one-intake repair
- thread merge
- thread split
- manual anchor rekey
- object surgery or manual reopen

The only supported repair mechanism is rebuild.

`getThreadRuntimeStatus(memoryId)` must surface at least:

- `projectionState`
- `servableThroughIntakeSeq`
- `highestEnqueuedIntakeSeq`
- `publicationBlocked`
- `failedRowCount`
- `rebuildInProgress`
- `latestRebuildCutoffItemId`

Recommended `projectionState` values:

- `AVAILABLE`
- `LAGGING`
- `REBUILD_REQUIRED`
- `REBUILDING`

## 23. Rebuild Contract

`rebuild(memoryId)` is the authoritative repair path.

Rules:

- rebuild reads only authoritative `item + graph`
- rebuild captures a deterministic source cutoff at start:
  `latest_rebuild_cutoff_item_id = MAX(item_id)` visible for that `memoryId`
- rebuild reads only items with `item_id <= latest_rebuild_cutoff_item_id`
- rebuild reads only graph evidence attributable to items inside that same cutoff
- if the store cannot expose a cutoff-filtered graph view directly, rebuild must derive its working graph view from cutoff-bounded item-linked graph primitives
- rebuild must not consume global graph aggregates whose values can be changed by post-cutoff items unless those aggregates are recomputed from the same cutoff-bounded source set
- rebuild does not use previous thread projection as semantic input
- rebuild processes items in stable order:
  `semantic_time ASC, item_id ASC`
- rebuild runs the same discovery, normalization, and reducer logic as incremental intake
- rebuild produces a full replacement current projection for exactly that cutoff
- rebuild acquires the same memory-scoped projector lease used by incremental publication
- successful rebuild must ensure that every authoritative item inside the cutoff has a canonical outbox row
- successful rebuild must finalize every unresolved outbox row inside the cutoff as `COMPLETED` with `completion_mode = COVERED_BY_REBUILD`
- successful rebuild publishes one new visible projection boundary and updates `servable_through_intake_seq` to the greatest intake row covered by that cutoff
- items committed after the cutoff are explicitly out of scope for that rebuild run and remain eligible for later incremental intake

Replacement projection includes:

- `memory_thread`
- `memory_thread_event`
- `memory_thread_membership`
- `memory_thread_anchor`

Rebuild must repair:

1. missing intake rows
2. failed intake rows
3. inconsistent or stale thread projection

## 24. Serving During Rebuild

V1 rebuild must be query-safe and simple.

Rules:

- rebuild is exclusive per `memoryId`
- rebuild computes replacement state in scratch storage or equivalent isolated state
- additive-gap rebuild may continue serving the previously published projection during rebuild execution
- `REBUILD_REQUIRED` rebuild must not serve invalidated projection state during rebuild execution
- cutover replaces the current projection atomically at `memoryId` scope
- cutover must atomically include any outbox finalization needed to mark cutoff-covered rows as `COMPLETED/COVERED_BY_REBUILD`
- after cutover, all current thread queries see the new projection together
- source facts committed after rebuild start are excluded from that rebuild and must remain pending for later incremental processing or a later rebuild

V1 does not allow query-visible half-cutover states inside one `memoryId`.

### 24.1 Required thread store contract

The current lightweight `MemoryThreadOperations` contract is not sufficient for V1.

V1 requires a thread projection store that can:

- idempotently upsert current incremental state by `thread_key`, membership uniqueness, active anchor uniqueness, and `event_key`
- build replacement projection state in isolated scratch storage for one `memoryId`
- atomically replace the current projection for one `memoryId`
- atomically finalize rebuild-covered outbox rows in the same cutover boundary
- maintain one memory-scoped publication boundary for the visible projection
- coordinate the memory-scoped projector lease across incremental publication and rebuild

The implementation may use staging tables, copy-on-write rows, or transaction-scoped replace, but readers must never observe mixed old and new projection state inside one `memoryId`.

### 24.2 Required thread source reader contract

The current generic `GraphOperations` interface is not sufficient as a correctness contract for V1 rebuild determinism.

V1 requires a thread source reader that can supply:

- active items for one `memoryId`
- cutoff-bounded item semantic time
- cutoff-bounded item-linked graph primitives
- cutoff-bounded persisted thread semantic markers

Thread correctness must be defined against that source reader, not against mutable global graph aggregates.

## 25. Core Invariants

Thread V1 intentionally keeps a small set of strict invariants.

- `thread_key` is unique within one `memoryId`
- active exact anchor ownership is unique per `(memory_id, anchor_kind, anchor_key)`
- canonical anchor is immutable within one published thread identity
- membership uniqueness is `(memory_id, thread_id, item_id)`
- event uniqueness is `(memory_id, thread_id, event_key)`
- `event_seq` is unique and monotonic inside one thread
- outbox uniqueness is `(memory_id, intake_seq)` and `(memory_id, trigger_item_id)`
- published projection is either unavailable due to `REBUILD_REQUIRED`, or exactly reflects source facts up to `servable_through_intake_seq`
- `memory_thread.last_event_at` matches the reducer result from current thread events
- `memory_thread.object_state`, `lifecycle_status`, and `snapshot_json` come from one structural reducer output
- repeated rebuild over unchanged source facts and the same cutoff yields identical thread identity, anchors, memberships, normalized events, event ordering, and structured snapshot
- successful incremental materialization is guaranteed rebuild-equivalent only over the same published prefix or the same rebuild cutoff

## 26. Current Codebase Implications

This V1 design intentionally aligns with the current memind architecture:

- thread derivation already runs after item extraction and commit rather than inside the source-fact transaction boundary
- the existing scheduler already supports full rebuild from persisted items
- the current thread schema is small and simple

But the current codebase must change in important ways:

- remove single-thread-per-item in both in-memory and store-backed implementations
- replace the current lightweight snapshot-only thread model with event-centric thread state
- introduce deterministic anchor canonicalization
- make canonical anchor immutable within one published thread identity
- extend source-fact extraction to persist thread-relevant semantic markers
- introduce outbox claim and finalization semantics suitable for multi-instance deployment
- add source-retraction handling that marks thread projection `REBUILD_REQUIRED`
- replace the current `MemoryThreadOperations` contract with a projection store that supports scratch build and atomic memory-scoped cutover
- add a thread-specific source reader over active items plus cutoff-consistent graph primitives
- make rebuild operate against an explicit source cutoff rather than an unfenced full scan
- preserve thread as optional experimental capability that depends on committed `item + graph`

## 27. Verification Requirements

V1 is not complete unless the following are demonstrated by automated verification.

### 27.1 Many-to-many membership

One item can belong to multiple threads and the association survives rebuild.

### 27.2 Published-prefix and rebuild equivalence

Given the same authoritative `item + graph` inputs:

- a published incremental prefix
- a rebuild over the equivalent source cutoff

must produce equivalent thread state.

### 27.3 Missing outbox compensation

If source facts exist but outbox rows are missing:

- `backfillMissingOutbox(memoryId)` must recover them, or
- `rebuild(memoryId)` must converge to the correct result without them

### 27.4 Failed intake tolerance

If one intake fails repeatedly and becomes `FAILED`:

- visible projection must remain pinned at the last published prefix
- later rows must not become query-visible above the failed prefix
- runtime status must report that publication is blocked
- rebuild must recover final correctness

### 27.5 Source retraction handling

If an item is deleted, invalidated, or has thread-relevant source facts retracted:

- thread runtime state must become `REBUILD_REQUIRED`
- thread queries must not serve invalidated projection state
- rebuild must converge to the correct replacement projection

### 27.6 Rebuild cutoff under concurrent writes

If new items are committed while rebuild is running:

- the rebuild result must include only items at or below its captured cutoff
- post-start items must remain pending after cutover
- later incremental processing or a later rebuild must incorporate them without corrupting current projection state

### 27.7 Idempotent event and membership application

Repeated processing of the same trigger item, historical backfill, or rebuild must not create duplicate:

- outbox rows
- thread events
- memberships

### 27.8 Deterministic identity

Identical authoritative inputs must produce identical:

- `thread_key`
- active anchors
- membership sets
- normalized structural snapshot

### 27.9 Dyadic relationship discipline

Representative `RELATIONSHIP` samples must:

- produce one canonical dyadic anchor for the same participant pair
- refuse to create a relationship thread from non-dyadic group evidence alone

### 27.10 Graph cutoff determinism

The same rebuild cutoff must produce the same result even if later committed items have changed global graph aggregates outside that cutoff.

### 27.11 Event quality

Representative `WORK`, `CASE`, `RELATIONSHIP`, and `TOPIC` samples must produce meaningful normalized events rather than raw item copies.

### 27.12 Snapshot correctness without text enrich

If text enrich fails or is disabled:

- structural snapshot remains complete
- lifecycle and object-state queries remain correct

## 28. Delivery Phases

### Phase 1

- new core schema
- outbox claim model
- published-prefix runtime status
- many-to-many membership support
- deterministic anchor canonicalization
- immutable canonical anchor policy
- minimal persisted thread semantic markers
- event normalization skeleton
- structural reducer
- source-retraction to rebuild-required handling
- thread source reader over cutoff-consistent graph primitives
- rebuild path with cutoff and atomic cutover

### Phase 2

- richer type-aware object discovery
- bounded historical backfill on create
- better event normalization coverage
- rule-based text enrichment

### Phase 3

- admin surface
- diagnostics
- deterministic verification suite

### Later Hardening

If thread proves valuable, later phases may consider:

- manual repair surfaces
- merge and split
- anchor rekey
- redirected historical identity
- retained generation audit
- stronger worker fencing
- thread-aware retrieval integration

Those belong to a later hardening track, not V1.

## 29. Final Recommendation

Adopt this simplified V1 as the canonical thread-core direction.

It keeps the product-defining parts:

- object identity
- evolution timeline
- current snapshot
- rebuildability

It deliberately removes the premature infrastructure burden:

- quarantine
- recovery directives
- generation headers
- redirect chains
- current-membership mirror tables
- event-source side tables

This produces a thread subsystem that is strict enough for an open-source core design, but still optimized for what users will actually perceive:

- better object tracking
- better progress awareness
- better state explanation
- better narrative continuity across time
