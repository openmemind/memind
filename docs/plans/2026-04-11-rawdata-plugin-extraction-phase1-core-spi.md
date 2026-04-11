# RawData Plugin Extraction Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Introduce the core SPI, processor hooks, and explicit plugin registration path needed for rawdata extraction modularization, while keeping all existing rawdata files in `memind-core` and preserving current runtime behavior.

**Architecture:** Phase 1 is a transitional refactor. Move hardcoded metadata/governance/time-range/source-identity logic into `RawContent` and `RawContentProcessor` SPI methods, route `RawDataLayer` and `MemoryExtractor` through those hooks, and add an explicit `RawDataPlugin` assembly path in the builder. Keep `@JsonSubTypes` in place and keep existing document/image/audio/toolcall classes in core for this phase; dynamic subtype registration becomes authoritative when files are physically extracted in Phase 2.

**Tech Stack:** Java 21, Maven, Reactor, Jackson, JUnit 5, Mockito

---

## File Structure

### New files

- `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/RawContentTypeRegistrar.java`
  Responsibility: SPI for Jackson RawContent subtype contributions.
- `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/RawContentProcessorRegistry.java`
  Responsibility: shared processor lookup/validation used by both `RawDataLayer` and `MemoryExtractor`.
- `memind-core/src/main/java/com/openmemind/ai/memory/core/plugin/RawDataPlugin.java`
  Responsibility: SPI for explicit plugin contributions.
- `memind-core/src/main/java/com/openmemind/ai/memory/core/plugin/RawDataPluginContext.java`
  Responsibility: typed construction context for plugin processors/parsers.
- `memind-core/src/main/java/com/openmemind/ai/memory/core/plugin/CoreBuiltinRawDataPlugin.java`
  Responsibility: transitional in-core plugin that contributes current document/image/audio/toolcall processors through the same SPI path that external plugins will later use.
- `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/RawContentJackson.java`
  Responsibility: helper for applying `RawContentTypeRegistrar` contributions to an `ObjectMapper`.
- `memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/rawdata/content/RawContentSpiTest.java`
  Responsibility: verifies `RawContent` SPI methods on current in-core content classes.
- `memind-core/src/test/java/com/openmemind/ai/memory/core/builder/RawDataPluginAssemblyTest.java`
  Responsibility: verifies explicit plugin registration, parser-registry precedence, and duplicate fail-fast behavior.
- `memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/rawdata/RawContentJacksonTest.java`
  Responsibility: verifies subtype registration helper behavior without relying on `@JsonSubTypes`.

### Modified files

- `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/content/RawContent.java`
- `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/content/DocumentContent.java`
- `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/content/ImageContent.java`
- `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/content/AudioContent.java`
- `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/RawContentProcessor.java`
- `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/RawContentProcessorRegistry.java`
- `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/processor/ConversationContentProcessor.java`
- `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/processor/DocumentContentProcessor.java`
- `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/processor/ImageContentProcessor.java`
- `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/processor/AudioContentProcessor.java`
- `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/RawDataLayer.java`
- `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/MultimodalMetadataNormalizer.java`
- `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/ExtractionRequest.java`
- `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/MemoryExtractor.java`
- `memind-core/src/main/java/com/openmemind/ai/memory/core/builder/MemoryBuilder.java`
- `memind-core/src/main/java/com/openmemind/ai/memory/core/builder/DefaultMemoryBuilder.java`
- `memind-core/src/main/java/com/openmemind/ai/memory/core/builder/MemoryAssemblyContext.java`
- `memind-core/src/main/java/com/openmemind/ai/memory/core/builder/MemoryExtractionAssembler.java`
- `memind-core/src/main/java/com/openmemind/ai/memory/core/utils/JsonUtils.java`
- `memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/ExtractionRequestTest.java`
- `memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/rawdata/RawDataLayerProcessorTest.java`
- `memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/rawdata/processor/ConversationContentProcessorTest.java`
- `memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/MemoryExtractorMultimodalFileTest.java`
- `memind-core/src/test/java/com/openmemind/ai/memory/core/builder/MemoryAssemblersTest.java`

### Deleted files

- `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/ParsedContentLimitValidator.java`
- `memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/ParsedContentLimitValidatorTest.java`

---

### Task 1: Add `RawContent` SPI methods and per-type overrides

**Files:**
- Create: `memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/rawdata/content/RawContentSpiTest.java`
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/content/RawContent.java`
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/content/DocumentContent.java`
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/content/ImageContent.java`
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/content/AudioContent.java`

- [ ] **Step 1: Write the failing SPI behavior test**

```java
package com.openmemind.ai.memory.core.extraction.rawdata.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmemind.ai.memory.core.data.enums.ContentGovernanceType;
import com.openmemind.ai.memory.core.extraction.BuiltinContentProfiles;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RawContentSpiTest {

    @Test
    void documentContentExposesMetadataGovernanceProfileAndCopy() {
        var content =
                new DocumentContent(
                        "Guide",
                        "text/markdown",
                        "# title",
                        List.of(),
                        "file:///tmp/guide.md",
                        Map.of("author", "alice"));

        assertThat(content.contentMetadata()).containsEntry("author", "alice");
        assertThat(content.directGovernanceType())
                .isEqualTo(ContentGovernanceType.DOCUMENT_TEXT_LIKE);
        assertThat(content.directContentProfile())
                .isEqualTo(BuiltinContentProfiles.DOCUMENT_MARKDOWN);
        assertThat(content.withMetadata(Map.of("parserId", "direct")))
                .isInstanceOf(DocumentContent.class)
                .extracting(value -> ((DocumentContent) value).metadata().get("parserId"))
                .isEqualTo("direct");
    }

    @Test
    void imageContentExposesMetadataGovernanceProfileAndCopy() {
        var content =
                new ImageContent(
                        "image/png",
                        "chart screenshot",
                        "Q1 revenue",
                        "file:///tmp/chart.png",
                        Map.of("width", 1280));

        assertThat(content.contentMetadata()).containsEntry("width", 1280);
        assertThat(content.directGovernanceType())
                .isEqualTo(ContentGovernanceType.IMAGE_CAPTION_OCR);
        assertThat(content.directContentProfile())
                .isEqualTo(BuiltinContentProfiles.IMAGE_CAPTION_OCR);
        assertThat(content.withMetadata(Map.of("parserId", "vision")))
                .isInstanceOf(ImageContent.class)
                .extracting(value -> ((ImageContent) value).metadata().get("parserId"))
                .isEqualTo("vision");
    }

    @Test
    void audioContentExposesMetadataGovernanceProfileAndCopy() {
        var content =
                new AudioContent(
                        "audio/mpeg",
                        "hello world",
                        List.of(),
                        "file:///tmp/audio.mp3",
                        Map.of("durationSeconds", 12));

        assertThat(content.contentMetadata()).containsEntry("durationSeconds", 12);
        assertThat(content.directGovernanceType())
                .isEqualTo(ContentGovernanceType.AUDIO_TRANSCRIPT);
        assertThat(content.directContentProfile())
                .isEqualTo(BuiltinContentProfiles.AUDIO_TRANSCRIPT);
        assertThat(content.withMetadata(Map.of("parserId", "whisper")))
                .isInstanceOf(AudioContent.class)
                .extracting(value -> ((AudioContent) value).metadata().get("parserId"))
                .isEqualTo("whisper");
    }

    @Test
    void baseRawContentWithMetadataFailsFastWhenNotOverridden() {
        var content = ConversationContent.builder().addUserMessage("hello").build();

        assertThatThrownBy(() -> content.withMetadata(Map.of("x", 1)))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("ConversationContent");
    }
}
```

- [ ] **Step 2: Run the new test and verify it fails**

Run: `mvn -pl memind-core -Dtest=RawContentSpiTest test`

Expected: FAIL with compile errors for missing `contentMetadata()`, `withMetadata()`, `directGovernanceType()`, and `directContentProfile()`.

- [ ] **Step 3: Add the SPI methods and overrides**

Add to [`RawContent.java`](/Users/zhengyate/dev/openmemind/memind/memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/content/RawContent.java):

```java
import com.openmemind.ai.memory.core.data.enums.ContentGovernanceType;
import java.util.Map;

public Map<String, Object> contentMetadata() {
    return Map.of();
}

public RawContent withMetadata(Map<String, Object> metadata) {
    throw new UnsupportedOperationException(
            getClass().getName() + " must override withMetadata(metadata)");
}

public String mimeType() {
    return null;
}

public String sourceUri() {
    return null;
}

public ContentGovernanceType directGovernanceType() {
    return null;
}

public String directContentProfile() {
    return null;
}
```

Add to [`DocumentContent.java`](/Users/zhengyate/dev/openmemind/memind/memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/content/DocumentContent.java):

```java
@Override
public Map<String, Object> contentMetadata() {
    return metadata;
}

@Override
public RawContent withMetadata(Map<String, Object> metadata) {
    return new DocumentContent(title, mimeType, parsedText, sections, sourceUri, metadata);
}

@Override
public ContentGovernanceType directGovernanceType() {
    String mime = mimeType();
    if ("text/markdown".equals(mime)
            || "text/html".equals(mime)
            || "text/plain".equals(mime)
            || "text/csv".equals(mime)) {
        return ContentGovernanceType.DOCUMENT_TEXT_LIKE;
    }
    if (mime != null && !mime.isBlank()) {
        return ContentGovernanceType.DOCUMENT_BINARY;
    }
    return sections().isEmpty()
            ? ContentGovernanceType.DOCUMENT_TEXT_LIKE
            : ContentGovernanceType.DOCUMENT_BINARY;
}

@Override
public String directContentProfile() {
    String mime = mimeType();
    if ("text/markdown".equals(mime)) {
        return BuiltinContentProfiles.DOCUMENT_MARKDOWN;
    }
    if ("text/html".equals(mime)) {
        return BuiltinContentProfiles.DOCUMENT_HTML;
    }
    if ("text/plain".equals(mime) || "text/csv".equals(mime)) {
        return BuiltinContentProfiles.DOCUMENT_TEXT;
    }
    if (mime != null && !mime.isBlank()) {
        return BuiltinContentProfiles.DOCUMENT_BINARY;
    }
    return sections().isEmpty()
            ? BuiltinContentProfiles.DOCUMENT_TEXT
            : BuiltinContentProfiles.DOCUMENT_BINARY;
}
```

Add to [`ImageContent.java`](/Users/zhengyate/dev/openmemind/memind/memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/content/ImageContent.java):

```java
@Override
public Map<String, Object> contentMetadata() {
    return metadata;
}

@Override
public RawContent withMetadata(Map<String, Object> metadata) {
    return new ImageContent(mimeType, description, ocrText, sourceUri, metadata);
}

@Override
public ContentGovernanceType directGovernanceType() {
    return ContentGovernanceType.IMAGE_CAPTION_OCR;
}

@Override
public String directContentProfile() {
    return BuiltinContentProfiles.IMAGE_CAPTION_OCR;
}
```

Add to [`AudioContent.java`](/Users/zhengyate/dev/openmemind/memind/memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/content/AudioContent.java):

```java
@Override
public Map<String, Object> contentMetadata() {
    return metadata;
}

@Override
public RawContent withMetadata(Map<String, Object> metadata) {
    return new AudioContent(mimeType, transcript, segments, sourceUri, metadata);
}

@Override
public ContentGovernanceType directGovernanceType() {
    return ContentGovernanceType.AUDIO_TRANSCRIPT;
}

@Override
public String directContentProfile() {
    return BuiltinContentProfiles.AUDIO_TRANSCRIPT;
}
```

- [ ] **Step 4: Re-run the targeted test**

Run: `mvn -pl memind-core -Dtest=RawContentSpiTest test`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/content/RawContent.java \
        memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/content/DocumentContent.java \
        memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/content/ImageContent.java \
        memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/content/AudioContent.java \
        memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/rawdata/content/RawContentSpiTest.java
git commit -m "refactor: add raw content SPI methods for plugin extraction"
```

---

### Task 2: Add processor SPI hooks, introduce a shared processor registry, and refactor `RawDataLayer`

**Files:**
- Create: `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/RawContentProcessorRegistry.java`
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/RawContentProcessor.java`
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/processor/ConversationContentProcessor.java`
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/processor/DocumentContentProcessor.java`
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/processor/ImageContentProcessor.java`
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/processor/AudioContentProcessor.java`
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/RawDataLayer.java`
- Modify: `memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/rawdata/processor/ConversationContentProcessorTest.java`
- Modify: `memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/rawdata/RawDataLayerProcessorTest.java`

- [ ] **Step 1: Add failing tests for time-range resolution, source-identity hashing, and duplicate processor registration**

Add to [`ConversationContentProcessorTest.java`](/Users/zhengyate/dev/openmemind/memind/memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/rawdata/processor/ConversationContentProcessorTest.java):

```java
@Test
@DisplayName("resolveSegmentStartTime and resolveSegmentEndTime should use message boundaries")
void resolvesSegmentTimesFromConversationMessages() {
    var first = Message.user("hello", Instant.parse("2026-04-11T10:00:00Z"));
    var second = Message.assistant("hi", Instant.parse("2026-04-11T10:00:10Z"));
    var content = new ConversationContent(List.of(first, second));
    var segment = new Segment("hello", null, new MessageBoundary(0, 2), Map.of());

    assertThat(processor.resolveSegmentStartTime(content, segment, Instant.EPOCH))
            .isEqualTo(first.timestamp());
    assertThat(processor.resolveSegmentEndTime(content, segment, Instant.EPOCH))
            .isEqualTo(second.timestamp());
}
```

Add to [`RawDataLayerProcessorTest.java`](/Users/zhengyate/dev/openmemind/memind/memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/rawdata/RawDataLayerProcessorTest.java):

```java
@Test
@DisplayName("duplicate processor registrations should fail fast")
void duplicateProcessorRegistrationsFailFast() {
    var first = mock(DocumentContentProcessor.class);
    var second = mock(DocumentContentProcessor.class);
    when(first.contentClass()).thenReturn(DocumentContent.class);
    when(second.contentClass()).thenReturn(DocumentContent.class);

    assertThatThrownBy(() -> new RawDataLayer(List.of(first, second), defaultCaption, store, vector, 64))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining(DocumentContent.class.getName());
}

@Test
@DisplayName("Document processor should opt into source-identity-aware content id hashing")
void sourceIdentityAwareHashingUsesProcessorHook() {
    var documentProcessor = mock(DocumentContentProcessor.class);
    when(documentProcessor.contentClass()).thenReturn(DocumentContent.class);
    when(documentProcessor.usesSourceIdentity()).thenReturn(true);
    when(documentProcessor.chunk(any(DocumentContent.class))).thenReturn(Mono.just(List.of()));
    when(documentProcessor.captionGenerator()).thenReturn(defaultCaption);

    var layer = new RawDataLayer(List.of(documentProcessor), defaultCaption, store, vector, 64);
    var content = DocumentContent.of("Report", "text/plain", "hello");

    when(defaultCaption.generateForSegments(any(), any())).thenReturn(Mono.just(List.of()));
    when(rawDataOps.getRawDataByContentId(any(), any())).thenReturn(Optional.empty());
    when(vector.storeBatch(any(), any(), any())).thenReturn(Mono.just(List.of()));

    layer.extract(
                    new com.openmemind.ai.memory.core.data.DefaultMemoryId("test", "agent"),
                    content,
                    "DOCUMENT",
                    Map.of("sourceUri", "https://example.com/report.txt"))
            .block();

    verify(documentProcessor).usesSourceIdentity();
    verify(rawDataOps)
            .getRawDataByContentId(any(), argThat(id -> !id.equals(content.getContentId())));
}
```

- [ ] **Step 2: Run the targeted tests and verify they fail**

Run: `mvn -pl memind-core -Dtest=ConversationContentProcessorTest,RawDataLayerProcessorTest test`

Expected: FAIL with missing `resolveSegmentStartTime()`, `resolveSegmentEndTime()`, `usesSourceIdentity()`, and duplicate-registration validation.

- [ ] **Step 3: Add the processor SPI hooks, create `RawContentProcessorRegistry`, and refactor `RawDataLayer` to use it**

Create [`RawContentProcessorRegistry.java`](/Users/zhengyate/dev/openmemind/memind/memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/RawContentProcessorRegistry.java):

```java
public final class RawContentProcessorRegistry {

    private final Map<Class<?>, RawContentProcessor<?>> processors;

    public RawContentProcessorRegistry(List<RawContentProcessor<?>> processors) {
        Map<Class<?>, RawContentProcessor<?>> resolved = new LinkedHashMap<>();
        for (RawContentProcessor<?> processor :
                Objects.requireNonNull(processors, "processors").stream()
                        .filter(Objects::nonNull)
                        .toList()) {
            Class<?> contentClass =
                    Objects.requireNonNull(processor.contentClass(), "processor.contentClass()");
            RawContentProcessor<?> previous = resolved.putIfAbsent(contentClass, processor);
            if (previous != null) {
                throw new IllegalStateException(
                        "Duplicate processor registration for " + contentClass.getName());
            }
        }
        this.processors = Map.copyOf(resolved);
    }

    @SuppressWarnings("unchecked")
    public <T extends RawContent> RawContentProcessor<T> resolve(T content) {
        Class<?> cls = Objects.requireNonNull(content, "content").getClass();
        while (cls != null && cls != Object.class) {
            RawContentProcessor<?> processor = processors.get(cls);
            if (processor != null) {
                return (RawContentProcessor<T>) processor;
            }
            cls = cls.getSuperclass();
        }
        throw new IllegalArgumentException(
                "No processor registered for: " + content.getClass().getName());
    }

    public List<RawContentProcessor<?>> all() {
        return List.copyOf(processors.values());
    }
}
```

Add to [`RawContentProcessor.java`](/Users/zhengyate/dev/openmemind/memind/memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/RawContentProcessor.java):

```java
default boolean usesSourceIdentity() {
    return false;
}

default void validateParsedContent(T content) {}

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

Add to [`DocumentContentProcessor.java`](/Users/zhengyate/dev/openmemind/memind/memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/processor/DocumentContentProcessor.java), [`ImageContentProcessor.java`](/Users/zhengyate/dev/openmemind/memind/memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/processor/ImageContentProcessor.java), and [`AudioContentProcessor.java`](/Users/zhengyate/dev/openmemind/memind/memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/processor/AudioContentProcessor.java):

```java
@Override
public boolean usesSourceIdentity() {
    return true;
}
```

Add to [`ConversationContentProcessor.java`](/Users/zhengyate/dev/openmemind/memind/memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/processor/ConversationContentProcessor.java):

```java
@Override
public Instant resolveSegmentStartTime(
        ConversationContent content, Segment segment, Instant fallback) {
    if (segment.runtimeContext() != null && segment.runtimeContext().startTime() != null) {
        return segment.runtimeContext().startTime();
    }
    if (segment.boundary() instanceof MessageBoundary mb) {
        int idx = mb.startMessage();
        if (idx >= 0 && idx < content.getMessages().size()) {
            Instant ts = content.getMessages().get(idx).timestamp();
            return ts != null ? ts : fallback;
        }
    }
    return fallback;
}

@Override
public Instant resolveSegmentEndTime(
        ConversationContent content, Segment segment, Instant fallback) {
    if (segment.runtimeContext() != null && segment.runtimeContext().observedAt() != null) {
        return segment.runtimeContext().observedAt();
    }
    if (segment.boundary() instanceof MessageBoundary mb) {
        int idx = mb.endMessage() - 1;
        if (idx >= 0 && idx < content.getMessages().size()) {
            Instant ts = content.getMessages().get(idx).timestamp();
            return ts != null ? ts : fallback;
        }
    }
    return fallback;
}
```

Refactor [`RawDataLayer.java`](/Users/zhengyate/dev/openmemind/memind/memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/RawDataLayer.java):

```java
private final RawContentProcessorRegistry processorRegistry;

public RawDataLayer(
        List<RawContentProcessor<?>> processorList,
        CaptionGenerator defaultCaptionGenerator,
        MemoryStore memoryStore,
        MemoryVector vector,
        int vectorBatchSize) {
    this.processorRegistry = new RawContentProcessorRegistry(processorList);
    this.defaultCaptionGenerator = defaultCaptionGenerator;
    this.memoryStore = memoryStore;
    this.vector = vector;
    ...
}

@SuppressWarnings("unchecked")
private <T extends RawContent> RawContentProcessor<T> getTypedProcessor(T content) {
    return processorRegistry.resolve(content);
}

private CaptionGenerator getCaptionGenerator(RawContent content) {
    var generator = processorRegistry.resolve(content).captionGenerator();
    return generator != null ? generator : defaultCaptionGenerator;
}

private String resolveRawDataContentId(RawDataInput input) {
    String textContentId = input.content().getContentId();
    if (!getTypedProcessor(input.content()).usesSourceIdentity()) {
        return textContentId;
    }
    String sourceIdentity = resolveSourceIdentity(input.memoryId(), input.metadata());
    return HashUtils.sampledSha256(
            normalizeContentType(input.contentType())
                    + "|"
                    + textContentId
                    + "|"
                    + (sourceIdentity == null ? "" : sourceIdentity));
}

private Instant resolveStartTime(RawDataInput input, Segment segment, Instant fallback) {
    return input.content() == null
            ? fallback
            : getTypedProcessor(input.content())
                    .resolveSegmentStartTime(input.content(), segment, fallback);
}

private Instant resolveEndTime(RawDataInput input, Segment segment, Instant fallback) {
    return input.content() == null
            ? fallback
            : getTypedProcessor(input.content())
                    .resolveSegmentEndTime(input.content(), segment, fallback);
}
```

Then replace the `buildAndPersist(...)` timestamp calls with:

```java
resolveStartTime(input, segment, now),
resolveEndTime(input, segment, now)
```

- [ ] **Step 4: Re-run the targeted tests**

Run: `mvn -pl memind-core -Dtest=ConversationContentProcessorTest,RawDataLayerProcessorTest test`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/RawContentProcessor.java \
        memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/RawContentProcessorRegistry.java \
        memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/processor/ConversationContentProcessor.java \
        memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/processor/DocumentContentProcessor.java \
        memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/processor/ImageContentProcessor.java \
        memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/processor/AudioContentProcessor.java \
        memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/RawDataLayer.java \
        memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/rawdata/processor/ConversationContentProcessorTest.java \
        memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/rawdata/RawDataLayerProcessorTest.java
git commit -m "refactor: add shared raw content processor registry and SPI hooks"
```

---

### Task 3: Generalize metadata normalization and `ExtractionRequest.of(...)`

**Files:**
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/MultimodalMetadataNormalizer.java`
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/ExtractionRequest.java`
- Modify: `memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/ExtractionRequestTest.java`

- [ ] **Step 1: Add failing tests for generic direct-content normalization**

Add to [`ExtractionRequestTest.java`](/Users/zhengyate/dev/openmemind/memind/memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/ExtractionRequestTest.java):

```java
@Test
void ofShouldNormalizeAnyContentThatDeclaresDirectGovernanceType() {
    final class CustomContent extends RawContent {
        private final Map<String, Object> metadata;

        private CustomContent(Map<String, Object> metadata) {
            this.metadata = Map.copyOf(metadata);
        }

        @Override
        public String contentType() {
            return "CUSTOM";
        }

        @Override
        public String toContentString() {
            return "payload";
        }

        @Override
        public String getContentId() {
            return "custom-id";
        }

        @Override
        public Map<String, Object> contentMetadata() {
            return metadata;
        }

        @Override
        public RawContent withMetadata(Map<String, Object> metadata) {
            return new CustomContent(metadata);
        }

        @Override
        public String mimeType() {
            return "text/plain";
        }

        @Override
        public ContentGovernanceType directGovernanceType() {
            return ContentGovernanceType.DOCUMENT_TEXT_LIKE;
        }

        @Override
        public String directContentProfile() {
            return "custom.text";
        }
    }

    RawContent content = new CustomContent(Map.of("x", 1));

    var request = ExtractionRequest.of(DefaultMemoryId.of("user-1", "agent-1"), content);

    assertThat(request.contentType()).isEqualTo("CUSTOM");
    assertThat(request.metadata())
            .containsEntry("x", 1)
            .containsEntry("sourceKind", "DIRECT")
            .containsEntry("parserId", "direct")
            .containsEntry("contentProfile", "custom.text")
            .containsEntry("governanceType", ContentGovernanceType.DOCUMENT_TEXT_LIKE.name());
}
```

Also add to the same test class:

```java
@Test
void deprecatedDirectFactoriesDelegateToOfAndRemainDiscoverable() throws Exception {
    var memoryId = DefaultMemoryId.of("user-1", "agent-1");
    var document = DocumentContent.of("Guide", "text/markdown", "# title");
    var image = ImageContent.of("dashboard screenshot");
    var audio = AudioContent.of("hello world");

    assertThat(ExtractionRequest.document(memoryId, document))
            .usingRecursiveComparison()
            .isEqualTo(ExtractionRequest.of(memoryId, document));
    assertThat(ExtractionRequest.image(memoryId, image))
            .usingRecursiveComparison()
            .isEqualTo(ExtractionRequest.of(memoryId, image));
    assertThat(ExtractionRequest.audio(memoryId, audio))
            .usingRecursiveComparison()
            .isEqualTo(ExtractionRequest.of(memoryId, audio));

    assertThat(
                    ExtractionRequest.class
                            .getMethod("document", MemoryId.class, DocumentContent.class)
                            .getAnnotation(Deprecated.class))
            .isNotNull();
    assertThat(
                    ExtractionRequest.class
                            .getMethod("image", MemoryId.class, ImageContent.class)
                            .getAnnotation(Deprecated.class))
            .isNotNull();
    assertThat(
                    ExtractionRequest.class
                            .getMethod("audio", MemoryId.class, AudioContent.class)
                            .getAnnotation(Deprecated.class))
            .isNotNull();
}
```

- [ ] **Step 2: Run the targeted tests and verify they fail**

Run: `mvn -pl memind-core -Dtest=ExtractionRequestTest test`

Expected: FAIL because `ExtractionRequest.of(...)` still special-cases `DocumentContent`, `ImageContent`, and `AudioContent`.

- [ ] **Step 3: Refactor `MultimodalMetadataNormalizer` and `ExtractionRequest`**

Replace the helper methods in [`MultimodalMetadataNormalizer.java`](/Users/zhengyate/dev/openmemind/memind/memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/MultimodalMetadataNormalizer.java):

```java
private static Map<String, Object> contentMetadata(RawContent content) {
    return content.contentMetadata();
}

public static RawContent withMetadata(RawContent content, Map<String, Object> metadata) {
    Objects.requireNonNull(content, "content");
    Objects.requireNonNull(metadata, "metadata");
    return content.withMetadata(metadata);
}

private static void putTransportMetadata(Map<String, Object> normalized, RawContent content) {
    putIfNotBlank(normalized, "sourceUri", content.sourceUri());
    putIfNotBlank(normalized, "mimeType", content.mimeType());
}

private static ContentGovernanceType deriveDirectGovernanceType(RawContent content) {
    ContentGovernanceType governanceType = content.directGovernanceType();
    if (governanceType == null) {
        throw new IllegalArgumentException("Unsupported multimodal content: " + content.contentType());
    }
    return governanceType;
}

private static String deriveDirectProfile(RawContent content) {
    String profile = content.directContentProfile();
    return profile != null ? profile : content.contentType().toLowerCase(Locale.ROOT);
}
```

Refactor [`ExtractionRequest.java`](/Users/zhengyate/dev/openmemind/memind/memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/ExtractionRequest.java):

```java
public static ExtractionRequest of(MemoryId memoryId, RawContent content) {
    if (content.directGovernanceType() != null) {
        var normalizedContent = MultimodalMetadataNormalizer.normalizeDirectContent(content);
        return new ExtractionRequest(
                memoryId,
                normalizedContent,
                null,
                null,
                normalizedContent.contentType(),
                MultimodalMetadataNormalizer.snapshot(normalizedContent),
                ExtractionConfig.defaults());
    }
    return new ExtractionRequest(
            memoryId,
            content,
            null,
            null,
            content.contentType(),
            Map.of(),
            ExtractionConfig.defaults());
}

@Deprecated(forRemoval = true)
public static ExtractionRequest document(MemoryId memoryId, DocumentContent content) {
    return of(memoryId, content);
}

@Deprecated(forRemoval = true)
public static ExtractionRequest image(MemoryId memoryId, ImageContent content) {
    return of(memoryId, content);
}

@Deprecated(forRemoval = true)
public static ExtractionRequest audio(MemoryId memoryId, AudioContent content) {
    return of(memoryId, content);
}
```

Keep `conversation()` and `toolCall()` unchanged in this phase.

- [ ] **Step 4: Re-run the targeted tests**

Run: `mvn -pl memind-core -Dtest=ExtractionRequestTest test`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/MultimodalMetadataNormalizer.java \
        memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/ExtractionRequest.java \
        memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/ExtractionRequestTest.java
git commit -m "refactor: generalize direct raw content normalization"
```

---

### Task 4: Move parsed-content validation into processors and wire `MemoryExtractor`

**Files:**
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/processor/DocumentContentProcessor.java`
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/processor/ImageContentProcessor.java`
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/processor/AudioContentProcessor.java`
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/MemoryExtractor.java`
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/builder/MemoryExtractionAssembler.java`
- Delete: `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/ParsedContentLimitValidator.java`
- Delete: `memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/ParsedContentLimitValidatorTest.java`
- Modify: `memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/MemoryExtractorMultimodalFileTest.java`
- Modify: `memind-core/src/test/java/com/openmemind/ai/memory/core/builder/MemoryAssemblersTest.java`

- [ ] **Step 1: Add failing integration coverage for processor-based validation**

Add to [`MemoryExtractorMultimodalFileTest.java`](/Users/zhengyate/dev/openmemind/memind/memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/MemoryExtractorMultimodalFileTest.java):

```java
@Test
void directDocumentExtractionUsesProcessorValidation() {
    var rawDataStep = new RecordingRawDataStep(false);
    var processorRegistry =
            new RawContentProcessorRegistry(
                    List.of(
                            new DocumentContentProcessor(
                                    new ProfileAwareDocumentChunker(),
                                    restrictiveDocumentOptions())));
    var content =
            new DocumentContent(
                    "Manual",
                    "application/pdf",
                    "word ".repeat(200),
                    List.of(),
                    null,
                    Map.of("contentProfile", "document.binary"));

    var result =
            extractorWithRestrictiveOptions(rawDataStep, processorRegistry)
                    .extract(
                            ExtractionRequest.of(
                                    DefaultMemoryId.of("user-1", "agent-1"),
                                    content))
                    .block();

    assertThat(result).isNotNull();
    assertThat(result.isFailed()).isTrue();
    assertThat(result.errorMessage()).contains("document.binary");
}

private MemoryExtractor extractorWithRestrictiveOptions(
        RawDataExtractStep rawDataStep, RawContentProcessorRegistry processorRegistry) {
    return new MemoryExtractor(
            rawDataStep,
            (memoryId, rawDataResult, config) -> Mono.just(MemoryItemResult.empty()),
            (memoryId, memoryItemResult) -> Mono.just(InsightResult.empty()),
            null,
            null,
            null,
            null,
            processorRegistry,
            null,
            null,
            null,
            new RawDataExtractionOptions(
                    com.openmemind.ai.memory.core.extraction.rawdata.chunk
                            .ConversationChunkingConfig.DEFAULT,
                    restrictiveDocumentOptions(),
                    com.openmemind.ai.memory.core.builder.ImageExtractionOptions.defaults(),
                    com.openmemind.ai.memory.core.builder.AudioExtractionOptions.defaults(),
                    com.openmemind.ai.memory.core.builder.ToolCallChunkingOptions.defaults(),
                    com.openmemind.ai.memory.core.extraction.context.CommitDetectorConfig.defaults(),
                    64),
            ItemExtractionOptions.defaults());
}

private com.openmemind.ai.memory.core.builder.DocumentExtractionOptions
        restrictiveDocumentOptions() {
    return new com.openmemind.ai.memory.core.builder.DocumentExtractionOptions(
            new com.openmemind.ai.memory.core.builder.SourceLimitOptions(1024),
            new com.openmemind.ai.memory.core.builder.SourceLimitOptions(1024),
            new com.openmemind.ai.memory.core.builder.ParsedContentLimitOptions(256, null, null, null),
            new com.openmemind.ai.memory.core.builder.ParsedContentLimitOptions(128, null, null, null),
            new com.openmemind.ai.memory.core.builder.TokenChunkingOptions(64, 96),
            new com.openmemind.ai.memory.core.builder.TokenChunkingOptions(64, 96));
}
```

Add to [`MemoryAssemblersTest.java`](/Users/zhengyate/dev/openmemind/memind/memind-core/src/test/java/com/openmemind/ai/memory/core/builder/MemoryAssemblersTest.java):

```java
@Test
void extractionAssemblerSharesProcessorRegistryBetweenRawDataLayerAndExtractor() {
    var assembly =
            new MemoryExtractionAssembler()
                    .assemble(context(MemoryBuildOptions.defaults(), null, null));
    var extractor = (MemoryExtractor) assembly.pipeline();
    var rawDataLayer = readField(extractor, "rawDataStep", RawDataLayer.class);

    assertThat(
                    readField(
                            extractor,
                            "rawContentProcessorRegistry",
                            RawContentProcessorRegistry.class))
            .isSameAs(
                    readField(
                            rawDataLayer,
                            "processorRegistry",
                            RawContentProcessorRegistry.class));
}
```

Keep the existing `fileRequestShouldRejectParsedContentThatExceedsConfiguredLimit()` test in [`MemoryExtractorMultimodalFileTest.java`](/Users/zhengyate/dev/openmemind/memind/memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/MemoryExtractorMultimodalFileTest.java); after this refactor that test becomes the parser-path proof that `MemoryExtractor` routes parsed content through processor validation.

- [ ] **Step 2: Run the targeted test and verify it fails**

Run: `mvn -pl memind-core -Dtest=MemoryExtractorMultimodalFileTest test`

Expected: FAIL because direct-content validation is still handled by `ParsedContentLimitValidator`.

- [ ] **Step 3: Move the validation logic into processors and update `MemoryExtractor`**

Add to [`DocumentContentProcessor.java`](/Users/zhengyate/dev/openmemind/memind/memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/processor/DocumentContentProcessor.java):

```java
@Override
public void validateParsedContent(DocumentContent content) {
    String profile = resolveContentProfile(content);
    var limits =
            resolveGovernanceType(content, profile) == ContentGovernanceType.DOCUMENT_TEXT_LIKE
                    ? options.textLikeParsedLimit()
                    : options.binaryParsedLimit();
    int tokenCount = TokenUtils.countTokens(content.toContentString());
    if (tokenCount > limits.maxTokens()) {
        throw new ParsedContentTooLargeException(
                "Parsed content exceeds token limit: profile=%s tokens=%d max=%d"
                        .formatted(profile, tokenCount, limits.maxTokens()));
    }
    validateDocumentStructure(profile, content, limits);
}
```

Add to [`ImageContentProcessor.java`](/Users/zhengyate/dev/openmemind/memind/memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/processor/ImageContentProcessor.java):

```java
@Override
public void validateParsedContent(ImageContent content) {
    int tokenCount = TokenUtils.countTokens(content.toContentString());
    int maxTokens = options.parsedLimit().maxTokens();
    if (tokenCount > maxTokens) {
        throw new ParsedContentTooLargeException(
                "Parsed content exceeds token limit: profile=%s tokens=%d max=%d"
                        .formatted(resolveProfile(content), tokenCount, maxTokens));
    }
}
```

Add to [`AudioContentProcessor.java`](/Users/zhengyate/dev/openmemind/memind/memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/processor/AudioContentProcessor.java):

```java
@Override
public void validateParsedContent(AudioContent content) {
    int tokenCount = TokenUtils.countTokens(content.toContentString());
    var limit = options.parsedLimit();
    if (tokenCount > limit.maxTokens()) {
        throw new ParsedContentTooLargeException(
                "Parsed content exceeds token limit: profile=%s tokens=%d max=%d"
                        .formatted(resolveProfile(content), tokenCount, limit.maxTokens()));
    }
    Duration duration = resolveAudioDuration(content);
    if (limit.maxDuration() != null
            && duration != null
            && duration.compareTo(limit.maxDuration()) > 0) {
        throw new ParsedContentTooLargeException(
                "Parsed content exceeds duration limit: profile=%s duration=%s max=%s"
                        .formatted(resolveProfile(content), duration, limit.maxDuration()));
    }
}
```

Refactor [`MemoryExtractor.java`](/Users/zhengyate/dev/openmemind/memind/memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/MemoryExtractor.java):

```java
private final RawContentProcessorRegistry rawContentProcessorRegistry;

private RawContentProcessorRegistry processorRegistryRequired() {
    if (rawContentProcessorRegistry == null) {
        throw new IllegalStateException(
                "RawContentProcessorRegistry is required for multimodal validation");
    }
    return rawContentProcessorRegistry;
}

private <T extends RawContent> T validateWithProcessor(T content) {
    processorRegistryRequired().resolve(content).validateParsedContent(content);
    return content;
}
```

Update the constructor chain so the assembler path passes the same `RawContentProcessorRegistry` instance to both `RawDataLayer` and `MemoryExtractor`, while legacy overloads delegate with `null`.

Then replace all `parsedContentLimitValidator.validate(...)` calls in direct/file/url resolution with processor-based validation:

```java
private Mono<ResolvedExtractionRequest> resolveDirectContentRequest(ExtractionRequest request) {
    if (request.content() == null) {
        return Mono.error(
                new IllegalArgumentException(
                        "Extraction request content, fileInput, or urlInput is required"));
    }
    if (request.content().directGovernanceType() == null) {
        return Mono.just(new ResolvedExtractionRequest(request, null));
    }

    RawContent normalizedContent =
            validateWithProcessor(
                    MultimodalMetadataNormalizer.normalizeDirectContent(
                            request.content(), request.metadata()));
    Map<String, Object> normalizedMetadata =
            ExtractionRequest.normalizeMultimodalMetadata(normalizedContent);
    return Mono.just(
            new ResolvedExtractionRequest(
                    new ExtractionRequest(
                            request.memoryId(),
                            normalizedContent,
                            null,
                            null,
                            normalizedContent.contentType(),
                            normalizedMetadata,
                            request.config()),
                    null));
}
```

Apply the same `validateWithProcessor(...)` call in the file and URL parser paths immediately after `normalizeParsedContent(...)`.

Update [`MemoryExtractionAssembler.java`](/Users/zhengyate/dev/openmemind/memind/memind-core/src/main/java/com/openmemind/ai/memory/core/builder/MemoryExtractionAssembler.java) to create one `RawContentProcessorRegistry` from the assembled processor list and pass it to both `RawDataLayer` and `MemoryExtractor`.

Delete [`ParsedContentLimitValidator.java`](/Users/zhengyate/dev/openmemind/memind/memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/ParsedContentLimitValidator.java) and [`ParsedContentLimitValidatorTest.java`](/Users/zhengyate/dev/openmemind/memind/memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/ParsedContentLimitValidatorTest.java).

- [ ] **Step 4: Re-run the targeted test**

Run: `mvn -pl memind-core -Dtest=MemoryExtractorMultimodalFileTest,MemoryAssemblersTest test`

Expected: PASS

- [ ] **Step 5: Run the processor-focused tests**

Run: `mvn -pl memind-core -Dtest=DocumentContentProcessorTest,ImageContentProcessorTest,AudioContentProcessorTest test`

Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/processor/DocumentContentProcessor.java \
        memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/processor/ImageContentProcessor.java \
        memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/processor/AudioContentProcessor.java \
        memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/MemoryExtractor.java \
        memind-core/src/main/java/com/openmemind/ai/memory/core/builder/MemoryExtractionAssembler.java \
        memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/MemoryExtractorMultimodalFileTest.java \
        memind-core/src/test/java/com/openmemind/ai/memory/core/builder/MemoryAssemblersTest.java
git rm memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/ParsedContentLimitValidator.java \
       memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/ParsedContentLimitValidatorTest.java
git commit -m "refactor: move parsed content validation into raw content processors"
```

---

### Task 5: Add explicit plugin registration and transitional builder assembly

**Files:**
- Create: `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/RawContentTypeRegistrar.java`
- Create: `memind-core/src/main/java/com/openmemind/ai/memory/core/plugin/RawDataPlugin.java`
- Create: `memind-core/src/main/java/com/openmemind/ai/memory/core/plugin/RawDataPluginContext.java`
- Create: `memind-core/src/main/java/com/openmemind/ai/memory/core/plugin/CoreBuiltinRawDataPlugin.java`
- Create: `memind-core/src/test/java/com/openmemind/ai/memory/core/builder/RawDataPluginAssemblyTest.java`
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/builder/MemoryBuilder.java`
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/builder/DefaultMemoryBuilder.java`
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/builder/MemoryAssemblyContext.java`
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/builder/MemoryExtractionAssembler.java`
- Modify: `memind-core/src/test/java/com/openmemind/ai/memory/core/builder/MemoryAssemblersTest.java`

- [ ] **Step 1: Write the failing plugin-assembly tests**

Create [`RawDataPluginAssemblyTest.java`](/Users/zhengyate/dev/openmemind/memind/memind-core/src/test/java/com/openmemind/ai/memory/core/builder/RawDataPluginAssemblyTest.java):

```java
package com.openmemind.ai.memory.core.builder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmemind.ai.memory.core.extraction.MemoryExtractor;
import com.openmemind.ai.memory.core.extraction.rawdata.RawContentProcessor;
import com.openmemind.ai.memory.core.extraction.rawdata.RawContentTypeRegistrar;
import com.openmemind.ai.memory.core.extraction.rawdata.content.DocumentContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent;
import com.openmemind.ai.memory.core.plugin.RawDataPlugin;
import com.openmemind.ai.memory.core.plugin.RawDataPluginContext;
import com.openmemind.ai.memory.core.resource.ContentCapability;
import com.openmemind.ai.memory.core.resource.ContentParser;
import com.openmemind.ai.memory.core.resource.ContentParserRegistry;
import com.openmemind.ai.memory.core.resource.DefaultContentParserRegistry;
import com.openmemind.ai.memory.core.resource.SourceDescriptor;
import com.openmemind.ai.memory.core.resource.SourceKind;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class RawDataPluginAssemblyTest {

    @Test
    void duplicatePluginIdFailsFast() {
        RawDataPlugin first = new TestPlugin("document-plugin", List.of(), List.of());
        RawDataPlugin second = new TestPlugin("document-plugin", List.of(), List.of());

        assertThatThrownBy(
                        () -> new DefaultMemoryBuilder().rawDataPlugin(first).rawDataPlugin(second))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate pluginId");
    }

    @Test
    void assemblerBuildsDefaultRegistryFromPluginParsersWhenExplicitRegistryIsMissing() {
        var plugin =
                new TestPlugin("document-plugin", List.of(testParser("document-test")), List.of());
        var assembly =
                new MemoryExtractionAssembler()
                        .assemble(MemoryAssemblersTest.context(MemoryBuildOptions.defaults(), null, null, List.of(plugin)));
        var extractor = (MemoryExtractor) assembly.pipeline();

        ContentParserRegistry registry =
                MemoryAssemblersTest.readField(
                        extractor, "contentParserRegistry", ContentParserRegistry.class);

        assertThat(registry).isInstanceOf(DefaultContentParserRegistry.class);
        assertThat(registry.capabilities())
                .extracting(ContentCapability::parserId)
                .contains("document-test");
    }

    @Test
    void explicitParserRegistryRemainsAuthoritativeOverPluginParsers() {
        ContentParserRegistry explicitRegistry = org.mockito.Mockito.mock(ContentParserRegistry.class);
        var plugin =
                new TestPlugin("document-plugin", List.of(testParser("document-test")), List.of());
        var assembly =
                new MemoryExtractionAssembler()
                        .assemble(
                                MemoryAssemblersTest.context(
                                        MemoryBuildOptions.defaults(),
                                        explicitRegistry,
                                        null,
                                        List.of(plugin)));
        var extractor = (MemoryExtractor) assembly.pipeline();

        assertThat(
                        MemoryAssemblersTest.readField(
                                extractor, "contentParserRegistry", ContentParserRegistry.class))
                .isSameAs(explicitRegistry);
    }

    @Test
    void duplicatePluginParserIdsFailFastDuringAssembly() {
        var first = new TestPlugin("plugin-1", List.of(testParser("duplicate-parser")), List.of());
        var second =
                new TestPlugin("plugin-2", List.of(testParser("duplicate-parser")), List.of());

        assertThatThrownBy(
                        () ->
                                new MemoryExtractionAssembler()
                                        .assemble(
                                                MemoryAssemblersTest.context(
                                                        MemoryBuildOptions.defaults(),
                                                        null,
                                                        null,
                                                        List.of(first, second))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("duplicate-parser");
    }

    @Test
    void duplicateBuiltinAndUserPluginIdsFailFastDuringAssembly() {
        var conflicting =
                new TestPlugin("core-builtin-rawdata", List.of(), List.of());

        assertThatThrownBy(
                        () ->
                                new MemoryExtractionAssembler()
                                        .assemble(
                                                MemoryAssemblersTest.context(
                                                        MemoryBuildOptions.defaults(),
                                                        null,
                                                        null,
                                                        List.of(conflicting))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("core-builtin-rawdata");
    }

    @Test
    void duplicatePluginSubtypeNamesFailFastDuringAssembly() {
        var first =
                new TestPlugin(
                        "plugin-1",
                        List.of(),
                        List.of(() -> Map.of("document", DocumentContent.class)));
        var second =
                new TestPlugin(
                        "plugin-2",
                        List.of(),
                        List.of(() -> Map.of("document", DocumentContent.class)));

        assertThatThrownBy(
                        () ->
                                new MemoryExtractionAssembler()
                                        .assemble(
                                                MemoryAssemblersTest.context(
                                                        MemoryBuildOptions.defaults(),
                                                        null,
                                                        null,
                                                        List.of(first, second))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("document");
    }

    private static ContentParser testParser(String parserId) {
        return new ContentParser() {
            @Override
            public String parserId() {
                return parserId;
            }

            @Override
            public String contentType() {
                return "DOCUMENT";
            }

            @Override
            public String contentProfile() {
                return "document.binary";
            }

            @Override
            public Set<String> supportedMimeTypes() {
                return Set.of("application/pdf");
            }

            @Override
            public boolean supports(SourceDescriptor source) {
                return source.kind() == SourceKind.FILE
                        && "application/pdf".equals(source.mimeType());
            }

            @Override
            public Mono<RawContent> parse(byte[] data, SourceDescriptor source) {
                return Mono.error(new UnsupportedOperationException("not needed for assembly test"));
            }
        };
    }

    private record TestPlugin(
            String pluginId,
            List<ContentParser> parsers,
            List<RawContentTypeRegistrar> typeRegistrars)
            implements RawDataPlugin {

        @Override
        public List<RawContentProcessor<?>> processors(RawDataPluginContext context) {
            return List.of();
        }

        @Override
        public List<ContentParser> parsers(RawDataPluginContext context) {
            return parsers;
        }

        @Override
        public List<RawContentTypeRegistrar> typeRegistrars() {
            return typeRegistrars;
        }
    }
}
```

Add to [`MemoryAssemblersTest.java`](/Users/zhengyate/dev/openmemind/memind/memind-core/src/test/java/com/openmemind/ai/memory/core/builder/MemoryAssemblersTest.java):

```java
@Test
void extractionAssemblerStillBuildsFiveProcessorsViaConversationPlusBuiltinPlugin() {
    var assembly =
            new MemoryExtractionAssembler()
                    .assemble(context(MemoryBuildOptions.defaults(), null, null, List.of()));
    var extractor = (MemoryExtractor) assembly.pipeline();
    var rawDataLayer = readField(extractor, "rawDataStep", RawDataLayer.class);

    var processorRegistry =
            readField(rawDataLayer, "processorRegistry", RawContentProcessorRegistry.class);

    assertThat(processorRegistry.all()).hasSize(5);
}
```

- [ ] **Step 2: Run the targeted tests and verify they fail**

Run: `mvn -pl memind-core -Dtest=RawDataPluginAssemblyTest,MemoryAssemblersTest test`

Expected: FAIL because `MemoryBuilder` has no `rawDataPlugin(...)` method and there is no plugin assembly path yet.

- [ ] **Step 3: Add the SPI surfaces and transitional assembly path**

Create [`RawDataPlugin.java`](/Users/zhengyate/dev/openmemind/memind/memind-core/src/main/java/com/openmemind/ai/memory/core/plugin/RawDataPlugin.java):

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

Create [`RawDataPluginContext.java`](/Users/zhengyate/dev/openmemind/memind/memind-core/src/main/java/com/openmemind/ai/memory/core/plugin/RawDataPluginContext.java):

```java
public record RawDataPluginContext(
        ChatClientRegistry chatClientRegistry,
        PromptRegistry promptRegistry,
        MemoryBuildOptions buildOptions) {}
```

Create [`CoreBuiltinRawDataPlugin.java`](/Users/zhengyate/dev/openmemind/memind/memind-core/src/main/java/com/openmemind/ai/memory/core/plugin/CoreBuiltinRawDataPlugin.java):

```java
final class CoreBuiltinRawDataPlugin implements RawDataPlugin {

    @Override
    public String pluginId() {
        return "core-builtin-rawdata";
    }

    @Override
    public List<RawContentProcessor<?>> processors(RawDataPluginContext context) {
        var rawdata = context.buildOptions().extraction().rawdata();
        return List.of(
                new ToolCallContentProcessor(
                        new ToolCallChunker(rawdata.toolCall()),
                        new ToolCallCaptionGenerator(),
                        new LlmToolCallItemExtractionStrategy(
                                context.chatClientRegistry().resolve(ChatClientSlot.TOOL_CALL_EXTRACTION),
                                context.promptRegistry())),
                new DocumentContentProcessor(new ProfileAwareDocumentChunker(), rawdata.document()),
                new ImageContentProcessor(new ImageSegmentComposer(), rawdata.image()),
                new AudioContentProcessor(new TranscriptSegmentChunker(), rawdata.audio()));
    }

    @Override
    public List<RawContentTypeRegistrar> typeRegistrars() {
        return List.of(
                () -> Map.of("tool_call", ToolCallContent.class),
                () -> Map.of("document", DocumentContent.class),
                () -> Map.of("image", ImageContent.class),
                () -> Map.of("audio", AudioContent.class));
    }
}
```

Keep `parsers()` returning `List.of()` in this transitional plugin for Phase 1 so builder behavior does not silently change when the caller did not explicitly register any parser-providing plugin.

Modify [`MemoryBuilder.java`](/Users/zhengyate/dev/openmemind/memind/memind-core/src/main/java/com/openmemind/ai/memory/core/builder/MemoryBuilder.java):

```java
MemoryBuilder rawDataPlugin(RawDataPlugin plugin);
```

Modify [`DefaultMemoryBuilder.java`](/Users/zhengyate/dev/openmemind/memind/memind-core/src/main/java/com/openmemind/ai/memory/core/builder/DefaultMemoryBuilder.java):

```java
private final List<RawDataPlugin> rawDataPlugins = new ArrayList<>();

@Override
public MemoryBuilder rawDataPlugin(RawDataPlugin plugin) {
    Objects.requireNonNull(plugin, "plugin");
    if (rawDataPlugins.stream().anyMatch(existing -> existing.pluginId().equals(plugin.pluginId()))) {
        throw new IllegalArgumentException("duplicate pluginId: " + plugin.pluginId());
    }
    rawDataPlugins.add(plugin);
    return this;
}
```

Pass `List.copyOf(rawDataPlugins)` through [`MemoryAssemblyContext.java`](/Users/zhengyate/dev/openmemind/memind/memind-core/src/main/java/com/openmemind/ai/memory/core/builder/MemoryAssemblyContext.java), and in [`MemoryExtractionAssembler.java`](/Users/zhengyate/dev/openmemind/memind/memind-core/src/main/java/com/openmemind/ai/memory/core/builder/MemoryExtractionAssembler.java):

```java
var pluginContext = new RawDataPluginContext(registry, context.promptRegistry(), context.options());
List<RawDataPlugin> plugins = resolvePlugins(context);

List<RawContentProcessor<?>> processors = new ArrayList<>();
processors.add(
        conversationProcessor(
                registry, captionGenerator, context.promptRegistry(), context.options()));
for (var plugin : plugins) {
    processors.addAll(plugin.processors(pluginContext));
}

List<ContentParser> pluginParsers =
        plugins.stream().flatMap(plugin -> plugin.parsers(pluginContext).stream()).toList();
List<RawContentTypeRegistrar> typeRegistrars =
        plugins.stream().flatMap(plugin -> plugin.typeRegistrars().stream()).toList();
RawContentJackson.pluginSubtypeMappings(typeRegistrars);
ContentParserRegistry effectiveParserRegistry =
        context.contentParserRegistry() != null
                ? context.contentParserRegistry()
                : pluginParsers.isEmpty()
                        ? null
                        : new DefaultContentParserRegistry(pluginParsers);
```

Add to the same assembler:

```java
private List<RawDataPlugin> resolvePlugins(MemoryAssemblyContext context) {
    List<RawDataPlugin> plugins = new ArrayList<>();
    plugins.add(new CoreBuiltinRawDataPlugin());
    plugins.addAll(context.rawDataPlugins());

    Set<String> pluginIds = new LinkedHashSet<>();
    for (RawDataPlugin plugin : plugins) {
        String pluginId = Objects.requireNonNull(plugin.pluginId(), "plugin.pluginId()");
        if (!pluginIds.add(pluginId)) {
            throw new IllegalStateException("Duplicate rawData pluginId: " + pluginId);
        }
    }
    return List.copyOf(plugins);
}
```

Keep explicit parser-registry precedence in the assembler path exactly as above. If no explicit registry and no registered plugin contributes parsers, keep `effectiveParserRegistry` as `null` to preserve current behavior. In Phase 1, `typeRegistrars` are aggregated and validated for duplicate subtype names, but they are not yet injected into a process-global mapper; runtime-scoped mapper wiring is deferred to Phase 2 when codecs/plugins are physically split.

In [`MemoryAssemblersTest.java`](/Users/zhengyate/dev/openmemind/memind/memind-core/src/test/java/com/openmemind/ai/memory/core/builder/MemoryAssemblersTest.java), change the lightweight harness helpers `context(...)` and `readField(...)` from `private static` to package-private `static` so [`RawDataPluginAssemblyTest.java`](/Users/zhengyate/dev/openmemind/memind/memind-core/src/test/java/com/openmemind/ai/memory/core/builder/RawDataPluginAssemblyTest.java) can reuse them without copy-pasting another reflection harness.

Also update the `context(...)` helper signature in [`MemoryAssemblersTest.java`](/Users/zhengyate/dev/openmemind/memind/memind-core/src/test/java/com/openmemind/ai/memory/core/builder/MemoryAssemblersTest.java) to accept `List<RawDataPlugin> rawDataPlugins` and forward that list into the new `MemoryAssemblyContext(..., rawDataPlugins)` constructor.

- [ ] **Step 4: Re-run the targeted tests**

Run: `mvn -pl memind-core -Dtest=RawDataPluginAssemblyTest,MemoryAssemblersTest test`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/RawContentTypeRegistrar.java \
        memind-core/src/main/java/com/openmemind/ai/memory/core/plugin/RawDataPlugin.java \
        memind-core/src/main/java/com/openmemind/ai/memory/core/plugin/RawDataPluginContext.java \
        memind-core/src/main/java/com/openmemind/ai/memory/core/plugin/CoreBuiltinRawDataPlugin.java \
        memind-core/src/main/java/com/openmemind/ai/memory/core/builder/MemoryBuilder.java \
        memind-core/src/main/java/com/openmemind/ai/memory/core/builder/DefaultMemoryBuilder.java \
        memind-core/src/main/java/com/openmemind/ai/memory/core/builder/MemoryAssemblyContext.java \
        memind-core/src/main/java/com/openmemind/ai/memory/core/builder/MemoryExtractionAssembler.java \
        memind-core/src/test/java/com/openmemind/ai/memory/core/builder/RawDataPluginAssemblyTest.java \
        memind-core/src/test/java/com/openmemind/ai/memory/core/builder/MemoryAssemblersTest.java
git commit -m "feat: add explicit raw data plugin registration to core builder"
```

---

### Task 6: Add `RawContentJackson` helper and core tests

**Files:**
- Create: `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/RawContentJackson.java`
- Create: `memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/rawdata/RawContentJacksonTest.java`
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/utils/JsonUtils.java`

- [ ] **Step 1: Write the failing helper tests**

Create [`RawContentJacksonTest.java`](/Users/zhengyate/dev/openmemind/memind/memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/rawdata/RawContentJacksonTest.java):

```java
package com.openmemind.ai.memory.core.extraction.rawdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmemind.ai.memory.core.extraction.rawdata.content.ConversationContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.DocumentContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.ToolCallContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RawContentJacksonTest {

    @Test
    void registerCoreSubtypesSupportsConversationDeserializationWithoutPlugins() throws Exception {
        var mapper = new ObjectMapper();
        RawContentJackson.registerCoreSubtypes(mapper);

        String json = mapper.writeValueAsString(new ConversationContent(List.of(Message.user("hello"))));
        RawContent decoded = mapper.readValue(json, RawContent.class);

        assertThat(decoded).isInstanceOf(ConversationContent.class);
    }

    @Test
    void registerPluginSubtypesAppliesRegistrarMappings() throws Exception {
        var mapper = new ObjectMapper();
        RawContentJackson.registerCoreSubtypes(mapper);
        RawContentJackson.registerPluginSubtypes(
                mapper, List.of(() -> Map.of("tool_call", ToolCallContent.class)));

        String json =
                "{\"type\":\"tool_call\",\"calls\":[]}";

        RawContent decoded = mapper.readValue(json, RawContent.class);

        assertThat(decoded.contentType()).isEqualTo("TOOL_CALL");
    }

    @Test
    void duplicateSubtypeNamesFailFast() {
        var mapper = new ObjectMapper();

        assertThatThrownBy(
                        () ->
                                RawContentJackson.registerPluginSubtypes(
                                        mapper,
                                        List.of(
                                                () -> Map.of("document", DocumentContent.class),
                                                () -> Map.of("document", DocumentContent.class))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("document");
    }
}
```

- [ ] **Step 2: Run the targeted test and verify it fails**

Run: `mvn -pl memind-core -Dtest=RawContentJacksonTest test`

Expected: FAIL because `RawContentJackson` does not exist yet.

- [ ] **Step 3: Implement the helper and wire `JsonUtils`**

Create [`RawContentJackson.java`](/Users/zhengyate/dev/openmemind/memind/memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/RawContentJackson.java):

```java
public final class RawContentJackson {

    private RawContentJackson() {}

    public static void registerCoreSubtypes(ObjectMapper mapper) {
        mapper.registerSubtypes(new NamedType(ConversationContent.class, "conversation"));
    }

    public static void registerPluginSubtypes(
            ObjectMapper mapper, Collection<RawContentTypeRegistrar> registrars) {
        for (var entry : pluginSubtypeMappings(registrars).entrySet()) {
            mapper.registerSubtypes(new NamedType(entry.getValue(), entry.getKey()));
        }
    }

    public static void registerAll(
            ObjectMapper mapper, Collection<RawContentTypeRegistrar> registrars) {
        registerCoreSubtypes(mapper);
        registerPluginSubtypes(mapper, registrars);
    }

    public static Map<String, Class<? extends RawContent>> pluginSubtypeMappings(
            Collection<RawContentTypeRegistrar> registrars) {
        Map<String, Class<? extends RawContent>> mappings = new LinkedHashMap<>();
        for (RawContentTypeRegistrar registrar :
                Objects.requireNonNull(registrars, "registrars")) {
            for (var entry : registrar.subtypes().entrySet()) {
                Class<? extends RawContent> previous =
                        mappings.putIfAbsent(entry.getKey(), entry.getValue());
                if (previous != null) {
                    throw new IllegalArgumentException(
                            "Duplicate RawContent subtype name: " + entry.getKey());
                }
            }
        }
        return Map.copyOf(mappings);
    }
}
```

Update [`JsonUtils.java`](/Users/zhengyate/dev/openmemind/memind/memind-core/src/main/java/com/openmemind/ai/memory/core/utils/JsonUtils.java):

```java
private static ObjectMapper createMapper() {
    ObjectMapper mapper =
            new ObjectMapper()
                    .registerModule(new JavaTimeModule())
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    RawContentJackson.registerCoreSubtypes(mapper);
    return mapper;
}
```

This remains intentionally narrow in Phase 1: `JsonUtils` only gains explicit core-subtype registration for consistency with `RawContentJackson`, but plugin-specific runtime mapper injection is deferred. Phase 2 will introduce runtime-scoped mapper/codec wiring so subtype registration follows each assembled `Memory` instance instead of mutating process-global state.

- [ ] **Step 4: Re-run the targeted test**

Run: `mvn -pl memind-core -Dtest=RawContentJacksonTest test`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/RawContentJackson.java \
        memind-core/src/main/java/com/openmemind/ai/memory/core/utils/JsonUtils.java \
        memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/rawdata/RawContentJacksonTest.java
git commit -m "feat: add raw content jackson registration helper"
```

---

### Task 7: Full regression run and plan exit criteria

**Files:**
- Review all files touched in Tasks 1-6

- [ ] **Step 1: Run the full `memind-core` test suite**

Run: `mvn -pl memind-core test`

Expected: PASS

- [ ] **Step 2: Run the full project verification**

Run: `mvn clean verify -DskipTests=false`

Expected: PASS

- [ ] **Step 3: Verify the Phase 1 exit criteria**

Check the codebase and confirm all of the following are true:

- `RawContent` exposes metadata/governance SPI methods and `DocumentContent` / `ImageContent` / `AudioContent` override them
- `RawContentProcessor` exposes `usesSourceIdentity()`, `validateParsedContent()`, `resolveSegmentStartTime()`, and `resolveSegmentEndTime()`
- `RawContentProcessorRegistry` exists and the assembler shares one instance between `RawDataLayer` and `MemoryExtractor`
- `RawDataLayer` no longer uses hardcoded multimodal type checks or `ConversationContent` type checks for timestamp resolution
- `MultimodalMetadataNormalizer` and `ExtractionRequest.of(...)` no longer `instanceof`-dispatch on document/image/audio
- `MemoryExtractor` validates parsed/direct content through processor hooks rather than `ParsedContentLimitValidator`
- `ParsedContentLimitValidator.java` and its test are gone
- `MemoryBuilder` accepts explicit `rawDataPlugin(...)` registrations
- builder/plugin assembly rejects duplicate plugin IDs including builtin-vs-user conflicts, rejects duplicate parser IDs, and honors explicit `ContentParserRegistry` precedence
- non-Spring assembler path aggregates and validates plugin `typeRegistrars()` contributions without mutating process-global mapper state
- `RawContentJackson` rejects duplicate subtype discriminator names
- `RawContentJackson` exists and is covered by tests
- `@JsonSubTypes` is still present on `RawContent` in this phase

- [ ] **Step 4: Commit the regression checkpoint**

```bash
git add memind-core docs/plans/2026-04-11-rawdata-plugin-extraction-phase1-core-spi.md
git commit -m "test: verify rawdata plugin extraction phase 1"
```

---

## Guardrails

- Do not remove `@JsonSubTypes` from `RawContent` in this phase.
- Do not move any document/image/audio/toolcall production files out of `memind-core` in this phase.
- Do not add ServiceLoader-based plugin discovery in this phase; explicit `rawDataPlugin(...)` registration is the only non-Spring plugin path.
- Do not remove `ExtractionRequest.document()` / `image()` / `audio()` yet; deprecate and delegate only.
- Do not change public `contentType()` values or ToolCall public API signatures.
- Do not split `ContentTypes.DOCUMENT` / `IMAGE` / `AUDIO` out of core in this phase; defer constant relocation to Phase 2 when the concrete rawdata modules actually move.
- Do not mutate process-global mapper state with plugin-specific subtype registration in this phase; runtime-scoped mapper wiring belongs to Phase 2.
- If assembling a parser registry from plugin parsers would change behavior when no plugins are registered, keep the registry `null` so current behavior is preserved.

---

## Follow-up Phases

- **Phase 2:** Create plugin Maven modules, move document/image/audio/toolcall implementations, remove `@JsonSubTypes`, and make `RawContentJackson` the authoritative subtype path
- **Phase 3:** Add Spring Boot starter modules and auto-configured `RawDataPlugin` beans
- **Phase 4:** Implement image/audio parser plugins and migrate persistence codecs outside `memind-core` to use `RawContentJackson`
