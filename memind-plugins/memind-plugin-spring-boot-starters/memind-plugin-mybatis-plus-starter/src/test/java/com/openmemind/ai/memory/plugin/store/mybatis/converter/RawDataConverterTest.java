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
import com.openmemind.ai.memory.core.data.MemoryRawData;
import com.openmemind.ai.memory.core.extraction.rawdata.content.ConversationContent;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.CharBoundary;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.Segment;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.SegmentRuntimeContext;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.MemoryRawDataDO;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RawDataConverterTest {

    @Test
    void toDoAndToRecordKeepRuntimeContextOutOfDurableSegmentMaps() {
        var memoryId = DefaultMemoryId.of("user-1", "agent-1");
        var record =
                new MemoryRawData(
                        "raw-1",
                        memoryId.toIdentifier(),
                        ConversationContent.TYPE,
                        "content-1",
                        new Segment(
                                "hello",
                                "caption",
                                new CharBoundary(0, 5),
                                Map.of("source", "chat"),
                                new SegmentRuntimeContext(
                                        Instant.parse("2026-03-27T02:17:00Z"),
                                        Instant.parse("2026-03-27T02:18:00Z"),
                                        "Alice")),
                        "caption",
                        null,
                        Map.of("channel", "chat"),
                        "res-1",
                        "text/plain",
                        Instant.parse("2026-03-27T02:18:30Z"),
                        Instant.parse("2026-03-27T02:17:00Z"),
                        Instant.parse("2026-03-27T02:18:00Z"));

        var dataObject = RawDataConverter.toDO(memoryId, record);
        assertThat(dataObject.getSegment()).doesNotContainKey("runtimeContext");
        assertThat(dataObject.getResourceId()).isEqualTo("res-1");
        assertThat(dataObject.getMimeType()).isEqualTo("text/plain");

        var roundTrip = RawDataConverter.toRecord(dataObject);
        assertThat(roundTrip.segment().runtimeContext()).isNull();
        assertThat(roundTrip.resourceId()).isEqualTo("res-1");
        assertThat(roundTrip.mimeType()).isEqualTo("text/plain");
    }

    @Test
    void toRecordAcceptsLegacySegmentMapWithoutRuntimeContext() {
        var dataObject = new MemoryRawDataDO();
        dataObject.setBizId("raw-legacy");
        dataObject.setMemoryId("user-1:agent-1");
        dataObject.setType(ConversationContent.TYPE);
        dataObject.setContentId("content-legacy");
        dataObject.setResourceId("res-legacy");
        dataObject.setMimeType("application/json");
        dataObject.setSegment(
                Map.of(
                        "content",
                        "legacy",
                        "caption",
                        "legacy caption",
                        "metadata",
                        Map.of("source", "legacy"),
                        "boundary",
                        Map.of("type", "char", "startChar", 0, "endChar", 6)));

        var record = RawDataConverter.toRecord(dataObject);
        assertThat(record.segment().runtimeContext()).isNull();
        assertThat(record.resourceId()).isEqualTo("res-legacy");
        assertThat(record.mimeType()).isEqualTo("application/json");
    }
}
