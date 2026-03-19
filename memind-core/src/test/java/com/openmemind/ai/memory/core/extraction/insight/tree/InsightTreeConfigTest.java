package com.openmemind.ai.memory.core.extraction.insight.tree;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("InsightTreeConfig")
class InsightTreeConfigTest {

    @Nested
    @DisplayName("defaults")
    class Defaults {

        @Test
        @DisplayName("The default configuration should have reasonable default values")
        void shouldHaveReasonableDefaults() {
            var config = InsightTreeConfig.defaults();
            assertThat(config.branchBubbleThreshold()).isEqualTo(3);
            assertThat(config.rootBubbleThreshold()).isEqualTo(2);
            assertThat(config.minBranchesForRoot()).isEqualTo(2);
            assertThat(config.rootTargetTokens()).isEqualTo(800);
        }
    }
}
