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
package com.openmemind.ai.client.internal;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.openmemind.ai.client.exception.MemindApiException;
import com.openmemind.ai.client.exception.MemindTimeoutException;
import com.openmemind.ai.client.model.response.HealthResponse;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

@WireMockTest
class MemindHttpClientTest {

    @Test
    void get_successfulResponse_returnsData(WireMockRuntimeInfo wmInfo) {
        stubFor(
                get("/open/v1/health")
                        .willReturn(
                                okJson(
                                        """
                                        {"code":"success","data":{"status":"UP","service":"memind-server"},"timestamp":"2026-01-01T00:00:00Z"}
                                        """)));

        MemindHttpClient httpClient =
                new MemindHttpClient(
                        wmInfo.getHttpBaseUrl(), null, Duration.ofSeconds(5), Duration.ofSeconds(5));

        HealthResponse result =
                httpClient
                        .get("/open/v1/health", new TypeReference<ApiResult<HealthResponse>>() {})
                        .join();

        assertThat(result.status()).isEqualTo("UP");
        assertThat(result.service()).isEqualTo("memind-server");
    }

    @Test
    void post_apiError_throwsMemindApiException(WireMockRuntimeInfo wmInfo) {
        stubFor(
                post("/open/v1/memory/retrieve")
                        .willReturn(
                                aResponse()
                                        .withStatus(400)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                """
                                                {"code":"bad_request","message":"query is required","timestamp":"2026-01-01T00:00:00Z","traceId":"abc123"}
                                                """)));

        MemindHttpClient httpClient =
                new MemindHttpClient(
                        wmInfo.getHttpBaseUrl(), null, Duration.ofSeconds(5), Duration.ofSeconds(5));

        assertThatThrownBy(
                        () ->
                                httpClient
                                        .post(
                                                "/open/v1/memory/retrieve",
                                                Map.of(),
                                                new TypeReference<ApiResult<Void>>() {})
                                        .join())
                .hasCauseInstanceOf(MemindApiException.class)
                .extracting(e -> (MemindApiException) e.getCause())
                .satisfies(
                        ex -> {
                            assertThat(ex.getHttpStatus()).isEqualTo(400);
                            assertThat(ex.getErrorCode()).isEqualTo("bad_request");
                            assertThat(ex.getTraceId()).isEqualTo("abc123");
                        });
    }

    @Test
    void post_withApiToken_sendsAuthorizationHeader(WireMockRuntimeInfo wmInfo) {
        stubFor(
                post("/open/v1/memory/commit")
                        .willReturn(
                                okJson(
                                        """
                                        {"code":"200","timestamp":"2026-01-01T00:00:00Z"}
                                        """)));

        MemindHttpClient httpClient =
                new MemindHttpClient(
                        wmInfo.getHttpBaseUrl(),
                        "mk-test-token",
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(5));

        httpClient
                .post(
                        "/open/v1/memory/commit",
                        Map.of("userId", "u1", "agentId", "a1"),
                        new TypeReference<ApiResult<Void>>() {})
                .join();

        verify(
                postRequestedFor(urlEqualTo("/open/v1/memory/commit"))
                        .withHeader("Authorization", equalTo("Bearer mk-test-token"))
                        .withHeader("User-Agent", matching("memind-java-client/.*")));
    }

    @Test
    void get_timeout_throwsMemindTimeoutException(WireMockRuntimeInfo wmInfo) {
        stubFor(get("/open/v1/health").willReturn(ok().withFixedDelay(3000)));

        MemindHttpClient httpClient =
                new MemindHttpClient(
                        wmInfo.getHttpBaseUrl(), null, Duration.ofSeconds(1), Duration.ofSeconds(1));

        assertThatThrownBy(
                        () ->
                                httpClient
                                        .get(
                                                "/open/v1/health",
                                                new TypeReference<ApiResult<HealthResponse>>() {})
                                        .join())
                .hasCauseInstanceOf(MemindTimeoutException.class);
    }

    @Test
    void get_withBaseUrlPath_preservesContextPath(WireMockRuntimeInfo wmInfo) {
        stubFor(
                get("/memind/open/v1/health")
                        .willReturn(
                                okJson(
                                        """
                                        {"code":"success","data":{"status":"UP","service":"memind-server"}}
                                        """)));

        MemindHttpClient httpClient =
                new MemindHttpClient(
                        wmInfo.getHttpBaseUrl() + "/memind",
                        null,
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(5));

        httpClient
                .get("/open/v1/health", new TypeReference<ApiResult<HealthResponse>>() {})
                .join();

        verify(getRequestedFor(urlEqualTo("/memind/open/v1/health")));
    }
}
