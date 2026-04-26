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
package com.openmemind.ai.memory.core.extraction.item.graph.entity.resolve;

import com.openmemind.ai.memory.core.builder.ItemGraphOptions;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.extraction.item.graph.EntityAliasClass;
import com.openmemind.ai.memory.core.extraction.item.graph.EntityAliasObservation;
import com.openmemind.ai.memory.core.extraction.item.graph.entity.normalize.EntityKeyCanonicalizer;
import com.openmemind.ai.memory.core.extraction.item.graph.entity.normalize.EntityNameNormalizer;
import com.openmemind.ai.memory.core.extraction.item.graph.entity.normalize.LanguageAwareEntityNameNormalizer;
import com.openmemind.ai.memory.core.extraction.item.graph.entity.normalize.NormalizedEntityMentionCandidate;
import com.openmemind.ai.memory.core.store.graph.GraphEntityAlias;
import com.openmemind.ai.memory.core.store.graph.GraphEntityType;
import com.openmemind.ai.memory.core.store.graph.GraphOperations;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Default candidate retrieval for conservative resolution.
 */
public final class DefaultEntityCandidateRetriever implements EntityCandidateRetriever {

    private final GraphOperations graphOperations;
    private final EntityVariantKeyGenerator variantKeyGenerator;
    private final boolean historicalAliasLookupEnabled;
    private final EntityKeyCanonicalizer canonicalizer = new EntityKeyCanonicalizer();
    private final EntityNameNormalizer nameNormalizer = new LanguageAwareEntityNameNormalizer();

    public DefaultEntityCandidateRetriever(
            GraphOperations graphOperations,
            EntityVariantKeyGenerator variantKeyGenerator,
            boolean historicalAliasLookupEnabled) {
        this.graphOperations = Objects.requireNonNull(graphOperations, "graphOperations");
        this.variantKeyGenerator =
                Objects.requireNonNull(variantKeyGenerator, "variantKeyGenerator");
        this.historicalAliasLookupEnabled = historicalAliasLookupEnabled;
    }

    @Override
    public CandidateRetrievalResult retrieve(
            MemoryId memoryId,
            NormalizedEntityMentionCandidate candidate,
            ItemGraphOptions options) {
        LinkedHashSet<String> candidateKeys = new LinkedHashSet<>();
        List<CandidateProbe> probes = new ArrayList<>();
        EnumMap<EntityResolutionRejectReason, Integer> rejectCounts =
                new EnumMap<>(EntityResolutionRejectReason.class);
        AtomicInteger candidateCapHitCount = new AtomicInteger();

        addProbe(
                candidate.preResolutionEntityKey(),
                EntityResolutionSource.EXACT_CANONICAL_HIT,
                null,
                candidateKeys,
                probes,
                options,
                rejectCounts,
                candidateCapHitCount);

        probeExplicitAliasEvidence(
                candidate, candidateKeys, probes, options, rejectCounts, candidateCapHitCount);
        probeHistoricalAlias(
                memoryId,
                candidate,
                candidateKeys,
                probes,
                options,
                rejectCounts,
                candidateCapHitCount);

        if (supportsSafeVariants(candidate, options)) {
            for (EntityVariantKeyGenerator.VariantCandidate variant :
                    variantKeyGenerator.generate(candidate)) {
                String entityKey =
                        canonicalizer.canonicalize(
                                candidate.entityType(), variant.normalizedName(), Optional.empty());
                addProbe(
                        entityKey,
                        EntityResolutionSource.SAFE_VARIANT_HIT,
                        variant.aliasClass(),
                        candidateKeys,
                        probes,
                        options,
                        rejectCounts,
                        candidateCapHitCount);
            }
        }

        probeUserDictionary(
                candidate, candidateKeys, probes, options, rejectCounts, candidateCapHitCount);
        return new CandidateRetrievalResult(
                List.copyOf(probes), rejectCounts, candidateCapHitCount.get());
    }

    private void probeExplicitAliasEvidence(
            NormalizedEntityMentionCandidate candidate,
            Collection<String> candidateKeys,
            List<CandidateProbe> probes,
            ItemGraphOptions options,
            EnumMap<EntityResolutionRejectReason, Integer> rejectCounts,
            AtomicInteger candidateCapHitCount) {
        for (EntityAliasObservation observation : candidate.aliasObservations()) {
            if (!supportsExplicitAliasProbe(observation)) {
                continue;
            }
            String normalizedAlias = nameNormalizer.normalizeCanonical(observation.aliasSurface());
            String entityKey =
                    canonicalizer.canonicalize(
                            candidate.entityType(), normalizedAlias, Optional.empty());
            addProbe(
                    entityKey,
                    EntityResolutionSource.EXPLICIT_ALIAS_EVIDENCE_HIT,
                    observation.aliasClass(),
                    candidateKeys,
                    probes,
                    options,
                    rejectCounts,
                    candidateCapHitCount);
        }
    }

    private void probeHistoricalAlias(
            MemoryId memoryId,
            NormalizedEntityMentionCandidate candidate,
            Collection<String> candidateKeys,
            List<CandidateProbe> probes,
            ItemGraphOptions options,
            EnumMap<EntityResolutionRejectReason, Integer> rejectCounts,
            AtomicInteger candidateCapHitCount) {
        if (!historicalAliasLookupEnabled
                || candidate.entityType() == GraphEntityType.SPECIAL
                || candidate.normalizedName() == null
                || candidate.normalizedName().isBlank()) {
            return;
        }

        LinkedHashSet<String> matchedEntityKeys =
                graphOperations
                        .listEntityAliasesByNormalizedAlias(
                                memoryId, candidate.entityType(), candidate.normalizedName())
                        .stream()
                        .map(GraphEntityAlias::entityKey)
                        .collect(Collectors.toCollection(LinkedHashSet::new));

        if (matchedEntityKeys.size() > 1) {
            rejectCounts.merge(
                    EntityResolutionRejectReason.AMBIGUOUS_HISTORICAL_ALIAS, 1, Integer::sum);
            return;
        }
        if (matchedEntityKeys.isEmpty()) {
            return;
        }

        addProbe(
                matchedEntityKeys.iterator().next(),
                EntityResolutionSource.HISTORICAL_ALIAS_HIT,
                null,
                candidateKeys,
                probes,
                options,
                rejectCounts,
                candidateCapHitCount);
    }

    private void probeUserDictionary(
            NormalizedEntityMentionCandidate candidate,
            Collection<String> candidateKeys,
            List<CandidateProbe> probes,
            ItemGraphOptions options,
            EnumMap<EntityResolutionRejectReason, Integer> rejectCounts,
            AtomicInteger candidateCapHitCount) {
        options.userAliasDictionary()
                .lookup(candidate.entityType(), candidate.normalizedName())
                .ifPresent(
                        entityKey ->
                                addProbe(
                                        entityKey,
                                        EntityResolutionSource.USER_DICTIONARY_HIT,
                                        EntityAliasClass.USER_DICTIONARY,
                                        candidateKeys,
                                        probes,
                                        options,
                                        rejectCounts,
                                        candidateCapHitCount));
    }

    private void addProbe(
            String entityKey,
            EntityResolutionSource source,
            EntityAliasClass aliasClass,
            Collection<String> candidateKeys,
            List<CandidateProbe> probes,
            ItemGraphOptions options,
            EnumMap<EntityResolutionRejectReason, Integer> rejectCounts,
            AtomicInteger candidateCapHitCount) {
        if (entityKey == null || entityKey.isBlank() || candidateKeys.contains(entityKey)) {
            return;
        }
        if (candidateKeys.size() >= options.maxResolutionCandidatesPerMention()) {
            rejectCounts.merge(
                    EntityResolutionRejectReason.CANDIDATE_CAP_OVERFLOW, 1, Integer::sum);
            candidateCapHitCount.incrementAndGet();
            return;
        }
        candidateKeys.add(entityKey);
        probes.add(new CandidateProbe(entityKey, source, aliasClass));
    }

    private boolean supportsSafeVariants(
            NormalizedEntityMentionCandidate candidate, ItemGraphOptions options) {
        String normalizedName = candidate.normalizedName();
        if (normalizedName == null || normalizedName.isBlank()) {
            return false;
        }
        boolean hasHan = false;
        boolean asciiLatinOnly = true;
        for (int i = 0; i < normalizedName.length(); i++) {
            char ch = normalizedName.charAt(i);
            if (Character.isWhitespace(ch) || Character.isDigit(ch) || isAsciiPunctuation(ch)) {
                continue;
            }
            if (isHan(ch)) {
                hasHan = true;
                asciiLatinOnly = false;
                continue;
            }
            if (!(ch >= 'a' && ch <= 'z') && !(ch >= 'A' && ch <= 'Z')) {
                return false;
            }
        }
        if (hasHan) {
            return options.supportedLanguagePacks().contains("zh");
        }
        return asciiLatinOnly && options.supportedLanguagePacks().contains("en");
    }

    private static boolean supportsExplicitAliasProbe(EntityAliasObservation observation) {
        if (observation == null || observation.aliasClass() == null) {
            return false;
        }
        return observation.aliasClass() == EntityAliasClass.EXPLICIT_PARENTHETICAL
                || observation.aliasClass() == EntityAliasClass.EXPLICIT_SLASH_APPOSITION;
    }

    private static boolean isHan(char ch) {
        return Character.UnicodeScript.of(ch) == Character.UnicodeScript.HAN;
    }

    private static boolean isAsciiPunctuation(char ch) {
        return ch <= 0x7f && !Character.isLetterOrDigit(ch) && !Character.isWhitespace(ch);
    }
}
