# Memind Incremental Multimodal Ingestion Design

**Date:** 2026-04-22
**Status:** Proposed
**Scope:** `memind-core` multimodal ingestion kernel, plugin SPI, resource persistence, background processing runtime, and store schema additions for large-file document, audio, image, and URL ingestion

## 1. Context

`memind` already has a useful multimodal shell:

- file and URL extraction can persist `memory_resource`
- parsed multimodal content can become `memory_raw_data`
- downstream item and insight extraction already reuse the same raw-data pipeline

The current architecture is directionally correct, but its ingestion kernel is still built around whole-content materialization:

- `ContentParser` parses `byte[]` into `RawContent`
- `ContentParserRegistry` routes `byte[] -> RawContent`
- `FetchSession.readBody(maxBytes)` reads an entire URL body into memory
- `RawContentProcessor` chunks only after full parsed content already exists

That architecture forces large-content safety to be enforced through up-front source-size and parsed-content limits rather than through incremental processing.

Today this shows up as modality-specific caps such as:

- document source caps for text-like and binary inputs
- parsed-content token caps
- image source and parsed-description caps
- audio source, parsed-token, and duration caps

These guards are reasonable in a whole-buffer design, but they are not the right long-term answer for the product goal.

The product goal is not "reject large files safely." The product goal is "ingest real multimodal resources completely, including files in the `50MB` to `100MB` range, without pathological memory behavior."

This design is therefore not "make `memind` behave like `cognee`."

This design keeps `memind`'s existing strengths:

- `memory_resource` as a first-class durable source
- multimodal governance and content-profile semantics
- unified downstream raw-data, item, and insight layers

The change is specifically to replace whole-buffer ingestion with `resource-first + async + segment-stream`.

## 2. Current-State Assessment

### 2.1 What is already strong

The current system already has several foundations worth preserving:

- `memory_resource` is the right durable source abstraction
- `memory_raw_data` is already the right durable segment abstraction
- multimodal bytes can already be stored before downstream processing
- item and insight extraction already operate on segmented raw data instead of modality-specific tables
- URL ingestion already distinguishes resource fetching from content parsing at a conceptual level

This means the redesign does not need a new product model. It needs a better ingestion kernel.

### 2.2 What is weak today

#### 2.2.1 Parser SPI is whole-buffer by construction

`ContentParser.parse(byte[] data, SourceDescriptor source)` makes "load everything first" the default and often the only practical implementation path.

#### 2.2.2 URL fetching is whole-buffer by construction

`FetchSession.readBody(maxBytes)` turns remote resources into the same problem as local large files: the source must be fully resident before parsing starts.

#### 2.2.3 Parsed-content validation guards the wrong layer

Current document, image, and audio processors validate total parsed content size. In a streaming design, total file size and total token count are not the dangerous unit.

The dangerous unit is:

- a single processing window
- a single emitted segment
- a single prompt

#### 2.2.4 Chunking happens too late

The system chunks after full `RawContent` exists. For large files, the system should instead parse and chunk incrementally as data becomes available.

#### 2.2.5 URL is treated partly as a content path

In reality, URL is only a source kind. The content kind is still document, image, audio, or HTML-like document. The new architecture should make that explicit.

## 3. Problem Statement

`memind` cannot reliably support large multimodal ingestion by simply increasing current limits.

If the current caps are relaxed without changing architecture, the system still risks:

- loading large files fully into memory
- building large parsed-content objects before chunking
- paying parse cost again after transient failures
- losing progress on restart
- reprocessing large inputs from the beginning after partial success

The real problem is architectural:

1. source acquisition is not incremental enough
2. parser boundaries are too coarse
3. persistence does not track long-running multimodal processing state
4. retry and recovery are not modeled as first-class runtime concepts

## 4. Goals

### 4.1 Support complete ingestion of large multimodal resources

The system should handle normal large inputs, including `50MB` and `100MB` files, without default truncation.

### 4.2 Make durable resource persistence the first step

The system should store or otherwise stabilize the source resource before expensive downstream processing.

### 4.3 Replace whole-buffer parsing with incremental segment production

The kernel should process resources as streams or file-backed iterators and emit durable segments progressively.

### 4.4 Keep downstream raw-data, item, and insight semantics unified

The redesign should not introduce separate end-state storage models for document, image, audio, and URL.

### 4.5 Support restart, retry, and idempotent reprocessing

Progress must survive worker crashes and transient provider failures.

### 4.6 Keep public limits simple

The new design should keep only the limits that defend actual failure modes:

- source byte budget
- single-segment and single-prompt token budgets

### 4.7 Preserve `memind`'s multimodal semantics

The redesign should preserve:

- content type
- content profile
- governance type
- resource linkage
- unified raw-data lineage

## 5. Non-Goals

### 5.1 No early normalization into text files

The design does not convert every source into a `text_<hash>.txt` intermediate artifact and then pretend every modality is just text.

Unification happens at the standardized segment level, not at an intermediate text-file level.

### 5.2 No staging-output table in v1

This iteration does not add a second raw-data staging table before publish.

### 5.3 No exact-once distributed processing guarantee

The design targets `at least once + idempotent upsert`, not expensive exact-once semantics.

### 5.4 No compatibility shim for the old multimodal SPI

The old whole-buffer multimodal SPI should not remain as the long-term kernel with adapters layered on top.

### 5.5 No public knob explosion

This design does not introduce a large set of public run-time tuning options for pages, sections, duration, parser phases, or retry internals.

### 5.6 No transactional "publish all outputs at the very end" model in v1

Raw data may be persisted incrementally during a run. Completion visibility is controlled by run state and downstream triggering, not by holding all outputs until the end.

## 6. Design Principles

### 6.1 Resource first, processing second

The durable resource is the authoritative source for multimodal reprocessing.

### 6.2 Source kind and content kind are different concepts

`URL` is a source kind. It is not a content kind.

### 6.3 The segment is the unification boundary

The system should unify document, audio, image, and URL processing around a common `StandardSegment` contract.

### 6.4 Total file tokens are not the dangerous unit

Model context windows are threatened by oversized prompts, not by the total token count of a whole resource processed incrementally.

### 6.5 Progress must be durable

Long-running multimodal work should be restartable from a checkpoint, not from byte zero.

### 6.6 Idempotency is a core design property

A restarted run should upsert the same segment identities instead of creating duplicates.

### 6.7 Simplicity is a feature

The system should prefer one well-defined run table and one checkpoint model over a large coordination framework.

## 7. Recommended Architecture

### 7.1 High-level flow

The new ingestion flow is:

1. submit source
2. resolve and persist a stable `MemoryResource`
3. create a `ResourceProcessingRun`
4. let a background coordinator claim the run
5. open the correct `SegmentProducer`
6. emit `StandardSegment` batches incrementally
7. pass those batches through a shared incremental raw-data pipeline
8. update checkpoint and stats after each committed batch
9. publish the generation and supersede the old one if necessary
10. mark the run terminal
11. trigger downstream item and insight extraction only after successful publication

### 7.2 New core components

#### 7.2.1 `ResourceResolver`

Responsible for turning user input into a stable resource descriptor.

It handles:

- local file submissions
- URL submissions
- source metadata normalization
- content-type hints before actual producer selection

It does not do full content parsing.

#### 7.2.2 `ResourceSubmissionService`

Responsible for:

- storing the source into `ResourceStore` when needed
- writing or upserting the corresponding `memory_resource`
- creating or reusing a `resource_processing_run`

This is the point where multimodal ingestion becomes durable.

#### 7.2.3 `ResourceProcessingRunCoordinator`

Responsible for:

- claiming runnable rows from `resource_processing_run`
- renewing leases
- invoking the correct `SegmentProducer`
- updating checkpoint and stats
- classifying failures as retryable or terminal

#### 7.2.4 `SegmentProducer`

Responsible for turning a durable resource into a stream of `StandardSegment` batches.

This is the new modality extension point and replaces the old multimodal dependence on:

- `ContentParser.parse(byte[])`
- `RawContentProcessor.chunk(parsedContent)`

#### 7.2.5 `IncrementalRawDataPipeline`

Responsible for a producer-agnostic segment pipeline:

- caption generation
- vectorization
- `MemoryRawData` materialization
- idempotent upsert

It does not decide how a resource is parsed. It only decides how emitted segments become durable raw data.

### 7.3 Why this architecture is preferable

This architecture is preferable because it attacks the actual bottleneck:

- the kernel no longer requires full `byte[]` materialization
- progress can be checkpointed at modality-appropriate boundaries
- retries can resume from the last completed batch
- item and insight layers stay unchanged conceptually because `memory_raw_data` remains the end-state contract

## 8. Standard Segment Model

### 8.1 `StandardSegment`

All multimodal producers should emit the same logical segment structure.

The segment contract should carry:

- `segmentKey`
- `content`
- `captionHint`
- `contentType`
- `contentProfile`
- `governanceType`
- `boundary`
- `metadata`
- optional runtime context needed before persistence

### 8.2 Segment metadata expectations

The metadata should carry lineage and producer-specific position data such as:

- `resourceId`
- `resourceProcessingRunId`
- `producerId`
- `segmentIndex`
- `segmentKind`
- `page`
- `pageRange`
- `sectionIndex`
- `charStart`
- `charEnd`
- `startMs`
- `endMs`
- `sourceKind`

### 8.3 Stable identity

Each segment must have a stable `segmentKey` derived only from durable source identity and durable producer position, not from transient runtime state.

Examples:

- document: `page + section + chunkIndex`
- audio: `startMs + endMs`
- image: `regionId` or a fixed single-image segment key
- HTML document: `sectionPath + chunkIndex`

## 9. Data Model

### 9.1 Keep `memory_resource`

`memory_resource` remains the authoritative durable source row.

It should continue to store stable metadata only:

- `sourceUri`
- `storageUri`
- `fileName`
- `mimeType`
- `checksum`
- `sizeBytes`
- normalized source metadata

It should not become the run-state table.

One narrow exception is justified:

- `active_generation`
- `published_run_id`

These are not transient execution state. They are stable publication pointers for the currently visible derived output of the resource.

### 9.2 Keep `memory_raw_data`

`memory_raw_data` remains the authoritative durable segment/output row.

It should continue to represent the final segment-level unit reused by downstream extraction.

No modality-specific output tables are introduced.

`memory_raw_data` does, however, need a few explicit publication and lineage columns in addition to metadata:

- `resource_processing_run_id`
- `resource_generation`
- `published`
- `published_at`

These fields should be real columns rather than metadata-only projections because current query and text-search paths need a portable, indexed visibility filter across sqlite, mysql, and postgresql.

### 9.3 Add `resource_processing_run`

Add exactly one new coordination table:

- `resource_processing_run`

Recommended columns:

- `id`
- `biz_id`
- `user_id`
- `agent_id`
- `memory_id`
- `resource_id`
- `generation`
- `status`
- `attempt`
- `producer_id`
- `source_checksum`
- `submitted_at`
- `started_at`
- `finished_at`
- `next_retry_at`
- `checkpoint_json`
- `stats_json`
- `error_code`
- `error_message`
- `lease_owner`
- `lease_until`
- `cancel_requested`
- `created_at`
- `updated_at`
- `deleted`

`id` should remain the internal database primary key. `biz_id` should be the stable external `runId`, following the existing `memind` persistence convention used by `memory_resource`, `memory_raw_data`, `memory_item`, and `memory_insight`.

### 9.4 Why one table is enough

One run has exactly one active position at a time.

The system does not need:

- a separate checkpoint table
- a separate output staging table
- a separate queue table

`resource_processing_run` is both:

- the execution record
- the durable queue row

### 9.5 Run-table indexing and uniqueness

The new run table should follow existing store conventions and define at least:

- `uk_resource_processing_run_biz_id(user_id, agent_id, biz_id)`
- `uk_resource_processing_run_generation(user_id, agent_id, resource_id, generation)`
- `idx_run_resource_status(user_id, agent_id, resource_id, status, submitted_at)`
- `idx_run_claim(status, next_retry_at, lease_until, updated_at)`

Single-flight submission for a resource should be enforced by the submission service rather than by a cross-dialect partial unique index:

- when a new request arrives, the service checks for active rows in `PENDING`, `RUNNING`, or `RETRY_WAITING`
- if one exists for the same `(user_id, agent_id, resource_id)`, the service reuses that run unless the caller explicitly requests a new generation

This keeps the semantics strict without depending on dialect-specific partial-index behavior.

### 9.6 Checkpoint storage

`checkpoint_json` stores modality-specific progress.

Examples:

- document:
  - `stage=PRODUCING_SEGMENTS`
  - `pageCursor=42`
  - `sectionCursor=3`
  - `nextSegmentIndex=128`
- audio:
  - `stage=TRANSCRIBING`
  - `offsetMs=900000`
  - `windowIndex=31`
  - `nextSegmentIndex=31`
- URL:
  - `stage=FETCHING_RESOURCE`
  - `bytesPersisted=5242880`

The checkpoint is intentionally JSON because producers have different durable cursors.

### 9.7 Stats storage

`stats_json` is for operational statistics only:

- `producedSegmentCount`
- `persistedSegmentCount`
- `lastSegmentKey`
- `bytesRead`
- `elapsedMs`

It is not a correctness boundary.

### 9.8 Published-state and generation model

Each resource should have a monotonically increasing derived-output generation.

The rules are:

- `resource_processing_run.generation` identifies the candidate output generation for that run
- only one generation for a resource may be `published` at a time
- all `memory_raw_data` rows created by a run start as `published = false`
- `memory_resource.active_generation` points to the currently visible generation
- `memory_resource.published_run_id` points to the run that published it

Submission behavior should distinguish two cases:

- ordinary repeat submissions with the same durable resource identity and no explicit rerun intent should reuse the active successful generation or an already-active run
- explicit rerun or forced reprocessing should allocate the next generation for that resource

This avoids mixing "same work resumed" with "new output supersedes old output."

### 9.9 Supersession and cleanup model

Publishing a new generation must be a first-class phase, not an incidental side effect of `SUCCEEDED`.

The publish sequence should be:

1. serialize publication by resource
2. identify the previously published generation for the resource, if any
3. collect the superseded raw-data ids and their derived item ids
4. in one relational publish transaction where possible:
   - publish the new generation's `memory_raw_data` rows
   - soft-delete or unpublish the old generation's raw-data rows
   - advance `memory_resource.active_generation` and `published_run_id`
5. delete items derived from those superseded raw-data rows
6. delete insights for the affected memory
7. schedule a rebuild from surviving items plus the new generation

`SUCCEEDED` should only be written after this publish-and-cleanup phase completes.

The intentionally coarse part is insight cleanup.

Current `memind` store contracts already support deleting items and insights, but they do not provide precise stable provenance from insight back to a single resource generation. For correctness, the v1 supersession model should therefore prefer:

- targeted item deletion by `raw_data_id`
- memory-level insight delete-and-rebuild

over a more fragile partial insight patching strategy.

### 9.10 Store-contract implications

To support publication and supersession cleanly, store contracts need to grow in a targeted way.

Required additions include:

- raw-data lookup and delete operations by `resource_id`, `resource_generation`, and `resource_processing_run_id`
- raw-data listing APIs that default to `published = true`
- item lookup helpers by `raw_data_id`
- an orchestration path for memory-level insight delete-and-rebuild during resource supersession

Without these additions, generation replacement remains underspecified and query visibility remains inconsistent.

## 10. Run State Model

### 10.1 States

`resource_processing_run.status` should only use:

- `PENDING`
- `RUNNING`
- `RETRY_WAITING`
- `SUCCEEDED`
- `FAILED`
- `CANCELLED`

### 10.2 State semantics

- `PENDING`: ready to be claimed
- `RUNNING`: currently owned by a worker lease
- `RETRY_WAITING`: failed transiently and will retry after `next_retry_at`
- `SUCCEEDED`: all producer work, vector patching, and generation publication completed
- `FAILED`: terminal failure
- `CANCELLED`: user or operator requested cancellation

### 10.3 Lease model

The coordinator claims runs by writing:

- `status=RUNNING`
- `attempt=attempt+1`
- `leaseOwner`
- `leaseUntil`

If a worker dies and `leaseUntil` expires, another worker may reclaim the run.

### 10.4 Claimable rows

The coordinator should claim from:

- `PENDING`
- `RETRY_WAITING` where `next_retry_at <= now`
- `RUNNING` where `lease_until < now`

## 11. Limit Model

### 11.1 Keep `SourceLimitOptions.maxBytes`

This remains the main guard for:

- pathological source sizes
- malicious uploads
- unacceptable fetch/storage cost
- unacceptable parser runtime risk

### 11.2 Keep `TokenChunkingOptions.targetTokens` and `hardMaxTokens`

These remain the guards for:

- segment size
- chunk window size
- single-prompt safety

### 11.3 Remove total parsed-content token limits from the new kernel

`ParsedContentLimitOptions.maxTokens` should not remain part of the new multimodal ingestion kernel.

The system should not reject a whole resource just because its total token count is large if the resource can be processed incrementally.

### 11.4 Remove modality-level total-content caps from the public design

The new multimodal ingestion design should not expose public structural caps such as:

- `maxPages`
- `maxSections`
- `maxDuration`

Those are not the right product boundary for an incremental kernel.

### 11.5 What still fails

The system should still fail when:

- source bytes exceed `maxBytes`
- a single segment cannot be split below `hardMaxTokens`
- the source format is unsupported
- the source is corrupt or unreadable

## 12. Per-Modality Producer Design

### 12.1 Document producer

#### 12.1.1 Responsibility

The document producer reads a durable document resource and emits segments incrementally.

It supports:

- plain text
- markdown
- HTML-like documents
- PDF
- Office-style documents as long as the underlying parser can operate from file or stream

#### 12.1.2 Important implementation rule

The kernel requirement is not "every parser must be perfectly streaming internally."

The kernel requirement is:

- `memind` must not require full source `byte[]` materialization in its own ingestion boundary
- parsers may still read from a file-backed source if their library model requires it

This distinction matters for realistic parser integrations such as Tika-like libraries.

#### 12.1.3 Chunking model

The document producer should emit segments in this order:

1. parse structural units incrementally where available
2. derive text windows
3. apply token chunking
4. emit stable `segmentKey`s

#### 12.1.4 Checkpoint model

Checkpoint should track:

- parse stage
- page cursor when pages exist
- section cursor when sections exist
- next emitted segment index

#### 12.1.5 Whole-document fast path

For very small documents, a producer may still emit one segment. This is an optimization, not a different architecture.

### 12.2 Audio producer

#### 12.2.1 Responsibility

The audio producer reads a durable audio resource and emits transcript-backed segments incrementally.

#### 12.2.2 Processing model

The producer should work in time windows, not whole-audio transcript mode.

The exact STT backend is interchangeable:

- native streaming transcription
- file-backed chunk transcription
- local split and remote transcription

The common abstraction remains the same:

- transcript windows in
- `StandardSegment`s out

#### 12.2.3 Checkpoint model

Checkpoint should track:

- `offsetMs`
- `windowIndex`
- `nextSegmentIndex`
- current transcription stage

#### 12.2.4 Segment identity

Audio segment identity should derive from stable temporal boundaries such as:

- `startMs`
- `endMs`

### 12.3 Image producer

#### 12.3.1 Responsibility

The image producer reads a durable image resource and emits one or more image-derived segments.

#### 12.3.2 Processing model

Image ingestion does not need to be artificially forced into a complicated streaming model.

In most cases:

- one image resource
- one vision/OCR pass
- one or several region-derived segments

is enough.

#### 12.3.3 Checkpoint model

Image checkpoint can remain coarse:

- current phase
- whether OCR completed
- next region index when region segmentation exists

#### 12.3.4 Why image is different

Image is usually not the memory-risk driver. The main redesign pressure comes from document, audio, and URL.

### 12.4 URL producer behavior

#### 12.4.1 URL is not its own content producer

URL handling should be split into:

1. remote fetch and durable resource stabilization
2. delegation to the correct content producer based on final MIME and file identity

#### 12.4.2 URL-specific steps

The URL submission path should:

- open the remote response
- inspect headers
- enforce declared-size checks where possible
- stream the body into durable resource storage
- finalize a `memory_resource`
- dispatch to document, audio, or image producer

#### 12.4.3 HTML

HTML should be treated as a document-like content kind, not as a special URL-only pipeline.

## 13. Incremental Raw-Data Pipeline

### 13.1 Responsibilities

Once a producer emits `StandardSegment`s, the shared pipeline should:

1. normalize metadata
2. materialize stable raw-data identities
3. persist `MemoryRawData` rows as unpublished
4. generate captions where required
5. vectorize captions or segment text
6. patch vector ids back onto the persisted raw-data rows
7. leave publication to the terminal publish phase

### 13.2 Stable raw-data identity

`MemoryRawData.id` should be derived deterministically as:

`hash(resourceId + segmentKey + segmentSchemaVersion)`

`contentId` should also be stable and derived from the same segment identity.

This uses the existing `memory_raw_data.biz_id` uniqueness model instead of introducing a second deduplication mechanism.

### 13.3 Metadata on persisted raw data

Each persisted row should include enough metadata to trace back to the run:

- `resourceId`
- `resourceProcessingRunId`
- `segmentKey`
- `segmentIndex`
- `producerId`
- producer-specific position metadata

### 13.4 Vector-consistency rule

The incremental pipeline must not rely on the vector store and the relational store committing atomically.

The new batch protocol should therefore be:

1. persist raw-data rows first, unpublished and with stable `biz_id`
2. vectorize using those stable raw-data identities as the canonical lineage anchor
3. patch `caption_vector_id` back onto the raw-data rows
4. only then advance the run checkpoint

This uses the existing `updateRawDataVectorIds(...)` style of split persistence as a feature rather than treating vector generation as something that must happen before durable raw-data existence.

The preferred long-term contract is for `MemoryVector` to support caller-supplied deterministic vector ids derived from `MemoryRawData.id`.

That gives the new kernel:

- idempotent vector upsert on retry
- no duplicate vector documents on replay
- no orphan-vector risk caused solely by process crash between vector write and raw-data patch

### 13.5 Batch commit rule

The system should commit work in small batches:

1. producer emits a batch
2. raw-data rows for that batch are inserted unpublished
3. vector ids are patched onto those rows
4. run checkpoint and stats advance
5. lease is renewed

This is the fundamental durability loop.

## 14. Failure Handling, Retry, and Idempotency

### 14.1 Retry model

Failures should be classified as:

- retryable
- non-retryable

Retryable failures transition to `RETRY_WAITING`.

Non-retryable failures transition to `FAILED`.

### 14.2 Retryable failures

Typical retryable failures include:

- transient URL fetch failures
- provider `429` and `5xx`
- temporary database or resource-store failures
- worker death while a lease is active

### 14.3 Non-retryable failures

Typical non-retryable failures include:

- source exceeds `maxBytes`
- unsupported format
- corrupt or unreadable source
- a segment cannot be reduced below `hardMaxTokens`

### 14.4 Retry scheduling

Retry scheduling should use an internal bounded exponential backoff.

This does not need to be a complex public configuration surface in v1.

### 14.5 Idempotent recovery rule

The system should always:

1. persist raw-data rows for the batch
2. patch vector ids onto those rows
3. then advance the checkpoint

Preferably both happen in one transaction.

If a crash occurs:

- already-persisted raw-data rows will be reused or upserted harmlessly
- missing vector patches will be retried against stable raw-data identities
- not-yet-persisted segments will be retried from the old checkpoint

### 14.6 Partial raw data during failed runs

Partial raw data may exist physically while a run is still `RUNNING`, `FAILED`, or `CANCELLED`.

That is acceptable in v1 because:

- the system uses idempotent upsert
- ordinary raw-data retrieval is gated by `published = true`
- downstream item and insight extraction is gated on run success

This avoids the complexity of a second staging store while keeping downstream semantics clean.

## 15. Downstream Triggering

### 15.1 Raw-data visibility is gated by publication

All standard raw-data query surfaces must expose only:

- `deleted = false`
- `published = true`

This requirement applies to:

- JDBC text-search implementations
- MyBatis text-search implementations
- raw-data listing APIs used by normal retrieval paths

Admin and debugging tooling may expose unpublished rows explicitly, but unpublished multimodal rows must not leak into ordinary retrieval.

### 15.2 Trigger only after successful publication

Item extraction and insight building should not run continuously during a multimodal resource-processing run.

They should run only after the run reaches `SUCCEEDED` and its output generation is published.

### 15.3 Why this is the right tradeoff

This avoids:

- propagating partial failed runs into downstream memory items
- building insight from incomplete resources
- coupling retry behavior across subsystems

### 15.4 Existing conversation flow stays separate

Conversation ingestion can continue to use its current direct segment path because it does not suffer from the same large-resource problem.

## 16. Public API and Surface Semantics

### 16.1 External surface should stay as stable as practical

The redesign is allowed to break internal multimodal SPI, but user-facing extraction entry points should remain recognizable where possible.

### 16.2 Recommended submission semantics

The multimodal external surface should conceptually become:

- submit resource
- receive or reference a `ResourceProcessingRun`
- optionally wait for completion when the caller wants synchronous behavior

This allows:

- small-resource convenience
- large-resource async safety

without preserving the old whole-buffer kernel internally.

## 17. Plugin and SPI Migration

### 17.1 Replace old multimodal SPI

The old multimodal combination of:

- `ContentParser`
- `ContentParserRegistry`
- multimodal `RawContentProcessor.chunk(...)`

should no longer define the long-term ingestion kernel.

### 17.2 New extension points

The new plugin model should contribute:

- source/resource resolvers where needed
- `SegmentProducer`s
- ingestion policies

The key new extension point is the `SegmentProducer`.

### 17.3 Why no compatibility layer

Keeping the old whole-buffer SPI as an adapter target would preserve the architecture that causes the current problem.

This redesign is intentionally a kernel replacement, not a wrapper.

## 18. Resource Store and Fetcher Changes

### 18.1 `ResourceStore` must evolve

Current `ResourceStore.store(..., byte[] data, ...)` still assumes full in-memory source availability.

The new design requires file-backed or stream-backed resource persistence semantics.

### 18.2 `ResourceFetcher` must evolve

Current `FetchSession.readBody(maxBytes)` also assumes full in-memory body materialization.

The new design requires:

- header-first inspection
- incremental body persistence
- durable resource finalization before producer execution

### 18.3 Minimal requirement

Even if some parser libraries are file-based rather than fully stream-native, `memind`'s own acquisition boundary must become incremental and resource-backed.

### 18.4 `MemoryVector` must evolve

Current `MemoryVector` contracts return backend-generated ids only.

That is workable for the current synchronous shell, but it is not the right durability contract for incremental multimodal processing.

The new kernel should evolve `MemoryVector` so that vector writes can be anchored to stable caller-supplied ids derived from `MemoryRawData.id`.

If a backend cannot support that immediately, the transitional implementation must still preserve correctness by:

- writing raw-data rows first
- treating vector ids as a patch step
- retrying patchable vector work without publishing the run

The long-term target remains deterministic vector identity, not permanent dependence on compensating delete.

## 19. Migration Plan

### 19.1 Step 1: schema

Add `resource_processing_run` migrations for:

- sqlite
- mysql
- postgresql

Also extend existing multimodal tables with the minimal explicit publication fields needed by the new kernel:

- `memory_resource.active_generation`
- `memory_resource.published_run_id`
- `memory_raw_data.resource_processing_run_id`
- `memory_raw_data.resource_generation`
- `memory_raw_data.published`
- `memory_raw_data.published_at`

### 19.2 Step 2: domain and store contracts

Add:

- `ResourceProcessingRun`
- run persistence operations
- claim and lease operations
- generation-aware raw-data lookup and delete operations
- item lookup by `raw_data_id`
- publication and supersession orchestration contracts

### 19.3 Step 3: resource-first submission

Refactor file and URL entry points to submit stable resources before multimodal processing.

### 19.4 Step 4: new producer registry

Introduce the new `SegmentProducer` registry and bootstrap document, audio, image, and URL delegation behavior.

### 19.5 Step 5: shared incremental raw-data pipeline

Refactor multimodal raw-data persistence to consume emitted segments incrementally with:

- stable raw-data ids
- unpublished initial writes
- vector patching after raw-data persistence
- checkpoint advancement only after the vector patch is durable

### 19.6 Step 6: publish and supersede

Publish a successful generation, retire the superseded generation, and clean up its derived outputs.

### 19.7 Step 7: downstream success gating

Trigger item and insight extraction only when a run reaches `SUCCEEDED` and its generation has been published.

### 19.8 Step 8: delete old multimodal kernel

Remove old multimodal reliance on:

- `ContentParser.parse(byte[])`
- multimodal whole-content `RawContentProcessor.chunk(...)`
- URL `readBody(maxBytes)` whole-buffer fetch

## 20. Testing Strategy

### 20.1 Unit tests

Add unit coverage for:

- run state transitions
- generation allocation and single-flight reuse
- retry classification
- lease reclaim logic
- checkpoint serialization
- stable `segmentKey` and `MemoryRawData.id` generation
- published-visibility state transitions
- `hardMaxTokens` enforcement

### 20.2 Producer tests

Add modality-specific producer tests for:

- document page and section resume
- audio window resume
- image single and multi-region output
- URL header-first persistence and producer delegation

### 20.3 Store integration tests

Add integration coverage for all supported stores to verify:

- `resource_processing_run` schema
- claim and lease semantics
- published-row filtering on raw-data queries
- generation publish and supersession behavior
- retry scheduling
- idempotent raw-data upsert under recovery

### 20.4 Crash-recovery tests

Add end-to-end failure injection for:

- crash after raw-data persist but before checkpoint advance
- crash after publish visibility flip but before terminal status update
- crash after checkpoint advance
- lease expiration and worker takeover
- repeated replay without duplicate `memory_raw_data`

### 20.5 Large-resource tests

Add real large-file tests that verify:

- `50MB` and `100MB` document resources can complete
- URL ingestion does not require full in-memory body materialization
- peak memory is bounded by segment/window size, not by total file size

### 20.6 Regression tests

Preserve regression coverage for:

- existing conversation extraction
- item extraction
- insight extraction
- resource linkage metadata

## 21. Risks and Tradeoffs

### 21.1 This is an intentional internal break

The redesign simplifies the long term but requires a real kernel migration rather than a local patch.

### 21.2 Some third-party parsers may still be only file-backed

That is acceptable as long as `memind` itself does not require whole `byte[]` inputs and progress remains checkpointable above the parser boundary where practical.

### 21.3 Partial raw data exists before success

This is a conscious v1 tradeoff that reduces complexity and improves recovery.

### 21.4 Async execution changes operational expectations

Large multimodal ingestion becomes a run-driven process rather than a purely synchronous request-response action.

That is the correct tradeoff for correctness and scalability.

### 21.5 Supersession cleanup is intentionally correctness-first

Deleting superseded items and rebuilding insights at memory scope is heavier than a fine-grained patch.

That cost is acceptable in v1 because:

- rerun and supersession are less frequent than ordinary ingestion
- the current store contracts already support safe delete-and-rebuild primitives
- correctness is more important than attempting fragile partial insight surgery

## 22. Final Recommendation

`memind` should replace its current multimodal whole-buffer ingestion kernel with a `resource-first + async + segment-stream` architecture centered on:

- `MemoryResource`
- `ResourceProcessingRun`
- `SegmentProducer`
- `IncrementalRawDataPipeline`

The redesign should keep the current end-state storage model:

- `memory_resource`
- `memory_raw_data`

and add exactly one new coordination table:

- `resource_processing_run`

The public limit model should be simplified to:

- `SourceLimitOptions.maxBytes`
- `TokenChunkingOptions.targetTokens`
- `TokenChunkingOptions.hardMaxTokens`

The system should stop enforcing whole-resource parsed token caps in the new kernel.

The key product outcome is straightforward:

`memind` should be able to ingest large multimodal resources completely by processing them incrementally, not by rejecting them before the real work even starts.
