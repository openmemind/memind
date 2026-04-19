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

import com.openmemind.ai.memory.core.builder.DeepMemoryThreadAssistOptions;
import com.openmemind.ai.memory.core.builder.DeepRetrievalGraphOptions;
import com.openmemind.ai.memory.core.builder.DeepRetrievalOptions;
import com.openmemind.ai.memory.core.builder.ExtractionCommonOptions;
import com.openmemind.ai.memory.core.builder.ExtractionOptions;
import com.openmemind.ai.memory.core.builder.InsightExtractionOptions;
import com.openmemind.ai.memory.core.builder.InsightGraphAssistOptions;
import com.openmemind.ai.memory.core.builder.ItemExtractionOptions;
import com.openmemind.ai.memory.core.builder.ItemGraphOptions;
import com.openmemind.ai.memory.core.builder.MemoryBuildOptions;
import com.openmemind.ai.memory.core.builder.MemoryThreadDerivationOptions;
import com.openmemind.ai.memory.core.builder.MemoryThreadLifecycleOptions;
import com.openmemind.ai.memory.core.builder.MemoryThreadOptions;
import com.openmemind.ai.memory.core.builder.MemoryThreadRuleOptions;
import com.openmemind.ai.memory.core.builder.PromptBudgetOptions;
import com.openmemind.ai.memory.core.builder.RawDataExtractionOptions;
import com.openmemind.ai.memory.core.builder.RetrievalOptions;
import com.openmemind.ai.memory.core.builder.SimpleMemoryThreadAssistOptions;
import com.openmemind.ai.memory.core.builder.SimpleRetrievalGraphOptions;
import com.openmemind.ai.memory.core.builder.SimpleRetrievalOptions;
import com.openmemind.ai.memory.core.extraction.insight.scheduler.InsightBuildConfig;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class MemoryOptionsCodecTest {

    private final MemoryOptionsCodec codec = new MemoryOptionsCodec(new ObjectMapper());

    @Test
    void roundTripsMemoryBuildOptions() {
        var options = MemoryBuildOptions.builder().build();

        String json = codec.write(options);

        assertThat(codec.read(json)).isEqualTo(options);
    }

    @Test
    void roundTripsGraphAssistOptions() {
        var options =
                MemoryBuildOptions.builder()
                        .extraction(
                                new ExtractionOptions(
                                        ExtractionCommonOptions.defaults(),
                                        RawDataExtractionOptions.defaults(),
                                        new ItemExtractionOptions(
                                                false,
                                                PromptBudgetOptions.defaults(),
                                                ItemGraphOptions.defaults().withEnabled(true)),
                                        new InsightExtractionOptions(
                                                true,
                                                InsightBuildConfig.defaults(),
                                                new InsightGraphAssistOptions(
                                                        true, 4, 8, 7, 1600, false))))
                        .build();

        String json = codec.write(options);

        assertThat(codec.read(json)).isEqualTo(options);
    }

    @Test
    void roundTripsItemGraphSemanticHardeningOptions() {
        var options =
                MemoryBuildOptions.builder()
                        .extraction(
                                new ExtractionOptions(
                                        ExtractionCommonOptions.defaults(),
                                        RawDataExtractionOptions.defaults(),
                                        new ItemExtractionOptions(
                                                false,
                                                PromptBudgetOptions.defaults(),
                                                ItemGraphOptions.defaults()
                                                        .withEnabled(true)
                                                        .withSemanticSearchHeadroom(6)
                                                        .withSemanticLinkConcurrency(2)
                                                        .withSemanticSourceWindowSize(64)),
                                        InsightExtractionOptions.defaults()))
                        .build();

        String json = codec.write(options);

        assertThat(codec.read(json)).isEqualTo(options);
    }

    @Test
    void roundTripsRetrievalGraphAssistOptions() {
        var options =
                MemoryBuildOptions.builder()
                        .retrieval(
                                new RetrievalOptions(
                                        RetrievalOptions.defaults().common(),
                                        new SimpleRetrievalOptions(
                                                RetrievalOptions.defaults().simple().timeout(),
                                                RetrievalOptions.defaults().simple().insightTopK(),
                                                RetrievalOptions.defaults().simple().itemTopK(),
                                                RetrievalOptions.defaults().simple().rawDataTopK(),
                                                false,
                                                new SimpleRetrievalGraphOptions(
                                                        true,
                                                        4,
                                                        12,
                                                        2,
                                                        2,
                                                        2,
                                                        3,
                                                        8,
                                                        0.35d,
                                                        0.55d,
                                                        0.70f,
                                                        3,
                                                        0.65d,
                                                        java.time.Duration.ofMillis(350))),
                                        RetrievalOptions.defaults().deep(),
                                        RetrievalOptions.defaults().advanced()))
                        .build();

        String json = codec.write(options);

        assertThat(codec.read(json)).isEqualTo(options);
    }

    @Test
    void roundTripsDeepRetrievalGraphAssistOptions() {
        var options =
                MemoryBuildOptions.builder()
                        .retrieval(
                                new RetrievalOptions(
                                        RetrievalOptions.defaults().common(),
                                        RetrievalOptions.defaults().simple(),
                                        new DeepRetrievalOptions(
                                                RetrievalOptions.defaults().deep().timeout(),
                                                RetrievalOptions.defaults().deep().insightTopK(),
                                                RetrievalOptions.defaults().deep().itemTopK(),
                                                RetrievalOptions.defaults().deep().rawDataEnabled(),
                                                RetrievalOptions.defaults().deep().rawDataTopK(),
                                                RetrievalOptions.defaults().deep().queryExpansion(),
                                                RetrievalOptions.defaults().deep().sufficiency(),
                                                new DeepRetrievalGraphOptions(
                                                        true,
                                                        6,
                                                        14,
                                                        2,
                                                        2,
                                                        2,
                                                        4,
                                                        8,
                                                        0.30d,
                                                        0.55d,
                                                        0.70f,
                                                        5,
                                                        0.40d,
                                                        java.time.Duration.ofMillis(450))),
                                        RetrievalOptions.defaults().advanced()))
                        .build();

        String json = codec.write(options);

        assertThat(codec.read(json)).isEqualTo(options);
    }

    @Test
    void roundTripsMemoryThreadOptions() {
        var defaults = RetrievalOptions.defaults();
        var options =
                MemoryBuildOptions.builder()
                        .retrieval(
                                new RetrievalOptions(
                                        defaults.common(),
                                        new SimpleRetrievalOptions(
                                                defaults.simple().timeout(),
                                                defaults.simple().insightTopK(),
                                                defaults.simple().itemTopK(),
                                                defaults.simple().rawDataTopK(),
                                                defaults.simple().keywordSearchEnabled(),
                                                defaults.simple().graphAssist(),
                                                new SimpleMemoryThreadAssistOptions(
                                                        true, 3, 2, 4, Duration.ofMillis(180))),
                                        new DeepRetrievalOptions(
                                                defaults.deep().timeout(),
                                                defaults.deep().insightTopK(),
                                                defaults.deep().itemTopK(),
                                                defaults.deep().rawDataEnabled(),
                                                defaults.deep().rawDataTopK(),
                                                defaults.deep().queryExpansion(),
                                                defaults.deep().sufficiency(),
                                                defaults.deep().graphAssist(),
                                                new DeepMemoryThreadAssistOptions(
                                                        true, 2, 2, 6, Duration.ofMillis(220))),
                                        defaults.advanced()))
                        .memoryThread(
                                new MemoryThreadOptions(
                                        true,
                                        new MemoryThreadDerivationOptions(true, false),
                                        new MemoryThreadRuleOptions(0.80d, 0.72d, 5, 24, 4),
                                        new MemoryThreadLifecycleOptions(
                                                Duration.ofDays(10), Duration.ofDays(30))))
                        .build();

        String json = codec.write(options);

        assertThat(codec.read(json)).isEqualTo(options);
    }
}
