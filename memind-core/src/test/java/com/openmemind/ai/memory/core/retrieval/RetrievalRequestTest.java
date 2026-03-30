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
package com.openmemind.ai.memory.core.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RetrievalRequest")
class RetrievalRequestTest {

    private static final MemoryId MID = () -> "test";

    @Test
    @DisplayName("strategy factories materialize matching configs")
    void strategyFactoriesMaterializeMatchingConfigs() {
        var simple = RetrievalRequest.of(MID, "query", RetrievalConfig.Strategy.SIMPLE);
        var deep = RetrievalRequest.of(MID, "query", RetrievalConfig.Strategy.DEEP);

        assertThat(simple.config().strategyName()).isEqualTo("simple");
        assertThat(deep.config().strategyName()).isEqualTo("deep_retrieval");
        assertThat(simple.conversationHistory()).isEmpty();
        assertThat(deep.metadata()).isEmpty();
    }

    @Test
    @DisplayName("userMemory sets USER scope")
    void userMemorySetsUserScope() {
        var req = RetrievalRequest.userMemory(MID, "query", RetrievalConfig.Strategy.DEEP);
        assertThat(req.scope()).isEqualTo(MemoryScope.USER);
        assertThat(req.categories()).isNull();
    }

    @Test
    @DisplayName("agentMemory sets AGENT scope")
    void agentMemorySetsAgentScope() {
        var req = RetrievalRequest.agentMemory(MID, "query", RetrievalConfig.Strategy.DEEP);
        assertThat(req.scope()).isEqualTo(MemoryScope.AGENT);
        assertThat(req.categories()).isNull();
    }

    @Test
    @DisplayName("byCategories preserves explicit filters and empty metadata")
    void byCategoriesPreservesExplicitFiltersAndEmptyMetadata() {
        var request =
                RetrievalRequest.byCategories(
                        MID, "query", Set.of(MemoryCategory.TOOL), RetrievalConfig.Strategy.DEEP);

        assertThat(request.categories()).containsExactly(MemoryCategory.TOOL);
        assertThat(request.scope()).isNull();
        assertThat(request.metadata()).isEmpty();
    }
}
