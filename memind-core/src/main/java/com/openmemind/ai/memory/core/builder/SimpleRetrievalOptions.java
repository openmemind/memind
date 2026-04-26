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

public record SimpleRetrievalOptions(
        Duration timeout,
        int insightTopK,
        int itemTopK,
        int rawDataTopK,
        boolean keywordSearchEnabled,
        SimpleTemporalRetrievalOptions temporalRetrieval,
        SimpleRetrievalGraphOptions graphAssist,
        SimpleMemoryThreadAssistOptions memoryThreadAssist) {

    public SimpleRetrievalOptions {
        timeout = Objects.requireNonNull(timeout, "timeout");
        temporalRetrieval =
                temporalRetrieval != null
                        ? temporalRetrieval
                        : SimpleTemporalRetrievalOptions.defaults();
        graphAssist = graphAssist != null ? graphAssist : SimpleRetrievalGraphOptions.defaults();
        memoryThreadAssist =
                memoryThreadAssist != null
                        ? memoryThreadAssist
                        : SimpleMemoryThreadAssistOptions.defaults();
    }

    public SimpleRetrievalOptions(
            Duration timeout,
            int insightTopK,
            int itemTopK,
            int rawDataTopK,
            boolean keywordSearchEnabled) {
        this(
                timeout,
                insightTopK,
                itemTopK,
                rawDataTopK,
                keywordSearchEnabled,
                SimpleTemporalRetrievalOptions.defaults(),
                SimpleRetrievalGraphOptions.defaults(),
                SimpleMemoryThreadAssistOptions.defaults());
    }

    public SimpleRetrievalOptions(
            Duration timeout,
            int insightTopK,
            int itemTopK,
            int rawDataTopK,
            boolean keywordSearchEnabled,
            SimpleRetrievalGraphOptions graphAssist) {
        this(
                timeout,
                insightTopK,
                itemTopK,
                rawDataTopK,
                keywordSearchEnabled,
                SimpleTemporalRetrievalOptions.defaults(),
                graphAssist,
                SimpleMemoryThreadAssistOptions.defaults());
    }

    public SimpleRetrievalOptions(
            Duration timeout,
            int insightTopK,
            int itemTopK,
            int rawDataTopK,
            boolean keywordSearchEnabled,
            SimpleRetrievalGraphOptions graphAssist,
            SimpleMemoryThreadAssistOptions memoryThreadAssist) {
        this(
                timeout,
                insightTopK,
                itemTopK,
                rawDataTopK,
                keywordSearchEnabled,
                SimpleTemporalRetrievalOptions.defaults(),
                graphAssist,
                memoryThreadAssist);
    }

    public static SimpleRetrievalOptions defaults() {
        return new SimpleRetrievalOptions(Duration.ofSeconds(10), 5, 15, 5, true);
    }

    public SimpleRetrievalOptions withMemoryThreadAssist(
            SimpleMemoryThreadAssistOptions memoryThreadAssist) {
        return new SimpleRetrievalOptions(
                timeout,
                insightTopK,
                itemTopK,
                rawDataTopK,
                keywordSearchEnabled,
                temporalRetrieval,
                graphAssist,
                memoryThreadAssist);
    }

    public SimpleRetrievalOptions withTemporalRetrieval(
            SimpleTemporalRetrievalOptions temporalRetrieval) {
        return new SimpleRetrievalOptions(
                timeout,
                insightTopK,
                itemTopK,
                rawDataTopK,
                keywordSearchEnabled,
                temporalRetrieval,
                graphAssist,
                memoryThreadAssist);
    }
}
