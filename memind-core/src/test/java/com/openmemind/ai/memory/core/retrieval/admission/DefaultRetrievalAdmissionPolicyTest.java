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
package com.openmemind.ai.memory.core.retrieval.admission;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.retrieval.RetrievalConfig;
import com.openmemind.ai.memory.core.retrieval.RetrievalRequest;
import com.openmemind.ai.memory.core.support.TestMemoryIds;
import org.junit.jupiter.api.Test;

class DefaultRetrievalAdmissionPolicyTest {

    private final DefaultRetrievalAdmissionPolicy policy =
            new DefaultRetrievalAdmissionPolicy(new RetrievalAdmissionOptions(20, 8));

    @Test
    void shouldSkipNullQuery() {
        var result = policy.evaluate(request(null));

        assertThat(result.decision()).isEqualTo(RetrievalAdmissionDecision.SKIP);
        assertThat(result.reason()).isEqualTo(RetrievalAdmissionReason.EMPTY_QUERY);
    }

    @Test
    void shouldSkipBlankQuery() {
        var result = policy.evaluate(request("   \n\t  "));

        assertThat(result.decision()).isEqualTo(RetrievalAdmissionDecision.SKIP);
        assertThat(result.reason()).isEqualTo(RetrievalAdmissionReason.EMPTY_QUERY);
    }

    @Test
    void shouldSkipPurePunctuation() {
        var result = policy.evaluate(request("?!...---"));

        assertThat(result.decision()).isEqualTo(RetrievalAdmissionDecision.SKIP);
        assertThat(result.reason()).isEqualTo(RetrievalAdmissionReason.NO_TEXT_TOKEN);
    }

    @Test
    void shouldSkipPureEmoji() {
        var result = policy.evaluate(request("🔥🔥🔥"));

        assertThat(result.decision()).isEqualTo(RetrievalAdmissionDecision.SKIP);
        assertThat(result.reason()).isEqualTo(RetrievalAdmissionReason.NO_TEXT_TOKEN);
    }

    @Test
    void shouldAdmitShortNaturalQueries() {
        assertThat(policy.evaluate(request("你好")).decision())
                .isEqualTo(RetrievalAdmissionDecision.ADMIT);
        assertThat(policy.evaluate(request("ok")).decision())
                .isEqualTo(RetrievalAdmissionDecision.ADMIT);
        assertThat(policy.evaluate(request("test")).decision())
                .isEqualTo(RetrievalAdmissionDecision.ADMIT);
        assertThat(policy.evaluate(request("111111")).decision())
                .isEqualTo(RetrievalAdmissionDecision.ADMIT);
    }

    @Test
    void shouldRejectInvalidControlCharacters() {
        var result = policy.evaluate(request("hello\u0000world"));

        assertThat(result.decision()).isEqualTo(RetrievalAdmissionDecision.REJECT);
        assertThat(result.reason()).isEqualTo(RetrievalAdmissionReason.INVALID_CONTROL_CHARACTER);
    }

    @Test
    void shouldAllowWhitespaceControlCharacters() {
        var result = policy.evaluate(request("hello\nworld"));

        assertThat(result.decision()).isEqualTo(RetrievalAdmissionDecision.ADMIT);
    }

    @Test
    void shouldDetectTokenLimit() {
        var tokenPolicy =
                new DefaultRetrievalAdmissionPolicy(new RetrievalAdmissionOptions(200, 8));
        var result =
                tokenPolicy.evaluate(
                        request("alpha beta gamma delta epsilon zeta eta theta iota kappa"));

        assertThat(result.decision()).isEqualTo(RetrievalAdmissionDecision.QUERY_TOO_LONG);
        assertThat(result.reason()).isEqualTo(RetrievalAdmissionReason.QUERY_TOO_MANY_TOKENS);
    }

    @Test
    void shouldDetectCodePointCharLimit() {
        var unicodePolicy =
                new DefaultRetrievalAdmissionPolicy(new RetrievalAdmissionOptions(3, 20));
        var result = unicodePolicy.evaluate(request("你好吗啊"));

        assertThat(result.decision()).isEqualTo(RetrievalAdmissionDecision.QUERY_TOO_LONG);
        assertThat(result.reason()).isEqualTo(RetrievalAdmissionReason.QUERY_TOO_MANY_CHARS);
        assertThat(result.charCount()).isEqualTo(4);
    }

    private static RetrievalRequest request(String query) {
        return RetrievalRequest.of(
                TestMemoryIds.userAgent(), query, RetrievalConfig.Strategy.SIMPLE);
    }
}
