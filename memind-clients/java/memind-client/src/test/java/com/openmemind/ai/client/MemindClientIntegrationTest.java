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
package com.openmemind.ai.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.openmemind.ai.client.exception.MemindApiException;
import com.openmemind.ai.client.model.common.ConversationContent;
import com.openmemind.ai.client.model.common.Message;
import com.openmemind.ai.client.model.common.Strategy;
import com.openmemind.ai.client.model.request.ExtractMemoryRequest;
import com.openmemind.ai.client.model.request.RetrieveMemoryRequest;
import com.openmemind.ai.client.model.response.ExtractMemoryResponse;
import com.openmemind.ai.client.model.response.HealthResponse;
import com.openmemind.ai.client.model.response.RetrieveMemoryResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MemindClientIntegrationTest {

    @Test
    void clientCanCallRealMemindServerHealthAndErrorEnvelope() {
        assumeIntegrationEnabled();

        String baseUrl = envOrDefault("MEMIND_BASE_URL", "http://localhost:8366");
        String apiToken = System.getenv("MEMIND_API_TOKEN");
        String suffix = UUID.randomUUID().toString();

        try (MemindClient client = client(baseUrl, apiToken)) {
            HealthResponse health = client.health();
            assertThat(health.status()).isEqualToIgnoringCase("UP");

            assertThatThrownBy(
                            () ->
                                    client.retrieve(
                                            RetrieveMemoryRequest.builder()
                                                    .userId("java-it-user-" + suffix)
                                                    .agentId("java-it-agent-" + suffix)
                                                    .query("")
                                                    .strategy(Strategy.SIMPLE)
                                                    .build()))
                    .isInstanceOf(MemindApiException.class)
                    .satisfies(
                            ex -> {
                                MemindApiException apiException = (MemindApiException) ex;
                                assertThat(apiException.getHttpStatus()).isEqualTo(400);
                                assertThat(apiException.getErrorCode()).isEqualTo("validation_failed");
                                assertThat(apiException.getRequestId()).isNotBlank();
                            });
        }
    }

    @Test
    void clientCanRunRealMemindServerMemoryFlow() {
        assumeIntegrationEnabled();
        assumeTrue(
                Boolean.parseBoolean(System.getenv("MEMIND_FULL_MEMORY_FLOW_TEST")),
                "Set MEMIND_FULL_MEMORY_FLOW_TEST=true to run extraction/retrieval integration tests");

        String baseUrl = envOrDefault("MEMIND_BASE_URL", "http://localhost:8366");
        String apiToken = System.getenv("MEMIND_API_TOKEN");
        String suffix = UUID.randomUUID().toString();
        String userId = "java-it-user-" + suffix;
        String agentId = "java-it-agent-" + suffix;
        String memoryText = "Java integration memory " + suffix;

        try (MemindClient client = client(baseUrl, apiToken)) {
            HealthResponse health = client.health();
            assertThat(health.status()).isEqualToIgnoringCase("UP");

            ExtractMemoryResponse extract =
                    client.extract(
                            ExtractMemoryRequest.builder()
                                    .userId(userId)
                                    .agentId(agentId)
                                    .rawContent(
                                            ConversationContent.of(
                                                    List.of(Message.user(memoryText))))
                                    .sourceClient("memind-java-integration-test")
                                    .build());
            assertThat(extract.status()).isIn("SUCCESS", "PARTIAL_SUCCESS");
            assertThat(extract.rawDataIds()).isNotNull();

            RetrieveMemoryResponse retrieved =
                    client.retrieve(
                            RetrieveMemoryRequest.builder()
                                    .userId(userId)
                                    .agentId(agentId)
                                    .query(memoryText)
                                    .strategy(Strategy.SIMPLE)
                                    .trace(true)
                                    .build());
            assertThat(retrieved.items()).isNotNull();
        }
    }

    private void assumeIntegrationEnabled() {
        assumeTrue(
                Boolean.parseBoolean(System.getenv("MEMIND_INTEGRATION_TEST")),
                "Set MEMIND_INTEGRATION_TEST=true to run real-server integration tests");
    }

    private MemindClient client(String baseUrl, String apiToken) {
        return MemindClient.builder()
                .baseUrl(baseUrl)
                .apiToken(apiToken)
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(60))
                .build();
    }

    private String envOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
