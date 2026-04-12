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

import com.openmemind.ai.memory.core.builder.SourceLimitOptions;
import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.enums.ContentGovernanceType;
import com.openmemind.ai.memory.core.extraction.rawdata.RawContentProcessorRegistry;
import com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent;
import com.openmemind.ai.memory.core.plugin.RawDataIngestionPolicy;
import com.openmemind.ai.memory.core.plugin.RawDataIngestionPolicyRegistry;
import com.openmemind.ai.memory.core.resource.ContentCapability;
import com.openmemind.ai.memory.core.resource.ContentParser;
import com.openmemind.ai.memory.core.resource.ContentParserRegistry;
import com.openmemind.ai.memory.core.resource.FetchSession;
import com.openmemind.ai.memory.core.resource.FetchedResource;
import com.openmemind.ai.memory.core.resource.ParserResolution;
import com.openmemind.ai.memory.core.resource.ResourceFetchRequest;
import com.openmemind.ai.memory.core.resource.ResourceFetcher;
import com.openmemind.ai.memory.core.resource.ResourceRef;
import com.openmemind.ai.memory.core.resource.ResourceStore;
import com.openmemind.ai.memory.core.resource.SourceDescriptor;
import com.openmemind.ai.memory.core.resource.SourceKind;
import com.openmemind.ai.memory.core.support.TestDocumentContent;
import com.openmemind.ai.memory.core.support.TestDocumentProcessor;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class DefaultExtractionRequestResolverTest {

    @Test
    void resolveDirectShouldNormalizeGovernedContentMetadata() {
        var resolver =
                new DefaultExtractionRequestResolver(
                        documentProcessorRegistry(),
                        null,
                        null,
                        null,
                        RawDataIngestionPolicyRegistry.empty());

        var content =
                new TestDocumentContent(
                        "Report",
                        "application/pdf",
                        "Document body",
                        "direct://doc-1",
                        ContentGovernanceType.DOCUMENT_BINARY,
                        "document.pdf.tika",
                        Map.of());

        var resolved =
                resolver.resolve(ExtractionRequest.of(DefaultMemoryId.of("u1", "a1"), content))
                        .block();

        assertThat(resolved).isNotNull();
        assertThat(resolved.content()).isInstanceOf(TestDocumentContent.class);
        assertThat(resolved.metadata())
                .containsEntry("sourceKind", "DIRECT")
                .containsEntry("sourceUri", "direct://doc-1")
                .containsEntry("mimeType", "application/pdf")
                .containsEntry("parserId", "direct")
                .containsEntry("governanceType", ContentGovernanceType.DOCUMENT_BINARY.name());
    }

    @Test
    void resolveFileShouldParseStoreAndBuildCleanupRef() {
        var parserRegistry = new RecordingRegistry();
        var resourceStore = new RecordingResourceStore();
        var resolver =
                new DefaultExtractionRequestResolver(
                        documentProcessorRegistry(),
                        parserRegistry,
                        resourceStore,
                        null,
                        defaultDocumentIngestionPolicyRegistry());

        var resolved =
                resolver.resolve(
                                ExtractionRequest.file(
                                        DefaultMemoryId.of("u1", "a1"),
                                        "report.pdf",
                                        new byte[] {1, 2, 3},
                                        "application/pdf"))
                        .block();

        assertThat(resolved).isNotNull();
        assertThat(parserRegistry.calls).containsExactly("report.pdf:application/pdf:3");
        assertThat(resolved.metadata())
                .containsEntry("fileName", "report.pdf")
                .containsEntry("mimeType", "application/pdf")
                .containsEntry("resourceId", "stored-res-1")
                .containsEntry("storageUri", "file:///stored/report.pdf")
                .containsEntry("author", "Alice");
        assertThat(resolved.cleanupRef()).isEqualTo(resourceStore.storedRefs.getFirst());
    }

    @Test
    void resolveUrlShouldFetchParseStoreAndBuildCleanupRef() {
        var parserRegistry = new RecordingRegistry();
        var resourceStore = new RecordingResourceStore();
        var resourceFetcher = new RecordingResourceFetcher();
        var resolver =
                new DefaultExtractionRequestResolver(
                        documentProcessorRegistry(),
                        parserRegistry,
                        resourceStore,
                        resourceFetcher,
                        defaultDocumentIngestionPolicyRegistry());

        var resolved =
                resolver.resolve(
                                ExtractionRequest.url(
                                        DefaultMemoryId.of("u1", "a1"),
                                        "https://example.com/report.pdf"))
                        .block();

        assertThat(resolved).isNotNull();
        assertThat(resourceFetcher.requests)
                .singleElement()
                .satisfies(
                        request -> {
                            assertThat(request.sourceUrl())
                                    .isEqualTo("https://example.com/report.pdf");
                            assertThat(request.requestedFileName()).isNull();
                        });
        assertThat(parserRegistry.calls).contains("report.pdf:application/pdf:3");
        assertThat(resolved.metadata())
                .containsEntry("sourceKind", SourceKind.URL.name())
                .containsEntry("sourceUri", "https://cdn.example.com/report.pdf")
                .containsEntry("resourceId", "stored-res-1")
                .containsEntry("storageUri", "file:///stored/report.pdf");
        assertThat(resolved.cleanupRef()).isEqualTo(resourceStore.storedRefs.getFirst());
    }

    private RawContentProcessorRegistry documentProcessorRegistry() {
        return new RawContentProcessorRegistry(List.of(new TestDocumentProcessor(false)));
    }

    private RawDataIngestionPolicyRegistry defaultDocumentIngestionPolicyRegistry() {
        return new RawDataIngestionPolicyRegistry(
                List.of(
                        new RawDataIngestionPolicy(
                                TestDocumentContent.TYPE,
                                Set.of(ContentGovernanceType.DOCUMENT_BINARY),
                                new SourceLimitOptions(1024 * 1024))));
    }

    private static final class RecordingRegistry implements ContentParserRegistry {

        private final List<String> calls = new ArrayList<>();

        @Override
        public Mono<ParserResolution> resolve(SourceDescriptor source) {
            calls.add(
                    "%s:%s:%s"
                            .formatted(
                                    source.fileName(),
                                    source.mimeType(),
                                    source.sizeBytes() == null ? "null" : source.sizeBytes()));
            return Mono.just(
                    new ParserResolution(
                            new TestDocumentParser(),
                            new ContentCapability(
                                    "document-test",
                                    TestDocumentContent.TYPE,
                                    "document.binary",
                                    ContentGovernanceType.DOCUMENT_BINARY,
                                    Set.of("application/pdf"),
                                    Set.of(".pdf"),
                                    0)));
        }

        @Override
        public Mono<RawContent> parse(byte[] data, SourceDescriptor source) {
            return Mono.just(
                    new TestDocumentContent(
                            "Parsed %s".formatted(source.fileName()),
                            source.mimeType(),
                            "Parsed %s".formatted(source.fileName()),
                            source.sourceUrl(),
                            ContentGovernanceType.DOCUMENT_BINARY,
                            "document.binary",
                            Map.of("author", "Alice")));
        }

        @Override
        public List<ContentCapability> capabilities() {
            return List.of();
        }
    }

    private static final class TestDocumentParser implements ContentParser {

        @Override
        public String parserId() {
            return "document-test";
        }

        @Override
        public String contentType() {
            return TestDocumentContent.TYPE;
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
        public Set<String> supportedExtensions() {
            return Set.of(".pdf");
        }

        @Override
        public Mono<RawContent> parse(byte[] data, SourceDescriptor source) {
            return Mono.error(new UnsupportedOperationException("unused in test"));
        }
    }

    private static final class RecordingResourceStore implements ResourceStore {

        private final List<ResourceRef> storedRefs = new ArrayList<>();

        @Override
        public Mono<ResourceRef> store(
                com.openmemind.ai.memory.core.data.MemoryId memoryId,
                String fileName,
                byte[] data,
                String mimeType,
                Map<String, Object> metadata) {
            var ref =
                    new ResourceRef(
                            "stored-res-%d".formatted(storedRefs.size() + 1),
                            memoryId.toIdentifier(),
                            fileName,
                            mimeType,
                            "file:///stored/%s".formatted(fileName),
                            data.length,
                            Instant.parse("2026-04-12T00:00:00Z"));
            storedRefs.add(ref);
            return Mono.just(ref);
        }

        @Override
        public Mono<byte[]> retrieve(ResourceRef ref) {
            return Mono.error(new UnsupportedOperationException("unused in test"));
        }

        @Override
        public Mono<Boolean> exists(ResourceRef ref) {
            return Mono.error(new UnsupportedOperationException("unused in test"));
        }

        @Override
        public Mono<Void> delete(ResourceRef ref) {
            return Mono.empty();
        }
    }

    private static final class RecordingResourceFetcher implements ResourceFetcher {

        private final List<ResourceFetchRequest> requests = new ArrayList<>();

        @Override
        public Mono<FetchSession> open(ResourceFetchRequest request) {
            requests.add(request);
            return Mono.just(new TestFetchSession(request.sourceUrl()));
        }
    }

    private static final class TestFetchSession implements FetchSession {

        private final String sourceUrl;

        private TestFetchSession(String sourceUrl) {
            this.sourceUrl = sourceUrl;
        }

        @Override
        public String sourceUrl() {
            return sourceUrl;
        }

        @Override
        public String finalUrl() {
            return "https://cdn.example.com/report.pdf";
        }

        @Override
        public int statusCode() {
            return 200;
        }

        @Override
        public Map<String, List<String>> responseHeaders() {
            return Map.of("Content-Type", List.of("application/pdf"));
        }

        @Override
        public String resolvedFileName() {
            return "report.pdf";
        }

        @Override
        public String resolvedMimeType() {
            return "application/pdf";
        }

        @Override
        public Long declaredContentLength() {
            return 3L;
        }

        @Override
        public Mono<FetchedResource> readBody(long maxBytes) {
            return Mono.just(
                    new FetchedResource(
                            sourceUrl,
                            finalUrl(),
                            resolvedFileName(),
                            new byte[] {1, 2, 3},
                            resolvedMimeType(),
                            3));
        }
    }
}
