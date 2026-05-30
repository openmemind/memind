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

import java.util.List;
import java.util.Map;

public final class MemindMcpToolValidation {

    private MemindMcpToolValidation() {}

    public static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    public static String normalizeSourceClient(String value) {
        if (value == null || value.isBlank()) {
            return "mcp";
        }
        return value.trim();
    }

    public static List<String> requireStringIds(
            List<String> values, String fieldName, int maxSize) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be empty");
        }
        if (values.size() > maxSize) {
            throw new IllegalArgumentException(
                    fieldName + " must contain at most " + maxSize + " values");
        }
        return values.stream().map(value -> requireListValue(value, fieldName)).toList();
    }

    public static List<Long> requireLongIds(List<String> values, String fieldName, int maxSize) {
        return requireStringIds(values, fieldName, maxSize).stream()
                .map(value -> parseLong(value, fieldName))
                .toList();
    }

    public static Map<String, Object> requireMap(Map<String, Object> value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        return Map.copyOf(value);
    }

    public static int effectivePositiveInt(
            Integer value, int defaultValue, int maxValue, String fieldName) {
        if (value == null) {
            return defaultValue;
        }
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be greater than 0");
        }
        return Math.min(value, maxValue);
    }

    private static String requireListValue(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not contain blank values");
        }
        return value.trim();
    }

    private static Long parseLong(String value, String fieldName) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(fieldName + " must contain numeric ids", exception);
        }
    }
}
