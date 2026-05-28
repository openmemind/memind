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

import com.openmemind.ai.memory.plugin.rawdata.agent.config.AgentPrivacyOptions;
import com.openmemind.ai.memory.plugin.rawdata.agent.model.AgentEvent;
import com.openmemind.ai.memory.plugin.rawdata.agent.model.AgentEventKind;
import com.openmemind.ai.memory.plugin.rawdata.agent.model.AgentEventStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentEventRedactorTest {

    @Test
    void shouldTruncateLongCommandOutputAndMarkEventAsRedacted() {
        AgentEvent event = commandWithOutput("npm test", "ok ".repeat(5000));

        AgentEvent redacted = new AgentEventRedactor().redact(event);

        assertThat(redacted.output()).hasSizeLessThanOrEqualTo(4000);
        assertThat(redacted.metadata()).containsEntry("redacted", true);
        assertThat(redacted.metadata()).containsEntry("truncated", true);
    }

    @Test
    void shouldRedactSecretsFromInputOutputAndMetadata() {
        AgentEvent event =
                new AgentEvent(
                        "e1",
                        1,
                        AgentEventKind.COMMAND,
                        Instant.parse("2026-05-24T10:00:00Z"),
                        "Run deployment",
                        "Bash",
                        "Authorization: Bearer abc.def.ghi",
                        "DATABASE_URL=postgres://u:p@example/db",
                        AgentEventStatus.SUCCESS,
                        12L,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "deploy",
                        0,
                        Map.of("existing", "value"));

        AgentEvent redacted = new AgentEventRedactor().redact(event);

        assertThat(redacted.input()).contains("[REDACTED:bearer_token]");
        assertThat(redacted.output()).contains("[REDACTED:database_url]");
        assertThat(redacted.metadata())
                .containsEntry("existing", "value")
                .containsEntry("redacted", true);
        assertThat(redacted.metadata().get("redactionKinds"))
                .asList()
                .containsExactly("bearer_token", "database_url");
    }

    @Test
    void shouldDropFileContentForSensitivePathsByDefault() {
        AgentEvent event =
                new AgentEvent(
                        "e1",
                        1,
                        AgentEventKind.FILE_READ,
                        Instant.parse("2026-05-24T10:00:00Z"),
                        null,
                        "Read",
                        "PRIVATE=secret",
                        "secret file body",
                        AgentEventStatus.SUCCESS,
                        12L,
                        null,
                        null,
                        null,
                        "/repo/.env",
                        "read",
                        null,
                        null,
                        Map.of());

        AgentEvent redacted = new AgentEventRedactor().redact(event);

        assertThat(redacted.input()).isEqualTo("[REDACTED:file_content]");
        assertThat(redacted.output()).isEqualTo("[REDACTED:file_content]");
        assertThat(redacted.metadata().get("redactionKinds"))
                .asList()
                .containsExactly("file_content");
    }

    @Test
    void shouldKeepFileContentWhenCaptureIsAllowedAndPathIsAllowed() {
        AgentPrivacyOptions options =
                new AgentPrivacyOptions(
                        true, 2000, 4000, true, List.of(".env"), List.of("fixtures/.env"));
        AgentEvent event =
                new AgentEvent(
                        "e1",
                        1,
                        AgentEventKind.FILE_READ,
                        Instant.parse("2026-05-24T10:00:00Z"),
                        null,
                        "Read",
                        "fixture",
                        "DATABASE_URL=postgres://u:p@example/db",
                        AgentEventStatus.SUCCESS,
                        12L,
                        null,
                        null,
                        null,
                        "/repo/fixtures/.env",
                        "read",
                        null,
                        null,
                        Map.of());

        AgentEvent redacted = new AgentEventRedactor(options).redact(event);

        assertThat(redacted.input()).isEqualTo("fixture");
        assertThat(redacted.output()).contains("[REDACTED:database_url]");
        assertThat(redacted.metadata().get("redactionKinds"))
                .asList()
                .containsExactly("database_url");
    }

    @Test
    void shouldPreserveToolTelemetryWhenRedactingText() {
        AgentEvent event =
                new AgentEvent(
                        "e1",
                        1,
                        AgentEventKind.COMMAND,
                        Instant.parse("2026-05-24T10:00:00Z"),
                        null,
                        "Bash",
                        null,
                        "Bearer secret-token-value",
                        AgentEventStatus.SUCCESS,
                        1234L,
                        11,
                        22,
                        "sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                        null,
                        null,
                        "npm test payment",
                        0,
                        Map.of());

        AgentEvent redacted = new AgentEventRedactor().redact(event);

        assertThat(redacted.durationMs()).isEqualTo(1234L);
        assertThat(redacted.inputTokens()).isEqualTo(11);
        assertThat(redacted.outputTokens()).isEqualTo(22);
        assertThat(redacted.contentHash()).isEqualTo(event.contentHash());
        assertThat(redacted.output()).contains("[REDACTED:bearer_token]");
    }

    private static AgentEvent commandWithOutput(String command, String output) {
        return new AgentEvent(
                "e1",
                1,
                AgentEventKind.COMMAND,
                Instant.parse("2026-05-24T10:00:00Z"),
                null,
                "Bash",
                null,
                output,
                AgentEventStatus.SUCCESS,
                42L,
                null,
                null,
                null,
                null,
                null,
                command,
                0,
                Map.of());
    }
}
