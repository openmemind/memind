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

import com.openmemind.ai.memory.core.builder.ItemExtractionOptions;
import com.openmemind.ai.memory.core.builder.PromptBudgetOptions;
import com.openmemind.ai.memory.core.builder.RawDataExtractionOptions;
import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryRawData;
import com.openmemind.ai.memory.core.data.enums.ContentGovernanceType;
import com.openmemind.ai.memory.core.extraction.item.ItemExtractionConfig;
import com.openmemind.ai.memory.core.extraction.rawdata.ParsedSegment;
import com.openmemind.ai.memory.core.extraction.rawdata.RawContentProcessor;
import com.openmemind.ai.memory.core.extraction.rawdata.RawContentProcessorRegistry;
import com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.Segment;
import com.openmemind.ai.memory.core.extraction.result.InsightResult;
import com.openmemind.ai.memory.core.extraction.result.MemoryItemResult;
import com.openmemind.ai.memory.core.extraction.result.RawDataResult;
import com.openmemind.ai.memory.core.extraction.step.MemoryItemExtractStep;
import com.openmemind.ai.memory.core.extraction.step.RawDataExtractStep;
import com.openmemind.ai.memory.core.plugin.RawDataIngestionPolicy;
import com.openmemind.ai.memory.core.plugin.RawDataIngestionPolicyRegistry;
import com.openmemind.ai.memory.core.resource.ContentCapability;
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
import com.openmemind.ai.memory.core.resource.SourceTooLargeException;
import com.openmemind.ai.memory.core.resource.UnsupportedContentSourceException;
import com.openmemind.ai.memory.core.support.TestDocumentContent;
import com.openmemind.ai.memory.core.support.TestDocumentProcessor;
import com.openmemind.ai.memory.core.utils.TokenUtils;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class DefaultMemoryExtractorMultimodalFileTest {

    private static final String IMAGE_TYPE = "IMAGE";
    private static final String AUDIO_TYPE = "AUDIO";

    @Test
    void fileRequestShouldParseAndStoreBytesBeforeRunningRawDataExtraction() {
        var parserRegistry = new RecordingRegistry();
        var resourceStore = new RecordingResourceStore();
        var rawDataStep = new RecordingRawDataStep(false);
        var extractor =
                extractor(
                        rawDataStep,
                        (memoryId, rawDataResult, config) -> Mono.just(MemoryItemResult.empty()),
                        (memoryId, memoryItemResult) -> Mono.just(InsightResult.empty()),
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
        assertThat(rawDataStep.lastContent).isInstanceOf(TestDocumentContent.class);
        assertThat(rawDataStep.lastContentType).isEqualTo(TestDocumentContent.TYPE);
        assertThat(rawDataStep.lastMetadata)
                .containsEntry("fileName", "report.pdf")
                .containsEntry("mimeType", "application/pdf")
                .containsEntry("parserId", "document-test")
                .containsEntry("contentProfile", "document.binary")
                .containsEntry("governanceType", ContentGovernanceType.DOCUMENT_BINARY.name())
                .containsEntry("resourceId", "stored-res-1")
                .containsEntry("storageUri", "file:///stored/report.pdf")
                .containsEntry("sizeBytes", 3L)
                .containsEntry("author", "Alice");
    }

    @Test
    void fileRequestShouldSupportCustomCapabilityProfileViaGovernanceType() {
        var parserRegistry = new CustomProfileRegistry();
        var rawDataStep = new RecordingRawDataStep(false);
        var extractor =
                extractor(
                        rawDataStep,
                        (memoryId, rawDataResult, config) -> Mono.just(MemoryItemResult.empty()),
                        (memoryId, memoryItemResult) -> Mono.just(InsightResult.empty()),
                        parserRegistry,
                        null,
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
        assertThat(rawDataStep.lastMetadata)
                .containsEntry("parserId", "document-custom")
                .containsEntry("contentProfile", "document.pdf.tika")
                .containsEntry("governanceType", ContentGovernanceType.DOCUMENT_BINARY.name());
        assertThat(((TestDocumentContent) rawDataStep.lastContent).metadata())
                .containsEntry("parserId", "document-custom")
                .containsEntry("contentProfile", "document.pdf.tika")
                .containsEntry("governanceType", ContentGovernanceType.DOCUMENT_BINARY.name());
    }

    @Test
    void fileRequestShouldRejectParserMetadataGovernanceConflict() {
        var parserRegistry = new ConflictingGovernanceRegistry();
        var extractor =
                extractor(
                        (memoryId, content, contentType, metadata) ->
                                Mono.just(RawDataResult.empty()),
                        (memoryId, rawDataResult, config) -> Mono.just(MemoryItemResult.empty()),
                        (memoryId, memoryItemResult) -> Mono.just(InsightResult.empty()),
                        parserRegistry,
                        null,
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
        assertThat(result.errorMessage()).contains("governanceType");
    }

    @Test
    void fileRequestShouldRejectBuiltinProfileThatConflictsWithCapabilityGovernance() {
        var parserRegistry = new ConflictingBuiltinProfileRegistry();
        var extractor =
                extractor(
                        (memoryId, content, contentType, metadata) ->
                                Mono.just(RawDataResult.empty()),
                        (memoryId, rawDataResult, config) -> Mono.just(MemoryItemResult.empty()),
                        (memoryId, memoryItemResult) -> Mono.just(InsightResult.empty()),
                        parserRegistry,
                        null,
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
        assertThat(result.errorMessage()).contains("contentProfile").contains("document.markdown");
    }

    @Test
    void fileRequestShouldFailFastWhenParserRegistryIsMissing() {
        var extractor =
                extractor(
                        (memoryId, content, contentType, metadata) ->
                                Mono.just(RawDataResult.empty()),
                        (memoryId, rawDataResult, config) -> Mono.just(MemoryItemResult.empty()),
                        (memoryId, memoryItemResult) -> Mono.just(InsightResult.empty()),
                        null,
                        null,
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
        assertThat(result.errorMessage()).contains("ContentParserRegistry");
    }

    @Test
    void fileRequestShouldFailWhenRegistryRejectsSource() {
        var parserRegistry = new UnsupportedRegistry();
        var extractor =
                extractor(
                        (memoryId, content, contentType, metadata) ->
                                Mono.just(RawDataResult.empty()),
                        (memoryId, rawDataResult, config) -> Mono.just(MemoryItemResult.empty()),
                        (memoryId, memoryItemResult) -> Mono.just(InsightResult.empty()),
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
    void fileRequestShouldRejectParsedContentThatExceedsConfiguredLimit() {
        var parserRegistry = new OversizedDocumentRegistry();
        var rawDataStep = new RecordingRawDataStep(false);
        var extractor =
                extractor(
                        rawDataStep,
                        (memoryId, rawDataResult, config) -> Mono.just(MemoryItemResult.empty()),
                        (memoryId, memoryItemResult) -> Mono.just(InsightResult.empty()),
                        documentProcessorRegistry(restrictiveBinaryParsedMaxTokens()),
                        parserRegistry,
                        null,
                        null,
                        new RawDataExtractionOptions(
                                com.openmemind.ai.memory.core.extraction.rawdata.chunk
                                        .ConversationChunkingConfig.DEFAULT,
                                com.openmemind.ai.memory.core.extraction.context
                                        .CommitDetectorConfig.defaults(),
                                64),
                        ItemExtractionOptions.defaults());

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
        assertThat(result.errorMessage()).contains("document.binary");
        assertThat(rawDataStep.lastContent).isNull();
    }

    @Test
    void fileRequestParsesImageViaParserRegistry() {
        var rawDataStep = new RecordingRawDataStep(false);
        var extractor =
                extractor(
                        rawDataStep,
                        (memoryId, rawDataResult, config) -> Mono.just(MemoryItemResult.empty()),
                        (memoryId, memoryItemResult) -> Mono.just(InsightResult.empty()),
                        imageProcessorRegistry(),
                        imageParserRegistry(),
                        null,
                        null,
                        RawDataExtractionOptions.defaults(),
                        ItemExtractionOptions.defaults());

        var result =
                extractor
                        .extract(
                                ExtractionRequest.file(
                                        DefaultMemoryId.of("user-1", "agent-1"),
                                        "chart.png",
                                        new byte[] {1, 2, 3},
                                        "image/png"))
                        .block();

        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
        assertThat(rawDataStep.lastContentType).isEqualTo(IMAGE_TYPE);
        assertThat(rawDataStep.lastMetadata)
                .containsEntry("parserId", "image-vision")
                .containsEntry("contentProfile", "image.caption-ocr");
    }

    @Test
    void fileRequestShouldRejectOversizedSourceUsingPluginIngestionPolicy() {
        var parserRegistry = new RecordingResolutionRegistry();
        var extractor =
                extractor(
                        (memoryId, content, contentType, metadata) ->
                                Mono.just(RawDataResult.empty()),
                        (memoryId, rawDataResult, config) -> Mono.just(MemoryItemResult.empty()),
                        (memoryId, memoryItemResult) -> Mono.just(InsightResult.empty()),
                        documentProcessorRegistry(),
                        parserRegistry,
                        null,
                        null,
                        new RawDataIngestionPolicyRegistry(
                                List.of(
                                        new RawDataIngestionPolicy(
                                                TestDocumentContent.TYPE,
                                                Set.of(ContentGovernanceType.DOCUMENT_BINARY),
                                                new com.openmemind.ai.memory.core.builder
                                                        .SourceLimitOptions(2)))),
                        RawDataExtractionOptions.defaults(),
                        ItemExtractionOptions.defaults());

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
        assertThat(result.errorMessage()).contains("Source exceeds byte limit");
    }

    @Test
    void directDocumentExtractionUsesProcessorValidation() {
        var rawDataStep = new RecordingRawDataStep(false);
        var processorRegistry =
                new RawContentProcessorRegistry(
                        List.of(
                                new TestDocumentProcessor(
                                        false, restrictiveBinaryParsedMaxTokens())));
        var content =
                new TestDocumentContent(
                        "Manual",
                        "application/pdf",
                        "word ".repeat(200),
                        null,
                        null,
                        null,
                        Map.of("contentProfile", "document.binary"));

        var result =
                extractorWithRestrictiveOptions(rawDataStep, processorRegistry)
                        .extract(
                                ExtractionRequest.of(
                                        DefaultMemoryId.of("user-1", "agent-1"), content))
                        .block();

        assertThat(result).isNotNull();
        assertThat(result.isFailed()).isTrue();
        assertThat(result.errorMessage()).contains("document.binary");
        assertThat(rawDataStep.lastContent).isNull();
    }

    @Test
    void rawDataFailureShouldBestEffortDeleteStoredBytes() {
        var parserRegistry = new RecordingRegistry();
        var resourceStore = new RecordingResourceStore();
        var extractor =
                extractor(
                        (memoryId, content, contentType, metadata) ->
                                Mono.error(new IllegalStateException("rawdata failed")),
                        (memoryId, rawDataResult, config) -> Mono.just(MemoryItemResult.empty()),
                        (memoryId, memoryItemResult) -> Mono.just(InsightResult.empty()),
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
                extractor(
                        rawDataStep,
                        (memoryId, rawDataResult, config) ->
                                Mono.error(new IllegalStateException("item failed")),
                        (memoryId, memoryItemResult) -> Mono.just(InsightResult.empty()),
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
                extractor(
                        rawDataStep,
                        (memoryId, rawDataResult, config) -> Mono.just(MemoryItemResult.empty()),
                        (memoryId, memoryItemResult) -> Mono.just(InsightResult.empty()),
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
        assertThat(rawDataStep.lastContent).isInstanceOf(TestDocumentContent.class);
        assertThat(rawDataStep.lastContentType).isEqualTo(TestDocumentContent.TYPE);
        assertThat(rawDataStep.lastMetadata)
                .containsEntry("fileName", "report.pdf")
                .containsEntry("mimeType", "application/pdf")
                .containsEntry("sourceKind", "URL")
                .containsEntry("sourceUri", "https://example.com/report.pdf")
                .containsEntry("parserId", "document-test")
                .containsEntry("contentProfile", "document.binary")
                .containsEntry("governanceType", ContentGovernanceType.DOCUMENT_BINARY.name())
                .containsEntry("resourceId", "stored-res-1")
                .containsEntry("storageUri", "file:///stored/report.pdf");
    }

    @Test
    void urlRequestParsesAudioViaParserRegistry() {
        var rawDataStep = new RecordingRawDataStep(false);
        var extractor =
                extractor(
                        rawDataStep,
                        (memoryId, rawDataResult, config) -> Mono.just(MemoryItemResult.empty()),
                        (memoryId, memoryItemResult) -> Mono.just(InsightResult.empty()),
                        audioProcessorRegistry(),
                        audioParserRegistry(),
                        null,
                        new RecordingFetchSessionFetcher("clip.mp3", "audio/mpeg", 3L),
                        RawDataExtractionOptions.defaults(),
                        ItemExtractionOptions.defaults());

        var result =
                extractor
                        .extract(
                                ExtractionRequest.url(
                                        DefaultMemoryId.of("user-1", "agent-1"),
                                        "https://example.com/clip.mp3"))
                        .block();

        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
        assertThat(rawDataStep.lastContentType).isEqualTo(AUDIO_TYPE);
        assertThat(rawDataStep.lastMetadata)
                .containsEntry("parserId", "audio-transcription")
                .containsEntry("contentProfile", "audio.transcript");
    }

    @Test
    void urlExtractionResolvesBeforeAndAfterHeaders() {
        var registry = new RecordingResolutionRegistry();
        var fetcher = new RecordingFetchSessionFetcher("final.pdf", "application/pdf", 300L);
        var extractor =
                extractor(
                        (memoryId, content, contentType, metadata) ->
                                Mono.just(RawDataResult.empty()),
                        (memoryId, rawDataResult, config) -> Mono.just(MemoryItemResult.empty()),
                        (memoryId, memoryItemResult) -> Mono.just(InsightResult.empty()),
                        registry,
                        null,
                        fetcher);

        var result =
                extractor
                        .extract(
                                ExtractionRequest.url(
                                        DefaultMemoryId.of("user-1", "agent-1"),
                                        "https://example.com/file"))
                        .block();

        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
        assertThat(registry.descriptors()).hasSize(2);
        assertThat(registry.descriptors().get(0).sourceKind()).isEqualTo(SourceKind.URL);
        assertThat(registry.descriptors().get(1).mimeType()).isEqualTo("application/pdf");
    }

    @Test
    void unknownUrlShouldValidateDeclaredLengthAgainstFinalResolvedCapability() {
        var registry = new RecordingResolutionRegistry();
        var fetcher =
                new RecordingFetchSessionFetcher("final.pdf", "application/pdf", 3L * 1024 * 1024);
        var extractor =
                extractor(
                        (memoryId, content, contentType, metadata) ->
                                Mono.just(RawDataResult.empty()),
                        (memoryId, rawDataResult, config) -> Mono.just(MemoryItemResult.empty()),
                        (memoryId, memoryItemResult) -> Mono.just(InsightResult.empty()),
                        documentProcessorRegistry(),
                        registry,
                        null,
                        fetcher,
                        RawDataExtractionOptions.defaults(),
                        ItemExtractionOptions.defaults());

        var result =
                extractor
                        .extract(
                                ExtractionRequest.url(
                                        DefaultMemoryId.of("user-1", "agent-1"),
                                        "https://example.com/file"))
                        .block();

        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
        assertThat(registry.descriptors()).hasSize(2);
        assertThat(registry.descriptors().get(1).mimeType()).isEqualTo("application/pdf");
    }

    @Test
    void urlRequestShouldFailFastWhenFetcherIsMissing() {
        var parserRegistry = new RecordingRegistry();
        var extractorWithRegistry =
                extractor(
                        (memoryId, content, contentType, metadata) ->
                                Mono.just(RawDataResult.empty()),
                        (memoryId, rawDataResult, config) -> Mono.just(MemoryItemResult.empty()),
                        (memoryId, memoryItemResult) -> Mono.just(InsightResult.empty()),
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
                extractor(
                        (memoryId, content, contentType, metadata) ->
                                Mono.just(RawDataResult.empty()),
                        (memoryId, rawDataResult, config) -> Mono.just(MemoryItemResult.empty()),
                        (memoryId, memoryItemResult) -> Mono.just(InsightResult.empty()),
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
                extractor(
                        (memoryId, content, contentType, metadata) ->
                                Mono.just(RawDataResult.empty()),
                        (memoryId, rawDataResult, config) -> Mono.just(MemoryItemResult.empty()),
                        (memoryId, memoryItemResult) -> Mono.just(InsightResult.empty()),
                        new RecordingRegistry(),
                        null,
                        request -> Mono.error(new IllegalStateException("download failed")));

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
    void requestWithoutSourceShouldFailFastOnConstruction() {
        assertThatThrownBy(
                        () ->
                                new ExtractionRequest(
                                        DefaultMemoryId.of("user-1", "agent-1"),
                                        null,
                                        Map.of(),
                                        ExtractionConfig.defaults()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("source");
    }

    @Test
    void extractorAppliesBudgetBeforeItemExtraction() {
        var itemStep = new RecordingMemoryItemStep();
        var extractor =
                extractor(
                        new OversizedRawDataStep(),
                        itemStep,
                        (memoryId, memoryItemResult) -> Mono.just(InsightResult.empty()),
                        documentProcessorRegistry(),
                        null,
                        null,
                        null,
                        RawDataExtractionOptions.defaults(),
                        new ItemExtractionOptions(
                                false, new PromptBudgetOptions(2_048, 400, 400, 200)));

        extractor
                .extract(
                        ExtractionRequest.of(
                                DefaultMemoryId.of("user-1", "agent-1"),
                                TestDocumentContent.of(
                                        "Manual",
                                        "application/pdf",
                                        "# Intro\n"
                                                + "word ".repeat(3_000)
                                                + "\n\n## Usage\n"
                                                + "word ".repeat(3_000))))
                .block();

        assertThat(itemStep.lastRawDataResult()).isNotNull();
        assertThat(itemStep.lastRawDataResult().segments())
                .allSatisfy(
                        segment ->
                                assertThat(TokenUtils.countTokens(segment.text()))
                                        .isLessThanOrEqualTo(1_048));
    }

    private static final class RecordingRegistry implements ContentParserRegistry {

        private final List<String> calls = new ArrayList<>();

        @Override
        public Mono<ParserResolution> resolve(SourceDescriptor source) {
            return Mono.just(
                    new ParserResolution(
                            nullParser(),
                            new ContentCapability(
                                    "document-test",
                                    TestDocumentContent.TYPE,
                                    "document.binary",
                                    Set.of("application/pdf"),
                                    Set.of(".pdf"),
                                    10)));
        }

        @Override
        public Mono<RawContent> parse(byte[] data, SourceDescriptor source) {
            calls.add(source.fileName() + ":" + source.mimeType() + ":" + data.length);
            return Mono.just(
                    new TestDocumentContent(
                            "Report",
                            source.mimeType(),
                            "parsed body",
                            null,
                            null,
                            null,
                            Map.of("author", "Alice")));
        }

        @Override
        public List<ContentCapability> capabilities() {
            return List.of(
                    new ContentCapability(
                            "document-test",
                            TestDocumentContent.TYPE,
                            "document.binary",
                            Set.of("application/pdf"),
                            Set.of(".pdf"),
                            10));
        }
    }

    private static ContentParserRegistry imageParserRegistry() {
        return new ContentParserRegistry() {
            @Override
            public Mono<ParserResolution> resolve(SourceDescriptor source) {
                return Mono.just(
                        new ParserResolution(
                                testImageParser(),
                                new ContentCapability(
                                        "image-vision",
                                        IMAGE_TYPE,
                                        "image.caption-ocr",
                                        Set.of("image/png"),
                                        Set.of(".png"),
                                        50)));
            }

            @Override
            public Mono<RawContent> parse(byte[] data, SourceDescriptor source) {
                return testImageParser().parse(data, source);
            }

            @Override
            public List<ContentCapability> capabilities() {
                return List.of();
            }
        };
    }

    private static ContentParserRegistry audioParserRegistry() {
        return new ContentParserRegistry() {
            @Override
            public Mono<ParserResolution> resolve(SourceDescriptor source) {
                return Mono.just(
                        new ParserResolution(
                                testAudioParser(),
                                new ContentCapability(
                                        "audio-transcription",
                                        AUDIO_TYPE,
                                        "audio.transcript",
                                        Set.of("audio/mpeg"),
                                        Set.of(".mp3"),
                                        50)));
            }

            @Override
            public Mono<RawContent> parse(byte[] data, SourceDescriptor source) {
                return testAudioParser().parse(data, source);
            }

            @Override
            public List<ContentCapability> capabilities() {
                return List.of();
            }
        };
    }

    private static com.openmemind.ai.memory.core.resource.ContentParser testImageParser() {
        return new com.openmemind.ai.memory.core.resource.ContentParser() {
            @Override
            public String parserId() {
                return "image-vision";
            }

            @Override
            public String contentType() {
                return IMAGE_TYPE;
            }

            @Override
            public String contentProfile() {
                return "image.caption-ocr";
            }

            @Override
            public Set<String> supportedMimeTypes() {
                return Set.of("image/png");
            }

            @Override
            public Set<String> supportedExtensions() {
                return Set.of(".png");
            }

            @Override
            public Mono<RawContent> parse(byte[] data, SourceDescriptor source) {
                return Mono.just(
                        new TestImageContent(
                                "image/png",
                                "A revenue chart",
                                "Q1 revenue grew 42%",
                                source.sourceUrl(),
                                Map.of("provider", "test")));
            }
        };
    }

    private static com.openmemind.ai.memory.core.resource.ContentParser testAudioParser() {
        return new com.openmemind.ai.memory.core.resource.ContentParser() {
            @Override
            public String parserId() {
                return "audio-transcription";
            }

            @Override
            public String contentType() {
                return AUDIO_TYPE;
            }

            @Override
            public String contentProfile() {
                return "audio.transcript";
            }

            @Override
            public Set<String> supportedMimeTypes() {
                return Set.of("audio/mpeg");
            }

            @Override
            public Set<String> supportedExtensions() {
                return Set.of(".mp3");
            }

            @Override
            public Mono<RawContent> parse(byte[] data, SourceDescriptor source) {
                return Mono.just(
                        new TestAudioContent(
                                "audio/mpeg",
                                "Speaker one said hello",
                                source.sourceUrl(),
                                Map.of("provider", "test")));
            }
        };
    }

    private DefaultMemoryExtractor extractorWithRestrictiveOptions(
            RawDataExtractStep rawDataStep, RawContentProcessorRegistry processorRegistry) {
        return extractor(
                rawDataStep,
                (memoryId, rawDataResult, config) -> Mono.just(MemoryItemResult.empty()),
                (memoryId, memoryItemResult) -> Mono.just(InsightResult.empty()),
                processorRegistry,
                null,
                null,
                null,
                new RawDataExtractionOptions(
                        com.openmemind.ai.memory.core.extraction.rawdata.chunk
                                .ConversationChunkingConfig.DEFAULT,
                        com.openmemind.ai.memory.core.extraction.context.CommitDetectorConfig
                                .defaults(),
                        64),
                ItemExtractionOptions.defaults());
    }

    private DefaultMemoryExtractor extractor(
            RawDataExtractStep rawDataStep,
            MemoryItemExtractStep memoryItemStep,
            com.openmemind.ai.memory.core.extraction.step.InsightExtractStep insightStep,
            ContentParserRegistry contentParserRegistry,
            ResourceStore resourceStore,
            ResourceFetcher resourceFetcher) {
        return extractor(
                rawDataStep,
                memoryItemStep,
                insightStep,
                documentProcessorRegistry(),
                contentParserRegistry,
                resourceStore,
                resourceFetcher,
                defaultDocumentIngestionPolicyRegistry(),
                RawDataExtractionOptions.defaults(),
                ItemExtractionOptions.defaults());
    }

    private DefaultMemoryExtractor extractor(
            RawDataExtractStep rawDataStep,
            MemoryItemExtractStep memoryItemStep,
            com.openmemind.ai.memory.core.extraction.step.InsightExtractStep insightStep,
            RawContentProcessorRegistry processorRegistry,
            ContentParserRegistry contentParserRegistry,
            ResourceStore resourceStore,
            ResourceFetcher resourceFetcher,
            RawDataExtractionOptions rawDataExtractionOptions,
            ItemExtractionOptions itemExtractionOptions) {
        return extractor(
                rawDataStep,
                memoryItemStep,
                insightStep,
                processorRegistry,
                contentParserRegistry,
                resourceStore,
                resourceFetcher,
                defaultDocumentIngestionPolicyRegistry(),
                rawDataExtractionOptions,
                itemExtractionOptions);
    }

    private DefaultMemoryExtractor extractor(
            RawDataExtractStep rawDataStep,
            MemoryItemExtractStep memoryItemStep,
            com.openmemind.ai.memory.core.extraction.step.InsightExtractStep insightStep,
            RawContentProcessorRegistry processorRegistry,
            ContentParserRegistry contentParserRegistry,
            ResourceStore resourceStore,
            ResourceFetcher resourceFetcher,
            RawDataIngestionPolicyRegistry ingestionPolicyRegistry,
            RawDataExtractionOptions rawDataExtractionOptions,
            ItemExtractionOptions itemExtractionOptions) {
        return new DefaultMemoryExtractor(
                rawDataStep,
                memoryItemStep,
                insightStep,
                null,
                null,
                null,
                null,
                processorRegistry,
                contentParserRegistry,
                resourceStore,
                resourceFetcher,
                ingestionPolicyRegistry,
                rawDataExtractionOptions,
                itemExtractionOptions);
    }

    private RawContentProcessorRegistry documentProcessorRegistry() {
        return documentProcessorRegistry(Integer.MAX_VALUE);
    }

    private RawContentProcessorRegistry imageProcessorRegistry() {
        return new RawContentProcessorRegistry(List.of(new TestImageProcessor()));
    }

    private RawContentProcessorRegistry audioProcessorRegistry() {
        return new RawContentProcessorRegistry(List.of(new TestAudioProcessor()));
    }

    private RawContentProcessorRegistry documentProcessorRegistry(int maxParsedTokens) {
        return new RawContentProcessorRegistry(
                List.of(new TestDocumentProcessor(false, maxParsedTokens)));
    }

    private static final class TestImageProcessor implements RawContentProcessor<TestImageContent> {

        @Override
        public String contentType() {
            return IMAGE_TYPE;
        }

        @Override
        public Class<TestImageContent> contentClass() {
            return TestImageContent.class;
        }

        @Override
        public Mono<List<Segment>> chunk(TestImageContent content) {
            return Mono.just(List.of(Segment.single(content.toContentString())));
        }
    }

    private static final class TestAudioProcessor implements RawContentProcessor<TestAudioContent> {

        @Override
        public String contentType() {
            return AUDIO_TYPE;
        }

        @Override
        public Class<TestAudioContent> contentClass() {
            return TestAudioContent.class;
        }

        @Override
        public Mono<List<Segment>> chunk(TestAudioContent content) {
            return Mono.just(List.of(Segment.single(content.toContentString())));
        }
    }

    private int restrictiveBinaryParsedMaxTokens() {
        return 128;
    }

    private RawDataIngestionPolicyRegistry defaultDocumentIngestionPolicyRegistry() {
        return new RawDataIngestionPolicyRegistry(
                List.of(
                        new RawDataIngestionPolicy(
                                TestDocumentContent.TYPE,
                                Set.of(ContentGovernanceType.DOCUMENT_TEXT_LIKE),
                                new com.openmemind.ai.memory.core.builder.SourceLimitOptions(
                                        2L * 1024 * 1024)),
                        new RawDataIngestionPolicy(
                                TestDocumentContent.TYPE,
                                Set.of(ContentGovernanceType.DOCUMENT_BINARY),
                                new com.openmemind.ai.memory.core.builder.SourceLimitOptions(
                                        20L * 1024 * 1024)),
                        new RawDataIngestionPolicy(
                                IMAGE_TYPE,
                                Set.of(ContentGovernanceType.IMAGE_CAPTION_OCR),
                                new com.openmemind.ai.memory.core.builder.SourceLimitOptions(
                                        10L * 1024 * 1024)),
                        new RawDataIngestionPolicy(
                                AUDIO_TYPE,
                                Set.of(ContentGovernanceType.AUDIO_TRANSCRIPT),
                                new com.openmemind.ai.memory.core.builder.SourceLimitOptions(
                                        25L * 1024 * 1024))));
    }

    private static final class RecordingResolutionRegistry implements ContentParserRegistry {

        private final List<SourceDescriptor> descriptors = new ArrayList<>();

        @Override
        public Mono<ParserResolution> resolve(SourceDescriptor source) {
            descriptors.add(source);
            if (source.fileName() == null && source.mimeType() == null) {
                return Mono.error(
                        new UnsupportedContentSourceException(
                                "Unsupported provisional source: " + source.sourceUrl()));
            }
            return Mono.just(
                    new ParserResolution(
                            nullParser(),
                            new ContentCapability(
                                    "document-tika",
                                    TestDocumentContent.TYPE,
                                    "document.binary",
                                    Set.of("application/pdf"),
                                    Set.of(".pdf"),
                                    50)));
        }

        @Override
        public Mono<RawContent> parse(byte[] data, SourceDescriptor source) {
            return Mono.just(TestDocumentContent.of("Report", source.mimeType(), "parsed body"));
        }

        @Override
        public List<ContentCapability> capabilities() {
            return List.of();
        }

        List<SourceDescriptor> descriptors() {
            return descriptors;
        }
    }

    private static final class CustomProfileRegistry implements ContentParserRegistry {

        @Override
        public Mono<ParserResolution> resolve(SourceDescriptor source) {
            return Mono.just(
                    new ParserResolution(
                            nullParser(),
                            new ContentCapability(
                                    "document-custom",
                                    TestDocumentContent.TYPE,
                                    "document.pdf.tika",
                                    ContentGovernanceType.DOCUMENT_BINARY,
                                    Set.of("application/pdf"),
                                    Set.of(".pdf"),
                                    10)));
        }

        @Override
        public Mono<RawContent> parse(byte[] data, SourceDescriptor source) {
            return Mono.just(
                    new TestDocumentContent(
                            "Report",
                            source.mimeType(),
                            "parsed body",
                            null,
                            null,
                            null,
                            Map.of("contentProfile", "document.pdf.tika")));
        }

        @Override
        public List<ContentCapability> capabilities() {
            return List.of(
                    new ContentCapability(
                            "document-custom",
                            TestDocumentContent.TYPE,
                            "document.pdf.tika",
                            ContentGovernanceType.DOCUMENT_BINARY,
                            Set.of("application/pdf"),
                            Set.of(".pdf"),
                            10));
        }
    }

    private static final class ConflictingGovernanceRegistry implements ContentParserRegistry {

        @Override
        public Mono<ParserResolution> resolve(SourceDescriptor source) {
            return Mono.just(
                    new ParserResolution(
                            nullParser(),
                            new ContentCapability(
                                    "document-custom",
                                    TestDocumentContent.TYPE,
                                    "document.pdf.tika",
                                    ContentGovernanceType.DOCUMENT_BINARY,
                                    Set.of("application/pdf"),
                                    Set.of(".pdf"),
                                    10)));
        }

        @Override
        public Mono<RawContent> parse(byte[] data, SourceDescriptor source) {
            return Mono.just(
                    new TestDocumentContent(
                            "Report",
                            source.mimeType(),
                            "parsed body",
                            null,
                            null,
                            null,
                            Map.of(
                                    "contentProfile",
                                    "document.pdf.tika",
                                    "governanceType",
                                    ContentGovernanceType.DOCUMENT_TEXT_LIKE.name())));
        }

        @Override
        public List<ContentCapability> capabilities() {
            return List.of();
        }
    }

    private static final class ConflictingBuiltinProfileRegistry implements ContentParserRegistry {

        @Override
        public Mono<ParserResolution> resolve(SourceDescriptor source) {
            return Mono.just(
                    new ParserResolution(
                            nullParser(),
                            new ContentCapability(
                                    "document-custom",
                                    TestDocumentContent.TYPE,
                                    "document.pdf.tika",
                                    ContentGovernanceType.DOCUMENT_BINARY,
                                    Set.of("application/pdf"),
                                    Set.of(".pdf"),
                                    10)));
        }

        @Override
        public Mono<RawContent> parse(byte[] data, SourceDescriptor source) {
            return Mono.just(
                    new TestDocumentContent(
                            "Report",
                            source.mimeType(),
                            "parsed body",
                            null,
                            null,
                            null,
                            Map.of("contentProfile", "document.markdown")));
        }

        @Override
        public List<ContentCapability> capabilities() {
            return List.of();
        }
    }

    private static final class UnsupportedRegistry implements ContentParserRegistry {

        private final List<String> calls = new ArrayList<>();

        @Override
        public Mono<ParserResolution> resolve(SourceDescriptor source) {
            calls.add(source.fileName() + ":" + source.mimeType() + ":" + source.sizeBytes());
            return Mono.error(
                    new UnsupportedContentSourceException("Unsupported source: " + source));
        }

        @Override
        public Mono<RawContent> parse(byte[] data, SourceDescriptor source) {
            calls.add(source.fileName() + ":" + source.mimeType() + ":" + data.length);
            return Mono.error(
                    new UnsupportedContentSourceException(
                            "Unsupported source: fileName="
                                    + source.fileName()
                                    + ", mimeType="
                                    + source.mimeType()));
        }

        @Override
        public List<ContentCapability> capabilities() {
            return List.of(
                    new ContentCapability(
                            "document-test",
                            TestDocumentContent.TYPE,
                            "document.binary",
                            Set.of("application/pdf"),
                            Set.of(".pdf"),
                            10));
        }
    }

    private static final class OversizedDocumentRegistry implements ContentParserRegistry {

        @Override
        public Mono<ParserResolution> resolve(SourceDescriptor source) {
            return Mono.just(
                    new ParserResolution(
                            nullParser(),
                            new ContentCapability(
                                    "document-test",
                                    TestDocumentContent.TYPE,
                                    "document.binary",
                                    Set.of("application/pdf"),
                                    Set.of(".pdf"),
                                    10)));
        }

        @Override
        public Mono<RawContent> parse(byte[] data, SourceDescriptor source) {
            return Mono.just(
                    new TestDocumentContent(
                            "Report",
                            source.mimeType(),
                            "word ".repeat(300),
                            null,
                            null,
                            null,
                            Map.of("contentProfile", "document.binary")));
        }

        @Override
        public List<ContentCapability> capabilities() {
            return List.of();
        }
    }

    private static RawContent nullParserContent() {
        return TestDocumentContent.of("Report", "application/pdf", "parsed body");
    }

    private static final class TestImageContent extends RawContent {

        private final String mimeType;
        private final String description;
        private final String ocrText;
        private final String sourceUri;
        private final Map<String, Object> metadata;

        private TestImageContent(
                String mimeType,
                String description,
                String ocrText,
                String sourceUri,
                Map<String, Object> metadata) {
            this.mimeType = mimeType;
            this.description = description;
            this.ocrText = ocrText;
            this.sourceUri = sourceUri;
            this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }

        @Override
        public String contentType() {
            return IMAGE_TYPE;
        }

        @Override
        public String toContentString() {
            if (ocrText == null || ocrText.isBlank()) {
                return description == null ? "" : description;
            }
            return description == null || description.isBlank()
                    ? ocrText
                    : description + "\n" + ocrText;
        }

        @Override
        public String getContentId() {
            return "image:" + toContentString();
        }

        @Override
        public Map<String, Object> contentMetadata() {
            return metadata;
        }

        @Override
        public RawContent withMetadata(Map<String, Object> metadata) {
            return new TestImageContent(mimeType, description, ocrText, sourceUri, metadata);
        }

        @Override
        public String mimeType() {
            return mimeType;
        }

        @Override
        public String sourceUri() {
            return sourceUri;
        }

        @Override
        public ContentGovernanceType directGovernanceType() {
            return ContentGovernanceType.IMAGE_CAPTION_OCR;
        }

        @Override
        public String directContentProfile() {
            return "image.caption-ocr";
        }
    }

    private static final class TestAudioContent extends RawContent {

        private final String mimeType;
        private final String transcript;
        private final String sourceUri;
        private final Map<String, Object> metadata;

        private TestAudioContent(
                String mimeType,
                String transcript,
                String sourceUri,
                Map<String, Object> metadata) {
            this.mimeType = mimeType;
            this.transcript = transcript;
            this.sourceUri = sourceUri;
            this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }

        @Override
        public String contentType() {
            return AUDIO_TYPE;
        }

        @Override
        public String toContentString() {
            return transcript == null ? "" : transcript;
        }

        @Override
        public String getContentId() {
            return "audio:" + toContentString();
        }

        @Override
        public Map<String, Object> contentMetadata() {
            return metadata;
        }

        @Override
        public RawContent withMetadata(Map<String, Object> metadata) {
            return new TestAudioContent(mimeType, transcript, sourceUri, metadata);
        }

        @Override
        public String mimeType() {
            return mimeType;
        }

        @Override
        public String sourceUri() {
            return sourceUri;
        }

        @Override
        public ContentGovernanceType directGovernanceType() {
            return ContentGovernanceType.AUDIO_TRANSCRIPT;
        }

        @Override
        public String directContentProfile() {
            return "audio.transcript";
        }
    }

    private static com.openmemind.ai.memory.core.resource.ContentParser nullParser() {
        return new com.openmemind.ai.memory.core.resource.ContentParser() {
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
            public Mono<RawContent> parse(byte[] data, SourceDescriptor source) {
                return Mono.just(nullParserContent());
            }
        };
    }

    private static final class RecordingResourceFetcher implements ResourceFetcher {

        private final List<String> calls = new ArrayList<>();

        @Override
        public Mono<FetchSession> open(ResourceFetchRequest request) {
            calls.add(request.sourceUrl());
            return Mono.just(
                    new TestFetchSession(
                            request.sourceUrl(),
                            request.sourceUrl(),
                            request.requestedFileName() == null
                                    ? "report.pdf"
                                    : request.requestedFileName(),
                            request.requestedMimeType() == null
                                    ? "application/pdf"
                                    : request.requestedMimeType(),
                            3L,
                            new byte[] {1, 2, 3}));
        }
    }

    private static final class RecordingFetchSessionFetcher implements ResourceFetcher {

        private final String fileName;
        private final String mimeType;
        private final long declaredContentLength;

        private RecordingFetchSessionFetcher(
                String fileName, String mimeType, long declaredContentLength) {
            this.fileName = fileName;
            this.mimeType = mimeType;
            this.declaredContentLength = declaredContentLength;
        }

        @Override
        public Mono<FetchSession> open(ResourceFetchRequest request) {
            return Mono.just(
                    new TestFetchSession(
                            request.sourceUrl(),
                            request.sourceUrl(),
                            fileName,
                            mimeType,
                            declaredContentLength,
                            new byte[] {1, 2, 3}));
        }
    }

    private static final class TestFetchSession implements FetchSession {

        private final String sourceUrl;
        private final String finalUrl;
        private final String fileName;
        private final String mimeType;
        private final long declaredContentLength;
        private final byte[] body;

        private TestFetchSession(
                String sourceUrl,
                String finalUrl,
                String fileName,
                String mimeType,
                long declaredContentLength,
                byte[] body) {
            this.sourceUrl = sourceUrl;
            this.finalUrl = finalUrl;
            this.fileName = fileName;
            this.mimeType = mimeType;
            this.declaredContentLength = declaredContentLength;
            this.body = body;
        }

        @Override
        public String sourceUrl() {
            return sourceUrl;
        }

        @Override
        public String finalUrl() {
            return finalUrl;
        }

        @Override
        public int statusCode() {
            return 200;
        }

        @Override
        public Map<String, List<String>> responseHeaders() {
            return Map.of("Content-Type", List.of(mimeType));
        }

        @Override
        public String resolvedFileName() {
            return fileName;
        }

        @Override
        public String resolvedMimeType() {
            return mimeType;
        }

        @Override
        public Long declaredContentLength() {
            return declaredContentLength;
        }

        @Override
        public Mono<FetchedResource> readBody(long maxBytes) {
            if (declaredContentLength > maxBytes) {
                return Mono.error(new SourceTooLargeException("source too large"));
            }
            return Mono.just(
                    new FetchedResource(
                            sourceUrl, finalUrl, fileName, body, mimeType, body.length));
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

    private static final class OversizedRawDataStep implements RawDataExtractStep {

        @Override
        public Mono<RawDataResult> extract(
                MemoryId memoryId,
                RawContent content,
                String contentType,
                Map<String, Object> metadata) {
            return extract(memoryId, content, contentType, metadata, null);
        }

        @Override
        public Mono<RawDataResult> extract(
                MemoryId memoryId,
                RawContent content,
                String contentType,
                Map<String, Object> metadata,
                String language) {
            String text =
                    "# Intro\n" + "word ".repeat(3_000) + "\n\n## Usage\n" + "word ".repeat(3_000);
            return Mono.just(
                    new RawDataResult(
                            List.of(),
                            List.of(
                                    new ParsedSegment(
                                            text,
                                            "caption",
                                            0,
                                            text.length(),
                                            "raw-1",
                                            Map.of("contentProfile", "document.markdown"))),
                            false));
        }
    }

    private static final class RecordingMemoryItemStep implements MemoryItemExtractStep {

        private RawDataResult lastRawDataResult;

        @Override
        public Mono<MemoryItemResult> extract(
                MemoryId memoryId, RawDataResult rawDataResult, ItemExtractionConfig config) {
            this.lastRawDataResult = rawDataResult;
            return Mono.just(MemoryItemResult.empty());
        }

        RawDataResult lastRawDataResult() {
            return lastRawDataResult;
        }
    }
}
