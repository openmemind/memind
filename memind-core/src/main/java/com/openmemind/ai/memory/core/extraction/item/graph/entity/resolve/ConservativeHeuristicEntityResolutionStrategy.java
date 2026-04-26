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
import com.openmemind.ai.memory.core.extraction.item.graph.EntityResolutionMode;
import com.openmemind.ai.memory.core.extraction.item.graph.entity.normalize.EntityNormalizationVersions;
import com.openmemind.ai.memory.core.extraction.item.graph.entity.normalize.NormalizedEntityMentionCandidate;
import com.openmemind.ai.memory.core.extraction.item.graph.pipeline.model.NormalizedGraphBatch;
import com.openmemind.ai.memory.core.extraction.item.graph.pipeline.model.ResolvedGraphBatch;
import com.openmemind.ai.memory.core.store.graph.GraphEntity;
import com.openmemind.ai.memory.core.store.graph.GraphEntityType;
import com.openmemind.ai.memory.core.store.graph.GraphOperations;
import com.openmemind.ai.memory.core.store.graph.ItemEntityMention;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Conservative resolver that only merges into pre-existing deterministic candidates.
 */
public final class ConservativeHeuristicEntityResolutionStrategy
        implements EntityResolutionStrategy {

    private static final Comparator<ResolvedCandidate> BEST_CANDIDATE_ORDER =
            Comparator.comparingDouble(ResolvedCandidate::score)
                    .reversed()
                    .thenComparingInt(candidate -> sourcePriority(candidate.probe().source()))
                    .thenComparingInt(
                            candidate -> aliasPriority(candidate.probe().resolvedViaAliasClass()))
                    .thenComparing(candidate -> candidate.entity().entityKey());

    private final GraphOperations graphOperations;
    private final EntityCandidateRetriever candidateRetriever;

    public ConservativeHeuristicEntityResolutionStrategy(
            GraphOperations graphOperations, EntityCandidateRetriever candidateRetriever) {
        this.graphOperations = Objects.requireNonNull(graphOperations, "graphOperations");
        this.candidateRetriever = Objects.requireNonNull(candidateRetriever, "candidateRetriever");
    }

    @Override
    public ResolvedGraphBatch resolve(
            MemoryId memoryId, NormalizedGraphBatch batch, ItemGraphOptions options) {
        Map<String, GraphEntity> entities = new LinkedHashMap<>();
        List<ItemEntityMention> mentions = new ArrayList<>();
        DiagnosticsAccumulator diagnostics = new DiagnosticsAccumulator();

        for (NormalizedEntityMentionCandidate candidate : batch.candidates()) {
            diagnostics.recordAliasEvidenceObserved(candidate.aliasObservations().size());
            if (isReservedSpecialIdentity(candidate)) {
                resolveSpecialBypass(memoryId, candidate, entities, mentions, diagnostics);
                continue;
            }

            EntityCandidateRetriever.CandidateRetrievalResult retrieval =
                    candidateRetriever.retrieve(memoryId, candidate, options);
            retrieval.rejectCounts().forEach(diagnostics::recordRejected);
            for (int i = 0; i < retrieval.candidateCapHitCount(); i++) {
                diagnostics.recordCandidateCapHit();
            }

            Map<String, EntityCandidateRetriever.CandidateProbe> probesByEntityKey =
                    retrieval.probes().stream()
                            .collect(
                                    java.util.stream.Collectors.toMap(
                                            EntityCandidateRetriever.CandidateProbe::entityKey,
                                            java.util.function.Function.identity(),
                                            (left, right) -> left,
                                            LinkedHashMap::new));

            List<ResolvedCandidate> resolvedCandidates =
                    graphOperations
                            .listEntitiesByEntityKeys(memoryId, probesByEntityKey.keySet())
                            .stream()
                            .map(
                                    entity ->
                                            toResolvedCandidate(
                                                    entity,
                                                    candidate,
                                                    probesByEntityKey.get(entity.entityKey())))
                            .filter(Objects::nonNull)
                            .toList();

            resolvedCandidates.forEach(diagnostics::recordCandidate);
            List<ResolvedCandidate> accepted =
                    resolvedCandidates.stream()
                            .filter(
                                    resolved ->
                                            resolved.score() >= options.resolutionMergeThreshold())
                            .toList();
            int rejectedCount = resolvedCandidates.size() - accepted.size();
            if (rejectedCount > 0) {
                diagnostics.recordRejected(
                        EntityResolutionRejectReason.BELOW_THRESHOLD, rejectedCount);
            }

            if (!accepted.isEmpty()) {
                ResolvedCandidate selected =
                        accepted.stream().sorted(BEST_CANDIDATE_ORDER).findFirst().orElseThrow();
                diagnostics.recordAccepted(selected.score());
                diagnostics.recordAliasEvidenceMerged(selected.probe().source());
                GraphEntity existingEntity =
                        entities.getOrDefault(selected.entity().entityKey(), selected.entity());
                GraphEntity mergedEntity =
                        mergeResolvedEntity(
                                memoryId,
                                existingEntity,
                                candidate,
                                selected.probe().source(),
                                selected.probe().resolvedViaAliasClass());
                entities.put(mergedEntity.entityKey(), mergedEntity);
                mentions.add(
                        buildMention(
                                memoryId, candidate, mergedEntity.entityKey(), selected.probe()));
                continue;
            }

            if (!resolvedCandidates.isEmpty()) {
                diagnostics.recordMergeRejected();
            }
            GraphEntity currentEntity = entities.get(candidate.preResolutionEntityKey());
            GraphEntity created =
                    currentEntity == null
                            ? buildNewEntity(
                                    memoryId,
                                    candidate,
                                    EntityResolutionSource.EXACT_CANONICAL_HIT,
                                    null)
                            : mergeResolvedEntity(
                                    memoryId,
                                    currentEntity,
                                    candidate,
                                    EntityResolutionSource.EXACT_CANONICAL_HIT,
                                    null);
            entities.put(created.entityKey(), created);
            mentions.add(
                    buildMention(
                            memoryId,
                            candidate,
                            created.entityKey(),
                            new EntityCandidateRetriever.CandidateProbe(
                                    created.entityKey(),
                                    EntityResolutionSource.EXACT_CANONICAL_HIT,
                                    null)));
            diagnostics.recordCreated();
        }

        return new ResolvedGraphBatch(
                List.copyOf(entities.values()),
                List.of(),
                List.copyOf(mentions),
                batch.itemLinks(),
                batch.diagnostics(),
                diagnostics.toDiagnostics());
    }

    private void resolveSpecialBypass(
            MemoryId memoryId,
            NormalizedEntityMentionCandidate candidate,
            Map<String, GraphEntity> entities,
            List<ItemEntityMention> mentions,
            DiagnosticsAccumulator diagnostics) {
        String entityKey = candidate.preResolutionEntityKey();
        GraphEntity existing =
                entities.get(entityKey) != null
                        ? entities.get(entityKey)
                        : graphOperations
                                .listEntitiesByEntityKeys(memoryId, List.of(entityKey))
                                .stream()
                                .findFirst()
                                .orElse(null);
        GraphEntity entity =
                existing == null
                        ? buildNewEntity(
                                memoryId,
                                candidate,
                                EntityResolutionSource.EXACT_CANONICAL_HIT,
                                null)
                        : mergeResolvedEntity(
                                memoryId,
                                existing,
                                candidate,
                                EntityResolutionSource.EXACT_CANONICAL_HIT,
                                null);
        if (existing == null) {
            diagnostics.recordCreated();
        } else {
            diagnostics.recordAccepted(1.0d);
        }
        diagnostics.recordSpecialBypass();
        entities.put(entity.entityKey(), entity);
        mentions.add(
                buildMention(
                        memoryId,
                        candidate,
                        entity.entityKey(),
                        new EntityCandidateRetriever.CandidateProbe(
                                entity.entityKey(),
                                EntityResolutionSource.EXACT_CANONICAL_HIT,
                                null)));
    }

    private ResolvedCandidate toResolvedCandidate(
            GraphEntity entity,
            NormalizedEntityMentionCandidate mention,
            EntityCandidateRetriever.CandidateProbe probe) {
        if (entity == null || probe == null || entity.entityType() != mention.entityType()) {
            return null;
        }
        return new ResolvedCandidate(entity, probe, score(mention, probe));
    }

    private double score(
            NormalizedEntityMentionCandidate mention,
            EntityCandidateRetriever.CandidateProbe probe) {
        double base =
                switch (probe.source()) {
                    case EXACT_CANONICAL_HIT -> 1.0d;
                    case EXPLICIT_ALIAS_EVIDENCE_HIT -> 0.96d;
                    case HISTORICAL_ALIAS_HIT -> 0.95d;
                    case SAFE_VARIANT_HIT -> safeVariantScore(probe.resolvedViaAliasClass());
                    case USER_DICTIONARY_HIT -> 0.86d;
                };
        double salienceSupport = Math.min(Math.max(mention.salience(), 0.0f), 1.0f) * 0.05d;
        return Math.min(1.0d, base + salienceSupport);
    }

    private static double safeVariantScore(EntityAliasClass aliasClass) {
        if (aliasClass == null) {
            return 0.85d;
        }
        return switch (aliasClass) {
            case CASE_ONLY -> 0.96d;
            case PUNCTUATION -> 0.94d;
            case SPACING -> 0.92d;
            case ORG_SUFFIX -> 0.90d;
            case EXPLICIT_PARENTHETICAL, EXPLICIT_SLASH_APPOSITION -> 0.90d;
            case USER_DICTIONARY -> 0.86d;
        };
    }

    private static int sourcePriority(EntityResolutionSource source) {
        return switch (source) {
            case EXACT_CANONICAL_HIT -> 0;
            case EXPLICIT_ALIAS_EVIDENCE_HIT -> 1;
            case HISTORICAL_ALIAS_HIT -> 2;
            case SAFE_VARIANT_HIT -> 3;
            case USER_DICTIONARY_HIT -> 4;
        };
    }

    private static int aliasPriority(EntityAliasClass aliasClass) {
        if (aliasClass == null) {
            return -1;
        }
        return switch (aliasClass) {
            case CASE_ONLY -> 0;
            case PUNCTUATION -> 1;
            case SPACING -> 2;
            case ORG_SUFFIX -> 3;
            case EXPLICIT_PARENTHETICAL -> 4;
            case EXPLICIT_SLASH_APPOSITION -> 5;
            case USER_DICTIONARY -> 6;
        };
    }

    private static boolean isReservedSpecialIdentity(NormalizedEntityMentionCandidate candidate) {
        return candidate.entityType() == GraphEntityType.SPECIAL
                && candidate.preResolutionEntityKey() != null
                && candidate.preResolutionEntityKey().startsWith("special:");
    }

    private GraphEntity mergeResolvedEntity(
            MemoryId memoryId,
            GraphEntity existing,
            NormalizedEntityMentionCandidate candidate,
            EntityResolutionSource source,
            EntityAliasClass aliasClass) {
        return new GraphEntity(
                existing.entityKey(),
                memoryId.toIdentifier(),
                existing.displayName(),
                existing.entityType(),
                EntityAliasMetadataMerger.merge(
                        existing.metadata(),
                        aliasObservationsForPersistence(candidate, source, aliasClass),
                        candidate.createdAt()),
                existing.createdAt(),
                laterOf(existing.updatedAt(), candidate.createdAt()));
    }

    private GraphEntity buildNewEntity(
            MemoryId memoryId,
            NormalizedEntityMentionCandidate candidate,
            EntityResolutionSource source,
            EntityAliasClass aliasClass) {
        return new GraphEntity(
                candidate.preResolutionEntityKey(),
                memoryId.toIdentifier(),
                candidate.displayName(),
                candidate.entityType(),
                EntityAliasMetadataMerger.merge(
                        baseEntityMetadata(),
                        aliasObservationsForPersistence(candidate, source, aliasClass),
                        candidate.createdAt()),
                candidate.createdAt(),
                candidate.createdAt());
    }

    private ItemEntityMention buildMention(
            MemoryId memoryId,
            NormalizedEntityMentionCandidate candidate,
            String entityKey,
            EntityCandidateRetriever.CandidateProbe probe) {
        return new ItemEntityMention(
                memoryId.toIdentifier(),
                candidate.itemId(),
                entityKey,
                candidate.salience(),
                mentionMetadata(
                        candidate,
                        EntityResolutionMode.CONSERVATIVE,
                        probe.source(),
                        probe.resolvedViaAliasClass()),
                candidate.createdAt());
    }

    private List<EntityAliasObservation> aliasObservationsForPersistence(
            NormalizedEntityMentionCandidate candidate,
            EntityResolutionSource source,
            EntityAliasClass aliasClass) {
        List<EntityAliasObservation> persisted = new ArrayList<>(candidate.aliasObservations());
        if (aliasClass == EntityAliasClass.CASE_ONLY
                || aliasClass == EntityAliasClass.PUNCTUATION
                || aliasClass == EntityAliasClass.SPACING
                || aliasClass == EntityAliasClass.ORG_SUFFIX
                || aliasClass == EntityAliasClass.USER_DICTIONARY) {
            persisted.add(
                    new EntityAliasObservation(
                            candidate.displayName(),
                            aliasClass,
                            source.name().toLowerCase(Locale.ROOT),
                            candidate.salience()));
        }
        return List.copyOf(persisted);
    }

    private static Map<String, Object> baseEntityMetadata() {
        return Map.of(
                "source",
                "item_extraction",
                "normalizationVersion",
                EntityNormalizationVersions.STAGE1A_V1);
    }

    private static Map<String, Object> mentionMetadata(
            NormalizedEntityMentionCandidate candidate,
            EntityResolutionMode resolutionMode,
            EntityResolutionSource source,
            EntityAliasClass aliasClass) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", "item_extraction");
        metadata.put("rawName", candidate.rawName());
        metadata.put("rawTypeLabel", candidate.rawTypeLabel());
        metadata.put("normalizedName", candidate.normalizedName());
        metadata.put("normalizationVersion", EntityNormalizationVersions.STAGE1A_V1);
        metadata.put("resolutionMode", resolutionMode.name().toLowerCase(Locale.ROOT));
        metadata.put("resolutionSource", source.name().toLowerCase(Locale.ROOT));
        if (aliasClass != null) {
            metadata.put("resolvedViaAliasClass", aliasClass.wireValue());
        }
        return Map.copyOf(metadata);
    }

    private static java.time.Instant laterOf(java.time.Instant left, java.time.Instant right) {
        if (left == null) {
            return right;
        }
        if (right == null || left.isAfter(right)) {
            return left;
        }
        return right;
    }

    private record ResolvedCandidate(
            GraphEntity entity, EntityCandidateRetriever.CandidateProbe probe, double score) {}

    private static final class DiagnosticsAccumulator {

        private int candidateCount;
        private final EnumMap<EntityResolutionSource, Integer> candidateSourceCounts =
                new EnumMap<>(EntityResolutionSource.class);
        private final EnumMap<EntityResolutionRejectReason, Integer> candidateRejectCounts =
                new EnumMap<>(EntityResolutionRejectReason.class);
        private int mergeAcceptedCount;
        private int mergeRejectedCount;
        private int createNewCount;
        private int candidateCapHitCount;
        private int aliasEvidenceObservedCount;
        private int aliasEvidenceMergedCount;
        private int specialBypassCount;
        private final List<Double> acceptedScores = new ArrayList<>();

        private void recordCandidate(ResolvedCandidate candidate) {
            candidateCount++;
            candidateSourceCounts.merge(candidate.probe().source(), 1, Integer::sum);
        }

        private void recordRejected(EntityResolutionRejectReason reason, int count) {
            if (count <= 0) {
                return;
            }
            candidateRejectCounts.merge(reason, count, Integer::sum);
        }

        private void recordAccepted(double score) {
            mergeAcceptedCount++;
            acceptedScores.add(score);
        }

        private void recordAliasEvidenceMerged(EntityResolutionSource source) {
            if (source == EntityResolutionSource.EXPLICIT_ALIAS_EVIDENCE_HIT) {
                aliasEvidenceMergedCount++;
            }
        }

        private void recordMergeRejected() {
            mergeRejectedCount++;
        }

        private void recordCreated() {
            createNewCount++;
        }

        private void recordCandidateCapHit() {
            candidateCapHitCount++;
        }

        private void recordAliasEvidenceObserved(int count) {
            aliasEvidenceObservedCount += Math.max(count, 0);
        }

        private void recordSpecialBypass() {
            specialBypassCount++;
        }

        private EntityResolutionDiagnostics toDiagnostics() {
            return new EntityResolutionDiagnostics(
                    candidateCount,
                    candidateSourceCounts,
                    candidateRejectCounts,
                    mergeAcceptedCount,
                    mergeRejectedCount,
                    createNewCount,
                    0,
                    candidateCapHitCount,
                    aliasEvidenceObservedCount,
                    aliasEvidenceMergedCount,
                    specialBypassCount,
                    summarizeAcceptedScoreHistogram());
        }

        private String summarizeAcceptedScoreHistogram() {
            if (acceptedScores.isEmpty()) {
                return "";
            }
            int[] buckets = new int[5];
            for (double score : acceptedScores) {
                if (score >= 0.98d) {
                    buckets[0]++;
                } else if (score >= 0.95d) {
                    buckets[1]++;
                } else if (score >= 0.90d) {
                    buckets[2]++;
                } else if (score >= 0.85d) {
                    buckets[3]++;
                } else {
                    buckets[4]++;
                }
            }
            List<String> summary = new ArrayList<>();
            if (buckets[0] > 0) {
                summary.add("0.98-1.00=" + buckets[0]);
            }
            if (buckets[1] > 0) {
                summary.add("0.95-0.97=" + buckets[1]);
            }
            if (buckets[2] > 0) {
                summary.add("0.90-0.94=" + buckets[2]);
            }
            if (buckets[3] > 0) {
                summary.add("0.85-0.89=" + buckets[3]);
            }
            if (buckets[4] > 0) {
                summary.add("<0.85=" + buckets[4]);
            }
            return String.join(", ", summary);
        }
    }
}
