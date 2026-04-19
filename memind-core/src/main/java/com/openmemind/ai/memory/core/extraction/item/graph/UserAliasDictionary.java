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
package com.openmemind.ai.memory.core.extraction.item.graph;

import com.openmemind.ai.memory.core.store.graph.GraphEntityType;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Runtime canonical-override mapping keyed by {@code <lowercase type>|<normalized mention name>}.
 */
public record UserAliasDictionary(
        boolean enabled, Map<String, String> normalizedMentionLookupKeyToEntityKey) {

    public UserAliasDictionary {
        normalizedMentionLookupKeyToEntityKey =
                normalizedMentionLookupKeyToEntityKey == null
                        ? Map.of()
                        : Map.copyOf(normalizedMentionLookupKeyToEntityKey);
        normalizedMentionLookupKeyToEntityKey.keySet().forEach(UserAliasDictionary::validateKey);
    }

    public static UserAliasDictionary disabled() {
        return new UserAliasDictionary(false, Map.of());
    }

    private static void validateKey(String lookupKey) {
        if (lookupKey == null || lookupKey.isBlank()) {
            throw new IllegalArgumentException(
                    "UserAliasDictionary keys must use '<lowercase type>|<normalized mention"
                            + " name>'");
        }
        int separatorIndex = lookupKey.indexOf('|');
        if (separatorIndex <= 0
                || separatorIndex != lookupKey.lastIndexOf('|')
                || separatorIndex == lookupKey.length() - 1) {
            throw new IllegalArgumentException(
                    "UserAliasDictionary keys must use '<lowercase type>|<normalized mention"
                            + " name>'");
        }
    }

    public Optional<String> lookup(GraphEntityType type, String normalizedMentionName) {
        if (!enabled
                || type == null
                || normalizedMentionName == null
                || normalizedMentionName.isBlank()) {
            return Optional.empty();
        }
        if (normalizedMentionName.indexOf('|') >= 0) {
            throw new IllegalArgumentException(
                    "normalized mention names for UserAliasDictionary must not contain '|'");
        }
        return Optional.ofNullable(
                normalizedMentionLookupKeyToEntityKey.get(
                        type.name().toLowerCase(Locale.ROOT) + "|" + normalizedMentionName));
    }
}
