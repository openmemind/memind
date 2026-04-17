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

import java.time.Duration;
import java.util.Objects;

public record DeepRetrievalOptions(
        Duration timeout,
        int insightTopK,
        int itemTopK,
        boolean rawDataEnabled,
        int rawDataTopK,
        QueryExpansionOptions queryExpansion,
        SufficiencyOptions sufficiency,
        DeepRetrievalGraphOptions graphAssist) {

    public DeepRetrievalOptions {
        timeout = Objects.requireNonNull(timeout, "timeout");
        queryExpansion = queryExpansion != null ? queryExpansion : QueryExpansionOptions.defaults();
        sufficiency = sufficiency != null ? sufficiency : SufficiencyOptions.defaults();
        graphAssist = graphAssist != null ? graphAssist : DeepRetrievalGraphOptions.defaults();
    }

    public DeepRetrievalOptions(
            Duration timeout,
            int insightTopK,
            int itemTopK,
            boolean rawDataEnabled,
            int rawDataTopK,
            QueryExpansionOptions queryExpansion,
            SufficiencyOptions sufficiency) {
        this(
                timeout,
                insightTopK,
                itemTopK,
                rawDataEnabled,
                rawDataTopK,
                queryExpansion,
                sufficiency,
                DeepRetrievalGraphOptions.defaults());
    }

    public static DeepRetrievalOptions defaults() {
        return new DeepRetrievalOptions(
                Duration.ofSeconds(120),
                5,
                50,
                false,
                0,
                QueryExpansionOptions.defaults(),
                SufficiencyOptions.defaults(),
                DeepRetrievalGraphOptions.defaults());
    }
}
