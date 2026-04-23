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
package com.openmemind.ai.memory.core.store;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.data.DefaultInsightTypes;
import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.InsightPoint;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryInsight;
import com.openmemind.ai.memory.core.data.MemoryInsightType;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.MemoryRawData;
import com.openmemind.ai.memory.core.data.MemoryResource;
import com.openmemind.ai.memory.core.data.enums.InsightAnalysisMode;
import com.openmemind.ai.memory.core.data.enums.InsightTier;
import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.data.enums.MemoryItemType;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadLifecycleStatus;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadMembershipRole;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadObjectState;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadProjectionState;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadType;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadMembership;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadProjection;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadRuntimeState;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.Segment;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("InMemoryMemoryStore")
class InMemoryMemoryStoreTest {

    private static final MemoryId MEMORY_ID = DefaultMemoryId.of("user-1", "agent-1");
    private static final Instant BASE_TIME = Instant.parse("2026-03-21T00:00:00Z");

    @Test
    @DisplayName("constructor bootstraps default insight taxonomy once")
    void constructorBootstrapsDefaultInsightTaxonomyOnce() {
        var store = new InMemoryMemoryStore();

        assertThat(store.insightOperations().listInsightTypes())
                .extracting(MemoryInsightType::name)
                .containsExactlyInAnyOrderElementsOf(
                        DefaultInsightTypes.all().stream().map(MemoryInsightType::name).toList());
    }

    @Test
    @DisplayName("upsertRawData replaces an existing record")
    void upsertRawDataReplacesExistingRecord() {
        var store = new InMemoryMemoryStore();

        store.rawDataOperations()
                .upsertRawData(
                        MEMORY_ID,
                        List.of(
                                rawData(
                                        "raw-1",
                                        "old caption",
                                        "content-1",
                                        Map.of("source", "seed"))));

        store.rawDataOperations()
                .upsertRawData(
                        MEMORY_ID,
                        List.of(
                                rawData(
                                        "raw-1",
                                        "new caption",
                                        "content-1",
                                        Map.of("source", "updated"))));

        assertThat(store.rawDataOperations().listRawData(MEMORY_ID))
                .singleElement()
                .extracting(MemoryRawData::caption)
                .isEqualTo("new caption");
    }

    @Test
    @DisplayName("upsertInsightTypes uses a global namespace")
    void upsertInsightTypesUsesGlobalNamespace() {
        var store = new InMemoryMemoryStore();

        store.insightOperations()
                .upsertInsightTypes(List.of(insightType(1L, "profile"), insightType(2L, "agent")));

        assertThat(store.insightOperations().getInsightType("profile"))
                .get()
                .extracting(MemoryInsightType::description)
                .isEqualTo("description-profile");
        assertThat(store.insightOperations().getInsightType("agent")).isPresent();
        assertThat(store.insightOperations().listInsightTypes())
                .extracting(MemoryInsightType::name)
                .contains("profile", "agent");
    }

    @Test
    @DisplayName("deleteItems removes only requested items")
    void deleteItemsRemovesOnlyRequestedItems() {
        var store = new InMemoryMemoryStore();

        store.itemOperations().insertItems(MEMORY_ID, List.of(item(1L), item(2L), item(3L)));

        store.itemOperations().deleteItems(MEMORY_ID, List.of(2L, 3L));

        assertThat(store.itemOperations().listItems(MEMORY_ID))
                .extracting(MemoryItem::id)
                .containsExactly(1L);
    }

    @Test
    @DisplayName("deleteInsights removes only requested insights")
    void deleteInsightsRemovesOnlyRequestedInsights() {
        var store = new InMemoryMemoryStore();

        store.insightOperations()
                .upsertInsights(MEMORY_ID, List.of(insight(10L), insight(20L), insight(30L)));

        store.insightOperations().deleteInsights(MEMORY_ID, List.of(20L, 30L));

        assertThat(store.insightOperations().listInsights(MEMORY_ID))
                .extracting(MemoryInsight::id)
                .containsExactly(10L);
    }

    @Test
    @DisplayName("resourceOperations upserts and loads resources")
    void resourceOperationsUpsertsAndLoadsResources() {
        var store = new InMemoryMemoryStore();
        var resource =
                new MemoryResource(
                        "res-1",
                        MEMORY_ID.toIdentifier(),
                        "file:///tmp/report.pdf",
                        null,
                        "report.pdf",
                        "application/pdf",
                        "abc",
                        123L,
                        Map.of("pages", 2),
                        BASE_TIME);

        store.resourceOperations().upsertResources(MEMORY_ID, List.of(resource));

        assertThat(store.resourceOperations().getResource(MEMORY_ID, "res-1")).contains(resource);
    }

    @Test
    @DisplayName("graphOperations stays empty by default and is exposed by in-memory store")
    void graphOperationsStaysEmptyByDefaultAndIsExposedByInMemoryStore() {
        var store = new InMemoryMemoryStore();

        assertThat(store.graphOperations().listEntities(MEMORY_ID)).isEmpty();
        assertThat(store.graphOperations().listItemLinks(MEMORY_ID)).isEmpty();
    }

    @Test
    @DisplayName("threadOperations persists many-to-many memberships in the in-memory store")
    void threadOperationsPersistsManyToManyMembershipsInTheInMemoryStore() {
        var store = new InMemoryMemoryStore();

        store.threadOperations()
                .replaceProjection(
                        MEMORY_ID,
                        List.of(
                                projection("relationship:relationship:person:alice|person:bob"),
                                projection("topic:topic:concept:travel")),
                        List.of(),
                        List.of(
                                membership(
                                        "relationship:relationship:person:alice|person:bob", 101L),
                                membership("topic:topic:concept:travel", 101L)),
                        runtime(),
                        BASE_TIME);

        assertThat(store.threadOperations().listThreadsByItemId(MEMORY_ID, 101L))
                .extracting(MemoryThreadProjection::threadKey)
                .containsExactlyInAnyOrder(
                        "relationship:relationship:person:alice|person:bob",
                        "topic:topic:concept:travel");
        assertThat(store.threadOperations().getRuntime(MEMORY_ID))
                .get()
                .extracting(MemoryThreadRuntimeState::projectionState)
                .isEqualTo(MemoryThreadProjectionState.AVAILABLE);
    }

    private static MemoryRawData rawData(
            String id, String caption, String contentId, Map<String, Object> metadata) {
        return new MemoryRawData(
                id,
                MEMORY_ID.toIdentifier(),
                "conversation",
                contentId,
                Segment.single("segment-" + id),
                caption,
                null,
                metadata,
                null,
                null,
                BASE_TIME,
                BASE_TIME,
                BASE_TIME.plusSeconds(30));
    }

    private static MemoryInsightType insightType(Long id, String name) {
        return new MemoryInsightType(
                id,
                name,
                "description-" + name,
                null,
                List.of("profile"),
                600,
                BASE_TIME,
                BASE_TIME,
                BASE_TIME,
                InsightAnalysisMode.BRANCH,
                null,
                MemoryScope.USER);
    }

    private static MemoryItem item(Long id) {
        return new MemoryItem(
                id,
                MEMORY_ID.toIdentifier(),
                "content-" + id,
                MemoryScope.USER,
                MemoryCategory.PROFILE,
                "conversation",
                "vector-" + id,
                "raw-" + id,
                "hash-" + id,
                BASE_TIME,
                BASE_TIME.plusSeconds(5),
                Map.of("id", id),
                BASE_TIME,
                MemoryItemType.FACT);
    }

    private static MemoryInsight insight(Long id) {
        return new MemoryInsight(
                id,
                MEMORY_ID.toIdentifier(),
                "profile",
                MemoryScope.USER,
                "name-" + id,
                List.of("profile"),
                List.of(new InsightPoint(InsightPoint.PointType.SUMMARY, "point-" + id, List.of())),
                "group",
                BASE_TIME,
                List.of(),
                BASE_TIME,
                BASE_TIME,
                InsightTier.LEAF,
                null,
                List.of(),
                1);
    }

    private static MemoryThreadProjection projection(String threadKey) {
        return new MemoryThreadProjection(
                MEMORY_ID.toIdentifier(),
                threadKey,
                MemoryThreadType.TOPIC,
                "topic",
                threadKey.substring(threadKey.lastIndexOf(':') + 1),
                "label-" + threadKey,
                MemoryThreadLifecycleStatus.ACTIVE,
                MemoryThreadObjectState.ONGOING,
                "headline",
                Map.of("source", "test"),
                1,
                BASE_TIME,
                BASE_TIME,
                BASE_TIME,
                null,
                1,
                1,
                BASE_TIME,
                BASE_TIME);
    }

    private static MemoryThreadMembership membership(String threadKey, Long itemId) {
        return new MemoryThreadMembership(
                MEMORY_ID.toIdentifier(),
                threadKey,
                itemId,
                MemoryThreadMembershipRole.CORE,
                true,
                1.0d,
                BASE_TIME,
                BASE_TIME);
    }

    private static MemoryThreadRuntimeState runtime() {
        return new MemoryThreadRuntimeState(
                MEMORY_ID.toIdentifier(),
                MemoryThreadProjectionState.AVAILABLE,
                0,
                0,
                101L,
                101L,
                false,
                null,
                0L,
                "v1",
                null,
                BASE_TIME);
    }
}
