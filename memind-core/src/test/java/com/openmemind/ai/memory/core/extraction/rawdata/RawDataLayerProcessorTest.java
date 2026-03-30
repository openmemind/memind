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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openmemind.ai.memory.core.extraction.rawdata.caption.CaptionGenerator;
import com.openmemind.ai.memory.core.extraction.rawdata.content.ConversationContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.ToolCallContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.tool.ToolCallRecord;
import com.openmemind.ai.memory.core.extraction.rawdata.processor.ConversationContentProcessor;
import com.openmemind.ai.memory.core.extraction.rawdata.processor.ToolCallContentProcessor;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.CharBoundary;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.MessageBoundary;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.Segment;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.SegmentRuntimeContext;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.store.rawdata.RawDataOperations;
import com.openmemind.ai.memory.core.vector.MemoryVector;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
    }

    @Nested
    @DisplayName("getProcessor routing")
    class GetProcessorRouting {

        @Test
        @DisplayName("ConversationContent should route to ConversationContentProcessor")
        void conversationContentUsesConversationProcessor() {
            var convProcessor = mock(ConversationContentProcessor.class);
            when(convProcessor.contentClass()).thenReturn(ConversationContent.class);
            when(convProcessor.chunk(any(ConversationContent.class)))
                    .thenReturn(Mono.just(List.of()));
            when(convProcessor.captionGenerator()).thenReturn(defaultCaption);

            var toolProcessor = mock(ToolCallContentProcessor.class);
            when(toolProcessor.contentClass()).thenReturn(ToolCallContent.class);

            var layer =
                    new RawDataLayer(
                            List.of(convProcessor, toolProcessor), defaultCaption, store, vector);

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

            var toolProcessor = mock(ToolCallContentProcessor.class);
            when(toolProcessor.contentClass()).thenReturn(ToolCallContent.class);
            when(toolProcessor.chunk(any(ToolCallContent.class))).thenReturn(Mono.just(List.of()));
            when(toolProcessor.captionGenerator()).thenReturn(defaultCaption);

            var layer =
                    new RawDataLayer(
                            List.of(convProcessor, toolProcessor), defaultCaption, store, vector);

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
        @DisplayName("Unknown RawContent type should throw IllegalArgumentException")
        void unknownContentTypeThrows() {
            var convProcessor = mock(ConversationContentProcessor.class);
            when(convProcessor.contentClass()).thenReturn(ConversationContent.class);

            var layer = new RawDataLayer(List.of(convProcessor), defaultCaption, store, vector);

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

            var layer = new RawDataLayer(List.of(convProcessor), defaultCaption, store, vector);

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
    }

    @Test
    @DisplayName("processSegment should forward explicit language to caption generation")
    void processSegment_forwards_language_to_caption_generation() {
        var layer = new RawDataLayer(List.of(), defaultCaption, store, vector);
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
    @DisplayName("processSegment keeps runtime context transient while using it for rawdata timing")
    void processSegmentKeepsRuntimeContextTransientWhileUsingItForRawdataTiming() {
        var layer = new RawDataLayer(List.of(), defaultCaption, store, vector);
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
        assertThat(result.rawDataList().getFirst().startTime())
                .isEqualTo(Instant.parse("2026-03-27T02:17:00Z"));
        assertThat(result.rawDataList().getFirst().endTime())
                .isEqualTo(Instant.parse("2026-03-27T02:18:00Z"));
    }
}
