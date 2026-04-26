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
package com.openmemind.ai.memory.core.extraction.item.graph.link.temporal;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.data.enums.MemoryItemType;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TemporalRelationClassifierTest {

    private static final MemoryId MEMORY_ID = DefaultMemoryId.of("user-1", "agent-1");
    private static final Instant CREATED_AT = Instant.parse("2026-04-16T00:00:00Z");

    @Test
    void classifierShouldResolveWindowsAndClassifyBeforeOverlapAndNearby() {
        var classifier = new TemporalRelationClassifier();

        var point = classifier.resolveWindow(item(101L, "point", "2026-04-10T09:00:00Z"));
        var overlap =
                classifier.resolveWindow(
                        rangedItem(
                                102L, "overlap", "2026-04-10T08:00:00Z", "2026-04-10T10:00:00Z"));
        var nearby = classifier.resolveWindow(item(103L, "nearby", "2026-04-10T20:00:00Z"));
        var far = classifier.resolveWindow(item(104L, "far", "2026-04-12T20:00:00Z"));
        var endedBefore =
                classifier.resolveWindow(
                        rangedItem(
                                105L,
                                "ended-before",
                                "2026-04-10T09:00:00Z",
                                "2026-04-10T11:00:00Z"));
        var afterEnded =
                classifier.resolveWindow(item(106L, "after-ended", "2026-04-10T12:00:00Z"));

        assertThat(classifier.classify(point, overlap)).isEqualTo("overlap");
        assertThat(classifier.classify(point, nearby)).isEqualTo("nearby");
        assertThat(classifier.classify(point, far)).isEqualTo("before");
        assertThat(classifier.classify(endedBefore, afterEnded)).isEqualTo("before");
        assertThat(classifier.classify(nearby, far)).isEqualTo("before");
    }

    private static MemoryItem item(Long id, String content, String occurredAt) {
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
                Instant.parse(occurredAt),
                CREATED_AT,
                Map.of(),
                CREATED_AT,
                MemoryItemType.FACT);
    }

    private static MemoryItem rangedItem(Long id, String content, String start, String end) {
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
                Instant.parse(start),
                Instant.parse(end),
                "unknown",
                CREATED_AT,
                Map.of(),
                CREATED_AT,
                MemoryItemType.FACT);
    }
}
