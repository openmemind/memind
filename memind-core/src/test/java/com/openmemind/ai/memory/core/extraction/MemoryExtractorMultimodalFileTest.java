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
package com.openmemind.ai.memory.core.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmemind.ai.memory.core.data.ContentTypes;
import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.MemoryRawData;
import com.openmemind.ai.memory.core.extraction.rawdata.ParsedSegment;
import com.openmemind.ai.memory.core.extraction.rawdata.content.DocumentContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.Segment;
import com.openmemind.ai.memory.core.extraction.result.InsightResult;
import com.openmemind.ai.memory.core.extraction.result.MemoryItemResult;
import com.openmemind.ai.memory.core.extraction.result.RawDataResult;
import com.openmemind.ai.memory.core.extraction.step.RawDataExtractStep;
import com.openmemind.ai.memory.core.resource.ContentParserRegistry;
import com.openmemind.ai.memory.core.resource.FetchedResource;
import com.openmemind.ai.memory.core.resource.ResourceFetcher;
import com.openmemind.ai.memory.core.resource.ResourceRef;
import com.openmemind.ai.memory.core.resource.ResourceStore;
import com.openmemind.ai.memory.core.resource.UnsupportedContentSourceException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class MemoryExtractorMultimodalFileTest {

    @Test
    void fileRequestShouldParseAndStoreBytesBeforeRunningRawDataExtraction() {
        var parserRegistry = new RecordingRegistry();
        var resourceStore = new RecordingResourceStore();
        var rawDataStep = new RecordingRawDataStep(false);
        var extractor =
                new MemoryExtractor(
                        rawDataStep,
                        (memoryId, rawDataResult, config) -> Mono.just(MemoryItemResult.empty()),
                        (memoryId, memoryItemResult) -> Mono.just(InsightResult.empty()),
                        null,
                        null,
                        null,
                        null,
                        parserRegistry,
                        resourceStore,
                        null);

        var result =
                extractor
                        .extract(
                                ExtractionRequest.file(
                                        DefaultMemoryId.of("user-1", "agent-1"),
                                        "report.pdf",
                                        new byte[] {1, 2, 3},
                                        "application/pdf"))
                        .block();

        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
        assertThat(parserRegistry.calls).containsExactly("report.pdf:application/pdf:3");
        assertThat(resourceStore.storedRefs).hasSize(1);
        assertThat(rawDataStep.lastContent).isInstanceOf(DocumentContent.class);
        assertThat(rawDataStep.lastContentType).isEqualTo(ContentTypes.DOCUMENT);
        assertThat(rawDataStep.lastMetadata)
                .containsEntry("fileName", "report.pdf")
                .containsEntry("mimeType", "application/pdf")
                .containsEntry("resourceId", "stored-res-1")
                .containsEntry("storageUri", "file:///stored/report.pdf")
                .containsEntry("sizeBytes", 3L)
                .containsEntry("author", "Alice");
    }

    @Test
    void fileRequestShouldFailFastWhenParserRegistryIsMissing() {
        var extractor =
                new MemoryExtractor(
                        (memoryId, content, contentType, metadata) ->
                                Mono.just(RawDataResult.empty()),
                        (memoryId, rawDataResult, config) -> Mono.just(MemoryItemResult.empty()),
                        (memoryId, memoryItemResult) -> Mono.just(InsightResult.empty()));

        var result =
                extractor
                        .extract(
                                ExtractionRequest.file(
                                        DefaultMemoryId.of("user-1", "agent-1"),
                                        "report.pdf",
                                        new byte[] {1, 2, 3},
                                        "application/pdf"))
                        .block();

        assertThat(result).isNotNull();
        assertThat(result.isFailed()).isTrue();
        assertThat(result.errorMessage()).contains("ContentParserRegistry");
    }

    @Test
    void fileRequestShouldFailWhenRegistryRejectsSource() {
        var parserRegistry = new UnsupportedRegistry();
        var extractor =
                new MemoryExtractor(
                        (memoryId, content, contentType, metadata) ->
                                Mono.just(RawDataResult.empty()),
                        (memoryId, rawDataResult, config) -> Mono.just(MemoryItemResult.empty()),
                        (memoryId, memoryItemResult) -> Mono.just(InsightResult.empty()),
                        null,
                        null,
                        null,
                        null,
                        parserRegistry,
                        null,
                        null);

        var result =
                extractor
                        .extract(
                                ExtractionRequest.file(
                                        DefaultMemoryId.of("user-1", "agent-1"),
                                        "image.png",
                                        new byte[] {1, 2, 3},
                                        "image/png"))
                        .block();

        assertThat(result).isNotNull();
        assertThat(result.isFailed()).isTrue();
        assertThat(result.errorMessage()).contains("Unsupported source");
        assertThat(parserRegistry.calls).containsExactly("image.png:image/png:3");
    }

    @Test
    void rawDataFailureShouldBestEffortDeleteStoredBytes() {
        var parserRegistry = new RecordingRegistry();
        var resourceStore = new RecordingResourceStore();
        var extractor =
                new MemoryExtractor(
                        (memoryId, content, contentType, metadata) ->
                                Mono.error(new IllegalStateException("rawdata failed")),
                        (memoryId, rawDataResult, config) -> Mono.just(MemoryItemResult.empty()),
                        (memoryId, memoryItemResult) -> Mono.just(InsightResult.empty()),
                        null,
                        null,
                        null,
                        null,
                        parserRegistry,
                        resourceStore,
                        null);

        var result =
                extractor
                        .extract(
                                ExtractionRequest.file(
                                        DefaultMemoryId.of("user-1", "agent-1"),
                                        "report.pdf",
                                        new byte[] {1, 2, 3},
                                        "application/pdf"))
                        .block();

        assertThat(result).isNotNull();
        assertThat(result.isFailed()).isTrue();
        assertThat(resourceStore.deletedRefs).hasSize(1);
    }

    @Test
    void itemFailureAfterRawDataSuccessShouldNotDeleteStoredBytes() {
        var parserRegistry = new RecordingRegistry();
        var resourceStore = new RecordingResourceStore();
        var rawDataStep = new RecordingRawDataStep(false);
        var extractor =
                new MemoryExtractor(
                        rawDataStep,
                        (memoryId, rawDataResult, config) ->
                                Mono.error(new IllegalStateException("item failed")),
                        (memoryId, memoryItemResult) -> Mono.just(InsightResult.empty()),
                        null,
                        null,
                        null,
                        null,
                        parserRegistry,
                        resourceStore,
                        null);

        var result =
                extractor
                        .extract(
                                ExtractionRequest.file(
                                        DefaultMemoryId.of("user-1", "agent-1"),
                                        "report.pdf",
                                        new byte[] {1, 2, 3},
                                        "application/pdf"))
                        .block();

        assertThat(result).isNotNull();
        assertThat(result.isFailed()).isTrue();
        assertThat(resourceStore.deletedRefs).isEmpty();
    }

    @Test
    void urlRequestShouldFetchParseAndStoreBytesBeforeRunningRawDataExtraction() {
        var fetcher = new RecordingResourceFetcher();
        var parserRegistry = new RecordingRegistry();
        var resourceStore = new RecordingResourceStore();
        var rawDataStep = new RecordingRawDataStep(false);
        var extractor =
                new MemoryExtractor(
                        rawDataStep,
                        (memoryId, rawDataResult, config) -> Mono.just(MemoryItemResult.empty()),
                        (memoryId, memoryItemResult) -> Mono.just(InsightResult.empty()),
                        null,
                        null,
                        null,
                        null,
                        parserRegistry,
                        resourceStore,
                        fetcher);

        var result =
                extractor
                        .extract(
                                ExtractionRequest.url(
                                        DefaultMemoryId.of("user-1", "agent-1"),
                                        "https://example.com/report.pdf"))
                        .block();

        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
        assertThat(fetcher.calls).containsExactly("https://example.com/report.pdf");
        assertThat(parserRegistry.calls).containsExactly("report.pdf:application/pdf:3");
        assertThat(resourceStore.storedRefs).hasSize(1);
        assertThat(rawDataStep.lastContent).isInstanceOf(DocumentContent.class);
        assertThat(rawDataStep.lastContentType).isEqualTo(ContentTypes.DOCUMENT);
        assertThat(rawDataStep.lastMetadata)
                .containsEntry("fileName", "report.pdf")
                .containsEntry("mimeType", "application/pdf")
                .containsEntry("sourceUri", "https://example.com/report.pdf")
                .containsEntry("resourceId", "stored-res-1")
                .containsEntry("storageUri", "file:///stored/report.pdf");
    }

    @Test
    void urlRequestShouldFailFastWhenFetcherIsMissing() {
        var parserRegistry = new RecordingRegistry();
        var extractorWithRegistry =
                new MemoryExtractor(
                        (memoryId, content, contentType, metadata) ->
                                Mono.just(RawDataResult.empty()),
                        (memoryId, rawDataResult, config) -> Mono.just(MemoryItemResult.empty()),
                        (memoryId, memoryItemResult) -> Mono.just(InsightResult.empty()),
                        null,
                        null,
                        null,
                        null,
                        parserRegistry,
                        null,
                        null);

        var result =
                extractorWithRegistry
                        .extract(
                                ExtractionRequest.url(
                                        DefaultMemoryId.of("user-1", "agent-1"),
                                        "https://example.com/report.pdf"))
                        .block();

        assertThat(result).isNotNull();
        assertThat(result.isFailed()).isTrue();
        assertThat(result.errorMessage()).contains("ResourceFetcher");
    }

    @Test
    void urlRequestShouldFailBeforeDownloadWhenParserRegistryIsMissing() {
        var fetcher = new RecordingResourceFetcher();
        var extractor =
                new MemoryExtractor(
                        (memoryId, content, contentType, metadata) ->
                                Mono.just(RawDataResult.empty()),
                        (memoryId, rawDataResult, config) -> Mono.just(MemoryItemResult.empty()),
                        (memoryId, memoryItemResult) -> Mono.just(InsightResult.empty()),
                        null,
                        null,
                        null,
                        null,
                        (ContentParserRegistry) null,
                        null,
                        fetcher);

        var result =
                extractor
                        .extract(
                                ExtractionRequest.url(
                                        DefaultMemoryId.of("user-1", "agent-1"),
                                        "https://example.com/report.pdf"))
                        .block();

        assertThat(result).isNotNull();
        assertThat(result.isFailed()).isTrue();
        assertThat(result.errorMessage()).contains("ContentParserRegistry");
        assertThat(fetcher.calls).isEmpty();
    }

    @Test
    void urlRequestShouldFailFastWhenUrlSchemeIsUnsupported() {
        assertThatThrownBy(
                        () ->
                                ExtractionRequest.url(
                                        DefaultMemoryId.of("user-1", "agent-1"),
                                        "ftp://example.com/report.pdf"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("http/https");
    }

    @Test
    void urlRequestShouldSurfaceFetcherFailure() {
        var extractor =
                new MemoryExtractor(
                        (memoryId, content, contentType, metadata) ->
                                Mono.just(RawDataResult.empty()),
                        (memoryId, rawDataResult, config) -> Mono.just(MemoryItemResult.empty()),
                        (memoryId, memoryItemResult) -> Mono.just(InsightResult.empty()),
                        null,
                        null,
                        null,
                        null,
                        new RecordingRegistry(),
                        null,
                        (memoryId, sourceUrl, fileName, mimeType) ->
                                Mono.error(new IllegalStateException("download failed")));

        var result =
                extractor
                        .extract(
                                ExtractionRequest.url(
                                        DefaultMemoryId.of("user-1", "agent-1"),
                                        "https://example.com/report.pdf"))
                        .block();

        assertThat(result).isNotNull();
        assertThat(result.isFailed()).isTrue();
        assertThat(result.errorMessage()).contains("download failed");
    }

    @Test
    void requestWithoutContentFileOrUrlShouldFailFastDuringResolution() {
        var extractor =
                new MemoryExtractor(
                        (memoryId, content, contentType, metadata) ->
                                Mono.just(RawDataResult.empty()),
                        (memoryId, rawDataResult, config) -> Mono.just(MemoryItemResult.empty()),
                        (memoryId, memoryItemResult) -> Mono.just(InsightResult.empty()));

        var result =
                extractor
                        .extract(
                                new ExtractionRequest(
                                        DefaultMemoryId.of("user-1", "agent-1"),
                                        null,
                                        null,
                                        null,
                                        ContentTypes.CONVERSATION,
                                        Map.of(),
                                        ExtractionConfig.defaults()))
                        .block();

        assertThat(result).isNotNull();
        assertThat(result.isFailed()).isTrue();
        assertThat(result.errorMessage()).contains("content, fileInput, or urlInput is required");
    }

    private static final class RecordingRegistry implements ContentParserRegistry {

        private final List<String> calls = new ArrayList<>();

        @Override
        public Mono<RawContent> parse(byte[] data, String fileName, String mimeType) {
            calls.add(fileName + ":" + mimeType + ":" + data.length);
            return Mono.just(
                    new DocumentContent(
                            "Report",
                            mimeType,
                            "parsed body",
                            List.of(),
                            null,
                            Map.of("author", "Alice")));
        }

        @Override
        public Map<String, Set<String>> supportedMimeTypesByContentType() {
            return Map.of(ContentTypes.DOCUMENT, Set.of("application/pdf"));
        }
    }

    private static final class UnsupportedRegistry implements ContentParserRegistry {

        private final List<String> calls = new ArrayList<>();

        @Override
        public Mono<RawContent> parse(byte[] data, String fileName, String mimeType) {
            calls.add(fileName + ":" + mimeType + ":" + data.length);
            return Mono.error(
                    new UnsupportedContentSourceException(
                            "Unsupported source: fileName=" + fileName + ", mimeType=" + mimeType));
        }

        @Override
        public Map<String, Set<String>> supportedMimeTypesByContentType() {
            return Map.of(ContentTypes.DOCUMENT, Set.of("application/pdf"));
        }
    }

    private static final class RecordingResourceFetcher implements ResourceFetcher {

        private final List<String> calls = new ArrayList<>();

        @Override
        public Mono<FetchedResource> fetch(
                com.openmemind.ai.memory.core.data.MemoryId memoryId,
                String sourceUrl,
                String fileName,
                String mimeType) {
            calls.add(sourceUrl);
            return Mono.just(
                    new FetchedResource(
                            sourceUrl,
                            fileName == null ? "report.pdf" : fileName,
                            new byte[] {1, 2, 3},
                            mimeType == null ? "application/pdf" : mimeType));
        }
    }

    private static final class RecordingResourceStore implements ResourceStore {

        private final List<ResourceRef> storedRefs = new ArrayList<>();
        private final List<ResourceRef> deletedRefs = new ArrayList<>();

        @Override
        public Mono<ResourceRef> store(
                com.openmemind.ai.memory.core.data.MemoryId memoryId,
                String fileName,
                byte[] data,
                String mimeType,
                Map<String, Object> metadata) {
            var ref =
                    new ResourceRef(
                            "stored-res-1",
                            memoryId.toIdentifier(),
                            fileName,
                            mimeType,
                            "file:///stored/" + fileName,
                            data.length,
                            Instant.parse("2026-04-09T00:00:00Z"));
            storedRefs.add(ref);
            return Mono.just(ref);
        }

        @Override
        public Mono<byte[]> retrieve(ResourceRef ref) {
            return Mono.empty();
        }

        @Override
        public Mono<Void> delete(ResourceRef ref) {
            deletedRefs.add(ref);
            return Mono.empty();
        }

        @Override
        public Mono<Boolean> exists(ResourceRef ref) {
            return Mono.just(storedRefs.contains(ref));
        }
    }

    private static final class RecordingRawDataStep implements RawDataExtractStep {

        private final boolean fail;
        private com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent lastContent;
        private String lastContentType;
        private Map<String, Object> lastMetadata;

        private RecordingRawDataStep(boolean fail) {
            this.fail = fail;
        }

        @Override
        public Mono<RawDataResult> extract(
                com.openmemind.ai.memory.core.data.MemoryId memoryId,
                com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent content,
                String contentType,
                Map<String, Object> metadata) {
            return extract(memoryId, content, contentType, metadata, null);
        }

        @Override
        public Mono<RawDataResult> extract(
                com.openmemind.ai.memory.core.data.MemoryId memoryId,
                com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent content,
                String contentType,
                Map<String, Object> metadata,
                String language) {
            lastContent = content;
            lastContentType = contentType;
            lastMetadata = metadata;
            if (fail) {
                return Mono.error(new IllegalStateException("rawdata failed"));
            }
            return Mono.just(
                    new RawDataResult(
                            List.of(
                                    new MemoryRawData(
                                            "raw-1",
                                            memoryId.toIdentifier(),
                                            contentType,
                                            "content-1",
                                            Segment.single("parsed body"),
                                            "caption",
                                            null,
                                            metadata,
                                            null,
                                            null,
                                            Instant.parse("2026-04-09T00:00:00Z"),
                                            Instant.parse("2026-04-09T00:00:00Z"),
                                            Instant.parse("2026-04-09T00:00:01Z"))),
                            List.of(
                                    new ParsedSegment(
                                            "parsed body", "caption", 0, 11, "raw-1", metadata)),
                            false));
        }
    }
}
