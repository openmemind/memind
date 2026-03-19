package com.openmemind.ai.memory.core.extraction;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ExtractionConfig")
class ExtractionConfigTest {

    @Test
    @DisplayName("defaults should enable insight and use USER scope")
    void defaultsShouldHaveCorrectSettings() {
        var config = ExtractionConfig.defaults();
        assertThat(config.enableInsight()).isTrue();
        assertThat(config.scope()).isEqualTo(MemoryScope.USER);
    }

    @Test
    @DisplayName("agentOnly should use AGENT scope")
    void agentOnlyShouldUseAgentScope() {
        var config = ExtractionConfig.agentOnly();
        assertThat(config.scope()).isEqualTo(MemoryScope.AGENT);
    }
}
