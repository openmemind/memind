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
    @DisplayName("of factory method should return unfiltered request")
    void ofShouldHaveNoFilters() {
        var req = RetrievalRequest.of(MID, "query", RetrievalConfig.Strategy.DEEP);
        assertThat(req.scope()).isNull();
        assertThat(req.categories()).isNull();
    }

    @Test
    @DisplayName("userMemory should set USER scope")
    void userMemoryShouldSetUserScope() {
        var req = RetrievalRequest.userMemory(MID, "query", RetrievalConfig.Strategy.DEEP);
        assertThat(req.scope()).isEqualTo(MemoryScope.USER);
    }

    @Test
    @DisplayName("agentMemory should set AGENT scope")
    void agentMemoryShouldSetAgentScope() {
        var req = RetrievalRequest.agentMemory(MID, "query", RetrievalConfig.Strategy.DEEP);
        assertThat(req.scope()).isEqualTo(MemoryScope.AGENT);
    }

    @Test
    @DisplayName("byCategories should set specified categories")
    void byCategoriesShouldSetCategories() {
        var req =
                RetrievalRequest.byCategories(
                        MID, "query", Set.of(MemoryCategory.TOOL), RetrievalConfig.Strategy.DEEP);
        assertThat(req.categories()).containsExactly(MemoryCategory.TOOL);
    }
}
