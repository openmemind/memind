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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
import com.openmemind.ai.memory.core.extraction.insight.group.InsightGroupClassifier;
import com.openmemind.ai.memory.core.extraction.insight.group.InsightGroupRouter;
import com.openmemind.ai.memory.core.extraction.insight.tree.InsightTreeReorganizer;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.store.insight.InsightOperations;
import com.openmemind.ai.memory.core.store.item.ItemOperations;
import com.openmemind.ai.memory.core.utils.IdUtils;
import java.time.Instant;
import java.util.List;
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
                                                        0.9f,
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
                                                        1,
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
                                                        0.9f,
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
                                                        9,
                                                        new InsightPoint(
                                                                InsightPoint.PointType.SUMMARY,
                                                                "invalid",
                                                                0.9f,
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
                                                        0.9f,
                                                        List.of("1", "2"))))));

        scheduler.flushSync(MEMORY_ID, TYPE_NAME, "English");

        verify(generator)
                .generatePoints(any(), anyString(), anyList(), anyList(), anyInt(), any(), any());
        verify(insightOperations).upsertInsights(eq(MEMORY_ID), anyList());
    }

    private void seedGroupedBuffer(Long itemId) {
        buffer.append(MEMORY_ID, TYPE_NAME, List.of(itemId));
        buffer.assignGroup(MEMORY_ID, TYPE_NAME, List.of(itemId), GROUP_NAME);
    }

    private static MemoryItem item(Long id) {
        return new MemoryItem(
                id,
                MEMORY_ID.toIdentifier(),
                "item-" + id,
                MemoryScope.USER,
                null,
                "text/plain",
                null,
                null,
                null,
                Instant.now(),
                Instant.now(),
                null,
                Instant.now(),
                MemoryItemType.FACT);
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
                                InsightPoint.PointType.SUMMARY, content, 0.8f, List.of("1", "2"))),
                GROUP_NAME,
                0.8f,
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
