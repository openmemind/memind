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
import com.openmemind.ai.memory.core.extraction.thread.marker.ThreadSemanticMarkerReader;
import com.openmemind.ai.memory.core.store.graph.EntityCooccurrence;
import com.openmemind.ai.memory.core.store.graph.ItemEntityMention;
import com.openmemind.ai.memory.core.store.graph.ItemLink;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ThreadAnchorProviderTest {

    private final RelationshipAnchorProvider relationshipProvider =
            new RelationshipAnchorProvider();
    private final GroupRelationshipAnchorProvider groupRelationshipProvider =
            new GroupRelationshipAnchorProvider();
    private final TopicAnchorProvider topicProvider = new TopicAnchorProvider();
    private final MetadataCanonicalRefAnchorProvider metadataProvider =
            new MetadataCanonicalRefAnchorProvider();
    private final ThreadAnchorCanonicalizer canonicalizer = new ThreadAnchorCanonicalizer();

    @Test
    void relationshipProviderProducesSignalThatCanonicalizesSpecialUserToSpecialSelf() {
        MemoryItem item = item(401L, "Alice followed up with the user.");

        List<ThreadIntakeSignal> signals =
                relationshipProvider.extract(
                        context(
                                item,
                                List.of(
                                        mention(401L, "special:user"),
                                        mention(401L, "person:alice")),
                                List.of(),
                                List.of()));

        assertThat(signals)
                .singleElement()
                .satisfies(
                        signal ->
                                assertThat(
                                                canonicalizer
                                                        .canonicalize(signal)
                                                        .orElseThrow()
                                                        .threadKey())
                                        .isEqualTo(
                                                "relationship:relationship:special:self|person:alice"));
    }

    @Test
    void topicProviderEmitsDistinctConceptAnchorsInStableOrder() {
        MemoryItem item = item(402L, "The user is planning Japan travel.");

        List<ThreadIntakeSignal> signals =
                topicProvider.extract(
                        context(
                                item,
                                List.of(
                                        mention(402L, "concept:travel"),
                                        mention(402L, "concept:japan")),
                                List.of(),
                                List.of()));

        assertThat(signals)
                .extracting(
                        signal -> signal.threadType(),
                        signal -> signal.anchorCandidates().getFirst().anchorKey())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                MemoryThreadType.TOPIC, "concept:japan"),
                        org.assertj.core.groups.Tuple.tuple(
                                MemoryThreadType.TOPIC, "concept:travel"));
    }

    @Test
    void groupRelationshipProviderEmitsSignalForStableThreePersonInteraction() {
        MemoryItem item = item(4021L, "Alice, Bob, and Carol planned the launch together.");

        List<ThreadIntakeSignal> signals =
                groupRelationshipProvider.extract(
                        context(
                                item,
                                List.of(
                                        mention(4021L, "person:carol"),
                                        mention(4021L, "person:alice"),
                                        mention(4021L, "person:bob")),
                                List.of(),
                                List.of()));

        assertThat(signals)
                .singleElement()
                .satisfies(
                        signal -> {
                            assertThat(signal.threadType())
                                    .isEqualTo(MemoryThreadType.RELATIONSHIP);
                            assertThat(signal.anchorCandidates())
                                    .singleElement()
                                    .satisfies(
                                            anchor -> {
                                                assertThat(anchor.anchorKind())
                                                        .isEqualTo("relationship_group");
                                                assertThat(anchor.participants())
                                                        .containsExactly(
                                                                "person:alice",
                                                                "person:bob",
                                                                "person:carol");
                                            });
                        });
    }

    @Test
    void canonicalizerBuildsStableGroupAnchorKey() {
        ThreadIntakeSignal.AnchorCandidate candidate =
                new ThreadIntakeSignal.AnchorCandidate(
                        "relationship_group",
                        null,
                        List.of("person:carol", "person:alice", "person:bob", "person:bob"),
                        1.0d);

        assertThat(canonicalizer.canonicalize(MemoryThreadType.RELATIONSHIP, candidate))
                .get()
                .extracting(
                        ThreadAnchorCanonicalizer.CanonicalThreadAnchor::anchorKind,
                        ThreadAnchorCanonicalizer.CanonicalThreadAnchor::anchorKey)
                .containsExactly("relationship_group", "person:alice|person:bob|person:carol");
    }

    @Test
    void extractorRecordsProviderHitOnlyWhenProviderEmitsSignals() {
        RecordingThreadDerivationMetrics metrics = new RecordingThreadDerivationMetrics();
        ThreadIntakeSignalExtractor extractor =
                new ThreadIntakeSignalExtractor(ThreadMaterializationPolicy.v1(), metrics);

        extractor.extract(
                item(4022L, "Alice, Bob, and Carol planned the launch together."),
                List.of(
                        mention(4022L, "person:alice"),
                        mention(4022L, "person:bob"),
                        mention(4022L, "person:carol")),
                List.of(),
                List.of());

        assertThat(metrics.providerHits()).contains("relationship_group");
    }

    @Test
    void metadataProviderAcceptsOnlyMilestoneOneRefTypes() {
        MemoryItem item =
                new MemoryItem(
                        403L,
                        "memory-user-agent",
                        "Project Alpha and the payment outage were both updated.",
                        MemoryScope.USER,
                        MemoryCategory.EVENT,
                        "conversation",
                        "vec-403",
                        "raw-403",
                        "hash-403",
                        Instant.parse("2026-04-20T12:00:00Z"),
                        Instant.parse("2026-04-20T12:00:00Z"),
                        Map.of(
                                "threadSemantics",
                                Map.of(
                                        "version",
                                        1,
                                        "canonicalRefs",
                                        List.of(
                                                Map.of("refType", "topic", "refKey", "travel"),
                                                Map.of("refType", "project", "refKey", "alpha"),
                                                Map.of(
                                                        "refType",
                                                        "case",
                                                        "refKey",
                                                        "payment-outage"),
                                                Map.of("refType", "person", "refKey", "alice")))),
                        Instant.parse("2026-04-20T12:00:00Z"),
                        MemoryItemType.FACT);

        List<ThreadIntakeSignal> signals =
                metadataProvider.extract(context(item, List.of(), List.of(), List.of()));

        assertThat(signals)
                .extracting(
                        ThreadIntakeSignal::threadType,
                        signal -> signal.anchorCandidates().getFirst().anchorKind(),
                        signal -> signal.anchorCandidates().getFirst().anchorKey())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                MemoryThreadType.WORK, "project", "project:alpha"),
                        org.assertj.core.groups.Tuple.tuple(
                                MemoryThreadType.CASE, "case", "case:payment-outage"),
                        org.assertj.core.groups.Tuple.tuple(
                                MemoryThreadType.TOPIC, "topic", "concept:travel"));
    }

    @Test
    void metadataProviderDoesNotUnlockWorkCaseCreationWithoutBoundMarker() {
        MemoryItem item =
                new MemoryItem(
                        404L,
                        "memory-user-agent",
                        "Project Alpha and the payment outage both got an update.",
                        MemoryScope.USER,
                        MemoryCategory.EVENT,
                        "conversation",
                        "vec-404",
                        "raw-404",
                        "hash-404",
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

        List<ThreadIntakeSignal> signals =
                metadataProvider.extract(context(item, List.of(), List.of(), List.of()));

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

    private static ThreadAnchorContext context(
            MemoryItem item,
            List<ItemEntityMention> mentions,
            List<ItemLink> adjacentLinks,
            List<EntityCooccurrence> cooccurrences) {
        return new ThreadAnchorContext(
                item,
                mentions,
                adjacentLinks,
                cooccurrences,
                ThreadSemanticMarkerReader.read(item.metadata()),
                ThreadEventTimeResolver.resolve(item));
    }

    private static MemoryItem item(long itemId, String content) {
        return new MemoryItem(
                itemId,
                "memory-user-agent",
                content,
                MemoryScope.USER,
                MemoryCategory.EVENT,
                "conversation",
                "vec-" + itemId,
                "raw-" + itemId,
                "hash-" + itemId,
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
