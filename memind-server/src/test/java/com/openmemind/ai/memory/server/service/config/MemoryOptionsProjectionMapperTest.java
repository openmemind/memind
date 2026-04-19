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

        assertThat(projection).containsKeys("extraction", "retrieval");
        assertThat(projection.get("extraction"))
                .extracting(MemoryOptionItemView::key)
                .contains(
                        "extraction.common.timeout",
                        "extraction.rawdata.conversation.messagesPerChunk",
                        "extraction.rawdata.vectorBatchSize");
        assertThat(projection.get("retrieval"))
                .extracting(MemoryOptionItemView::key)
                .contains("retrieval.simple.timeout", "retrieval.advanced.rerank.mode");
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

        assertThat(projection.get("extraction"))
                .extracting(MemoryOptionItemView::key)
                .contains(
                        "extraction.item.graph.enabled",
                        "extraction.item.graph.semanticSearchHeadroom",
                        "extraction.item.graph.semanticLinkConcurrency",
                        "extraction.item.graph.semanticSourceWindowSize",
                        "extraction.insight.graphAssist.enabled",
                        "extraction.insight.graphAssist.maxContextChars");

        assertThat(
                        projection.get("extraction").stream()
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

        assertThat(projection.get("retrieval"))
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

        assertThat(projection.get("retrieval"))
                .extracting(MemoryOptionItemView::key)
                .contains(
                        "retrieval.deep.graphAssist.enabled",
                        "retrieval.deep.graphAssist.maxSeedItems",
                        "retrieval.deep.graphAssist.semanticEvidenceDecayFactor",
                        "retrieval.deep.graphAssist.timeout");
        assertThat(
                        projection.get("retrieval").stream()
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

        assertThat(projection).containsKey("memoryThread");
        assertThat(projection.get("memoryThread"))
                .extracting(MemoryOptionItemView::key)
                .contains(
                        "memoryThread.enabled",
                        "memoryThread.derivation.enabled",
                        "memoryThread.rule.maxCandidateThreads",
                        "memoryThread.rule.maxRetrievalMembersPerThread",
                        "memoryThread.lifecycle.dormantAfter");
        assertThat(projection.get("retrieval"))
                .extracting(MemoryOptionItemView::key)
                .contains(
                        "retrieval.simple.memoryThreadAssist.enabled",
                        "retrieval.deep.memoryThreadAssist.enabled");

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
