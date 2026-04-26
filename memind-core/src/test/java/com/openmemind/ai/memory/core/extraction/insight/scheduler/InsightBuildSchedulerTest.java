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
package com.openmemind.ai.memory.core.extraction.insight.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openmemind.ai.memory.core.buffer.InMemoryInsightBuffer;
import com.openmemind.ai.memory.core.data.InsightPoint;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryInsight;
import com.openmemind.ai.memory.core.data.MemoryInsightType;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.PointOperation;
import com.openmemind.ai.memory.core.data.enums.InsightAnalysisMode;
import com.openmemind.ai.memory.core.data.enums.MemoryItemType;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.extraction.insight.generator.InsightGenerator;
import com.openmemind.ai.memory.core.extraction.insight.generator.InsightPointGenerateResponse;
import com.openmemind.ai.memory.core.extraction.insight.generator.InsightPointOpsResponse;
import com.openmemind.ai.memory.core.extraction.insight.graph.InsightGraphAssistContext.GroupingAssist;
import com.openmemind.ai.memory.core.extraction.insight.graph.InsightGraphAssistContext.LeafAssist;
import com.openmemind.ai.memory.core.extraction.insight.graph.InsightGraphAssistant;
import com.openmemind.ai.memory.core.extraction.insight.graph.NoOpInsightGraphAssistant;
import com.openmemind.ai.memory.core.extraction.insight.group.InsightGroupClassifier;
import com.openmemind.ai.memory.core.extraction.insight.group.InsightGroupRouter;
import com.openmemind.ai.memory.core.extraction.insight.tree.InsightTreeReorganizer;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.store.insight.InsightOperations;
import com.openmemind.ai.memory.core.store.item.ItemOperations;
import com.openmemind.ai.memory.core.utils.IdUtils;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
@DisplayName("InsightBuildScheduler")
class InsightBuildSchedulerTest {

    private static final MemoryId MEMORY_ID = () -> "memory-1";
    private static final String TYPE_NAME = "identity";
    private static final String GROUP_NAME = "group-a";

    @Mock private MemoryStore store;
    @Mock private InsightGenerator generator;
    @Mock private InsightGroupClassifier groupClassifier;
    @Mock private InsightGroupRouter groupRouter;
    @Mock private InsightGraphAssistant graphAssistant;
    @Mock private InsightTreeReorganizer treeReorganizer;
    @Mock private ItemOperations itemOperations;
    @Mock private InsightOperations insightOperations;

    private InMemoryInsightBuffer buffer;
    private InsightBuildScheduler scheduler;
    private MemoryInsightType insightType;

    @BeforeEach
    void setUp() {
        buffer = new InMemoryInsightBuffer();
        scheduler =
                new InsightBuildScheduler(
                        buffer,
                        store,
                        generator,
                        groupClassifier,
                        groupRouter,
                        treeReorganizer,
                        null,
                        IdUtils.snowflake(),
                        InsightBuildConfig.defaults());
        scheduler =
                new InsightBuildScheduler(
                        buffer,
                        store,
                        generator,
                        groupClassifier,
                        groupRouter,
                        treeReorganizer,
                        null,
                        IdUtils.snowflake(),
                        InsightBuildConfig.defaults(),
                        graphAssistant,
                        null);
        insightType =
                new MemoryInsightType(
                        1L,
                        TYPE_NAME,
                        "Identity insights",
                        null,
                        List.of("profile"),
                        300,
                        null,
                        null,
                        null,
                        InsightAnalysisMode.BRANCH,
                        null,
                        MemoryScope.USER);

        when(store.itemOperations()).thenReturn(itemOperations);
        when(store.insightOperations()).thenReturn(insightOperations);
        when(insightOperations.getInsightType(TYPE_NAME)).thenReturn(Optional.of(insightType));
        lenient()
                .when(graphAssistant.groupingAssist(any(), any(), anyList()))
                .thenReturn(GroupingAssist.empty());
        lenient()
                .when(graphAssistant.leafAssist(any(), any(), anyString(), anyList()))
                .thenAnswer(invocation -> new LeafAssist(invocation.getArgument(3), ""));
    }

    @Test
    @DisplayName(
            "flushSync should route null-category items through semantic grouping with graph"
                    + " context")
    void nullCategorySemanticGroupingShouldRouteThroughSemanticPathWithGraphContext() {
        seedUngroupedBuffer(1L);
        when(itemOperations.getItemsByIds(eq(MEMORY_ID), anyList())).thenReturn(List.of(item(1L)));
        when(graphAssistant.groupingAssist(any(), any(), anyList()))
                .thenReturn(new GroupingAssist("GraphGroupingHints: cluster alpha"));
        when(groupRouter.groupSemantic(
                        any(),
                        anyList(),
                        anyList(),
                        eq("GraphGroupingHints: cluster alpha"),
                        eq("English")))
                .thenReturn(Mono.just(Map.of()));

        scheduler.flushSync(MEMORY_ID, TYPE_NAME, "English");

        verify(groupRouter)
                .groupSemantic(
                        eq(insightType),
                        anyList(),
                        anyList(),
                        eq("GraphGroupingHints: cluster alpha"),
                        eq("English"));
        verify(groupClassifier, never())
                .classify(any(), anyList(), anyList(), anyString(), anyString());
    }

    @Test
    @DisplayName("flushSync should pass graph grouping context to categorized semantic routing")
    void categorizedSemanticGroupingShouldPassGroupingAssistContextToRouter() {
        seedUngroupedBuffer(1L);
        var eventItem =
                item(1L, com.openmemind.ai.memory.core.data.enums.MemoryCategory.EVENT, null);
        when(itemOperations.getItemsByIds(eq(MEMORY_ID), anyList())).thenReturn(List.of(eventItem));
        when(graphAssistant.groupingAssist(any(), any(), anyList()))
                .thenReturn(new GroupingAssist("GraphGroupingHints: cluster alpha"));
        when(groupRouter.group(
                        any(),
                        any(),
                        anyList(),
                        anyList(),
                        eq("GraphGroupingHints: cluster alpha"),
                        eq("English")))
                .thenReturn(Mono.just(Map.of()));

        scheduler.flushSync(MEMORY_ID, TYPE_NAME, "English");

        verify(groupRouter)
                .group(
                        eq(insightType),
                        eq(com.openmemind.ai.memory.core.data.enums.MemoryCategory.EVENT),
                        anyList(),
                        anyList(),
                        eq("GraphGroupingHints: cluster alpha"),
                        eq("English"));
    }

    @Test
    @DisplayName("flushSync should keep metadata grouping primary keys untouched")
    void metadataGroupingShouldKeepPrimaryGroupKeysUntouched() {
        seedUngroupedBuffer(1L);
        var toolItem =
                item(
                        1L,
                        com.openmemind.ai.memory.core.data.enums.MemoryCategory.TOOL,
                        Map.of("toolName", "web_search"));
        when(itemOperations.getItemsByIds(eq(MEMORY_ID), anyList())).thenReturn(List.of(toolItem));
        when(groupRouter.group(
                        any(),
                        any(),
                        anyList(),
                        anyList(),
                        argThat((String context) -> context == null || context.isBlank()),
                        eq("English")))
                .thenReturn(Mono.just(Map.of()));

        scheduler.flushSync(MEMORY_ID, TYPE_NAME, "English");

        verify(graphAssistant, never()).groupingAssist(any(), any(), anyList());
        verify(groupRouter)
                .group(
                        eq(insightType),
                        eq(com.openmemind.ai.memory.core.data.enums.MemoryCategory.TOOL),
                        anyList(),
                        anyList(),
                        argThat((String context) -> context == null || context.isBlank()),
                        eq("English"));
        verify(groupClassifier, never())
                .classify(any(), anyList(), anyList(), anyString(), anyString());
    }

    @Test
    @DisplayName("flushSync should backfill missing point ids before generating point ops")
    void buildLeafForGroupBackfillsMissingPointIdsBeforePointOps() {
        seedGroupedBuffer(1L);
        when(itemOperations.getItemsByIds(eq(MEMORY_ID), anyList())).thenReturn(List.of(item(1L)));
        when(insightOperations.getLeafByGroup(MEMORY_ID, TYPE_NAME, GROUP_NAME))
                .thenReturn(Optional.of(existingLegacyLeaf("legacy point")));
        when(generator.generateLeafPointOps(
                        any(), anyString(), anyList(), anyList(), anyInt(), any(), any()))
                .thenAnswer(
                        invocation -> {
                            @SuppressWarnings("unchecked")
                            var existingPoints = (List<InsightPoint>) invocation.getArgument(2);
                            assertThat(existingPoints).allMatch(point -> point.pointId() != null);
                            return Mono.just(new InsightPointOpsResponse(List.of()));
                        });

        scheduler.flushSync(MEMORY_ID, TYPE_NAME, "English");

        verify(insightOperations).upsertInsights(eq(MEMORY_ID), anyList());
    }

    @Test
    @DisplayName("flushSync should use graph ordered items and pass leaf hint context")
    void leafBuildShouldUseGraphOrderedItemsAndPassLeafHintContext() {
        seedGroupedBuffer(1L, 2L);
        var item1 = item(1L);
        var item2 = item(2L);
        when(itemOperations.getItemsByIds(eq(MEMORY_ID), anyList()))
                .thenReturn(List.of(item1, item2));
        when(graphAssistant.leafAssist(any(), any(), eq(GROUP_NAME), anyList()))
                .thenReturn(
                        new LeafAssist(List.of(item2, item1), "GraphEvidenceHints: causal chain"));
        when(generator.generateLeafPointOps(
                        any(), anyString(), anyList(), anyList(), anyInt(), any(), any()))
                .thenReturn(Mono.just(new InsightPointOpsResponse(List.of())));

        scheduler.flushSync(MEMORY_ID, TYPE_NAME, "English");

        verify(generator)
                .generateLeafPointOps(
                        eq(insightType),
                        eq(GROUP_NAME),
                        anyList(),
                        argThat(items -> itemIdsMatch(items, List.of(2L, 1L))),
                        anyInt(),
                        eq("GraphEvidenceHints: causal chain"),
                        eq("English"));
    }

    @Test
    @DisplayName("disabled graph assist should keep legacy leaf ordering and null context")
    void disabledInsightGraphAssistShouldKeepLegacyLeafOrdering() {
        var disabledScheduler =
                new InsightBuildScheduler(
                        buffer,
                        store,
                        generator,
                        groupClassifier,
                        groupRouter,
                        treeReorganizer,
                        null,
                        IdUtils.snowflake(),
                        InsightBuildConfig.defaults(),
                        NoOpInsightGraphAssistant.INSTANCE,
                        null);
        seedGroupedBuffer(1L, 2L);
        var item1 = item(1L);
        var item2 = item(2L);
        when(itemOperations.getItemsByIds(eq(MEMORY_ID), anyList()))
                .thenReturn(List.of(item1, item2));
        when(generator.generateLeafPointOps(
                        any(), anyString(), anyList(), anyList(), anyInt(), any(), any()))
                .thenReturn(Mono.just(new InsightPointOpsResponse(List.of())));

        disabledScheduler.flushSync(MEMORY_ID, TYPE_NAME, "English");

        verify(generator)
                .generateLeafPointOps(
                        eq(insightType),
                        eq(GROUP_NAME),
                        anyList(),
                        argThat(items -> itemIdsMatch(items, List.of(1L, 2L))),
                        anyInt(),
                        isNull(),
                        eq("English"));
    }

    @Test
    @DisplayName(
            "flushSync should reuse graph ordered items and hint context for full rewrite fallback")
    void leafFullRewriteFallbackShouldReuseGraphOrderedItemsAndLeafHintContext() {
        seedGroupedBuffer(1L, 2L);
        var item1 = item(1L);
        var item2 = item(2L);
        when(itemOperations.getItemsByIds(eq(MEMORY_ID), anyList()))
                .thenReturn(List.of(item1, item2));
        when(graphAssistant.leafAssist(any(), any(), eq(GROUP_NAME), anyList()))
                .thenReturn(
                        new LeafAssist(List.of(item2, item1), "GraphEvidenceHints: causal chain"));
        when(generator.generateLeafPointOps(
                        any(), anyString(), anyList(), anyList(), anyInt(), any(), any()))
                .thenReturn(Mono.empty());
        when(generator.generatePoints(
                        any(), anyString(), anyList(), anyList(), anyInt(), any(), any()))
                .thenReturn(
                        Mono.just(
                                new InsightPointGenerateResponse(
                                        List.of(
                                                new InsightPoint(
                                                        InsightPoint.PointType.SUMMARY,
                                                        "fallback",
                                                        List.of("1", "2"))))));

        scheduler.flushSync(MEMORY_ID, TYPE_NAME, "English");

        verify(generator)
                .generatePoints(
                        eq(insightType),
                        eq(GROUP_NAME),
                        anyList(),
                        argThat(items -> itemIdsMatch(items, List.of(2L, 1L))),
                        anyInt(),
                        eq("GraphEvidenceHints: causal chain"),
                        eq("English"));
    }

    @Test
    @DisplayName("flushSync should not persist or reorganize when leaf ops resolve to noop")
    void buildLeafForGroupDoesNotPersistAndDoesNotEnterTreeReorganizeWhenOpsResolveToNoop() {
        seedGroupedBuffer(1L);
        when(itemOperations.getItemsByIds(eq(MEMORY_ID), anyList())).thenReturn(List.of(item(1L)));
        when(insightOperations.getLeafByGroup(MEMORY_ID, TYPE_NAME, GROUP_NAME))
                .thenReturn(Optional.of(existingLeaf("existing")));
        when(generator.generateLeafPointOps(
                        any(), anyString(), anyList(), anyList(), anyInt(), any(), any()))
                .thenReturn(Mono.just(new InsightPointOpsResponse(List.of())));

        scheduler.flushSync(MEMORY_ID, TYPE_NAME, "English");

        verify(generator, never())
                .generatePoints(any(), anyString(), anyList(), anyList(), anyInt(), any(), any());
        verify(insightOperations, never()).upsertInsights(eq(MEMORY_ID), anyList());
        verify(treeReorganizer, never())
                .onLeafsUpdated(any(), anyString(), any(), anyList(), any(), any());
    }

    @Test
    @DisplayName("flushSync should fall back to full rewrite when ops response is missing")
    void buildLeafForGroupFallsBackToFullRewriteWhenOpsResponseIsMissing() {
        seedGroupedBuffer(1L);
        when(itemOperations.getItemsByIds(eq(MEMORY_ID), anyList())).thenReturn(List.of(item(1L)));
        when(insightOperations.getLeafByGroup(MEMORY_ID, TYPE_NAME, GROUP_NAME))
                .thenReturn(Optional.of(existingLeaf("existing")));
        when(generator.generateLeafPointOps(
                        any(), anyString(), anyList(), anyList(), anyInt(), any(), any()))
                .thenReturn(Mono.empty());
        when(generator.generatePoints(
                        any(), anyString(), anyList(), anyList(), anyInt(), any(), any()))
                .thenReturn(
                        Mono.just(
                                new InsightPointGenerateResponse(
                                        List.of(
                                                new InsightPoint(
                                                        InsightPoint.PointType.SUMMARY,
                                                        "fallback",
                                                        List.of("1", "2"))))));

        scheduler.flushSync(MEMORY_ID, TYPE_NAME, "English");

        verify(generator)
                .generatePoints(any(), anyString(), anyList(), anyList(), anyInt(), any(), any());
        verify(insightOperations).upsertInsights(eq(MEMORY_ID), anyList());
    }

    @Test
    @DisplayName("flushSync should fall back to full rewrite when ops resolution requests fallback")
    void buildLeafForGroupFallsBackToFullRewriteWhenOpsResolutionRequestsFallback() {
        seedGroupedBuffer(1L);
        when(itemOperations.getItemsByIds(eq(MEMORY_ID), anyList())).thenReturn(List.of(item(1L)));
        when(insightOperations.getLeafByGroup(MEMORY_ID, TYPE_NAME, GROUP_NAME))
                .thenReturn(Optional.of(existingLeaf("existing")));
        when(generator.generateLeafPointOps(
                        any(), anyString(), anyList(), anyList(), anyInt(), any(), any()))
                .thenReturn(
                        Mono.just(
                                new InsightPointOpsResponse(
                                        List.of(
                                                new PointOperation(
                                                        PointOperation.OpType.DELETE,
                                                        "pt_existing",
                                                        null,
                                                        "drop")))));
        when(generator.generatePoints(
                        any(), anyString(), anyList(), anyList(), anyInt(), any(), any()))
                .thenReturn(
                        Mono.just(
                                new InsightPointGenerateResponse(
                                        List.of(
                                                new InsightPoint(
                                                        InsightPoint.PointType.SUMMARY,
                                                        "fallback",
                                                        List.of("1", "2"))))));

        scheduler.flushSync(MEMORY_ID, TYPE_NAME, "English");

        verify(generator)
                .generatePoints(any(), anyString(), anyList(), anyList(), anyInt(), any(), any());
        verify(insightOperations).upsertInsights(eq(MEMORY_ID), anyList());
    }

    @Test
    @DisplayName("flushSync should fall back to full rewrite when all ops are invalid")
    void buildLeafForGroupFallsBackToFullRewriteWhenAllOpsAreInvalid() {
        seedGroupedBuffer(1L);
        when(itemOperations.getItemsByIds(eq(MEMORY_ID), anyList())).thenReturn(List.of(item(1L)));
        when(insightOperations.getLeafByGroup(MEMORY_ID, TYPE_NAME, GROUP_NAME))
                .thenReturn(Optional.of(existingLeaf("existing")));
        when(generator.generateLeafPointOps(
                        any(), anyString(), anyList(), anyList(), anyInt(), any(), any()))
                .thenReturn(
                        Mono.just(
                                new InsightPointOpsResponse(
                                        List.of(
                                                new PointOperation(
                                                        PointOperation.OpType.UPDATE,
                                                        "pt_missing",
                                                        new InsightPoint(
                                                                InsightPoint.PointType.SUMMARY,
                                                                "invalid",
                                                                List.of("1", "2")),
                                                        "bad target")))));
        when(generator.generatePoints(
                        any(), anyString(), anyList(), anyList(), anyInt(), any(), any()))
                .thenReturn(
                        Mono.just(
                                new InsightPointGenerateResponse(
                                        List.of(
                                                new InsightPoint(
                                                        InsightPoint.PointType.SUMMARY,
                                                        "fallback",
                                                        List.of("1", "2"))))));

        scheduler.flushSync(MEMORY_ID, TYPE_NAME, "English");

        verify(generator)
                .generatePoints(any(), anyString(), anyList(), anyList(), anyInt(), any(), any());
        verify(insightOperations).upsertInsights(eq(MEMORY_ID), anyList());
    }

    private void seedGroupedBuffer(Long... itemIds) {
        var ids = List.of(itemIds);
        buffer.append(MEMORY_ID, TYPE_NAME, ids);
        buffer.assignGroup(MEMORY_ID, TYPE_NAME, ids, GROUP_NAME);
    }

    private void seedUngroupedBuffer(Long itemId) {
        buffer.append(MEMORY_ID, TYPE_NAME, List.of(itemId));
    }

    private static MemoryItem item(Long id) {
        return item(id, null, null);
    }

    private static MemoryItem item(
            Long id,
            com.openmemind.ai.memory.core.data.enums.MemoryCategory category,
            Map<String, Object> metadata) {
        return new MemoryItem(
                id,
                MEMORY_ID.toIdentifier(),
                "item-" + id,
                MemoryScope.USER,
                category,
                "text/plain",
                null,
                null,
                null,
                Instant.now(),
                Instant.now(),
                metadata,
                Instant.now(),
                MemoryItemType.FACT);
    }

    private static boolean itemIdsMatch(List<MemoryItem> items, List<Long> expectedIds) {
        return items.stream().map(MemoryItem::id).toList().equals(expectedIds);
    }

    private static MemoryInsight existingLeaf(String content) {
        return new MemoryInsight(
                10L,
                MEMORY_ID.toIdentifier(),
                TYPE_NAME,
                MemoryScope.USER,
                "Leaf summary",
                List.of("profile"),
                List.of(
                        new InsightPoint(
                                "pt_existing",
                                InsightPoint.PointType.SUMMARY,
                                content,
                                List.of("1", "2"))),
                GROUP_NAME,
                Instant.now(),
                null,
                Instant.now(),
                Instant.now(),
                com.openmemind.ai.memory.core.data.enums.InsightTier.LEAF,
                null,
                List.of(),
                1);
    }

    private static MemoryInsight existingLegacyLeaf(String content) {
        return new MemoryInsight(
                10L,
                MEMORY_ID.toIdentifier(),
                TYPE_NAME,
                MemoryScope.USER,
                "Leaf summary",
                List.of("profile"),
                List.of(
                        new InsightPoint(
                                InsightPoint.PointType.SUMMARY, content, List.of("1", "2"))),
                GROUP_NAME,
                Instant.now(),
                null,
                Instant.now(),
                Instant.now(),
                com.openmemind.ai.memory.core.data.enums.InsightTier.LEAF,
                null,
                List.of(),
                1);
    }
}
