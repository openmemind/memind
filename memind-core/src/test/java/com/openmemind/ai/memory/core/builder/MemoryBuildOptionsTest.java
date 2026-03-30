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

import com.openmemind.ai.memory.core.data.enums.MemoryScope;
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
}
