# Multimodal RawData Quality Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the verified correctness, consistency, and safety gaps in the current multimodal RawData pipeline without rewriting the architecture or expanding into unscoped video/VLM/ASR work.

**Architecture:** Keep the existing `normalize -> chunk -> caption -> vectorize -> persist` pipeline, but harden the weak links in order. Phase 1 fixes deterministic correctness in transcript chunking and RawDataLayer persistence; Phase 2 upgrades image/audio caption generation from generic truncation to content-aware deterministic summaries; Phase 3 hardens native HTML parsing and leaves heavyweight image/audio file parsing as plugin work behind the existing `ContentParser` boundary.

**Tech Stack:** Java 21, Reactor, SLF4J, JUnit 5, Mockito, Maven multi-module

---

## Scope

### In scope for this plan

- Transcript chunking correctness:
  - boundary fallback must not silently reuse `searchFrom`
  - oversized transcript slices must recompute `startTime` / `endTime`
  - multi-speaker merged chunks must preserve speaker identity
- Image OCR chunking policy:
  - remove hidden `targetTokens / 2` merge rule
  - expose the OCR-inline threshold in options
- RawDataLayer consistency:
  - caption generator lookup must follow the same inheritance-aware processor resolution as chunking
  - empty caption strings must not be embedded as `""`
  - vector writes must be cleaned up on persistence failure
  - vector batch size must be bounded, explicit, and test-covered
- Image/audio caption generation:
  - stop falling back to generic `TruncateCaptionGenerator`
  - use content-aware deterministic captioners in core
- Native HTML parsing:
  - remove regex-based stripping from the native text parser
  - add real coverage for HTML / CSV / malformed content paths

### Explicitly out of scope for this patch set

- Video `RawContent` support
- Heavyweight image OCR and audio ASR execution inside `memind-core`
- Cross-system distributed transactions between SQL and vector stores
- Semantic image captioning via multimodal LLMs

### Deferred follow-up after this plan lands

- Image file parser plugin(s) on top of `ContentParserRegistry`
- Audio file parser plugin(s) on top of `ContentParserRegistry`
- Document parser plugin expansion beyond the existing Tika plugin

The current branch already has the right extension seam for those follow-ups:

- `memind-core/src/main/java/com/openmemind/ai/memory/core/resource/ContentParser.java`
- `memind-plugins/memind-plugin-content-parser-document-tika`

This patch set should harden the existing core behavior first, then plugin work can build on a stable base.

---

## File Structure

### Core correctness and consistency files

- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/chunk/TranscriptSegmentChunker.java`
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/chunk/ImageSegmentComposer.java`
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/RawDataLayer.java`
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/builder/ImageExtractionOptions.java`
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/builder/RawDataExtractionOptions.java`
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/builder/MemoryExtractionAssembler.java`

### New focused helpers

- Create: `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/caption/ImageCaptionGenerator.java`
- Create: `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/caption/AudioCaptionGenerator.java`
- Create: `memind-core/src/main/java/com/openmemind/ai/memory/core/resource/HtmlTextExtractor.java`

### Parser and processor updates

- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/processor/ImageContentProcessor.java`
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/processor/AudioContentProcessor.java`
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/resource/NativeTextDocumentContentParser.java`

### Tests

- Create: `memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/rawdata/chunk/TranscriptSegmentChunkerTest.java`
- Create: `memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/rawdata/chunk/ImageSegmentComposerTest.java`
- Modify: `memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/rawdata/processor/AudioContentProcessorTest.java`
- Modify: `memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/rawdata/processor/ImageContentProcessorTest.java`
- Modify: `memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/rawdata/RawDataLayerProcessorTest.java`
- Modify: `memind-core/src/test/java/com/openmemind/ai/memory/core/resource/NativeTextDocumentContentParserTest.java`
- Modify: `memind-core/src/test/java/com/openmemind/ai/memory/core/builder/MemoryAssemblersTest.java`
- Modify: `memind-core/src/test/java/com/openmemind/ai/memory/core/builder/MemoryBuildOptionsTest.java`
- Modify: `memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/ParsedContentLimitValidatorTest.java`
- Modify: `memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/MemoryExtractorMultimodalFileTest.java`

---

## Design Decisions

### 1. Transcript boundary fallback must be explicit, monotonic, and observable

Do not leave `TranscriptSegmentChunker` with silent `indexOf(...)= -1 -> start = searchFrom`.

Implement a three-stage resolution strategy:

1. Exact match from `searchFrom`
2. Normalized-whitespace match from `searchFrom`
3. Sequential approximate fallback only if both fail

Represent the result as an explicit match kind:

- `EXACT`
- `NORMALIZED`
- `APPROXIMATE`

Requirements:

- normalized-whitespace matching must return the real character span in the original transcript, not `normalizedStart + text.length()`
- only `APPROXIMATE` may set `boundaryApproximate = true`
- only `APPROXIMATE` should emit a warning log
- `NORMALIZED` is a supported recovery path, not an error path

Approximate fallback must:

- keep boundaries monotonic
- never move backward
- mark the segment metadata with `boundaryApproximate = true`
- log a warning with a short transcript excerpt and the segment text

### 2. Oversized transcript timing must be recomputed per child

Do not let `TokenAwareSegmentAssembler.splitOversized(...)` decide final audio timing metadata.

`TranscriptSegmentChunker` should:

- build the atomic segment for each transcript record
- if the atomic segment is oversized, split it
- recompute each child segment's `startTime` / `endTime` proportionally from the child char span inside the parent span
- keep `speaker` for single-speaker children

### 3. Mixed speakers should preserve identity without lying

Merged transcript chunks must not emit a fake single `speaker`.

Use:

- `speaker = "<name>"` only when the merged chunk is truly single-speaker
- `speakers = ["Alice", "Bob"]` when multiple non-blank speakers exist in encounter order

Do not emit both keys for the same merged chunk.

### 4. OCR merge policy must be configuration, not hidden arithmetic

Replace `targetTokens / 2` with an explicit `captionOcrMergeMaxTokens` field on `ImageExtractionOptions`.

Default value:

- `400` tokens

Reasoning:

- it matches the current default behavior (`800 / 2`) without keeping the rule implicit
- it lets downstream users tune image OCR behavior without changing chunk target size

### 5. RawData vectorization must be deterministic and recoverable

RawDataLayer should vectorize by the first non-blank text source:

1. `segment.caption()`
2. `segment.content()`
3. otherwise skip vectorization for that segment

Also:

- add `vectorBatchSize` to `RawDataExtractionOptions`
- default `vectorBatchSize = 64`
- batch `storeBatch(...)` calls sequentially
- if any `storeBatch(...)` call fails after earlier batches succeeded, delete the already-written vectors before rethrowing
- if SQL persistence throws after vectors were written, run best-effort `vector.deleteBatch(...)`
- if cleanup also fails, attach the cleanup exception as suppressed and rethrow the original persistence failure

### 6. Core image/audio captioning should be content-aware but dependency-free

Do not add LLM-backed image/audio caption generation inside `memind-core`.

Instead:

- `ImageCaptionGenerator` should prefer human/image description text and only fall back to OCR excerpts when description is absent
- `AudioCaptionGenerator` should prefer speaker-aware transcript excerpts and include speaker labels when present

This removes the current generic truncation behavior while keeping the core deterministic and cheap.

### 7. Native HTML parsing should be linear, not regex-based

Do not keep the current `replaceAll("(?is)<script.*?</script>", ...)` approach.

Implement a small `HtmlTextExtractor` that performs a single pass over the string and:

- skips tag bodies
- skips `<script>` and `<style>` contents
- preserves visible text
- turns common block-level tags such as `<p>`, `<div>`, `<li>`, `<br>` into line boundaries
- decodes common entities such as `&amp;`, `&lt;`, `&gt;`, `&quot;`, `&#39;`, `&nbsp;`
- normalizes line breaks and collapses repeated whitespace after extraction

This avoids regex backtracking risk without adding heavyweight parser dependencies to `memind-core`.

---

## Tasks

### Task 1: Fix transcript chunker correctness

**Files:**
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/chunk/TranscriptSegmentChunker.java`
- Create: `memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/rawdata/chunk/TranscriptSegmentChunkerTest.java`
- Modify: `memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/rawdata/processor/AudioContentProcessorTest.java`

- [ ] **Step 1: Write failing chunker tests for the three verified transcript bugs**

```java
@Test
void normalizedWhitespaceMatchReturnsOriginalBoundaryWithoutApproximateFlag() {
    var transcript = "Hello   world\nNext line";
    var content =
            new AudioContent(
                    "audio/mpeg",
                    transcript,
                    List.of(new TranscriptSegment("Hello world", Duration.ZERO, Duration.ofSeconds(2), "Alice")),
                    null,
                    Map.of());

    var segments = new TranscriptSegmentChunker().chunk(content, AudioExtractionOptions.defaults());

    assertThat(segments).hasSize(1);
    assertThat(segments.getFirst().metadata()).doesNotContainKey("boundaryApproximate");
    assertThat(((CharBoundary) segments.getFirst().boundary()).startChar()).isZero();
    assertThat(((CharBoundary) segments.getFirst().boundary()).endChar()).isEqualTo("Hello   world".length());
}

@Test
void approximateFallbackMarksBoundaryAndStaysMonotonic() {
    var transcript = "Alpha-beta";
    var content =
            new AudioContent(
                    "audio/mpeg",
                    transcript,
                    List.of(new TranscriptSegment("Alpha beta", Duration.ZERO, Duration.ofSeconds(2), "Alice")),
                    null,
                    Map.of());

    var segments = new TranscriptSegmentChunker().chunk(content, AudioExtractionOptions.defaults());

    assertThat(segments).hasSize(1);
    assertThat(segments.getFirst().metadata()).containsEntry("boundaryApproximate", true);
    assertThat(((CharBoundary) segments.getFirst().boundary()).startChar()).isZero();
}

@Test
void oversizedTranscriptChildrenReceiveProportionalTimeBounds() {
    String longText = "word ".repeat(AudioExtractionOptions.defaults().chunking().hardMaxTokens() * 2);
    var content =
            new AudioContent(
                    "audio/mpeg",
                    longText,
                    List.of(new TranscriptSegment(longText, Duration.ZERO, Duration.ofMinutes(1), "Alice")),
                    null,
                    Map.of());

    var segments = new TranscriptSegmentChunker().chunk(content, AudioExtractionOptions.defaults());

    assertThat(segments).hasSizeGreaterThan(1);
    assertThat(segments.get(0).metadata().get("startTime")).isEqualTo(Duration.ZERO);
    assertThat(segments.get(0).metadata().get("endTime")).isNotEqualTo(Duration.ofMinutes(1));
    assertThat(segments.getLast().metadata().get("endTime")).isEqualTo(Duration.ofMinutes(1));
}

@Test
void mergedTranscriptChunkKeepsSpeakerListWhenSpeakersDiffer() {
    var content =
            new AudioContent(
                    "audio/mpeg",
                    "hello\nworld",
                    List.of(
                            new TranscriptSegment("hello", Duration.ZERO, Duration.ofSeconds(1), "Alice"),
                            new TranscriptSegment("world", Duration.ofSeconds(1), Duration.ofSeconds(2), "Bob")),
                    null,
                    Map.of());

    var segments = new TranscriptSegmentChunker().chunk(content, AudioExtractionOptions.defaults());

    assertThat(segments).hasSize(1);
    assertThat(segments.getFirst().metadata())
            .doesNotContainKey("speaker")
            .containsEntry("speakers", List.of("Alice", "Bob"));
}
```

- [ ] **Step 2: Run the focused tests and verify they fail on current code**

Run: `mvn -pl memind-core -Dtest=TranscriptSegmentChunkerTest,AudioContentProcessorTest test`

Expected:

- normalized-whitespace boundary assertion fails because current code computes `endChar` from normalized text length
- approximate fallback assertion fails because current code does not distinguish match quality
- oversized split timing assertion fails because child segments inherit the parent time range
- multi-speaker metadata assertion fails because the merged chunk drops speaker identity

- [ ] **Step 3: Implement explicit boundary resolution, proportional timing, and speaker-list preservation**

```java
private enum MatchKind {
    EXACT,
    NORMALIZED,
    APPROXIMATE
}

private record ResolvedBoundary(CharBoundary boundary, MatchKind matchKind) {}

private record NormalizedText(String text, List<Integer> originalIndices) {

    int toNormalizedIndex(int originalIndex) {
        for (int i = 0; i < originalIndices.size(); i++) {
            if (originalIndices.get(i) >= originalIndex) {
                return i;
            }
        }
        return text.length();
    }

    int toOriginalIndex(int normalizedIndex) {
        return originalIndices.get(normalizedIndex);
    }
}

private ResolvedBoundary resolveBoundary(String transcript, String text, int searchFrom) {
    int exact = transcript.indexOf(text, searchFrom);
    if (exact >= 0) {
        return new ResolvedBoundary(new CharBoundary(exact, exact + text.length()), MatchKind.EXACT);
    }

    CharBoundary normalized = findNormalizedWhitespaceBoundary(transcript, text, searchFrom);
    if (normalized != null) {
        return new ResolvedBoundary(normalized, MatchKind.NORMALIZED);
    }

    int start = Math.max(0, searchFrom);
    int end = Math.min(transcript.length(), start + text.length());
    return new ResolvedBoundary(new CharBoundary(start, end), MatchKind.APPROXIMATE);
}

private CharBoundary findNormalizedWhitespaceBoundary(String transcript, String text, int searchFrom) {
    var normalizedTranscript = normalizeWhitespaceWithIndexMap(transcript);
    String normalizedText = collapseWhitespace(text.trim());
    int normalizedSearchFrom = normalizedTranscript.toNormalizedIndex(searchFrom);
    int normalizedStart = normalizedTranscript.text().indexOf(normalizedText, normalizedSearchFrom);
    if (normalizedStart < 0) {
        return null;
    }
    int normalizedEnd = normalizedStart + normalizedText.length() - 1;
    int originalStart = normalizedTranscript.toOriginalIndex(normalizedStart);
    int originalEndExclusive = normalizedTranscript.toOriginalIndex(normalizedEnd) + 1;
    return new CharBoundary(originalStart, originalEndExclusive);
}

private NormalizedText normalizeWhitespaceWithIndexMap(String source) {
    StringBuilder normalized = new StringBuilder();
    List<Integer> indices = new ArrayList<>();
    boolean previousWhitespace = false;
    for (int i = 0; i < source.length(); i++) {
        char ch = source.charAt(i);
        if (Character.isWhitespace(ch)) {
            if (!previousWhitespace) {
                normalized.append(' ');
                indices.add(i);
                previousWhitespace = true;
            }
        } else {
            normalized.append(ch);
            indices.add(i);
            previousWhitespace = false;
        }
    }
    return new NormalizedText(normalized.toString().trim(), indices);
}

private String collapseWhitespace(String value) {
    StringBuilder collapsed = new StringBuilder();
    boolean previousWhitespace = false;
    for (int i = 0; i < value.length(); i++) {
        char ch = value.charAt(i);
        if (Character.isWhitespace(ch)) {
            if (!previousWhitespace) {
                collapsed.append(' ');
                previousWhitespace = true;
            }
        } else {
            collapsed.append(ch);
            previousWhitespace = false;
        }
    }
    return collapsed.toString().trim();
}

private List<Segment> splitOversizedTranscriptSegment(
        TranscriptSegment source, Segment atomic, int hardMaxTokens) {
    return tokenAwareSegmentAssembler.splitOversized(atomic, hardMaxTokens).stream()
            .map(
                    child ->
                            new Segment(
                                    child.content(),
                                    child.caption(),
                                    child.boundary(),
                                    recomputeTimingMetadata(source, atomic, child),
                                    child.runtimeContext()))
            .toList();
}

private Map<String, Object> segmentMetadata(
        TranscriptSegment first, TranscriptSegment last, String singleSpeaker, List<String> speakers, MatchKind matchKind) {
    var metadata = new LinkedHashMap<String, Object>();
    if (first.startTime() != null) {
        metadata.put("startTime", first.startTime());
    }
    if (last.endTime() != null) {
        metadata.put("endTime", last.endTime());
    }
    if (singleSpeaker != null) {
        metadata.put("speaker", singleSpeaker);
    } else if (!speakers.isEmpty()) {
        metadata.put("speakers", speakers);
    }
    if (matchKind == MatchKind.APPROXIMATE) {
        metadata.put("boundaryApproximate", true);
    }
    return metadata.isEmpty() ? Map.of() : Map.copyOf(metadata);
}
```

- [ ] **Step 4: Re-run the focused tests and then the whole audio processor suite**

Run: `mvn -pl memind-core -Dtest=TranscriptSegmentChunkerTest,AudioContentProcessorTest test`

Expected:

- all transcript chunker tests PASS
- existing audio processor tests continue to PASS with the new metadata semantics

- [ ] **Step 5: Commit**

```bash
git add memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/chunk/TranscriptSegmentChunker.java \
        memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/rawdata/chunk/TranscriptSegmentChunkerTest.java \
        memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/rawdata/processor/AudioContentProcessorTest.java
git commit -m "fix: harden transcript chunking semantics"
```

### Task 2: Make image OCR merge policy explicit and testable

**Files:**
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/builder/ImageExtractionOptions.java`
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/chunk/ImageSegmentComposer.java`
- Create: `memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/rawdata/chunk/ImageSegmentComposerTest.java`
- Modify: `memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/rawdata/processor/ImageContentProcessorTest.java`

- [ ] **Step 1: Write failing tests around OCR-inline threshold behavior**

```java
@Test
void smallOcrCanBeMergedIntoCaptionSegmentWhenBelowConfiguredThreshold() {
    var options =
            new ImageExtractionOptions(
                    ImageExtractionOptions.defaults().sourceLimit(),
                    ImageExtractionOptions.defaults().parsedLimit(),
                    ImageExtractionOptions.defaults().chunking(),
                    12);

    var content = new ImageContent("image/png", "Dashboard screenshot", "A B C", null, Map.of());

    assertThat(new ImageSegmentComposer().compose(content, options))
            .singleElement()
            .extracting(Segment::content)
            .isEqualTo("Dashboard screenshot\nA B C");
}

@Test
void negativeOcrMergeThresholdIsRejected() {
    assertThatThrownBy(
                    () ->
                            new ImageExtractionOptions(
                                    ImageExtractionOptions.defaults().sourceLimit(),
                                    ImageExtractionOptions.defaults().parsedLimit(),
                                    ImageExtractionOptions.defaults().chunking(),
                                    -1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("captionOcrMergeMaxTokens");
}

@Test
void largeOcrMovesToDedicatedSegmentsWhenAboveConfiguredThreshold() {
    var options =
            new ImageExtractionOptions(
                    ImageExtractionOptions.defaults().sourceLimit(),
                    ImageExtractionOptions.defaults().parsedLimit(),
                    ImageExtractionOptions.defaults().chunking(),
                    4);

    var content =
            new ImageContent("image/png", "Dashboard screenshot", "word ".repeat(20), null, Map.of());

    assertThat(new ImageSegmentComposer().compose(content, options)).hasSizeGreaterThan(1);
}
```

- [ ] **Step 2: Run the focused image chunking tests and verify current code still behaves implicitly**

Run: `mvn -pl memind-core -Dtest=ImageSegmentComposerTest,ImageContentProcessorTest test`

Expected:

- compile or assertion failure because `ImageExtractionOptions` has no explicit OCR threshold field yet

- [ ] **Step 3: Add the option field and route the composer through it**

```java
public record ImageExtractionOptions(
        SourceLimitOptions sourceLimit,
        ParsedContentLimitOptions parsedLimit,
        TokenChunkingOptions chunking,
        int captionOcrMergeMaxTokens) {

    public ImageExtractionOptions {
        sourceLimit = Objects.requireNonNull(sourceLimit, "sourceLimit");
        parsedLimit = Objects.requireNonNull(parsedLimit, "parsedLimit");
        chunking = Objects.requireNonNull(chunking, "chunking");
        if (captionOcrMergeMaxTokens < 0) {
            throw new IllegalArgumentException("captionOcrMergeMaxTokens must be >= 0");
        }
    }

    public static ImageExtractionOptions defaults() {
        return new ImageExtractionOptions(
                new SourceLimitOptions(10L * 1024 * 1024),
                new ParsedContentLimitOptions(4_000, null, null, null),
                new TokenChunkingOptions(800, 1200),
                400);
    }
}
```

```java
if (!caption.isBlank()
        && !ocrText.isBlank()
        && TokenUtils.countTokens(ocrText) <= options.captionOcrMergeMaxTokens()) {
    String merged = caption + "\n" + ocrText;
    return tokenAwareSegmentAssembler.assemble(
            List.of(new Segment(merged, null, new CharBoundary(0, merged.length()), Map.of("segmentRole", "caption_ocr"))),
            options.chunking());
}
```

- [ ] **Step 4: Re-run the focused image tests**

Run: `mvn -pl memind-core -Dtest=ImageSegmentComposerTest,ImageContentProcessorTest test`

Expected:

- threshold behavior is now explicit and PASSING
- existing processor tests still PASS

- [ ] **Step 5: Commit**

```bash
git add memind-core/src/main/java/com/openmemind/ai/memory/core/builder/ImageExtractionOptions.java \
        memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/chunk/ImageSegmentComposer.java \
        memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/rawdata/chunk/ImageSegmentComposerTest.java \
        memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/rawdata/processor/ImageContentProcessorTest.java
git commit -m "fix: make image OCR merge policy explicit"
```

### Task 3: Harden RawDataLayer caption routing, batching, and cleanup

**Files:**
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/builder/RawDataExtractionOptions.java`
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/builder/MemoryExtractionAssembler.java`
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/RawDataLayer.java`
- Modify: `memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/rawdata/RawDataLayerProcessorTest.java`
- Modify: `memind-core/src/test/java/com/openmemind/ai/memory/core/builder/MemoryAssemblersTest.java`
- Modify: `memind-core/src/test/java/com/openmemind/ai/memory/core/builder/MemoryBuildOptionsTest.java`
- Modify: `memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/ParsedContentLimitValidatorTest.java`
- Modify: `memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/MemoryExtractorMultimodalFileTest.java`

- [ ] **Step 1: Write failing tests for the verified RawDataLayer gaps**

```java
@Test
void subclassUsesCaptionGeneratorViaInheritanceAwareProcessorLookup() {
    var processor = mock(ConversationContentProcessor.class);
    var caption = mock(CaptionGenerator.class);
    when(processor.contentClass()).thenReturn(ConversationContent.class);
    when(processor.chunk(any(ConversationContent.class))).thenReturn(Mono.just(List.of()));
    when(processor.captionGenerator()).thenReturn(caption);

    var layer = new RawDataLayer(List.of(processor), defaultCaption, store, vector, 64);
    var content = new ConversationContent(List.of()) { };

    when(caption.generateForSegments(any(), any())).thenReturn(Mono.just(List.of()));
    when(rawDataOps.getRawDataByContentId(any(), any())).thenReturn(Optional.empty());
    when(vector.storeBatch(any(), any(), any())).thenReturn(Mono.just(List.of()));

    layer.extract(DefaultMemoryId.of("u", "a"), content, "CONVERSATION", Map.of()).block();

    verify(caption).generateForSegments(any(), any());
}

@Test
void blankCaptionFallsBackToSegmentContentForEmbedding() {
    var layer = new RawDataLayer(List.of(), defaultCaption, store, vector, 64);
    var segment = new Segment("actual content", "", new CharBoundary(0, 14), Map.of());

    when(defaultCaption.generateForSegments(any(), any())).thenReturn(Mono.just(List.of(segment)));
    when(rawDataOps.getRawDataByContentId(any(), any())).thenReturn(Optional.empty());
    when(vector.storeBatch(any(), eq(List.of("actual content")), any())).thenReturn(Mono.just(List.of("vec-1")));

    layer.processSegment(DefaultMemoryId.of("u", "a"), segment, "DOCUMENT", "content-1", Map.of()).block();

    verify(vector).storeBatch(any(), eq(List.of("actual content")), any());
}

@Test
void persistenceFailureDeletesWrittenVectors() {
    when(store.rawDataOperations()).thenReturn(rawDataOps);
    doThrow(new IllegalStateException("db down")).when(store).upsertRawDataWithResources(any(), any(), any());
    when(vector.storeBatch(any(), any(), any())).thenReturn(Mono.just(List.of("vec-1", "vec-2")));

    assertThatThrownBy(() -> layer.extract(DefaultMemoryId.of("u", "a"), content, "DOCUMENT", Map.of()).block())
            .hasMessageContaining("db down");

    verify(vector).deleteBatch(any(), eq(List.of("vec-1", "vec-2")));
}

@Test
void partialVectorBatchFailureDeletesAlreadyWrittenVectors() {
    var layer = new RawDataLayer(List.of(), defaultCaption, store, vector, 2);
    var s1 = new Segment("one", "one", new CharBoundary(0, 3), Map.of());
    var s2 = new Segment("two", "two", new CharBoundary(4, 7), Map.of());
    var s3 = new Segment("three", "three", new CharBoundary(8, 13), Map.of());

    when(defaultCaption.generateForSegments(any(), any())).thenReturn(Mono.just(List.of(s1, s2, s3)));
    when(rawDataOps.getRawDataByContentId(any(), any())).thenReturn(Optional.empty());
    when(vector.storeBatch(any(), any(), any()))
            .thenReturn(Mono.just(List.of("vec-1", "vec-2")))
            .thenReturn(Mono.error(new IllegalStateException("embed down")));

    assertThatThrownBy(
                    () ->
                            layer.extract(
                                            DefaultMemoryId.of("u", "a"),
                                            DocumentContent.of("notes", "text/plain", "one\ntwo\nthree"),
                                            "DOCUMENT",
                                            Map.of())
                                    .block())
            .hasMessageContaining("embed down");

    verify(vector).deleteBatch(any(), eq(List.of("vec-1", "vec-2")));
}

@Test
void vectorWritesAreChunkedByConfiguredBatchSize() {
    var layer = new RawDataLayer(List.of(), defaultCaption, store, vector, 2);
    var s1 = new Segment("one", "one", new CharBoundary(0, 3), Map.of());
    var s2 = new Segment("two", "two", new CharBoundary(4, 7), Map.of());
    var s3 = new Segment("three", "three", new CharBoundary(8, 13), Map.of());

    when(defaultCaption.generateForSegments(any(), any())).thenReturn(Mono.just(List.of(s1, s2, s3)));
    when(rawDataOps.getRawDataByContentId(any(), any())).thenReturn(Optional.empty());
    when(vector.storeBatch(any(), any(), any()))
            .thenReturn(Mono.just(List.of("vec-1", "vec-2")))
            .thenReturn(Mono.just(List.of("vec-3")));

    layer.extract(
                    DefaultMemoryId.of("u", "a"),
                    DocumentContent.of("notes", "text/plain", "one\ntwo\nthree"),
                    "DOCUMENT",
                    Map.of())
            .block();

    verify(vector, times(2)).storeBatch(any(), any(), any());
}

@Test
void nonPositiveVectorBatchSizeIsRejected() {
    assertThatThrownBy(
                    () ->
                            new RawDataExtractionOptions(
                                    ConversationChunkingConfig.DEFAULT,
                                    DocumentExtractionOptions.defaults(),
                                    ImageExtractionOptions.defaults(),
                                    AudioExtractionOptions.defaults(),
                                    ToolCallChunkingOptions.defaults(),
                                    CommitDetectorConfig.defaults(),
                                    0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("vectorBatchSize");
}
```

- [ ] **Step 2: Run the RawDataLayer suite and confirm the failures**

Run: `mvn -pl memind-core -Dtest=RawDataLayerProcessorTest,MemoryAssemblersTest,MemoryBuildOptionsTest,ParsedContentLimitValidatorTest,MemoryExtractorMultimodalFileTest test`

Expected:

- constructor/compilation failures until `vectorBatchSize` is added
- caption generator inheritance test fails
- partial vector batch failure cleanup test fails on current behavior
- fallback-to-content and cleanup tests fail on current behavior

- [ ] **Step 3: Add explicit vector batching config and rework RawDataLayer to use it**

```java
public record RawDataExtractionOptions(
        ConversationChunkingConfig conversation,
        DocumentExtractionOptions document,
        ImageExtractionOptions image,
        AudioExtractionOptions audio,
        ToolCallChunkingOptions toolCall,
        CommitDetectorConfig commitDetection,
        int vectorBatchSize) {

    public RawDataExtractionOptions {
        conversation = Objects.requireNonNull(conversation, "conversation");
        document = Objects.requireNonNull(document, "document");
        image = Objects.requireNonNull(image, "image");
        audio = Objects.requireNonNull(audio, "audio");
        toolCall = Objects.requireNonNull(toolCall, "toolCall");
        commitDetection = Objects.requireNonNull(commitDetection, "commitDetection");
        if (vectorBatchSize <= 0) {
            throw new IllegalArgumentException("vectorBatchSize must be > 0");
        }
    }

    public static RawDataExtractionOptions defaults() {
        return new RawDataExtractionOptions(
                ConversationChunkingConfig.DEFAULT,
                DocumentExtractionOptions.defaults(),
                ImageExtractionOptions.defaults(),
                AudioExtractionOptions.defaults(),
                ToolCallChunkingOptions.defaults(),
                CommitDetectorConfig.defaults(),
                64);
    }
}
```

```java
private CaptionGenerator getCaptionGenerator(RawContent content) {
    var processor = getProcessor(content);
    var generator = processor.captionGenerator();
    return generator != null ? generator : defaultCaptionGenerator;
}

private record VectorCandidate(int index, String text, Map<String, Object> metadata) {}

private record VectorBatch(
        List<Integer> indices, List<String> texts, List<Map<String, Object>> metadataList) {}

private List<VectorCandidate> buildVectorCandidates(List<Segment> segments) {
    return IntStream.range(0, segments.size())
            .mapToObj(
                    index -> {
                        Segment segment = segments.get(index);
                        String text =
                                (segment.caption() != null && !segment.caption().isBlank())
                                        ? segment.caption()
                                        : segment.content();
                        if (text == null || text.isBlank()) {
                            return null;
                        }
                        return new VectorCandidate(index, text, Map.of());
                    })
            .filter(Objects::nonNull)
            .toList();
}

private List<VectorBatch> partitionVectorCandidates(
        List<VectorCandidate> candidates, int batchSize) {
    List<VectorBatch> batches = new ArrayList<>();
    for (int start = 0; start < candidates.size(); start += batchSize) {
        int end = Math.min(candidates.size(), start + batchSize);
        List<VectorCandidate> window = candidates.subList(start, end);
        batches.add(
                new VectorBatch(
                        window.stream().map(VectorCandidate::index).toList(),
                        window.stream().map(VectorCandidate::text).toList(),
                        window.stream().map(VectorCandidate::metadata).toList()));
    }
    return batches;
}

private List<String> flattenVectorIds(List<List<String>> batches) {
    return batches.stream().flatMap(List::stream).toList();
}

private List<Segment> attachVectorIds(
        List<Segment> segments, List<VectorCandidate> candidates, List<String> vectorIds) {
    List<Segment> result = new ArrayList<>(segments);
    for (int i = 0; i < candidates.size(); i++) {
        VectorCandidate candidate = candidates.get(i);
        Segment segment = result.get(candidate.index());
        Map<String, Object> metadata = new LinkedHashMap<>(segment.metadata());
        metadata.put("vectorId", vectorIds.get(i));
        result.set(
                candidate.index(),
                new Segment(
                        segment.content(),
                        segment.caption(),
                        segment.boundary(),
                        Map.copyOf(metadata),
                        segment.runtimeContext()));
    }
    return List.copyOf(result);
}

private Mono<List<Segment>> vectorize(MemoryId memoryId, List<Segment> segments) {
    List<VectorCandidate> candidates = buildVectorCandidates(segments);
    if (candidates.isEmpty()) {
        return Mono.just(segments);
    }

    var writtenVectorIds = new ArrayList<String>();
    return Flux.fromIterable(partitionVectorCandidates(candidates, vectorBatchSize))
            .concatMap(
                    batch ->
                            vector.storeBatch(memoryId, batch.texts(), batch.metadataList())
                                    .doOnNext(writtenVectorIds::addAll))
            .collectList()
            .map(this::flattenVectorIds)
            .map(vectorIds -> attachVectorIds(segments, candidates, vectorIds))
            .onErrorResume(error -> cleanupWrittenVectors(memoryId, writtenVectorIds, error));
}

private Mono<List<Segment>> cleanupWrittenVectors(
        MemoryId memoryId, List<String> writtenVectorIds, Throwable error) {
    if (writtenVectorIds.isEmpty()) {
        return Mono.error(error);
    }
    return vector.deleteBatch(memoryId, List.copyOf(writtenVectorIds))
            .then(Mono.error(error))
            .onErrorResume(
                    cleanupError -> {
                        error.addSuppressed(cleanupError);
                        return Mono.error(error);
                    });
}

private Mono<RawDataProcessResult> doProcess(
        RawDataInput input, MemoryId memoryId, String contentHash, String language) {
    return chunkContent(input.content())
            .flatMap(segments -> getCaptionGenerator(input.content()).generateForSegments(segments, language))
            .flatMap(segments -> vectorize(memoryId, segments))
            .flatMap(
                    vectorized ->
                            Mono.fromCallable(() -> buildAndPersist(memoryId, input, contentHash, vectorized))
                                    .onErrorResume(error -> cleanupVectors(memoryId, vectorized, error)));
}

private Mono<RawDataProcessResult> cleanupVectors(
        MemoryId memoryId, List<Segment> vectorized, Throwable error) {
    List<String> vectorIds =
            vectorized.stream()
                    .map(segment -> segment.metadata().get("vectorId"))
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .toList();
    if (vectorIds.isEmpty()) {
        return Mono.error(error);
    }
    return vector.deleteBatch(memoryId, vectorIds)
            .then(Mono.error(error))
            .onErrorResume(
                    cleanupError -> {
                        error.addSuppressed(cleanupError);
                        return Mono.error(error);
                    });
}
```

- [ ] **Step 4: Re-run the focused consistency tests**

Run: `mvn -pl memind-core -Dtest=RawDataLayerProcessorTest,MemoryAssemblersTest,MemoryBuildOptionsTest,ParsedContentLimitValidatorTest,MemoryExtractorMultimodalFileTest test`

Expected:

- inheritance-aware caption lookup PASS
- `vector.storeBatch(...)` never receives `""`
- partial vector batch failure triggers cleanup of previously written vector IDs
- persistence failure triggers `deleteBatch(...)`
- config plumbing compiles and PASSes

- [ ] **Step 5: Commit**

```bash
git add memind-core/src/main/java/com/openmemind/ai/memory/core/builder/RawDataExtractionOptions.java \
        memind-core/src/main/java/com/openmemind/ai/memory/core/builder/MemoryExtractionAssembler.java \
        memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/RawDataLayer.java \
        memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/rawdata/RawDataLayerProcessorTest.java \
        memind-core/src/test/java/com/openmemind/ai/memory/core/builder/MemoryAssemblersTest.java \
        memind-core/src/test/java/com/openmemind/ai/memory/core/builder/MemoryBuildOptionsTest.java \
        memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/ParsedContentLimitValidatorTest.java \
        memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/MemoryExtractorMultimodalFileTest.java
git commit -m "fix: harden rawdata vectorization and cleanup"
```

### Task 4: Replace generic truncation with image/audio-aware caption generators

**Files:**
- Create: `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/caption/ImageCaptionGenerator.java`
- Create: `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/caption/AudioCaptionGenerator.java`
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/processor/ImageContentProcessor.java`
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/processor/AudioContentProcessor.java`
- Modify: `memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/rawdata/processor/ImageContentProcessorTest.java`
- Modify: `memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/rawdata/processor/AudioContentProcessorTest.java`
- Modify: `memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/rawdata/RawDataLayerProcessorTest.java`

- [ ] **Step 1: Write failing tests for content-aware image/audio captions**

```java
@Test
void imageCaptionGeneratorPrefersDescriptionOverMergedOcrBody() {
    var processor = new ImageContentProcessor(new ImageSegmentComposer(), ImageExtractionOptions.defaults());
    var content = new ImageContent("image/png", "Architecture diagram", "CPU 85%", null, Map.of());

    StepVerifier.create(processor.captionGenerator().generate("Architecture diagram\nCPU 85%", Map.of("segmentRole", "caption_ocr")))
            .expectNext("Architecture diagram")
            .verifyComplete();
}

@Test
void audioCaptionGeneratorIncludesSpeakerContext() {
    var processor = new AudioContentProcessor(new TranscriptSegmentChunker(), AudioExtractionOptions.defaults());

    StepVerifier.create(processor.captionGenerator().generate("hello world", Map.of("speaker", "Alice")))
            .expectNext("Transcript - Alice: hello world")
            .verifyComplete();
}
```

- [ ] **Step 2: Run the processor tests and verify current code still falls back to truncation**

Run: `mvn -pl memind-core -Dtest=ImageContentProcessorTest,AudioContentProcessorTest,RawDataLayerProcessorTest test`

Expected:

- new caption assertions fail because both processors still inherit `TruncateCaptionGenerator`

- [ ] **Step 3: Add deterministic content-aware caption generators and wire them through the processors**

```java
public final class ImageCaptionGenerator implements CaptionGenerator {

    @Override
    public Mono<String> generate(String content, Map<String, Object> metadata) {
        String role = (String) metadata.get("segmentRole");
        if ("caption".equals(role) || "caption_ocr".equals(role)) {
            return Mono.just(firstLine(content));
        }
        return Mono.just(prefix("OCR", firstLine(content)));
    }
}
```

```java
public final class AudioCaptionGenerator implements CaptionGenerator {

    @Override
    public Mono<String> generate(String content, Map<String, Object> metadata) {
        String speaker = resolveSpeakerLabel(metadata);
        return Mono.just(speaker == null ? "Transcript: " + excerpt(content) : "Transcript - " + speaker + ": " + excerpt(content));
    }
}
```

```java
@Override
public CaptionGenerator captionGenerator() {
    return new ImageCaptionGenerator();
}
```

- [ ] **Step 4: Re-run the caption-related tests**

Run: `mvn -pl memind-core -Dtest=ImageContentProcessorTest,AudioContentProcessorTest,RawDataLayerProcessorTest test`

Expected:

- image/audio processors no longer use generic truncation
- captions are stable, deterministic, and speaker-aware where metadata exists

- [ ] **Step 5: Commit**

```bash
git add memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/caption/ImageCaptionGenerator.java \
        memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/caption/AudioCaptionGenerator.java \
        memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/processor/ImageContentProcessor.java \
        memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/processor/AudioContentProcessor.java \
        memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/rawdata/processor/ImageContentProcessorTest.java \
        memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/rawdata/processor/AudioContentProcessorTest.java \
        memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/rawdata/RawDataLayerProcessorTest.java
git commit -m "feat: add content-aware image and audio captions"
```

### Task 5: Harden native HTML parsing and close parser coverage gaps

**Files:**
- Create: `memind-core/src/main/java/com/openmemind/ai/memory/core/resource/HtmlTextExtractor.java`
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/resource/NativeTextDocumentContentParser.java`
- Modify: `memind-core/src/test/java/com/openmemind/ai/memory/core/resource/NativeTextDocumentContentParserTest.java`

- [ ] **Step 1: Write failing tests for HTML, CSV, malformed HTML, and empty payload handling**

```java
@Test
void htmlParsingSkipsScriptAndStyleBodies() {
    var parser = new NativeTextDocumentContentParser();

    var content =
            (DocumentContent)
                    parser.parse(
                                    "<html><style>.x{}</style><body>Hello<script>alert(1)</script><b>world</b></body></html>"
                                            .getBytes(StandardCharsets.UTF_8),
                                    new SourceDescriptor(SourceKind.FILE, "index.html", "text/html", 64L, null))
                            .block();

    assertThat(content.toContentString()).isEqualTo("Hello world");
}

@Test
void htmlParsingPreservesBlockBoundariesAndDecodesEntities() {
    var parser = new NativeTextDocumentContentParser();

    var content =
            (DocumentContent)
                    parser.parse(
                                    "<div>Tom &amp; Jerry&nbsp;Co</div><div>Line&nbsp;2</div><ul><li>A</li><li>B</li></ul><br>Tail"
                                            .getBytes(StandardCharsets.UTF_8),
                                    new SourceDescriptor(SourceKind.FILE, "index.html", "text/html", 96L, null))
                            .block();

    assertThat(content.toContentString()).isEqualTo("Tom & Jerry Co\nLine 2\nA\nB\nTail");
}

@Test
void csvParsingRemainsPlainTextAndKeepsDocumentProfile() {
    var parser = new NativeTextDocumentContentParser();

    var content =
            (DocumentContent)
                    parser.parse(
                                    "a,b\n1,2".getBytes(StandardCharsets.UTF_8),
                                    new SourceDescriptor(SourceKind.FILE, "data.csv", "text/csv", 7L, null))
                            .block();

    assertThat(content.metadata()).containsEntry("contentProfile", "document.text");
    assertThat(content.toContentString()).isEqualTo("a,b\n1,2");
}

@Test
void emptyPayloadStillFailsFast() {
    assertThatThrownBy(
                    () ->
                            new NativeTextDocumentContentParser()
                                    .parse(new byte[0], new SourceDescriptor(SourceKind.FILE, "empty.txt", "text/plain", 0L, null))
                                    .block())
            .hasMessageContaining("must not be empty");
}
```

- [ ] **Step 2: Run the parser tests and confirm the current HTML implementation is inadequate**

Run: `mvn -pl memind-core -Dtest=NativeTextDocumentContentParserTest test`

Expected:

- HTML script/style stripping test fails or stays under-specified
- block-boundary and entity-decoding test fails on current regex-based implementation
- coverage is obviously incomplete on current branch

- [ ] **Step 3: Replace regex stripping with a linear HTML extractor**

```java
public final class HtmlTextExtractor {

    private enum State {
        TEXT,
        TAG,
        SCRIPT,
        STYLE
    }

    public String extract(String html) {
        State state = State.TEXT;
        StringBuilder visible = new StringBuilder();
        StringBuilder tag = new StringBuilder();
        for (int i = 0; i < html.length(); i++) {
            char ch = html.charAt(i);
            state = advance(state, ch, tag, visible);
        }
        return decodeEntities(normalizeBlockBreaks(collapseWhitespace(visible.toString())));
    }

    private State advance(State state, char ch, StringBuilder tag, StringBuilder visible) {
        if (state == State.TEXT) {
            if (ch == '<') {
                tag.setLength(0);
                tag.append(ch);
                return State.TAG;
            }
            visible.append(ch);
            return State.TEXT;
        }

        tag.append(ch);
        String rawTag = tag.toString().toLowerCase(Locale.ROOT);
        if (state == State.SCRIPT) {
            return rawTag.endsWith("</script>") ? State.TEXT : State.SCRIPT;
        }
        if (state == State.STYLE) {
            return rawTag.endsWith("</style>") ? State.TEXT : State.STYLE;
        }
        if (ch != '>') {
            return state;
        }
        if (rawTag.startsWith("<script")) {
            tag.setLength(0);
            return State.SCRIPT;
        }
        if (rawTag.startsWith("<style")) {
            tag.setLength(0);
            return State.STYLE;
        }
        if (isBlockBoundaryTag(rawTag) && (visible.isEmpty() || visible.charAt(visible.length() - 1) != '\n')) {
            visible.append('\n');
        }
        tag.setLength(0);
        return State.TEXT;
    }

    private boolean isBlockBoundaryTag(String rawTag) {
        return rawTag.startsWith("<br")
                || rawTag.startsWith("<p")
                || rawTag.startsWith("</p")
                || rawTag.startsWith("<div")
                || rawTag.startsWith("</div")
                || rawTag.startsWith("<li")
                || rawTag.startsWith("</li")
                || rawTag.startsWith("<ul")
                || rawTag.startsWith("</ul")
                || rawTag.startsWith("<ol")
                || rawTag.startsWith("</ol");
    }

    private String normalizeBlockBreaks(String text) {
        return text.replaceAll("\\n{3,}", "\n\n").strip();
    }

    private String decodeEntities(String text) {
        return text.replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
    }
}
```

```java
private static String normalizeText(String rawText, String mimeType) {
    String normalized = rawText.replace("\r\n", "\n").replace('\r', '\n');
    if ("text/html".equals(mimeType)) {
        normalized = new HtmlTextExtractor().extract(normalized);
    }
    return normalized.strip();
}
```

- [ ] **Step 4: Re-run the parser tests**

Run: `mvn -pl memind-core -Dtest=NativeTextDocumentContentParserTest test`

Expected:

- HTML visible text is preserved
- script/style bodies are removed
- block tags become line boundaries
- common entities are decoded
- CSV and plain-text behavior remains unchanged

- [ ] **Step 5: Commit**

```bash
git add memind-core/src/main/java/com/openmemind/ai/memory/core/resource/HtmlTextExtractor.java \
        memind-core/src/main/java/com/openmemind/ai/memory/core/resource/NativeTextDocumentContentParser.java \
        memind-core/src/test/java/com/openmemind/ai/memory/core/resource/NativeTextDocumentContentParserTest.java
git commit -m "fix: harden native html text extraction"
```

### Task 6: Full regression run and review checkpoint

**Files:**
- No new production files
- Review: the files changed in Tasks 1-5

- [ ] **Step 1: Run the focused multimodal/core regression suite**

Run: `mvn -pl memind-core -Dtest=TranscriptSegmentChunkerTest,ImageSegmentComposerTest,AudioContentProcessorTest,ImageContentProcessorTest,RawDataLayerProcessorTest,NativeTextDocumentContentParserTest,MemoryAssemblersTest,MemoryBuildOptionsTest,ParsedContentLimitValidatorTest,MemoryExtractorMultimodalFileTest test`

Expected:

- all targeted multimodal tests PASS
- no constructor-plumbing regressions remain

- [ ] **Step 2: Run the full `memind-core` test suite**

Run: `mvn -pl memind-core test`

Expected:

- full module PASS
- no regressions in existing conversation or tool-call flows

- [ ] **Step 3: Review the final code against the original issue list**

Checklist:

- transcript fallback is explicit and observable
- normalized transcript matching preserves real source spans
- oversized transcript child timing is correct
- OCR merge threshold is configured and documented in code
- image/audio caption generation no longer falls back to generic truncation
- RawDataLayer does not embed `""`
- RawDataLayer cleans up vectors after partial batch failure
- RawDataLayer cleans up vectors on persistence failure
- vector batch size is bounded and explicit
- native HTML parsing no longer uses regex stripping
- native HTML parsing preserves block boundaries and decodes common entities

- [ ] **Step 4: Commit the final integration checkpoint**

```bash
git add memind-core
git commit -m "test: close multimodal rawdata quality gaps"
```

---

## Risks and Guardrails

- Do not change public content model names or `contentProfile` semantics in this patch.
- Do not add image/audio file parser execution to `memind-core`; keep that behind future plugin modules.
- Do not persist both `speaker` and `speakers` for one transcript chunk.
- Do not silently swallow vector cleanup failures; attach them as suppressed exceptions.
- Do not add noisy warning logs for valid empty image/audio inputs; those paths should be test-covered no-ops, not operational noise.
- Do not commit anything under `docs/superpowers/`.

---

## Follow-up Plan Boundary

After this plan is complete, the next dedicated implementation plan should cover parser plugins only:

- image parser module selection
- audio parser module selection
- starter auto-configuration
- integration tests for `ExtractionRequest.file(...)` and URL downloads that produce `ImageContent` / `AudioContent`

That follow-up should be a separate plan because it depends on engine selection and transitive dependency policy, not on the correctness fixes above.
