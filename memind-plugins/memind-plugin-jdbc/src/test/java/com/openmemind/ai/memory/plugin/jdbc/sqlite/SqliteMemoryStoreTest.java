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
package com.openmemind.ai.memory.plugin.jdbc.sqlite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmemind.ai.memory.core.data.ContentTypes;
import com.openmemind.ai.memory.core.data.InsightPoint;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryInsight;
import com.openmemind.ai.memory.core.data.MemoryInsightType;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.MemoryRawData;
import com.openmemind.ai.memory.core.data.enums.InsightAnalysisMode;
import com.openmemind.ai.memory.core.data.enums.InsightTier;
import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.data.enums.MemoryItemType;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.extraction.insight.tree.InsightTreeConfig;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.CharBoundary;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.MessageBoundary;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.Segment;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

class SqliteMemoryStoreTest {

    private static final Instant BASE_TIME = Instant.parse("2026-03-22T00:00:00Z");

    @TempDir Path tempDir;

    private final TestMemoryId memoryId = new TestMemoryId("u1", "a1");

    private DataSource dataSource;
    private SqliteMemoryStore store;

    @BeforeEach
    void setUp() {
        dataSource = dataSource("memory-store.db");
        store = new SqliteMemoryStore(dataSource);
    }

    @Test
    void constructorInitializesSchemaByDefault() {
        DataSource emptyDataSource = dataSource("store-auto-init.db");

        new SqliteMemoryStore(emptyDataSource);

        assertThat(tableExists(emptyDataSource, "memory_raw_data")).isTrue();
        assertThat(tableExists(emptyDataSource, "memory_item")).isTrue();
        assertThat(tableExists(emptyDataSource, "memory_insight")).isTrue();
    }

    @Test
    void constructorWithCreateIfNotExistFalseFailsOnEmptyDatabase() {
        DataSource emptyDataSource = dataSource("store-no-auto-init.db");

        assertThatThrownBy(() -> new SqliteMemoryStore(emptyDataSource, false))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("memory_raw_data");
    }

    @Test
    void rawDataCrudAndVectorUpdatesWork() {
        MemoryRawData rawData =
                new MemoryRawData(
                        "rd-1",
                        memoryId.toIdentifier(),
                        ContentTypes.CONVERSATION,
                        "content-1",
                        new Segment(
                                "hello world",
                                "segment caption",
                                new CharBoundary(0, 11),
                                Map.of("chunk", 1)),
                        "caption-1",
                        null,
                        Map.of("source", "chat"),
                        BASE_TIME.minusSeconds(300),
                        BASE_TIME.minusSeconds(320),
                        BASE_TIME.minusSeconds(290));

        store.upsertRawData(memoryId, List.of(rawData));

        MemoryRawData inserted = store.getRawData(memoryId, "rd-1").orElseThrow();
        assertThat(inserted.contentId()).isEqualTo("content-1");
        assertThat(inserted.segment()).isEqualTo(rawData.segment());
        assertThat(inserted.caption()).isEqualTo("caption-1");

        MemoryRawData updated =
                new MemoryRawData(
                        "rd-1",
                        memoryId.toIdentifier(),
                        ContentTypes.TOOL_CALL,
                        "content-1",
                        new Segment(
                                "updated content",
                                "updated segment caption",
                                new MessageBoundary(1, 3),
                                Map.of("chunk", 2)),
                        "caption-2",
                        null,
                        Map.of("source", "updated"),
                        BASE_TIME.minusSeconds(300),
                        BASE_TIME.minusSeconds(280),
                        BASE_TIME.minusSeconds(260));

        store.upsertRawData(memoryId, List.of(updated));

        MemoryRawData byContent = store.getRawDataByContentId(memoryId, "content-1").orElseThrow();
        assertThat(byContent.contentType()).isEqualTo(ContentTypes.TOOL_CALL);
        assertThat(byContent.caption()).isEqualTo("caption-2");
        assertThat(byContent.segment()).isEqualTo(updated.segment());
        assertThat(store.listRawData(memoryId)).hasSize(1);

        store.updateRawDataVectorIds(
                memoryId, Map.of("rd-1", "vec-1"), Map.of("source", "patched", "score", 0.9d));

        MemoryRawData vectorized = store.getRawData(memoryId, "rd-1").orElseThrow();
        assertThat(vectorized.captionVectorId()).isEqualTo("vec-1");
        assertThat(vectorized.metadata()).containsEntry("source", "patched");
        assertThat(vectorized.metadata()).containsEntry("score", 0.9d);
    }

    @Test
    void pollRawDataWithoutVectorFiltersByAgeAndParsesSqliteDatetimeNow() {
        store.upsertRawData(
                memoryId,
                List.of(
                        rawData("rd-old", "content-old", null, Instant.now().minusSeconds(600)),
                        rawData(
                                "rd-recent",
                                "content-recent",
                                null,
                                Instant.now().minusSeconds(10)),
                        rawData(
                                "rd-vectorized",
                                "content-vectorized",
                                "vec-2",
                                Instant.now().minusSeconds(600))));

        List<MemoryRawData> polled =
                store.pollRawDataWithoutVector(memoryId, 10, Duration.ofSeconds(60));

        assertThat(polled).extracting(MemoryRawData::id).containsExactly("rd-old");

        executeUpdate(
                """
                INSERT INTO memory_raw_data
                    (biz_id, user_id, agent_id, memory_id, type, content_id, caption)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                "rd-legacy-time",
                memoryId.userId(),
                memoryId.agentId(),
                memoryId.toIdentifier(),
                ContentTypes.CONVERSATION,
                "content-legacy",
                "legacy caption");

        MemoryRawData legacy = store.getRawData(memoryId, "rd-legacy-time").orElseThrow();
        assertThat(legacy.createdAt()).isNotNull();
    }

    @Test
    void itemQueriesAndSoftDeleteWork() {
        MemoryItem first = item(101L, "first item", "vec-101", "hash-101", "rd-1");
        MemoryItem second = item(102L, "second item", "vec-102", "hash-102", "rd-2");

        store.insertItems(memoryId, List.of(first, second));

        assertThat(store.getItemsByIds(memoryId, List.of(101L, 999L)))
                .extracting(MemoryItem::id)
                .containsExactly(101L);
        assertThat(store.getItemsByVectorIds(memoryId, List.of("vec-102")))
                .extracting(MemoryItem::id)
                .containsExactly(102L);
        assertThat(store.getItemsByContentHashes(memoryId, List.of("hash-101", "missing")))
                .extracting(MemoryItem::id)
                .containsExactly(101L);
        assertThat(store.listItems(memoryId)).hasSize(2);
        assertThat(store.hasItems(memoryId)).isTrue();

        store.deleteItems(memoryId, List.of(101L));

        assertThat(itemDeletedFlag(101L)).isEqualTo(1);
        assertThat(store.getItemsByIds(memoryId, List.of(101L, 102L)))
                .extracting(MemoryItem::id)
                .containsExactly(102L);
        assertThat(store.listItems(memoryId)).extracting(MemoryItem::id).containsExactly(102L);
        assertThat(store.hasItems(memoryId)).isTrue();

        store.deleteItems(memoryId, List.of(102L));

        assertThat(itemDeletedFlag(102L)).isEqualTo(1);
        assertThat(store.listItems(memoryId)).isEmpty();
        assertThat(store.hasItems(memoryId)).isFalse();
    }

    @Test
    void itemsCanPersistWithNullOccurredAt() {
        Instant observedAt = BASE_TIME.plusSeconds(30);
        MemoryItem itemWithoutOccurredAt =
                new MemoryItem(
                        103L,
                        memoryId.toIdentifier(),
                        "stable profile fact",
                        MemoryScope.USER,
                        MemoryCategory.PROFILE,
                        ContentTypes.CONVERSATION,
                        "vec-103",
                        "rd-103",
                        "hash-103",
                        null,
                        observedAt,
                        Map.of("kind", "profile"),
                        BASE_TIME,
                        MemoryItemType.FACT);

        store.insertItems(memoryId, List.of(itemWithoutOccurredAt));

        assertThat(store.getItemsByIds(memoryId, List.of(103L)))
                .singleElement()
                .satisfies(
                        item -> {
                            assertThat(item.occurredAt()).isNull();
                            assertThat(item.observedAt()).isEqualTo(observedAt);
                        });
    }

    @Test
    void insightTypesCanBeUpsertedAndListed() {
        MemoryInsightType initial =
                new MemoryInsightType(
                        201L,
                        "profile",
                        "initial description",
                        "profile-vec",
                        List.of("profile"),
                        512,
                        Map.of("summary", "prompt-a"),
                        BASE_TIME.minusSeconds(30),
                        BASE_TIME.minusSeconds(60),
                        BASE_TIME.minusSeconds(10),
                        InsightAnalysisMode.BRANCH,
                        InsightTreeConfig.defaults(),
                        MemoryScope.USER,
                        null);

        MemoryInsightType updated =
                new MemoryInsightType(
                        201L,
                        "profile",
                        "updated description",
                        "profile-vec-2",
                        List.of("profile", "behavior"),
                        640,
                        Map.of("summary", "prompt-b"),
                        BASE_TIME,
                        BASE_TIME.minusSeconds(60),
                        BASE_TIME,
                        InsightAnalysisMode.ROOT,
                        new InsightTreeConfig(4, 3, 2, 900),
                        MemoryScope.USER,
                        null);

        store.upsertInsightTypes(List.of(initial));
        store.upsertInsightTypes(List.of(updated));

        MemoryInsightType fetched = store.getInsightType("profile").orElseThrow();
        assertThat(fetched.description()).isEqualTo("updated description");
        assertThat(fetched.descriptionVectorId()).isEqualTo("profile-vec-2");
        assertThat(fetched.targetTokens()).isEqualTo(640);
        assertThat(fetched.categories()).containsExactly("profile", "behavior");
        assertThat(fetched.summaryPrompt()).containsEntry("summary", "prompt-b");
        assertThat(fetched.insightAnalysisMode()).isEqualTo(InsightAnalysisMode.ROOT);
        assertThat(fetched.treeConfig()).isEqualTo(new InsightTreeConfig(4, 3, 2, 900));
        assertThat(fetched.scope()).isEqualTo(MemoryScope.USER);
        assertThat(store.listInsightTypes())
                .extracting(MemoryInsightType::name)
                .containsExactly("profile");
    }

    @Test
    void insightsCanBeQueriedByTreeSelectorsAndSoftDeleted() {
        MemoryInsight leaf =
                insight(
                        301L,
                        "profile",
                        InsightTier.LEAF,
                        "group-a",
                        302L,
                        List.of(),
                        "leaf point");
        MemoryInsight branch =
                insight(
                        302L,
                        "profile",
                        InsightTier.BRANCH,
                        null,
                        303L,
                        List.of(301L),
                        "branch point");
        MemoryInsight root =
                insight(303L, "profile", InsightTier.ROOT, null, null, List.of(302L), "root point");

        store.upsertInsights(memoryId, List.of(leaf, branch, root));

        MemoryInsight updatedLeaf =
                new MemoryInsight(
                        301L,
                        memoryId.toIdentifier(),
                        "profile",
                        MemoryScope.USER,
                        "leaf-title",
                        List.of("profile"),
                        List.of(point("leaf point updated")),
                        "group-a",
                        0.95f,
                        BASE_TIME.minusSeconds(5),
                        List.of(0.8f, 0.9f),
                        BASE_TIME.minusSeconds(20),
                        BASE_TIME.minusSeconds(1),
                        InsightTier.LEAF,
                        302L,
                        List.of(),
                        2);

        store.upsertInsights(memoryId, List.of(updatedLeaf));

        MemoryInsight fetchedLeaf = store.getInsight(memoryId, 301L).orElseThrow();
        assertThat(fetchedLeaf.points())
                .extracting(InsightPoint::content)
                .containsExactly("leaf point updated");
        assertThat(fetchedLeaf.confidence()).isEqualTo(0.95f);
        assertThat(fetchedLeaf.version()).isEqualTo(2);
        assertThat(store.listInsights(memoryId)).hasSize(3);
        assertThat(store.getInsightsByType(memoryId, "profile")).hasSize(3);
        assertThat(store.getInsightsByTier(memoryId, InsightTier.LEAF))
                .extracting(MemoryInsight::id)
                .containsExactly(301L);
        assertThat(store.getLeafByGroup(memoryId, "profile", "group-a"))
                .hasValueSatisfying(insight -> assertThat(insight.id()).isEqualTo(301L));
        assertThat(store.getBranchByType(memoryId, "profile"))
                .hasValueSatisfying(insight -> assertThat(insight.id()).isEqualTo(302L));
        assertThat(store.getRootByType(memoryId, "profile"))
                .hasValueSatisfying(insight -> assertThat(insight.id()).isEqualTo(303L));

        store.deleteInsights(memoryId, List.of(301L, 303L));

        assertThat(insightDeletedFlag(301L)).isEqualTo(1);
        assertThat(insightDeletedFlag(303L)).isEqualTo(1);
        assertThat(store.getInsight(memoryId, 301L)).isEmpty();
        assertThat(store.getRootByType(memoryId, "profile")).isEmpty();
        assertThat(store.getBranchByType(memoryId, "profile"))
                .hasValueSatisfying(insight -> assertThat(insight.id()).isEqualTo(302L));
        assertThat(store.listInsights(memoryId))
                .extracting(MemoryInsight::id)
                .containsExactly(302L);
    }

    private DataSource dataSource(String fileName) {
        SQLiteDataSource sqliteDataSource = new SQLiteDataSource();
        sqliteDataSource.setUrl("jdbc:sqlite:" + tempDir.resolve(fileName));
        return sqliteDataSource;
    }

    private boolean tableExists(DataSource targetDataSource, String tableName) {
        try (Connection connection = targetDataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                "SELECT name FROM sqlite_master WHERE type='table' AND name = ?")) {
            statement.setString(1, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void executeUpdate(String sql, Object... params) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                statement.setObject(i + 1, params[i]);
            }
            statement.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private int deletedFlag(String tableName, Long bizId) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                "SELECT deleted FROM "
                                        + tableName
                                        + " WHERE user_id = ? AND agent_id = ? AND biz_id = ?")) {
            statement.setString(1, memoryId.userId());
            statement.setString(2, memoryId.agentId());
            statement.setLong(3, bizId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return -1;
                }
                return resultSet.getInt(1);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private MemoryRawData rawData(String id, String contentId, String vectorId, Instant createdAt) {
        return new MemoryRawData(
                id,
                memoryId.toIdentifier(),
                ContentTypes.CONVERSATION,
                contentId,
                new Segment(
                        "content-" + id,
                        "segment-" + id,
                        new CharBoundary(0, id.length()),
                        Map.of()),
                "caption-" + id,
                vectorId,
                Map.of("raw", id),
                createdAt,
                createdAt.minusSeconds(5),
                createdAt.plusSeconds(5));
    }

    private MemoryItem item(
            Long id, String content, String vectorId, String contentHash, String rawDataId) {
        return new MemoryItem(
                id,
                memoryId.toIdentifier(),
                content,
                MemoryScope.USER,
                MemoryCategory.PROFILE,
                ContentTypes.CONVERSATION,
                vectorId,
                rawDataId,
                contentHash,
                BASE_TIME.minusSeconds(60),
                BASE_TIME.minusSeconds(55),
                Map.of("origin", content),
                BASE_TIME.minusSeconds(30),
                MemoryItemType.FACT);
    }

    private MemoryInsight insight(
            Long id,
            String type,
            InsightTier tier,
            String group,
            Long parentInsightId,
            List<Long> childInsightIds,
            String pointContent) {
        return new MemoryInsight(
                id,
                memoryId.toIdentifier(),
                type,
                MemoryScope.USER,
                tier.name().toLowerCase() + "-title",
                List.of("profile"),
                List.of(point(pointContent)),
                group,
                0.7f,
                BASE_TIME.minusSeconds(10),
                List.of(0.1f, 0.2f),
                BASE_TIME.minusSeconds(20),
                BASE_TIME.minusSeconds(15),
                tier,
                parentInsightId,
                childInsightIds,
                1);
    }

    private InsightPoint point(String content) {
        return new InsightPoint(
                InsightPoint.PointType.SUMMARY,
                content,
                0.8f,
                List.of("101"),
                Map.of("kind", "summary"));
    }

    private int itemDeletedFlag(Long itemId) {
        return deletedFlag("memory_item", itemId);
    }

    private int insightDeletedFlag(Long insightId) {
        return deletedFlag("memory_insight", insightId);
    }

    private record TestMemoryId(String userId, String agentId) implements MemoryId {

        @Override
        public String toIdentifier() {
            return userId + ":" + agentId;
        }

        @Override
        public String getAttribute(String key) {
            return switch (key) {
                case "userId" -> userId;
                case "agentId" -> agentId;
                default -> null;
            };
        }
    }
}
