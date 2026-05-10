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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.openmemind.ai.client.exception.MemindApiException;
import com.openmemind.ai.client.model.common.ConversationContent;
import com.openmemind.ai.client.model.common.Message;
import com.openmemind.ai.client.model.common.Strategy;
import com.openmemind.ai.client.model.request.AddMessageRequest;
import com.openmemind.ai.client.model.request.ExtractMemoryRequest;
import com.openmemind.ai.client.model.request.RetrieveMemoryRequest;
import com.openmemind.ai.client.model.response.HealthResponse;
import com.openmemind.ai.client.model.response.RetrieveMemoryResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

@WireMockTest
class MemindClientTest {

    @Test
    void health_returnsResponse(WireMockRuntimeInfo wmInfo) {
        stubFor(
                get("/open/v1/health")
                        .willReturn(
                                okJson(
                                        """
                                        {"code":"success","data":{"status":"UP","service":"memind-server"}}
                                        """)));

        try (MemindClient client = MemindClient.builder().baseUrl(wmInfo.getHttpBaseUrl()).build()) {
            HealthResponse health = client.health();
            assertThat(health.status()).isEqualTo("UP");
        }
    }

    @Test
    void addMessage_sendsCorrectPayload(WireMockRuntimeInfo wmInfo) {
        stubFor(
                post("/open/v1/memory/add-message")
                        .willReturn(
                                okJson(
                                        """
                                        {"code":"200"}
                                        """)));

        try (MemindClient client = MemindClient.builder().baseUrl(wmInfo.getHttpBaseUrl()).build()) {
            client.addMessage(
                    AddMessageRequest.builder()
                            .userId("user-1")
                            .agentId("agent-1")
                            .message(Message.user("hello"))
                            .build());
        }

        verify(
                postRequestedFor(urlEqualTo("/open/v1/memory/add-message"))
                        .withRequestBody(matchingJsonPath("$.userId", equalTo("user-1")))
                        .withRequestBody(matchingJsonPath("$.message.role", equalTo("USER"))));
    }

    @Test
    void extract_sendsRawContent(WireMockRuntimeInfo wmInfo) {
        stubFor(
                post("/open/v1/memory/extract")
                        .willReturn(
                                okJson(
                                        """
                                        {"code":"200"}
                                        """)));

        try (MemindClient client = MemindClient.builder().baseUrl(wmInfo.getHttpBaseUrl()).build()) {
            client.extract(
                    ExtractMemoryRequest.builder()
                            .userId("user-1")
                            .agentId("agent-1")
                            .rawContent(ConversationContent.of(List.of(Message.user("test"))))
                            .build());
        }

        verify(
                postRequestedFor(urlEqualTo("/open/v1/memory/extract"))
                        .withRequestBody(
                                matchingJsonPath("$.rawContent.type", equalTo("conversation"))));
    }

    @Test
    void retrieve_returnsMemories(WireMockRuntimeInfo wmInfo) {
        stubFor(
                post("/open/v1/memory/retrieve")
                        .willReturn(
                                okJson(
                                        """
                                        {"code":"success","data":{
                                            "status":"success","items":[{"id":"1","text":"memory text","vectorScore":0.9,"finalScore":0.85}],
                                            "insights":[],"rawData":[],"evidences":[],"strategy":"SIMPLE","query":"test"
                                        }}
                                        """)));

        try (MemindClient client = MemindClient.builder().baseUrl(wmInfo.getHttpBaseUrl()).build()) {
            RetrieveMemoryResponse response =
                    client.retrieve(
                            RetrieveMemoryRequest.builder()
                                    .userId("user-1")
                                    .agentId("agent-1")
                                    .query("test")
                                    .strategy(Strategy.SIMPLE)
                                    .build());

            assertThat(response.status()).isEqualTo("success");
            assertThat(response.items()).hasSize(1);
            assertThat(response.items().get(0).text()).isEqualTo("memory text");
        }
    }

    @Test
    void builder_missingBaseUrl_throws() {
        assertThatThrownBy(() -> MemindClient.builder().build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("baseUrl");
    }

    @Test
    void syncMethod_apiError_throwsUnwrappedException(WireMockRuntimeInfo wmInfo) {
        stubFor(
                post("/open/v1/memory/retrieve")
                        .willReturn(
                                aResponse()
                                        .withStatus(400)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                """
                                                {"code":"bad_request","message":"query is required","traceId":"t1"}
                                                """)));

        try (MemindClient client = MemindClient.builder().baseUrl(wmInfo.getHttpBaseUrl()).build()) {
            assertThatThrownBy(
                            () ->
                                    client.retrieve(
                                            RetrieveMemoryRequest.builder()
                                                    .userId("u1")
                                                    .agentId("a1")
                                                    .query("q")
                                                    .strategy(Strategy.SIMPLE)
                                                    .build()))
                    .isInstanceOf(MemindApiException.class)
                    .satisfies(
                            ex -> {
                                var apiEx = (MemindApiException) ex;
                                assertThat(apiEx.getHttpStatus()).isEqualTo(400);
                                assertThat(apiEx.getErrorCode()).isEqualTo("bad_request");
                            });
        }
    }

    @Test
    void close_thenCall_throwsIllegalState(WireMockRuntimeInfo wmInfo) {
        MemindClient client = MemindClient.builder().baseUrl(wmInfo.getHttpBaseUrl()).build();
        client.close();

        assertThatThrownBy(client::health).isInstanceOf(IllegalStateException.class);
    }
}
