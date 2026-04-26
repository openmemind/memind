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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.openmemind.ai.memory.core.builder.ItemGraphOptions;
import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.extraction.item.graph.EntityResolutionMode;
import com.openmemind.ai.memory.core.extraction.item.graph.entity.normalize.NormalizedEntityMentionCandidate;
import com.openmemind.ai.memory.core.store.graph.GraphEntityAlias;
import com.openmemind.ai.memory.core.store.graph.GraphEntityType;
import com.openmemind.ai.memory.core.store.graph.InMemoryGraphOperations;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DefaultEntityCandidateRetrieverTest {

    private static final MemoryId MEMORY_ID = DefaultMemoryId.of("user-1", "agent-1");
    private static final Instant CREATED_AT = Instant.parse("2026-04-19T00:00:00Z");

    @Test
    void historicalAliasShouldProduceUniqueTypedCandidate() {
        var graphOps = new InMemoryGraphOperations();
        graphOps.upsertEntityAliases(
                MEMORY_ID,
                List.of(
                        new GraphEntityAlias(
                                MEMORY_ID.toIdentifier(),
                                "organization:google",
                                GraphEntityType.ORGANIZATION,
                                "谷歌",
                                1,
                                Map.of(),
                                CREATED_AT,
                                CREATED_AT)));

        var retriever =
                new DefaultEntityCandidateRetriever(
                        graphOps, new EntityVariantKeyGenerator(), true);

        var retrieval =
                retriever.retrieve(
                        MEMORY_ID,
                        candidate(
                                101L,
                                "谷歌",
                                "organization",
                                "谷歌",
                                "谷歌",
                                GraphEntityType.ORGANIZATION,
                                "organization:谷歌",
                                0.93f),
                        conservativeOptions());

        assertThat(retrieval.probes())
                .extracting(
                        EntityCandidateRetriever.CandidateProbe::entityKey,
                        EntityCandidateRetriever.CandidateProbe::source)
                .contains(
                        tuple("organization:google", EntityResolutionSource.HISTORICAL_ALIAS_HIT));
        assertThat(retrieval.rejectCounts()).isEmpty();
    }

    @Test
    void ambiguousHistoricalAliasShouldFailClosedAndRecordRejectReason() {
        var graphOps = new InMemoryGraphOperations();
        graphOps.upsertEntityAliases(
                MEMORY_ID,
                List.of(
                        new GraphEntityAlias(
                                MEMORY_ID.toIdentifier(),
                                "organization:google",
                                GraphEntityType.ORGANIZATION,
                                "谷歌",
                                1,
                                Map.of(),
                                CREATED_AT,
                                CREATED_AT),
                        new GraphEntityAlias(
                                MEMORY_ID.toIdentifier(),
                                "organization:google_hk",
                                GraphEntityType.ORGANIZATION,
                                "谷歌",
                                1,
                                Map.of(),
                                CREATED_AT,
                                CREATED_AT)));

        var retriever =
                new DefaultEntityCandidateRetriever(
                        graphOps, new EntityVariantKeyGenerator(), true);

        var retrieval =
                retriever.retrieve(
                        MEMORY_ID,
                        candidate(
                                101L,
                                "谷歌",
                                "organization",
                                "谷歌",
                                "谷歌",
                                GraphEntityType.ORGANIZATION,
                                "organization:谷歌",
                                0.93f),
                        conservativeOptions());

        assertThat(retrieval.probes())
                .extracting(EntityCandidateRetriever.CandidateProbe::source)
                .doesNotContain(EntityResolutionSource.HISTORICAL_ALIAS_HIT);
        assertThat(retrieval.rejectCounts())
                .containsEntry(EntityResolutionRejectReason.AMBIGUOUS_HISTORICAL_ALIAS, 1);
    }

    @Test
    void historicalAliasShouldNeverProbeReservedSpecialIdentities() {
        var graphOps = new InMemoryGraphOperations();
        graphOps.upsertEntityAliases(
                MEMORY_ID,
                List.of(
                        new GraphEntityAlias(
                                MEMORY_ID.toIdentifier(),
                                "special:user",
                                GraphEntityType.SPECIAL,
                                "用户",
                                1,
                                Map.of(),
                                CREATED_AT,
                                CREATED_AT)));

        var retriever =
                new DefaultEntityCandidateRetriever(
                        graphOps, new EntityVariantKeyGenerator(), true);

        var retrieval =
                retriever.retrieve(
                        MEMORY_ID,
                        candidate(
                                101L,
                                "用户",
                                "special",
                                "用户",
                                "用户",
                                GraphEntityType.SPECIAL,
                                "special:user",
                                0.90f),
                        conservativeOptions());

        assertThat(retrieval.probes())
                .extracting(EntityCandidateRetriever.CandidateProbe::source)
                .doesNotContain(EntityResolutionSource.HISTORICAL_ALIAS_HIT);
    }

    private static ItemGraphOptions conservativeOptions() {
        return ItemGraphOptions.defaults()
                .withEnabled(true)
                .withResolutionMode(EntityResolutionMode.CONSERVATIVE);
    }

    private static NormalizedEntityMentionCandidate candidate(
            long itemId,
            String rawName,
            String rawTypeLabel,
            String normalizedName,
            String displayName,
            GraphEntityType entityType,
            String preResolutionEntityKey,
            float salience) {
        return new NormalizedEntityMentionCandidate(
                itemId,
                MEMORY_ID.toIdentifier(),
                rawName,
                rawTypeLabel,
                normalizedName,
                displayName,
                entityType,
                preResolutionEntityKey,
                salience,
                List.of(),
                CREATED_AT);
    }
}
