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
import com.openmemind.ai.memory.core.data.enums.MemoryThreadEventType;
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
    void extractorAddsRelationshipGroupSignalForStableThreePersonInteraction() {
        MemoryItem item = item(3011L, "Alice, Bob, and Carol coordinated the launch.");

        List<ThreadIntakeSignal> signals =
                extractor.extract(
                        item,
                        List.of(
                                mention(3011L, "person:carol"),
                                mention(3011L, "person:alice"),
                                mention(3011L, "person:bob")),
                        List.of(),
                        List.of());

        assertThat(signals)
                .filteredOn(signal -> signal.threadType() == MemoryThreadType.RELATIONSHIP)
                .extracting(signal -> signal.anchorCandidates().getFirst().anchorKind())
                .contains("relationship_group");
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
    void emitsDistinctTopicSignalsForMultipleConceptMentions() {
        MemoryItem item = item(304L, "The user booked a trip to Japan and studied the itinerary.");

        List<ThreadIntakeSignal> signals =
                extractor.extract(
                        item,
                        List.of(
                                mention(304L, "concept:travel"),
                                mention(304L, "concept:japan"),
                                mention(304L, "concept:travel")),
                        List.of(),
                        List.of());

        assertThat(signals)
                .filteredOn(signal -> signal.threadType() == MemoryThreadType.TOPIC)
                .extracting(signal -> signal.anchorCandidates().getFirst().anchorKey())
                .containsExactly("concept:japan", "concept:travel");
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

    @Test
    void ignoresSignalsWhenItemHasNoAuthoritativeEventTime() {
        MemoryItem item =
                new MemoryItem(
                        305L,
                        "memory-user-agent",
                        "The user mentioned travel with no timestamps.",
                        MemoryScope.USER,
                        MemoryCategory.EVENT,
                        "conversation",
                        "vec-305",
                        "raw-305",
                        "hash-305",
                        null,
                        null,
                        null,
                        null,
                        null,
                        Map.of(),
                        null,
                        MemoryItemType.FACT);

        List<ThreadIntakeSignal> signals =
                extractor.extract(
                        item, List.of(mention(305L, "concept:travel")), List.of(), List.of());

        assertThat(signals).isEmpty();
    }

    @Test
    void nonEventLikeItemsDoNotProduceGroupRelationshipSignals() {
        MemoryItem item =
                new MemoryItem(
                        3051L,
                        "memory-user-agent",
                        "Alice, Bob, and Carol are people the user knows.",
                        MemoryScope.USER,
                        MemoryCategory.PROFILE,
                        "conversation",
                        "vec-3051",
                        "raw-3051",
                        "hash-3051",
                        Instant.parse("2026-04-20T10:30:00Z"),
                        Instant.parse("2026-04-20T10:30:00Z"),
                        Map.of(),
                        Instant.parse("2026-04-20T10:30:00Z"),
                        MemoryItemType.FACT);

        List<ThreadIntakeSignal> signals =
                extractor.extract(
                        item,
                        List.of(
                                mention(3051L, "person:alice"),
                                mention(3051L, "person:bob"),
                                mention(3051L, "person:carol")),
                        List.of(),
                        List.of());

        assertThat(signals)
                .filteredOn(signal -> signal.threadType() == MemoryThreadType.RELATIONSHIP)
                .extracting(signal -> signal.anchorCandidates().getFirst().anchorKind())
                .doesNotContain("relationship_group");
    }

    @Test
    void extractsMetadataBackedWorkSignalFromThreadSemantics() {
        MemoryItem item =
                new MemoryItem(
                        306L,
                        "memory-user-agent",
                        "Project Alpha moved from planning into implementation.",
                        MemoryScope.USER,
                        MemoryCategory.EVENT,
                        "conversation",
                        "vec-306",
                        "raw-306",
                        "hash-306",
                        Instant.parse("2026-04-20T11:00:00Z"),
                        Instant.parse("2026-04-20T11:00:00Z"),
                        Map.of(
                                "threadSemantics",
                                Map.of(
                                        "version",
                                        1,
                                        "markers",
                                        List.of(
                                                Map.of(
                                                        "type",
                                                        "STATE_CHANGE",
                                                        "objectRef",
                                                        "project:alpha",
                                                        "fromState",
                                                        "planning",
                                                        "toState",
                                                        "implementation",
                                                        "summary",
                                                        "Project Alpha entered implementation")),
                                        "canonicalRefs",
                                        List.of(
                                                Map.of("refType", "project", "refKey", "Alpha"),
                                                Map.of("refType", "person", "refKey", "Alice")),
                                        "continuityLinks",
                                        List.of(
                                                Map.of(
                                                        "linkType",
                                                        "CONTINUES",
                                                        "targetItemId",
                                                        301L),
                                                Map.of(
                                                        "linkType",
                                                        "STARTS",
                                                        "targetItemId",
                                                        999L)))),
                        Instant.parse("2026-04-20T11:00:00Z"),
                        MemoryItemType.FACT);

        List<ThreadIntakeSignal> signals = extractor.extract(item, List.of(), List.of(), List.of());

        assertThat(signals)
                .filteredOn(signal -> signal.threadType() == MemoryThreadType.WORK)
                .singleElement()
                .satisfies(
                        signal -> {
                            assertThat(signal.anchorCandidates())
                                    .singleElement()
                                    .satisfies(
                                            anchor -> {
                                                assertThat(anchor.anchorKind())
                                                        .isEqualTo("project");
                                                assertThat(anchor.anchorKey())
                                                        .isEqualTo("project:alpha");
                                            });
                            assertThat(signal.semanticMarkers())
                                    .singleElement()
                                    .satisfies(
                                            marker -> {
                                                assertThat(marker.eventType())
                                                        .isEqualTo(
                                                                MemoryThreadEventType.STATE_CHANGE);
                                                assertThat(marker.objectRef())
                                                        .isEqualTo("project:alpha");
                                                assertThat(marker.attribute("fromState"))
                                                        .isEqualTo("planning");
                                                assertThat(marker.attribute("toState"))
                                                        .isEqualTo("implementation");
                                            });
                            assertThat(signal.canonicalRefs())
                                    .singleElement()
                                    .extracting(
                                            ThreadIntakeSignal.CanonicalRef::refType,
                                            ThreadIntakeSignal.CanonicalRef::refKey)
                                    .containsExactly("project", "Alpha");
                            assertThat(signal.supportingItemIds()).containsExactly(301L);
                        });
    }

    @Test
    void ignoresUnsupportedMetadataCanonicalRefTypes() {
        MemoryItem item =
                new MemoryItem(
                        307L,
                        "memory-user-agent",
                        "Alice mentioned OpenAI in passing.",
                        MemoryScope.USER,
                        MemoryCategory.EVENT,
                        "conversation",
                        "vec-307",
                        "raw-307",
                        "hash-307",
                        Instant.parse("2026-04-20T12:00:00Z"),
                        Instant.parse("2026-04-20T12:00:00Z"),
                        Map.of(
                                "threadSemantics",
                                Map.of(
                                        "version",
                                        1,
                                        "canonicalRefs",
                                        List.of(
                                                Map.of("refType", "person", "refKey", "Alice"),
                                                Map.of(
                                                        "refType",
                                                        "organization",
                                                        "refKey",
                                                        "OpenAI")))),
                        Instant.parse("2026-04-20T12:00:00Z"),
                        MemoryItemType.FACT);

        List<ThreadIntakeSignal> signals = extractor.extract(item, List.of(), List.of(), List.of());

        assertThat(signals).isEmpty();
    }

    @Test
    void unboundMeaningfulMarkerDoesNotUnlockMultipleMetadataAnchors() {
        MemoryItem item =
                new MemoryItem(
                        308L,
                        "memory-user-agent",
                        "Project Alpha and the payment outage both got an update.",
                        MemoryScope.USER,
                        MemoryCategory.EVENT,
                        "conversation",
                        "vec-308",
                        "raw-308",
                        "hash-308",
                        Instant.parse("2026-04-20T12:30:00Z"),
                        Instant.parse("2026-04-20T12:30:00Z"),
                        Map.of(
                                "threadSemantics",
                                Map.of(
                                        "version",
                                        1,
                                        "markers",
                                        List.of(
                                                Map.of(
                                                        "type",
                                                        "STATE_CHANGE",
                                                        "fromState",
                                                        "triage",
                                                        "toState",
                                                        "in_progress",
                                                        "summary",
                                                        "Status changed but the anchor is"
                                                                + " unspecified")),
                                        "canonicalRefs",
                                        List.of(
                                                Map.of("refType", "project", "refKey", "Alpha"),
                                                Map.of(
                                                        "refType",
                                                        "case",
                                                        "refKey",
                                                        "payment-outage")))),
                        Instant.parse("2026-04-20T12:30:00Z"),
                        MemoryItemType.FACT);

        List<ThreadIntakeSignal> signals = extractor.extract(item, List.of(), List.of(), List.of());

        assertThat(signals)
                .filteredOn(
                        signal ->
                                signal.threadType() == MemoryThreadType.WORK
                                        || signal.threadType() == MemoryThreadType.CASE)
                .hasSize(2)
                .allSatisfy(
                        signal -> {
                            assertThat(signal.semanticMarkers()).isEmpty();
                            assertThat(signal.eligibility().scoreFor(signal.threadType()))
                                    .isLessThan(
                                            ThreadMaterializationPolicy.v1()
                                                    .minimumCreateScoreAfterTwoHit());
                        });
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
