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
import com.openmemind.ai.client.model.request.CommitMemoryRequest;
import com.openmemind.ai.client.model.request.ExtractMemoryRequest;
import com.openmemind.ai.client.model.request.MetadataFilter;
import com.openmemind.ai.client.model.request.QueryMemoryItemsRequest;
import com.openmemind.ai.client.model.request.QueryMemoryRawDataRequest;
import com.openmemind.ai.client.model.request.RetrieveMemoryRequest;
import com.openmemind.ai.client.model.response.ExtractMemoryResponse;
import com.openmemind.ai.client.model.response.HealthResponse;
import com.openmemind.ai.client.model.response.QueryMemoryItemsResponse;
import com.openmemind.ai.client.model.response.QueryMemoryRawDataResponse;
import com.openmemind.ai.client.model.response.RetrieveMemoryResponse;
import java.time.Instant;
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
                                        {"data":{"status":"UP","service":"memind-server"}}
                                        """)));

        try (MemindClient client = MemindClient.builder().baseUrl(wmInfo.getHttpBaseUrl()).build()) {
            HealthResponse health = client.health();
            assertThat(health.status()).isEqualTo("UP");
        }
    }

    @Test
    void addMessage_sendsCorrectPayload(WireMockRuntimeInfo wmInfo) {
        stubFor(
                post("/open/v1/memory/sync/add-message")
                        .willReturn(
                                okJson(
                                        """
                                        {"data":{"triggered":false}}
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
                postRequestedFor(urlEqualTo("/open/v1/memory/sync/add-message"))
                        .withRequestBody(matchingJsonPath("$.userId", equalTo("user-1")))
                        .withRequestBody(matchingJsonPath("$.message.role", equalTo("USER"))));
    }

    @Test
    void extract_sendsRawContent(WireMockRuntimeInfo wmInfo) {
        stubFor(
                post("/open/v1/memory/sync/extract")
                        .willReturn(
                                okJson(
                                        """
                                        {"data":{
                                            "status":"SUCCESS",
                                            "rawDataIds":["rd-1"],
                                            "itemIds":[101],
                                            "insightIds":[],
                                            "insightPending":false,
                                            "durationMillis":12
                                        }}
                                        """)));

        try (MemindClient client = MemindClient.builder().baseUrl(wmInfo.getHttpBaseUrl()).build()) {
            ExtractMemoryResponse response =
                    client.extract(
                            ExtractMemoryRequest.builder()
                                    .userId("user-1")
                                    .agentId("agent-1")
                                    .rawContent(ConversationContent.of(List.of(Message.user("test"))))
                                    .build());

            assertThat(response.status()).isEqualTo("SUCCESS");
            assertThat(response.rawDataIds()).containsExactly("rd-1");
        }

        verify(
                postRequestedFor(urlEqualTo("/open/v1/memory/sync/extract"))
                        .withRequestBody(
                                matchingJsonPath("$.rawContent.type", equalTo("conversation"))));
    }

    @Test
    void commit_usesSyncEndpoint(WireMockRuntimeInfo wmInfo) {
        stubFor(
                post("/open/v1/memory/sync/commit")
                        .willReturn(
                                okJson(
                                        """
                                        {"data":{
                                            "status":"SUCCESS",
                                            "rawDataIds":[],
                                            "itemIds":[],
                                            "insightIds":[],
                                            "insightPending":false
                                        }}
                                        """)));

        try (MemindClient client = MemindClient.builder().baseUrl(wmInfo.getHttpBaseUrl()).build()) {
            client.commit(
                    CommitMemoryRequest.builder().userId("user-1").agentId("agent-1").build());
        }

        verify(postRequestedFor(urlEqualTo("/open/v1/memory/sync/commit")));
    }

    @Test
    void extract_partialSuccessIsReturnedToCaller(WireMockRuntimeInfo wmInfo) {
        stubFor(
                post("/open/v1/memory/sync/extract")
                        .willReturn(
                                okJson(
                                        """
                                        {"data":{
                                            "status":"PARTIAL_SUCCESS",
                                            "rawDataIds":["rd-1"],
                                            "itemIds":[],
                                            "insightIds":[],
                                            "insightPending":false,
                                            "errorMessage":"insight failed"
                                        }}
                                        """)));

        try (MemindClient client = MemindClient.builder().baseUrl(wmInfo.getHttpBaseUrl()).build()) {
            ExtractMemoryResponse response =
                    client.extract(
                            ExtractMemoryRequest.builder()
                                    .userId("u")
                                    .agentId("a")
                                    .rawContent(ConversationContent.of(List.of(Message.user("test"))))
                                    .build());

            assertThat(response.status()).isEqualTo("PARTIAL_SUCCESS");
            assertThat(response.errorMessage()).isEqualTo("insight failed");
        }
    }

    @Test
    void extract_failureEnvelopeThrowsApiException(WireMockRuntimeInfo wmInfo) {
        stubFor(
                post("/open/v1/memory/sync/extract")
                        .willReturn(
                                aResponse()
                                        .withStatus(500)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                """
                                                {"error":{"code":"extraction_failed","message":"extract failed"}}
                                                """)));

        try (MemindClient client = MemindClient.builder().baseUrl(wmInfo.getHttpBaseUrl()).build()) {
            assertThatThrownBy(
                            () ->
                                    client.extract(
                                            ExtractMemoryRequest.builder()
                                                    .userId("u")
                                                    .agentId("a")
                                                    .rawContent(
                                                            ConversationContent.of(
                                                                    List.of(Message.user("test"))))
                                                    .build()))
                    .isInstanceOf(MemindApiException.class)
                    .satisfies(
                            ex -> {
                                var apiEx = (MemindApiException) ex;
                                assertThat(apiEx.getHttpStatus()).isEqualTo(500);
                                assertThat(apiEx.getErrorCode()).isEqualTo("extraction_failed");
                            });
        }
    }

    @Test
    void extractAsync_returnsExtractionResponse(WireMockRuntimeInfo wmInfo) {
        stubFor(
                post("/open/v1/memory/sync/extract")
                        .willReturn(
                                okJson(
                                        """
                                        {"data":{
                                            "status":"SUCCESS",
                                            "rawDataIds":["rd-async"],
                                            "itemIds":[],
                                            "insightIds":[],
                                            "insightPending":false
                                        }}
                                        """)));

        try (MemindClient client = MemindClient.builder().baseUrl(wmInfo.getHttpBaseUrl()).build()) {
            ExtractMemoryResponse response =
                    client.extractAsync(
                            ExtractMemoryRequest.builder()
                                    .userId("user-1")
                                    .agentId("agent-1")
                                    .rawContent(ConversationContent.of(List.of(Message.user("test"))))
                                    .build())
                            .join();

            assertThat(response.rawDataIds()).containsExactly("rd-async");
        }

        verify(postRequestedFor(urlEqualTo("/open/v1/memory/sync/extract")));
    }

    @Test
    void retrieve_returnsMemories(WireMockRuntimeInfo wmInfo) {
        stubFor(
                post("/open/v1/memory/retrieve")
                        .willReturn(
                                okJson(
                                        """
                                        {"data":{
                                            "status":"success","items":[{"id":"1","text":"memory text","vectorScore":0.9,"finalScore":0.85}],
                                            "insights":[],"rawData":[{"rawDataId":"rd-1","type":"agent_timeline","sourceClient":"claude-code","metadata":{"sessionId":"s1"}}],"evidences":[],"strategy":"SIMPLE","query":"test"
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
                                    .scope("ALL")
                                    .categories(List.of("playbook"))
                                    .metadataFilter(
                                            new MetadataFilter(
                                                    List.of(
                                                            new MetadataFilter.Condition(
                                                                    "project", "eq", "memind")),
                                                    List.of(),
                                                    List.of()))
                                    .build());

            assertThat(response.status()).isEqualTo("success");
            assertThat(response.items()).hasSize(1);
            assertThat(response.items().get(0).text()).isEqualTo("memory text");
            assertThat(response.rawData().get(0).type()).isEqualTo("agent_timeline");
            assertThat(response.rawData().get(0).sourceClient()).isEqualTo("claude-code");
        }

        verify(
                postRequestedFor(urlEqualTo("/open/v1/memory/retrieve"))
                        .withRequestBody(matchingJsonPath("$.scope", equalTo("ALL")))
                        .withRequestBody(matchingJsonPath("$.categories[0]", equalTo("playbook")))
                        .withRequestBody(
                                matchingJsonPath(
                                        "$.metadataFilter.all[0].path", equalTo("project"))));
    }

    @Test
    void queryItems_usesStructuredEndpoint(WireMockRuntimeInfo wmInfo) {
        stubFor(
                post("/open/v1/memory/items/query")
                        .willReturn(
                                okJson(
                                        """
                                        {"data":{"items":[{"id":"101","text":"Run targeted tests.","rawDataType":"agent_timeline","sourceClient":"claude-code","metadata":{"project":"memind"}}],"nextCursor":"101"}}
                                        """)));

        try (MemindClient client = MemindClient.builder().baseUrl(wmInfo.getHttpBaseUrl()).build()) {
            QueryMemoryItemsResponse response =
                    client.queryItems(
                            QueryMemoryItemsRequest.builder()
                                    .userId("user-1")
                                    .agentId("agent-1")
                                    .categories(List.of("playbook"))
                                    .sourceClients(List.of("claude-code"))
                                    .rawDataTypes(List.of("agent_timeline"))
                                    .limit(10)
                                    .build());

            assertThat(response.items()).hasSize(1);
            assertThat(response.items().get(0).rawDataType()).isEqualTo("agent_timeline");
            assertThat(response.nextCursor()).isEqualTo("101");
        }

        verify(
                postRequestedFor(urlEqualTo("/open/v1/memory/items/query"))
                        .withRequestBody(matchingJsonPath("$.sourceClients[0]", equalTo("claude-code")))
                        .withRequestBody(
                                matchingJsonPath("$.rawDataTypes[0]", equalTo("agent_timeline"))));
    }

    @Test
    void queryRawData_usesStructuredEndpoint(WireMockRuntimeInfo wmInfo) {
        stubFor(
                post("/open/v1/memory/raw-data/query")
                        .willReturn(
                                okJson(
                                        """
                                        {"data":{"rawData":[{"id":"rd-1","type":"agent_timeline","sourceClient":"codex","caption":"Fixed retry test.","metadata":{"sessionId":"s1"},"segment":{"events":[]}}],"nextCursor":null}}
                                        """)));

        try (MemindClient client = MemindClient.builder().baseUrl(wmInfo.getHttpBaseUrl()).build()) {
            QueryMemoryRawDataResponse response =
                    client.queryRawData(
                            QueryMemoryRawDataRequest.builder()
                                    .userId("user-1")
                                    .agentId("agent-1")
                                    .types(List.of("agent_timeline"))
                                    .sourceClients(List.of("codex"))
                                    .include(new QueryMemoryRawDataRequest.IncludeOptions(true, true))
                                    .build());

            assertThat(response.rawData()).hasSize(1);
            assertThat(response.rawData().get(0).id()).isEqualTo("rd-1");
            assertThat(response.rawData().get(0).segment()).containsKey("events");
        }

        verify(
                postRequestedFor(urlEqualTo("/open/v1/memory/raw-data/query"))
                        .withRequestBody(matchingJsonPath("$.include.segment", equalTo("true"))));
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
                                                {"error":{"code":"bad_request","message":"query is required"}}
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
