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
package com.openmemind.ai.memory.core.extraction.insight.buffer;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.data.MemoryId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("InMemoryInsightBufferStore Test")
class InMemoryInsightBufferStoreTest {

    private InMemoryInsightBufferStore store;
    private static final MemoryId MEMORY_ID = () -> "test-memory";
    private static final String TYPE = "preference";

    @BeforeEach
    void setUp() {
        store = new InMemoryInsightBufferStore();
    }

    @Nested
    @DisplayName("append + countUnGrouped")
    class AppendAndCount {

        @Test
        @DisplayName("The number of ungrouped items is correct after appending")
        void appendIncreasesUnGroupedCount() {
            store.append(MEMORY_ID, TYPE, List.of(1L, 2L, 3L));
            assertThat(store.countUnGrouped(MEMORY_ID, TYPE)).isEqualTo(3);
        }

        @Test
        @DisplayName("Multiple appends accumulate")
        void multipleAppendsAccumulate() {
            store.append(MEMORY_ID, TYPE, List.of(1L, 2L));
            store.append(MEMORY_ID, TYPE, List.of(3L));
            assertThat(store.countUnGrouped(MEMORY_ID, TYPE)).isEqualTo(3);
        }

        @Test
        @DisplayName("Empty buffer returns zero")
        void emptyBufferReturnsZero() {
            assertThat(store.countUnGrouped(MEMORY_ID, TYPE)).isZero();
        }

        @Test
        @DisplayName("getUnGrouped returns ungrouped entries")
        void getUnGroupedReturnsEntries() {
            store.append(MEMORY_ID, TYPE, List.of(10L, 20L));
            var entries = store.getUnGrouped(MEMORY_ID, TYPE);
            assertThat(entries)
                    .hasSize(2)
                    .allMatch(BufferEntry::isUngrouped)
                    .extracting(BufferEntry::itemId)
                    .containsExactly(10L, 20L);
        }
    }

    @Nested
    @DisplayName("assignGroup")
    class AssignGroup {

        @Test
        @DisplayName(
                "After grouping, remove from ungrouped and appear in the unbuilt list within the"
                        + " group")
        void assignGroupMovesEntries() {
            store.append(MEMORY_ID, TYPE, List.of(1L, 2L, 3L));
            store.assignGroup(MEMORY_ID, TYPE, List.of(1L, 3L), "food");

            assertThat(store.countUnGrouped(MEMORY_ID, TYPE)).isEqualTo(1);
            assertThat(store.getUnGrouped(MEMORY_ID, TYPE))
                    .extracting(BufferEntry::itemId)
                    .containsExactly(2L);
            assertThat(store.countGroupUnbuilt(MEMORY_ID, TYPE, "food")).isEqualTo(2);
            assertThat(store.getGroupUnbuilt(MEMORY_ID, TYPE, "food"))
                    .extracting(BufferEntry::itemId)
                    .containsExactlyInAnyOrder(1L, 3L);
        }
    }

    @Nested
    @DisplayName("listGroups")
    class ListGroups {

        @Test
        @DisplayName("Returns all group names")
        void returnsAllGroupNames() {
            store.append(MEMORY_ID, TYPE, List.of(1L, 2L, 3L, 4L));
            store.assignGroup(MEMORY_ID, TYPE, List.of(1L), "food");
            store.assignGroup(MEMORY_ID, TYPE, List.of(2L), "music");
            assertThat(store.listGroups(MEMORY_ID, TYPE))
                    .containsExactlyInAnyOrder("food", "music");
        }

        @Test
        @DisplayName("Returns an empty collection when there are no groups")
        void emptyWhenNoGroups() {
            store.append(MEMORY_ID, TYPE, List.of(1L));
            assertThat(store.listGroups(MEMORY_ID, TYPE)).isEmpty();
        }
    }

    @Nested
    @DisplayName("markBuilt")
    class MarkBuilt {

        @Test
        @DisplayName("Marking built removes from the unbuilt list within the group")
        void markBuiltRemovesFromUnbuilt() {
            store.append(MEMORY_ID, TYPE, List.of(1L, 2L, 3L));
            store.assignGroup(MEMORY_ID, TYPE, List.of(1L, 2L, 3L), "food");
            store.markBuilt(MEMORY_ID, TYPE, List.of(1L, 2L));

            assertThat(store.countGroupUnbuilt(MEMORY_ID, TYPE, "food")).isEqualTo(1);
            assertThat(store.getGroupUnbuilt(MEMORY_ID, TYPE, "food"))
                    .extracting(BufferEntry::itemId)
                    .containsExactly(3L);
        }

        @Test
        @DisplayName("The group still exists after marking built")
        void groupStillExistsAfterMarkBuilt() {
            store.append(MEMORY_ID, TYPE, List.of(1L));
            store.assignGroup(MEMORY_ID, TYPE, List.of(1L), "food");
            store.markBuilt(MEMORY_ID, TYPE, List.of(1L));
            assertThat(store.listGroups(MEMORY_ID, TYPE)).contains("food");
        }
    }
}
