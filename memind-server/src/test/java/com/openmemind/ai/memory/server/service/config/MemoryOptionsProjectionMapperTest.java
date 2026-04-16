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
                        "extraction.insight.graphAssist.enabled",
                        "extraction.insight.graphAssist.maxContextChars");

        updateValue(projection, "extraction.item.graph.enabled", true);
        updateValue(projection, "extraction.insight.graphAssist.enabled", true);
        updateValue(projection, "extraction.insight.graphAssist.maxContextChars", 1600);

        var rebuilt = mapper.toOptions(projection);

        assertThat(rebuilt.extraction().item().graph().enabled()).isTrue();
        assertThat(rebuilt.extraction().insight().graphAssist().enabled()).isTrue();
        assertThat(rebuilt.extraction().insight().graphAssist().maxContextChars()).isEqualTo(1600);
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
}
