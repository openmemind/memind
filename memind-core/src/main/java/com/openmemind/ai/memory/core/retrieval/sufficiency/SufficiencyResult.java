package com.openmemind.ai.memory.core.retrieval.sufficiency;

import java.util.List;

/**
 * Sufficiency check result
 *
 * @param sufficient Is sufficient
 * @param reasoning  Reasoning (1-2 sentences)
 * @param evidences  When sufficient: key sentences extracted from the original text (max 5); when insufficient: empty list
 * @param gaps            When insufficient: description of missing specific information (max 3), drives TypedQueryExpander; when sufficient: empty list
 * @param keyInformation  Confirmed key information (max 5), used to provide context for the next round of expanded queries
 */
public record SufficiencyResult(
        boolean sufficient,
        String reasoning,
        List<String> evidences,
        List<String> gaps,
        List<String> keyInformation) {

    /** Error fallback: conservatively determined as insufficient */
    public static SufficiencyResult fallbackInsufficient() {
        return new SufficiencyResult(false, "fallback", List.of(), List.of(), List.of());
    }
}
