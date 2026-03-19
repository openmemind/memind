package com.openmemind.ai.memory.core.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.retrieval.sufficiency.SufficiencyResult;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SufficiencyResult Unit Test")
class SufficiencyResultTest {

    @Test
    @DisplayName("fallbackInsufficient should return sufficient=false and list is empty")
    void fallbackInsufficientShouldBeFalse() {
        var result = SufficiencyResult.fallbackInsufficient();
        assertThat(result.sufficient()).isFalse();
        assertThat(result.evidences()).isEmpty();
        assertThat(result.gaps()).isEmpty();
    }

    @Test
    @DisplayName("when sufficient=true evidences is not empty, gaps is empty")
    void sufficientResultShouldHaveEvidences() {
        var result =
                new SufficiencyResult(
                        true,
                        "Found the answer",
                        List.of("Caroline went on May 7"),
                        List.of(),
                        List.of());
        assertThat(result.sufficient()).isTrue();
        assertThat(result.evidences()).containsExactly("Caroline went on May 7");
        assertThat(result.gaps()).isEmpty();
    }

    @Test
    @DisplayName("when sufficient=false gaps is not empty, evidences is empty")
    void insufficientResultShouldHaveGaps() {
        var result =
                new SufficiencyResult(
                        false,
                        "Missing date",
                        List.of(),
                        List.of("specific date of the event"),
                        List.of());
        assertThat(result.sufficient()).isFalse();
        assertThat(result.gaps()).containsExactly("specific date of the event");
        assertThat(result.evidences()).isEmpty();
    }
}
