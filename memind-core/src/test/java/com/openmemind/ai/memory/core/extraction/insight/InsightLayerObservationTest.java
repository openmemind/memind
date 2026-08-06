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
package com.openmemind.ai.memory.core.extraction.insight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.MemoryInsightType;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.enums.InsightAnalysisMode;
import com.openmemind.ai.memory.core.data.enums.MemoryItemType;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.extraction.insight.scheduler.InsightBuildScheduler;
import com.openmemind.ai.memory.core.extraction.result.MemoryItemResult;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.store.insight.InsightOperations;
import com.openmemind.ai.memory.core.support.RecordingObservationRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class InsightLayerObservationTest {

    @Test
    void extractPublishesInsightObservation() {
        var registry = new RecordingObservationRegistry();
        var store = mock(MemoryStore.class);
        var insightOperations = mock(InsightOperations.class);
        var scheduler = mock(InsightBuildScheduler.class);
        var memoryId = DefaultMemoryId.of("user1", "agent1");
        var insightType =
                new MemoryInsightType(
                        1L,
                        "profile",
                        "Profile",
                        null,
                        List.of("profile"),
                        400,
                        null,
                        null,
                        null,
                        InsightAnalysisMode.BRANCH,
                        null,
                        MemoryScope.USER);
        var item =
                new MemoryItem(
                        1L,
                        memoryId.toIdentifier(),
                        "User likes Java",
                        MemoryScope.USER,
                        null,
                        "conversation",
                        "vector-1",
                        "raw-1",
                        "hash-1",
                        Instant.parse("2026-03-20T00:00:00Z"),
                        null,
                        Map.of("insightTypes", List.of("profile")),
                        Instant.parse("2026-03-20T00:00:00Z"),
                        MemoryItemType.FACT);
        when(store.insightOperations()).thenReturn(insightOperations);
        when(insightOperations.listInsightTypes()).thenReturn(List.of(insightType));
        var layer = new InsightLayer(store, scheduler, registry);

        StepVerifier.create(
                        layer.extract(
                                memoryId,
                                new MemoryItemResult(List.of(item), List.of(insightType)),
                                "English"))
                .assertNext(result -> assertThat(result.isEmpty()).isTrue())
                .verifyComplete();

        assertThat(registry.observations()).hasSize(1);
        var observation = registry.observations().getFirst();
        assertThat(observation.observationName()).isEqualTo("memind.extraction.insight");
        assertThat(observation.requestAttributes())
                .containsEntry("memind.memory_id", memoryId.toIdentifier());
    }
}
