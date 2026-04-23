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

import java.util.ArrayList;
import java.util.List;

/**
 * Replayable evidence summary for one admitted thread decision.
 */
record ThreadAdmissionEvidence(int supportCount, List<String> dominantFamilies) {

    static final String EXACT_ANCHOR = "exact_anchor";
    static final String EXPLICIT_CONTINUITY = "explicit_continuity";
    static final String CAUSAL_CONTINUITY = "causal_continuity";
    static final String TEMPORAL_CONTINUITY = "temporal_continuity";
    static final String SEMANTIC_CONTINUITY = "semantic_continuity";
    static final String ENTITY_SUPPORT = "entity_support";
    static final String TWO_HIT_SUPPORT = "two_hit_support";

    ThreadAdmissionEvidence {
        if (supportCount < 0) {
            throw new IllegalArgumentException("supportCount must be non-negative");
        }
        dominantFamilies = dominantFamilies == null ? List.of() : List.copyOf(dominantFamilies);
    }

    static ThreadAdmissionEvidence none() {
        return new ThreadAdmissionEvidence(0, List.of());
    }

    static ThreadAdmissionEvidence exactAnchor() {
        return new ThreadAdmissionEvidence(1, List.of(EXACT_ANCHOR));
    }

    static ThreadAdmissionEvidence twoHitSupport(int supportCount) {
        return new ThreadAdmissionEvidence(
                Math.max(0, supportCount), supportCount > 0 ? List.of(TWO_HIT_SUPPORT) : List.of());
    }

    static ThreadAdmissionEvidence fromCandidate(ThreadCandidateScore candidate) {
        ArrayList<String> families = new ArrayList<>();
        if (candidate.explicitContinuityScore() > 0.0d) {
            families.add(EXPLICIT_CONTINUITY);
        }
        if (candidate.causalScore() > 0.0d) {
            families.add(CAUSAL_CONTINUITY);
        }
        if (candidate.temporalScore() > 0.0d) {
            families.add(TEMPORAL_CONTINUITY);
        }
        if (candidate.semanticScore() > 0.0d) {
            families.add(SEMANTIC_CONTINUITY);
        }
        if (candidate.entityScore() > 0.0d) {
            families.add(ENTITY_SUPPORT);
        }
        return new ThreadAdmissionEvidence(families.size(), List.copyOf(families));
    }

    boolean isEmpty() {
        return supportCount == 0 && dominantFamilies.isEmpty();
    }

    boolean hasFamily(String family) {
        return dominantFamilies.contains(family);
    }
}
