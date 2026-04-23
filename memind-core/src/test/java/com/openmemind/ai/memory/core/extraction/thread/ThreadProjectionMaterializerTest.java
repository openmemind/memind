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
import static org.assertj.core.groups.Tuple.tuple;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.data.enums.MemoryItemType;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadMembershipRole;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadEnrichmentInput;
import com.openmemind.ai.memory.core.store.graph.EntityCooccurrence;
import com.openmemind.ai.memory.core.store.graph.InMemoryGraphOperations;
import com.openmemind.ai.memory.core.store.graph.ItemEntityMention;
import com.openmemind.ai.memory.core.store.InMemoryMemoryStore;
import com.openmemind.ai.memory.core.store.item.InMemoryItemOperations;
import com.openmemind.ai.memory.core.support.TestMemoryIds;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ThreadProjectionMaterializerTest {

    @Test
    void secondHitCreatesTopicThreadAndRetroactivelyAdmitsFirstItem() {
        MemoryId memoryId = TestMemoryIds.userAgent();
        InMemoryMemoryStore store = new InMemoryMemoryStore();
        ThreadProjectionMaterializer materializer = materializer(store);

        store.itemOperations().insertItems(memoryId, List.of(item(301L, "The user planned a trip.")));
        store.graphOperations()
                .upsertItemEntityMentions(
                        memoryId, List.of(mention(memoryId, 301L, "concept:travel")));

        ThreadProjectionMaterializer.MaterializedProjection firstProjection =
                materializer.materializeUpTo(memoryId, 301L);

        assertThat(firstProjection.threads()).isEmpty();

        store.itemOperations().insertItems(memoryId, List.of(item(302L, "The user booked the trip.")));
        store.graphOperations()
                .upsertItemEntityMentions(
                        memoryId, List.of(mention(memoryId, 302L, "concept:travel")));

        ThreadProjectionMaterializer.MaterializedProjection secondProjection =
                materializer.materializeUpTo(memoryId, 302L);

        assertThat(secondProjection.threads())
                .singleElement()
                .extracting(thread -> thread.threadKey(), thread -> thread.memberCount())
                .containsExactly("topic:topic:concept:travel", 2L);
        assertThat(secondProjection.memberships())
                .filteredOn(membership -> membership.threadKey().equals("topic:topic:concept:travel"))
                .extracting(membership -> membership.itemId())
                .containsExactly(301L, 302L);
    }

    @Test
    void exactAnchorMembershipUsesWeightOnePointZero() {
        MemoryId memoryId = TestMemoryIds.userAgent();
        InMemoryMemoryStore store = new InMemoryMemoryStore();
        ThreadProjectionMaterializer materializer = materializer(store);

        store.itemOperations()
                .insertItems(
                        memoryId,
                        List.of(
                                item(311L, "The user planned a trip."),
                                item(312L, "The user booked the trip.")));
        store.graphOperations()
                .upsertItemEntityMentions(
                        memoryId,
                        List.of(
                                mention(memoryId, 311L, "concept:travel"),
                                mention(memoryId, 312L, "concept:travel")));

        ThreadProjectionMaterializer.MaterializedProjection projection =
                materializer.materializeUpTo(memoryId, 312L);

        assertThat(projection.memberships())
                .filteredOn(membership -> membership.threadKey().equals("topic:topic:concept:travel"))
                .extracting(
                        membership -> membership.itemId(),
                        membership -> membership.role(),
                        membership -> membership.primary(),
                        membership -> membership.relevanceWeight())
                .containsExactly(
                        tuple(311L, MemoryThreadMembershipRole.CORE, true, 1.0d),
                        tuple(312L, MemoryThreadMembershipRole.TRIGGER, true, 1.0d));
    }

    @Test
    void explicitContinuityAttachesWithoutExactAnchor() {
        MemoryId memoryId = TestMemoryIds.userAgent();
        InMemoryMemoryStore store = new InMemoryMemoryStore();
        ThreadProjectionMaterializer materializer = materializer(store);

        store.itemOperations()
                .insertItems(
                        memoryId,
                        List.of(
                                item(321L, "The user planned a trip."),
                                item(322L, "The user booked the trip."),
                                continuityItem(
                                        323L,
                                        "The user started the visa paperwork.",
                                        "concept:passport",
                                        322L)));
        store.graphOperations()
                .upsertItemEntityMentions(
                        memoryId,
                        List.of(
                                mention(memoryId, 321L, "concept:travel"),
                                mention(memoryId, 322L, "concept:travel"),
                                mention(memoryId, 323L, "concept:passport")));

        ThreadProjectionMaterializer.MaterializedProjection projection =
                materializer.materializeUpTo(memoryId, 323L);

        assertThat(projection.threads())
                .singleElement()
                .extracting(thread -> thread.threadKey(), thread -> thread.memberCount())
                .containsExactly("topic:topic:concept:travel", 3L);
        assertThat(projection.memberships())
                .filteredOn(membership -> membership.threadKey().equals("topic:topic:concept:travel"))
                .extracting(membership -> membership.itemId())
                .containsExactly(321L, 322L, 323L);
    }

    @Test
    void nonExactAttachmentUsesCompositeMatchScoreAsWeight() {
        MemoryId memoryId = TestMemoryIds.userAgent();
        InMemoryMemoryStore store = new InMemoryMemoryStore();
        ThreadProjectionMaterializer materializer = materializer(store);

        store.itemOperations()
                .insertItems(
                        memoryId,
                        List.of(
                                item(324L, "The user planned a trip."),
                                item(325L, "The user booked the trip."),
                                continuityItem(
                                        326L,
                                        "The user started the visa paperwork.",
                                        "concept:passport",
                                        325L)));
        store.graphOperations()
                .upsertItemEntityMentions(
                        memoryId,
                        List.of(
                                mention(memoryId, 324L, "concept:travel"),
                                mention(memoryId, 325L, "concept:travel"),
                                mention(memoryId, 326L, "concept:passport")));

        ThreadProjectionMaterializer.MaterializedProjection projection =
                materializer.materializeUpTo(memoryId, 326L);

        assertThat(projection.memberships())
                .filteredOn(membership -> membership.itemId() == 326L)
                .singleElement()
                .satisfies(
                        membership -> {
                            assertThat(membership.threadKey())
                                    .isEqualTo("topic:topic:concept:travel");
                            assertThat(membership.role())
                                    .isEqualTo(MemoryThreadMembershipRole.TRIGGER);
                            assertThat(membership.primary()).isTrue();
                            assertThat(membership.relevanceWeight()).isEqualTo(0.95d);
                        });
    }

    @Test
    void snapshotContainsEvidenceFacetWithSupportCountAndDominantFamilies() {
        MemoryId memoryId = TestMemoryIds.userAgent();
        InMemoryMemoryStore store = new InMemoryMemoryStore();
        ThreadProjectionMaterializer materializer = materializer(store);

        store.itemOperations()
                .insertItems(
                        memoryId,
                        List.of(
                                item(327L, "The user planned a trip."),
                                item(328L, "The user booked the trip."),
                                continuityItem(
                                        329L,
                                        "The user started the visa paperwork.",
                                        "concept:passport",
                                        328L)));
        store.graphOperations()
                .upsertItemEntityMentions(
                        memoryId,
                        List.of(
                                mention(memoryId, 327L, "concept:travel"),
                                mention(memoryId, 327L, "organization:delta-air"),
                                mention(memoryId, 328L, "concept:travel"),
                                mention(memoryId, 328L, "organization:delta-air"),
                                mention(memoryId, 329L, "concept:passport"),
                                mention(memoryId, 329L, "organization:delta-air")));

        ThreadProjectionMaterializer.MaterializedProjection projection =
                materializer.materializeUpTo(memoryId, 329L);

        assertThat(projection.threads())
                .singleElement()
                .satisfies(
                        thread -> {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> facets =
                                    (Map<String, Object>) thread.snapshotJson().get("facets");
                            @SuppressWarnings("unchecked")
                            Map<String, Object> evidence =
                                    (Map<String, Object>) facets.get("evidence");
                            assertThat(evidence)
                                    .containsEntry("supportCount", 2)
                                    .containsEntry(
                                            "dominantFamilies",
                                            List.of(
                                                    "explicit_continuity", "entity_support"));
                        });
    }

    @Test
    void metadataBackedWorkRequiresBoundMeaningfulMarker() {
        MemoryId memoryId = TestMemoryIds.userAgent();
        InMemoryMemoryStore store = new InMemoryMemoryStore();
        ThreadProjectionMaterializer materializer = materializer(store);

        store.itemOperations()
                .insertItems(
                        memoryId,
                        List.of(
                                metadataItem(331L, "Project Alpha was mentioned.", "project", "alpha", List.of()),
                                metadataItem(
                                        332L,
                                        "Project Alpha was mentioned again.",
                                        "project",
                                        "alpha",
                                        List.of())));

        ThreadProjectionMaterializer.MaterializedProjection refOnlyProjection =
                materializer.materializeUpTo(memoryId, 332L);

        assertThat(refOnlyProjection.threads()).isEmpty();

        store.itemOperations()
                .insertItems(
                        memoryId,
                        List.of(
                                metadataItem(
                                        333L,
                                        "Project Alpha moved into implementation.",
                                        "project",
                                        "alpha",
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
                                                        "Project Alpha entered implementation")))));

        ThreadProjectionMaterializer.MaterializedProjection boundMarkerProjection =
                materializer.materializeUpTo(memoryId, 333L);

        assertThat(boundMarkerProjection.threads())
                .singleElement()
                .extracting(thread -> thread.threadKey(), thread -> thread.memberCount())
                .containsExactly("work:project:project:alpha", 3L);
        assertThat(boundMarkerProjection.memberships())
                .filteredOn(membership -> membership.threadKey().equals("work:project:project:alpha"))
                .extracting(membership -> membership.itemId())
                .containsExactly(331L, 332L, 333L);
    }

    @Test
    void multipleExactTopicMatchesInSameItemAreIgnoredAsAmbiguous() {
        MemoryId memoryId = TestMemoryIds.userAgent();
        InMemoryMemoryStore store = new InMemoryMemoryStore();
        ThreadProjectionMaterializer materializer = materializer(store);

        store.itemOperations()
                .insertItems(
                        memoryId,
                        List.of(
                                item(341L, "The user planned a trip."),
                                item(342L, "The user booked the trip."),
                                item(343L, "The user planned a Japan itinerary."),
                                item(344L, "The user booked the Japan tickets."),
                                item(
                                        345L,
                                        "The user compared the Japan trip with the broader"
                                                + " itinerary.")));
        store.graphOperations()
                .upsertItemEntityMentions(
                        memoryId,
                        List.of(
                                mention(memoryId, 341L, "concept:travel"),
                                mention(memoryId, 342L, "concept:travel"),
                                mention(memoryId, 343L, "concept:japan"),
                                mention(memoryId, 344L, "concept:japan"),
                                mention(memoryId, 345L, "concept:travel"),
                                mention(memoryId, 345L, "concept:japan")));

        ThreadProjectionMaterializer.MaterializedProjection projection =
                materializer.materializeUpTo(memoryId, 345L);

        assertThat(projection.threads())
                .extracting(thread -> thread.threadKey(), thread -> thread.memberCount())
                .containsExactlyInAnyOrder(
                        tuple("topic:topic:concept:travel", 2L),
                        tuple("topic:topic:concept:japan", 2L));
        assertThat(projection.memberships())
                .extracting(membership -> membership.itemId())
                .doesNotContain(345L);
    }

    @Test
    void secondHitCreatesRelationshipGroupThreadAndRetroactivelyAdmitsFirstItem() {
        MemoryId memoryId = TestMemoryIds.userAgent();
        InMemoryMemoryStore store = new InMemoryMemoryStore();
        ThreadProjectionMaterializer materializer = materializer(store);

        store.itemOperations()
                .insertItems(
                        memoryId,
                        List.of(
                                item(3451L, "Alice, Bob, and Carol planned the launch together.")));
        store.graphOperations()
                .upsertItemEntityMentions(
                        memoryId,
                        List.of(
                                mention(memoryId, 3451L, "person:alice"),
                                mention(memoryId, 3451L, "person:bob"),
                                mention(memoryId, 3451L, "person:carol")));

        ThreadProjectionMaterializer.MaterializedProjection firstProjection =
                materializer.materializeUpTo(memoryId, 3451L);

        assertThat(firstProjection.threads()).isEmpty();

        store.itemOperations()
                .insertItems(
                        memoryId,
                        List.of(
                                item(3452L, "Alice, Bob, and Carol reviewed the launch plan.")));
        store.graphOperations()
                .upsertItemEntityMentions(
                        memoryId,
                        List.of(
                                mention(memoryId, 3452L, "person:alice"),
                                mention(memoryId, 3452L, "person:bob"),
                                mention(memoryId, 3452L, "person:carol")));

        ThreadProjectionMaterializer.MaterializedProjection secondProjection =
                materializer.materializeUpTo(memoryId, 3452L);

        assertThat(secondProjection.threads())
                .singleElement()
                .extracting(thread -> thread.threadKey(), thread -> thread.memberCount())
                .containsExactly(
                        "relationship:relationship_group:person:alice|person:bob|person:carol",
                        2L);
        assertThat(secondProjection.memberships())
                .filteredOn(
                        membership ->
                                membership.threadKey()
                                        .equals(
                                                "relationship:relationship_group:person:alice|person:bob|person:carol"))
                .extracting(membership -> membership.itemId())
                .containsExactly(3451L, 3452L);
    }

    @Test
    void oneItemAdmitsAtMostOneMembershipPerThreadType() {
        MemoryId memoryId = TestMemoryIds.userAgent();
        InMemoryMemoryStore store = new InMemoryMemoryStore();
        ThreadProjectionMaterializer materializer = materializer(store);

        store.itemOperations()
                .insertItems(
                        memoryId,
                        List.of(
                                item(346L, "The user planned a trip."),
                                item(347L, "The user booked the trip."),
                                item(348L, "The user planned a Japan itinerary."),
                                item(349L, "The user booked the Japan tickets."),
                                exactAnchorContinuityItem(
                                        350L,
                                        "The user checked travel details against the Japan plans.",
                                        "travel",
                                        349L)));
        store.graphOperations()
                .upsertItemEntityMentions(
                        memoryId,
                        List.of(
                                mention(memoryId, 346L, "concept:travel"),
                                mention(memoryId, 347L, "concept:travel"),
                                mention(memoryId, 348L, "concept:japan"),
                                mention(memoryId, 349L, "concept:japan")));

        ThreadProjectionMaterializer.MaterializedProjection projection =
                materializer.materializeUpTo(memoryId, 350L);

        assertThat(projection.memberships())
                .filteredOn(membership -> membership.itemId() == 350L)
                .singleElement()
                .satisfies(
                        membership -> {
                            assertThat(membership.threadKey())
                                    .isEqualTo("topic:topic:concept:travel");
                            assertThat(membership.role())
                                    .isEqualTo(MemoryThreadMembershipRole.TRIGGER);
                            assertThat(membership.primary()).isTrue();
                        });
    }

    @Test
    void materializerPassesCommittedEntityCooccurrencesIntoSignalExtraction() {
        MemoryId memoryId = TestMemoryIds.userAgent();
        InMemoryItemOperations itemOperations = new InMemoryItemOperations();
        RecordingGraphOperations graphOperations = new RecordingGraphOperations();
        ThreadMaterializationPolicy policy =
                new ThreadMaterializationPolicy(
                        "test-policy", 0.78d, 0.75d, 4, Duration.ofDays(7), Duration.ofDays(21));
        ThreadProjectionMaterializer materializer =
                new ThreadProjectionMaterializer(itemOperations, graphOperations, policy);

        itemOperations.insertItems(
                memoryId,
                List.of(
                        profileItem(401L, "Travel planning is becoming a recurring theme."),
                        profileItem(402L, "Travel prep is still front of mind.")));
        graphOperations.upsertItemEntityMentions(
                memoryId,
                List.of(
                        mention(memoryId, 401L, "concept:travel"),
                        mention(memoryId, 402L, "concept:travel")));

        ThreadProjectionMaterializer.MaterializedProjection projection =
                materializer.materializeUpTo(memoryId, 402L);

        assertThat(graphOperations.cooccurrenceReadCount).isEqualTo(1);
        assertThat(projection.threads())
                .singleElement()
                .extracting(thread -> thread.threadKey(), thread -> thread.memberCount())
                .containsExactly("topic:topic:concept:travel", 2L);
    }

    @Test
    void exactAnchorShortCircuitsBeforeMaxCandidateThreadsTruncation() {
        MemoryId memoryId = TestMemoryIds.userAgent();
        InMemoryMemoryStore store = new InMemoryMemoryStore();
        ThreadMaterializationPolicy policy =
                new ThreadMaterializationPolicy(
                        "test-policy", 0.78d, 0.70d, 1, Duration.ofDays(7), Duration.ofDays(21));
        ThreadProjectionMaterializer materializer =
                new ThreadProjectionMaterializer(
                        store.itemOperations(), store.graphOperations(), policy);

        store.itemOperations()
                .insertItems(
                        memoryId,
                        List.of(
                                item(511L, "The user planned a trip."),
                                item(512L, "The user booked the trip."),
                                item(513L, "The user planned a Japan itinerary."),
                                item(514L, "The user booked the Japan tickets."),
                                exactAnchorContinuityItem(
                                        515L,
                                        "The user checked travel details against the Japan plans.",
                                        "travel",
                                        514L)));
        store.graphOperations()
                .upsertItemEntityMentions(
                        memoryId,
                        List.of(
                                mention(memoryId, 511L, "concept:travel"),
                                mention(memoryId, 512L, "concept:travel"),
                                mention(memoryId, 513L, "concept:japan"),
                                mention(memoryId, 514L, "concept:japan")));

        ThreadProjectionMaterializer.MaterializedProjection projection =
                materializer.materializeUpTo(memoryId, 515L);

        assertThat(projection.threads())
                .extracting(thread -> thread.threadKey(), thread -> thread.memberCount())
                .containsExactlyInAnyOrder(
                        tuple("topic:topic:concept:travel", 3L),
                        tuple("topic:topic:concept:japan", 2L));
        assertThat(projection.memberships())
                .filteredOn(membership -> membership.threadKey().equals("topic:topic:concept:travel"))
                .extracting(membership -> membership.itemId())
                .containsExactly(511L, 512L, 515L);
    }

    @Test
    void replayLoadsOnlyInputsInsideCutoffAndMatchingPolicyVersion() {
        MemoryId memoryId = TestMemoryIds.userAgent();
        InMemoryMemoryStore store = new InMemoryMemoryStore();
        ThreadMaterializationPolicy policy = ThreadMaterializationPolicy.v1();
        ThreadProjectionMaterializer materializer =
                new ThreadProjectionMaterializer(
                        store.itemOperations(),
                        store.graphOperations(),
                        store.threadEnrichmentInputStore(),
                        policy);

        store.itemOperations()
                .insertItems(
                        memoryId,
                        List.of(
                                item(551L, "The user planned a trip."),
                                item(552L, "The user booked the trip.")));
        store.graphOperations()
                .upsertItemEntityMentions(
                        memoryId,
                        List.of(
                                mention(memoryId, 551L, "concept:travel"),
                                mention(memoryId, 552L, "concept:travel")));
        store.threadEnrichmentInputStore()
                .appendRunAndEnqueueReplay(
                        memoryId,
                        552L,
                        List.of(
                                enrichmentInput(
                                        memoryId,
                                        552L,
                                        2L,
                                        "topic:topic:concept:travel|552|2|" + policy.version(),
                                        0,
                                        "Current-policy headline",
                                        policy.version())));
        store.threadEnrichmentInputStore()
                .appendRunAndEnqueueReplay(
                        memoryId,
                        552L,
                        List.of(
                                enrichmentInput(
                                        memoryId,
                                        552L,
                                        2L,
                                        "topic:topic:concept:travel|552|2|legacy-policy",
                                        0,
                                        "Legacy headline",
                                        "legacy-policy")));
        store.threadEnrichmentInputStore()
                .appendRunAndEnqueueReplay(
                        memoryId,
                        553L,
                        List.of(
                                enrichmentInput(
                                        memoryId,
                                        553L,
                                        3L,
                                        "topic:topic:concept:travel|553|3|" + policy.version(),
                                        0,
                                        "Future headline",
                                        policy.version())));

        ThreadProjectionMaterializer.MaterializedProjection projection =
                materializer.materializeUpTo(memoryId, 552L);

        assertThat(projection.events())
                .filteredOn(event -> event.eventKey().contains(":enrichment:"))
                .extracting(event -> event.eventPayloadJson().get("summary"))
                .containsExactly("Current-policy headline");
        assertThat(projection.threads())
                .singleElement()
                .extracting(thread -> thread.headline())
                .isEqualTo("Current-policy headline");
    }

    @Test
    void headlineRefreshUpdatesHeadlineWithoutPollutingSalientFacts() {
        MemoryId memoryId = TestMemoryIds.userAgent();
        InMemoryMemoryStore store = new InMemoryMemoryStore();
        ThreadMaterializationPolicy policy = ThreadMaterializationPolicy.v1();
        ThreadProjectionMaterializer materializer =
                new ThreadProjectionMaterializer(
                        store.itemOperations(),
                        store.graphOperations(),
                        store.threadEnrichmentInputStore(),
                        policy);

        store.itemOperations()
                .insertItems(
                        memoryId,
                        List.of(
                                item(561L, "The user planned a trip."),
                                item(562L, "The user booked the trip.")));
        store.graphOperations()
                .upsertItemEntityMentions(
                        memoryId,
                        List.of(
                                mention(memoryId, 561L, "concept:travel"),
                                mention(memoryId, 562L, "concept:travel")));
        store.threadEnrichmentInputStore()
                .appendRunAndEnqueueReplay(
                        memoryId,
                        562L,
                        List.of(
                                enrichmentInput(
                                        memoryId,
                                        562L,
                                        2L,
                                        "topic:topic:concept:travel|562|2|" + policy.version(),
                                        0,
                                        "Travel storyline headline",
                                        policy.version())));

        ThreadProjectionMaterializer.MaterializedProjection projection =
                materializer.materializeUpTo(memoryId, 562L);

        assertThat(projection.threads())
                .singleElement()
                .satisfies(
                        thread -> {
                            assertThat(thread.headline()).isEqualTo("Travel storyline headline");
                            assertThat(thread.snapshotJson().get("salientFacts"))
                                    .isEqualTo(
                                            List.of(
                                                    "The user planned a trip.",
                                                    "The user booked the trip."));
                            @SuppressWarnings("unchecked")
                            Map<String, Object> facets =
                                    (Map<String, Object>) thread.snapshotJson().get("facets");
                            @SuppressWarnings("unchecked")
                            Map<String, Object> enrichment =
                                    (Map<String, Object>) facets.get("enrichment");
                            assertThat(enrichment)
                                    .containsEntry(
                                            "lastEnrichedMeaningfulEventCount", 2L)
                                    .containsEntry("headlineSource", "THREAD_LLM");
                        });
    }

    private static ThreadProjectionMaterializer materializer(InMemoryMemoryStore store) {
        return new ThreadProjectionMaterializer(
                store.itemOperations(), store.graphOperations(), ThreadMaterializationPolicy.v1());
    }

    private static MemoryItem item(long itemId, String content) {
        return new MemoryItem(
                itemId,
                TestMemoryIds.userAgent().toIdentifier(),
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

    private static MemoryItem continuityItem(
            long itemId, String content, String conceptKey, long targetItemId) {
        return new MemoryItem(
                itemId,
                TestMemoryIds.userAgent().toIdentifier(),
                content,
                MemoryScope.USER,
                MemoryCategory.EVENT,
                "conversation",
                "vec-" + itemId,
                "raw-" + itemId,
                "hash-" + itemId,
                Instant.parse("2026-04-20T09:00:00Z"),
                Instant.parse("2026-04-20T09:00:00Z"),
                Map.of(
                        "threadSemantics",
                        Map.of(
                                "version",
                                1,
                                "canonicalRefs",
                                List.of(
                                        Map.of(
                                                "refType",
                                                "topic",
                                                "refKey",
                                                conceptKey.substring("concept:".length()))),
                                "continuityLinks",
                                List.of(
                                        Map.of(
                                                "linkType",
                                                "CONTINUES",
                                                "targetItemId",
                                                targetItemId)))),
                Instant.parse("2026-04-20T09:00:00Z"),
                MemoryItemType.FACT);
    }

    private static MemoryItem exactAnchorContinuityItem(
            long itemId, String content, String topicKey, long targetItemId) {
        return new MemoryItem(
                itemId,
                TestMemoryIds.userAgent().toIdentifier(),
                content,
                MemoryScope.USER,
                MemoryCategory.EVENT,
                "conversation",
                "vec-" + itemId,
                "raw-" + itemId,
                "hash-" + itemId,
                Instant.parse("2026-04-20T09:00:00Z"),
                Instant.parse("2026-04-20T09:00:00Z"),
                Map.of(
                        "threadSemantics",
                        Map.of(
                                "version",
                                1,
                                "canonicalRefs",
                                List.of(Map.of("refType", "topic", "refKey", topicKey)),
                                "continuityLinks",
                                List.of(
                                        Map.of(
                                                "linkType",
                                                "CONTINUES",
                                                "targetItemId",
                                                targetItemId)))),
                Instant.parse("2026-04-20T09:00:00Z"),
                MemoryItemType.FACT);
    }

    private static MemoryItem metadataItem(
            long itemId,
            String content,
            String refType,
            String refKey,
            List<Map<String, Object>> markers) {
        return new MemoryItem(
                itemId,
                TestMemoryIds.userAgent().toIdentifier(),
                content,
                MemoryScope.USER,
                MemoryCategory.EVENT,
                "conversation",
                "vec-" + itemId,
                "raw-" + itemId,
                "hash-" + itemId,
                Instant.parse("2026-04-20T09:00:00Z"),
                Instant.parse("2026-04-20T09:00:00Z"),
                Map.of(
                        "threadSemantics",
                        Map.of(
                                "version",
                                1,
                                "markers",
                                markers,
                                "canonicalRefs",
                                List.of(Map.of("refType", refType, "refKey", refKey)))),
                Instant.parse("2026-04-20T09:00:00Z"),
                MemoryItemType.FACT);
    }

    private static MemoryItem profileItem(long itemId, String content) {
        return new MemoryItem(
                itemId,
                TestMemoryIds.userAgent().toIdentifier(),
                content,
                MemoryScope.USER,
                MemoryCategory.PROFILE,
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

    private static ItemEntityMention mention(MemoryId memoryId, long itemId, String entityKey) {
        return new ItemEntityMention(
                memoryId.toIdentifier(),
                itemId,
                entityKey,
                1.0f,
                Map.of("source", "test"),
                Instant.parse("2026-04-20T09:00:00Z"));
    }

    private static MemoryThreadEnrichmentInput enrichmentInput(
            MemoryId memoryId,
            long basisCutoffItemId,
            long basisMeaningfulEventCount,
            String inputRunKey,
            int entrySeq,
            String summary,
            String policyVersion) {
        return new MemoryThreadEnrichmentInput(
                memoryId.toIdentifier(),
                "topic:topic:concept:travel",
                inputRunKey,
                entrySeq,
                basisCutoffItemId,
                basisMeaningfulEventCount,
                policyVersion,
                Map.of(
                        "eventType",
                        "OBSERVATION",
                        "meaningful",
                        false,
                        "basisEventKey",
                        "topic:topic:concept:travel:observation:" + basisCutoffItemId,
                        "summary",
                        summary,
                        "summaryRole",
                        "HEADLINE_REFRESH"),
                Map.of(
                        "sourceType",
                        "THREAD_LLM",
                        "supportingItemIds",
                        List.of(basisCutoffItemId)),
                Instant.parse("2026-04-22T10:30:00Z"));
    }

    private static final class RecordingGraphOperations extends InMemoryGraphOperations {

        private int cooccurrenceReadCount;

        @Override
        public List<EntityCooccurrence> listEntityCooccurrences(MemoryId memoryId) {
            cooccurrenceReadCount++;
            return List.of(
                    new EntityCooccurrence(
                            memoryId.toIdentifier(),
                            "concept:travel",
                            "concept:passport",
                            4,
                            Map.of("source", "test"),
                            Instant.parse("2026-04-20T09:00:00Z")));
        }
    }
}
