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
package com.openmemind.ai.memory.core.extraction.item.graph.link.causal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.openmemind.ai.memory.core.builder.ItemGraphOptions;
import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.data.enums.MemoryItemType;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.extraction.item.support.ExtractedGraphHints;
import com.openmemind.ai.memory.core.extraction.item.support.ExtractedMemoryEntry;
import com.openmemind.ai.memory.core.store.graph.ItemLink;
import com.openmemind.ai.memory.core.store.graph.ItemLinkType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CausalHintNormalizerTest {

    private static final MemoryId MEMORY_ID = DefaultMemoryId.of("user-1", "agent-1");
    private static final Instant CREATED_AT = Instant.parse("2026-04-16T00:00:00Z");

    @Test
    void normalizeShouldKeepOnlyBackwardLinksWithSupportedRelationTypes() {
        var items = List.of(item(101L, "Cause"), item(102L, "Effect"), item(103L, "Future target"));
        var entries =
                List.of(
                        entry("Cause", List.of()),
                        entry(
                                "Effect",
                                List.of(
                                        causalHint(0, "CAUSED_BY", 0.95f),
                                        causalHint(1, "caused_by", 0.99f),
                                        causalHint(2, "caused_by", 0.98f),
                                        causalHint(0, "related_to", 0.97f),
                                        causalHint(0, null, 0.96f))),
                        entry("Future target", List.of()));

        var links =
                new CausalHintNormalizer()
                        .normalize(
                                MEMORY_ID,
                                items,
                                entries,
                                ItemGraphOptions.defaults().withEnabled(true));

        assertThat(links)
                .extracting(
                        ItemLink::linkType,
                        ItemLink::sourceItemId,
                        ItemLink::targetItemId,
                        link -> link.metadata().get("relationType"))
                .containsExactly(tuple(ItemLinkType.CAUSAL, 101L, 102L, "caused_by"));
    }

    @Test
    void normalizeShouldTrimPerSourceByStrengthAcrossTheBatch() {
        var items =
                List.of(
                        item(101L, "Shared cause"),
                        item(102L, "Weaker effect"),
                        item(103L, "Stronger effect"),
                        item(104L, "Second cause"),
                        item(105L, "Independent effect"));
        var entries =
                List.of(
                        entry("Shared cause", List.of()),
                        entry("Weaker effect", List.of(causalHint(0, "enabled_by", 0.61f))),
                        entry("Stronger effect", List.of(causalHint(0, "motivated_by", 0.91f))),
                        entry("Second cause", List.of()),
                        entry("Independent effect", List.of(causalHint(3, "caused_by", 0.75f))));

        var options = new ItemGraphOptions(true, 8, 1, 10, 5, 0.82d, 4, 1, 128);
        var links = new CausalHintNormalizer().normalize(MEMORY_ID, items, entries, options);

        assertThat(links)
                .extracting(
                        ItemLink::sourceItemId,
                        ItemLink::targetItemId,
                        ItemLink::strength,
                        link -> link.metadata().get("relationType"))
                .containsExactly(
                        tuple(101L, 103L, Double.valueOf(0.91f), "motivated_by"),
                        tuple(104L, 105L, 0.75d, "caused_by"));
    }

    @Test
    void normalizeDropsWeakAndMissingStrengthButKeepsThresholdBoundary() {
        var items =
                List.of(
                        item(101L, "Primary cause"),
                        item(102L, "Dropped effect"),
                        item(103L, "Boundary effect"),
                        item(104L, "Secondary cause"),
                        item(105L, "Strong effect"));
        var entries =
                List.of(
                        entry("Primary cause", List.of()),
                        entry(
                                "Dropped effect",
                                List.of(
                                        causalHint(0, "caused_by", null),
                                        causalHint(0, "caused_by", 0.49f))),
                        entry("Boundary effect", List.of(causalHint(0, "enabled_by", 0.50f))),
                        entry("Secondary cause", List.of()),
                        entry("Strong effect", List.of(causalHint(3, "motivated_by", 0.91f))));

        var links =
                new CausalHintNormalizer()
                        .normalize(
                                MEMORY_ID,
                                items,
                                entries,
                                ItemGraphOptions.defaults().withEnabled(true));

        assertThat(links)
                .extracting(
                        ItemLink::sourceItemId,
                        ItemLink::targetItemId,
                        ItemLink::strength,
                        link -> link.metadata().get("relationType"))
                .containsExactly(
                        tuple(101L, 103L, 0.5d, "enabled_by"),
                        tuple(104L, 105L, Double.valueOf(0.91f), "motivated_by"));
    }

    private static MemoryItem item(Long id, String content) {
        return new MemoryItem(
                id,
                MEMORY_ID.toIdentifier(),
                content,
                MemoryScope.USER,
                MemoryCategory.EVENT,
                "conversation",
                "vector-" + id,
                "raw-" + id,
                "hash-" + id,
                null,
                CREATED_AT,
                Map.of(),
                CREATED_AT,
                MemoryItemType.FACT);
    }

    private static ExtractedMemoryEntry entry(
            String content, List<ExtractedGraphHints.ExtractedCausalRelationHint> causalRelations) {
        return new ExtractedMemoryEntry(
                content,
                1.0f,
                null,
                null,
                null,
                null,
                CREATED_AT,
                "raw-1",
                null,
                List.of(),
                Map.of(),
                MemoryItemType.FACT,
                "event",
                new ExtractedGraphHints(List.of(), causalRelations));
    }

    private static ExtractedGraphHints.ExtractedCausalRelationHint causalHint(
            Integer targetIndex, String relationType, Float strength) {
        return new ExtractedGraphHints.ExtractedCausalRelationHint(
                targetIndex, relationType, strength);
    }
}
