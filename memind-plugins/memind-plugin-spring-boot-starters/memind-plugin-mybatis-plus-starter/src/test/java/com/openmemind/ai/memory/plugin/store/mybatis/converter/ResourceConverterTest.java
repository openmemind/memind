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
package com.openmemind.ai.memory.plugin.store.mybatis.converter;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.MemoryResource;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ResourceConverterTest {

    @Test
    void toDoAndToRecordRoundTripResourceFields() {
        var memoryId = DefaultMemoryId.of("user-1", "agent-1");
        var resource =
                new MemoryResource(
                        "res-1",
                        memoryId.toIdentifier(),
                        "https://example.com/report.pdf",
                        "file:///tmp/memory/report.pdf",
                        "report.pdf",
                        "application/pdf",
                        "abc123",
                        1024L,
                        Map.of("pages", 7),
                        Instant.parse("2026-04-09T10:00:00Z"));

        var dataObject = ResourceConverter.toDO(memoryId, resource);
        assertThat(dataObject.getBizId()).isEqualTo("res-1");
        assertThat(dataObject.getMemoryId()).isEqualTo(memoryId.toIdentifier());
        assertThat(dataObject.getMimeType()).isEqualTo("application/pdf");
        assertThat(dataObject.getMetadata()).containsEntry("pages", 7);

        var roundTrip = ResourceConverter.toRecord(dataObject);
        assertThat(roundTrip).isEqualTo(resource);
    }
}
