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
package com.openmemind.ai.memory.plugin.rawdata.toolcall.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.extraction.ExtractionRequest;
import com.openmemind.ai.memory.core.extraction.source.DirectContentSource;
import com.openmemind.ai.memory.plugin.rawdata.toolcall.content.ToolCallContent;
import com.openmemind.ai.memory.plugin.rawdata.toolcall.model.ToolCallRecord;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ToolCallExtractionRequestsTest {

    @Test
    void toolCallRequestWrapsRecordsWithGenericExtractionRequest() {
        MemoryId memoryId = DefaultMemoryId.of("u1", "a1");
        ToolCallRecord record =
                new ToolCallRecord(
                        "search",
                        "{}",
                        "ok",
                        "SUCCESS",
                        1L,
                        1,
                        1,
                        ToolCallRecord.computeHash("search", "{}", "ok"),
                        Instant.parse("2026-04-12T00:00:00Z"));

        ExtractionRequest request = ToolCallExtractionRequests.toolCall(memoryId, List.of(record));

        assertThat(request.memoryId()).isEqualTo(memoryId);
        assertThat(request.source()).isInstanceOf(DirectContentSource.class);
        assertThat(request.content()).isInstanceOf(ToolCallContent.class);
        assertThat(request.content().contentType()).isEqualTo(ToolCallContent.TYPE);
    }
}
