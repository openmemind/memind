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
package com.openmemind.ai.memory.core.extraction.insight.generator;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.utils.JsonUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("InsightPointOpsResponse")
class InsightPointOpsResponseTest {

    @Test
    @DisplayName("deserialization should distinguish explicit empty operations from missing field")
    void deserializationDistinguishesExplicitEmptyOperationsFromMissingOperationsField()
            throws Exception {
        var mapper = JsonUtils.newMapper();

        var explicitNoop = mapper.readValue("{\"operations\":[]}", InsightPointOpsResponse.class);
        var missingField = mapper.readValue("{}", InsightPointOpsResponse.class);
        var explicitNull = mapper.readValue("{\"operations\":null}", InsightPointOpsResponse.class);

        assertThat(explicitNoop.hasExplicitOperationsArray()).isTrue();
        assertThat(explicitNoop.operations()).isEmpty();
        assertThat(missingField.hasExplicitOperationsArray()).isFalse();
        assertThat(explicitNull.hasExplicitOperationsArray()).isFalse();
    }
}
