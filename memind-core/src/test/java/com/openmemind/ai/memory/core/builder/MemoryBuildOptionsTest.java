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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.extraction.context.CommitDetectorConfig;
import com.openmemind.ai.memory.core.extraction.insight.scheduler.InsightBuildConfig;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoringConfig;
import com.openmemind.ai.memory.core.store.InMemoryMemoryStore;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.store.insight.InMemoryInsightOperations;
import com.openmemind.ai.memory.core.store.item.InMemoryItemOperations;
import com.openmemind.ai.memory.core.store.rawdata.InMemoryRawDataOperations;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class MemoryBuildOptionsTest {

    @Test
    void defaultsMatchBuilderBaseline() {
        assertThat(MemoryBuildOptions.defaults())
                .usingRecursiveComparison()
                .isEqualTo(MemoryBuildOptions.builder().build());
    }

    @Test
    void overridingExtractionLeavesRetrievalBranchUntouched() {
        var retrievalDefaults = RetrievalOptions.defaults();
        var customExtraction =
                new ExtractionOptions(
                        new ExtractionCommonOptions(
                                MemoryScope.AGENT, Duration.ofSeconds(30), "Chinese"),
                        RawDataExtractionOptions.defaults(),
                        ItemExtractionOptions.defaults(),
                        InsightExtractionOptions.defaults());

        var options = MemoryBuildOptions.builder().extraction(customExtraction).build();

        assertThat(options.extraction()).isEqualTo(customExtraction);
        assertThat(options.retrieval()).isEqualTo(retrievalDefaults);
    }

    @Test
    void rawDataDefaultsExposeOnlySharedCorePolicies() {
        var defaults = RawDataExtractionOptions.defaults();
        var componentNames =
                java.util.Arrays.stream(RawDataExtractionOptions.class.getRecordComponents())
                        .map(java.lang.reflect.RecordComponent::getName)
                        .toList();

        assertThat(componentNames)
                .containsExactly("conversation", "commitDetection", "vectorBatchSize");
        assertThat(defaults.vectorBatchSize()).isEqualTo(64);
    }

    @Test
    void rawDataOptionsRejectNonPositiveVectorBatchSize() {
        assertThatThrownBy(
                        () ->
                                new RawDataExtractionOptions(
                                        com.openmemind.ai.memory.core.extraction.rawdata.chunk
                                                .ConversationChunkingConfig.DEFAULT,
                                        CommitDetectorConfig.defaults(),
                                        0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vectorBatchSize");
    }

    @Test
    void memoryThreadDefaultsStayDisabledAndConservative() {
        var defaults = MemoryBuildOptions.defaults().memoryThread();

        assertThat(defaults.enabled()).isFalse();
        assertThat(defaults.derivation().enabled()).isFalse();
        assertThat(defaults.derivation().async()).isTrue();
        assertThat(defaults.rule().maxCandidateThreads()).isEqualTo(4);
        assertThat(defaults.rule().maxMembersPerThread()).isEqualTo(32);
        assertThat(defaults.rule().maxRetrievalMembersPerThread()).isEqualTo(6);
    }

    @Test
    void sanitizerForceDisablesDerivationWhenItemGraphIsDisabled() {
        var invalid =
                MemoryBuildOptions.builder()
                        .extraction(
                                new ExtractionOptions(
                                        ExtractionCommonOptions.defaults(),
                                        RawDataExtractionOptions.defaults(),
                                        ItemExtractionOptions.defaults(),
                                        InsightExtractionOptions.defaults()))
                        .memoryThread(
                                MemoryThreadOptions.defaults()
                                        .withEnabled(true)
                                        .withDerivation(
                                                MemoryThreadDerivationOptions.defaults()
                                                        .withEnabled(true)))
                        .build();

        var result = new MemoryBuildOptionsSanitizer().sanitize(invalid, new InMemoryMemoryStore());

        assertThat(result.options().memoryThread().derivation().enabled()).isFalse();
        assertThat(result.warnings())
                .containsExactly(
                        "memoryThread.derivation.enabled requires extraction.item.graph.enabled;"
                                + " derivation was force-disabled");
        assertThat(result.memoryThreadForcedDisableReason()).isPresent();
        assertThat(result.memoryThreadForcedDisableReason().orElseThrow())
                .contains("memoryThread.derivation.enabled requires extraction.item.graph.enabled");
    }

    @Test
    void sanitizerForceDisablesDerivationWhenStoreGraphOperationsAreUnavailable() {
        var requested =
                MemoryBuildOptions.builder()
                        .extraction(
                                new ExtractionOptions(
                                        ExtractionCommonOptions.defaults(),
                                        RawDataExtractionOptions.defaults(),
                                        new ItemExtractionOptions(
                                                false,
                                                PromptBudgetOptions.defaults(),
                                                ItemGraphOptions.defaults().withEnabled(true)),
                                        InsightExtractionOptions.defaults()))
                        .memoryThread(
                                MemoryThreadOptions.defaults()
                                        .withEnabled(true)
                                        .withDerivation(
                                                MemoryThreadDerivationOptions.defaults()
                                                        .withEnabled(true)))
                        .build();

        MemoryStore legacyStore =
                MemoryStore.of(
                        new InMemoryRawDataOperations(),
                        new InMemoryItemOperations(),
                        new InMemoryInsightOperations());

        var result = new MemoryBuildOptionsSanitizer().sanitize(requested, legacyStore);

        assertThat(result.options().memoryThread().derivation().enabled()).isFalse();
        assertThat(result.warnings())
                .containsExactly(
                        "memoryThread.derivation.enabled requires store-backed typed item graph"
                                + " operations; derivation was force-disabled");
        assertThat(result.memoryThreadForcedDisableReason()).isPresent();
        assertThat(result.memoryThreadForcedDisableReason().orElseThrow())
                .contains(
                        "memoryThread.derivation.enabled requires store-backed typed item graph"
                                + " operations");
    }

    @Test
    void itemDefaultsExposePromptBudget() {
        var defaults = ItemExtractionOptions.defaults();

        assertThat(defaults.promptBudget().maxInputTokens()).isEqualTo(8192);
        assertThat(defaults.promptBudget().reservedOutputTokens()).isEqualTo(1200);
    }

    @Test
    void itemGraphOptionsDefaultsToDisabledAndKeepsPhaseOneCaps() {
        var options = ItemGraphOptions.defaults();

        assertThat(options.enabled()).isFalse();
        assertThat(options.maxEntitiesPerItem()).isEqualTo(8);
        assertThat(options.maxCausalReferencesPerItem()).isEqualTo(2);
        assertThat(options.maxTemporalLinksPerItem()).isEqualTo(10);
        assertThat(options.maxSemanticLinksPerItem()).isEqualTo(5);
        assertThat(options.semanticMinScore()).isEqualTo(0.82d);
        assertThat(options.semanticSearchHeadroom()).isEqualTo(4);
        assertThat(options.semanticLinkConcurrency()).isEqualTo(1);
    }

    @Test
    void legacyItemExtractionOptionsConstructorStillBuildsDisabledGraphConfig() {
        var options = new ItemExtractionOptions(false);

        assertThat(options.graph()).isEqualTo(ItemGraphOptions.defaults());
    }

    @Test
    void insightGraphAssistDefaultsStayDisabledAndBounded() {
        var options = InsightGraphAssistOptions.defaults();

        assertThat(options.enabled()).isFalse();
        assertThat(options.maxGroupingClusters()).isEqualTo(3);
        assertThat(options.maxRepresentativeItems()).isEqualTo(6);
        assertThat(options.maxRelationHints()).isEqualTo(6);
        assertThat(options.maxContextChars()).isEqualTo(1200);
        assertThat(options.reorderEvidence()).isTrue();
    }

    @Test
    void legacyInsightExtractionOptionsConstructorStillBuildsDisabledGraphAssist() {
        var options = new InsightExtractionOptions(true, InsightBuildConfig.defaults());

        assertThat(options.graphAssist()).isEqualTo(InsightGraphAssistOptions.defaults());
    }

    @Test
    void simpleRetrievalGraphOptionsDefaultToDisabledAndBounded() {
        var options = SimpleRetrievalGraphOptions.defaults();

        assertThat(options.enabled()).isFalse();
        assertThat(options.maxSeedItems()).isEqualTo(6);
        assertThat(options.maxExpandedItems()).isEqualTo(12);
        assertThat(options.maxSemanticNeighborsPerSeed()).isEqualTo(2);
        assertThat(options.maxTemporalNeighborsPerSeed()).isEqualTo(2);
        assertThat(options.maxCausalNeighborsPerSeed()).isEqualTo(2);
        assertThat(options.maxEntitySiblingItemsPerSeed()).isEqualTo(3);
        assertThat(options.maxItemsPerEntity()).isEqualTo(8);
        assertThat(options.graphChannelWeight()).isEqualTo(0.35d);
        assertThat(options.minLinkStrength()).isEqualTo(0.55d);
        assertThat(options.minMentionConfidence()).isEqualTo(0.70f);
        assertThat(options.protectDirectTopK()).isEqualTo(3);
        assertThat(options.semanticEvidenceDecayFactor()).isEqualTo(0.5d);
        assertThat(options.timeout()).isEqualTo(Duration.ofMillis(200));
    }

    @Test
    void legacySimpleRetrievalOptionsConstructorStillBuildsDisabledGraphAssist() {
        var options = new SimpleRetrievalOptions(Duration.ofSeconds(10), 5, 15, 5, true);

        assertThat(options.graphAssist()).isEqualTo(SimpleRetrievalGraphOptions.defaults());
    }

    @Test
    void deepRetrievalGraphOptionsDefaultToDisabledAndBounded() {
        var options = DeepRetrievalGraphOptions.defaults();

        assertThat(options.enabled()).isFalse();
        assertThat(options.maxSeedItems()).isEqualTo(8);
        assertThat(options.maxExpandedItems()).isEqualTo(16);
        assertThat(options.maxSemanticNeighborsPerSeed()).isEqualTo(2);
        assertThat(options.maxTemporalNeighborsPerSeed()).isEqualTo(2);
        assertThat(options.maxCausalNeighborsPerSeed()).isEqualTo(2);
        assertThat(options.maxEntitySiblingItemsPerSeed()).isEqualTo(4);
        assertThat(options.maxItemsPerEntity()).isEqualTo(8);
        assertThat(options.graphChannelWeight()).isEqualTo(0.30d);
        assertThat(options.minLinkStrength()).isEqualTo(0.55d);
        assertThat(options.minMentionConfidence()).isEqualTo(0.70f);
        assertThat(options.protectDirectTopK()).isEqualTo(5);
        assertThat(options.semanticEvidenceDecayFactor()).isEqualTo(0.5d);
        assertThat(options.timeout()).isEqualTo(Duration.ofMillis(300));
    }

    @Test
    void legacyDeepRetrievalOptionsConstructorStillBuildsDisabledGraphAssist() {
        var options =
                new DeepRetrievalOptions(
                        Duration.ofSeconds(10),
                        5,
                        15,
                        false,
                        0,
                        QueryExpansionOptions.defaults(),
                        SufficiencyOptions.defaults());

        assertThat(options.graphAssist()).isEqualTo(DeepRetrievalGraphOptions.defaults());
    }

    @Test
    void retrievalDefaultsExposeGraphAssistDefaults() {
        var options = RetrievalOptions.defaults();

        assertThat(options.simple().graphAssist())
                .isEqualTo(SimpleRetrievalGraphOptions.defaults());
        assertThat(options.advanced().scoring()).isEqualTo(ScoringConfig.defaults());
    }

    @Test
    void retrievalDefaultsExposeDeepGraphAssistDefaults() {
        var options = RetrievalOptions.defaults();

        assertThat(options.deep().graphAssist()).isEqualTo(DeepRetrievalGraphOptions.defaults());
    }
}
