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
package com.openmemind.ai.memory.core.extraction.item.dedup;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.extraction.item.support.ExtractedMemoryEntry;
import com.openmemind.ai.memory.core.support.RecordingObservationRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class CompositeDeduplicatorObservationTest {

    @Test
    void deduplicatePublishesCompositeObservation() {
        var registry = new RecordingObservationRegistry();
        var result = new DeduplicationResult(List.of(), List.of());
        var deduplicator =
                new CompositeDeduplicator(
                        List.of(new StubDeduplicator("memind.extraction.item.dedup.hash", result)),
                        registry);
        var memoryId = DefaultMemoryId.of("user1", "agent1");

        StepVerifier.create(deduplicator.deduplicate(memoryId, List.of(entry())))
                .expectNext(result)
                .verifyComplete();

        assertThat(registry.observations()).hasSize(1);
        var observation = registry.observations().getFirst();
        assertThat(observation.observationName()).isEqualTo("memind.extraction.item.dedup");
        assertThat(observation.requestAttributes())
                .containsEntry("memind.memory_id", memoryId.toIdentifier());
    }

    private static ExtractedMemoryEntry entry() {
        return new ExtractedMemoryEntry(
                "User likes Java",
                0.9f,
                null,
                null,
                "raw-1",
                null,
                List.of(),
                java.util.Map.of(),
                null,
                null);
    }

    private record StubDeduplicator(String observationName, DeduplicationResult result)
            implements MemoryItemDeduplicator {

        @Override
        public Mono<DeduplicationResult> deduplicate(
                MemoryId memoryId, List<ExtractedMemoryEntry> entries) {
            return Mono.just(result);
        }
    }
}
