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
package com.openmemind.ai.memory.core.retrieval.sufficiency;

import java.util.List;

/**
 * Sufficiency check result
 *
 * @param sufficient     Is sufficient
 * @param reasoning      Reasoning (1-2 sentences)
 * @param evidences      When sufficient: key sentences extracted from the original text (max 5);
 *                       when insufficient: empty list
 * @param gaps           When insufficient: description of missing specific information (max 3),
 *                       drives TypedQueryExpander; when sufficient: empty list
 * @param keyInformation Confirmed key information (max 5), used to provide context for the next round
 *                       of expanded queries
 */
public record SufficiencyResult(
        boolean sufficient,
        String reasoning,
        List<String> evidences,
        List<String> gaps,
        List<String> keyInformation) {

    /**
     * Error fallback: conservatively determined as insufficient
     */
    public static SufficiencyResult fallbackInsufficient() {
        return new SufficiencyResult(false, "fallback", List.of(), List.of(), List.of());
    }
}
