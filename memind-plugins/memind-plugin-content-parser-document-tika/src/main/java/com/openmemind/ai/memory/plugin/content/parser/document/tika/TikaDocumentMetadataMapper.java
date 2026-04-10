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
package com.openmemind.ai.memory.plugin.content.parser.document.tika;

import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Office;
import org.apache.tika.metadata.TikaCoreProperties;

final class TikaDocumentMetadataMapper {

    Map<String, Object> map(Metadata metadata, String detectedMimeType) {
        Map<String, Object> mapped = new LinkedHashMap<>();
        mapped.put("parser", "tika");
        put(mapped, "detectedMimeType", detectedMimeType);
        put(mapped, "author", metadata.get(Office.AUTHOR));
        put(mapped, "creator", metadata.get(TikaCoreProperties.CREATOR));
        put(mapped, "subject", metadata.get(TikaCoreProperties.SUBJECT));
        put(mapped, "keywords", metadata.get(Office.KEYWORDS));
        put(mapped, "description", metadata.get(TikaCoreProperties.DESCRIPTION));
        put(mapped, "language", metadata.get(TikaCoreProperties.LANGUAGE));
        put(mapped, "pageCount", parseInteger(metadata.get("xmpTPg:NPages")));
        put(mapped, "createdAt", toIso(metadata.getDate(TikaCoreProperties.CREATED)));
        put(mapped, "modifiedAt", toIso(metadata.getDate(TikaCoreProperties.MODIFIED)));
        return Map.copyOf(mapped);
    }

    private static void put(Map<String, Object> target, String key, Object value) {
        if (value instanceof String stringValue) {
            if (!stringValue.isBlank()) {
                target.put(key, stringValue);
            }
            return;
        }
        if (value != null) {
            target.put(key, value);
        }
    }

    private static Integer parseInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String toIso(java.util.Date value) {
        if (value == null) {
            return null;
        }
        return value.toInstant().atOffset(ZoneOffset.UTC).toString();
    }
}
