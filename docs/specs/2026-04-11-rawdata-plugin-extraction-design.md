# RawData Plugin Extraction Design

**Goal:** Extract the Document/Image/Audio RawData implementations and the ToolCall processing implementation from `memind-core` into independent plugin modules, leaving core with the framework SPI, the Conversation implementation, and the minimal ToolCall public API/model required for backward compatibility.

**Motivation:** `memind-core` currently bundles five RawData types with their processors, chunkers, caption generators, options, and content parsers. This makes core heavier than necessary and couples unrelated content-type implementations. As an open-source project, users who only need conversation memory should not have to pull in image/audio/document processing code. Splitting by RawData granularity gives each plugin a self-contained lifecycle: content model, parser, processor, chunker, caption generator, and options all live together. The split must work in both Spring Boot and pure builder-based runtimes; plugin registration cannot depend on Spring alone.

---

## Module Structure

```
memind-plugins/
├── memind-plugin-rawdatas/                          ← new parent POM
│   ├── memind-plugin-rawdata-document/              ← DocumentContent + Tika parser + processor + chunker
│   ├── memind-plugin-rawdata-image/                 ← ImageContent + LLM Vision parser + processor + composer
│   ├── memind-plugin-rawdata-audio/                 ← AudioContent + STT parser + processor + chunker
│   └── memind-plugin-rawdata-toolcall/              ← ToolCall processor + chunker + item extraction
├── memind-plugin-spring-boot-starters/
│   ├── memind-plugin-rawdata-document-starter/      ← new
│   ├── memind-plugin-rawdata-image-starter/         ← new
│   ├── memind-plugin-rawdata-audio-starter/         ← new
│   ├── memind-plugin-rawdata-toolcall-starter/      ← new
│   └── ... (existing starters unchanged)
└── ... (existing plugins unchanged)
```

**Deleted modules:**
- `memind-plugin-content-parser-document-tika` — merged into `memind-plugin-rawdata-document`
- `memind-plugin-content-parser-document-tika-starter` — replaced by `memind-plugin-rawdata-document-starter`

**Dependency direction:** each rawdata plugin depends on `memind-core` (one-way). No cross-plugin dependencies.

---

## What Stays in Core

### Framework SPI and runtime bootstrap

- `RawContent` abstract base class (with `@JsonTypeInfo` but **without** `@JsonSubTypes`; with new methods: `contentMetadata()`, `withMetadata()`, `mimeType()`, `sourceUri()`, `directGovernanceType()`, `directContentProfile()`)
- `RawContentProcessor<T>` interface (with new default methods: `usesSourceIdentity()`, `validateParsedContent()`, `resolveSegmentStartTime()`, `resolveSegmentEndTime()`)
- `RawContentTypeRegistrar` interface (new — Jackson subtype registration SPI)
- `RawDataPlugin` + `RawDataPluginContext` (new — runtime contribution SPI used by both Spring and non-Spring bootstrapping)
- `RawContentJackson` helper (new — shared subtype-registration helper for any `ObjectMapper` that serializes/deserializes `RawContent`)
- `CaptionGenerator` interface + `TruncateCaptionGenerator`
- `ItemExtractionStrategy` interface
- `Segment`, `SegmentBoundary`, `CharBoundary`, `MessageBoundary`
- `TokenAwareSegmentAssembler` (shared chunking infrastructure)
- `TextChunker`, `HeadingAwareTextChunker` (reusable text chunking utilities)
- `RawDataLayer` (orchestration engine, works through SPI)
- `ContentParser` interface + `ContentParserRegistry` / `DefaultContentParserRegistry`
- `ContentGovernanceType` enum (governance families — stays in core as framework vocabulary)
- `BuiltinContentProfiles` (profile constants and governance mapping — stays in core)
- `ContentGovernanceResolver`, `MultimodalMetadataNormalizer` (generalized to use `RawContent` base methods)
- `ResourceFetcher` / `HttpResourceFetcher`
- `SourceDescriptor`, `ResourceStore`, etc.
- `ExtractionRequest` (generalized), `MemoryExtractor`
- `MemoryItemLayer`, `InsightLayer`
- `DefaultInsightTypes` (content type string lists are harmless when plugins are absent)

### Conversation implementation (the only built-in RawData type)

- `ConversationContent` + Message hierarchy (`Message`, `ContentBlock`, `TextBlock`, `ImageBlock`, `AudioBlock`, `VideoBlock`, `Source`, `URLSource`, `Base64Source`)
- `ConversationContentProcessor`
- `ConversationChunker` + `LlmConversationChunker`
- `LlmConversationCaptionGenerator`
- `ConversationChunkingConfig`

### ToolCall compatibility API retained in core

- `ToolCallRecord`
- `ToolCallContent`
- `ExtractionRequest.toolCall(...)`
- `Memory.reportToolCall(...)` / `Memory.reportToolCalls(...)`
- `ToolStatsService` and tool-stats query APIs

These stay in core to preserve the public Agent-memory API. The processing implementation moves to the ToolCall plugin. If the ToolCall plugin is absent at runtime, `reportToolCall(s)` must fail fast with a descriptive missing-plugin error rather than silently no-op.

### Configuration retained in core

```java
public record RawDataExtractionOptions(
    ConversationChunkingConfig conversation,
    CommitDetectorConfig commitDetection,
    int vectorBatchSize)
```

Plugin-specific options (e.g. `DocumentExtractionOptions`) move to their respective plugin modules and are managed through Spring Boot auto-configuration with their own `@ConfigurationProperties` prefixes.

For non-Spring runtimes, plugin-specific options are configured when constructing each `RawDataPlugin` instance and then explicitly registered on `MemoryBuilder`. Core runtime bootstrap does not implicitly auto-discover rawdata plugins from the classpath; plugin enablement is explicit and deterministic outside Spring Boot.

---

## What Moves to Plugins

### memind-plugin-rawdata-document

From core:
- `DocumentContent`, `DocumentSection`
- `DocumentContentProcessor`
- `ProfileAwareDocumentChunker`
- `DocumentExtractionOptions`
- `NativeTextDocumentContentParser`
- `HtmlTextExtractor`

From `memind-plugin-content-parser-document-tika`:
- `TikaDocumentContentParser` and all Tika dependencies

### memind-plugin-rawdata-image

From core:
- `ImageContent`
- `ImageContentProcessor`
- `ImageSegmentComposer`
- `ImageCaptionGenerator`
- `ImageExtractionOptions`

New:
- `ImageContentParser` — LLM Vision-based parser that converts image bytes to `ImageContent` (description + OCR)

### memind-plugin-rawdata-audio

From core:
- `AudioContent`, `TranscriptSegment`
- `AudioContentProcessor`
- `TranscriptSegmentChunker`
- `AudioCaptionGenerator`
- `AudioExtractionOptions`

New:
- `AudioContentParser` — STT-based parser that converts audio bytes to `AudioContent` (transcript + segments)

### memind-plugin-rawdata-toolcall

From core:
- `ToolCallContentProcessor`
- `ToolCallChunker`
- `ToolCallCaptionGenerator`
- `ToolCallChunkingOptions`

Also owns:
- ToolCall item-extraction wiring/plugin bootstrap

Retained in core as compatibility shims:
- `ToolCallContent`, `ToolCallRecord`
- `ExtractionRequest.toolCall(...)`
- `Memory.reportToolCall(...)` / `Memory.reportToolCalls(...)`

No ContentParser (ToolCall content is only submitted directly via API).

---

## Decoupling the Hard References

### 1. Jackson `@JsonSubTypes` on `RawContent`

**Problem:** `RawContent.java` hardcodes all five subtypes in `@JsonSubTypes`.

**Solution:**
- Remove `@JsonSubTypes` from `RawContent`. Keep `@JsonTypeInfo`.
- Introduce `RawContentTypeRegistrar` SPI in core:

```java
public interface RawContentTypeRegistrar {
    Map<String, Class<? extends RawContent>> subtypes();
}
```

- Core registers `ConversationContent` via a built-in registrar.
- Each plugin implements `RawContentTypeRegistrar` declaring its type (e.g. `"document" → DocumentContent.class`).
- Introduce `RawContentJackson` in core as the single helper that applies `RawContentTypeRegistrar` contributions to an `ObjectMapper`.
- Spring path: auto-configuration collects all `RawContentTypeRegistrar` beans and applies them through `RawContentJackson` to the application `ObjectMapper`.
- Non-Spring path: `DefaultMemoryBuilder` collects registrars from explicitly registered `RawDataPlugin`s only, then applies them through the same `RawContentJackson` helper.
- Duplicate plugin contributions must fail fast:
  - duplicate `pluginId`
  - duplicate RawContent subtype discriminator name
  - duplicate parser identifiers or other registry keys that would make routing ambiguous
- Any component that serializes/deserializes `RawContent` (for example store codecs) must use `RawContentJackson` instead of constructing a bare `ObjectMapper` that knows only core subtypes.

### 2. `ExtractionRequest` factory methods

**Problem:** `document()`, `image()`, `audio()`, `toolCall()` factory methods import concrete content types.

**Solution:**
- Keep `conversation()` and `toolCall()` in core.
- Keep generic `of(MemoryId, RawContent)`, `file(...)`, `url(...)` in core.
- Remove `document()`, `image()`, `audio()` from core.
- Plugins provide their own convenience factories as utility classes (e.g. `DocumentExtractionRequests.document(...)`).
- `ExtractionRequest.of(...)` becomes fully generic and must not special-case plugin content classes.

### 3. `MultimodalMetadataNormalizer`

**Problem:** Uses `instanceof` checks against `DocumentContent`, `ImageContent`, `AudioContent` in five independent dispatch points:
- `withMetadata()` — creates a new instance with updated metadata (needs constructor knowledge)
- `deriveDirectGovernanceType()` — derives governance type from content type and MIME
- `deriveDirectProfile()` — derives content profile from content type and MIME
- `contentMetadata()` — reads the metadata map
- `putTransportMetadata()` — reads `sourceUri` and `mimeType`

A single `contentMetadata()` method is not enough to cover all five.

**Solution:** Add multiple default methods on `RawContent`:

```java
/** Returns the metadata map for this content. Override in subclasses that carry metadata. */
public Map<String, Object> contentMetadata() {
    return Map.of();
}

/** Returns a copy of this content with the given metadata. */
public RawContent withMetadata(Map<String, Object> metadata) {
    throw new UnsupportedOperationException(
            getClass().getName() + " must override withMetadata(metadata)");
}

/** Returns the MIME type of this content, if applicable. */
public String mimeType() {
    return null;
}

/** Returns the source URI of this content, if applicable. */
public String sourceUri() {
    return null;
}

/** Derives the governance type for direct (non-parser) ingestion. Returns null for types that do not participate in governance. */
public ContentGovernanceType directGovernanceType() {
    return null;
}

/** Derives the content profile string for direct ingestion. Returns null for types that do not participate in profile routing. */
public String directContentProfile() {
    return null;
}
```

Each plugin's Content subclass overrides these methods. `MultimodalMetadataNormalizer` calls the base-class methods without `instanceof` checks. `withMetadata()` is intentionally fail-fast: any content type that participates in metadata normalization must override it, so plugin authors cannot silently skip metadata propagation by inheriting a no-op default.

### 4. `ContentTypes` constants

**Problem:** Defines all five type strings in core.

**Solution:**
- Core keeps `CONVERSATION` and `TOOL_CALL` (the latter remains for ToolCall public API compatibility).
- Each plugin defines its own constant (e.g. `DocumentContentTypes.DOCUMENT = "DOCUMENT"`).
- `ContentTypes` in core is trimmed or deprecated.

### 5. `RawDataLayer.isMultimodal()`

**Problem:** Hardcodes `DOCUMENT || IMAGE || AUDIO` to decide source-identity-aware content ID hashing.

**Solution:** Add to `RawContentProcessor`:

```java
default boolean usesSourceIdentity() {
    return false;
}
```

Document, Image, Audio processors override to return `true`. `RawDataLayer` queries the processor registry instead of checking type strings.

### 6. `MemoryExtractionAssembler` hardcoded processor creation

**Problem:** Creates all five processors inline.

**Solution:**
- Core creates only `ConversationContentProcessor` internally.
- All other processors are contributed through `RawDataPlugin`.
- Introduce a core SPI:

```java
public interface RawDataPlugin {
    String pluginId();

    List<RawContentProcessor<?>> processors(RawDataPluginContext context);

    default List<ContentParser> parsers(RawDataPluginContext context) {
        return List.of();
    }

    default List<RawContentTypeRegistrar> typeRegistrars() {
        return List.of();
    }
}
```

- `RawDataPluginContext` exposes shared runtime dependencies needed to construct plugin components (chat-client registry, prompt registry, core build options, etc.).
- `DefaultMemoryBuilder` supports explicit `rawDataPlugin(...)` registration for non-Spring runtimes.
- `DefaultMemoryBuilder` builds an effective parser/runtime view as follows:
  - explicit `rawDataPlugin(...)` registrations are the only source of plugin contributions outside Spring Boot
  - if the caller supplies an explicit `ContentParserRegistry`, that registry remains authoritative
  - otherwise, builder assembles a `DefaultContentParserRegistry` from plugin-contributed parsers
- Duplicate plugin contributions must fail fast during builder assembly rather than being silently merged.
- Spring Boot starters create `RawDataPlugin` beans and feed them through the same assembly path.
- `MemoryExtractionAssembler` merges the built-in Conversation processor with plugin-contributed processors/parsers/registrars and passes the combined view to the runtime.

### 7. `ParsedContentLimitValidator`

**Problem:** `switch(governanceType)` with hardcoded branches for each content type.

**Solution:** Move validation into `RawContentProcessor`:

```java
default void validateParsedContent(RawContent content) {
    // no-op by default
}
```

Each plugin's processor implements its own validation. `MemoryExtractor` calls `processor.validateParsedContent(content)` after parsing. The centralized `ParsedContentLimitValidator` is removed or reduced to Conversation-only logic.

### 8. `ContentGovernanceType` enum and `BuiltinContentProfiles` constants

**Problem:** `ContentGovernanceType` is a closed enum with four values (`DOCUMENT_TEXT_LIKE`, `DOCUMENT_BINARY`, `IMAGE_CAPTION_OCR`, `AUDIO_TRANSCRIPT`) — all non-Conversation types. `BuiltinContentProfiles` defines six profile string constants and maps them to governance types. Both are referenced by `MultimodalMetadataNormalizer`, `ParsedContentLimitValidator`, `ContentParser`, `ContentCapability`, `ProfileAwareDocumentChunker`, and `DocumentContentProcessor`.

**Solution:**
- `ContentGovernanceType` enum stays in core as a framework-level concept. It defines the governance families that the ingestion policy system recognizes. Plugins use these enum values — they do not invent new ones. This is intentional: governance types are a finite, curated set that controls validation and routing behavior. Adding a new governance type is a deliberate framework extension, not a per-plugin decision.
- `BuiltinContentProfiles` stays in core for the same reason — profile strings are the vocabulary shared between parsers and processors. The mapping from profiles to governance types must be consistent and centralized.
- The existing `ContentParser.governanceType()` default method already validates that non-builtin profiles must explicitly declare their governance type. This mechanism is sufficient for plugins.

### 9. `DefaultInsightTypes` hardcoded content type lists

**Problem:** `DefaultInsightTypes` declares `USER_TEXTUAL_CONTENT_TYPES = List.of(CONVERSATION, DOCUMENT, IMAGE, AUDIO)` as the source content types for user-facing insights (identity, preferences, etc.). This is not about which types "support insight" (controlled by `RawContentProcessor.supportsInsight()`), but which content types contribute items to each insight dimension.

**Solution:** `DefaultInsightTypes` stays in core. The content type strings (`"DOCUMENT"`, `"IMAGE"`, `"AUDIO"`) are stable identifiers, not class references — they are just strings that match values in the database. The list means "if items with these content types exist, include them in insight aggregation." If a rawdata plugin is not installed, no items with that content type will exist, so the list entry is harmless. No refactoring needed — keeping the list is correct and forward-compatible.

### 10. `RawDataLayer` `ConversationContent` instanceof check

**Problem:** `buildAndPersist()` does `instanceof ConversationContent` to extract message list and resolve time range.

**Solution:** Make time-range resolution a `RawContentProcessor` responsibility, not a `RawContent` base-class responsibility.

Add default methods on `RawContentProcessor`:

```java
default Instant resolveSegmentStartTime(T content, Segment segment, Instant fallback) {
    if (segment.runtimeContext() != null && segment.runtimeContext().startTime() != null) {
        return segment.runtimeContext().startTime();
    }
    return fallback;
}

default Instant resolveSegmentEndTime(T content, Segment segment, Instant fallback) {
    if (segment.runtimeContext() != null && segment.runtimeContext().observedAt() != null) {
        return segment.runtimeContext().observedAt();
    }
    return fallback;
}
```

`ConversationContentProcessor` overrides these methods to resolve timestamps from `MessageBoundary` and conversation messages. Other processors inherit the runtime-context-based default. `RawDataLayer` resolves the processor once and delegates time-range calculation through this SPI; no `messages()`-style method is added to `RawContent`.

---

## Plugin Internal Structure

Each rawdata plugin follows the same package layout:

```
com.openmemind.ai.memory.plugin.rawdata.<type>/
├── content/          ← RawContent subclass + related data models
├── processor/        ← RawContentProcessor implementation
├── chunk/            ← Chunker/Composer
├── caption/          ← CaptionGenerator implementation
├── parser/           ← ContentParser implementation (if applicable)
├── config/           ← ExtractionOptions + Spring auto-configuration
├── plugin/           ← RawDataPlugin implementation / non-Spring bootstrap entry
└── RawContentTypeRegistrar implementation
```

Each plugin module provides:
- a `RawDataPlugin` implementation for non-Spring usage
- `RawContentTypeRegistrar` implementation(s)

Each starter module provides:
- `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Auto-configuration class that binds plugin options from `@ConfigurationProperties` and creates the corresponding `RawDataPlugin` bean

---

## Scope Boundaries

### In scope

- Module creation and file relocation
- SPI interface additions on `RawContentProcessor` (`usesSourceIdentity()`, `validateParsedContent()`, `resolveSegmentStartTime()`, `resolveSegmentEndTime()`) and on `RawContent` (`contentMetadata()`, `withMetadata()`, `mimeType()`, `sourceUri()`, `directGovernanceType()`, `directContentProfile()`); new `RawContentTypeRegistrar`, `RawDataPlugin`, and `RawContentJackson` support in core
- `ExtractionRequest` generalization
- `MemoryExtractionAssembler` dynamic plugin-driven processor/parser injection
- `RawDataLayer` decoupling from concrete types
- `RawDataExtractionOptions` trimming
- Jackson dynamic subtype registration for both Spring and non-Spring runtimes
- Shared `RawContent` JSON registration for persistence codecs
- New `ImageContentParser` and `AudioContentParser`
- Spring Boot starter modules for each rawdata plugin
- ToolCall compatibility boundary (public API in core, processing implementation in plugin)
- All existing tests relocated and passing

### Out of scope

- Video RawContent support
- Cross-system distributed transactions
- New content types beyond the existing four
- Removing the ToolCall public API from core
- Changes to the Conversation implementation itself
- Changes to the Insight or MemoryItem layers beyond removing hardcoded type references

---

## Risks

- **Jackson deserialization backward compatibility**: Existing serialized `RawContent` data uses `"type":"document"` etc. The dynamic registration must produce the same discriminator values. Verified by: round-trip deserialization tests in each plugin.
- **Mapper consistency across runtimes**: Spring and non-Spring runtimes must apply the same subtype registrars to every `ObjectMapper` that touches `RawContent`; otherwise persisted payloads become unreadable outside one bootstrap path. Verified by: shared `RawContentJackson` tests plus persistence-codec integration tests.
- **Non-Spring plugin registration parity**: builder-based runtimes must not lose capabilities that are available under Spring, while still remaining deterministic. Verified by: `DefaultMemoryBuilder` integration tests covering explicit plugin registration, explicit-registry precedence, and duplicate-contribution fail-fast behavior.
- **MemoryExtractor test coverage**: Many tests reference `ExtractionRequest.document()` etc. These must be updated to use the plugin's factory or the generic `of()` path.
- **ToolCall compatibility**: the public `Memory.reportToolCall(s)` API remains in core, but it now depends on an external processor plugin being present. Verified by: explicit missing-plugin tests and compatibility tests when the ToolCall plugin is installed.
- **Breaking change for downstream users**: `ExtractionRequest.document()` / `image()` / `audio()` removal is a public API change. Mitigated by: providing equivalent factories in plugin modules with clear migration path; `toolCall()` stays in core.
