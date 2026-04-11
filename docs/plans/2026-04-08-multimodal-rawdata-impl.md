# Multimodal RawData Support Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend memind's RawData pipeline to support document, image, and audio content through both direct multimodal requests and optional raw-file ingestion, while persisting multimodal file references through a durable `memory_resource` metadata table instead of leaving dead registration paths or partially wired persistence.

**Architecture:** Add three new `RawContent` implementations plus three matching `RawContentProcessor` implementations, but register them explicitly in `MemoryExtractionAssembler` instead of assuming auto-discovery. Direct multimodal requests should normalize source metadata eagerly inside `ExtractionRequest.document/image/audio(...)`, and `ExtractionRequest.file(...)` should let `MemoryExtractor` normalize raw bytes into `RawContent` through a configured `ContentParser` and optional `ResourceStore`. Multimodal raw-data idempotency must use a request-aware key composed from the text fingerprint plus normalized source identity, so different files with identical parsed text do not collapse onto one raw-data row. Normalized source metadata should then be resolved into persisted `MemoryResource` rows through `MemoryStore.resourceOperations()`, with `memory_raw_data.resource_id` acting as the logical reference to `memory_resource.biz_id`. `ResourceStore` remains the optional blob backend for original bytes, while the relational `memory_resource` table becomes the authoritative metadata/reference layer used by raw data, items, and downstream traceability.

**Tech Stack:** Java 21, Reactor (Mono/Flux), Jackson, JUnit 5, Maven multi-module

**Spec:** `docs/specs/2026-04-08-multimodal-rawdata-design.md`

---

## Implementation guardrails

- Keep runtime/persistence content types uppercase for compatibility with existing data:
  - `ContentTypes.CONVERSATION = "CONVERSATION"`
  - `ContentTypes.TOOL_CALL = "TOOL_CALL"`
  - new values are `DOCUMENT`, `IMAGE`, `AUDIO`
- Keep Jackson subtype names lowercase in `RawContent`:
  - `conversation`, `tool_call`, `document`, `image`, `audio`
- `RawDataLayer` is **not** "no changes needed" in this feature.
  - It must merge request metadata into persisted segment metadata.
  - It must project `resourceId` and `mimeType` into `MemoryRawData`.
  - It must resolve normalized source metadata into `MemoryResource` rows before raw data is persisted.
  - It must use a source-aware raw-data identity key for multimodal inputs; bare `RawContent.getContentId()` is not sufficient once `memory_resource` becomes authoritative.
- `ExtractionRequest.document(...)`, `image(...)`, and `audio(...)` are part of the metadata contract.
  - Each factory must start from a copy of `content.metadata()`.
  - Then add `sourceUri` and `mimeType` into request metadata when available.
  - `ImageContent` and `AudioContent` should expose optional `mimeType` fields so direct requests do not rely on implicit metadata conventions.
  - Direct multimodal call sites should use these factories rather than generic `ExtractionRequest.of(...)`.
- `MemoryExtractionAssembler` is part of the feature.
  - New processors are not auto-registered anywhere today.
  - `RawDataExtractionOptions.contentParser()` and the assembled `ResourceStore` hook must be passed into `MemoryExtractor`; adding the option/interface alone is not enough.
- `ExtractionRequest.file(...)` is part of this plan.
  - If this path is removed, `ContentParser` and `ResourceStore` leave scope, but the `memory_resource` table and `resource_id` reference model stay because direct multimodal requests still need durable source references.
  - The parser path must preserve parsed content metadata, not just file-level source fields.
- `memory_resource` is part of the feature.
  - It stores durable source-reference metadata, not parsed segment text.
  - Keep consistency with the existing store schema style: `resource_id` is a logical reference to `memory_resource.biz_id`; do not add hard foreign keys in this phase.
- Optional `ResourceStore` wiring must reach real runtime creation paths.
  - `JdbcStore`, JDBC starter auto-config, and MyBatis starter auto-config must all propagate it into `MemoryStore.of(...)`.
- `MemoryStore` is part of the persistence change.
  - Add `resourceOperations()` for relational resource metadata.
  - Add a single aggregate raw-data/resource persistence path so store implementations can keep `memory_resource` and `memory_raw_data` writes consistent.
  - Keep the legacy three-argument `MemoryStore.of(...)` only as a fail-fast compatibility path; it must not silently drop resource metadata.
- Item-level traceability is part of the feature.
  - `ParsedSegment.metadata()` must remain visible to downstream item extraction and persist into `MemoryItem.metadata()`, except for intentionally stripped legacy-only keys such as `messages`.
- User-scoped default insight types should accept multimodal content.
  - Agent-scoped defaults stay conversation-only.
- Unified item extraction prompts must be content-neutral.
  - Replace conversation-only wording and the `<Conversation>` wrapper with source-text wording such as `<SourceText>`.
- `RawDataExtractionOptions` constructor changes affect all direct call sites, including `memind-evaluation`.

---

## File structure

### New files in `memind-core`

| File | Responsibility |
|------|----------------|
| `core/extraction/rawdata/content/DocumentContent.java` | Document `RawContent` |
| `core/extraction/rawdata/content/ImageContent.java` | Image `RawContent` |
| `core/extraction/rawdata/content/AudioContent.java` | Audio `RawContent` |
| `core/extraction/rawdata/content/document/DocumentSection.java` | Structured document section |
| `core/extraction/rawdata/content/audio/TranscriptSegment.java` | Timestamped transcript segment |
| `core/extraction/rawdata/processor/DocumentContentProcessor.java` | Document chunking and segment metadata |
| `core/extraction/rawdata/processor/ImageContentProcessor.java` | Image single-segment processor |
| `core/extraction/rawdata/processor/AudioContentProcessor.java` | Audio chunking and time metadata |
| `core/extraction/RawFileInput.java` | Raw-file request payload |
| `core/data/MemoryResource.java` | Durable source-reference metadata record backed by `memory_resource` |
| `core/resource/ResourceRef.java` | Immutable stored-resource descriptor |
| `core/resource/ResourceStore.java` | Optional original-file storage interface |
| `core/resource/LocalFileResourceStore.java` | Local filesystem `ResourceStore` |
| `core/resource/ContentParser.java` | Optional parser from raw bytes to `RawContent` |
| `core/store/resource/ResourceOperations.java` | Persistence operations for `MemoryResource` |
| `core/store/resource/InMemoryResourceOperations.java` | In-memory `ResourceOperations` implementation |

All paths relative to: `memind-core/src/main/java/com/openmemind/ai/memory/`

### Modified files in `memind-core`

| File | Change |
|------|--------|
| `core/data/ContentTypes.java` | Add `DOCUMENT`, `IMAGE`, `AUDIO` constants |
| `core/extraction/rawdata/content/RawContent.java` | Register new Jackson subtypes |
| `core/store/InMemoryMemoryStore.java` | Surface in-memory `ResourceOperations` |
| `core/data/MemoryRawData.java` | Add `resourceId` and `mimeType` fields |
| `core/store/MemoryStore.java` | Add `resourceOperations()`, optional `resourceStore()`, and aggregate raw-data/resource persistence hooks |
| `core/extraction/ExtractionRequest.java` | Add multimodal factories and `file(...)` |
| `core/extraction/MemoryExtractor.java` | Branch between direct `RawContent` and raw-file ingestion, with parser and resource-store injection |
| `core/extraction/rawdata/RawDataLayer.java` | Merge request metadata, resolve/upsert `MemoryResource`, and persist source metadata |
| `core/builder/RawDataExtractionOptions.java` | Add document/audio chunking config and optional parser |
| `core/builder/MemoryExtractionAssembler.java` | Register new processors explicitly and pass parser/resource-store hooks into `MemoryExtractor` |
| `core/data/DefaultInsightTypes.java` | Allow user-scoped multimodal content into built-in insight types |
| `core/prompt/extraction/item/MemoryItemUnifiedPrompts.java` | Neutralize prompt wording from conversation-specific to content-generic |

### Modified files in `memind-evaluation`

| File | Change |
|------|--------|
| `memind-evaluation/.../EvaluationMemindConfiguration.java` | Update `RawDataExtractionOptions` constructor call |

### Modified JDBC plugin files

| File | Change |
|------|--------|
| `memind-plugin-jdbc/.../mysql/store/V2__multimodal.sql` | Create `memory_resource` and add `resource_id`, `mime_type` columns |
| `memind-plugin-jdbc/.../postgresql/store/V2__multimodal.sql` | Create `memory_resource` and add `resource_id`, `mime_type` columns |
| `memind-plugin-jdbc/.../sqlite/store/V2__multimodal.sql` | Create `memory_resource` and add `resource_id`, `mime_type` columns |
| `memind-plugin-jdbc/.../internal/schema/StoreSchemaBootstrap.java` | Apply multimodal resource/raw-data schema upgrades for existing and fresh databases |
| `memind-plugin-jdbc/.../internal/schema/SchemaVerifier.java` | Add table/column/index existence checks for upgrade gating |
| `memind-plugin-jdbc/.../mysql/MysqlMemoryStore.java` | Implement `ResourceOperations` and bind/read resource + raw-data fields |
| `memind-plugin-jdbc/.../postgresql/PostgresqlMemoryStore.java` | Implement `ResourceOperations` and bind/read resource + raw-data fields |
| `memind-plugin-jdbc/.../sqlite/SqliteMemoryStore.java` | Implement `ResourceOperations` and bind/read resource + raw-data fields |
| `memind-plugin-jdbc/.../JdbcStore.java` | Propagate `ResourceOperations`/`ResourceStore` and override aggregate raw-data/resource persistence |

### Modified JDBC starter files

| File | Change |
|------|--------|
| `memind-plugin-jdbc-starter/.../JdbcPluginAutoConfiguration.java` | Wire `ResourceOperations` and optional `ResourceStore` beans into `MemoryStore` |

### Modified MyBatis starter files

| File | Change |
|------|--------|
| `memind-plugin-mybatis-plus-starter/.../db/migration/*/V3__multimodal.sql` | Create `memory_resource` and add `resource_id`, `mime_type` columns for starter-managed schema init |
| `memind-plugin-mybatis-plus-starter/.../schema/DatabaseDialect.java` | Register multimodal migration scripts in the fixed script list |
| `memind-plugin-mybatis-plus-starter/.../dataobject/MemoryResourceDO.java` | Add MyBatis data object for `memory_resource` |
| `memind-plugin-mybatis-plus-starter/.../dataobject/MemoryRawDataDO.java` | Add `resourceId`, `mimeType` fields |
| `memind-plugin-mybatis-plus-starter/.../converter/ResourceConverter.java` | Map `MemoryResource` to/from MyBatis DOs |
| `memind-plugin-mybatis-plus-starter/.../converter/RawDataConverter.java` | Map `resourceId`, `mimeType` |
| `memind-plugin-mybatis-plus-starter/.../mapper/MemoryResourceMapper.java` | MyBatis mapper for `memory_resource` |
| `memind-plugin-mybatis-plus-starter/.../MybatisPlusMemoryStore.java` | Implement `ResourceOperations` and transactional raw-data/resource persistence |
| `memind-plugin-mybatis-plus-starter/.../MemoryMybatisPlusAutoConfiguration.java` | Wire `ResourceOperations` and optional `ResourceStore` bean into `MemoryStore` |

### Tests

| File | Tests |
|------|-------|
| `test/.../content/DocumentContentTest.java` | content type, text rendering, ID, sections, Jackson round-trip |
| `test/.../content/ImageContentTest.java` | content type, text rendering, ID, Jackson round-trip |
| `test/.../content/AudioContentTest.java` | content type, text rendering, ID, segments, Jackson round-trip |
| `test/.../processor/DocumentContentProcessorTest.java` | chunking with and without sections, metadata, boundaries |
| `test/.../processor/ImageContentProcessorTest.java` | single-segment chunking |
| `test/.../processor/AudioContentProcessorTest.java` | chunking with and without transcript segments |
| `test/.../resource/LocalFileResourceStoreTest.java` | store, retrieve, delete, exists, path traversal rejection |
| `test/.../store/resource/InMemoryResourceOperationsTest.java` | upsert, get, and list `MemoryResource` records |
| `test/.../store/MemoryStoreTest.java` | legacy `MemoryStore.of(...)` fail-fast behavior for multimodal resource persistence |
| `test/.../extraction/ExtractionRequestTest.java` | multimodal factories, metadata normalization, and `file(...)` |
| `test/.../extraction/MemoryExtractorMultimodalFileTest.java` | `file(...)` path, parser invocation, optional resource store, and cleanup |
| `test/.../rawdata/RawDataLayerProcessorTest.java` | metadata merge, `MemoryResource` upsert, persisted `resourceId`/`mimeType` |
| `test/.../data/MemoryRawDataTest.java` | `withVectorId(...)` / `withMetadata(...)` keep projected source fields consistent |
| `test/.../builder/MemoryAssemblersTest.java` | processor registration, parser/resource-store propagation, and option wiring |
| `test/.../data/DefaultInsightTypesTest.java` | multimodal content acceptance for user insight types |
| `test/.../extraction/item/strategy/LlmItemExtractionStrategyTest.java` | segment metadata merges into extracted item metadata while stripping legacy-only payloads |
| `test/.../prompt/extraction/item/MemoryItemUnifiedPromptsTest.java` | `<SourceText>` wrapper and content-neutral wording |
| `memind-plugin-jdbc/.../internal/schema/StoreSchemaBootstrapTest.java` | existing-db upgrade path applies `memory_resource` plus multimodal columns |
| `memind-plugin-jdbc/.../JdbcStoreTest.java` | runtime `ResourceOperations`/`ResourceStore` propagation |
| `memind-plugin-jdbc/.../SqliteMemoryStoreTest.java` | `memory_resource` CRUD and raw-data/resource consistency |
| `memind-plugin-jdbc-starter/.../JdbcPluginAutoConfigurationSqliteTest.java` | `ResourceOperations` and optional `ResourceStore` bean wiring |
| `memind-plugin-mybatis-plus-starter/.../schema/MemoryStoreDdlTest.java` | multimodal migration scripts are registered for each dialect |
| `memind-plugin-mybatis-plus-starter/.../schema/MemorySchemaAutoConfigurationTest.java` | auto-config exposes multimodal migration scripts |
| `memind-plugin-mybatis-plus-starter/.../converter/ResourceConverterTest.java` | `MemoryResource` mapping |
| `memind-plugin-mybatis-plus-starter/.../RawDataConverterTest.java` | new-field mapping |
| `memind-plugin-mybatis-plus-starter/.../MybatisPlusMemoryStoreBatchOperationsTest.java` | `MemoryResource` + `MemoryRawData` persistence fields |
| `memind-plugin-mybatis-plus-starter/.../MemoryStoreAutoConfigurationTest.java` | `ResourceOperations` and optional `ResourceStore` bean wiring |
| JDBC/MyBatis existing tests | constructor changes and new-field mapping |

Core test paths are relative to: `memind-core/src/test/java/com/openmemind/ai/memory/core/`

Plugin/starter tests are listed with module-prefixed paths.

---

## Key reuse points

- Reuse `TextChunker` for document and audio chunking, but do not fall back to "always one segment" for long inputs.
- Reuse `TextChunkingConfig` for document and audio defaults via `RawDataExtractionOptions`.
- Reuse `TruncateCaptionGenerator` for new multimodal processors unless a processor-specific captioner is later needed.
- Reuse `HashUtils.sampledSha256()` for stable multimodal content IDs and deterministic direct-input `resourceId` derivation when only `sourceUri` is available.
- Reuse `DefaultMemoryItemExtractor` and `LlmItemExtractionStrategy`.
  - The extractor stays generic.
  - Insight routing must still be updated through `DefaultInsightTypes`.
- Reuse `MemoryStore.resourceStore()` as the optional file-storage hook.
  - Do not add a second resource-store configuration path.
- Reuse `MemoryStore.resourceOperations()` and its aggregate raw-data/resource write path as the only relational resource persistence hook.
  - Do not add a side registry that bypasses `MemoryStore`.
- Reuse the unified item extraction prompt pipeline.
  - Only neutralize prompt wording and wrapper tags; do not fork a separate multimodal-only prompt stack in this phase.

---

## Tasks

### Task 1: Shared multimodal primitives and raw-data options

**Files:**
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/data/ContentTypes.java`
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/builder/RawDataExtractionOptions.java`
- Create: `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/content/document/DocumentSection.java`
- Create: `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/content/audio/TranscriptSegment.java`
- Create: `memind-core/src/main/java/com/openmemind/ai/memory/core/data/MemoryResource.java`
- Create: `memind-core/src/main/java/com/openmemind/ai/memory/core/resource/ContentParser.java`
- Create: `memind-core/src/main/java/com/openmemind/ai/memory/core/resource/ResourceRef.java`
- Create: `memind-core/src/main/java/com/openmemind/ai/memory/core/resource/ResourceStore.java`
- Create: `memind-core/src/main/java/com/openmemind/ai/memory/core/resource/LocalFileResourceStore.java`
- Create: `memind-core/src/main/java/com/openmemind/ai/memory/core/store/resource/ResourceOperations.java`
- Create: `memind-core/src/main/java/com/openmemind/ai/memory/core/store/resource/InMemoryResourceOperations.java`
- Modify: `memind-evaluation/src/main/java/com/openmemind/ai/memory/evaluation/config/EvaluationMemindConfiguration.java`
- Modify: `memind-core/src/test/java/com/openmemind/ai/memory/core/builder/MemoryAssemblersTest.java`
- Modify: `memind-core/src/test/java/com/openmemind/ai/memory/core/builder/MemoryBuildOptionsTest.java`
- Create: `memind-core/src/test/java/com/openmemind/ai/memory/core/resource/LocalFileResourceStoreTest.java`
- Create: `memind-core/src/test/java/com/openmemind/ai/memory/core/store/resource/InMemoryResourceOperationsTest.java`

- [ ] **Step 1: Add new content type constants**

Add to `ContentTypes.java` after `TOOL_CALL`:

```java
public static final String DOCUMENT = "DOCUMENT";
public static final String IMAGE = "IMAGE";
public static final String AUDIO = "AUDIO";
```

- [ ] **Step 2: Create `DocumentSection` and `TranscriptSegment`**

Keep them as immutable records with small convenience constructors:

```java
public record DocumentSection(
        String title,
        String content,
        int index,
        Map<String, Object> metadata) { ... }

public record TranscriptSegment(
        String text,
        Duration startTime,
        Duration endTime,
        String speaker) { ... }
```

- [ ] **Step 3: Define parser and resource-storage interfaces**

Use a parser API that can return modality-specific content instead of a generic text-only DTO:

```java
public interface ContentParser {
    Set<String> supportedMimeTypes();
    Mono<RawContent> parse(byte[] data, String fileName, String mimeType);
}
```

Keep `ResourceStore` unchanged in shape, but make `LocalFileResourceStore` reject unsafe file names:
- reject absolute paths
- reject `..`
- normalize the resolved path and ensure it stays under `baseDir`

- [ ] **Step 4: Add `MemoryResource` and `ResourceOperations`**

Create a relational metadata model separate from `ResourceRef`:

```java
public record MemoryResource(
        String id,
        String memoryId,
        String sourceUri,
        String storageUri,
        String fileName,
        String mimeType,
        String checksum,
        Long sizeBytes,
        Map<String, Object> metadata,
        Instant createdAt) { ... }
```

Create `ResourceOperations` with a small CRUD surface:

```java
public interface ResourceOperations {
    void upsertResources(MemoryId memoryId, List<MemoryResource> resources);
    Optional<MemoryResource> getResource(MemoryId memoryId, String resourceId);
    List<MemoryResource> listResources(MemoryId memoryId);
}
```

Rules:
- `MemoryResource.id` is the business ID referenced by `MemoryRawData.resourceId`
- `ResourceRef` stays the blob-store descriptor returned by `ResourceStore`, not the relational model
- `InMemoryResourceOperations` should upsert by `id` so tests and `InMemoryMemoryStore` have a usable default implementation

- [ ] **Step 5: Extend `RawDataExtractionOptions`**

Change the record shape from:

```java
public record RawDataExtractionOptions(
        ConversationChunkingConfig chunking,
        CommitDetectorConfig commitDetection)
```

to:

```java
public record RawDataExtractionOptions(
        ConversationChunkingConfig chunking,
        TextChunkingConfig documentChunking,
        TextChunkingConfig audioChunking,
        CommitDetectorConfig commitDetection,
        ContentParser contentParser)
```

Defaults:
- `documentChunking = TextChunkingConfig.DEFAULT`
- `audioChunking = TextChunkingConfig.DEFAULT`
- `contentParser = null`

- [ ] **Step 6: Update all direct `RawDataExtractionOptions` constructor call sites**

The current constructor call in [MemoryAssemblersTest.java](/Users/zhengyate/dev/openmemind/memind/memind-core/src/test/java/com/openmemind/ai/memory/core/builder/MemoryAssemblersTest.java) will no longer compile. Update all direct constructor usages to pass the new fields explicitly, including:
- [MemoryAssemblersTest.java](/Users/zhengyate/dev/openmemind/memind/memind-core/src/test/java/com/openmemind/ai/memory/core/builder/MemoryAssemblersTest.java)
- [MemoryBuildOptionsTest.java](/Users/zhengyate/dev/openmemind/memind/memind-core/src/test/java/com/openmemind/ai/memory/core/builder/MemoryBuildOptionsTest.java)
- [EvaluationMemindConfiguration.java](/Users/zhengyate/dev/openmemind/memind/memind-evaluation/src/main/java/com/openmemind/ai/memory/evaluation/config/EvaluationMemindConfiguration.java)

- [ ] **Step 7: Add resource-store and resource-ops tests**

`LocalFileResourceStoreTest` should cover:
- store and retrieve
- exists after store
- delete removes file
- invalid `fileName` such as `../escape.txt` throws `IllegalArgumentException`

Use `DefaultMemoryId.of("user1", "agent1")`, not the non-existent one-argument overload.

`InMemoryResourceOperationsTest` should cover:
- upsert then get by `resourceId`
- upsert overwrite semantics for the same `id`
- list results scoped by `MemoryId`

- [ ] **Step 8: Run targeted tests**

Run:

```bash
cd /Users/zhengyate/dev/openmemind/memind
mvn test -pl memind-core -Dtest="MemoryBuildOptionsTest,MemoryAssemblersTest,LocalFileResourceStoreTest,InMemoryResourceOperationsTest" -q
mvn -pl memind-evaluation -am -DskipTests compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 9: Commit**

```bash
git add memind-core/src/main/java/com/openmemind/ai/memory/core/data/ContentTypes.java \
  memind-core/src/main/java/com/openmemind/ai/memory/core/builder/RawDataExtractionOptions.java \
  memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/content/document/DocumentSection.java \
  memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/content/audio/TranscriptSegment.java \
  memind-core/src/main/java/com/openmemind/ai/memory/core/data/MemoryResource.java \
  memind-core/src/main/java/com/openmemind/ai/memory/core/resource/ContentParser.java \
  memind-core/src/main/java/com/openmemind/ai/memory/core/resource/ResourceRef.java \
  memind-core/src/main/java/com/openmemind/ai/memory/core/resource/ResourceStore.java \
  memind-core/src/main/java/com/openmemind/ai/memory/core/resource/LocalFileResourceStore.java \
  memind-core/src/main/java/com/openmemind/ai/memory/core/store/resource/ResourceOperations.java \
  memind-core/src/main/java/com/openmemind/ai/memory/core/store/resource/InMemoryResourceOperations.java \
  memind-evaluation/src/main/java/com/openmemind/ai/memory/evaluation/config/EvaluationMemindConfiguration.java \
  memind-core/src/test/java/com/openmemind/ai/memory/core/builder/MemoryAssemblersTest.java \
  memind-core/src/test/java/com/openmemind/ai/memory/core/builder/MemoryBuildOptionsTest.java \
  memind-core/src/test/java/com/openmemind/ai/memory/core/resource/LocalFileResourceStoreTest.java \
  memind-core/src/test/java/com/openmemind/ai/memory/core/store/resource/InMemoryResourceOperationsTest.java
git commit -m "feat: add multimodal primitives, parser hook, and resource metadata model"
```

---

### Task 2: Multimodal `RawContent` models and Jackson registration

**Files:**
- Create: `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/content/DocumentContent.java`
- Create: `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/content/ImageContent.java`
- Create: `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/content/AudioContent.java`
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/content/RawContent.java`
- Create: `memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/rawdata/content/DocumentContentTest.java`
- Create: `memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/rawdata/content/ImageContentTest.java`
- Create: `memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/rawdata/content/AudioContentTest.java`

- [ ] **Step 1: Implement `DocumentContent`, `ImageContent`, and `AudioContent`**

Required fields:
- `DocumentContent`: `title`, `mimeType`, `parsedText`, `sections`, `sourceUri`, `metadata`
- `ImageContent`: `mimeType`, `description`, `ocrText`, `sourceUri`, `metadata`
- `AudioContent`: `mimeType`, `transcript`, `segments`, `sourceUri`, `metadata`

Rules:
- `contentType()` returns the new uppercase constants
- `toContentString()` returns the text visible to downstream extraction
- `getContentId()` remains a text-only content fingerprint and should hash the same visible text returned by `toContentString()`
- `ImageContent.getContentId()` must include `ocrText` when present, not just `description`
- do not treat `getContentId()` as the final multimodal raw-data idempotency key; that key is resolved later from text fingerprint + normalized source identity
- use immutable defensive copies for lists/maps

- [ ] **Step 2: Register JSON subtype names in `RawContent`**

Update the annotation to:

```java
@JsonSubTypes({
    @JsonSubTypes.Type(value = ConversationContent.class, name = "conversation"),
    @JsonSubTypes.Type(value = ToolCallContent.class, name = "tool_call"),
    @JsonSubTypes.Type(value = DocumentContent.class, name = "document"),
    @JsonSubTypes.Type(value = ImageContent.class, name = "image"),
    @JsonSubTypes.Type(value = AudioContent.class, name = "audio")
})
```

Do not change the existing lowercase subtype names.

- [ ] **Step 3: Add model tests**

Each test suite should cover:
- `contentType()`
- `toContentString()`
- `getContentId()`
- optional collections default to empty
- `sourceUri` can be null
- `mimeType` can be null
- Jackson round-trip preserves the concrete subtype using lowercase `type`

- [ ] **Step 4: Run targeted tests**

Run:

```bash
cd /Users/zhengyate/dev/openmemind/memind
mvn test -pl memind-core -Dtest="DocumentContentTest,ImageContentTest,AudioContentTest" -q
```

Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/content/DocumentContent.java \
  memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/content/ImageContent.java \
  memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/content/AudioContent.java \
  memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/content/RawContent.java \
  memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/rawdata/content/DocumentContentTest.java \
  memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/rawdata/content/ImageContentTest.java \
  memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/rawdata/content/AudioContentTest.java
git commit -m "feat: add multimodal raw content models"
```

---

### Task 3: Multimodal processors with real chunking and stable boundaries

**Files:**
- Create: `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/processor/DocumentContentProcessor.java`
- Create: `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/processor/ImageContentProcessor.java`
- Create: `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/processor/AudioContentProcessor.java`
- Create: `memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/rawdata/processor/DocumentContentProcessorTest.java`
- Create: `memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/rawdata/processor/ImageContentProcessorTest.java`
- Create: `memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/rawdata/processor/AudioContentProcessorTest.java`

- [ ] **Step 1: Implement `DocumentContentProcessor` using `TextChunker`**

Constructor shape:

```java
public DocumentContentProcessor(
        TextChunker textChunker,
        TextChunkingConfig chunkingConfig)
```

Behavior:
- when `sections` is empty, chunk `parsedText` with `TextChunker`
- when `sections` is present, chunk section-by-section and accumulate a global cursor so `CharBoundary` remains monotonic
- attach section metadata such as `sectionIndex` and `sectionTitle`
- do not use fake `new CharBoundary(i, i + 1)` boundaries

- [ ] **Step 2: Implement `ImageContentProcessor`**

Behavior:
- always return one segment
- content is `toContentString()`
- boundary can be `Segment.single(...)`
- metadata should preserve `sourceUri` only if the caller already provided it through request metadata; do not duplicate content-owned metadata inside the processor

- [ ] **Step 3: Implement `AudioContentProcessor` using `TextChunker`**

Constructor shape:

```java
public AudioContentProcessor(
        TextChunker textChunker,
        TextChunkingConfig chunkingConfig)
```

Behavior:
- when `segments` is empty, chunk the full transcript with `TextChunker`
- when `segments` is present, merge adjacent transcript segments into text chunks up to `chunkingConfig.chunkSize()`
- each emitted segment should carry:
  - `startTimeMs`
  - `endTimeMs`
  - optional `speakers`
- boundaries should be char-based offsets into the emitted transcript text, not ordinal placeholders

- [ ] **Step 4: Add processor tests**

Cover:
- content class and content type
- long text produces multiple chunks for document/audio
- structured sections/segments preserve metadata
- boundaries are `CharBoundary`
- `supportsInsight()` remains `true`

- [ ] **Step 5: Run targeted tests**

Run:

```bash
cd /Users/zhengyate/dev/openmemind/memind
mvn test -pl memind-core -Dtest="DocumentContentProcessorTest,ImageContentProcessorTest,AudioContentProcessorTest" -q
```

Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/processor/DocumentContentProcessor.java \
  memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/processor/ImageContentProcessor.java \
  memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/processor/AudioContentProcessor.java \
  memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/rawdata/processor/DocumentContentProcessorTest.java \
  memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/rawdata/processor/ImageContentProcessorTest.java \
  memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/rawdata/processor/AudioContentProcessorTest.java
git commit -m "feat: add multimodal raw content processors"
```

---

### Task 4: Register processors explicitly and align default insight routing

**Files:**
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/builder/MemoryExtractionAssembler.java`
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/data/DefaultInsightTypes.java`
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/prompt/extraction/item/MemoryItemUnifiedPrompts.java`
- Modify: `memind-core/src/test/java/com/openmemind/ai/memory/core/builder/MemoryAssemblersTest.java`
- Create: `memind-core/src/test/java/com/openmemind/ai/memory/core/data/DefaultInsightTypesTest.java`
- Modify: `memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/item/strategy/LlmItemExtractionStrategyTest.java`
- Modify: `memind-core/src/test/java/com/openmemind/ai/memory/core/prompt/extraction/item/MemoryItemUnifiedPromptsTest.java`

- [ ] **Step 1: Register new processors in `MemoryExtractionAssembler`**

Update `createProcessors(...)` to instantiate and return:
- `ConversationContentProcessor`
- `ToolCallContentProcessor`
- `DocumentContentProcessor`
- `ImageContentProcessor`
- `AudioContentProcessor`

Pass:
- one shared `TextChunker`
- `options.extraction().rawdata().documentChunking()`
- `options.extraction().rawdata().audioChunking()`

Do not leave the registry as `List.of(conversationProcessor, toolCallProcessor)`.

- [ ] **Step 2: Expand user-scoped default insight type acceptance**

In `DefaultInsightTypes.java`:
- replace `CONVERSATION_ONLY` with a user-scoped textual content list:

```java
private static final List<String> USER_TEXTUAL_CONTENT_TYPES =
        List.of(ContentTypes.CONVERSATION, ContentTypes.DOCUMENT, ContentTypes.IMAGE, ContentTypes.AUDIO);
```

- use that list for `identity`, `preferences`, `relationships`, `experiences`, and `behavior`
- keep `directives`, `playbooks`, and `resolutions` conversation-only

- [ ] **Step 3: Neutralize unified prompt framing**

In `MemoryItemUnifiedPrompts.java`:
- change user prompt wording from "the following conversation" to "the following source text"
- replace the wrapper tag:

```xml
<SourceText>
{{CONVERSATION}}
</SourceText>
```

- keep message/timestamp-specific rules only where they describe one possible source-text format
- do not hard-code conversation-only framing in the prompt objective or user prompt

- [ ] **Step 4: Add tests**

`MemoryAssemblersTest` should assert the assembled `RawDataLayer` registry contains five processors.

`DefaultInsightTypesTest` should assert:
- user branches accept `DOCUMENT`, `IMAGE`, and `AUDIO`
- agent branches still accept only `CONVERSATION`

`LlmItemExtractionStrategyTest` should assert:
- segment metadata merges into extracted item metadata
- source-traceability keys such as `resourceId`, `sourceUri`, and `mimeType` are preserved
- legacy-only payloads such as `messages` are still stripped

`MemoryItemUnifiedPromptsTest` should assert:
- rendered user prompt uses `<SourceText>`
- rendered user prompt no longer uses `<Conversation>`
- system prompt keeps the existing extraction rules while avoiding conversation-only framing in the wrapper/instruction text

- [ ] **Step 5: Run targeted tests**

Run:

```bash
cd /Users/zhengyate/dev/openmemind/memind
mvn test -pl memind-core -Dtest="MemoryAssemblersTest,DefaultInsightTypesTest,LlmItemExtractionStrategyTest,MemoryItemUnifiedPromptsTest" -q
```

Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add memind-core/src/main/java/com/openmemind/ai/memory/core/builder/MemoryExtractionAssembler.java \
  memind-core/src/main/java/com/openmemind/ai/memory/core/data/DefaultInsightTypes.java \
  memind-core/src/main/java/com/openmemind/ai/memory/core/prompt/extraction/item/MemoryItemUnifiedPrompts.java \
  memind-core/src/test/java/com/openmemind/ai/memory/core/builder/MemoryAssemblersTest.java \
  memind-core/src/test/java/com/openmemind/ai/memory/core/data/DefaultInsightTypesTest.java \
  memind-core/src/test/java/com/openmemind/ai/memory/core/prompt/extraction/item/MemoryItemUnifiedPromptsTest.java
git commit -m "feat: register multimodal processors and insight routing"
```

---

### Task 5: Multimodal extraction requests and raw-file ingestion

**Files:**
- Create: `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/RawFileInput.java`
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/ExtractionRequest.java`
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/MemoryExtractor.java`
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/store/MemoryStore.java`
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/store/InMemoryMemoryStore.java`
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/builder/MemoryExtractionAssembler.java`
- Create: `memind-core/src/test/java/com/openmemind/ai/memory/core/store/MemoryStoreTest.java`
- Create: `memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/ExtractionRequestTest.java`
- Create: `memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/MemoryExtractorMultimodalFileTest.java`
- Modify: `memind-core/src/test/java/com/openmemind/ai/memory/core/builder/MemoryAssemblersTest.java`

- [ ] **Step 1: Add `RawFileInput`**

Create:

```java
public record RawFileInput(
        String fileName,
        byte[] data,
        String mimeType) { ... }
```

Rules:
- validate non-null `fileName`, `data`, `mimeType`
- defensive-copy `byte[]`

- [ ] **Step 2: Extend `ExtractionRequest`**

Change the record shape to carry either direct content or a raw-file payload:

```java
public record ExtractionRequest(
        MemoryId memoryId,
        RawContent content,
        RawFileInput fileInput,
        String contentType,
        Map<String, Object> metadata,
        ExtractionConfig config)
```

Add factories:
- `document(MemoryId, DocumentContent)`
- `image(MemoryId, ImageContent)`
- `audio(MemoryId, AudioContent)`
- `file(MemoryId, String fileName, byte[] data, String mimeType)`

Direct multimodal factories must normalize source metadata eagerly:
- start from a defensive copy of `content.metadata()`
- add `sourceUri` when `content.sourceUri()` is present
- add `mimeType` from `DocumentContent` / `ImageContent` / `AudioContent` when present
- preserve explicit request metadata semantics by storing the normalized map on the request itself

Keep `conversation`, `text`, `toolCall`, and `of` working.
Keep `of(...)` generic; do not add modality-specific inference there.
For direct multimodal content, prefer `document(...)` / `image(...)` / `audio(...)` over `of(...)` so metadata normalization is not bypassed.
Update `withConfig(...)` and `withMetadata(...)` to preserve `fileInput`.

- [ ] **Step 3: Extend `MemoryStore`**

Add:

```java
ResourceOperations resourceOperations();

default ResourceStore resourceStore() { return null; }

default void upsertRawDataWithResources(
        MemoryId memoryId,
        List<MemoryResource> resources,
        List<MemoryRawData> rawDataList) {
    if (resources != null && !resources.isEmpty()) {
        resourceOperations().upsertResources(memoryId, resources);
    }
    rawDataOperations().upsertRawData(memoryId, rawDataList);
}
```

Also add an overload:

```java
static MemoryStore of(
        RawDataOperations rawDataOperations,
        ItemOperations itemOperations,
        InsightOperations insightOperations,
        ResourceOperations resourceOperations,
        ResourceStore resourceStore)
```

Keep the old three-argument `of(...)` only as a fail-fast compatibility overload:
- delegate to a `ResourceOperations` implementation whose methods throw `IllegalStateException("ResourceOperations is required for multimodal persistence; use MemoryStore.of(..., resourceOperations, resourceStore)")`
- do not silently no-op on multimodal writes
- update all real runtime creation paths to use the new overload
Update `InMemoryMemoryStore` to expose `InMemoryResourceOperations`.
If `resourceStore` is `AutoCloseable`, include it in `close()`.

- [ ] **Step 4: Implement parser-aware raw-file ingestion in `MemoryExtractor`**

Add `ContentParser` and `ResourceStore` fields to `MemoryExtractor`, and extend the constructor chain so the assembled runtime can pass both hooks into it.
Keep the existing public constructors delegating to the new full constructor with `null` parser and `null` resource store so current call sites remain source-compatible while the assembler adopts the new path.

Update `extract(ExtractionRequest request)` so it accepts:
- direct `content`
- or `fileInput`

Add a private helper with this shape:

```java
private Mono<ExtractionRequest> resolveRawFileRequest(ExtractionRequest request)
```

Behavior:
- resolve `fileInput` before enforcing `request.content() != null`
- if `fileInput == null`, return `Mono.just(request)`
- if no parser is configured, fail with `IllegalStateException`
- if parser does not support the MIME type, fail with `IllegalArgumentException`
- parse bytes into `RawContent`
- if parsing fails, do not persist any resource bytes
- derive file-level `checksum` and `sizeBytes` for normalized metadata regardless of whether `ResourceStore` is configured
- after parsing succeeds, store the bytes only when injected `resourceStore` is present
  - keep the returned `ResourceRef` available for compensation while the request is still in the raw-data/resource persistence phase
- ensure file requests always end with a stable `resourceId`
  - when `resourceStore` returns a `ResourceRef`, reuse `ref.id()`
  - otherwise derive one from `memoryId.toIdentifier()`, `fileName`, and `checksum`
- normalize parsed multimodal metadata using the same helper/policy as `ExtractionRequest.document/image/audio(...)`
  - start from a copy of parsed content metadata when available
  - then overlay file-level source metadata
- enrich metadata with:
  - `resourceId`
  - `mimeType`
  - `sourceUri` when available
  - `storageUri` when a resource was stored
  - `fileName`
  - `checksum`
  - `sizeBytes`
- return a new request with `content != null` and `fileInput == null`

Compensation rules:
- if failure happens before `extractRawData(...)` successfully persists via `memoryStore.upsertRawDataWithResources(...)`, perform a best-effort delete of the just-stored bytes before surfacing the error
- once raw-data/resource persistence succeeds, do **not** delete the blob because later item/insight failures must not turn persisted DB references into dangling URIs

- [ ] **Step 5: Wire parser and resource-store injection through `MemoryExtractionAssembler`**

When constructing `MemoryExtractor`, pass:

```java
context.options().extraction().rawdata().contentParser()
```

and:

```java
context.memoryStore().resourceStore()
```

Do not leave either hook stranded with no runtime path into `MemoryExtractor`.

- [ ] **Step 6: Add request/extractor tests**

`ExtractionRequestTest`:
- direct factories set `contentType` correctly
- direct multimodal factories copy `content.metadata()` into request metadata
- direct multimodal factories project `sourceUri` and `mimeType` into request metadata when available, including image/audio direct requests
- `file(...)` stores `RawFileInput`
- `withConfig(...)` and `withMetadata(...)` keep `fileInput`

`MemoryExtractorMultimodalFileTest`:
- parser is called for `file(...)`
- optional `ResourceStore` is used when configured
- missing parser fails fast
- unsupported MIME type fails fast
- parsed multimodal content metadata is preserved on the resolved request metadata
- parse failure does not leave an orphaned stored resource
- raw-data/resource persistence failure triggers best-effort cleanup of a newly stored resource
- item/insight-stage failure after raw-data persistence does not delete an already-persisted blob

`MemoryStoreTest`:
- the legacy three-argument `MemoryStore.of(...)` throws `IllegalStateException` when multimodal resource persistence tries to access `resourceOperations()`
- it does not silently discard `MemoryResource` writes

`MemoryAssemblersTest`:
- assembled `MemoryExtractor` receives the configured parser from `RawDataExtractionOptions`
- assembled `MemoryExtractor` receives the resolved `ResourceStore` hook from `MemoryStore`
- assembled `MemoryStore` still exposes non-null `resourceOperations()`

- [ ] **Step 7: Run targeted tests**

Run:

```bash
cd /Users/zhengyate/dev/openmemind/memind
mvn test -pl memind-core -Dtest="MemoryStoreTest,ExtractionRequestTest,MemoryExtractorMultimodalFileTest,MemoryAssemblersTest" -q
```

Expected: BUILD SUCCESS

- [ ] **Step 8: Commit**

```bash
git add memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/RawFileInput.java \
  memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/ExtractionRequest.java \
  memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/MemoryExtractor.java \
  memind-core/src/main/java/com/openmemind/ai/memory/core/store/MemoryStore.java \
  memind-core/src/main/java/com/openmemind/ai/memory/core/store/InMemoryMemoryStore.java \
  memind-core/src/main/java/com/openmemind/ai/memory/core/builder/MemoryExtractionAssembler.java \
  memind-core/src/test/java/com/openmemind/ai/memory/core/store/MemoryStoreTest.java \
  memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/ExtractionRequestTest.java \
  memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/MemoryExtractorMultimodalFileTest.java \
  memind-core/src/test/java/com/openmemind/ai/memory/core/builder/MemoryAssemblersTest.java
git commit -m "feat: add multimodal extraction requests and resource-aware file ingestion"
```

---

### Task 6: Persist source metadata in `RawDataLayer` and `MemoryRawData`

**Files:**
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/data/MemoryRawData.java`
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/RawDataLayer.java`
- Create: `memind-core/src/test/java/com/openmemind/ai/memory/core/data/MemoryRawDataTest.java`
- Modify: `memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/rawdata/RawDataLayerProcessorTest.java`
- Modify: constructor call sites that use `new MemoryRawData(...)`

- [ ] **Step 1: Extend `MemoryRawData`**

Add nullable fields after `metadata`:

```java
String resourceId,
String mimeType,
```

Update:
- canonical constructor call sites
- `withVectorId(...)`
- `withMetadata(...)`

Rules for derived methods:
- `withVectorId(...)` must preserve or recompute `resourceId` / `mimeType` consistently from the merged metadata
- `withMetadata(...)` must recompute `resourceId` / `mimeType` from the replacement metadata instead of keeping stale projected fields

- [ ] **Step 2: Merge request metadata and resolve `MemoryResource` inside `RawDataLayer`**

The current implementation drops `RawDataInput.metadata()` on the floor.

Before the existing-db fast path, resolve a request-aware raw-data identity key:

```java
private String resolveRawDataContentId(RawDataInput input)
```

Rules:
- start from `input.content().getContentId()`
- for `CONVERSATION` and `TOOL_CALL`, keep the existing text-only key
- for `DOCUMENT`, `IMAGE`, and `AUDIO`, fold in a normalized source identity when present:
  - prefer `resourceId`
  - else `sourceUri`
  - else `storageUri`
  - else `fileName` + `checksum`
- build the final multimodal key from `contentType + textContentId + sourceIdentity`
- use this resolved key for:
  - `getRawDataByContentId(...)` idempotency checks
  - new `MemoryRawData.contentId`
  - any `process(...)` / `extract(...)` path that currently forwards bare `content.getContentId()`
- do not let two different sources with identical parsed text collapse onto the same raw-data row

Inside `buildAndPersist(...)`, first create request-level normalized metadata:

```java
Map<String, Object> requestMetadata = new LinkedHashMap<>();
requestMetadata.putAll(input.metadata());
```

Resolve a relational resource record once from `requestMetadata` before building any `MemoryRawData` rows:

```java
private Optional<MemoryResource> resolveResource(
        MemoryId memoryId,
        Map<String, Object> requestMetadata,
        Instant now)
```

Rules:
- if `requestMetadata` already contains `resourceId`, use it
- else if `requestMetadata` contains `sourceUri`, derive a stable `resourceId` from `memoryId.toIdentifier()` + `sourceUri` via `HashUtils.sampledSha256(...)`
- else if `requestMetadata` contains both `fileName` and `checksum`, derive a stable `resourceId` from those file-level fields
- if none of `resourceId`, `sourceUri`/`storageUri`, or `fileName` + `checksum` is present, skip resource creation for that request
- build `MemoryResource` from normalized keys such as `resourceId`, `sourceUri`, `storageUri`, `fileName`, `mimeType`, `checksum`, and `sizeBytes`
- do not copy segment-only keys such as `vectorId`, chunk boundaries, or timestamp projections into `MemoryResource.metadata`
- after resource resolution, build segment metadata with:

```java
Map<String, Object> mergedMetadata = new LinkedHashMap<>();
mergedMetadata.putAll(requestMetadata);
mergedMetadata.putAll(segment.metadata());
```

- write the final `resourceId` and `mimeType` into each `mergedMetadata` before constructing `MemoryRawData`
- deduplicate resolved resources by `resourceId` before `memoryStore.upsertRawDataWithResources(...)`
- persist resources and raw data via `memoryStore.upsertRawDataWithResources(...)` rather than calling `rawDataOperations()` directly

- [ ] **Step 3: Keep segment runtime behavior unchanged**

Do not regress:
- timestamp resolution for conversation segments
- vector ID injection
- `processSegment(...)` fast path

- [ ] **Step 4: Update all `new MemoryRawData(...)` call sites**

Search globally:

```bash
rg -n "new MemoryRawData\\(" -S
```

Expected updates include:
- `memind-core/.../RawDataLayer.java`
- `memind-core` tests
- JDBC store tests
- MyBatis converter tests
- server tests

Do not stop at `RawDataLayer` and `RawDataConverter`.

- [ ] **Step 5: Add `MemoryRawDataTest` and extend `RawDataLayerProcessorTest`**

`MemoryRawDataTest` should assert:
- `withVectorId(...)` keeps `resourceId` / `mimeType` aligned with merged metadata
- `withMetadata(...)` updates `resourceId` / `mimeType` when replacement metadata changes those keys

Add assertions that:
- request metadata is merged into persisted raw-data metadata
- direct multimodal metadata with only `sourceUri` derives a stable `resourceId`
- file-style metadata with `fileName` + `checksum` still derives a stable `resourceId`
- same parsed multimodal text with different `sourceUri` or `resourceId` resolves to different raw-data `contentId` values and does not hit the existing-raw-data fast path
- resolved `MemoryResource` records are passed into `memoryStore.upsertRawDataWithResources(...)`
- `resourceId` and `mimeType` are persisted on `MemoryRawData`

- [ ] **Step 6: Run targeted tests**

Run:

```bash
cd /Users/zhengyate/dev/openmemind/memind
mvn test -pl memind-core -Dtest="MemoryRawDataTest,RawDataLayerProcessorTest" -q
```

Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add memind-core/src/main/java/com/openmemind/ai/memory/core/data/MemoryRawData.java \
  memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/RawDataLayer.java \
  memind-core/src/test/java/com/openmemind/ai/memory/core/data/MemoryRawDataTest.java \
  memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/rawdata/RawDataLayerProcessorTest.java
git commit -m "feat: persist multimodal source metadata and resource references"
```

---

### Task 7: JDBC and MyBatis persistence updates

**Files:**
- Create: `memind-plugins/memind-plugin-jdbc/src/main/resources/db/jdbc/mysql/store/V2__multimodal.sql`
- Create: `memind-plugins/memind-plugin-jdbc/src/main/resources/db/jdbc/postgresql/store/V2__multimodal.sql`
- Create: `memind-plugins/memind-plugin-jdbc/src/main/resources/db/jdbc/sqlite/store/V2__multimodal.sql`
- Modify: `memind-plugins/memind-plugin-jdbc/src/main/java/com/openmemind/ai/memory/plugin/jdbc/internal/schema/StoreSchemaBootstrap.java`
- Modify: `memind-plugins/memind-plugin-jdbc/src/main/java/com/openmemind/ai/memory/plugin/jdbc/internal/schema/SchemaVerifier.java`
- Modify: `memind-plugins/memind-plugin-jdbc/src/main/java/com/openmemind/ai/memory/plugin/jdbc/mysql/MysqlMemoryStore.java`
- Modify: `memind-plugins/memind-plugin-jdbc/src/main/java/com/openmemind/ai/memory/plugin/jdbc/postgresql/PostgresqlMemoryStore.java`
- Modify: `memind-plugins/memind-plugin-jdbc/src/main/java/com/openmemind/ai/memory/plugin/jdbc/sqlite/SqliteMemoryStore.java`
- Modify: `memind-plugins/memind-plugin-jdbc/src/main/java/com/openmemind/ai/memory/plugin/jdbc/JdbcStore.java`
- Create: `memind-plugins/memind-plugin-jdbc/src/test/java/com/openmemind/ai/memory/plugin/jdbc/internal/schema/StoreSchemaBootstrapTest.java`
- Modify: `memind-plugins/memind-plugin-jdbc/src/test/java/com/openmemind/ai/memory/plugin/jdbc/JdbcStoreTest.java`
- Modify: `memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-jdbc-starter/src/main/java/com/openmemind/ai/memory/plugin/jdbc/autoconfigure/JdbcPluginAutoConfiguration.java`
- Modify: `memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-jdbc-starter/src/test/java/com/openmemind/ai/memory/plugin/jdbc/autoconfigure/JdbcPluginAutoConfigurationSqliteTest.java`
- Modify: `memind-plugins/memind-plugin-jdbc/src/test/java/com/openmemind/ai/memory/plugin/jdbc/sqlite/SqliteMemoryStoreTest.java`
- Create: `memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-mybatis-plus-starter/src/main/resources/db/migration/mysql/V3__multimodal.sql`
- Create: `memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-mybatis-plus-starter/src/main/resources/db/migration/postgresql/V3__multimodal.sql`
- Create: `memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-mybatis-plus-starter/src/main/resources/db/migration/sqlite/V3__multimodal.sql`
- Modify: `memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-mybatis-plus-starter/src/main/java/com/openmemind/ai/memory/plugin/store/mybatis/schema/DatabaseDialect.java`
- Modify: `memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-mybatis-plus-starter/src/main/java/com/openmemind/ai/memory/plugin/store/mybatis/MybatisPlusMemoryStore.java`
- Create: `memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-mybatis-plus-starter/src/main/java/com/openmemind/ai/memory/plugin/store/mybatis/dataobject/MemoryResourceDO.java`
- Modify: `memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-mybatis-plus-starter/src/main/java/com/openmemind/ai/memory/plugin/store/mybatis/dataobject/MemoryRawDataDO.java`
- Create: `memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-mybatis-plus-starter/src/main/java/com/openmemind/ai/memory/plugin/store/mybatis/converter/ResourceConverter.java`
- Modify: `memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-mybatis-plus-starter/src/main/java/com/openmemind/ai/memory/plugin/store/mybatis/converter/RawDataConverter.java`
- Create: `memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-mybatis-plus-starter/src/main/java/com/openmemind/ai/memory/plugin/store/mybatis/mapper/MemoryResourceMapper.java`
- Modify: `memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-mybatis-plus-starter/src/main/java/com/openmemind/ai/memory/plugin/store/mybatis/MemoryMybatisPlusAutoConfiguration.java`
- Modify: `memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-mybatis-plus-starter/src/test/java/com/openmemind/ai/memory/plugin/store/mybatis/schema/MemoryStoreDdlTest.java`
- Modify: `memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-mybatis-plus-starter/src/test/java/com/openmemind/ai/memory/plugin/store/mybatis/schema/MemorySchemaAutoConfigurationTest.java`
- Create: `memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-mybatis-plus-starter/src/test/java/com/openmemind/ai/memory/plugin/store/mybatis/converter/ResourceConverterTest.java`
- Modify: `memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-mybatis-plus-starter/src/test/java/com/openmemind/ai/memory/plugin/store/mybatis/converter/RawDataConverterTest.java`
- Modify: `memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-mybatis-plus-starter/src/test/java/com/openmemind/ai/memory/plugin/store/mybatis/MybatisPlusMemoryStoreBatchOperationsTest.java`
- Modify: `memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-mybatis-plus-starter/src/test/java/com/openmemind/ai/memory/plugin/store/mybatis/MemoryStoreAutoConfigurationTest.java`
- Modify: MyBatis starter/server tests that construct `MemoryRawData` or `MemoryResource`

- [ ] **Step 1: Create JDBC V2 migrations for `memory_resource` and raw-data references**

For MySQL, PostgreSQL, and SQLite, each `V2__multimodal.sql` must:
- create `memory_resource` using the existing store-table style for that dialect
- include relational metadata columns at minimum:
  - `biz_id`
  - `user_id`
  - `agent_id`
  - `memory_id`
  - `source_uri`
  - `storage_uri`
  - `file_name`
  - `mime_type`
  - `checksum`
  - `size_bytes`
  - `metadata`
  - `created_at`
  - `updated_at`
  - `deleted`
- add `uk_resource_biz_id (user_id, agent_id, biz_id)`
- add `idx_resource_memory_id (user_id, agent_id)`
- add `resource_id` and `mime_type` to `memory_raw_data`
- add `idx_raw_data_resource_id (user_id, agent_id, resource_id)`

Do not add hard foreign keys in this phase; keep `memory_raw_data.resource_id` as a logical reference to `memory_resource.biz_id`, consistent with the rest of the schema.

- [ ] **Step 2: Update JDBC schema bootstrap to apply V2 for existing databases**

Current JDBC schema bootstrap is not directory-scanning; it hard-codes `V1__init.sql`. Update it so multimodal resource/raw-data schema changes are applied for both fresh and pre-existing databases:
- `StoreSchemaBootstrap` should ensure base schema first, then repair missing `memory_resource` tables, columns, and indexes plus missing raw-data columns/indexes
- `SchemaVerifier` should gain the table/column/index existence helpers needed for SQLite/MySQL/PostgreSQL gating
- fresh databases should end up with the same final schema as upgraded databases
- existing databases that already have `memory_raw_data` but lack `memory_resource` must not be skipped
- do not blindly rerun the whole `V2__multimodal.sql` in mixed states such as:
  - `memory_resource` exists but `size_bytes` does not
  - `resource_id` exists but `mime_type` does not
  - both tables/columns exist but `idx_raw_data_resource_id` is missing
- bootstrap logic must apply only the missing table/column/index operations, or otherwise use an equivalent split-script strategy that is safe for partial upgrades

- [ ] **Step 3: Update JDBC stores for resource metadata and combined persistence**

For all three stores:
- implement `ResourceOperations` on the same store class that already handles raw data
- add resource upsert/get/list SQL for `memory_resource`
- add `resource_id` and `mime_type` to the raw-data insert column list
- bind them in `bindRawDataUpsert(...)`
- update the conflict clause to write both columns
- map them back in `mapRawData(...)`
- add a store-local helper such as `upsertResourcesAndRawData(...)` so `JdbcStore` can persist `memory_resource` and `memory_raw_data` on the same connection/transaction

- [ ] **Step 4: Wire resource-aware aggregate persistence through real runtime factory paths**

Update all runtime creation paths that currently call `MemoryStore.of(rawData, item, insight)`:
- [JdbcStore.java](/Users/zhengyate/dev/openmemind/memind/memind-plugins/memind-plugin-jdbc/src/main/java/com/openmemind/ai/memory/plugin/jdbc/JdbcStore.java)
- [JdbcPluginAutoConfiguration.java](/Users/zhengyate/dev/openmemind/memind/memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-jdbc-starter/src/main/java/com/openmemind/ai/memory/plugin/jdbc/autoconfigure/JdbcPluginAutoConfiguration.java)
- [MemoryMybatisPlusAutoConfiguration.java](/Users/zhengyate/dev/openmemind/memind/memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-mybatis-plus-starter/src/main/java/com/openmemind/ai/memory/plugin/store/mybatis/MemoryMybatisPlusAutoConfiguration.java)

Rules:
- always pass a real `ResourceOperations` implementation into `MemoryStore.of(...)`
- if no `ResourceStore` is available, pass `null`
- if a `ResourceStore` bean is available in Spring auto-config, propagate it into `MemoryStore.of(...)`
- `JdbcStore` must expose public creation overloads that accept a `ResourceStore`, then thread it through the shared internal `create(...)` path
- `JdbcStore` and the MyBatis auto-config path should override `MemoryStore.upsertRawDataWithResources(...)` so resource rows and raw-data rows are written consistently
- do not introduce a second independent file-storage config object in plugins

- [ ] **Step 5: Add MyBatis starter multimodal migration scripts and register them**

Current MyBatis starter schema init also uses a fixed script list rather than directory scanning. Add:
- `db/migration/sqlite/V3__multimodal.sql`
- `db/migration/mysql/V3__multimodal.sql`
- `db/migration/postgresql/V3__multimodal.sql`

Then update `DatabaseDialect.java` so each dialect returns:
- store init
- text-search init
- multimodal resource/raw-data migration

Do not rely on "file exists in resources" alone; it must be present in the explicit `scriptPaths()` list.

- [ ] **Step 6: Update MyBatis data objects, mappers, converters, and store**

Add `MemoryResourceDO` and `MemoryResourceMapper` for `memory_resource`.

`MemoryResourceDO` should carry the same persisted fields as the JDBC table: `bizId`, `memoryId`, `sourceUri`, `storageUri`, `fileName`, `mimeType`, `checksum`, `sizeBytes`, `metadata`, and timestamps.

Add `ResourceConverter`:
- map `MemoryResource` to `MemoryResourceDO`
- map `MemoryResourceDO` back to `MemoryResource`

`MemoryRawDataDO`:

```java
@TableField("resource_id")
private String resourceId;

@TableField("mime_type")
private String mimeType;
```

`RawDataConverter`:
- set `resourceId` and `mimeType` in `toDO(...)`
- read them in `toRecord(...)`

Update `MybatisPlusMemoryStore` to:
- implement `ResourceOperations`
- persist/query `memory_resource`
- expose a transactional helper that writes `MemoryResource` rows and `MemoryRawData` rows together for the `MemoryStore` aggregate override

- [ ] **Step 7: Update affected tests**

At minimum:
- `StoreSchemaBootstrapTest`
- `JdbcStoreTest`
- `JdbcPluginAutoConfigurationSqliteTest`
- `SqliteMemoryStoreTest`
- `MemoryStoreDdlTest`
- `MemorySchemaAutoConfigurationTest`
- `ResourceConverterTest`
- `RawDataConverterTest`
- `MybatisPlusMemoryStoreBatchOperationsTest`
- `MemoryStoreAutoConfigurationTest`
- any server tests that instantiate `MemoryRawData` or `MemoryResource`

`StoreSchemaBootstrapTest` should cover:
- fresh database initialization reaches the final multimodal schema
- existing V1 database upgrades to include `memory_resource`, raw-data columns, and the resource index
- mixed states repair correctly without failing on already-present tables/columns

`SqliteMemoryStoreTest` should cover:
- `memory_resource` upsert/get/list behavior
- combined `memory_resource` + `memory_raw_data` persistence keeps `resource_id` aligned

`MemoryStoreAutoConfigurationTest` and `JdbcStoreTest` should assert the built aggregate exposes:
- non-null `resourceOperations()`
- the optional `resourceStore()` hook when configured

- [ ] **Step 8: Run persistence-focused tests**

Run:

```bash
cd /Users/zhengyate/dev/openmemind/memind
mvn test -pl memind-plugins/memind-plugin-jdbc -am -Dtest="StoreSchemaBootstrapTest,JdbcStoreTest,SqliteMemoryStoreTest" -q
mvn test -pl memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-jdbc-starter -am -Dtest=JdbcPluginAutoConfigurationSqliteTest -q
mvn test -pl memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-mybatis-plus-starter -am -Dtest="MemoryStoreDdlTest,MemorySchemaAutoConfigurationTest,ResourceConverterTest,RawDataConverterTest,MybatisPlusMemoryStoreBatchOperationsTest,MemoryStoreAutoConfigurationTest" -q
```

Expected: BUILD SUCCESS

- [ ] **Step 9: Commit**

```bash
git add memind-plugins/memind-plugin-jdbc/src/main/resources/db/jdbc/mysql/store/V2__multimodal.sql \
  memind-plugins/memind-plugin-jdbc/src/main/resources/db/jdbc/postgresql/store/V2__multimodal.sql \
  memind-plugins/memind-plugin-jdbc/src/main/resources/db/jdbc/sqlite/store/V2__multimodal.sql \
  memind-plugins/memind-plugin-jdbc/src/main/java/com/openmemind/ai/memory/plugin/jdbc/internal/schema/StoreSchemaBootstrap.java \
  memind-plugins/memind-plugin-jdbc/src/main/java/com/openmemind/ai/memory/plugin/jdbc/internal/schema/SchemaVerifier.java \
  memind-plugins/memind-plugin-jdbc/src/main/java/com/openmemind/ai/memory/plugin/jdbc/mysql/MysqlMemoryStore.java \
  memind-plugins/memind-plugin-jdbc/src/main/java/com/openmemind/ai/memory/plugin/jdbc/postgresql/PostgresqlMemoryStore.java \
  memind-plugins/memind-plugin-jdbc/src/main/java/com/openmemind/ai/memory/plugin/jdbc/sqlite/SqliteMemoryStore.java \
  memind-plugins/memind-plugin-jdbc/src/main/java/com/openmemind/ai/memory/plugin/jdbc/JdbcStore.java \
  memind-plugins/memind-plugin-jdbc/src/test/java/com/openmemind/ai/memory/plugin/jdbc/internal/schema/StoreSchemaBootstrapTest.java \
  memind-plugins/memind-plugin-jdbc/src/test/java/com/openmemind/ai/memory/plugin/jdbc/JdbcStoreTest.java \
  memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-jdbc-starter/src/main/java/com/openmemind/ai/memory/plugin/jdbc/autoconfigure/JdbcPluginAutoConfiguration.java \
  memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-jdbc-starter/src/test/java/com/openmemind/ai/memory/plugin/jdbc/autoconfigure/JdbcPluginAutoConfigurationSqliteTest.java \
  memind-plugins/memind-plugin-jdbc/src/test/java/com/openmemind/ai/memory/plugin/jdbc/sqlite/SqliteMemoryStoreTest.java \
  memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-mybatis-plus-starter/src/main/resources/db/migration/mysql/V3__multimodal.sql \
  memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-mybatis-plus-starter/src/main/resources/db/migration/postgresql/V3__multimodal.sql \
  memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-mybatis-plus-starter/src/main/resources/db/migration/sqlite/V3__multimodal.sql \
  memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-mybatis-plus-starter/src/main/java/com/openmemind/ai/memory/plugin/store/mybatis/schema/DatabaseDialect.java \
  memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-mybatis-plus-starter/src/main/java/com/openmemind/ai/memory/plugin/store/mybatis/MybatisPlusMemoryStore.java \
  memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-mybatis-plus-starter/src/main/java/com/openmemind/ai/memory/plugin/store/mybatis/dataobject/MemoryResourceDO.java \
  memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-mybatis-plus-starter/src/main/java/com/openmemind/ai/memory/plugin/store/mybatis/dataobject/MemoryRawDataDO.java \
  memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-mybatis-plus-starter/src/main/java/com/openmemind/ai/memory/plugin/store/mybatis/converter/ResourceConverter.java \
  memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-mybatis-plus-starter/src/main/java/com/openmemind/ai/memory/plugin/store/mybatis/converter/RawDataConverter.java \
  memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-mybatis-plus-starter/src/main/java/com/openmemind/ai/memory/plugin/store/mybatis/mapper/MemoryResourceMapper.java \
  memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-mybatis-plus-starter/src/main/java/com/openmemind/ai/memory/plugin/store/mybatis/MemoryMybatisPlusAutoConfiguration.java \
  memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-mybatis-plus-starter/src/test/java/com/openmemind/ai/memory/plugin/store/mybatis/schema/MemoryStoreDdlTest.java \
  memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-mybatis-plus-starter/src/test/java/com/openmemind/ai/memory/plugin/store/mybatis/schema/MemorySchemaAutoConfigurationTest.java \
  memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-mybatis-plus-starter/src/test/java/com/openmemind/ai/memory/plugin/store/mybatis/converter/ResourceConverterTest.java \
  memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-mybatis-plus-starter/src/test/java/com/openmemind/ai/memory/plugin/store/mybatis/converter/RawDataConverterTest.java \
  memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-mybatis-plus-starter/src/test/java/com/openmemind/ai/memory/plugin/store/mybatis/MybatisPlusMemoryStoreBatchOperationsTest.java \
  memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-mybatis-plus-starter/src/test/java/com/openmemind/ai/memory/plugin/store/mybatis/MemoryStoreAutoConfigurationTest.java
git commit -m "feat: persist multimodal resources in jdbc and mybatis"
```

---

### Task 8: Full verification

- [ ] **Step 1: Run all `memind-core` tests**

Run:

```bash
cd /Users/zhengyate/dev/openmemind/memind
mvn test -pl memind-core
```

Expected: BUILD SUCCESS

- [ ] **Step 2: Run all affected plugin tests**

Run:

```bash
cd /Users/zhengyate/dev/openmemind/memind
mvn test -pl memind-plugins/memind-plugin-jdbc -am -q
mvn test -pl memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-jdbc-starter -am -q
mvn test -pl memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-mybatis-plus-starter -am -q
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Run full project compile**

Run:

```bash
cd /Users/zhengyate/dev/openmemind/memind
mvn clean compile -q
```

Expected: BUILD SUCCESS across all modules

- [ ] **Step 4: Run focused multimodal regression suite**

Run:

```bash
cd /Users/zhengyate/dev/openmemind/memind
mvn test -pl memind-core -Dtest="DocumentContentTest,ImageContentTest,AudioContentTest,DocumentContentProcessorTest,ImageContentProcessorTest,AudioContentProcessorTest,LocalFileResourceStoreTest,InMemoryResourceOperationsTest,MemoryStoreTest,ExtractionRequestTest,MemoryExtractorMultimodalFileTest,MemoryRawDataTest,RawDataLayerProcessorTest,MemoryAssemblersTest,DefaultInsightTypesTest,LlmItemExtractionStrategyTest,MemoryItemUnifiedPromptsTest" -q
```

Expected: BUILD SUCCESS

---

## Verification checklist

After implementation completes, confirm all of the following:

1. Direct multimodal requests work:
   - `ExtractionRequest.document(...)`
   - `ExtractionRequest.image(...)`
   - `ExtractionRequest.audio(...)`
   - direct factories normalize `content.metadata()`, `sourceUri`, and `mimeType` into request metadata when available
2. Raw-file requests work:
   - `ExtractionRequest.file(...)`
   - parser is required and invoked
   - resource store is optional
   - parse failure does not leave an orphaned stored resource
   - raw-data/resource persistence failure best-effort cleans up a newly stored resource
   - item/insight-stage failure after raw-data persistence does not delete an already-persisted blob
3. `RawDataExtractionOptions.contentParser()` and the assembled `ResourceStore` hook are propagated through `MemoryExtractionAssembler` into `MemoryExtractor`.
4. `MemoryStore.resourceOperations()` is wired in every real runtime creation path; the old three-argument `MemoryStore.of(...)` remains only as a fail-fast compatibility fallback.
5. New processors are explicitly registered in `MemoryExtractionAssembler`.
6. Document/audio chunking uses `TextChunker`, not "always single segment".
7. `RawDataLayer` persists request metadata instead of dropping it.
8. `RawDataLayer` resolves normalized source metadata into `MemoryResource` rows before persisting raw data.
9. Multimodal raw-data idempotency uses a source-aware key, not bare text-only `RawContent.getContentId()`.
10. Same parsed multimodal text from different `sourceUri` / `resourceId` values does not collapse onto one raw-data row.
11. Direct multimodal requests that provide only `sourceUri` still derive a stable `resourceId`.
12. `file(...)` requests still produce a stable `resourceId` and `MemoryResource` row even when no `ResourceStore` is configured.
13. `MemoryRawData.resourceId` and `MemoryRawData.mimeType` are populated when present.
14. `memory_resource` exists in fresh JDBC/MyBatis schemas and stores durable source-reference metadata rather than parsed text.
15. `memory_raw_data.resource_id` logically references `memory_resource.biz_id` without introducing hard foreign keys.
16. JDBC schema bootstrap upgrades existing databases that already have `memory_raw_data` but lack `memory_resource`, `resource_id`, or `mime_type`.
17. JDBC schema bootstrap also repairs mixed upgrade states, such as missing only one raw-data column, missing only one resource-table column, or only the resource index.
18. MyBatis starter schema init registers and executes multimodal migration scripts through the fixed `DatabaseDialect.scriptPaths()` list.
19. JDBC and MyBatis mappings read/write both `memory_resource` and the new raw-data fields.
20. Optional `ResourceStore` wiring reaches:
   - `JdbcStore`
   - JDBC starter auto-config
   - MyBatis starter auto-config
21. Aggregate store persistence keeps `memory_resource` and `memory_raw_data` writes consistent in JDBC/MyBatis implementations.
22. User-scoped default insight types accept `DOCUMENT`, `IMAGE`, and `AUDIO`.
23. Agent-scoped default insight types remain conversation-only.
24. Unified item extraction prompt uses content-neutral wording and the `<SourceText>` wrapper.
25. All direct `RawDataExtractionOptions(...)` constructor call sites are updated, including `memind-evaluation`.
26. Direct `image(...)` / `audio(...)` requests have a defined `mimeType` source on their content models, not an implicit convention.
27. Parsed multimodal content metadata is preserved on the `file(...)` path, not reduced to only file-level fields.
28. `MemoryRawData.withVectorId(...)` and `withMetadata(...)` keep projected `resourceId` / `mimeType` fields aligned with metadata.
29. Source-traceability metadata such as `resourceId`, `sourceUri`, and `mimeType` survives from `ParsedSegment.metadata()` into persisted `MemoryItem.metadata()`, aside from intentionally filtered legacy-only keys.
30. Existing tests still pass, especially:
    - `RawDataLayerProcessorTest`
    - `MemoryRawDataTest`
    - `LlmItemExtractionStrategyTest`
    - `ConversationContentProcessorTest`
    - `ToolCallContentProcessorTest`
    - `MemoryStoreTest`
    - `MemoryAssemblersTest`
    - `MemoryItemUnifiedPromptsTest`
    - `StoreSchemaBootstrapTest`
    - `JdbcStoreTest`
    - `SqliteMemoryStoreTest`
    - `JdbcPluginAutoConfigurationSqliteTest`
    - `MemoryStoreDdlTest`
    - `MemorySchemaAutoConfigurationTest`
    - `ResourceConverterTest`
    - `MemoryStoreAutoConfigurationTest`
