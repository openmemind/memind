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

import com.openmemind.ai.memory.core.data.ContentTypes;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryRawData;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.CharBoundary;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.MessageBoundary;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.Segment;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.SegmentBoundary;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.MemoryRawDataDO;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public final class RawDataConverter {

    private RawDataConverter() {}

    public static MemoryRawDataDO toDO(MemoryId memoryId, MemoryRawData record) {
        MemoryRawDataDO dataObject = new MemoryRawDataDO();
        dataObject.setBizId(record.id());
        dataObject.setUserId(memoryId.getAttribute("userId"));
        dataObject.setAgentId(memoryId.getAttribute("agentId"));
        dataObject.setMemoryId(memoryId.toIdentifier());
        dataObject.setType(
                record.contentType() != null ? record.contentType() : ContentTypes.CONVERSATION);
        dataObject.setContentId(record.contentId());
        dataObject.setCaption(record.caption());
        dataObject.setCaptionVectorId(record.captionVectorId());
        dataObject.setMetadata(record.metadata());
        dataObject.setCreatedAt(record.createdAt() != null ? record.createdAt() : Instant.now());
        dataObject.setUpdatedAt(Instant.now());
        if (record.segment() != null) {
            dataObject.setSegment(segmentToMap(record.segment()));
        }
        return dataObject;
    }

    public static MemoryRawData toRecord(MemoryRawDataDO dataObject) {
        Segment segment = null;
        if (dataObject.getSegment() != null) {
            segment = mapToSegment(dataObject.getSegment());
        }
        return new MemoryRawData(
                dataObject.getBizId(),
                dataObject.getMemoryId(),
                parseContentType(dataObject.getType()),
                dataObject.getContentId(),
                segment,
                dataObject.getCaption(),
                dataObject.getCaptionVectorId(),
                dataObject.getMetadata(),
                dataObject.getCreatedAt());
    }

    private static String parseContentType(String value) {
        if (value == null || value.isBlank()) {
            return ContentTypes.CONVERSATION;
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> segmentToMap(Segment segment) {
        Map<String, Object> map = new HashMap<>();
        map.put("content", segment.content());
        map.put("caption", segment.caption());
        map.put("metadata", segment.metadata());
        if (segment.boundary() != null) {
            Map<String, Object> boundaryMap = new HashMap<>();
            switch (segment.boundary()) {
                case CharBoundary cb -> {
                    boundaryMap.put("type", "char");
                    boundaryMap.put("startChar", cb.startChar());
                    boundaryMap.put("endChar", cb.endChar());
                }
                case MessageBoundary mb -> {
                    boundaryMap.put("type", "message");
                    boundaryMap.put("startMessage", mb.startMessage());
                    boundaryMap.put("endMessage", mb.endMessage());
                }
            }
            map.put("boundary", boundaryMap);
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    private static Segment mapToSegment(Map<String, Object> map) {
        String content = (String) map.get("content");
        String caption = (String) map.get("caption");
        Map<String, Object> metadata =
                map.get("metadata") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        SegmentBoundary boundary = null;
        if (map.get("boundary") instanceof Map<?, ?> bMap) {
            String type = (String) bMap.get("type");
            boundary =
                    switch (type) {
                        case "char" ->
                                new CharBoundary(
                                        ((Number) bMap.get("startChar")).intValue(),
                                        ((Number) bMap.get("endChar")).intValue());
                        case "message" ->
                                new MessageBoundary(
                                        ((Number) bMap.get("startMessage")).intValue(),
                                        ((Number) bMap.get("endMessage")).intValue());
                        default -> null;
                    };
        }
        return new Segment(content, caption, boundary, metadata);
    }
}
