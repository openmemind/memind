/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.openmemind.ai.memory.plugin.rawdata.audio.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.builder.ItemExtractionOptions;
import com.openmemind.ai.memory.core.builder.ParsedContentLimitOptions;
import com.openmemind.ai.memory.core.builder.RawDataExtractionOptions;
import com.openmemind.ai.memory.core.builder.SourceLimitOptions;
import com.openmemind.ai.memory.core.builder.TokenChunkingOptions;
import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.MemoryResource;
import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.data.enums.MemoryItemType;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.extraction.DefaultMemoryExtractor;
import com.openmemind.ai.memory.core.extraction.ExtractionRequest;
import com.openmemind.ai.memory.core.extraction.item.ItemExtractionConfig;
import com.openmemind.ai.memory.core.extraction.rawdata.ParsedSegment;
import com.openmemind.ai.memory.core.extraction.rawdata.RawContentProcessorRegistry;
import com.openmemind.ai.memory.core.extraction.rawdata.RawDataLayer;
import com.openmemind.ai.memory.core.extraction.result.InsightResult;
import com.openmemind.ai.memory.core.extraction.result.MemoryItemResult;
import com.openmemind.ai.memory.core.extraction.result.RawDataResult;
import com.openmemind.ai.memory.core.extraction.step.MemoryItemExtractStep;
import com.openmemind.ai.memory.core.plugin.RawDataIngestionPolicyRegistry;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.store.insight.InMemoryInsightOperations;
import com.openmemind.ai.memory.core.store.item.InMemoryItemOperations;
import com.openmemind.ai.memory.core.store.rawdata.InMemoryRawDataOperations;
import com.openmemind.ai.memory.core.store.resource.InMemoryResourceOperations;
import com.openmemind.ai.memory.core.utils.TokenUtils;
import com.openmemind.ai.memory.core.vector.MemoryVector;
import com.openmemind.ai.memory.core.vector.VectorSearchResult;
import com.openmemind.ai.memory.plugin.rawdata.audio.caption.AudioCaptionGenerator;
import com.openmemind.ai.memory.plugin.rawdata.audio.chunk.TranscriptSegmentChunker;
import com.openmemind.ai.memory.plugin.rawdata.audio.config.AudioExtractionOptions;
import com.openmemind.ai.memory.plugin.rawdata.audio.content.AudioContent;
import com.openmemind.ai.memory.plugin.rawdata.audio.content.audio.TranscriptSegment;
import com.openmemind.ai.memory.plugin.rawdata.audio.processor.AudioContentProcessor;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class AudioExtractionPipelineIntegrationTest {

    @Test
    void wholeTranscriptPersistenceStillFeedsMemoryItemsWithTranscriptText() {
        MemoryId memoryId = DefaultMemoryId.of("user-1", "agent-1");
        MemoryStore store = recordingStore();
        var memoryItemStep = new RecordingMemoryItemStep("rollback code is R-17");
        var extractor =
                extractor(
                        store,
                        memoryItemStep,
                        new AudioExtractionOptions(
                                new SourceLimitOptions(1024),
                                new ParsedContentLimitOptions(
                                        4096, null, null, Duration.ofMinutes(30)),
                                400,
                                new TokenChunkingOptions(128, 160)));
        String transcript = "intro ".repeat(60) + "rollback code is R-17";
        var content =
                new AudioContent(
                        "audio/mpeg",
                        transcript,
                        List.of(),
                        "file:///tmp/briefing.mp3",
                        Map.of("contentProfile", "audio.transcript"));

        var result =
                extractor.extract(ExtractionRequest.of(memoryId, content).withoutInsight()).block();

        assertThat(result).isNotNull();
        assertThat(result.isSuccess())
                .withFailMessage("status=%s error=%s", result.status(), result.errorMessage())
                .isTrue();
        assertThat(result.memoryItemResult().newItems())
                .singleElement()
                .extracting(MemoryItem::content)
                .isEqualTo("rollback code is R-17");
        assertThat(store.rawDataOperations().listRawData(memoryId)).hasSize(1);
        assertThat(store.rawDataOperations().listRawData(memoryId))
                .extracting(rawData -> rawData.segment().content())
                .containsExactly(transcript);
        assertThat(store.resourceOperations().listResources(memoryId))
                .singleElement()
                .extracting(MemoryResource::sourceUri)
                .isEqualTo("file:///tmp/briefing.mp3");
        assertThat(memoryItemStep.lastRawDataResult().segments())
                .extracting(ParsedSegment::text)
                .singleElement()
                .satisfies(text -> assertThat(text).contains("rollback code is R-17"));
        assertThat(memoryItemStep.lastRawDataResult().segments())
                .extracting(ParsedSegment::caption)
                .singleElement()
                .satisfies(caption -> assertThat(caption).doesNotContain("rollback code is R-17"));
    }

    @Test
    void overBudgetTranscriptPersistsMultipleRawDataEntries() {
        MemoryId memoryId = DefaultMemoryId.of("user-1", "agent-1");
        MemoryStore store = recordingStore();
        var extractor =
                extractor(
                        store,
                        new RecordingMemoryItemStep(),
                        new AudioExtractionOptions(
                                new SourceLimitOptions(1024),
                                new ParsedContentLimitOptions(
                                        4096, null, null, Duration.ofMinutes(30)),
                                20,
                                new TokenChunkingOptions(8, 12)));
        var transcript = "word ".repeat(40).trim();
        var content =
                new AudioContent(
                        "audio/mpeg",
                        transcript,
                        List.of(
                                new TranscriptSegment(
                                        "word ".repeat(10).trim(),
                                        Duration.ZERO,
                                        Duration.ofSeconds(10),
                                        "Alice"),
                                new TranscriptSegment(
                                        "word ".repeat(10).trim(),
                                        Duration.ofSeconds(10),
                                        Duration.ofSeconds(20),
                                        "Alice"),
                                new TranscriptSegment(
                                        "word ".repeat(10).trim(),
                                        Duration.ofSeconds(20),
                                        Duration.ofSeconds(30),
                                        "Alice"),
                                new TranscriptSegment(
                                        "word ".repeat(10).trim(),
                                        Duration.ofSeconds(30),
                                        Duration.ofSeconds(40),
                                        "Alice")),
                        "file:///tmp/rollout.mp3",
                        Map.of("contentProfile", "audio.transcript"));

        var result =
                extractor.extract(ExtractionRequest.of(memoryId, content).withoutInsight()).block();

        assertThat(result).isNotNull();
        assertThat(result.isSuccess())
                .withFailMessage("status=%s error=%s", result.status(), result.errorMessage())
                .isTrue();
        assertThat(store.rawDataOperations().listRawData(memoryId)).hasSizeGreaterThan(1);
        assertThat(store.resourceOperations().listResources(memoryId)).hasSize(1);
        assertThat(store.rawDataOperations().listRawData(memoryId))
                .allSatisfy(
                        rawData ->
                                assertThat(TokenUtils.countTokens(rawData.segment().content()))
                                        .isLessThanOrEqualTo(12));
        assertThat(store.rawDataOperations().listRawData(memoryId))
                .extracting(rawData -> rawData.resourceId())
                .doesNotContainNull()
                .hasSizeGreaterThan(1)
                .allSatisfy(resourceId -> assertThat(resourceId).isNotBlank());
    }

    private static DefaultMemoryExtractor extractor(
            MemoryStore store,
            RecordingMemoryItemStep memoryItemStep,
            AudioExtractionOptions options) {
        var processor = new AudioContentProcessor(new TranscriptSegmentChunker(), options);
        var rawDataLayer =
                new RawDataLayer(
                        List.of(processor),
                        new AudioCaptionGenerator(),
                        store,
                        new StubMemoryVector(),
                        16);
        return new DefaultMemoryExtractor(
                rawDataLayer,
                memoryItemStep,
                (memoryId, memoryItemResult) -> Mono.just(InsightResult.empty()),
                null,
                null,
                null,
                null,
                new RawContentProcessorRegistry(List.of(processor)),
                null,
                null,
                null,
                RawDataIngestionPolicyRegistry.empty(),
                RawDataExtractionOptions.defaults(),
                ItemExtractionOptions.defaults());
    }

    private static MemoryStore recordingStore() {
        return MemoryStore.of(
                new InMemoryRawDataOperations(),
                new InMemoryItemOperations(),
                new InMemoryInsightOperations(),
                new InMemoryResourceOperations(),
                null);
    }

    private static final class RecordingMemoryItemStep implements MemoryItemExtractStep {

        private final String requiredFact;
        private RawDataResult lastRawDataResult;
        private long nextId = 1L;

        private RecordingMemoryItemStep() {
            this(null);
        }

        private RecordingMemoryItemStep(String requiredFact) {
            this.requiredFact = requiredFact;
        }

        @Override
        public Mono<MemoryItemResult> extract(
                MemoryId memoryId, RawDataResult rawDataResult, ItemExtractionConfig config) {
            this.lastRawDataResult = rawDataResult;
            if (requiredFact == null) {
                return Mono.just(MemoryItemResult.empty());
            }

            String transcriptOnlyFact =
                    rawDataResult.segments().stream()
                            .map(ParsedSegment::text)
                            .filter(text -> text != null && text.contains(requiredFact))
                            .findFirst()
                            .map(ignored -> requiredFact)
                            .orElseThrow(
                                    () ->
                                            new IllegalStateException(
                                                    "expected transcript-only fact was not"
                                                            + " available to memory-item"
                                                            + " extraction"));
            String rawDataId =
                    rawDataResult.rawDataList().isEmpty()
                            ? null
                            : rawDataResult.rawDataList().getFirst().id();
            return Mono.just(
                    new MemoryItemResult(
                            List.of(
                                    new MemoryItem(
                                            nextId++,
                                            memoryId.toIdentifier(),
                                            transcriptOnlyFact,
                                            MemoryScope.USER,
                                            MemoryCategory.EVENT,
                                            AudioContent.TYPE,
                                            null,
                                            rawDataId,
                                            "hash-" + transcriptOnlyFact,
                                            Instant.parse("2026-04-14T00:00:00Z"),
                                            Instant.parse("2026-04-14T00:00:00Z"),
                                            Map.of("source", "audio-transcript-test"),
                                            Instant.parse("2026-04-14T00:00:01Z"),
                                            MemoryItemType.FACT)),
                            List.of()));
        }

        private RawDataResult lastRawDataResult() {
            return lastRawDataResult;
        }
    }

    private static final class StubMemoryVector implements MemoryVector {

        @Override
        public Mono<String> store(MemoryId memoryId, String text, Map<String, Object> metadata) {
            return Mono.just("vec-0");
        }

        @Override
        public Mono<List<String>> storeBatch(
                MemoryId memoryId, List<String> texts, List<Map<String, Object>> metadataList) {
            return Mono.just(IntStream.range(0, texts.size()).mapToObj(i -> "vec-" + i).toList());
        }

        @Override
        public Mono<Void> delete(MemoryId memoryId, String vectorId) {
            return Mono.empty();
        }

        @Override
        public Mono<Void> deleteBatch(MemoryId memoryId, List<String> vectorIds) {
            return Mono.empty();
        }

        @Override
        public Flux<VectorSearchResult> search(MemoryId memoryId, String query, int topK) {
            return Flux.empty();
        }

        @Override
        public Flux<VectorSearchResult> search(
                MemoryId memoryId, String query, int topK, Map<String, Object> filter) {
            return Flux.empty();
        }

        @Override
        public Mono<List<Float>> embed(String text) {
            return Mono.just(List.of());
        }

        @Override
        public Mono<List<List<Float>>> embedAll(List<String> texts) {
            return Mono.just(List.of());
        }
    }
}
