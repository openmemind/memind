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

import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.data.enums.MemoryItemType;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadType;
import com.openmemind.ai.memory.core.store.graph.ItemEntityMention;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ThreadIntakeSignalExtractorTest {

    private final ThreadIntakeSignalExtractor extractor =
            new ThreadIntakeSignalExtractor(ThreadMaterializationPolicy.v1());

    @Test
    void extractsRelationshipSignalFromDyadicSpecialAndPersonEvidence() {
        MemoryItem item = item(301L, "Alice asked me whether I had finished the draft.");

        List<ThreadIntakeSignal> signals =
                extractor.extract(
                        item,
                        List.of(mention(301L, "special:user"), mention(301L, "person:alice")),
                        List.of(),
                        List.of());

        assertThat(signals)
                .singleElement()
                .satisfies(
                        signal -> {
                            assertThat(signal.threadType())
                                    .isEqualTo(MemoryThreadType.RELATIONSHIP);
                            assertThat(signal.eventTime())
                                    .isEqualTo(Instant.parse("2026-04-20T09:00:00Z"));
                            assertThat(signal.anchorCandidates())
                                    .singleElement()
                                    .satisfies(
                                            anchor -> {
                                                assertThat(anchor.anchorKind())
                                                        .isEqualTo("relationship");
                                                assertThat(anchor.participants())
                                                        .containsExactlyInAnyOrder(
                                                                "special:user", "person:alice");
                                            });
                            assertThat(signal.eligibility().scoreFor(MemoryThreadType.RELATIONSHIP))
                                    .isGreaterThanOrEqualTo(0.70d);
                        });
    }

    @Test
    void extractsTopicSignalFromConceptMentionWhenNoRelationshipPairExists() {
        MemoryItem item = item(302L, "The user booked a long trip to Japan.");

        List<ThreadIntakeSignal> signals =
                extractor.extract(
                        item, List.of(mention(302L, "concept:travel")), List.of(), List.of());

        assertThat(signals)
                .singleElement()
                .satisfies(
                        signal -> {
                            assertThat(signal.threadType()).isEqualTo(MemoryThreadType.TOPIC);
                            assertThat(signal.anchorCandidates())
                                    .singleElement()
                                    .extracting(ThreadIntakeSignal.AnchorCandidate::anchorKey)
                                    .isEqualTo("concept:travel");
                        });
    }

    @Test
    void fallsBackToObservedAtWhenOccurredAtIsMissing() {
        MemoryItem item =
                new MemoryItem(
                        303L,
                        "memory-user-agent",
                        "The user mentioned Alice again.",
                        MemoryScope.USER,
                        MemoryCategory.PROFILE,
                        "conversation",
                        "vec-303",
                        "raw-303",
                        "hash-303",
                        null,
                        null,
                        null,
                        null,
                        Instant.parse("2026-04-20T10:15:00Z"),
                        Map.of(),
                        Instant.parse("2026-04-20T10:20:00Z"),
                        MemoryItemType.FACT);

        List<ThreadIntakeSignal> signals =
                extractor.extract(
                        item,
                        List.of(mention(303L, "special:self"), mention(303L, "person:alice")),
                        List.of(),
                        List.of());

        assertThat(signals)
                .singleElement()
                .extracting(ThreadIntakeSignal::eventTime)
                .isEqualTo(Instant.parse("2026-04-20T10:15:00Z"));
    }

    private static MemoryItem item(Long id, String content) {
        return new MemoryItem(
                id,
                "memory-user-agent",
                content,
                MemoryScope.USER,
                MemoryCategory.EVENT,
                "conversation",
                "vec-" + id,
                "raw-" + id,
                "hash-" + id,
                Instant.parse("2026-04-20T09:00:00Z"),
                Instant.parse("2026-04-20T09:00:00Z"),
                Map.of(),
                Instant.parse("2026-04-20T09:00:00Z"),
                MemoryItemType.FACT);
    }

    private static ItemEntityMention mention(long itemId, String entityKey) {
        return new ItemEntityMention(
                "memory-user-agent",
                itemId,
                entityKey,
                1.0f,
                Map.of("source", "test"),
                Instant.parse("2026-04-20T09:00:00Z"));
    }
}
