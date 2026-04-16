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
package com.openmemind.ai.memory.core.tracing.decorator;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.data.enums.MemoryItemType;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.extraction.item.graph.ItemGraphMaterializer;
import com.openmemind.ai.memory.core.extraction.item.support.ExtractedMemoryEntry;
import com.openmemind.ai.memory.core.support.RecordingMemoryObserver;
import com.openmemind.ai.memory.core.tracing.MemorySpanNames;
import com.openmemind.ai.memory.core.tracing.ObservationContext;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class TracingItemGraphMaterializerTest {

    @Test
    void tracingDecoratorShouldEmitGraphMaterializationSpan() {
        var observer = new RecordingMemoryObserver();
        ItemGraphMaterializer materializer =
                new TracingItemGraphMaterializer(
                        (memoryId, items, entries) -> Mono.empty(), observer);

        StepVerifier.create(
                        materializer.materialize(
                                DefaultMemoryId.of("user-1", "agent-1"),
                                List.of(newItem(101L)),
                                List.of(newEntry())))
                .verifyComplete();

        assertThat(observer.monoContexts())
                .extracting(ObservationContext::spanName)
                .contains(MemorySpanNames.GRAPH_MATERIALIZE);
    }

    private static MemoryItem newItem(Long id) {
        return new MemoryItem(
                id,
                "user-1:agent-1",
                "User discussed OpenAI deployment",
                MemoryScope.USER,
                MemoryCategory.EVENT,
                "conversation",
                "vector-" + id,
                "raw-" + id,
                "hash-" + id,
                Instant.parse("2026-04-16T10:00:00Z"),
                Instant.parse("2026-04-16T10:00:00Z"),
                Map.of(),
                Instant.parse("2026-04-16T10:00:00Z"),
                MemoryItemType.FACT);
    }

    private static ExtractedMemoryEntry newEntry() {
        return new ExtractedMemoryEntry(
                "User discussed OpenAI deployment",
                1.0f,
                Instant.parse("2026-04-16T10:00:00Z"),
                Instant.parse("2026-04-16T10:00:00Z"),
                "raw-1",
                "hash-1",
                List.of(),
                Map.of(),
                MemoryItemType.FACT,
                "event");
    }
}
