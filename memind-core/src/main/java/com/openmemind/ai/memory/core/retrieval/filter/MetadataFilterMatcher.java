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
package com.openmemind.ai.memory.core.retrieval.filter;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class MetadataFilterMatcher {

    private MetadataFilterMatcher() {}

    public static boolean matches(Map<String, Object> metadata, MetadataFilter filter) {
        if (filter == null || filter.isEmpty()) {
            return true;
        }
        Map<String, Object> safeMetadata = metadata == null ? Map.of() : metadata;
        boolean allMatch =
                filter.all().stream().allMatch(condition -> matches(safeMetadata, condition));
        boolean anyMatch =
                filter.any().isEmpty()
                        || filter.any().stream()
                                .anyMatch(condition -> matches(safeMetadata, condition));
        boolean noneExcluded =
                filter.not().stream().noneMatch(condition -> matches(safeMetadata, condition));
        return allMatch && anyMatch && noneExcluded;
    }

    private static boolean matches(
            Map<String, Object> metadata, MetadataFilter.Condition condition) {
        if (condition == null || condition.path() == null || condition.path().isBlank()) {
            return true;
        }
        String op =
                condition.op() == null || condition.op().isBlank()
                        ? "eq"
                        : condition.op().trim().toLowerCase(Locale.ROOT);
        boolean present = metadata.containsKey(condition.path());
        Object actual = metadata.get(condition.path());
        return switch (op) {
            case "eq" -> present && valuesEqual(actual, condition.value());
            case "in" -> present && valueIn(actual, condition.value());
            case "exists" -> present && actual != null;
            case "missing" -> !present || actual == null;
            case "contains" -> present && contains(actual, condition.value());
            default -> false;
        };
    }

    private static boolean valueIn(Object actual, Object expected) {
        if (expected instanceof Collection<?> collection) {
            return collection.stream().anyMatch(value -> valuesEqual(actual, value));
        }
        return valuesEqual(actual, expected);
    }

    private static boolean contains(Object actual, Object expected) {
        if (actual instanceof Collection<?> collection) {
            return collection.stream().anyMatch(value -> valuesEqual(value, expected));
        }
        if (actual instanceof Map<?, ?> map && expected != null) {
            return map.containsKey(String.valueOf(expected));
        }
        return actual != null
                && expected != null
                && String.valueOf(actual).contains(String.valueOf(expected));
    }

    private static boolean valuesEqual(Object actual, Object expected) {
        return Objects.equals(actual, expected)
                || (actual != null
                        && expected != null
                        && String.valueOf(actual).equals(String.valueOf(expected)));
    }
}
