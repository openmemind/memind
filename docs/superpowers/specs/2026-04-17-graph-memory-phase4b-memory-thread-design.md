# Graph Memory Phase 4B MemoryThread Design

Date: 2026-04-17

## Status

Proposed design. Derived from the approved phase 4B direction after phase 4A completion. Pending final user review before implementation planning.

## Summary

This document defines phase 4B of `memind` graph memory: the `MemoryThread` derived episodic memory layer plus bounded coarse-to-fine retrieval over memory threads.

The design completes the second half of the original phase 4 roadmap:

- derived thread/story storage and derivation
- coarse-to-fine retrieval over story threads

For this project, the higher-order process layer is named `MemoryThread` rather than `StoryThread` or `EpisodeThread`.

`MemoryThread` is a derived episodic memory layer built on top of the typed item graph. It groups multiple `MemoryItem`s into one coherent ongoing process line such as:

- breakup recovery
- relationship repair
- job transition
- sleep adjustment
- recurring incident handling
- project execution

Phase 4B includes:

- `MemoryThread` persistence
- asynchronous thread derivation and rebuild
- retrieval-time memory-thread recall and narrowing
- runtime config, projection, and codec support
- server/admin inspection and rebuild controls

Phase 4B does **not** replace:

- `MemoryItem` as atomic truth
- typed item graph as structural truth
- `rawdata caption` as raw-content description
- insight tree as the abstraction structure

The central architectural rule is:

- typed item graph stores bounded local relations between items
- `MemoryThread` stores higher-order episodic organization derived from those relations

## Goals

- Add a derived episodic organization layer without destabilizing the existing `RawData -> MemoryItem -> Insight` architecture.
- Represent continuous processes that span multiple `MemoryItem`s while keeping `MemoryItem` as the only atomic fact anchor.
- Improve retrieval by inserting a bounded episode-level narrowing step before typed graph expansion and rerank.
- Keep the feature optional, asynchronously rebuildable, and safe under partial population.
- Preserve the separation between raw descriptions, structural graph relations, episodic grouping, and abstraction.
- Support companionship-chat use cases where long-running life processes matter more than isolated facts.
- Provide server/admin visibility and rebuild control so the derived layer remains inspectable and operationally trustworthy.

## Non-Goals

- Replacing `MemoryItem` with thread-level primary objects.
- Replacing typed item graph traversal with thread-only retrieval.
- Treating `MemoryThread` as authoritative temporal or causal truth.
- Using raw caption text as direct thread membership truth.
- Using LLM output to decide thread membership truth.
- Replacing insight leaf/branch/root generation with thread summaries.
- Making `MemoryThread` mandatory for all deployments.
- Adding global-first thread search as the primary retrieval backbone.

## Existing Context

After phase 4A, `memind` already has:

- `MemoryItem` as the atomic memory unit
- typed item graph with semantic, temporal, causal, and entity-based expansion support
- graph-assisted retrieval in both simple and deep retrieval paths
- insight-tree graph assistance

What is still missing is an explicit process layer.

Today, the system can answer:

- which items are semantically related
- which items are temporally adjacent
- which items may form a causal chain
- which items share central entities

But it still lacks a first-class answer to:

- which items belong to the same ongoing process
- which items should be recalled together as one ongoing memory thread, issue line, or project line
- which bounded process should narrow retrieval before graph expansion fans out locally

That gap is especially visible in companionship chat. Users often ask questions such as:

- "Am I doing better than before?"
- "Is this the same issue that has been bothering me for weeks?"
- "What happened after that conversation with my mom?"
- "How has this job transition been going?"

Those questions are not best served by:

- one raw caption
- one atomic item
- one-hop graph neighbors
- one abstraction summary alone

They require a derived process-level representation.

## Phase Boundary

The original phase 4 roadmap was defined as:

- graph support in deep retrieval
- derived thread/story storage and derivation
- coarse-to-fine retrieval over story threads

Phase 4A already delivered the first item.

This document covers the remaining two items as one phase:

- `MemoryThread` storage and derivation
- coarse-to-fine retrieval over `MemoryThread`

This phase therefore starts after the typed item graph and phase 4A deep graph assist are already available.

## Naming Decision

The higher-order process layer is named `MemoryThread`.

This name is preferred over `StoryThread` and `EpisodeThread` because:

- it aligns better with the existing `MemoryItem` and `MemoryInsight` naming family
- it is more neutral and less likely to imply narrative fabrication
- it fits companionship chat, project workflows, and incident tracking equally well
- it does not overlap as strongly with caption-like or summary-like language
- it emphasizes continuity without claiming abstraction ownership

To avoid the name becoming too broad, this document fixes one semantic clarification:

- `MemoryThread` specifically means a derived episodic process line across multiple `MemoryItem`s
- it is not a conversation thread
- it is not an execution thread
- it is not a generic grouping bucket

The corresponding membership object is named `MemoryThreadItem`.

## Approach Comparison

### Recommended: derived episodic layer with rule-driven membership and seeded retrieval

The recommended design is:

1. persist a derived `MemoryThread` layer
2. derive thread membership asynchronously from item-level structure
3. keep membership truth rule-driven and deterministic
4. use direct item recall to discover candidate threads at query time
5. narrow retrieval to bounded thread members before typed graph expansion and rerank

This is the correct design because it:

- preserves item-centric retrieval as the backbone
- introduces process memory without replacing the typed graph
- keeps derivation explainable and rebuildable
- avoids turning thread retrieval into a second independent search system

### Rejected: lightweight tag/group layer with no retrieval narrowing

This would persist only loose item groups and use them for admin display, but not for actual retrieval-time narrowing.

This is rejected because it would:

- underdeliver on the main value of process memory
- behave more like item grouping metadata than a true episodic layer
- not justify the added storage and derivation complexity

### Rejected: LLM-driven thread membership truth

This would let LLM calls decide attach/create/merge/split outcomes directly.

This is rejected because it would:

- weaken determinism and rebuildability
- make degraded mode less trustworthy
- blur the boundary between episodic grouping and abstraction
- create high operational and cost variability for a core derived structure

### Rejected: global thread-first retrieval

This would search memory threads first and treat item recall as a secondary step.

This is rejected because it would:

- invert the direct-recall-first principle
- depend too heavily on complete and highly accurate thread coverage
- make failure or lag in derivation disproportionately harmful

## Design Principles

### 1. `MemoryItem` remains the only atomic source of truth

`MemoryThread` may group items, but it does not replace or supersede item-level truth.

### 2. typed item graph remains the structural truth layer

Temporal, causal, semantic, and entity-based relations continue to live in the typed item graph. `MemoryThread` only consumes these signals.

### 3. `MemoryThread` is derived and rebuildable

Thread data must always be safe to regenerate from persisted items and graph state.

### 4. Retrieval remains item-centered

The final retrieval result remains item-centered. `MemoryThread` is an intermediate narrowing layer, not the final retrieval object.

### 5. Membership truth is rule-driven

LLM may assist only with display text such as `title` and `summarySnapshot`, never with authoritative membership truth.

### 6. Conservative defaults are mandatory

False split is safer than false merge. The system must prefer leaving some items unthreaded over forcing them into weak or noisy threads.

### 7. Episode work must degrade gracefully

Thread derivation or retrieval failure must never break item writes, graph retrieval, or existing retrieval behavior.

## Target Architecture

When graph memory is enabled, the architecture becomes:

- `RawData -> MemoryItem`
- `MemoryItem -> Typed Item Graph`
- `Typed Item Graph -> Insight Signals`
- `Typed Item Graph -> Graph Retrieval Channel`
- `Typed Item Graph -> MemoryThread Layer`
- `Insight Signals + MemoryItem -> Insight Tree`

This preserves clear boundaries:

- `RawData` captures the source
- `MemoryItem` captures atomic memory facts
- typed item graph captures local structure
- `MemoryThread` captures process continuity
- insight tree captures abstraction and summarization

## Layer Boundaries

### `MemoryThread` vs rawdata caption

`rawdata caption` answers:

- what one raw segment is about

`MemoryThread` answers:

- which items belong to the same ongoing process

Required rules:

- raw caption text must not directly determine thread membership truth
- raw segment boundaries are weak hints only
- one raw segment may contribute items to multiple threads
- one thread may span many raw segments and many captions

### `MemoryThread` vs typed item graph

typed item graph answers:

- which items are locally related by semantic, temporal, causal, or entity signals

`MemoryThread` answers:

- which items together form one coherent process line

Required rules:

- typed item graph remains the structural truth layer
- `MemoryThread` is derived from the graph plus item metadata
- `MemoryThread` must not rewrite authoritative temporal or causal truth back into the graph
- `MemoryThread` may store `sequenceHint`, but not a second authoritative temporal graph

### `MemoryThread` vs insight tree

insight tree answers:

- what abstractions or conclusions can be drawn from groups of items

`MemoryThread` answers:

- what process line is unfolding across multiple items

Required rules:

- thread membership is not an insight
- thread title and summary are not replacements for leaf/branch/root summaries
- insight tree remains the abstraction layer
- `MemoryThread` remains the episodic continuity layer

## Data Model

### `MemoryThread`

Suggested fields:

- `id`
- `memory_id`
- `thread_key`
- `episode_type`
- `title`
- `summary_snapshot`
- `status`
- `confidence`
- `start_at`
- `end_at`
- `last_activity_at`
- `origin_item_id`
- `anchor_item_id`
- `display_order_hint`
- `metadata`
- `created_at`
- `updated_at`
- `deleted`

Field intent:

- `memory_id`: thread identity and derivation scope; phase 4B threads never cross memory boundaries
- `thread_key`: stable business identity used for deterministic rebuild and upsert
- `episode_type`: coarse process category
- `title`: short display name, generated by rules by default
- `summary_snapshot`: lightweight process description, not an insight summary
- `status`: lifecycle state of the process line
- `confidence`: confidence that the derived thread is coherent
- `start_at` and `end_at`: time envelope of the line
- `last_activity_at`: latest member activity time
- `origin_item_id`: immutable origin anchor used to derive stable thread identity in v1
- `anchor_item_id`: representative member for retrieval/debug/display
- `display_order_hint`: stable browsing order aid

Identity contract:

- `MemoryThread` identity is scoped per `memory_id`
- `id` may be an internal storage identifier, but phase 4B must not rely on it as rebuild identity
- `thread_key` is the stable per-memory business identity and must be unique under `(memory_id, thread_key)`
- v1 should derive `thread_key` deterministically from `origin_item_id`, for example `ep:<origin_item_id>`
- `origin_item_id` is the immutable earliest member selected when the thread is created under deterministic derivation order
- `anchor_item_id` is mutable and exists for retrieval and display quality only; it is not the thread's identity
- rebuild and refresh must upsert by `(memory_id, thread_key)` rather than generating fresh random thread identities

This contract intentionally gives the project stable thread identity under the same persisted data and config. If historical backfill or a legitimate split or merge changes the underlying earliest-member structure, a thread identity change is allowed because the derived process itself has materially changed.

### `MemoryThreadItem`

Suggested fields:

- `id`
- `memory_id`
- `thread_id`
- `item_id`
- `membership_weight`
- `role`
- `sequence_hint`
- `joined_at`
- `metadata`
- `created_at`
- `updated_at`
- `deleted`

Field intent:

- `membership_weight`: confidence that the item belongs to the thread
- `role`: local role within the thread only
- `sequence_hint`: approximate process order within the thread
- `joined_at`: time when the derivation layer attached the item to the thread

Storage invariants:

- `MemoryThreadItem` identity must be unique on `(thread_id, item_id)`
- because phase 4B fixes single-thread-per-item membership, the persisted membership model must also prevent the same item from belonging to multiple threads under the same `memory_id`
- a practical v1 uniqueness contract is `(memory_id, item_id)` at the membership layer

### Recommended enums

`episode_type`:

- `emotional_recovery`
- `relationship`
- `life_transition`
- `health_habit`
- `project`
- `recurring_issue`
- `incident`
- `other`

`status`:

- `open`
- `dormant`
- `closed`

`role`:

- `trigger`
- `core`
- `context`
- `outcome`

## Membership Model

Phase 4B fixes one important v1 decision:

- one `MemoryItem` may belong to at most one `MemoryThread`

This single-thread-per-item rule is chosen because it:

- keeps derivation deterministic
- avoids noisy duplicate recall from overlapping threads
- keeps lifecycle and rebuild behavior simpler
- prevents `MemoryThread` from becoming a soft-tagging layer

Future phases may revisit multi-membership if a clear need emerges, but phase 4B should not start there.

## Persistence Contract

`MemoryThread` data is secondary and derived.

The required persistence contract is:

- item persistence succeeds independently of thread persistence
- thread derivation runs after item commit semantics
- thread writes are idempotent
- thread rebuild is allowed at any time
- partial thread population is valid
- missing thread data degrades to non-thread retrieval behavior

Phase 4B must not introduce any pre-item-write dependency.

## Derivation Inputs

Thread derivation consumes:

- persisted `MemoryItem`s
- typed item graph links
- entity mention data
- item temporal fields
- persisted item metadata already stored with the item record
- existing open or dormant threads inside the same `memoryId`

Thread derivation must not directly consume as truth:

- raw caption text
- insight summaries
- unbounded full-graph traversal
- LLM-generated titles or summaries
- transient pipeline context that is not durably persisted

Rebuildability rule:

- if a future signal is considered important for thread membership truth, it must first be materialized into durable item or graph state before phase 4B derivation is allowed to depend on it
- derivation truth must therefore be reproducible from persisted item state, persisted graph state, and persisted thread state only

## Derivation Signals

### Strong positive signals

- temporal continuity
- explicit or validated causal progression
- stable central entities
- repeated focus on the same ongoing issue, goal, or life situation
- graph-connected neighborhood coherence
- bounded recurrence near existing thread anchors

### Supporting signals

- semantic cohesion
- repeated affect around the same event line
- stable environmental or participant context

### Strong split signals

- only topical similarity
- only shared high-fanout entities
- no meaningful temporal continuity
- no causal progression
- incompatible participant or context shift
- large time jump without graph-supported bridge

## Derivation Pipeline

Phase 4B should use a two-stage derivation flow.

### Stage 1: membership decision

For each new or changed item:

1. gather bounded candidate threads from:
   - direct entity overlap
   - temporal proximity
   - anchor-item adjacency
   - graph-supported local continuity
2. compute a rule-based membership score against each candidate thread
3. decide one of:
   - attach to one existing thread
   - create a new thread
   - attach to none
   - mark one or more threads for later rebuild review

This stage must be deterministic for the same inputs and config.

### Stage 2: thread refresh

For each touched thread:

- recompute `start_at`
- recompute `end_at`
- recompute `last_activity_at`
- recompute `status`
- recompute `anchor_item_id`
- recompute `sequence_hint`
- recompute `confidence`
- regenerate `title`
- regenerate `summary_snapshot`

Refresh is asynchronous and idempotent.

### Merge and split semantics

Complex merge and split logic must not run in the synchronous item path.

Allowed phase 4B behavior:

- mark threads for async merge review
- mark threads for async split review
- perform actual merge or split inside rebuild or maintenance tasks only

This keeps the primary item path predictable.

## Membership Scoring Contract

The exact formula may evolve, but the contract is fixed.

Membership scoring should combine bounded signals such as:

- temporal proximity score
- entity continuity score
- causal-chain score
- semantic cohesion score
- structural graph support score
- thread-status bonus or penalty
- thread-size penalty for anti-bloat

Required properties:

- transparent rule-driven scoring
- bounded candidate set
- deterministic output for the same inputs
- threshold-driven attach vs create-new vs no-attach outcome

## Conservative Defaults

The default system should prefer:

- no attach over weak attach
- new thread over questionable merge
- dormant thread over aggressive reopen
- explicit rebuild over silent in-place reshaping

This is a quality-preservation rule, not an implementation suggestion.

## Lifecycle Model

### `open`

A thread is `open` when:

- it has recent activity
- new members may still be added normally
- retrieval may use it as an active narrowing target

### `dormant`

A thread is `dormant` when:

- it has coherent history
- no recent activity is observed
- it remains retrievable
- a new high-confidence item may reopen it

### `closed`

A thread is `closed` when:

- the process is resolved or clearly ended
- or a strong rule marks the line as completed

It remains retrievable, but is not the default expansion target unless directly relevant.

### Allowed transitions

- `open -> dormant`
- `dormant -> open`
- `open -> closed`
- `dormant -> closed`

Phase 4B should avoid implicit reopen of strongly completed threads. If necessary, create a new thread rather than silently reopening a clearly ended one.

## Sequence and Role Assignment

`sequence_hint` should be derived primarily from:

- item time fields
- validated temporal links
- causal direction
- stable tie-break rules

`role` should be assigned heuristically:

- `trigger`: early initiating event
- `core`: main process-bearing item
- `context`: supporting or background item
- `outcome`: resolution or result item

Roles are local to the thread only and must not be written back onto `MemoryItem` as global truth.

## Title and Summary Generation

### Required baseline

Phase 4B must support title and summary generation without LLM.

Rule-first fallback should use:

- top-weighted members
- dominant entities
- time span
- episode type
- start-to-current or start-to-end transition pattern

Example fallback titles:

- `Breakup Recovery Line`
- `Sleep Adjustment Line`
- `Payment Timeout Incident Line`
- `Phase 4 Delivery Line`

Example fallback summaries:

- `From repeated insomnia after breakup toward partial emotional stabilization with continuing fluctuations`
- `From alert trigger to rollback and service recovery`

### Optional LLM refinement

LLM may refine only:

- `title`
- `summary_snapshot`

Under these rules:

- async only
- optional and disableable
- failure falls back to rule-generated text
- output must never determine thread membership truth

## Retrieval Integration

## Retrieval principle

`MemoryThread` is an additive intermediate retrieval layer.

It must not replace:

- direct recall
- typed graph expansion
- final rerank

The intended retrieval flow becomes:

1. direct recall
2. memory-thread recall
3. candidate narrowing to memory-thread members
4. bounded typed item-graph expansion
5. final rerank

## Memory-thread recall

Memory-thread recall must be seeded, not global-first.

The default query-time behavior is:

1. collect strong direct item seeds from existing retrieval channels
2. inspect thread memberships of those seeds
3. score candidate threads from seed overlap and seed strength
4. choose at most a bounded number of candidate threads
5. narrow the unpinned portion of the candidate item set toward the strongest members of those threads

This prevents phase 4B from becoming a second full-search subsystem.

## Direct-backbone protection and cardinality invariant

Memory-thread assist must not be allowed to suppress strong direct evidence merely because a thread hit is noisy or partially wrong.

Phase 4B fixes the following invariant:

- let `directWindow` be the bounded pre-memory-thread candidate list produced by the strategy's direct retrieval path
- preserve a pinned direct prefix of size `protectDirectTopK`
- memory-thread recall and thread-member enrichment may affect only the unpinned tail
- the memory-thread-enriched candidate pool must contain at most `directWindow.size()` items
- memory-thread assist may replace candidates only inside the unpinned tail, and must not enlarge the window

This keeps memory-thread assist additive and bounded in exactly the same architectural spirit as phase 4A graph assist.

## Simple retrieval integration

For `SimpleRetrievalStrategy`:

1. keep the current direct retrieval backbone
2. after direct candidate formation, identify candidate threads from direct hits
3. fuse bounded thread-member candidates only into the unpinned tail of the direct window
4. then run bounded typed graph expansion
5. then dedupe and rank

`MemoryThread` therefore enriches the candidate pool before graph expansion, but after direct retrieval has already established evidence.

## Deep retrieval integration

For `DeepRetrievalStrategy`:

1. keep the phase 4A direct and graph backbone
2. in the slow path, after direct candidate formation and before graph assist and rerank:
   - detect candidate threads from direct candidates
   - fuse bounded thread-member candidates only into the unpinned tail of the direct window
   - then run typed graph assist on the memory-thread-enriched bounded set
3. rerank the fused result normally

This makes phase 4B a clean continuation of phase 4A rather than a competing deep path.

## Hard retrieval constraints

Phase 4B must enforce:

- memory-thread recall only from direct seeds
- bounded number of candidate threads
- bounded members per thread used for retrieval
- preserve a pinned direct prefix of size `protectDirectTopK`
- memory-thread assist must not enlarge the bounded direct candidate window
- retrieval timeout for memory-thread assist
- thread failure degrades to non-memory-thread retrieval
- typed graph expansion remains bounded and item-centered

## Result shaping

`MemoryThread` may help explain or lightly organize results, but item results remain primary.

Allowed:

- memory-thread provenance on result items
- memory-thread title in debug or admin payload
- grouping hints for presentation

Not allowed in phase 4B:

- returning only threads and hiding items
- replacing item rerank with thread-only ranking

## Server and Admin Surface

Phase 4B should expose:

- list threads for a memory
- fetch thread detail
- fetch thread members
- fetch item-to-thread membership
- trigger thread rebuild or backfill
- inspect derivation status and failures

Suggested capability shape:

- `GET /admin/v1/memory-threads`
- `GET /admin/v1/memory-threads/{id}`
- `GET /admin/v1/memory-threads/{id}/items`
- `GET /admin/v1/items/{id}/memory-thread`
- `POST /admin/v1/memory-threads/rebuild`
- `POST /admin/v1/memory-threads/rebuild/{memoryId}`

The exact route structure may adapt to existing server conventions, but these capabilities should all exist.

## Runtime Config Surface

Suggested config groups:

- `memoryThread.enabled`
- `memoryThread.derivation.enabled`
- `memoryThread.derivation.async`
- `memoryThread.rule.matchThreshold`
- `memoryThread.rule.newThreadThreshold`
- `memoryThread.rule.maxCandidateThreads`
- `memoryThread.rule.maxMembersPerThread`
- `memoryThread.rule.maxRetrievalMembersPerThread`
- `memoryThread.lifecycle.dormantAfter`
- `memoryThread.lifecycle.closeAfter`
- `memoryThread.llmSummary.enabled`

Retrieval-specific config:

- `retrieval.simple.memoryThreadAssist.enabled`
- `retrieval.simple.memoryThreadAssist.protectDirectTopK`
- `retrieval.simple.memoryThreadAssist.maxThreads`
- `retrieval.simple.memoryThreadAssist.maxMembersPerThread`
- `retrieval.deep.memoryThreadAssist.enabled`
- `retrieval.deep.memoryThreadAssist.protectDirectTopK`
- `retrieval.deep.memoryThreadAssist.maxThreads`
- `retrieval.deep.memoryThreadAssist.maxMembersPerThread`

Defaults must be conservative and should preserve current behavior when the feature is disabled.

## Operational Semantics

### Async execution model

Thread derivation should run as:

- post-item-commit asynchronous work
- rebuildable maintenance work
- optionally batched background reconciliation

Phase 4B must not require thread derivation to complete before item extraction is considered successful.

### Rebuild and backfill

The system must support:

- full rebuild for one `memoryId`
- full rebuild for all thread data
- incremental rebuild for touched threads
- historical backfill from existing items and graph state

Rebuild must be:

- idempotent
- deterministic for the same underlying data and config
- safe under partial prior state

### Disabled mode

When `MemoryThread` is disabled:

- item writes continue normally
- graph writes continue normally
- retrieval continues without memory-thread assist
- admin surfaces may return disabled or empty behavior rather than failing

## Failure and Degradation Rules

### Derivation failure

If memory-thread derivation fails:

- item write still succeeds
- graph retrieval still succeeds
- insight extraction still succeeds
- failure is observable through tracing, metrics, or admin status

### Retrieval failure

If memory-thread assist fails at query time:

- drop only the memory-thread assist contribution
- continue with existing direct plus graph retrieval
- do not fail the query

### LLM summary failure

If optional thread text generation fails:

- keep rule-generated fallback text
- do not invalidate the thread
- do not block retrieval use

## Observability

Phase 4B should add tracing and metrics for:

- thread derivation task count
- attach, create, and no-op outcomes
- rebuild duration
- thread size distribution
- retrieval memory-thread-hit rate
- retrieval narrowing effectiveness
- derivation failures
- retrieval assist fallbacks

Suggested trace spans:

- `memory_thread.derive`
- `memory_thread.attach`
- `memory_thread.create`
- `memory_thread.refresh`
- `memory_thread.rebuild`
- `retrieval.memory_thread_assist`

## Anti-Bloat Controls

To keep the project safe under real workloads, phase 4B must include:

- max open threads per memory
- max members per thread
- max candidate threads evaluated per item
- max retrieval members consumed from a thread
- max rebuild batch size
- conservative attach thresholds

The system should be willing to leave some items unthreaded rather than forcing over-organization.

## Migration

Phase 4B must not require rewriting existing `MemoryItem` rows.

Migration requirements:

- add new memory-thread tables only
- support empty memory-thread state
- support staged backfill
- allow partial deployment where episode storage exists but derivation is disabled

## Example

Companionship-chat example:

`MemoryThread`

- `episode_type`: `emotional_recovery`
- `title`: `Breakup Recovery Line`
- `summary_snapshot`: `From repeated insomnia and self-blame toward partial emotional stabilization with continuing fluctuations`
- `status`: `open`

Members:

- `Could not sleep for three nights after the breakup` -> `trigger`
- `Today I did not cry, but still felt empty` -> `core`
- `Started seeing friends again` -> `outcome`
- `Still feel pain when seeing their updates` -> `context`

This is not:

- one raw caption
- one insight summary
- one temporal edge list

It is one ongoing process line.

## Required Tests

Phase 4B implementation must include at least:

- storage round-trip tests for threads and memberships
- uniqueness tests for single-thread-per-item persistence invariants
- derivation tests for attach vs new-thread vs no-attach
- conservative split tests for weak topical similarity
- lifecycle tests for open, dormant, and closed states
- stable `thread_key` reuse tests across deterministic rebuild
- fallback title and summary generation tests
- simple retrieval episode-assist tests
- deep retrieval episode-assist tests
- direct-prefix preservation tests for simple and deep memory-thread assist
- degradation tests when episode storage is unavailable
- rebuild idempotence tests
- config projection and codec tests
- admin endpoint tests
- backfill and rebuild consistency tests

## Out of Scope for Phase 4B

The following work remains explicitly out of scope:

- multi-membership items belonging to multiple memory threads
- memory-thread-first global search as the default retrieval entry point
- LLM-driven membership truth
- thread-driven rewriting of typed graph edges
- thread-only final retrieval result types
- replacing insight generation with thread summaries
- graph-primary routing

## Final Decisions

The following decisions are intentionally fixed by this document:

- `StoryThread` and `EpisodeThread` are replaced by `MemoryThread`
- `MemoryThread` is derived, not authoritative
- typed item graph remains the structural truth layer
- rawdata caption remains the raw-content description layer
- insight tree remains the abstraction layer
- membership truth is rule-driven
- optional LLM use is limited to display text only
- retrieval remains item-centered
- memory-thread recall is seeded from direct recall, not global-first
- phase 4B uses single-thread-per-item membership in v1
- memory-thread assist is bounded and optional
