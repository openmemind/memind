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

import com.openmemind.ai.memory.core.data.enums.MemoryThreadType;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Canonicalizes raw anchor candidates into stable thread identity.
 */
public final class ThreadAnchorCanonicalizer {

    public Optional<CanonicalThreadAnchor> canonicalize(ThreadIntakeSignal signal) {
        Objects.requireNonNull(signal, "signal");
        return signal.anchorCandidates().stream()
                .max(Comparator.comparing(ThreadIntakeSignal.AnchorCandidate::score))
                .flatMap(candidate -> canonicalize(signal.threadType(), candidate));
    }

    Optional<CanonicalThreadAnchor> canonicalize(
            MemoryThreadType threadType, ThreadIntakeSignal.AnchorCandidate candidate) {
        return switch (threadType) {
            case RELATIONSHIP -> canonicalizeRelationship(candidate);
            case WORK, CASE, TOPIC -> canonicalizeSimple(threadType, candidate);
        };
    }

    private Optional<CanonicalThreadAnchor> canonicalizeRelationship(
            ThreadIntakeSignal.AnchorCandidate candidate) {
        String anchorKind = normalizeToken(candidate.anchorKind());
        if ("relationship_group".equals(anchorKind)) {
            return canonicalizeRelationshipGroup(candidate);
        }
        if ("relationship".equals(anchorKind)) {
            return canonicalizeRelationshipDyad(candidate);
        }
        return Optional.empty();
    }

    private List<String> normalizeRelationshipParticipants(
            ThreadIntakeSignal.AnchorCandidate candidate) {
        return candidate.participants().stream()
                .map(ThreadAnchorCanonicalizer::normalizeRelationshipParticipant)
                .filter(token -> token != null && !token.isBlank())
                .distinct()
                .sorted(ThreadAnchorCanonicalizer::compareRelationshipParticipants)
                .toList();
    }

    private Optional<CanonicalThreadAnchor> canonicalizeRelationshipDyad(
            ThreadIntakeSignal.AnchorCandidate candidate) {
        List<String> participants = normalizeRelationshipParticipants(candidate);
        if (participants.size() != 2) {
            return Optional.empty();
        }
        long personCount =
                participants.stream()
                        .filter(participant -> participant.startsWith("person:"))
                        .count();
        long specialCount =
                participants.stream()
                        .filter(participant -> participant.startsWith("special:"))
                        .count();
        if (personCount == 0 || specialCount == 2) {
            return Optional.empty();
        }
        String anchorKey = String.join("|", participants);
        return Optional.of(
                new CanonicalThreadAnchor(
                        MemoryThreadType.RELATIONSHIP,
                        "relationship",
                        anchorKey,
                        threadKey(MemoryThreadType.RELATIONSHIP, "relationship", anchorKey)));
    }

    private Optional<CanonicalThreadAnchor> canonicalizeRelationshipGroup(
            ThreadIntakeSignal.AnchorCandidate candidate) {
        List<String> participants = normalizeRelationshipParticipants(candidate);
        if (participants.size() < 3 || participants.size() > 5) {
            return Optional.empty();
        }
        long personCount =
                participants.stream()
                        .filter(participant -> participant.startsWith("person:"))
                        .count();
        if (personCount < 2) {
            return Optional.empty();
        }
        String anchorKey = String.join("|", participants);
        return Optional.of(
                new CanonicalThreadAnchor(
                        MemoryThreadType.RELATIONSHIP,
                        "relationship_group",
                        anchorKey,
                        threadKey(MemoryThreadType.RELATIONSHIP, "relationship_group", anchorKey)));
    }

    private Optional<CanonicalThreadAnchor> canonicalizeSimple(
            MemoryThreadType threadType, ThreadIntakeSignal.AnchorCandidate candidate) {
        String anchorKind = normalizeToken(candidate.anchorKind());
        String anchorKey = normalizeToken(candidate.anchorKey());
        if (anchorKind == null || anchorKey == null) {
            return Optional.empty();
        }
        return Optional.of(
                new CanonicalThreadAnchor(
                        threadType,
                        anchorKind,
                        anchorKey,
                        threadKey(threadType, anchorKind, anchorKey)));
    }

    private static String threadKey(
            MemoryThreadType threadType, String anchorKind, String anchorKey) {
        return threadType.name().toLowerCase(Locale.ROOT) + ":" + anchorKind + ":" + anchorKey;
    }

    private static String normalizeRelationshipParticipant(String participant) {
        String normalized = normalizeToken(participant);
        if ("special:user".equals(normalized) || "special:self".equals(normalized)) {
            return "special:self";
        }
        return normalized;
    }

    static String normalizeToken(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    private static int compareRelationshipParticipants(String left, String right) {
        int leftRank = relationshipParticipantRank(left);
        int rightRank = relationshipParticipantRank(right);
        if (leftRank != rightRank) {
            return Integer.compare(leftRank, rightRank);
        }
        return left.compareTo(right);
    }

    private static int relationshipParticipantRank(String participant) {
        if ("special:self".equals(participant)) {
            return 0;
        }
        if ("special:assistant".equals(participant)) {
            return 1;
        }
        if (participant != null && participant.startsWith("special:")) {
            return 2;
        }
        return 3;
    }

    public record CanonicalThreadAnchor(
            MemoryThreadType threadType, String anchorKind, String anchorKey, String threadKey) {

        public CanonicalThreadAnchor {
            threadType = Objects.requireNonNull(threadType, "threadType");
            Objects.requireNonNull(anchorKind, "anchorKind");
            Objects.requireNonNull(anchorKey, "anchorKey");
            Objects.requireNonNull(threadKey, "threadKey");
        }
    }
}
