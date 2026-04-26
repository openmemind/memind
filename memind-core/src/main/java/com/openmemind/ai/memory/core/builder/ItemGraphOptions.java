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
        boolean entityOverlapSemanticLinksEnabled,
        int minSharedEntitiesForSemanticLink,
        int maxEntityOverlapLinksPerItem,
        int maxItemsPerEntityForSemanticLink,
        float entityOverlapMinMentionConfidence,
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
        if (minSharedEntitiesForSemanticLink <= 0) {
            throw new IllegalArgumentException("minSharedEntitiesForSemanticLink must be positive");
        }
        if (maxEntityOverlapLinksPerItem <= 0) {
            throw new IllegalArgumentException("maxEntityOverlapLinksPerItem must be positive");
        }
        if (maxItemsPerEntityForSemanticLink <= 0) {
            throw new IllegalArgumentException("maxItemsPerEntityForSemanticLink must be positive");
        }
        if (entityOverlapMinMentionConfidence < 0.0f || entityOverlapMinMentionConfidence > 1.0f) {
            throw new IllegalArgumentException(
                    "entityOverlapMinMentionConfidence must be in [0,1]");
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
                true,
                2,
                maxSemanticLinksPerItem,
                12,
                0.5f,
                EntityResolutionMode.EXACT,
                8,
                0.85d,
                defaultSupportedLanguagePacks(),
                CrossScriptMergePolicy.OFF,
                AliasEvidenceMode.METADATA,
                UserAliasDictionary.disabled());
    }

    public static ItemGraphOptions defaults() {
        return new ItemGraphOptions(true, 8, 2, 10, 5, 0.82d, 4, 1, 128);
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    public ItemGraphOptions withEnabled(boolean enabled) {
        return toBuilder().enabled(enabled).build();
    }

    public ItemGraphOptions withSemanticSearchHeadroom(int semanticSearchHeadroom) {
        return toBuilder().semanticSearchHeadroom(semanticSearchHeadroom).build();
    }

    public ItemGraphOptions withSemanticLinkConcurrency(int semanticLinkConcurrency) {
        return toBuilder().semanticLinkConcurrency(semanticLinkConcurrency).build();
    }

    public ItemGraphOptions withSemanticSourceWindowSize(int semanticSourceWindowSize) {
        return toBuilder().semanticSourceWindowSize(semanticSourceWindowSize).build();
    }

    public ItemGraphOptions withResolutionMode(EntityResolutionMode resolutionMode) {
        return toBuilder().resolutionMode(resolutionMode).build();
    }

    public ItemGraphOptions withUserAliasDictionary(UserAliasDictionary userAliasDictionary) {
        return toBuilder().userAliasDictionary(userAliasDictionary).build();
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

    public static final class Builder {

        private boolean enabled;
        private int maxEntitiesPerItem;
        private int maxCausalReferencesPerItem;
        private int maxTemporalLinksPerItem;
        private int maxSemanticLinksPerItem;
        private double semanticMinScore;
        private int semanticSearchHeadroom;
        private int semanticLinkConcurrency;
        private int semanticSourceWindowSize;
        private boolean entityOverlapSemanticLinksEnabled;
        private int minSharedEntitiesForSemanticLink;
        private int maxEntityOverlapLinksPerItem;
        private int maxItemsPerEntityForSemanticLink;
        private float entityOverlapMinMentionConfidence;
        private EntityResolutionMode resolutionMode;
        private int maxResolutionCandidatesPerMention;
        private double resolutionMergeThreshold;
        private Set<String> supportedLanguagePacks;
        private CrossScriptMergePolicy crossScriptMergePolicy;
        private AliasEvidenceMode aliasEvidenceMode;
        private UserAliasDictionary userAliasDictionary;

        private Builder() {
            this(ItemGraphOptions.defaults());
        }

        private Builder(ItemGraphOptions options) {
            this.enabled = options.enabled();
            this.maxEntitiesPerItem = options.maxEntitiesPerItem();
            this.maxCausalReferencesPerItem = options.maxCausalReferencesPerItem();
            this.maxTemporalLinksPerItem = options.maxTemporalLinksPerItem();
            this.maxSemanticLinksPerItem = options.maxSemanticLinksPerItem();
            this.semanticMinScore = options.semanticMinScore();
            this.semanticSearchHeadroom = options.semanticSearchHeadroom();
            this.semanticLinkConcurrency = options.semanticLinkConcurrency();
            this.semanticSourceWindowSize = options.semanticSourceWindowSize();
            this.entityOverlapSemanticLinksEnabled = options.entityOverlapSemanticLinksEnabled();
            this.minSharedEntitiesForSemanticLink = options.minSharedEntitiesForSemanticLink();
            this.maxEntityOverlapLinksPerItem = options.maxEntityOverlapLinksPerItem();
            this.maxItemsPerEntityForSemanticLink = options.maxItemsPerEntityForSemanticLink();
            this.entityOverlapMinMentionConfidence = options.entityOverlapMinMentionConfidence();
            this.resolutionMode = options.resolutionMode();
            this.maxResolutionCandidatesPerMention = options.maxResolutionCandidatesPerMention();
            this.resolutionMergeThreshold = options.resolutionMergeThreshold();
            this.supportedLanguagePacks = options.supportedLanguagePacks();
            this.crossScriptMergePolicy = options.crossScriptMergePolicy();
            this.aliasEvidenceMode = options.aliasEvidenceMode();
            this.userAliasDictionary = options.userAliasDictionary();
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder maxEntitiesPerItem(int maxEntitiesPerItem) {
            this.maxEntitiesPerItem = maxEntitiesPerItem;
            return this;
        }

        public Builder maxCausalReferencesPerItem(int maxCausalReferencesPerItem) {
            this.maxCausalReferencesPerItem = maxCausalReferencesPerItem;
            return this;
        }

        public Builder maxTemporalLinksPerItem(int maxTemporalLinksPerItem) {
            this.maxTemporalLinksPerItem = maxTemporalLinksPerItem;
            return this;
        }

        public Builder maxSemanticLinksPerItem(int maxSemanticLinksPerItem) {
            this.maxSemanticLinksPerItem = maxSemanticLinksPerItem;
            return this;
        }

        public Builder semanticMinScore(double semanticMinScore) {
            this.semanticMinScore = semanticMinScore;
            return this;
        }

        public Builder semanticSearchHeadroom(int semanticSearchHeadroom) {
            this.semanticSearchHeadroom = semanticSearchHeadroom;
            return this;
        }

        public Builder semanticLinkConcurrency(int semanticLinkConcurrency) {
            this.semanticLinkConcurrency = semanticLinkConcurrency;
            return this;
        }

        public Builder semanticSourceWindowSize(int semanticSourceWindowSize) {
            this.semanticSourceWindowSize = semanticSourceWindowSize;
            return this;
        }

        public Builder entityOverlapSemanticLinksEnabled(
                boolean entityOverlapSemanticLinksEnabled) {
            this.entityOverlapSemanticLinksEnabled = entityOverlapSemanticLinksEnabled;
            return this;
        }

        public Builder minSharedEntitiesForSemanticLink(int minSharedEntitiesForSemanticLink) {
            this.minSharedEntitiesForSemanticLink = minSharedEntitiesForSemanticLink;
            return this;
        }

        public Builder maxEntityOverlapLinksPerItem(int maxEntityOverlapLinksPerItem) {
            this.maxEntityOverlapLinksPerItem = maxEntityOverlapLinksPerItem;
            return this;
        }

        public Builder maxItemsPerEntityForSemanticLink(int maxItemsPerEntityForSemanticLink) {
            this.maxItemsPerEntityForSemanticLink = maxItemsPerEntityForSemanticLink;
            return this;
        }

        public Builder entityOverlapMinMentionConfidence(float entityOverlapMinMentionConfidence) {
            this.entityOverlapMinMentionConfidence = entityOverlapMinMentionConfidence;
            return this;
        }

        public Builder resolutionMode(EntityResolutionMode resolutionMode) {
            this.resolutionMode = resolutionMode;
            return this;
        }

        public Builder maxResolutionCandidatesPerMention(int maxResolutionCandidatesPerMention) {
            this.maxResolutionCandidatesPerMention = maxResolutionCandidatesPerMention;
            return this;
        }

        public Builder resolutionMergeThreshold(double resolutionMergeThreshold) {
            this.resolutionMergeThreshold = resolutionMergeThreshold;
            return this;
        }

        public Builder supportedLanguagePacks(Set<String> supportedLanguagePacks) {
            this.supportedLanguagePacks = supportedLanguagePacks;
            return this;
        }

        public Builder crossScriptMergePolicy(CrossScriptMergePolicy crossScriptMergePolicy) {
            this.crossScriptMergePolicy = crossScriptMergePolicy;
            return this;
        }

        public Builder aliasEvidenceMode(AliasEvidenceMode aliasEvidenceMode) {
            this.aliasEvidenceMode = aliasEvidenceMode;
            return this;
        }

        public Builder userAliasDictionary(UserAliasDictionary userAliasDictionary) {
            this.userAliasDictionary = userAliasDictionary;
            return this;
        }

        public ItemGraphOptions build() {
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
                    entityOverlapSemanticLinksEnabled,
                    minSharedEntitiesForSemanticLink,
                    maxEntityOverlapLinksPerItem,
                    maxItemsPerEntityForSemanticLink,
                    entityOverlapMinMentionConfidence,
                    resolutionMode,
                    maxResolutionCandidatesPerMention,
                    resolutionMergeThreshold,
                    supportedLanguagePacks,
                    crossScriptMergePolicy,
                    aliasEvidenceMode,
                    userAliasDictionary);
        }
    }
}
