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

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.builder.MemoryBuildOptions;
import org.junit.jupiter.api.Test;

class ExampleMemoryOptionsTest {

    @Test
    void defaultOptionsMatchLibraryDefaults() {
        assertThat(ExampleMemoryOptions.defaultOptions())
                .usingRecursiveComparison()
                .isEqualTo(MemoryBuildOptions.defaults());
    }

    @Test
    void insightTreeOptionsUseLowerInsightThresholds() {
        var options = ExampleMemoryOptions.insightTreeOptions();

        assertThat(options.extraction().insight().enabled()).isTrue();
        assertThat(options.extraction().insight().build().groupingThreshold()).isEqualTo(2);
        assertThat(options.extraction().insight().build().buildThreshold()).isEqualTo(2);
    }

    @Test
    void agentScopeOptionsMirrorInsightPresetForNow() {
        var options = ExampleMemoryOptions.agentScopeOptions();

        assertThat(options.extraction().insight().enabled()).isTrue();
        assertThat(options.extraction().insight().build().groupingThreshold()).isEqualTo(2);
        assertThat(options.extraction().insight().build().buildThreshold()).isEqualTo(2);
    }
}
