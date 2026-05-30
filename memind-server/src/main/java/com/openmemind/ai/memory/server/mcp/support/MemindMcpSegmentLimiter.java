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
package com.openmemind.ai.memory.server.mcp.support;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MemindMcpSegmentLimiter {

    private static final String TRUNCATED_SUFFIX = "...[truncated]";

    private MemindMcpSegmentLimiter() {}

    @SuppressWarnings("unchecked")
    public static Map<String, Object> limit(Map<String, Object> segment, int maxStringChars) {
        if (segment == null) {
            return null;
        }
        return (Map<String, Object>) limitValue(segment, maxStringChars);
    }

    private static Object limitValue(Object value, int maxStringChars) {
        if (value instanceof String text) {
            return limitString(text, maxStringChars);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> limited = new LinkedHashMap<>();
            map.forEach(
                    (key, child) ->
                            limited.put(String.valueOf(key), limitValue(child, maxStringChars)));
            return Map.copyOf(limited);
        }
        if (value instanceof List<?> list) {
            return list.stream().map(child -> limitValue(child, maxStringChars)).toList();
        }
        return value;
    }

    private static String limitString(String text, int maxStringChars) {
        if (maxStringChars <= 0 || text.length() <= maxStringChars) {
            return text;
        }
        return text.substring(0, maxStringChars) + TRUNCATED_SUFFIX;
    }
}
