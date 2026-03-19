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
package com.openmemind.ai.memory.core.rawdata.caption;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.extraction.rawdata.caption.CaptionGenerator;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.CharBoundary;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.Segment;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@DisplayName("CaptionGenerator Parallel Test")
class CaptionGeneratorChainTest {

    @Nested
    @DisplayName("generateForSegments Parallel Generation")
    class GenerateForSegmentsTests {

        @Test
        @DisplayName(
                "When there are multiple segments, captions should be generated in parallel,"
                        + " metadata should not contain previous_caption")
        void shouldGenerateCaptionsInParallel() {
            var receivedMetadata = new CopyOnWriteArrayList<Map<String, Object>>();

            CaptionGenerator generator =
                    (content, metadata) -> {
                        receivedMetadata.add(Map.copyOf(metadata));
                        return Mono.just("caption-for-" + content);
                    };

            var segments =
                    List.of(
                            new Segment("seg1", null, new CharBoundary(0, 4), Map.of()),
                            new Segment("seg2", null, new CharBoundary(4, 8), Map.of()));

            StepVerifier.create(generator.generateForSegments(segments))
                    .assertNext(
                            result -> {
                                assertThat(result).hasSize(2);
                                assertThat(result)
                                        .extracting(Segment::caption)
                                        .containsExactlyInAnyOrder(
                                                "caption-for-seg1", "caption-for-seg2");
                            })
                    .verifyComplete();

            assertThat(receivedMetadata).hasSize(2);
            receivedMetadata.forEach(
                    meta -> assertThat(meta).doesNotContainKey("previous_caption"));
        }

        @Test
        @DisplayName(
                "When there are three segments, all should be generated in parallel, with no chain"
                        + " dependency")
        void shouldGenerateAllSegmentsIndependently() {
            var receivedMetadata = new CopyOnWriteArrayList<Map<String, Object>>();

            CaptionGenerator generator =
                    (content, metadata) -> {
                        receivedMetadata.add(Map.copyOf(metadata));
                        return Mono.just("caption-" + content);
                    };

            var segments =
                    List.of(
                            new Segment("A", null, new CharBoundary(0, 1), Map.of()),
                            new Segment("B", null, new CharBoundary(1, 2), Map.of()),
                            new Segment("C", null, new CharBoundary(2, 3), Map.of()));

            StepVerifier.create(generator.generateForSegments(segments))
                    .assertNext(
                            result -> {
                                assertThat(result).hasSize(3);
                                assertThat(result)
                                        .extracting(Segment::caption)
                                        .containsExactlyInAnyOrder(
                                                "caption-A", "caption-B", "caption-C");
                            })
                    .verifyComplete();

            assertThat(receivedMetadata).hasSize(3);
            receivedMetadata.forEach(
                    meta -> assertThat(meta).doesNotContainKey("previous_caption"));
        }

        @Test
        @DisplayName("When there is a single segment, previous_caption should not be injected")
        void shouldNotInjectForSingleSegment() {
            var receivedMetadata = new CopyOnWriteArrayList<Map<String, Object>>();

            CaptionGenerator generator =
                    (content, metadata) -> {
                        receivedMetadata.add(Map.copyOf(metadata));
                        return Mono.just("single-caption");
                    };

            var segments = List.of(new Segment("only", null, new CharBoundary(0, 4), Map.of()));

            StepVerifier.create(generator.generateForSegments(segments))
                    .assertNext(
                            result -> {
                                assertThat(result).hasSize(1);
                                assertThat(result.getFirst().caption()).isEqualTo("single-caption");
                            })
                    .verifyComplete();

            assertThat(receivedMetadata.getFirst()).doesNotContainKey("previous_caption");
        }

        @Test
        @DisplayName("An empty list should return an empty list")
        void shouldReturnEmptyForEmptyList() {
            CaptionGenerator generator = (content, metadata) -> Mono.just("unreachable");

            StepVerifier.create(generator.generateForSegments(List.of()))
                    .assertNext(result -> assertThat(result).isEmpty())
                    .verifyComplete();
        }
    }
}
