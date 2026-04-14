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
package com.openmemind.ai.memory.plugin.rawdata.toolcall.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.data.enums.MemoryItemType;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.store.item.ItemOperations;
import com.openmemind.ai.memory.plugin.rawdata.toolcall.content.ToolCallContent;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class DefaultToolCallStatsServiceTest {

    @Test
    void getToolStatsAggregatesMetadataForSingleTool() {
        var memoryId = DefaultMemoryId.of("u1", "a1");
        ItemOperations itemOperations = mock(ItemOperations.class);
        MemoryStore memoryStore = mock(MemoryStore.class);
        when(memoryStore.itemOperations()).thenReturn(itemOperations);
        when(itemOperations.listItems(memoryId))
                .thenReturn(
                        List.of(
                                item(
                                        memoryId,
                                        "grep",
                                        Map.of(
                                                "toolName",
                                                "grep",
                                                "callCount",
                                                "2",
                                                "successCount",
                                                "1",
                                                "avgDurationMs",
                                                "150",
                                                "score",
                                                "0.8")),
                                item(
                                        memoryId,
                                        "grep",
                                        Map.of(
                                                "toolName",
                                                "grep",
                                                "callCount",
                                                "1",
                                                "successCount",
                                                "1",
                                                "avgDurationMs",
                                                "300",
                                                "score",
                                                "1.0"))));

        var service = new DefaultToolCallStatsService(memoryStore);

        StepVerifier.create(service.getToolStats(memoryId, "grep"))
                .assertNext(
                        stats -> {
                            assertThat(stats.totalCalls()).isEqualTo(3);
                            assertThat(stats.successRate()).isEqualTo(2.0 / 3.0);
                            assertThat(stats.avgTimeCost()).isEqualTo(200.0);
                            assertThat(stats.avgScore()).isEqualTo(0.9);
                        })
                .verifyComplete();
    }

    @Test
    void getAllToolStatsGroupsByToolName() {
        var memoryId = DefaultMemoryId.of("u1", "a1");
        ItemOperations itemOperations = mock(ItemOperations.class);
        MemoryStore memoryStore = mock(MemoryStore.class);
        when(memoryStore.itemOperations()).thenReturn(itemOperations);
        when(itemOperations.listItems(memoryId))
                .thenReturn(
                        List.of(
                                item(
                                        memoryId,
                                        "grep",
                                        Map.of(
                                                "toolName",
                                                "grep",
                                                "callCount",
                                                "2",
                                                "successCount",
                                                "2",
                                                "avgDurationMs",
                                                "100")),
                                item(
                                        memoryId,
                                        "search",
                                        Map.of(
                                                "toolName",
                                                "search",
                                                "callCount",
                                                "1",
                                                "successCount",
                                                "0",
                                                "avgDurationMs",
                                                "250"))));

        var service = new DefaultToolCallStatsService(memoryStore);

        StepVerifier.create(service.getAllToolStats(memoryId))
                .assertNext(
                        allStats -> {
                            assertThat(allStats).containsOnlyKeys("grep", "search");
                            assertThat(allStats.get("grep").successRate()).isEqualTo(1.0);
                            assertThat(allStats.get("search").successRate()).isZero();
                        })
                .verifyComplete();
    }

    private static MemoryItem item(
            DefaultMemoryId memoryId, String content, Map<String, Object> metadata) {
        Instant now = Instant.parse("2026-04-12T00:00:00Z");
        return new MemoryItem(
                1L,
                memoryId.toIdentifier(),
                content,
                MemoryScope.AGENT,
                MemoryCategory.TOOL,
                ToolCallContent.TYPE,
                null,
                "raw-1",
                "hash-1",
                now,
                now,
                metadata,
                now,
                MemoryItemType.FACT);
    }
}
