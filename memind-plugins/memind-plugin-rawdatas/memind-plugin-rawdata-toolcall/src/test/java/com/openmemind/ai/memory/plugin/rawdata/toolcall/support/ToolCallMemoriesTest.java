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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openmemind.ai.memory.core.Memory;
import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.extraction.ExtractionResult;
import com.openmemind.ai.memory.core.extraction.result.InsightResult;
import com.openmemind.ai.memory.core.extraction.result.MemoryItemResult;
import com.openmemind.ai.memory.core.extraction.result.RawDataResult;
import com.openmemind.ai.memory.plugin.rawdata.toolcall.content.ToolCallContent;
import com.openmemind.ai.memory.plugin.rawdata.toolcall.model.ToolCallRecord;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ToolCallMemoriesTest {

    @Test
    void reportDelegatesToGenericMemoryExtract() {
        Memory memory = mock(Memory.class);
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
        ExtractionResult result =
                ExtractionResult.success(
                        memoryId,
                        RawDataResult.empty(),
                        MemoryItemResult.empty(),
                        InsightResult.empty(),
                        Duration.ZERO);
        when(memory.extract(eq(memoryId), any(ToolCallContent.class)))
                .thenReturn(Mono.just(result));

        StepVerifier.create(ToolCallMemories.report(memory, memoryId, List.of(record)))
                .expectNext(result)
                .verifyComplete();

        verify(memory).extract(eq(memoryId), any(ToolCallContent.class));
    }
}
