# Content Governance Separation Implementation Plan

> **Execution:** Inline in the main agent only. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Separate open-ended `contentProfile` from authoritative multimodal governance routing so plugins can emit richer descriptive profiles without breaking source limits, parsed-content validation, or chunking policy selection.

**Architecture:** Introduce a closed `ContentGovernanceType` for all core governance decisions and keep `contentProfile` as an open descriptive field. Parser-backed requests get authoritative governance from `ParserResolution.capability()`, but their final descriptive `contentProfile` may be refined by parsed metadata within the same governance family; direct-content requests derive governance in metadata normalization, and legacy fallback is allowed only for builtin profiles.

**Tech Stack:** Java 21, Reactor, Maven, JUnit 5, AssertJ

---

## File Structure

**Create**
- `memind-core/src/main/java/com/openmemind/ai/memory/core/data/enums/ContentGovernanceType.java`
- `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/BuiltinContentProfiles.java`
- `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/ContentGovernanceResolver.java`
- `memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/ContentGovernanceResolverTest.java`
- `memind-core/src/test/java/com/openmemind/ai/memory/core/resource/NativeTextDocumentContentParserTest.java`

**Modify**
- `memind-core/src/main/java/com/openmemind/ai/memory/core/resource/ContentParser.java`
- `memind-core/src/main/java/com/openmemind/ai/memory/core/resource/ContentCapability.java`
- `memind-core/src/main/java/com/openmemind/ai/memory/core/resource/DefaultContentParserRegistry.java`
- `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/MultimodalMetadataNormalizer.java`
- `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/ExtractionRequest.java`
- `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/MemoryExtractor.java`
- `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/ParsedContentLimitValidator.java`
- `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/processor/DocumentContentProcessor.java`
- `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/chunk/ProfileAwareDocumentChunker.java`
- `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/item/SegmentBudgetEnforcer.java`
- `memind-core/src/test/java/com/openmemind/ai/memory/core/resource/ContentParserRegistryTest.java`
- `memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/ExtractionRequestTest.java`
- `memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/ParsedContentLimitValidatorTest.java`
- `memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/MemoryExtractorMultimodalFileTest.java`
- `memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/rawdata/processor/DocumentContentProcessorTest.java`
- `memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/item/SegmentBudgetEnforcerTest.java`

### Task 1: Add Closed Governance Primitives And Resolver

**Files:**
- Create: `memind-core/src/main/java/com/openmemind/ai/memory/core/data/enums/ContentGovernanceType.java`
- Create: `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/BuiltinContentProfiles.java`
- Create: `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/ContentGovernanceResolver.java`
- Create: `memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/ContentGovernanceResolverTest.java`

- [ ] **Step 1: Write the failing test**

```java
class ContentGovernanceResolverTest {

    @Test
    void resolvesBuiltinProfilesToClosedGovernanceTypes() {
        assertThat(BuiltinContentProfiles.governanceTypeOf("document.markdown"))
                .hasValue(ContentGovernanceType.DOCUMENT_TEXT_LIKE);
        assertThat(BuiltinContentProfiles.governanceTypeOf("document.binary"))
                .hasValue(ContentGovernanceType.DOCUMENT_BINARY);
        assertThat(BuiltinContentProfiles.governanceTypeOf("image.caption-ocr"))
                .hasValue(ContentGovernanceType.IMAGE_CAPTION_OCR);
        assertThat(BuiltinContentProfiles.governanceTypeOf("audio.transcript"))
                .hasValue(ContentGovernanceType.AUDIO_TRANSCRIPT);
    }

    @Test
    void prefersExplicitGovernanceTypeWhenPresent() {
        assertThat(ContentGovernanceResolver.resolveRequired(
                        Map.of(
                                "contentProfile", "document.pdf.tika",
                                "governanceType", "DOCUMENT_BINARY")))
                .isEqualTo(ContentGovernanceType.DOCUMENT_BINARY);
    }

    @Test
    void fallsBackToBuiltinProfileWhenGovernanceTypeIsMissing() {
        assertThat(ContentGovernanceResolver.resolveRequired(
                        Map.of("contentProfile", "document.markdown")))
                .isEqualTo(ContentGovernanceType.DOCUMENT_TEXT_LIKE);
    }

    @Test
    void rejectsUnknownProfileWithoutGovernanceType() {
        assertThatThrownBy(
                        () -> ContentGovernanceResolver.resolveRequired(
                                Map.of("contentProfile", "document.pdf.tika")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("document.pdf.tika")
                .hasMessageContaining("governanceType");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl memind-core test -Dtest=ContentGovernanceResolverTest`

Expected: FAIL with missing classes or unresolved symbols for `ContentGovernanceType`, `BuiltinContentProfiles`, and `ContentGovernanceResolver`

- [ ] **Step 3: Write minimal implementation**

```java
public enum ContentGovernanceType {
    DOCUMENT_TEXT_LIKE,
    DOCUMENT_BINARY,
    IMAGE_CAPTION_OCR,
    AUDIO_TRANSCRIPT
}
```

```java
public final class BuiltinContentProfiles {

    public static final String DOCUMENT_MARKDOWN = "document.markdown";
    public static final String DOCUMENT_HTML = "document.html";
    public static final String DOCUMENT_TEXT = "document.text";
    public static final String DOCUMENT_BINARY = "document.binary";
    public static final String IMAGE_CAPTION_OCR = "image.caption-ocr";
    public static final String AUDIO_TRANSCRIPT = "audio.transcript";

    private static final Map<String, ContentGovernanceType> GOVERNANCE_TYPES =
            Map.of(
                    DOCUMENT_MARKDOWN, ContentGovernanceType.DOCUMENT_TEXT_LIKE,
                    DOCUMENT_HTML, ContentGovernanceType.DOCUMENT_TEXT_LIKE,
                    DOCUMENT_TEXT, ContentGovernanceType.DOCUMENT_TEXT_LIKE,
                    DOCUMENT_BINARY, ContentGovernanceType.DOCUMENT_BINARY,
                    IMAGE_CAPTION_OCR, ContentGovernanceType.IMAGE_CAPTION_OCR,
                    AUDIO_TRANSCRIPT, ContentGovernanceType.AUDIO_TRANSCRIPT);

    private BuiltinContentProfiles() {}

    public static Optional<ContentGovernanceType> governanceTypeOf(String contentProfile) {
        return Optional.ofNullable(GOVERNANCE_TYPES.get(contentProfile));
    }
}
```

```java
public final class ContentGovernanceResolver {

    private ContentGovernanceResolver() {}

    public static ContentGovernanceType resolveRequired(Map<String, Object> metadata) {
        Object governanceType = metadata.get("governanceType");
        if (governanceType != null) {
            return ContentGovernanceType.valueOf(governanceType.toString());
        }
        Object contentProfile = metadata.get("contentProfile");
        return BuiltinContentProfiles.governanceTypeOf(
                        contentProfile == null ? null : contentProfile.toString())
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "Missing governanceType for contentProfile="
                                                + contentProfile));
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -pl memind-core test -Dtest=ContentGovernanceResolverTest`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add memind-core/src/main/java/com/openmemind/ai/memory/core/data/enums/ContentGovernanceType.java memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/BuiltinContentProfiles.java memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/ContentGovernanceResolver.java memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/ContentGovernanceResolverTest.java
git commit -m "refactor: add multimodal governance primitives"
```

### Task 2: Extend Parser Contracts With Authoritative Governance Routing

**Files:**
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/resource/ContentParser.java`
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/resource/ContentCapability.java`
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/resource/DefaultContentParserRegistry.java`
- Create: `memind-core/src/test/java/com/openmemind/ai/memory/core/resource/NativeTextDocumentContentParserTest.java`
- Test: `memind-core/src/test/java/com/openmemind/ai/memory/core/resource/ContentParserRegistryTest.java`

- [ ] **Step 1: Write the failing tests**

```java
@Test
void resolutionCarriesClosedGovernanceTypeAlongsideProfile() {
    ContentParser parser = new TestParser() {
        @Override
        public String contentProfile() {
            return "document.binary";
        }
    };

    ParserResolution resolution =
            new DefaultContentParserRegistry(List.of(parser))
                    .resolve(new SourceDescriptor(SourceKind.FILE, "a.pdf", "application/pdf", 10L, null))
                    .block();

    assertThat(resolution).isNotNull();
    assertThat(resolution.capability().contentProfile()).isEqualTo("document.binary");
    assertThat(resolution.capability().governanceType())
            .isEqualTo(ContentGovernanceType.DOCUMENT_BINARY);
}

@Test
void registryRejectsNonBuiltinProfileWithoutExplicitGovernanceType() {
    ContentParser parser = new TestParser() {
        @Override
        public String parserId() {
            return "document-custom";
        }

        @Override
        public String contentProfile() {
            return "document.pdf.tika";
        }
    };

    assertThatThrownBy(() -> new DefaultContentParserRegistry(List.of(parser)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("document.pdf.tika")
            .hasMessageContaining("governanceType()");
}

@Test
void nativeTextParserMayRefineProfileAfterParseWithinSameGovernanceFamily() {
    RawContent content =
            new NativeTextDocumentContentParser()
                    .parse(
                            "# Title".getBytes(StandardCharsets.UTF_8),
                            new SourceDescriptor(
                                    SourceKind.FILE,
                                    "guide.md",
                                    "text/markdown",
                                    7L,
                                    null))
                    .block();

    assertThat(((DocumentContent) content).metadata())
            .containsEntry("parserId", "document-native-text")
            .containsEntry("contentProfile", "document.markdown");
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q -pl memind-core test -Dtest=ContentParserRegistryTest,NativeTextDocumentContentParserTest`

Expected: FAIL because `governanceType()` / `ContentCapability.governanceType()` do not exist yet

- [ ] **Step 3: Write minimal implementation**

```java
public interface ContentParser {
    String parserId();
    String contentType();
    String contentProfile();

    default ContentGovernanceType governanceType() {
        return BuiltinContentProfiles.governanceTypeOf(contentProfile())
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "Parser "
                                                + parserId()
                                                + " uses non-builtin contentProfile "
                                                + contentProfile()
                                                + " and must override governanceType()"));
    }
}
```

```java
public record ContentCapability(
        String parserId,
        String contentType,
        String contentProfile,
        ContentGovernanceType governanceType,
        Set<String> supportedMimeTypes,
        Set<String> supportedExtensions,
        int priority) {}
```

```java
private static void validate(List<ContentParser> parsers) {
    for (ContentParser parser : parsers) {
        Objects.requireNonNull(parser.contentProfile(), "parser.contentProfile()");
        Objects.requireNonNull(parser.governanceType(), "parser.governanceType()");
    }
}
```

```java
private static ContentCapability capabilityOf(ContentParser parser) {
    return new ContentCapability(
            parser.parserId(),
            parser.contentType(),
            parser.contentProfile(),
            parser.governanceType(),
            parser.supportedMimeTypes(),
            parser.supportedExtensions(),
            parser.priority());
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q -pl memind-core test -Dtest=ContentParserRegistryTest,NativeTextDocumentContentParserTest`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add memind-core/src/main/java/com/openmemind/ai/memory/core/resource/ContentParser.java memind-core/src/main/java/com/openmemind/ai/memory/core/resource/ContentCapability.java memind-core/src/main/java/com/openmemind/ai/memory/core/resource/DefaultContentParserRegistry.java memind-core/src/test/java/com/openmemind/ai/memory/core/resource/ContentParserRegistryTest.java memind-core/src/test/java/com/openmemind/ai/memory/core/resource/NativeTextDocumentContentParserTest.java
git commit -m "refactor: carry governance type through parser resolution"
```

### Task 3: Normalize Authoritative Governance Metadata

**Files:**
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/MultimodalMetadataNormalizer.java`
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/ExtractionRequest.java`
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/MemoryExtractor.java`
- Test: `memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/ExtractionRequestTest.java`
- Test: `memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/MemoryExtractorMultimodalFileTest.java`

- [ ] **Step 1: Write the failing tests**

```java
@Test
void directDocumentRequestIncludesDerivedGovernanceType() {
    ExtractionRequest request =
            ExtractionRequest.document(
                    DefaultMemoryId.of("u", "a"),
                    new DocumentContent(
                            "Guide",
                            "application/pdf",
                            "body",
                            List.of(),
                            null,
                            Map.of("contentProfile", "document.pdf.external")));

    assertThat(request.metadata())
            .containsEntry("contentProfile", "document.pdf.external")
            .containsEntry("governanceType", "DOCUMENT_BINARY");
}

@Test
void directRequestRejectsConflictingGovernanceType() {
    assertThatThrownBy(
                    () ->
                            ExtractionRequest.document(
                                    DefaultMemoryId.of("u", "a"),
                                    new DocumentContent(
                                            "Guide",
                                            "text/markdown",
                                            "# Title",
                                            List.of(),
                                            null,
                                            Map.of("governanceType", "DOCUMENT_BINARY"))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("governanceType");
}

@Test
void parserBackedFileRequestUsesResolutionGovernanceType() {
    assertThat(rawDataStep.lastMetadata())
            .containsEntry("contentProfile", "document.binary")
            .containsEntry("governanceType", "DOCUMENT_BINARY");
}

@Test
void parserBackedMarkdownFileKeepsRefinedProfileAndAuthoritativeGovernanceType() {
    assertThat(rawDataStep.lastMetadata())
            .containsEntry("contentProfile", "document.markdown")
            .containsEntry("governanceType", "DOCUMENT_TEXT_LIKE");
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q -pl memind-core test -Dtest=ExtractionRequestTest,MemoryExtractorMultimodalFileTest`

Expected: FAIL because `governanceType` metadata is missing and direct validation is not enforced

- [ ] **Step 3: Write minimal implementation**

```java
public static Map<String, Object> normalizeDirect(RawContent content) {
    var normalized = new LinkedHashMap<>(baseMetadata(content));
    ContentGovernanceType governanceType = deriveDirectGovernanceType(content);
    String defaultProfile = deriveDirectProfile(content);
    validateGovernanceOverride(normalized, governanceType);
    validateBuiltinProfileCompatibility(normalized.get("contentProfile"), governanceType);
    normalized.put("sourceKind", "DIRECT");
    normalized.put("parserId", "direct");
    normalized.putIfAbsent("contentProfile", defaultProfile);
    normalized.put("governanceType", governanceType.name());
    return Map.copyOf(normalized);
}
```

```java
private static ContentGovernanceType deriveDirectGovernanceType(RawContent content) {
    if (content instanceof DocumentContent documentContent) {
        String mimeType = documentContent.mimeType();
        if ("text/markdown".equals(mimeType)
                || "text/html".equals(mimeType)
                || "text/plain".equals(mimeType)
                || "text/csv".equals(mimeType)) {
            return ContentGovernanceType.DOCUMENT_TEXT_LIKE;
        }
        if (mimeType != null && !mimeType.isBlank()) {
            return ContentGovernanceType.DOCUMENT_BINARY;
        }
        return documentContent.sections().isEmpty()
                ? ContentGovernanceType.DOCUMENT_TEXT_LIKE
                : ContentGovernanceType.DOCUMENT_BINARY;
    }
    if (content instanceof ImageContent) {
        return ContentGovernanceType.IMAGE_CAPTION_OCR;
    }
    if (content instanceof AudioContent) {
        return ContentGovernanceType.AUDIO_TRANSCRIPT;
    }
    throw new IllegalArgumentException("Unsupported direct content type: " + content.contentType());
}
```

```java
private static void validateGovernanceOverride(
        Map<String, Object> metadata, ContentGovernanceType governanceType) {
    Object provided = metadata.get("governanceType");
    if (provided != null && !governanceType.name().equals(provided.toString())) {
        throw new IllegalArgumentException(
                "Conflicting governanceType: expected="
                        + governanceType.name()
                        + " actual="
                        + provided);
    }
}

private static void validateBuiltinProfileCompatibility(
        Object providedProfile, ContentGovernanceType governanceType) {
    if (providedProfile == null) {
        return;
    }
    BuiltinContentProfiles.governanceTypeOf(providedProfile.toString())
            .filter(mapped -> mapped != governanceType)
            .ifPresent(
                    mapped -> {
                        throw new IllegalArgumentException(
                                "Conflicting contentProfile="
                                        + providedProfile
                                        + " for governanceType="
                                        + governanceType.name());
                    });
}
```

```java
Map<String, Object> normalized =
        MultimodalMetadataNormalizer.normalizeParsed(
                parsedContent,
                request.metadata(),
                resolution.capability().parserId(),
                resolution.capability().contentProfile(),
                resolution.capability().governanceType());
```

```java
public static Map<String, Object> normalizeParsed(
        RawContent content,
        Map<String, Object> requestMetadata,
        String parserId,
        String contentProfile,
        ContentGovernanceType governanceType) {
    var normalized = new LinkedHashMap<String, Object>();
    if (requestMetadata != null) {
        normalized.putAll(requestMetadata);
    }
    normalized.putAll(baseMetadata(content));
    validateAuthoritativeField(normalized, "parserId", parserId);
    validateAuthoritativeField(normalized, "governanceType", governanceType.name());
    String resolvedProfile =
            resolveParsedContentProfile(
                    normalized.get("contentProfile"), contentProfile, governanceType);
    normalized.put("parserId", parserId);
    normalized.put("contentProfile", resolvedProfile);
    normalized.put("governanceType", governanceType.name());
    return Map.copyOf(normalized);
}
```

```java
private static String resolveParsedContentProfile(
        Object parsedProfile,
        String defaultProfile,
        ContentGovernanceType governanceType) {
    String resolved =
            parsedProfile == null || parsedProfile.toString().isBlank()
                    ? defaultProfile
                    : parsedProfile.toString();
    BuiltinContentProfiles.governanceTypeOf(resolved)
            .filter(mapped -> mapped != governanceType)
            .ifPresent(
                    mapped -> {
                        throw new IllegalArgumentException(
                                "Conflicting contentProfile="
                                        + resolved
                                        + " for governanceType="
                                        + governanceType.name());
                    });
    return resolved;
}
```

```java
private static void validateAuthoritativeField(
        Map<String, Object> metadata, String key, String authoritativeValue) {
    Object current = metadata.get(key);
    if (current != null && !authoritativeValue.equals(current.toString())) {
        throw new IllegalArgumentException(
                "Conflicting " + key + ": expected=" + authoritativeValue + " actual=" + current);
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q -pl memind-core test -Dtest=ExtractionRequestTest,MemoryExtractorMultimodalFileTest`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/MultimodalMetadataNormalizer.java memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/ExtractionRequest.java memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/MemoryExtractor.java memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/ExtractionRequestTest.java memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/MemoryExtractorMultimodalFileTest.java
git commit -m "refactor: normalize authoritative multimodal governance metadata"
```

### Task 4: Move Core Routing To Closed Governance Types

**Files:**
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/MemoryExtractor.java`
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/ParsedContentLimitValidator.java`
- Test: `memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/ParsedContentLimitValidatorTest.java`
- Test: `memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/MemoryExtractorMultimodalFileTest.java`

- [ ] **Step 1: Write the failing tests**

```java
@Test
void validatorUsesGovernanceTypeForUnknownProfile() {
    DocumentContent content =
            new DocumentContent(
                    "Spec",
                    "application/pdf",
                    "word ".repeat(20_000),
                    List.of(),
                    null,
                    Map.of(
                            "contentProfile", "document.pdf.tika",
                            "governanceType", "DOCUMENT_BINARY"));

    assertThatThrownBy(() -> validator.validate(content))
            .isInstanceOf(ParsedContentTooLargeException.class)
            .hasMessageContaining("DOCUMENT_BINARY");
}

@Test
void extractorUsesGovernanceTypeForSourceLimitSelection() {
    assertThat(result.errorMessage()).contains("max=");
    assertThat(result.errorMessage()).doesNotContain("Unsupported content profile");
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q -pl memind-core test -Dtest=ParsedContentLimitValidatorTest,MemoryExtractorMultimodalFileTest`

Expected: FAIL because source limit and parsed-content limit still route on raw `contentProfile`

- [ ] **Step 3: Write minimal implementation**

```java
private long resolveSourceLimit(ContentGovernanceType governanceType) {
    return switch (governanceType) {
        case DOCUMENT_TEXT_LIKE -> rawDataExtractionOptions.document().textLikeSourceLimit().maxBytes();
        case DOCUMENT_BINARY -> rawDataExtractionOptions.document().binarySourceLimit().maxBytes();
        case IMAGE_CAPTION_OCR -> rawDataExtractionOptions.image().sourceLimit().maxBytes();
        case AUDIO_TRANSCRIPT -> rawDataExtractionOptions.audio().sourceLimit().maxBytes();
    };
}
```

```java
private ParsedContentLimitOptions resolveLimits(ContentGovernanceType governanceType) {
    return switch (governanceType) {
        case DOCUMENT_TEXT_LIKE -> options.document().textLikeParsedLimit();
        case DOCUMENT_BINARY -> options.document().binaryParsedLimit();
        case IMAGE_CAPTION_OCR -> options.image().parsedLimit();
        case AUDIO_TRANSCRIPT -> options.audio().parsedLimit();
    };
}
```

```java
ContentGovernanceType governanceType =
        ContentGovernanceResolver.resolveRequired(normalizedMetadata);
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q -pl memind-core test -Dtest=ParsedContentLimitValidatorTest,MemoryExtractorMultimodalFileTest`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/MemoryExtractor.java memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/ParsedContentLimitValidator.java memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/ParsedContentLimitValidatorTest.java memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/MemoryExtractorMultimodalFileTest.java
git commit -m "refactor: route multimodal governance by closed types"
```

### Task 5: Keep Structure-Aware Optimizations And Prompt-Budget Safety On Open Profiles

**Files:**
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/processor/DocumentContentProcessor.java`
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/chunk/ProfileAwareDocumentChunker.java`
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/item/SegmentBudgetEnforcer.java`
- Test: `memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/rawdata/processor/DocumentContentProcessorTest.java`
- Test: `memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/item/SegmentBudgetEnforcerTest.java`

- [ ] **Step 1: Write the failing tests**

```java
@Test
void markdownProfileStillUsesHeadingAwareChunkingWithinTextLikeGovernance() {
    DocumentContent content =
            new DocumentContent(
                    "Guide",
                    "text/markdown",
                    "# A\nbody\n\n# B\nbody",
                    List.of(),
                    null,
                    Map.of(
                            "contentProfile", "document.markdown",
                            "governanceType", "DOCUMENT_TEXT_LIKE"));

    List<Segment> segments = processor.chunk(content).block();

    assertThat(segments).hasSizeGreaterThan(1);
}

@Test
void unknownProfileFallsBackToGovernanceFamilyChunking() {
    DocumentContent content =
            new DocumentContent(
                    "Guide",
                    "application/pdf",
                    "para1\n\npara2\n\npara3",
                    List.of(),
                    null,
                    Map.of(
                            "contentProfile", "document.pdf.tika",
                            "governanceType", "DOCUMENT_BINARY"));

    List<Segment> segments = processor.chunk(content).block();

    assertThat(segments).isNotEmpty();
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q -pl memind-core test -Dtest=DocumentContentProcessorTest,SegmentBudgetEnforcerTest`

Expected: FAIL because document chunking still conflates governance-family selection with profile-specific optimization

- [ ] **Step 3: Write minimal implementation**

```java
ContentGovernanceType governanceType =
        ContentGovernanceResolver.resolveRequired(content.metadata());
String contentProfile = String.valueOf(content.metadata().getOrDefault("contentProfile", ""));
List<Segment> segments =
        profileAwareDocumentChunker.chunk(
                content.toContentString(), options, governanceType, contentProfile);
```

```java
public List<Segment> chunk(
        String text,
        DocumentExtractionOptions options,
        ContentGovernanceType governanceType,
        String contentProfile) {
    TokenChunkingOptions chunkingOptions =
            governanceType == ContentGovernanceType.DOCUMENT_BINARY
                    ? options.binaryChunking()
                    : options.textLikeChunking();
    List<Segment> candidates =
            BuiltinContentProfiles.DOCUMENT_MARKDOWN.equals(contentProfile)
                    ? tokenAwareSegmentAssembler.markdownCandidates(text)
                    : tokenAwareSegmentAssembler.paragraphCandidates(text);
    return tokenAwareSegmentAssembler.assemble(candidates, chunkingOptions);
}
```

```java
private List<Segment> structuredCandidates(Segment base) {
    String profile = String.valueOf(base.metadata().getOrDefault("contentProfile", "")).trim();
    return BuiltinContentProfiles.DOCUMENT_MARKDOWN.equals(profile)
            ? tokenAwareSegmentAssembler.markdownCandidates(base.content(), base.metadata())
            : tokenAwareSegmentAssembler.paragraphCandidates(base.content(), base.metadata());
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q -pl memind-core test -Dtest=DocumentContentProcessorTest,SegmentBudgetEnforcerTest`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/processor/DocumentContentProcessor.java memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/chunk/ProfileAwareDocumentChunker.java memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/item/SegmentBudgetEnforcer.java memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/rawdata/processor/DocumentContentProcessorTest.java memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/item/SegmentBudgetEnforcerTest.java
git commit -m "refactor: preserve structure-aware chunking on content profiles"
```

### Task 6: Run Compatibility Verification And Cleanup

**Files:**
- Modify: `memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/MemoryExtractorMultimodalFileTest.java`
- Modify: `memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/ExtractionRequestTest.java`
- Modify: `memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/ParsedContentLimitValidatorTest.java`
- Modify: `memind-core/src/test/java/com/openmemind/ai/memory/core/resource/ContentParserRegistryTest.java`

- [ ] **Step 1: Add compatibility tests**

```java
@Test
void legacyBuiltinProfileOnlyMetadataStillResolvesGovernanceType() {
    DocumentContent content =
            new DocumentContent(
                    "Legacy",
                    "text/markdown",
                    "# Title",
                    List.of(),
                    null,
                    Map.of("contentProfile", "document.markdown"));

    assertThatCode(() -> validator.validate(content)).doesNotThrowAnyException();
}

@Test
void legacyUnknownProfileWithoutGovernanceTypeFailsFast() {
    DocumentContent content =
            new DocumentContent(
                    "Legacy",
                    "application/pdf",
                    "body",
                    List.of(),
                    null,
                    Map.of("contentProfile", "document.pdf.tika"));

    assertThatThrownBy(() -> validator.validate(content))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("governanceType");
}
```

- [ ] **Step 2: Run focused regression suite**

Run: `mvn -q -pl memind-core test -Dtest=ContentGovernanceResolverTest,ContentParserRegistryTest,NativeTextDocumentContentParserTest,ExtractionRequestTest,ParsedContentLimitValidatorTest,DocumentContentProcessorTest,SegmentBudgetEnforcerTest,MemoryExtractorMultimodalFileTest`

Expected: PASS

- [ ] **Step 3: Run plugin regression suite**

Run: `mvn -q -pl memind-plugins/memind-plugin-content-parser-document-tika test -Dtest=TikaDocumentContentParserTest`

Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/MemoryExtractorMultimodalFileTest.java memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/ExtractionRequestTest.java memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/ParsedContentLimitValidatorTest.java memind-core/src/test/java/com/openmemind/ai/memory/core/resource/ContentParserRegistryTest.java
git commit -m "test: cover multimodal governance compatibility"
```

## Self-Review

- Spec coverage: The plan covers governance model introduction, parser contract tightening, authoritative metadata normalization, core routing migration, profile-aware optimization retention, and compatibility verification.
- Placeholder scan: No `TODO` / `TBD` / vague “handle later” steps remain.
- Type consistency: `ContentGovernanceType` is the only core routing key; `contentProfile` stays open and descriptive; parser-backed governance authority comes from `ParserResolution`, parser-backed final profile may be refined by parsed metadata within the same governance family, direct authority comes from the normalizer, and legacy fallback is restricted to builtin profiles only.
