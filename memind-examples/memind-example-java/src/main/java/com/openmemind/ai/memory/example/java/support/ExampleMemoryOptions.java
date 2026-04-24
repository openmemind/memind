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
package com.openmemind.ai.memory.example.java.support;

import com.openmemind.ai.memory.core.builder.ExtractionCommonOptions;
import com.openmemind.ai.memory.core.builder.ExtractionOptions;
import com.openmemind.ai.memory.core.builder.InsightExtractionOptions;
import com.openmemind.ai.memory.core.builder.ItemExtractionOptions;
import com.openmemind.ai.memory.core.builder.ItemGraphOptions;
import com.openmemind.ai.memory.core.builder.MemoryBuildOptions;
import com.openmemind.ai.memory.core.builder.RawDataExtractionOptions;
import com.openmemind.ai.memory.core.builder.RetrievalOptions;
import com.openmemind.ai.memory.core.builder.SimpleRetrievalGraphOptions;
import com.openmemind.ai.memory.core.builder.SimpleRetrievalOptions;
import com.openmemind.ai.memory.core.extraction.insight.scheduler.InsightBuildConfig;

public final class ExampleMemoryOptions {

    private ExampleMemoryOptions() {}

    public static MemoryBuildOptions defaultOptions() {
        return MemoryBuildOptions.defaults();
    }

    public static MemoryBuildOptions insightTreeOptions() {
        return buildInsightPreset();
    }

    public static MemoryBuildOptions agentScopeOptions() {
        return buildInsightPreset();
    }

    public static MemoryBuildOptions graphOptions() {
        var defaults = MemoryBuildOptions.defaults();
        var extraction = defaults.extraction();
        var item = extraction.item();
        var retrieval = defaults.retrieval();
        var simple = retrieval.simple();

        return MemoryBuildOptions.builder()
                .extraction(
                        new ExtractionOptions(
                                extraction.common(),
                                extraction.rawdata(),
                                new ItemExtractionOptions(
                                        item.foresightEnabled(),
                                        item.promptBudget(),
                                        ItemGraphOptions.defaults().withEnabled(true)),
                                new InsightExtractionOptions(
                                        false,
                                        extraction.insight().build(),
                                        extraction.insight().graphAssist())))
                .retrieval(
                        new RetrievalOptions(
                                retrieval.common(),
                                new SimpleRetrievalOptions(
                                        simple.timeout(),
                                        simple.insightTopK(),
                                        simple.itemTopK(),
                                        simple.rawDataTopK(),
                                        simple.keywordSearchEnabled(),
                                        SimpleRetrievalGraphOptions.defaults().withEnabled(true),
                                        simple.memoryThreadAssist()),
                                retrieval.deep(),
                                retrieval.advanced()))
                .memoryThread(defaults.memoryThread())
                .build();
    }

    private static MemoryBuildOptions buildInsightPreset() {
        InsightBuildConfig defaults = MemoryBuildOptions.defaults().extraction().insight().build();
        return MemoryBuildOptions.builder()
                .extraction(
                        new ExtractionOptions(
                                ExtractionCommonOptions.defaults(),
                                RawDataExtractionOptions.defaults(),
                                ItemExtractionOptions.defaults(),
                                new InsightExtractionOptions(
                                        true,
                                        new InsightBuildConfig(
                                                2,
                                                2,
                                                defaults.concurrency(),
                                                defaults.maxRetries()))))
                .retrieval(RetrievalOptions.defaults())
                .build();
    }
}
