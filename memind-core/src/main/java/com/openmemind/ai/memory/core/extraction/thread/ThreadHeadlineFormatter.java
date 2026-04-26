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
package com.openmemind.ai.memory.core.extraction.thread;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Deterministic fallback formatter for canonical thread anchors.
 */
public final class ThreadHeadlineFormatter {

    private ThreadHeadlineFormatter() {}

    public static String format(String anchorKind, String anchorKey) {
        String normalizedKind = ThreadAnchorCanonicalizer.normalizeToken(anchorKind);
        String normalizedKey = ThreadAnchorCanonicalizer.normalizeToken(anchorKey);
        if (normalizedKind == null || normalizedKey == null) {
            return null;
        }
        return switch (normalizedKind) {
            case "relationship" -> formatRelationshipDyad(normalizedKey);
            case "relationship_group" -> formatRelationshipGroup(normalizedKey);
            default -> humanizeCanonicalToken(normalizedKey);
        };
    }

    private static String formatRelationshipDyad(String anchorKey) {
        List<String> participants = humanizeParticipants(anchorKey);
        if (participants.size() != 2) {
            return null;
        }
        return participants.get(0) + " and " + participants.get(1);
    }

    private static String formatRelationshipGroup(String anchorKey) {
        List<String> participants = humanizeParticipants(anchorKey);
        if (participants.size() < 3) {
            return null;
        }
        if (participants.size() == 3) {
            return participants.get(0)
                    + ", "
                    + participants.get(1)
                    + ", and "
                    + participants.get(2);
        }
        String prefix = String.join(", ", participants.subList(0, participants.size() - 1));
        return prefix + ", and " + participants.getLast();
    }

    private static List<String> humanizeParticipants(String anchorKey) {
        return Arrays.stream(anchorKey.split("\\|"))
                .map(ThreadAnchorCanonicalizer::normalizeToken)
                .filter(Objects::nonNull)
                .map(ThreadHeadlineFormatter::humanizeCanonicalToken)
                .filter(Objects::nonNull)
                .toList();
    }

    private static String humanizeCanonicalToken(String token) {
        String normalized = ThreadAnchorCanonicalizer.normalizeToken(token);
        if (normalized == null) {
            return null;
        }
        int separator = normalized.indexOf(':');
        String namespace = separator > 0 ? normalized.substring(0, separator) : "";
        String value = separator > 0 ? normalized.substring(separator + 1) : normalized;
        if ("special".equals(namespace) && ("self".equals(value) || "user".equals(value))) {
            return "You";
        }
        if (value.contains(":")) {
            value = value.substring(value.lastIndexOf(':') + 1);
        }
        if (value.isBlank()) {
            return null;
        }
        return Arrays.stream(value.replace('_', ' ').replace('-', ' ').split("\\s+"))
                .filter(part -> !part.isBlank())
                .map(ThreadHeadlineFormatter::capitalizeWord)
                .reduce((left, right) -> left + " " + right)
                .orElse(null);
    }

    private static String capitalizeWord(String word) {
        String lower = word.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
