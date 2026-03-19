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
package com.openmemind.ai.memory.core.retrieval.rerank;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

@DisplayName("Reranker Unit Test")
class RerankerTest {

    @Nested
    @DisplayName("NoopReranker")
    class NoopRerankerTests {

        private final NoopReranker reranker = new NoopReranker();

        @Test
        @DisplayName("Should pass through all results (when not exceeding topK)")
        void shouldPassThroughWhenWithinTopK() {
            List<ScoredResult> results =
                    List.of(
                            new ScoredResult(ScoredResult.SourceType.ITEM, "1", "text1", 0.9f, 0.9),
                            new ScoredResult(
                                    ScoredResult.SourceType.ITEM, "2", "text2", 0.8f, 0.8));

            StepVerifier.create(reranker.rerank("query", results, 5))
                    .assertNext(
                            reranked -> {
                                assertThat(reranked).hasSize(2);
                                assertThat(reranked).isEqualTo(results);
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should truncate to topK")
        void shouldTruncateToTopK() {
            List<ScoredResult> results =
                    List.of(
                            new ScoredResult(ScoredResult.SourceType.ITEM, "1", "text1", 0.9f, 0.9),
                            new ScoredResult(ScoredResult.SourceType.ITEM, "2", "text2", 0.8f, 0.8),
                            new ScoredResult(
                                    ScoredResult.SourceType.ITEM, "3", "text3", 0.7f, 0.7));

            StepVerifier.create(reranker.rerank("query", results, 2))
                    .assertNext(
                            reranked -> {
                                assertThat(reranked).hasSize(2);
                                assertThat(reranked.getFirst().sourceId()).isEqualTo("1");
                                assertThat(reranked.getLast().sourceId()).isEqualTo("2");
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Empty results should pass through")
        void shouldHandleEmptyResults() {
            StepVerifier.create(reranker.rerank("query", List.of(), 5))
                    .assertNext(reranked -> assertThat(reranked).isEmpty())
                    .verifyComplete();
        }
    }
}
