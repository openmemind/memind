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
