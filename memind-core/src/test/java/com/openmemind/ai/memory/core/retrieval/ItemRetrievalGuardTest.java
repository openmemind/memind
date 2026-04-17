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
package com.openmemind.ai.memory.core.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.data.enums.MemoryItemType;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.retrieval.query.QueryContext;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ItemRetrievalGuardTest {

    private static final MemoryId MEMORY_ID = DefaultMemoryId.of("user-1", "agent-1");
    private static final Instant NOW = Instant.parse("2026-04-16T00:00:00Z");

    @Test
    void itemRetrievalGuardAppliesScopeCategoryAndForesightFiltersTogether() {
        var context =
                new QueryContext(
                        MEMORY_ID,
                        "q",
                        null,
                        List.of(),
                        Map.of(),
                        MemoryScope.AGENT,
                        Set.of(MemoryCategory.TOOL));

        assertThat(ItemRetrievalGuard.allows(agentToolFact(), context)).isTrue();
        assertThat(ItemRetrievalGuard.allows(userEventFact(), context)).isFalse();
        assertThat(ItemRetrievalGuard.allows(expiredAgentToolForesight(), context)).isFalse();
    }

    private static MemoryItem agentToolFact() {
        return item(101L, MemoryScope.AGENT, MemoryCategory.TOOL, MemoryItemType.FACT, Map.of());
    }

    private static MemoryItem userEventFact() {
        return item(102L, MemoryScope.USER, MemoryCategory.EVENT, MemoryItemType.FACT, Map.of());
    }

    private static MemoryItem expiredAgentToolForesight() {
        return item(
                103L,
                MemoryScope.AGENT,
                MemoryCategory.TOOL,
                MemoryItemType.FORESIGHT,
                Map.of("validUntil", "2024-01-01"));
    }

    private static MemoryItem item(
            long id,
            MemoryScope scope,
            MemoryCategory category,
            MemoryItemType type,
            Map<String, Object> metadata) {
        return new MemoryItem(
                id,
                MEMORY_ID.toIdentifier(),
                "item-" + id,
                scope,
                category,
                "text/plain",
                "vec-" + id,
                "raw-" + id,
                "hash-" + id,
                NOW,
                NOW,
                metadata,
                NOW,
                type);
    }
}
