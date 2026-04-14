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
package com.openmemind.ai.memory.core.store.resource;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.MemoryResource;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InMemoryResourceOperationsTest {

    @Test
    void upsertGetAndListShouldUseResourceIdAsBusinessKey() {
        var operations = new InMemoryResourceOperations();
        var memoryId = DefaultMemoryId.of("user-1", "agent-1");
        var first =
                new MemoryResource(
                        "res-1",
                        memoryId.toIdentifier(),
                        "file:///tmp/report.pdf",
                        null,
                        "report.pdf",
                        "application/pdf",
                        "abc",
                        123L,
                        Map.of("pages", 2),
                        Instant.parse("2026-04-09T00:00:00Z"));
        var updated =
                new MemoryResource(
                        "res-1",
                        memoryId.toIdentifier(),
                        "file:///tmp/report.pdf",
                        "file:///data/report.pdf",
                        "report.pdf",
                        "application/pdf",
                        "abc",
                        123L,
                        Map.of("pages", 3),
                        Instant.parse("2026-04-09T00:00:01Z"));

        operations.upsertResources(memoryId, List.of(first));
        operations.upsertResources(memoryId, List.of(updated));

        assertThat(operations.getResource(memoryId, "res-1")).contains(updated);
        assertThat(operations.listResources(memoryId)).containsExactly(updated);
    }
}
