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
package com.openmemind.ai.memory.core.extraction.thread.text;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.MemoryThread;
import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.data.enums.MemoryItemType;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadStatus;
import com.openmemind.ai.memory.core.support.TestMemoryIds;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RuleBasedMemoryThreadTextGeneratorTest {

    @Test
    void refreshCanonicalTextIsDeterministicAcrossMemberOrder() {
        RuleBasedMemoryThreadTextGenerator generator = new RuleBasedMemoryThreadTextGenerator();
        MemoryThread thread = openThread();
        List<MemoryItem> ordered =
                List.of(
                        item(101L, "Could not sleep for three nights after the breakup"),
                        item(102L, "Started seeing friends again"));
        List<MemoryItem> reversed =
                List.of(
                        item(102L, "Started seeing friends again"),
                        item(101L, "Could not sleep for three nights after the breakup"));

        assertThat(generator.refreshCanonicalText(thread, ordered))
                .usingRecursiveComparison()
                .isEqualTo(generator.refreshCanonicalText(thread, reversed));
    }

    private static MemoryThread openThread() {
        return new MemoryThread(
                101L,
                TestMemoryIds.userAgent().toIdentifier(),
                "ep:101",
                "emotional_recovery",
                "Breakup Recovery Line",
                "From insomnia toward partial stabilization",
                MemoryThreadStatus.OPEN,
                0.90d,
                Instant.parse("2026-04-18T00:00:00Z"),
                null,
                Instant.parse("2026-04-18T00:00:00Z"),
                101L,
                101L,
                1,
                Map.of(),
                Instant.parse("2026-04-18T00:00:00Z"),
                Instant.parse("2026-04-18T00:00:00Z"),
                false);
    }

    private static MemoryItem item(Long id, String content) {
        return new MemoryItem(
                id,
                TestMemoryIds.userAgent().toIdentifier(),
                content,
                MemoryScope.USER,
                MemoryCategory.PROFILE,
                "conversation",
                "vec-" + id,
                "raw-" + id,
                "hash-" + id,
                Instant.parse("2026-04-18T00:00:00Z").plusSeconds(id),
                Instant.parse("2026-04-18T00:00:00Z").plusSeconds(id),
                Map.of(),
                Instant.parse("2026-04-18T00:00:00Z"),
                MemoryItemType.FACT);
    }
}
