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
package com.openmemind.ai.memory.core.retrieval.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.retrieval.RetrievalResult;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("CaffeineRetrievalCache Unit Test")
class CaffeineRetrievalCacheTest {

    private CaffeineRetrievalCache cache;
    private final MemoryId memoryId = DefaultMemoryId.of("user1", "agent1");

    @BeforeEach
    void setUp() {
        cache = new CaffeineRetrievalCache();
    }

    @Nested
    @DisplayName("Basic Operations")
    class BasicOperationTests {

        @Test
        @DisplayName("put then get should return cached result")
        void shouldReturnCachedResult() {
            RetrievalResult result =
                    new RetrievalResult(
                            List.of(
                                    new ScoredResult(
                                            ScoredResult.SourceType.ITEM, "1", "test", 0.9f, 0.9)),
                            List.of(),
                            List.of(),
                            List.of(),
                            "deep_retrieval",
                            "query");

            cache.put(memoryId, "qhash", "chash", result);

            Optional<RetrievalResult> cached = cache.get(memoryId, "qhash", "chash");
            assertThat(cached).isPresent();
            assertThat(cached.get().items()).hasSize(1);
            assertThat(cached.get().items().getFirst().sourceId()).isEqualTo("1");
        }

        @Test
        @DisplayName("Uncached key should return empty")
        void shouldReturnEmptyForMissingKey() {
            Optional<RetrievalResult> cached = cache.get(memoryId, "missing", "missing");
            assertThat(cached).isEmpty();
        }

        @Test
        @DisplayName("Different queryHash should not hit")
        void shouldNotHitDifferentQueryHash() {
            RetrievalResult result = RetrievalResult.empty("deep_retrieval", "query");
            cache.put(memoryId, "hash1", "config", result);

            assertThat(cache.get(memoryId, "hash2", "config")).isEmpty();
        }

        @Test
        @DisplayName("Different configHash should not hit")
        void shouldNotHitDifferentConfigHash() {
            RetrievalResult result = RetrievalResult.empty("deep_retrieval", "query");
            cache.put(memoryId, "query", "config1", result);

            assertThat(cache.get(memoryId, "query", "config2")).isEmpty();
        }
    }

    @Nested
    @DisplayName("Invalidation")
    class InvalidationTests {

        @Test
        @DisplayName("invalidate should clear all caches for the specified memoryId")
        void shouldInvalidateByMemoryId() {
            cache.put(memoryId, "q1", "c1", RetrievalResult.empty("s", "q"));
            cache.put(memoryId, "q2", "c2", RetrievalResult.empty("s", "q"));

            MemoryId otherId = DefaultMemoryId.of("user2", "agent2");
            cache.put(otherId, "q1", "c1", RetrievalResult.empty("s", "q"));

            cache.invalidate(memoryId);

            assertThat(cache.get(memoryId, "q1", "c1")).isEmpty();
            assertThat(cache.get(memoryId, "q2", "c2")).isEmpty();
            // Other memoryId is not affected
            assertThat(cache.get(otherId, "q1", "c1")).isPresent();
        }
    }
}
