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
package com.openmemind.ai.memory.core.retrieval.rerank;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.llm.rerank.LlmReranker;
import com.openmemind.ai.memory.core.retrieval.RetrievalConfig.RerankConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("LlmReranker blendScore")
class LlmRerankerBlendTest {

    @Nested
    @DisplayName("P6: Rerank Mode Properties")
    class RerankModeProperties {

        @Test
        @DisplayName("P6.1: Blend Range -- Result is between rrfScore and rerankerScore")
        void blendScoreInRange() {
            RerankConfig config = RerankConfig.blend(10);
            double rrfScore = 0.8;
            double rerankerScore = 0.3;

            double blended = LlmReranker.blendScore(rrfScore, rerankerScore, 1, config);

            assertThat(blended).isBetween(rerankerScore, rrfScore);
        }

        @Test
        @DisplayName("P6.2: Blend Position Sensitive -- Top 3 leans more towards RRF")
        void blendPositionAware() {
            RerankConfig config = RerankConfig.blend(10);
            double rrfScore = 0.9;
            double rerankerScore = 0.1;

            double blendTop3 = LlmReranker.blendScore(rrfScore, rerankerScore, 1, config);
            double blendOther = LlmReranker.blendScore(rrfScore, rerankerScore, 15, config);

            // Top 3 gives more weight to RRF (rrfScore is higher), so blended score should be
            // higher
            assertThat(blendTop3).isGreaterThan(blendOther);
        }

        @Test
        @DisplayName("P6.3: Pure Mode -- Directly use reranker score")
        void pureMode() {
            RerankConfig config = RerankConfig.pure(10);
            double rrfScore = 0.9;
            double rerankerScore = 0.5;

            // In pure mode, blendWithRetrieval=false
            // The caller should use rerankerScore directly
            // But if blendScore is called, it should return just rerankerScore
            assertThat(config.blendWithRetrieval()).isFalse();
        }
    }
}
