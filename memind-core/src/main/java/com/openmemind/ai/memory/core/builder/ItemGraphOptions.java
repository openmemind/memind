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
package com.openmemind.ai.memory.core.builder;

import com.openmemind.ai.memory.core.extraction.item.graph.AliasEvidenceMode;
import com.openmemind.ai.memory.core.extraction.item.graph.CrossScriptMergePolicy;
import com.openmemind.ai.memory.core.extraction.item.graph.EntityResolutionMode;
import com.openmemind.ai.memory.core.extraction.item.graph.UserAliasDictionary;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public record ItemGraphOptions(
        boolean enabled,
        int maxEntitiesPerItem,
        int maxCausalReferencesPerItem,
        int maxTemporalLinksPerItem,
        int maxSemanticLinksPerItem,
        double semanticMinScore,
        int semanticSearchHeadroom,
        int semanticLinkConcurrency,
        int semanticSourceWindowSize,
        EntityResolutionMode resolutionMode,
        int maxResolutionCandidatesPerMention,
        double resolutionMergeThreshold,
        Set<String> supportedLanguagePacks,
        CrossScriptMergePolicy crossScriptMergePolicy,
        AliasEvidenceMode aliasEvidenceMode,
        UserAliasDictionary userAliasDictionary) {

    public ItemGraphOptions {
        if (maxEntitiesPerItem <= 0) {
            throw new IllegalArgumentException("maxEntitiesPerItem must be positive");
        }
        if (maxCausalReferencesPerItem <= 0) {
            throw new IllegalArgumentException("maxCausalReferencesPerItem must be positive");
        }
        if (maxTemporalLinksPerItem <= 0) {
            throw new IllegalArgumentException("maxTemporalLinksPerItem must be positive");
        }
        if (maxSemanticLinksPerItem <= 0) {
            throw new IllegalArgumentException("maxSemanticLinksPerItem must be positive");
        }
        if (semanticMinScore < 0.0d || semanticMinScore > 1.0d) {
            throw new IllegalArgumentException("semanticMinScore must be in [0,1]");
        }
        if (semanticSearchHeadroom < 0) {
            throw new IllegalArgumentException("semanticSearchHeadroom must be non-negative");
        }
        if (semanticLinkConcurrency <= 0) {
            throw new IllegalArgumentException("semanticLinkConcurrency must be positive");
        }
        if (semanticSourceWindowSize <= 0) {
            throw new IllegalArgumentException("semanticSourceWindowSize must be positive");
        }
        if (maxResolutionCandidatesPerMention <= 0) {
            throw new IllegalArgumentException(
                    "maxResolutionCandidatesPerMention must be positive");
        }
        if (resolutionMergeThreshold < 0.0d || resolutionMergeThreshold > 1.0d) {
            throw new IllegalArgumentException("resolutionMergeThreshold must be in [0,1]");
        }

        resolutionMode = resolutionMode == null ? EntityResolutionMode.EXACT : resolutionMode;
        crossScriptMergePolicy =
                crossScriptMergePolicy == null
                        ? CrossScriptMergePolicy.OFF
                        : crossScriptMergePolicy;
        aliasEvidenceMode =
                aliasEvidenceMode == null ? AliasEvidenceMode.METADATA : aliasEvidenceMode;
        supportedLanguagePacks =
                supportedLanguagePacks == null
                        ? defaultSupportedLanguagePacks()
                        : immutableOrderedSet(supportedLanguagePacks);
        userAliasDictionary =
                userAliasDictionary == null ? UserAliasDictionary.disabled() : userAliasDictionary;
    }

    public ItemGraphOptions(
            boolean enabled,
            int maxEntitiesPerItem,
            int maxCausalReferencesPerItem,
            int maxTemporalLinksPerItem,
            int maxSemanticLinksPerItem,
            double semanticMinScore,
            int semanticSearchHeadroom,
            int semanticLinkConcurrency,
            int semanticSourceWindowSize) {
        this(
                enabled,
                maxEntitiesPerItem,
                maxCausalReferencesPerItem,
                maxTemporalLinksPerItem,
                maxSemanticLinksPerItem,
                semanticMinScore,
                semanticSearchHeadroom,
                semanticLinkConcurrency,
                semanticSourceWindowSize,
                EntityResolutionMode.EXACT,
                8,
                0.85d,
                defaultSupportedLanguagePacks(),
                CrossScriptMergePolicy.OFF,
                AliasEvidenceMode.METADATA,
                UserAliasDictionary.disabled());
    }

    public static ItemGraphOptions defaults() {
        return new ItemGraphOptions(false, 8, 2, 10, 5, 0.82d, 4, 1, 128);
    }

    public ItemGraphOptions withEnabled(boolean enabled) {
        return new ItemGraphOptions(
                enabled,
                maxEntitiesPerItem,
                maxCausalReferencesPerItem,
                maxTemporalLinksPerItem,
                maxSemanticLinksPerItem,
                semanticMinScore,
                semanticSearchHeadroom,
                semanticLinkConcurrency,
                semanticSourceWindowSize,
                resolutionMode,
                maxResolutionCandidatesPerMention,
                resolutionMergeThreshold,
                supportedLanguagePacks,
                crossScriptMergePolicy,
                aliasEvidenceMode,
                userAliasDictionary);
    }

    public ItemGraphOptions withSemanticSearchHeadroom(int semanticSearchHeadroom) {
        return new ItemGraphOptions(
                enabled,
                maxEntitiesPerItem,
                maxCausalReferencesPerItem,
                maxTemporalLinksPerItem,
                maxSemanticLinksPerItem,
                semanticMinScore,
                semanticSearchHeadroom,
                semanticLinkConcurrency,
                semanticSourceWindowSize,
                resolutionMode,
                maxResolutionCandidatesPerMention,
                resolutionMergeThreshold,
                supportedLanguagePacks,
                crossScriptMergePolicy,
                aliasEvidenceMode,
                userAliasDictionary);
    }

    public ItemGraphOptions withSemanticLinkConcurrency(int semanticLinkConcurrency) {
        return new ItemGraphOptions(
                enabled,
                maxEntitiesPerItem,
                maxCausalReferencesPerItem,
                maxTemporalLinksPerItem,
                maxSemanticLinksPerItem,
                semanticMinScore,
                semanticSearchHeadroom,
                semanticLinkConcurrency,
                semanticSourceWindowSize,
                resolutionMode,
                maxResolutionCandidatesPerMention,
                resolutionMergeThreshold,
                supportedLanguagePacks,
                crossScriptMergePolicy,
                aliasEvidenceMode,
                userAliasDictionary);
    }

    public ItemGraphOptions withSemanticSourceWindowSize(int semanticSourceWindowSize) {
        return new ItemGraphOptions(
                enabled,
                maxEntitiesPerItem,
                maxCausalReferencesPerItem,
                maxTemporalLinksPerItem,
                maxSemanticLinksPerItem,
                semanticMinScore,
                semanticSearchHeadroom,
                semanticLinkConcurrency,
                semanticSourceWindowSize,
                resolutionMode,
                maxResolutionCandidatesPerMention,
                resolutionMergeThreshold,
                supportedLanguagePacks,
                crossScriptMergePolicy,
                aliasEvidenceMode,
                userAliasDictionary);
    }

    public ItemGraphOptions withResolutionMode(EntityResolutionMode resolutionMode) {
        return new ItemGraphOptions(
                enabled,
                maxEntitiesPerItem,
                maxCausalReferencesPerItem,
                maxTemporalLinksPerItem,
                maxSemanticLinksPerItem,
                semanticMinScore,
                semanticSearchHeadroom,
                semanticLinkConcurrency,
                semanticSourceWindowSize,
                resolutionMode,
                maxResolutionCandidatesPerMention,
                resolutionMergeThreshold,
                supportedLanguagePacks,
                crossScriptMergePolicy,
                aliasEvidenceMode,
                userAliasDictionary);
    }

    public ItemGraphOptions withUserAliasDictionary(UserAliasDictionary userAliasDictionary) {
        return new ItemGraphOptions(
                enabled,
                maxEntitiesPerItem,
                maxCausalReferencesPerItem,
                maxTemporalLinksPerItem,
                maxSemanticLinksPerItem,
                semanticMinScore,
                semanticSearchHeadroom,
                semanticLinkConcurrency,
                semanticSourceWindowSize,
                resolutionMode,
                maxResolutionCandidatesPerMention,
                resolutionMergeThreshold,
                supportedLanguagePacks,
                crossScriptMergePolicy,
                aliasEvidenceMode,
                userAliasDictionary);
    }

    private static Set<String> defaultSupportedLanguagePacks() {
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        ordered.add("en");
        ordered.add("zh");
        return Collections.unmodifiableSet(ordered);
    }

    private static Set<String> immutableOrderedSet(Set<String> values) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(values));
    }
}
