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

### 4.4 No durable latent-candidate subsystem in v1

Persistent `memory_thread_candidate` storage is explicitly postponed. V1 can still compute anchor candidates and decide `attach / create / ignore`, but it does not persist weak thread candidates as first-class rows.

### 4.5 No LLM-only write-path decisions

Thread creation and attachment must not depend on opaque end-to-end model output. LLM assistance may help normalization or labeling later, but core decisions must remain evidence-driven and deterministic.

## 5. Design Principles

### 5.1 Thread tracks durable objects, not just related text

A formal thread exists only when the system can reasonably say there is a persistent object worth tracking across time.

### 5.2 Identity, timeline, and snapshot are all required

If a structure has only identity, it is just an object card. If it has only history, it is just a log. If it has only current summary, it is just a cache. Thread requires all three.

### 5.3 Core is shared, specialization is layered

Broad thread types share the same core schema and lifecycle. Type-specific behavior lives in reducers, facets, and policies, not separate object systems.

### 5.4 Events are source-of-truth; snapshot is a reduction

Thread history is append-only. Snapshot is the current reduced view computed from events and memberships. Snapshot may be rebuilt from event history.

### 5.5 Membership is many-to-many

Items may contribute to multiple threads. The system may mark one primary membership per item, but it must not force exclusive ownership.

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

Every formal thread consists of six required parts plus optional typed facets.

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
- `anchorKind + anchorKey` expresses the canonical object anchor the thread tracks.
- `displayLabel` is the current best human-facing label.
- `normalizedAliases[]` stores stable alternate labels for matching and display.
- `anchorConfidence` expresses how strongly the system believes the anchor is canonical.
- `threadKey` remains stable even if anchor normalization or display labeling later improves.

### 9.2 Lifecycle

Lifecycle answers whether the thread is actively tracked.

Required fields:

- `lifecycleStatus`
- `openedAt`
- `lastEventAt`
- `lastMeaningfulUpdateAt`
- `closedAt`

Canonical lifecycle states:

- `ACTIVE`
- `DORMANT`
- `CLOSED`

Lifecycle rules:

- new formal threads start as `ACTIVE`
- lack of meaningful updates may transition `ACTIVE -> DORMANT`
- resolution or explicit closure may transition `ACTIVE|DORMANT -> CLOSED`
- a qualifying new update may transition `DORMANT|CLOSED -> ACTIVE` and append `REOPENED`

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

### 9.4 Timeline

Timeline is the append-only sequence of normalized thread events.

Properties:

- ordered by per-thread sequence and effective event time
- derived from items and graph evidence, but not equal to them
- durable source-of-truth for thread evolution

### 9.5 Members

Members are the items and other evidence-bearing objects that support the thread.

Properties:

- item membership is many-to-many
- one item may have at most one primary membership
- membership does not replace event source links; event source links explain why a specific event exists

### 9.6 Snapshot

Snapshot is the current reduced view of the thread.

Properties:

- rebuilt deterministically from thread events plus memberships
- structured first, textual second
- optimized for fast query and future consumption

### 9.7 Facets

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

Within one `memoryId`, there must be at most one formal thread for a given canonical `anchorKind + anchorKey`.

Implications:

- the system should reopen the existing thread rather than create a duplicate when new evidence arrives
- anchor normalization quality matters because anchor identity drives thread continuity

### 11.4 Anchor is object-level, not entity-level

Anchor does not mean "the most related entity". It means "the durable object this thread is about".

Examples:

- a bug case thread may be anchored by issue identity, not just a service name
- a relationship thread may be anchored by a canonical pair, not by one person entity alone
- a topic thread may be anchored by the normalized topic, not by the latest mentioning item

## 12. Persistence Model

V1 persists five required thread-core objects.

In addition, the design requires one non-core infrastructure object:

- `thread_intake_outbox`

`thread_intake_outbox` is required for reliability, but it is not part of the formal thread object model itself.

### 12.1 `memory_thread`

This is the thread root row.

Required responsibilities:

- identity and type
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
- `snapshot_version`
- `created_at`
- `updated_at`

Invariants:

- unique `(memory_id, thread_key)`
- unique `(memory_id, anchor_kind, anchor_key)`

### 12.2 `memory_thread_membership`

This replaces the old `memory_thread_item` semantics.

Required responsibilities:

- item-to-thread membership
- primary vs secondary membership
- membership role and relevance

Recommended fields:

- `biz_id`
- `memory_id`
- `thread_id`
- `item_id`
- `membership_role`
- `is_primary`
- `relevance_weight`
- `contribution_tags_json`
- `created_at`
- `updated_at`

Canonical membership roles:

- `PRIMARY`
- `SUPPORTING`
- `REFERENCE`
- `CONTRADICTING`

Invariants:

- unique `(memory_id, thread_id, item_id)`
- at most one `is_primary = true` membership for a given `item_id` in one `memory_id`

### 12.3 `memory_thread_event`

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
- `event_seq`
- `event_time`
- `event_type`
- `event_payload_json`
- `is_meaningful`
- `confidence`
- `created_at`

Invariants:

- unique `(memory_id, thread_id, event_seq)`
- append-only semantics

### 12.4 `memory_thread_event_source`

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
- `source_role`
- `confidence`
- `created_at`

Canonical source types:

- `ITEM`
- `GRAPH_NODE`
- `GRAPH_EDGE`
- `OBSERVATION`

Canonical source roles:

- `TRIGGER`
- `SUPPORT`
- `ANCHOR_EVIDENCE`
- `CONTRADICTION`

### 12.5 `memory_thread_snapshot`

This stores the current reduced thread state.

Required responsibilities:

- serve fast thread reads
- store structured current state
- store optional canonical text for later consumers

Recommended fields:

- `thread_id`
- `memory_id`
- `snapshot_version`
- `reduced_from_event_seq`
- `snapshot_payload_json`
- `snapshot_text`
- `updated_at`

Invariants:

- exactly one current snapshot row per thread
- snapshot version must match or trail the current thread reducer version

## 13. Objects Explicitly Deferred

The following objects are intentionally deferred beyond v1:

- `memory_thread_link`
- `memory_thread_candidate`
- `memory_thread_watch`

Reasoning:

- `thread_link` is valuable, but thread-to-thread semantics can remain out of core scope initially
- `thread_candidate` is useful for latent accumulation, but rebuild plus history-aware materialization is enough for v1
- `thread_watch` belongs to subscription and proactive workflows, not core object tracking

## 14. Intake and Materialization Pipeline

V1 uses synchronous source-fact commit plus asynchronous thread materialization.

### 14.1 Synchronous path

The main write path persists:

- raw input and normalized item data
- extraction result
- graph facts
- `thread_intake_outbox` record

The synchronous path does not directly create or mutate formal thread state.

### 14.2 Durable outbox

Every item commit that could affect threads must persist a durable outbox record containing at least:

- `memoryId`
- `itemId`
- item timestamp
- normalized thread-intake hints
- enqueue time

Outbox is required so thread work is retryable and rebuildable.

It is infrastructure, not thread domain state:

- it must be persisted durably
- it must survive process restart
- it must be safe to replay
- it must not be treated as a formal thread object

### 14.3 Asynchronous thread worker

The thread worker processes outbox entries per `memoryId`.

Worker stages:

1. load pending outbox entry and current thread state
2. extract `ThreadIntakeSignal`
3. generate anchor candidates
4. decide `attach / reopen / create / ignore`
5. normalize one or more thread events
6. update thread root row
7. upsert memberships
8. append thread events and event-source links
9. reduce and write snapshot
10. mark outbox entry complete

### 14.4 Thread-core transaction boundary

Within the thread worker, the following writes must commit atomically:

- `memory_thread`
- `memory_thread_membership`
- `memory_thread_event`
- `memory_thread_event_source`
- `memory_thread_snapshot`

If snapshot reduction fails, the thread-core transaction fails and the outbox entry remains retryable.

### 14.5 Rebuild path

The subsystem must support full rebuild per `memoryId`.

Rebuild semantics:

- clear or replace existing thread-core state for the memory
- rescan committed items and graph evidence in stable order
- rerun the same thread materialization logic

This replaces reliance on legacy thread rows for correctness.

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

### 15.3 Decision outputs

The decision stage must return exactly one of:

- `ATTACH_EXISTING`
- `REOPEN_EXISTING`
- `CREATE_NEW`
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
2. If the chosen existing thread is `DORMANT` or `CLOSED` and the new signal restores continuity, prefer `REOPEN_EXISTING`.
3. If no existing thread is strong enough and `anchorability + continuity + statefulness` all exceed creation threshold, use `CREATE_NEW`.
4. Otherwise use `IGNORE`.

### 15.6 No persisted latent candidate in v1

When a signal is promising but below creation threshold, v1 does not persist a `thread_candidate` row.

Instead:

- the item remains available to graph and later rebuild
- future items may cause creation during later incremental materialization or full rebuild

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

### 16.2 Event normalization rules

Rules:

- one item may generate zero, one, or multiple thread events
- one thread event may cite multiple evidence sources
- event payload must be structured JSON, not only summary text
- event type is reducer-critical and must not be hidden only inside metadata

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
- `latestUpdate`
- `activeBlockers[]`
- `nextActions[]`
- `resolutionState`
- `openQuestions[]`
- `lastMeaningfulUpdateAt`
- `confidence`

### 17.2 Snapshot reduction contract

The reducer must be deterministic over:

- ordered thread events
- current memberships
- thread type and reducer policy

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

### 18.1 Why membership exists separately from event source

Event source explains why a particular event exists.

Membership answers whether an item belongs in the broader case file or object context.

An item may:

- be a member without being the main trigger of the latest event
- trigger an event without being the only member that matters to the thread

### 18.2 Primary membership rule

Each item may have at most one primary thread membership per memory.

Purpose:

- preserve a strongest owner-like association when one exists
- still allow rich secondary memberships across related threads

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
- `resolveThreadByAnchor(memoryId, anchorKind, anchorKey)`

### 19.2 Minimum list filters

Thread lists should filter by:

- `threadType`
- `lifecycleStatus`
- `objectState`
- anchor label or alias
- updated time range

### 19.3 Return-shape requirement

The default thread detail response should include:

- thread identity
- lifecycle and coarse state
- current snapshot
- summary counts for events and members

Timeline and member rows should be queried separately.

## 20. Consistency, Failure Handling, and Operations

### 20.1 Source-fact priority

Item and graph commits remain the first durable fact layer. Thread materialization failure must never lose those source facts.

### 20.2 Retry model

If thread materialization fails:

- the outbox entry remains pending
- no partial thread-core transaction is committed
- the worker may retry safely

### 20.3 Idempotency requirement

Materialization must be idempotent for repeated outbox processing and full rebuild.

Required techniques include:

- deterministic anchor normalization
- stable event normalization
- uniqueness constraints for memberships and event sequence allocation

### 20.4 Operational controls

The subsystem should expose at least:

- outbox backlog size
- oldest pending outbox age
- attach/create/ignore counts
- ambiguous decision rate
- snapshot reduction failures
- rebuild success and failure counts

## 21. Testing Strategy

The thread core must be tested as a stateful subsystem, not just as row writes.

### 21.1 Unit tests

Required unit coverage:

- anchor normalization
- intake signal extraction
- attach/create/ignore decision logic
- event normalization
- snapshot reduction
- lifecycle transitions

### 21.2 Store tests

Required store coverage:

- thread uniqueness invariants
- many-to-many membership behavior
- primary membership constraint
- append-only event semantics
- snapshot replacement semantics

### 21.3 Integration tests

Required integration coverage:

- item commit writes durable outbox
- worker materializes thread core from outbox
- rebuild reproduces expected thread state
- dormant and reopen scenarios
- multi-thread membership scenarios

### 21.4 Behavioral fixtures

V1 should include representative fixtures for:

- project/work tracking
- issue/case tracking
- relationship evolution
- long-running topic accumulation

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
4. Add `memory_thread_event`, `memory_thread_event_source`, and `memory_thread_snapshot`.
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
- thread query contract is available
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
