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
package com.openmemind.ai.memory.core.extraction.thread;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.data.enums.MemoryThreadEventType;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadLifecycleStatus;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadObjectState;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadType;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadEvent;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadProjection;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ThreadHeadlineFormatterTest {

    @Test
    void relationshipGroupHeadlineIsDeterministicWithoutEnrichment() {
        assertThat(
                        ThreadHeadlineFormatter.format(
                                "relationship_group",
                                "person:alice|person:bob|person:carol"))
                .isEqualTo("Alice, Bob, and Carol");
    }

    @Test
    void reducerFallsBackToDeterministicHeadlineBeforeRawContent() {
        ThreadStructuralReducer reducer =
                new ThreadStructuralReducer(
                        new ThreadMaterializationPolicy(
                                "test-policy",
                                0.78d,
                                0.70d,
                                4,
                                Duration.ofDays(7),
                                Duration.ofDays(21)));

        MemoryThreadProjection reduced =
                reducer.reduce(
                        projection(),
                        List.of(
                                new MemoryThreadEvent(
                                        "memory-user-agent",
                                        "relationship:relationship_group:person:alice|person:bob|person:carol",
                                        "relationship:relationship_group:person:alice|person:bob|person:carol:observation:301",
                                        1L,
                                        MemoryThreadEventType.OBSERVATION,
                                        Instant.parse("2026-04-20T09:00:00Z"),
                                        Map.of(
                                                "summary",
                                                "Alice, Bob, and Carol planned the launch together."),
                                        1,
                                        false,
                                        1.0d,
                                        Instant.parse("2026-04-20T09:00:00Z"))),
                        List.of(),
                        Instant.parse("2026-04-20T09:00:00Z"));

        assertThat(reduced.headline()).isEqualTo("Alice, Bob, and Carol");
    }

    private static MemoryThreadProjection projection() {
        return new MemoryThreadProjection(
                "memory-user-agent",
                "relationship:relationship_group:person:alice|person:bob|person:carol",
                MemoryThreadType.RELATIONSHIP,
                "relationship_group",
                "person:alice|person:bob|person:carol",
                "person:alice|person:bob|person:carol",
                MemoryThreadLifecycleStatus.ACTIVE,
                MemoryThreadObjectState.UNCERTAIN,
                "Alice, Bob, and Carol planned the launch together.",
                Map.of(),
                1,
                Instant.parse("2026-04-20T09:00:00Z"),
                null,
                null,
                null,
                0L,
                0L,
                Instant.parse("2026-04-20T09:00:00Z"),
                Instant.parse("2026-04-20T09:00:00Z"));
    }
}
