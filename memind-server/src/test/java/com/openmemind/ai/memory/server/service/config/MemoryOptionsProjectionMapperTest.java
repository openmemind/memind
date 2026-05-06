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
package com.openmemind.ai.memory.server.service.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.builder.MemoryBuildOptions;
import com.openmemind.ai.memory.server.domain.config.view.MemoryOptionItemView;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MemoryOptionsProjectionMapperTest {

    private final MemoryOptionsProjectionMapper mapper = new MemoryOptionsProjectionMapper();

    @Test
    void mapsCanonicalOptionsToGroupedProjection() {
        var projection = mapper.toProjection(MemoryBuildOptions.defaults());

        assertThat(projection)
                .containsKeys(
                        "extraction.common",
                        "extraction.rawdata",
                        "extraction.item",
                        "extraction.itemGraph",
                        "extraction.insight",
                        "retrieval.common",
                        "retrieval.simple",
                        "retrieval.simpleGraphAssist",
                        "retrieval.simpleThreadAssist",
                        "retrieval.deep",
                        "retrieval.deepGraphAssist",
                        "retrieval.deepThreadAssist",
                        "retrieval.rerank",
                        "retrieval.scoring",
                        "memoryThread.lifecycle",
                        "memoryThread.enrichment")
                .doesNotContainKeys("extraction", "retrieval", "memoryThread");
        assertThat(projection.get("extraction.common"))
                .extracting(MemoryOptionItemView::key)
                .containsExactly(
                        "extraction.common.defaultScope",
                        "extraction.common.timeout",
                        "extraction.common.language");
        assertThat(projection.get("extraction.rawdata"))
                .extracting(MemoryOptionItemView::key)
                .contains(
                        "extraction.rawdata.conversation.messagesPerChunk",
                        "extraction.rawdata.vectorBatchSize");
        assertThat(projection.get("retrieval.simple"))
                .extracting(MemoryOptionItemView::key)
                .contains("retrieval.simple.timeout", "retrieval.simple.itemTopK");
        assertThat(projection.get("retrieval.rerank"))
                .extracting(MemoryOptionItemView::key)
                .contains("retrieval.advanced.rerank.mode");
    }

    @Test
    void projectsDirectlyEditableTypesForAdminEditors() {
        var projection = mapper.toProjection(MemoryBuildOptions.defaults());

        MemoryOptionItemView defaultScope = findItem(projection, "extraction.common.defaultScope");
        assertThat(defaultScope.type()).isEqualTo("string");
        assertThat(defaultScope.value()).isEqualTo("USER");
        assertThat(defaultScope.defaultValue()).isEqualTo("USER");
        assertThat(defaultScope.constraints())
                .containsEntry("allowedValues", java.util.List.of("USER", "AGENT"));

        MemoryOptionItemView timeout = findItem(projection, "extraction.common.timeout");
        assertThat(timeout.type()).isEqualTo("string");
        assertThat(timeout.value()).isInstanceOf(String.class);
        assertThat(timeout.constraints()).containsEntry("format", "iso-8601-duration");

        MemoryOptionItemView threshold =
                findItem(projection, "retrieval.simple.graphAssist.graphChannelWeight");
        assertThat(threshold.type()).isEqualTo("double");
    }

    @Test
    void groupsRuntimeOptionsByAdminEditingDomain() {
        var projection = mapper.toProjection(MemoryBuildOptions.defaults());

        assertThat(projection.get("extraction.itemGraph"))
                .extracting(MemoryOptionItemView::key)
                .contains(
                        "extraction.item.graph.enabled",
                        "extraction.item.graph.maxEntitiesPerItem",
                        "extraction.item.graph.supportedLanguagePacks",
                        "extraction.item.graph.userAliasDictionary.enabled");
        assertThat(projection.get("extraction.insight"))
                .extracting(MemoryOptionItemView::key)
                .contains(
                        "extraction.insight.enabled",
                        "extraction.insight.build.groupingThreshold",
                        "extraction.insight.graphAssist.enabled");
        assertThat(projection.get("retrieval.simpleGraphAssist"))
                .extracting(MemoryOptionItemView::key)
                .contains(
                        "retrieval.simple.graphAssist.enabled",
                        "retrieval.simple.graphAssist.maxSeedItems",
                        "retrieval.simple.graphAssist.mode");
        assertThat(projection.get("retrieval.deepGraphAssist"))
                .extracting(MemoryOptionItemView::key)
                .contains(
                        "retrieval.deep.graphAssist.enabled",
                        "retrieval.deep.graphAssist.maxSeedItems",
                        "retrieval.deep.graphAssist.mode");
        assertThat(projection.get("retrieval.simpleThreadAssist"))
                .extracting(MemoryOptionItemView::key)
                .contains(
                        "retrieval.simple.memoryThreadAssist.enabled",
                        "retrieval.simple.memoryThreadAssist.maxThreads");
        assertThat(projection.get("retrieval.deepThreadAssist"))
                .extracting(MemoryOptionItemView::key)
                .contains(
                        "retrieval.deep.memoryThreadAssist.enabled",
                        "retrieval.deep.memoryThreadAssist.maxThreads");
        assertThat(projection.get("retrieval.scoring"))
                .extracting(MemoryOptionItemView::key)
                .contains(
                        "retrieval.advanced.scoring.fusion.k",
                        "retrieval.advanced.scoring.timeDecay.rate",
                        "retrieval.advanced.scoring.keywordSearch.probeTopK");
        assertThat(projection.get("memoryThread.lifecycle"))
                .extracting(MemoryOptionItemView::key)
                .contains(
                        "memoryThread.enabled",
                        "memoryThread.derivation.enabled",
                        "memoryThread.rule.matchThreshold",
                        "memoryThread.lifecycle.dormantAfter");
        assertThat(projection.get("memoryThread.enrichment"))
                .extracting(MemoryOptionItemView::key)
                .contains(
                        "memoryThread.enrichment.enabled",
                        "memoryThread.enrichment.minimumWallClockGapBetweenRuns");
    }

    @Test
    void rebuildsCanonicalOptionsFromProjectionValues() {
        var projection = mapper.toProjection(MemoryBuildOptions.defaults());
        updateValue(projection, "extraction.item.foresightEnabled", true);
        updateValue(projection, "retrieval.simple.itemTopK", 21);
        updateValue(projection, "retrieval.deep.rawDataEnabled", true);

        var rebuilt = mapper.toOptions(projection);

        assertThat(rebuilt.extraction().item().foresightEnabled()).isTrue();
        assertThat(rebuilt.retrieval().simple().itemTopK()).isEqualTo(21);
        assertThat(rebuilt.retrieval().deep().rawDataEnabled()).isTrue();
    }

    @Test
    void projectionExposesInsightGraphAssistKeysAndRoundTripsValues() {
        var projection = mapper.toProjection(MemoryBuildOptions.defaults());

        assertThat(projection.get("extraction.itemGraph"))
                .extracting(MemoryOptionItemView::key)
                .contains(
                        "extraction.item.graph.enabled",
                        "extraction.item.graph.semanticSearchHeadroom",
                        "extraction.item.graph.semanticLinkConcurrency",
                        "extraction.item.graph.semanticSourceWindowSize");
        assertThat(projection.get("extraction.insight"))
                .extracting(MemoryOptionItemView::key)
                .contains(
                        "extraction.insight.graphAssist.enabled",
                        "extraction.insight.graphAssist.maxContextChars");

        assertThat(
                        projection.get("extraction.itemGraph").stream()
                                .filter(
                                        item ->
                                                item.key()
                                                        .equals(
                                                                "extraction.item.graph.semanticSourceWindowSize"))
                                .findFirst()
                                .orElseThrow()
                                .description())
                .contains("effectively disable windowing");

        updateValue(projection, "extraction.item.graph.enabled", true);
        updateValue(projection, "extraction.item.graph.semanticSearchHeadroom", 6);
        updateValue(projection, "extraction.item.graph.semanticLinkConcurrency", 2);
        updateValue(projection, "extraction.item.graph.semanticSourceWindowSize", 64);
        updateValue(projection, "extraction.insight.graphAssist.enabled", true);
        updateValue(projection, "extraction.insight.graphAssist.maxContextChars", 1600);

        var rebuilt = mapper.toOptions(projection);

        assertThat(rebuilt.extraction().item().graph().enabled()).isTrue();
        assertThat(rebuilt.extraction().item().graph().semanticSearchHeadroom()).isEqualTo(6);
        assertThat(rebuilt.extraction().item().graph().semanticLinkConcurrency()).isEqualTo(2);
        assertThat(rebuilt.extraction().item().graph().semanticSourceWindowSize()).isEqualTo(64);
        assertThat(rebuilt.extraction().insight().graphAssist().enabled()).isTrue();
        assertThat(rebuilt.extraction().insight().graphAssist().maxContextChars()).isEqualTo(1600);
    }

    @Test
    void projectionExposesRetrievalGraphAssistKeys() {
        var projection = mapper.toProjection(MemoryBuildOptions.defaults());

        assertThat(projection.get("retrieval.simpleGraphAssist"))
                .extracting(MemoryOptionItemView::key)
                .contains(
                        "retrieval.simple.graphAssist.enabled",
                        "retrieval.simple.graphAssist.maxSeedItems",
                        "retrieval.simple.graphAssist.semanticEvidenceDecayFactor",
                        "retrieval.simple.graphAssist.timeout");
    }

    @Test
    void projectionRoundTripsRetrievalGraphAssistValues() {
        var projection = mapper.toProjection(MemoryBuildOptions.defaults());

        updateValue(projection, "retrieval.simple.keywordSearchEnabled", false);
        updateValue(projection, "retrieval.simple.graphAssist.enabled", true);
        updateValue(projection, "retrieval.simple.graphAssist.maxSeedItems", 4);
        updateValue(projection, "retrieval.simple.graphAssist.semanticEvidenceDecayFactor", 0.65d);
        updateValue(projection, "retrieval.simple.graphAssist.timeout", "PT0.35S");

        var rebuilt = mapper.toOptions(projection);

        assertThat(rebuilt.retrieval().simple().keywordSearchEnabled()).isFalse();
        assertThat(rebuilt.retrieval().simple().graphAssist().enabled()).isTrue();
        assertThat(rebuilt.retrieval().simple().graphAssist().maxSeedItems()).isEqualTo(4);
        assertThat(rebuilt.retrieval().simple().graphAssist().semanticEvidenceDecayFactor())
                .isEqualTo(0.65d);
        assertThat(rebuilt.retrieval().simple().graphAssist().timeout())
                .isEqualTo(java.time.Duration.ofMillis(350));
    }

    @Test
    void projectionExposesDeepRetrievalGraphAssistKeysAndRoundTripsValues() {
        var projection = mapper.toProjection(MemoryBuildOptions.defaults());

        assertThat(projection.get("retrieval.deepGraphAssist"))
                .extracting(MemoryOptionItemView::key)
                .contains(
                        "retrieval.deep.graphAssist.enabled",
                        "retrieval.deep.graphAssist.maxSeedItems",
                        "retrieval.deep.graphAssist.semanticEvidenceDecayFactor",
                        "retrieval.deep.graphAssist.timeout");
        assertThat(
                        projection.get("retrieval.deepGraphAssist").stream()
                                .filter(
                                        item ->
                                                item.key()
                                                        .equals(
                                                                "retrieval.deep.graphAssist.enabled"))
                                .findFirst()
                                .orElseThrow()
                                .description())
                .isEqualTo(
                        "Whether deep retrieval may expand direct slow-path hits through the"
                                + " bounded item graph before rerank.");

        updateValue(projection, "retrieval.deep.graphAssist.enabled", true);
        updateValue(projection, "retrieval.deep.graphAssist.maxSeedItems", 6);
        updateValue(projection, "retrieval.deep.graphAssist.semanticEvidenceDecayFactor", 0.40d);
        updateValue(projection, "retrieval.deep.graphAssist.timeout", "PT0.45S");

        var rebuilt = mapper.toOptions(projection);

        assertThat(rebuilt.retrieval().deep().graphAssist().enabled()).isTrue();
        assertThat(rebuilt.retrieval().deep().graphAssist().maxSeedItems()).isEqualTo(6);
        assertThat(rebuilt.retrieval().deep().graphAssist().semanticEvidenceDecayFactor())
                .isEqualTo(0.40d);
        assertThat(rebuilt.retrieval().deep().graphAssist().timeout())
                .isEqualTo(java.time.Duration.ofMillis(450));
    }

    @Test
    void projectionExposesMemoryThreadKeysAndRoundTripsValues() {
        var projection = mapper.toProjection(MemoryBuildOptions.defaults());

        assertThat(projection).containsKey("memoryThread.lifecycle");
        assertThat(projection.get("memoryThread.lifecycle"))
                .extracting(MemoryOptionItemView::key)
                .contains(
                        "memoryThread.enabled",
                        "memoryThread.derivation.enabled",
                        "memoryThread.rule.maxCandidateThreads",
                        "memoryThread.rule.maxRetrievalMembersPerThread",
                        "memoryThread.lifecycle.dormantAfter");
        assertThat(projection.get("retrieval.simpleThreadAssist"))
                .extracting(MemoryOptionItemView::key)
                .contains("retrieval.simple.memoryThreadAssist.enabled");
        assertThat(projection.get("retrieval.deepThreadAssist"))
                .extracting(MemoryOptionItemView::key)
                .contains("retrieval.deep.memoryThreadAssist.enabled");

        updateValue(projection, "memoryThread.enabled", true);
        updateValue(projection, "memoryThread.derivation.enabled", true);
        updateValue(projection, "memoryThread.rule.maxCandidateThreads", 5);
        updateValue(projection, "memoryThread.rule.maxRetrievalMembersPerThread", 2);
        updateValue(projection, "memoryThread.lifecycle.dormantAfter", "PT240H");
        updateValue(projection, "retrieval.simple.memoryThreadAssist.enabled", true);

        var rebuilt = mapper.toOptions(projection);

        assertThat(rebuilt.memoryThread().enabled()).isTrue();
        assertThat(rebuilt.memoryThread().derivation().enabled()).isTrue();
        assertThat(rebuilt.memoryThread().rule().maxCandidateThreads()).isEqualTo(5);
        assertThat(rebuilt.memoryThread().rule().maxRetrievalMembersPerThread()).isEqualTo(2);
        assertThat(rebuilt.memoryThread().lifecycle().dormantAfter())
                .isEqualTo(java.time.Duration.ofDays(10));
        assertThat(rebuilt.retrieval().simple().memoryThreadAssist().enabled()).isTrue();
    }

    @Test
    void projectionExposesMemoryThreadEnrichmentKeysAndRoundTripsValues() {
        var projection = mapper.toProjection(MemoryBuildOptions.defaults());

        assertThat(projection.get("memoryThread.enrichment"))
                .extracting(MemoryOptionItemView::key)
                .contains(
                        "memoryThread.enrichment.enabled",
                        "memoryThread.enrichment.minimumEventCountForFirstEnrichment",
                        "memoryThread.enrichment.minimumMeaningfulEventDeltaForReenrichment",
                        "memoryThread.enrichment.minimumWallClockGapBetweenRuns",
                        "memoryThread.enrichment.timeout");

        updateValue(projection, "memoryThread.enrichment.enabled", true);
        updateValue(projection, "memoryThread.enrichment.minimumEventCountForFirstEnrichment", 3);
        updateValue(
                projection,
                "memoryThread.enrichment.minimumMeaningfulEventDeltaForReenrichment",
                4);
        updateValue(projection, "memoryThread.enrichment.minimumWallClockGapBetweenRuns", "PT20M");
        updateValue(projection, "memoryThread.enrichment.timeout", "PT7S");

        var rebuilt = mapper.toOptions(projection);

        assertThat(rebuilt.memoryThread().enrichment().enabled()).isTrue();
        assertThat(rebuilt.memoryThread().enrichment().minimumEventCountForFirstEnrichment())
                .isEqualTo(3);
        assertThat(rebuilt.memoryThread().enrichment().minimumMeaningfulEventDeltaForReenrichment())
                .isEqualTo(4);
        assertThat(rebuilt.memoryThread().enrichment().minimumWallClockGapBetweenRuns())
                .isEqualTo(java.time.Duration.ofMinutes(20));
        assertThat(rebuilt.memoryThread().enrichment().timeout())
                .isEqualTo(java.time.Duration.ofSeconds(7));
    }

    @Test
    void projectionUsesJsonFriendlyShapesForStructuredItemGraphValues() {
        var projection = mapper.toProjection(MemoryBuildOptions.defaults());

        assertThat(findItem(projection, "extraction.item.graph.supportedLanguagePacks").type())
                .isEqualTo("array");
        assertThat(
                        findItem(projection, "extraction.item.graph.supportedLanguagePacks")
                                .defaultValue())
                .isEqualTo(java.util.List.of("en", "zh"));
        assertThat(
                        findItem(
                                        projection,
                                        "extraction.item.graph.userAliasDictionary"
                                                + ".normalizedMentionLookupKeyToEntityKey")
                                .type())
                .isEqualTo("object");

        updateValue(
                projection,
                "extraction.item.graph.supportedLanguagePacks",
                java.util.List.of("en", "ja"));
        updateValue(projection, "extraction.item.graph.userAliasDictionary.enabled", true);
        updateValue(
                projection,
                "extraction.item.graph.userAliasDictionary.normalizedMentionLookupKeyToEntityKey",
                java.util.Map.of("person|alice", "entity://person/alice"));

        var rebuilt = mapper.toOptions(projection);

        assertThat(rebuilt.extraction().item().graph().supportedLanguagePacks())
                .containsExactly("en", "ja");
        assertThat(rebuilt.extraction().item().graph().userAliasDictionary().enabled()).isTrue();
        assertThat(
                        rebuilt.extraction()
                                .item()
                                .graph()
                                .userAliasDictionary()
                                .normalizedMentionLookupKeyToEntityKey())
                .containsEntry("person|alice", "entity://person/alice");
    }

    @Test
    void ignoresNullRuntimeOnlyFieldsFromProjectionDefinitions() {
        var projection = mapper.toProjection(MemoryBuildOptions.defaults());

        assertThat(projection.values().stream().flatMap(java.util.Collection::stream))
                .extracting(MemoryOptionItemView::key)
                .doesNotContain(
                        "extraction.rawdata.contentParser", "extraction.rawdata.resourceFetcher");
    }

    private static void updateValue(
            Map<String, java.util.List<MemoryOptionItemView>> projection,
            String key,
            Object value) {
        projection
                .values()
                .forEach(
                        items -> {
                            for (int i = 0; i < items.size(); i++) {
                                MemoryOptionItemView item = items.get(i);
                                if (item.key().equals(key)) {
                                    items.set(
                                            i,
                                            new MemoryOptionItemView(
                                                    item.key(),
                                                    value,
                                                    item.description(),
                                                    item.type(),
                                                    item.defaultValue(),
                                                    item.constraints()));
                                }
                            }
                        });
    }

    private static MemoryOptionItemView findItem(
            Map<String, java.util.List<MemoryOptionItemView>> projection, String key) {
        return projection.values().stream()
                .flatMap(java.util.Collection::stream)
                .filter(item -> item.key().equals(key))
                .findFirst()
                .orElseThrow();
    }
}
