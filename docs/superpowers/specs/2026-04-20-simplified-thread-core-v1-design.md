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

Tracks durable relation objects between two or more stable actors.

Expected semantics:

- collaboration patterns
- conflict and repair
- role shifts
- continuity across time

The object is the relationship itself, not either participant alone.

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
- preserve thread-local event ordering
- preserve the reducer input needed to rebuild the current snapshot

Recommended fields:

- `id`
- `memory_id`
- `thread_id`
- `event_seq`
- `event_time`
- `event_type`
- `event_payload_json`
- `is_meaningful`
- `confidence`
- `created_at`

`event_payload_json` includes embedded source references. V1 does not require a separate `event_source` table.

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

This table stores canonical anchor bindings and simple historical anchor retention.

Required responsibilities:

- resolve the currently active canonical anchor for a thread
- prevent duplicate active ownership of the same exact anchor
- retain historical anchors after replacement

Recommended fields:

- `id`
- `memory_id`
- `thread_id`
- `anchor_kind`
- `anchor_key`
- `status`
- `bound_at`
- `retired_at`
- `created_at`

V1 anchor statuses:

- `ACTIVE`
- `RETIRED`

V1 does not support redirected bindings or successor resolution.

Required uniqueness rule:

- `UNIQUE(memory_id, anchor_kind, anchor_key) WHERE status = 'ACTIVE'`

### 8.5 `thread_intake_outbox`

This table stores pending and finalized asynchronous intake work.

Required responsibilities:

- represent outstanding thread intake work
- support retryable asynchronous processing
- support multi-instance worker claim safety
- support operational diagnosis

Recommended fields:

- `id`
- `memory_id`
- `intake_seq`
- `trigger_item_id`
- `status`
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

## 10. Intake and Compensation Model

### 10.1 Enqueue after source commit

After a successful `item + graph` commit, the system writes one or more `thread_intake_outbox` rows for newly committed items.

The outbox row does not need to be written in the same database transaction as `item + graph`.

### 10.2 Missing outbox compensation

Because outbox write is not part of the source-fact transaction boundary, V1 requires an explicit compensation mechanism.

The system must provide:

- `backfillMissingOutbox(memoryId)`

This operation scans committed items and existing outbox rows and inserts missing intake rows.

### 10.3 Rebuild covers the same gap

`rebuild(memoryId)` must also repair the case where an item exists in authoritative storage but never entered the thread intake pipeline.

## 11. Intake Ordering and Worker Claim Rules

V1 uses a minimal but strict claim model.

Rules:

- at most one worker may claim one `PENDING` outbox row at a time
- `PROCESSING` rows are protected by `lease_token + leased_at`
- stale leases may be reclaimed after timeout
- intake rows are allocated with increasing `intake_seq` per `memoryId`
- workers should prefer lower `intake_seq` rows first
- `FAILED` rows do not block later rows forever

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
9. finalize the outbox row as `COMPLETED` or `FAILED`

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
    Map<String, Object> semanticHints
) {}
```

Required properties:

- deterministic from committed `item + graph` facts
- no online LLM dependency
- stable enough for rebuild equivalence

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
- `CASE`: strong `anchorability` and clear problem or resolution semantics
- `RELATIONSHIP`: high `continuity` and stable multi-actor evidence
- `TOPIC`: high `continuity` and repeated cumulative evidence

If an item does not satisfy the create gate and does not attach to an existing thread, the correct V1 decision is `IGNORE`.

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

Fallback anchor:

- normalized problem-subject plus symptom key

Examples:

- `issue:bug-123`
- `case:legacy-session-incompatibility`

### 16.3 `RELATIONSHIP` anchors

Preferred anchor kind:

- normalized participant pair

Example:

- `relationship:person:user|person:zhangsan`

Participant ordering must be canonicalized so the same pair cannot produce two threads.

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

Rule:

- same `thread_type + canonical_anchor` must produce the same `thread_key`

This allows rebuild to preserve thread identity without introducing generation headers, redirected bindings, or explicit lineage state.

Because V1 does not support anchor rekey, a changed canonical anchor is treated as a different recognized thread identity.

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
- event normalization is thread-scoped semantic interpretation
- event source references are embedded in payload
- `event_seq` is unique only inside one thread
- V1 does not require `event_seq` stability across rebuilds

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

V1 exposes only current projection queries.

Required APIs:

- `getThread(memoryId, threadId | threadKey)`
- `listThreads(memoryId, filters, paging)`
- `listThreadEvents(memoryId, threadId, paging)`
- `listThreadMembers(memoryId, threadId, paging)`
- `listThreadsByItem(memoryId, itemId)`
- `resolveThreadByAnchor(memoryId, anchorKind, anchorKey)`

Query rules:

- queries read current projection only
- queries do not read outbox rows as user-facing state
- `resolveThreadByAnchor` resolves only `ACTIVE` anchors
- V1 does not expose redirected or successor identity semantics

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

## 23. Rebuild Contract

`rebuild(memoryId)` is the authoritative repair path.

Rules:

- rebuild reads only authoritative `item + graph`
- rebuild does not use previous thread projection as semantic input
- rebuild processes items in stable order:
  `semantic_time ASC, item_id ASC`
- rebuild runs the same discovery, normalization, and reducer logic as incremental intake
- rebuild produces a full replacement current projection

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
- queries continue to read the old projection during rebuild execution
- cutover replaces the current projection atomically at `memoryId` scope
- after cutover, all current thread queries see the new projection together

V1 does not allow query-visible half-cutover states inside one `memoryId`.

## 25. Core Invariants

Thread V1 intentionally keeps a small set of strict invariants.

- `thread_key` is unique within one `memoryId`
- active exact anchor ownership is unique per `(memory_id, anchor_kind, anchor_key)`
- membership uniqueness is `(memory_id, thread_id, item_id)`
- `event_seq` is unique and monotonic inside one thread
- `memory_thread.last_event_at` matches the reducer result from current thread events
- `memory_thread.object_state`, `lifecycle_status`, and `snapshot_json` come from one structural reducer output
- repeated rebuild over unchanged source facts yields identical thread identity, anchors, memberships, normalized events, and structured snapshot
- successful incremental intake over a fixed source-fact set is equivalent to full rebuild over the same set

## 26. Current Codebase Implications

This V1 design intentionally aligns with the current memind architecture:

- thread derivation already runs after item extraction and commit rather than inside the source-fact transaction boundary
- the existing scheduler already supports full rebuild from persisted items
- the current thread schema is small and simple

But the current codebase must change in important ways:

- remove single-thread-per-item in both in-memory and store-backed implementations
- replace the current lightweight snapshot-only thread model with event-centric thread state
- introduce deterministic anchor canonicalization
- introduce outbox claim and finalization semantics suitable for multi-instance deployment
- preserve thread as optional experimental capability that depends on committed `item + graph`

## 27. Verification Requirements

V1 is not complete unless the following are demonstrated by automated verification.

### 27.1 Many-to-many membership

One item can belong to multiple threads and the association survives rebuild.

### 27.2 Incremental and rebuild equivalence

Given the same authoritative `item + graph` inputs:

- successful incremental intake
- full rebuild

must produce equivalent thread state.

### 27.3 Missing outbox compensation

If source facts exist but outbox rows are missing:

- `backfillMissingOutbox(memoryId)` must recover them, or
- `rebuild(memoryId)` must converge to the correct result without them

### 27.4 Failed intake tolerance

If one intake fails repeatedly and becomes `FAILED`:

- later intake work may still proceed
- rebuild must recover final correctness

### 27.5 Deterministic identity

Identical authoritative inputs must produce identical:

- `thread_key`
- active anchors
- membership sets
- normalized structural snapshot

### 27.6 Event quality

Representative `WORK`, `CASE`, `RELATIONSHIP`, and `TOPIC` samples must produce meaningful normalized events rather than raw item copies.

### 27.7 Snapshot correctness without text enrich

If text enrich fails or is disabled:

- structural snapshot remains complete
- lifecycle and object-state queries remain correct

## 28. Delivery Phases

### Phase 1

- new core schema
- outbox claim model
- many-to-many membership support
- deterministic anchor canonicalization
- event normalization skeleton
- structural reducer
- rebuild path

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
