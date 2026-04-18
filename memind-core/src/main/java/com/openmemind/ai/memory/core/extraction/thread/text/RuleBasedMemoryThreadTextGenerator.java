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
package com.openmemind.ai.memory.core.extraction.thread.text;

import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.MemoryThread;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Deterministic canonical text refresh for memory-thread snapshots.
 */
public final class RuleBasedMemoryThreadTextGenerator {

    public MemoryThread refreshCanonicalText(MemoryThread thread, List<MemoryItem> members) {
        Objects.requireNonNull(thread, "thread");
        List<MemoryItem> orderedMembers = orderedMembers(members);
        if (orderedMembers.isEmpty()) {
            return thread;
        }

        MemoryItem first = orderedMembers.getFirst();
        MemoryItem last = orderedMembers.get(orderedMembers.size() - 1);
        Instant startAt = activityAt(first);
        Instant lastActivityAt = activityAt(last);
        Instant updatedAt =
                laterOf(
                        thread.updatedAt(),
                        laterOf(
                                thread.createdAt(),
                                lastActivityAt != null ? lastActivityAt : thread.lastActivityAt()));

        return new MemoryThread(
                thread.id(),
                thread.memoryId(),
                thread.threadKey(),
                thread.episodeType(),
                buildTitle(thread, orderedMembers),
                buildSummary(orderedMembers),
                thread.status(),
                thread.confidence(),
                startAt != null ? startAt : thread.startAt(),
                thread.endAt(),
                lastActivityAt != null ? lastActivityAt : thread.lastActivityAt(),
                thread.originItemId(),
                last.id() != null ? last.id() : thread.anchorItemId(),
                thread.displayOrderHint(),
                thread.metadata(),
                thread.createdAt(),
                updatedAt,
                thread.deleted());
    }

    private List<MemoryItem> orderedMembers(List<MemoryItem> members) {
        if (members == null || members.isEmpty()) {
            return List.of();
        }
        return members.stream()
                .filter(Objects::nonNull)
                .sorted(
                        Comparator.comparing(
                                        RuleBasedMemoryThreadTextGenerator::activityAt,
                                        Comparator.nullsLast(Comparator.naturalOrder()))
                                .thenComparing(
                                        MemoryItem::id, Comparator.nullsLast(Long::compareTo)))
                .toList();
    }

    private String buildTitle(MemoryThread thread, List<MemoryItem> orderedMembers) {
        if (thread.episodeType() != null && !thread.episodeType().isBlank()) {
            return humanize(thread.episodeType()) + " Thread";
        }
        return trimSentence(orderedMembers.getFirst().content(), 5);
    }

    private String buildSummary(List<MemoryItem> orderedMembers) {
        if (orderedMembers.size() == 1) {
            return trimSentence(orderedMembers.getFirst().content(), 12);
        }
        return trimSentence(orderedMembers.getFirst().content(), 8)
                + " -> "
                + trimSentence(orderedMembers.get(orderedMembers.size() - 1).content(), 8);
    }

    private static Instant activityAt(MemoryItem item) {
        if (item == null) {
            return null;
        }
        if (item.occurredAt() != null) {
            return item.occurredAt();
        }
        if (item.occurredStart() != null) {
            return item.occurredStart();
        }
        if (item.observedAt() != null) {
            return item.observedAt();
        }
        return item.createdAt();
    }

    private static Instant laterOf(Instant left, Instant right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.isAfter(right) ? left : right;
    }

    private static String humanize(String raw) {
        return trimSentence(
                raw.replace(':', ' ').replace('_', ' ').replace('-', ' ').toLowerCase(Locale.ROOT),
                Integer.MAX_VALUE);
    }

    private static String trimSentence(String content, int maxWords) {
        if (content == null || content.isBlank()) {
            return "Untitled Thread";
        }
        String[] words = content.trim().split("\\s+");
        int count = Math.min(words.length, maxWords);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                builder.append(' ');
            }
            String word = words[i];
            if (i == 0 && !word.isEmpty()) {
                builder.append(Character.toUpperCase(word.charAt(0)));
                builder.append(word.substring(1));
            } else {
                builder.append(word);
            }
        }
        return builder.toString();
    }
}
