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
package com.openmemind.ai.memory.plugin.rawdata.agent.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class SecretPatternRedactorTest {

    private final SecretPatternRedactor redactor = new SecretPatternRedactor();

    @Test
    void shouldRedactBearerTokensDatabaseUrlsAndPrivateKeys() {
        assertThat(redactor.redact("Authorization: Bearer abc.def.ghi").text())
                .contains("[REDACTED:bearer_token]");
        assertThat(redactor.redact("DATABASE_URL=postgres://u:p@example/db").text())
                .contains("[REDACTED:database_url]");
        assertThat(redactor.redact("-----BEGIN PRIVATE KEY-----\nabc").text())
                .contains("[REDACTED:private_key]");
    }

    @Test
    void shouldReportAllRedactionKindsInStableOrder() {
        SecretPatternRedactor.RedactionResult result =
                redactor.redact(
                        "Authorization: Bearer abc.def.ghi\n"
                                + "OPENAI_API_KEY=sk-test-token\n"
                                + "AWS_SECRET_ACCESS_KEY=secret-value");

        assertThat(result.redacted()).isTrue();
        assertThat(result.redactionKinds())
                .containsExactly("bearer_token", "api_key", "cloud_credential");
        assertThat(result.text()).doesNotContain("abc.def.ghi", "sk-test-token", "secret-value");
    }

    @Test
    void shouldReturnOriginalTextWhenNoSecretMatches() {
        SecretPatternRedactor.RedactionResult result = redactor.redact("npm test passed");

        assertThat(result.text()).isEqualTo("npm test passed");
        assertThat(result.redacted()).isFalse();
        assertThat(result.redactionKinds()).isEqualTo(List.of());
    }
}
