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
package com.openmemind.ai.memory.core.extraction.rawdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.openmemind.ai.memory.core.data.MemoryRawData;
import com.openmemind.ai.memory.core.data.MemoryResource;
import com.openmemind.ai.memory.core.extraction.rawdata.caption.CaptionGenerator;
import com.openmemind.ai.memory.core.extraction.rawdata.content.ConversationContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.ToolCallContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.tool.ToolCallRecord;
import com.openmemind.ai.memory.core.extraction.rawdata.processor.ConversationContentProcessor;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.CharBoundary;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.MessageBoundary;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.Segment;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.SegmentRuntimeContext;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.store.insight.InMemoryInsightOperations;
import com.openmemind.ai.memory.core.store.insight.InsightOperations;
import com.openmemind.ai.memory.core.store.item.InMemoryItemOperations;
import com.openmemind.ai.memory.core.store.item.ItemOperations;
import com.openmemind.ai.memory.core.store.rawdata.InMemoryRawDataOperations;
import com.openmemind.ai.memory.core.store.rawdata.RawDataOperations;
import com.openmemind.ai.memory.core.store.resource.InMemoryResourceOperations;
import com.openmemind.ai.memory.core.store.resource.ResourceOperations;
import com.openmemind.ai.memory.core.support.TestDocumentContent;
import com.openmemind.ai.memory.core.vector.MemoryVector;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

@DisplayName("RawDataLayer processor registry")
class RawDataLayerProcessorTest {

    private final MemoryStore store = mock(MemoryStore.class);
    private final RawDataOperations rawDataOps = mock(RawDataOperations.class);
    private final MemoryVector vector = mock(MemoryVector.class);
    private final CaptionGenerator defaultCaption = mock(CaptionGenerator.class);

    {
        when(store.rawDataOperations()).thenReturn(rawDataOps);
        when(store.resourceOperations()).thenReturn(mock(ResourceOperations.class));
        doCallRealMethod().when(store).upsertRawDataWithResources(any(), any(), any());
    }

    @Nested
    @DisplayName("getProcessor routing")
    class GetProcessorRouting {

        @Test
        @DisplayName("duplicate processor registrations should fail fast")
        void duplicateProcessorRegistrationsFailFast() {
            var first = mockTestDocumentProcessor();
            var second = mockTestDocumentProcessor();
            when(first.contentClass()).thenReturn(TestDocumentContent.class);
            when(second.contentClass()).thenReturn(TestDocumentContent.class);

            assertThatThrownBy(
                            () ->
                                    new RawDataLayer(
                                            List.of(first, second),
                                            defaultCaption,
                                            store,
                                            vector,
                                            64))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(TestDocumentContent.class.getName());
        }

        @Test
        @DisplayName("ConversationContent should route to ConversationContentProcessor")
        void conversationContentUsesConversationProcessor() {
            var convProcessor = mock(ConversationContentProcessor.class);
            when(convProcessor.contentClass()).thenReturn(ConversationContent.class);
            when(convProcessor.chunk(any(ConversationContent.class)))
                    .thenReturn(Mono.just(List.of()));
            when(convProcessor.captionGenerator()).thenReturn(defaultCaption);

            @SuppressWarnings("unchecked")
            RawContentProcessor<ToolCallContent> toolProcessor = mock(RawContentProcessor.class);
            when(toolProcessor.contentClass()).thenReturn(ToolCallContent.class);

            var layer =
                    new RawDataLayer(
                            List.of(convProcessor, toolProcessor),
                            defaultCaption,
                            store,
                            vector,
                            64);

            var content = ConversationContent.builder().addUserMessage("hello").build();

            when(defaultCaption.generateForSegments(any(), any())).thenReturn(Mono.just(List.of()));
            when(rawDataOps.getRawDataByContentId(any(), any()))
                    .thenReturn(java.util.Optional.empty());
            when(vector.storeBatch(any(), any(), any())).thenReturn(Mono.just(List.of()));

            layer.extract(
                            new com.openmemind.ai.memory.core.data.DefaultMemoryId("test", "agent"),
                            content,
                            "CONVERSATION",
                            java.util.Map.of())
                    .block();

            verify(convProcessor).chunk(any(ConversationContent.class));
        }

        @Test
        @DisplayName("ToolCallContent should route to ToolCallContentProcessor")
        void toolCallContentUsesToolCallProcessor() {
            var convProcessor = mock(ConversationContentProcessor.class);
            when(convProcessor.contentClass()).thenReturn(ConversationContent.class);

            @SuppressWarnings("unchecked")
            RawContentProcessor<ToolCallContent> toolProcessor = mock(RawContentProcessor.class);
            when(toolProcessor.contentClass()).thenReturn(ToolCallContent.class);
            when(toolProcessor.chunk(any(ToolCallContent.class))).thenReturn(Mono.just(List.of()));
            when(toolProcessor.captionGenerator()).thenReturn(defaultCaption);

            var layer =
                    new RawDataLayer(
                            List.of(convProcessor, toolProcessor),
                            defaultCaption,
                            store,
                            vector,
                            64);

            var record =
                    new ToolCallRecord(
                            "search", "{}", "ok", "success", 100L, 10, 5, null, Instant.now());
            var content = new ToolCallContent(List.of(record));

            when(defaultCaption.generateForSegments(any(), any())).thenReturn(Mono.just(List.of()));
            when(rawDataOps.getRawDataByContentId(any(), any()))
                    .thenReturn(java.util.Optional.empty());
            when(vector.storeBatch(any(), any(), any())).thenReturn(Mono.just(List.of()));

            layer.extract(
                            new com.openmemind.ai.memory.core.data.DefaultMemoryId("test", "agent"),
                            content,
                            "TOOL_CALL",
                            java.util.Map.of())
                    .block();

            verify(toolProcessor).chunk(any(ToolCallContent.class));
        }

        @Test
        @DisplayName("Document processor should opt into source-identity-aware content id hashing")
        void sourceIdentityAwareHashingUsesProcessorHook() {
            var documentProcessor = mockTestDocumentProcessor();
            when(documentProcessor.contentClass()).thenReturn(TestDocumentContent.class);
            when(documentProcessor.usesSourceIdentity()).thenReturn(true);
            when(documentProcessor.chunk(any(TestDocumentContent.class)))
                    .thenReturn(Mono.just(List.of()));
            when(documentProcessor.captionGenerator()).thenReturn(defaultCaption);
            when(defaultCaption.generateForSegments(any(), any())).thenReturn(Mono.just(List.of()));

            var layer =
                    new RawDataLayer(List.of(documentProcessor), defaultCaption, store, vector, 64);
            var content = TestDocumentContent.of("Report", "text/plain", "hello");

            when(rawDataOps.getRawDataByContentId(any(), any())).thenReturn(Optional.empty());

            layer.extract(
                            new com.openmemind.ai.memory.core.data.DefaultMemoryId("test", "agent"),
                            content,
                            "DOCUMENT",
                            Map.of("sourceUri", "https://example.com/report.txt"))
                    .block();

            verify(documentProcessor).usesSourceIdentity();
            verify(rawDataOps)
                    .getRawDataByContentId(
                            any(), argThat(id -> !id.equals(content.getContentId())));
        }

        @Test
        @DisplayName("Unknown RawContent type should throw IllegalArgumentException")
        void unknownContentTypeThrows() {
            var convProcessor = mock(ConversationContentProcessor.class);
            when(convProcessor.contentClass()).thenReturn(ConversationContent.class);

            var layer = new RawDataLayer(List.of(convProcessor), defaultCaption, store, vector, 64);

            var unknownContent =
                    new RawContent() {
                        @Override
                        public String contentType() {
                            return "UNKNOWN";
                        }

                        @Override
                        public String toContentString() {
                            return "unknown";
                        }

                        @Override
                        public String getContentId() {
                            return "unknown-id";
                        }
                    };

            when(rawDataOps.getRawDataByContentId(any(), any()))
                    .thenReturn(java.util.Optional.empty());

            assertThatThrownBy(
                            () ->
                                    layer.extract(
                                                    new com.openmemind.ai.memory.core.data
                                                            .DefaultMemoryId("test", "agent"),
                                                    unknownContent,
                                                    "UNKNOWN",
                                                    java.util.Map.of())
                                            .block())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("No processor registered for");
        }

        @Test
        @DisplayName(
                "Subclass of ConversationContent should find the processor via inheritance lookup")
        void subclassFindsProcessorViaInheritance() {
            var convProcessor = mock(ConversationContentProcessor.class);
            when(convProcessor.contentClass()).thenReturn(ConversationContent.class);
            when(convProcessor.chunk(any(ConversationContent.class)))
                    .thenReturn(Mono.just(List.of()));
            when(convProcessor.captionGenerator()).thenReturn(defaultCaption);

            var layer = new RawDataLayer(List.of(convProcessor), defaultCaption, store, vector, 64);

            // Create a subclass of ConversationContent
            var subclassContent =
                    new ConversationContent(List.of()) {
                        @Override
                        public String getContentId() {
                            return "subclass-id";
                        }
                    };

            when(defaultCaption.generateForSegments(any(), any())).thenReturn(Mono.just(List.of()));
            when(rawDataOps.getRawDataByContentId(any(), any()))
                    .thenReturn(java.util.Optional.empty());
            when(vector.storeBatch(any(), any(), any())).thenReturn(Mono.just(List.of()));

            layer.extract(
                            new com.openmemind.ai.memory.core.data.DefaultMemoryId("test", "agent"),
                            subclassContent,
                            "CONVERSATION",
                            java.util.Map.of())
                    .block();

            verify(convProcessor).chunk(any(ConversationContent.class));
        }

        @Test
        @DisplayName("Subclass of ConversationContent should reuse the processor caption generator")
        void subclassReusesProcessorCaptionGenerator() {
            var processorCaption = mock(CaptionGenerator.class);
            var convProcessor = mock(ConversationContentProcessor.class);
            var baseSegment = new Segment("hello", null, new CharBoundary(0, 5), Map.of());
            when(convProcessor.contentClass()).thenReturn(ConversationContent.class);
            when(convProcessor.chunk(any(ConversationContent.class)))
                    .thenReturn(Mono.just(List.of(baseSegment)));
            when(convProcessor.captionGenerator()).thenReturn(processorCaption);

            var layer = new RawDataLayer(List.of(convProcessor), defaultCaption, store, vector, 64);

            var subclassContent =
                    new ConversationContent(List.of()) {
                        @Override
                        public String getContentId() {
                            return "subclass-id";
                        }
                    };

            when(processorCaption.generateForSegments(any(), any()))
                    .thenReturn(Mono.just(List.of(baseSegment.withCaption("processor caption"))));
            when(rawDataOps.getRawDataByContentId(any(), any()))
                    .thenReturn(java.util.Optional.empty());
            when(vector.storeBatch(any(), any(), any())).thenReturn(Mono.just(List.of("vec-1")));

            layer.extract(
                            new com.openmemind.ai.memory.core.data.DefaultMemoryId("test", "agent"),
                            subclassContent,
                            "CONVERSATION",
                            Map.of())
                    .block();

            verify(processorCaption).generateForSegments(any(), any());
            verify(defaultCaption, never()).generateForSegments(any(), any());
        }
    }

    @Test
    @DisplayName("processSegment should forward explicit language to caption generation")
    void processSegment_forwards_language_to_caption_generation() {
        var layer = new RawDataLayer(List.of(), defaultCaption, store, vector, 64);
        var memoryId = new com.openmemind.ai.memory.core.data.DefaultMemoryId("test", "agent");
        var segment = new Segment("hello", null, new CharBoundary(0, 5), Map.of());

        when(defaultCaption.generateForSegments(any(), eq("zh-CN")))
                .thenReturn(Mono.just(List.of(segment)));
        when(rawDataOps.getRawDataByContentId(any(), any())).thenReturn(java.util.Optional.empty());
        when(vector.storeBatch(any(), any(), any())).thenReturn(Mono.just(List.of("vec-1")));

        layer.processSegment(memoryId, segment, "CONVERSATION", "content-1", Map.of(), "zh-CN")
                .block();

        verify(defaultCaption).generateForSegments(any(), eq("zh-CN"));
    }

    @Test
    @DisplayName("processSegment returns existing rawData without vectorizing again")
    void processSegmentReturnsExistingRawDataWithoutVectorizingAgain() {
        var layer = new RawDataLayer(List.of(), defaultCaption, store, vector, 64);
        var memoryId = new com.openmemind.ai.memory.core.data.DefaultMemoryId("test", "agent");
        var segment = new Segment("hello", null, new CharBoundary(0, 5), Map.of());
        var existing =
                new MemoryRawData(
                        "raw-1",
                        memoryId.toIdentifier(),
                        "CONVERSATION",
                        "content-1",
                        new Segment("persisted", "caption", new CharBoundary(0, 9), Map.of()),
                        "caption",
                        "vec-1",
                        Map.of(),
                        null,
                        null,
                        Instant.parse("2026-03-27T02:18:00Z"),
                        Instant.parse("2026-03-27T02:17:00Z"),
                        Instant.parse("2026-03-27T02:18:00Z"));
        when(rawDataOps.getRawDataByContentId(memoryId, "content-1"))
                .thenReturn(java.util.Optional.of(existing));

        var result =
                layer.processSegment(
                                memoryId, segment, "CONVERSATION", "content-1", Map.of(), "zh-CN")
                        .block();

        assertThat(result).isNotNull();
        assertThat(result.existed()).isTrue();
        assertThat(result.rawDataList()).containsExactly(existing);
        verifyNoInteractions(defaultCaption, vector);
    }

    @Test
    @DisplayName("processSegment keeps runtime context transient while using it for rawdata timing")
    void processSegmentKeepsRuntimeContextTransientWhileUsingItForRawdataTiming() {
        var layer = new RawDataLayer(List.of(), defaultCaption, store, vector, 64);
        var memoryId = new com.openmemind.ai.memory.core.data.DefaultMemoryId("test", "agent");
        var runtimeContext =
                new SegmentRuntimeContext(
                        Instant.parse("2026-03-27T02:17:00Z"),
                        Instant.parse("2026-03-27T02:18:00Z"),
                        "Alice");
        var transientSegment =
                new Segment(
                        "hello",
                        null,
                        new MessageBoundary(0, 2),
                        Map.of("start_message", 0, "end_message", 2),
                        runtimeContext);

        when(defaultCaption.generateForSegments(any(), eq("zh-CN")))
                .thenReturn(Mono.just(List.of(transientSegment.withCaption("caption"))));
        when(rawDataOps.getRawDataByContentId(any(), any())).thenReturn(java.util.Optional.empty());
        when(vector.storeBatch(any(), any(), any())).thenReturn(Mono.just(List.of("vec-1")));

        var result =
                layer.processSegment(
                                memoryId,
                                transientSegment,
                                "CONVERSATION",
                                "content-1",
                                Map.of(),
                                "zh-CN")
                        .block();

        assertThat(result).isNotNull();
        assertThat(result.segments().getFirst().runtimeContext()).isEqualTo(runtimeContext);
        assertThat(result.rawDataList().getFirst().segment().runtimeContext()).isNull();
        verify(rawDataOps)
                .upsertRawData(
                        eq(memoryId),
                        argThat(
                                rawDataList ->
                                        rawDataList.size() == 1
                                                && rawDataList.getFirst().segment().runtimeContext()
                                                        == null
                                                && rawDataList
                                                        .getFirst()
                                                        .startTime()
                                                        .equals(runtimeContext.startTime())
                                                && rawDataList
                                                        .getFirst()
                                                        .endTime()
                                                        .equals(runtimeContext.observedAt())));
    }

    @Test
    @DisplayName(
            "extract should merge request metadata and persist resolved multimodal resource fields")
    void extractShouldMergeRequestMetadataAndPersistResolvedMultimodalResourceFields() {
        var recordingStore = new RecordingMemoryStore();
        var localVector = mock(MemoryVector.class);
        var localCaption = mock(CaptionGenerator.class);
        var documentProcessor = mockTestDocumentProcessor();
        var memoryId = new com.openmemind.ai.memory.core.data.DefaultMemoryId("test", "agent");
        var content =
                new TestDocumentContent(
                        "Report",
                        "application/pdf",
                        "document body",
                        "file:///docs/report.pdf",
                        null,
                        null,
                        Map.of("pageCount", 12));
        var chunkedSegment =
                new Segment(
                        "document body", null, new CharBoundary(0, 13), Map.of("sectionIndex", 0));

        when(documentProcessor.contentClass()).thenReturn(TestDocumentContent.class);
        when(documentProcessor.usesSourceIdentity()).thenReturn(true);
        when(documentProcessor.chunk(any(TestDocumentContent.class)))
                .thenReturn(Mono.just(List.of(chunkedSegment)));
        when(documentProcessor.captionGenerator()).thenReturn(localCaption);
        when(localCaption.generateForSegments(any(), eq("zh-CN")))
                .thenReturn(Mono.just(List.of(chunkedSegment.withCaption("summary"))));
        when(localVector.storeBatch(any(), any(), any())).thenReturn(Mono.just(List.of("vec-1")));
        var layer =
                new RawDataLayer(
                        List.of(documentProcessor), localCaption, recordingStore, localVector, 64);

        var result =
                layer.extract(
                                memoryId,
                                content,
                                content.contentType(),
                                Map.of(
                                        "pageCount", 12,
                                        "sourceUri", "file:///docs/report.pdf",
                                        "mimeType", "application/pdf",
                                        "fileName", "report.pdf",
                                        "channel", "upload"),
                                "zh-CN")
                        .block();

        assertThat(result).isNotNull();
        assertThat(result.existed()).isFalse();
        assertThat(recordingStore.lastResources).hasSize(1);
        assertThat(recordingStore.lastRawData).hasSize(1);

        MemoryResource resource = recordingStore.lastResources.getFirst();
        assertThat(resource.sourceUri()).isEqualTo("file:///docs/report.pdf");
        assertThat(resource.fileName()).isEqualTo("report.pdf");
        assertThat(resource.mimeType()).isEqualTo("application/pdf");
        assertThat(resource.metadata())
                .containsEntry("pageCount", 12)
                .containsEntry("channel", "upload")
                .doesNotContainKey("vectorId")
                .doesNotContainKey("sectionIndex");

        var storedRawData = recordingStore.lastRawData.getFirst();
        assertThat(storedRawData.resourceId()).isEqualTo(resource.id());
        assertThat(storedRawData.mimeType()).isEqualTo("application/pdf");
        assertThat(storedRawData.metadata())
                .containsEntry("pageCount", 12)
                .containsEntry("channel", "upload")
                .containsEntry("sectionIndex", 0)
                .containsEntry("resourceId", resource.id())
                .containsEntry("mimeType", "application/pdf")
                .containsEntry("sourceUri", "file:///docs/report.pdf")
                .containsEntry("vectorId", "vec-1");
        assertThat(result.segments())
                .singleElement()
                .satisfies(
                        parsedSegment -> {
                            assertThat(parsedSegment.metadata())
                                    .containsEntry("pageCount", 12)
                                    .containsEntry("channel", "upload")
                                    .containsEntry("sectionIndex", 0)
                                    .containsEntry("resourceId", resource.id())
                                    .containsEntry("mimeType", "application/pdf");
                        });
    }

    @Test
    @DisplayName("extract should use source-aware idempotency keys for multimodal content")
    void extractShouldUseSourceAwareIdempotencyKeysForMultimodalContent() {
        var recordingStore = new RecordingMemoryStore();
        var localVector = mock(MemoryVector.class);
        var localCaption = mock(CaptionGenerator.class);
        var documentProcessor = mockTestDocumentProcessor();
        var memoryId = new com.openmemind.ai.memory.core.data.DefaultMemoryId("test", "agent");
        var chunkedSegment =
                new Segment("same text", null, new CharBoundary(0, 9), Map.of("sectionIndex", 0));

        when(documentProcessor.contentClass()).thenReturn(TestDocumentContent.class);
        when(documentProcessor.usesSourceIdentity()).thenReturn(true);
        when(documentProcessor.chunk(any(TestDocumentContent.class)))
                .thenReturn(Mono.just(List.of(chunkedSegment)));
        when(documentProcessor.captionGenerator()).thenReturn(localCaption);
        when(localCaption.generateForSegments(any(), any()))
                .thenReturn(Mono.just(List.of(chunkedSegment.withCaption("summary"))));
        when(localVector.storeBatch(any(), any(), any())).thenReturn(Mono.just(List.of("vec-1")));
        var layer =
                new RawDataLayer(
                        List.of(documentProcessor), localCaption, recordingStore, localVector, 64);

        var first =
                layer.extract(
                                memoryId,
                                new TestDocumentContent(
                                        "Doc",
                                        "application/pdf",
                                        "same text",
                                        "file:///docs/a.pdf",
                                        null,
                                        null,
                                        Map.of()),
                                "DOCUMENT",
                                Map.of(
                                        "sourceUri", "file:///docs/a.pdf",
                                        "mimeType", "application/pdf"))
                        .block();
        var secondSameSource =
                layer.extract(
                                memoryId,
                                new TestDocumentContent(
                                        "Doc",
                                        "application/pdf",
                                        "same text",
                                        "file:///docs/a.pdf",
                                        null,
                                        null,
                                        Map.of()),
                                "DOCUMENT",
                                Map.of(
                                        "sourceUri", "file:///docs/a.pdf",
                                        "mimeType", "application/pdf"))
                        .block();
        var thirdDifferentSource =
                layer.extract(
                                memoryId,
                                new TestDocumentContent(
                                        "Doc",
                                        "application/pdf",
                                        "same text",
                                        "file:///docs/b.pdf",
                                        null,
                                        null,
                                        Map.of()),
                                "DOCUMENT",
                                Map.of(
                                        "sourceUri", "file:///docs/b.pdf",
                                        "mimeType", "application/pdf"))
                        .block();

        assertThat(first).isNotNull();
        assertThat(secondSameSource).isNotNull();
        assertThat(thirdDifferentSource).isNotNull();
        assertThat(secondSameSource.existed()).isTrue();
        assertThat(thirdDifferentSource.existed()).isFalse();
        assertThat(recordingStore.rawDataOperations.listRawData(memoryId)).hasSize(2);
        assertThat(recordingStore.lookupContentIds).hasSize(3);
        assertThat(recordingStore.rawDataOperations.listRawData(memoryId))
                .extracting(MemoryRawData::contentId)
                .doesNotHaveDuplicates();
    }

    @Test
    @DisplayName(
            "extract should vectorize using caption first then segment content and skip blank text")
    void extractShouldVectorizeUsingFirstNonBlankTextSource() {
        var recordingStore = new RecordingMemoryStore();
        var localVector = mock(MemoryVector.class);
        var localCaption = mock(CaptionGenerator.class);
        var documentProcessor = mockTestDocumentProcessor();
        var memoryId = new com.openmemind.ai.memory.core.data.DefaultMemoryId("test", "agent");
        var first = new Segment("body-1", null, new CharBoundary(0, 6), Map.of("order", 1));
        var second = new Segment("body-2", null, new CharBoundary(7, 13), Map.of("order", 2));
        var third = new Segment("   ", null, new CharBoundary(14, 17), Map.of("order", 3));

        when(documentProcessor.contentClass()).thenReturn(TestDocumentContent.class);
        when(documentProcessor.usesSourceIdentity()).thenReturn(true);
        when(documentProcessor.chunk(any(TestDocumentContent.class)))
                .thenReturn(Mono.just(List.of(first, second, third)));
        when(documentProcessor.captionGenerator()).thenReturn(localCaption);
        when(localCaption.generateForSegments(any(), any()))
                .thenReturn(
                        Mono.just(
                                List.of(
                                        first.withCaption("summary-1"),
                                        second.withCaption("   "),
                                        third.withCaption(null))));
        when(localVector.storeBatch(any(), any(), any()))
                .thenAnswer(
                        invocation -> {
                            @SuppressWarnings("unchecked")
                            var texts = (List<String>) invocation.getArgument(1);
                            return Mono.just(
                                    java.util.stream.IntStream.range(0, texts.size())
                                            .mapToObj(i -> "vec-" + (i + 1))
                                            .toList());
                        });

        var layer =
                new RawDataLayer(
                        List.of(documentProcessor), localCaption, recordingStore, localVector, 64);

        var result =
                layer.extract(
                                memoryId,
                                TestDocumentContent.of("Doc", "text/plain", "body-1\nbody-2"),
                                "DOCUMENT",
                                Map.of())
                        .block();

        assertThat(result).isNotNull();
        verify(localVector)
                .storeBatch(
                        eq(memoryId),
                        eq(List.of("summary-1", "body-2")),
                        eq(List.of(Map.of(), Map.of())));
        assertThat(recordingStore.lastRawData)
                .extracting(MemoryRawData::captionVectorId)
                .containsExactly("vec-1", "vec-2", null);
    }

    @Test
    @DisplayName(
            "extract should batch vector writes and cleanup completed batches on later failure")
    void extractShouldCleanupCompletedVectorBatchesWhenLaterBatchFails() {
        var recordingStore = new RecordingMemoryStore();
        var localVector = mock(MemoryVector.class);
        var localCaption = mock(CaptionGenerator.class);
        var documentProcessor = mockTestDocumentProcessor();
        var memoryId = new com.openmemind.ai.memory.core.data.DefaultMemoryId("test", "agent");
        var segments =
                List.of(
                        new Segment("body-1", null, new CharBoundary(0, 6), Map.of()),
                        new Segment("body-2", null, new CharBoundary(7, 13), Map.of()),
                        new Segment("body-3", null, new CharBoundary(14, 20), Map.of()));
        var failure = new IllegalStateException("batch 2 failed");

        when(documentProcessor.contentClass()).thenReturn(TestDocumentContent.class);
        when(documentProcessor.usesSourceIdentity()).thenReturn(true);
        when(documentProcessor.chunk(any(TestDocumentContent.class)))
                .thenReturn(Mono.just(segments));
        when(documentProcessor.captionGenerator()).thenReturn(localCaption);
        when(localCaption.generateForSegments(any(), any()))
                .thenReturn(
                        Mono.just(
                                List.of(
                                        segments.get(0).withCaption("caption-1"),
                                        segments.get(1).withCaption("caption-2"),
                                        segments.get(2).withCaption("caption-3"))));
        when(localVector.storeBatch(any(), any(), any()))
                .thenAnswer(
                        invocation -> {
                            @SuppressWarnings("unchecked")
                            var texts = (List<String>) invocation.getArgument(1);
                            if (texts.equals(List.of("caption-1", "caption-2"))) {
                                return Mono.just(List.of("vec-1", "vec-2"));
                            }
                            if (texts.equals(List.of("caption-3"))) {
                                return Mono.error(failure);
                            }
                            return Mono.error(new AssertionError("unexpected batch: " + texts));
                        });
        when(localVector.deleteBatch(memoryId, List.of("vec-1", "vec-2"))).thenReturn(Mono.empty());

        var layer =
                new RawDataLayer(
                        List.of(documentProcessor), localCaption, recordingStore, localVector, 2);

        assertThatThrownBy(
                        () ->
                                layer.extract(
                                                memoryId,
                                                TestDocumentContent.of(
                                                        "Doc",
                                                        "text/plain",
                                                        "body-1\nbody-2\nbody-3"),
                                                "DOCUMENT",
                                                Map.of())
                                        .block())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("batch 2 failed");

        var inOrder = inOrder(localVector);
        inOrder.verify(localVector)
                .storeBatch(eq(memoryId), eq(List.of("caption-1", "caption-2")), any());
        inOrder.verify(localVector).storeBatch(eq(memoryId), eq(List.of("caption-3")), any());
        inOrder.verify(localVector).deleteBatch(memoryId, List.of("vec-1", "vec-2"));
    }

    @Test
    @DisplayName(
            "extract should cleanup vectors when persistence fails and suppress cleanup failure")
    void extractShouldCleanupVectorsWhenPersistenceFails() {
        var failingStore = new FailingMemoryStore(new IllegalStateException("persist failed"));
        var localVector = mock(MemoryVector.class);
        var localCaption = mock(CaptionGenerator.class);
        var documentProcessor = mockTestDocumentProcessor();
        var memoryId = new com.openmemind.ai.memory.core.data.DefaultMemoryId("test", "agent");
        var segment = new Segment("body-1", null, new CharBoundary(0, 6), Map.of());
        var cleanupFailure = new IllegalStateException("cleanup failed");

        when(documentProcessor.contentClass()).thenReturn(TestDocumentContent.class);
        when(documentProcessor.usesSourceIdentity()).thenReturn(true);
        when(documentProcessor.chunk(any(TestDocumentContent.class)))
                .thenReturn(Mono.just(List.of(segment)));
        when(documentProcessor.captionGenerator()).thenReturn(localCaption);
        when(localCaption.generateForSegments(any(), any()))
                .thenReturn(Mono.just(List.of(segment.withCaption("caption-1"))));
        when(localVector.storeBatch(any(), any(), any())).thenReturn(Mono.just(List.of("vec-1")));
        when(localVector.deleteBatch(memoryId, List.of("vec-1")))
                .thenReturn(Mono.error(cleanupFailure));

        var layer =
                new RawDataLayer(
                        List.of(documentProcessor), localCaption, failingStore, localVector, 64);

        assertThatThrownBy(
                        () ->
                                layer.extract(
                                                memoryId,
                                                TestDocumentContent.of(
                                                        "Doc", "text/plain", "body-1"),
                                                "DOCUMENT",
                                                Map.of())
                                        .block())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("persist failed")
                .satisfies(
                        throwable ->
                                assertThat(throwable.getSuppressed()).contains(cleanupFailure));
    }

    private static class RecordingMemoryStore implements MemoryStore {

        private final RecordingRawDataOperations rawDataOperations =
                new RecordingRawDataOperations();
        private final ItemOperations itemOperations = new InMemoryItemOperations();
        private final InsightOperations insightOperations = new InMemoryInsightOperations();
        private final ResourceOperations resourceOperations = new InMemoryResourceOperations();
        private List<MemoryResource> lastResources = List.of();
        private List<MemoryRawData> lastRawData = List.of();
        private List<String> lookupContentIds = List.of();

        @Override
        public RawDataOperations rawDataOperations() {
            return rawDataOperations;
        }

        @Override
        public ItemOperations itemOperations() {
            return itemOperations;
        }

        @Override
        public InsightOperations insightOperations() {
            return insightOperations;
        }

        @Override
        public ResourceOperations resourceOperations() {
            return resourceOperations;
        }

        @Override
        public void upsertRawDataWithResources(
                com.openmemind.ai.memory.core.data.MemoryId memoryId,
                List<MemoryResource> resources,
                List<MemoryRawData> rawDataList) {
            lastResources = resources == null ? List.of() : List.copyOf(resources);
            lastRawData = rawDataList == null ? List.of() : List.copyOf(rawDataList);
            MemoryStore.super.upsertRawDataWithResources(memoryId, resources, rawDataList);
            lookupContentIds = List.copyOf(rawDataOperations.lookupContentIds);
        }
    }

    @SuppressWarnings("unchecked")
    private static RawContentProcessor<TestDocumentContent> mockTestDocumentProcessor() {
        return (RawContentProcessor<TestDocumentContent>) mock(RawContentProcessor.class);
    }

    private static final class RecordingRawDataOperations extends InMemoryRawDataOperations {

        private final List<String> lookupContentIds = new ArrayList<>();

        @Override
        public Optional<MemoryRawData> getRawDataByContentId(
                com.openmemind.ai.memory.core.data.MemoryId id, String contentId) {
            lookupContentIds.add(contentId);
            return super.getRawDataByContentId(id, contentId);
        }
    }

    private static final class FailingMemoryStore extends RecordingMemoryStore {

        private final RuntimeException failure;

        private FailingMemoryStore(RuntimeException failure) {
            this.failure = failure;
        }

        @Override
        public void upsertRawDataWithResources(
                com.openmemind.ai.memory.core.data.MemoryId memoryId,
                List<MemoryResource> resources,
                List<MemoryRawData> rawDataList) {
            throw failure;
        }
    }
}
