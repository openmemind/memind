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
package com.openmemind.ai.memory.core.extraction.insight.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import com.openmemind.ai.memory.core.builder.InsightGraphAssistOptions;
import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryInsightType;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.enums.InsightAnalysisMode;
import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.data.enums.MemoryItemType;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.store.graph.GraphEntity;
import com.openmemind.ai.memory.core.store.graph.GraphEntityType;
import com.openmemind.ai.memory.core.store.graph.GraphOperations;
import com.openmemind.ai.memory.core.store.graph.InMemoryGraphOperations;
import com.openmemind.ai.memory.core.store.graph.ItemEntityMention;
import com.openmemind.ai.memory.core.store.graph.ItemLink;
import com.openmemind.ai.memory.core.store.graph.ItemLinkType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultInsightGraphAssistantTest {

    private static final MemoryId MEMORY_ID = DefaultMemoryId.of("user-1", "agent-1");
    private static final Instant NOW = Instant.parse("2026-04-16T00:00:00Z");

    @Mock private MemoryStore store;
    @Mock private GraphOperations graphOperations;

    private MemoryInsightType insightType;

    @BeforeEach
    void setUp() {
        insightType =
                new MemoryInsightType(
                        1L,
                        "identity",
                        "Identity insights",
                        null,
                        List.of("profile"),
                        300,
                        NOW,
                        NOW,
                        NOW,
                        InsightAnalysisMode.BRANCH,
                        null,
                        MemoryScope.USER);
    }

    @Test
    void groupingAssistShouldEmitBoundedClusterHintsAndSkipSpecialEntities() {
        var graph = new InMemoryGraphOperations();
        seedGraph(graph);
        when(store.graphOperations()).thenReturn(graph);
        var assistant =
                new DefaultInsightGraphAssistant(
                        store,
                        InsightGraphAssistOptions.defaults().withEnabled(true),
                        new GraphPromptContextFormatter(InsightGraphAssistOptions.defaults()));

        var assist =
                assistant.groupingAssist(
                        MEMORY_ID, insightType, List.of(item(1L), item(2L), item(3L)));

        assertThat(assist.additionalContext()).contains("GraphGroupingHints");
        assertThat(assist.additionalContext()).contains("OpenAI");
        assertThat(assist.additionalContext()).doesNotContain("special:user");
        assertThat(assist.additionalContext().length()).isLessThanOrEqualTo(1200);
    }

    @Test
    void leafAssistShouldPreferTemporalCausalOrderBeforeCentralityFallback() {
        var graph = new InMemoryGraphOperations();
        seedGraph(graph);
        when(store.graphOperations()).thenReturn(graph);
        var assistant =
                new DefaultInsightGraphAssistant(
                        store,
                        InsightGraphAssistOptions.defaults().withEnabled(true),
                        new GraphPromptContextFormatter(InsightGraphAssistOptions.defaults()));

        var assist =
                assistant.leafAssist(
                        MEMORY_ID,
                        insightType,
                        "project-thread",
                        List.of(item(3L), item(1L), item(2L)));

        assertThat(assist.orderedItems()).extracting(MemoryItem::id).containsExactly(1L, 2L, 3L);
        assertThat(assist.additionalContext()).contains("GraphEvidenceHints");
    }

    @Test
    void graphReadFailuresShouldReturnIdentityContextsInsteadOfFailing() {
        when(store.graphOperations()).thenReturn(graphOperations);
        when(graphOperations.listItemEntityMentions(any(), anyList()))
                .thenThrow(new IllegalStateException("boom"));
        var assistant =
                new DefaultInsightGraphAssistant(
                        store,
                        InsightGraphAssistOptions.defaults().withEnabled(true),
                        new GraphPromptContextFormatter(InsightGraphAssistOptions.defaults()));
        var input = List.of(item(3L), item(1L), item(2L));

        var assist = assistant.leafAssist(MEMORY_ID, insightType, "project-thread", input);

        assertThat(assist.additionalContext()).isBlank();
        assertThat(assist.orderedItems()).containsExactlyElementsOf(input);
    }

    private static void seedGraph(InMemoryGraphOperations graph) {
        graph.upsertEntities(
                MEMORY_ID,
                List.of(
                        entity("organization:openai", "OpenAI", GraphEntityType.ORGANIZATION),
                        entity("concept:atlas", "Atlas", GraphEntityType.CONCEPT),
                        entity("special:user", "User", GraphEntityType.SPECIAL)));
        graph.upsertItemEntityMentions(
                MEMORY_ID,
                List.of(
                        mention(1L, "organization:openai"),
                        mention(1L, "concept:atlas"),
                        mention(1L, "special:user"),
                        mention(2L, "organization:openai"),
                        mention(2L, "concept:atlas"),
                        mention(3L, "organization:openai")));
        graph.upsertItemLinks(
                MEMORY_ID,
                List.of(
                        link(1L, 2L, ItemLinkType.CAUSAL, 0.92d),
                        link(2L, 3L, ItemLinkType.TEMPORAL, 0.88d),
                        link(1L, 3L, ItemLinkType.SEMANTIC, 0.51d)));
    }

    private static MemoryItem item(long id) {
        var occurredAt =
                switch ((int) id) {
                    case 1 -> Instant.parse("2026-04-15T09:00:00Z");
                    case 2 -> Instant.parse("2026-04-15T10:00:00Z");
                    default -> Instant.parse("2026-04-16T09:00:00Z");
                };
        return new MemoryItem(
                id,
                MEMORY_ID.toIdentifier(),
                "item-" + id,
                MemoryScope.USER,
                MemoryCategory.EVENT,
                "conversation",
                "vector-" + id,
                "raw-" + id,
                "hash-" + id,
                occurredAt,
                occurredAt,
                Map.of(),
                NOW,
                MemoryItemType.FACT);
    }

    private static GraphEntity entity(
            String entityKey, String displayName, GraphEntityType entityType) {
        return new GraphEntity(
                entityKey, MEMORY_ID.toIdentifier(), displayName, entityType, Map.of(), NOW, NOW);
    }

    private static ItemEntityMention mention(long itemId, String entityKey) {
        return new ItemEntityMention(
                MEMORY_ID.toIdentifier(), itemId, entityKey, 1.0f, Map.of(), NOW);
    }

    private static ItemLink link(
            long sourceItemId, long targetItemId, ItemLinkType linkType, double strength) {
        return new ItemLink(
                MEMORY_ID.toIdentifier(),
                sourceItemId,
                targetItemId,
                linkType,
                defaultRelationCode(linkType),
                defaultEvidenceSource(linkType),
                strength,
                Map.of(),
                NOW);
    }

    private static String defaultRelationCode(ItemLinkType linkType) {
        return switch (linkType) {
            case SEMANTIC -> null;
            case TEMPORAL -> "before";
            case CAUSAL -> "caused_by";
        };
    }

    private static String defaultEvidenceSource(ItemLinkType linkType) {
        return linkType == ItemLinkType.SEMANTIC ? "vector_search" : null;
    }
}
