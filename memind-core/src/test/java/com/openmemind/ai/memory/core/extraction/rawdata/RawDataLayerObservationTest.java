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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.extraction.rawdata.caption.CaptionGenerator;
import com.openmemind.ai.memory.core.extraction.rawdata.content.ConversationContent;
import com.openmemind.ai.memory.core.extraction.rawdata.processor.ConversationContentProcessor;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.Segment;
import com.openmemind.ai.memory.core.store.InMemoryMemoryStore;
import com.openmemind.ai.memory.core.support.RecordingObservationRegistry;
import com.openmemind.ai.memory.core.vector.MemoryVector;
import com.openmemind.ai.memory.core.vector.VectorSearchResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class RawDataLayerObservationTest {

    @Test
    void extractPublishesRawDataObservationWithSegmentCount() {
        var registry = new RecordingObservationRegistry();
        var processor = mock(ConversationContentProcessor.class);
        var captionGenerator = mock(CaptionGenerator.class);
        var segment = Segment.single("hello");
        when(processor.contentClass()).thenReturn(ConversationContent.class);
        when(processor.chunk(any(ConversationContent.class)))
                .thenReturn(Mono.just(List.of(segment)));
        when(processor.captionGenerator()).thenReturn(captionGenerator);
        when(captionGenerator.generateForSegments(any(), any()))
                .thenReturn(Mono.just(List.of(segment.withCaption("hello caption"))));
        var layer =
                new RawDataLayer(
                        List.of(processor),
                        captionGenerator,
                        new InMemoryMemoryStore(),
                        new StubMemoryVector(),
                        64,
                        registry);
        var memoryId = DefaultMemoryId.of("user1", "agent1");

        StepVerifier.create(
                        layer.extract(
                                memoryId,
                                ConversationContent.builder().addUserMessage("hello").build(),
                                ConversationContent.TYPE,
                                Map.of()))
                .assertNext(result -> assertThat(result.segments()).hasSize(1))
                .verifyComplete();

        assertThat(registry.observations()).hasSize(1);
        var observation = registry.observations().getFirst();
        assertThat(observation.observationName()).isEqualTo("memind.extraction.rawdata");
        assertThat(observation.requestAttributes())
                .containsEntry("memind.memory_id", memoryId.toIdentifier());
        assertThat(observation.resultAttributes())
                .containsEntry("memind.extraction.segment_count", "1");
    }

    private static final class StubMemoryVector implements MemoryVector {

        @Override
        public Mono<String> store(MemoryId memoryId, String text, Map<String, Object> metadata) {
            return Mono.just("vector-1");
        }

        @Override
        public Mono<List<String>> storeBatch(
                MemoryId memoryId, List<String> texts, List<Map<String, Object>> metadataList) {
            return Mono.just(
                    java.util.stream.IntStream.range(0, texts.size())
                            .mapToObj(i -> "vector-" + i)
                            .toList());
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
            return Mono.just(List.of(0.1f));
        }

        @Override
        public Mono<List<List<Float>>> embedAll(List<String> texts) {
            return Mono.just(texts.stream().map(ignored -> List.of(0.1f)).toList());
        }
    }
}
