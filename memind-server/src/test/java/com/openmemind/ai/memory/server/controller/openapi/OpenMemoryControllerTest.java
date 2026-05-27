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
package com.openmemind.ai.memory.server.controller.openapi;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openmemind.ai.memory.core.utils.JsonUtils;
import com.openmemind.ai.memory.server.configuration.RequestIdFilter;
import com.openmemind.ai.memory.server.domain.memory.request.AddMessageRequest;
import com.openmemind.ai.memory.server.domain.memory.request.CommitMemoryRequest;
import com.openmemind.ai.memory.server.domain.memory.request.ExtractMemoryRequest;
import com.openmemind.ai.memory.server.domain.memory.request.RetrieveMemoryRequest;
import com.openmemind.ai.memory.server.domain.memory.response.AddMessageResponse;
import com.openmemind.ai.memory.server.domain.memory.response.ExtractMemoryResponse;
import com.openmemind.ai.memory.server.domain.memory.response.QueryMemoryItemsResponse;
import com.openmemind.ai.memory.server.domain.memory.response.QueryMemoryRawDataResponse;
import com.openmemind.ai.memory.server.domain.memory.response.RetrieveMemoryResponse;
import com.openmemind.ai.memory.server.handler.ApiExceptionHandler;
import com.openmemind.ai.memory.server.service.memory.OpenMemoryApplicationService;
import com.openmemind.ai.memory.server.service.memory.OpenMemoryAssetQueryService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import tools.jackson.databind.json.JsonMapper;

class OpenMemoryControllerTest {

    private final StubOpenMemoryApplicationService service = new StubOpenMemoryApplicationService();
    private final StubOpenMemoryAssetQueryService assetQueryService =
            new StubOpenMemoryAssetQueryService();
    private final JsonMapper objectMapper = JsonUtils.mapper();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        this.mockMvc =
                MockMvcBuilders.standaloneSetup(
                                new OpenMemoryQueryController(service, assetQueryService),
                                new OpenMemorySyncController(service),
                                new OpenMemoryAsyncController(service))
                        .setControllerAdvice(new ApiExceptionHandler())
                        .addFilters(new RequestIdFilter())
                        .setMessageConverters(new JacksonJsonHttpMessageConverter(objectMapper))
                        .setValidator(validator)
                        .build();
    }

    @Test
    void asyncExtractAcceptsConversationRawContent() throws Exception {
        mockMvc.perform(
                        post("/open/v1/memory/async/extract")
                                .contentType(APPLICATION_JSON)
                                .content(validExtractJson()))
                .andExpect(status().isAccepted())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.data.operationId").exists())
                .andExpect(jsonPath("$.data.status").value("accepted"))
                .andExpect(jsonPath("$.data.mode").value("async"))
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.timestamp").doesNotExist());

        org.assertj.core.api.Assertions.assertThat(service.lastExtractRequest).isNotNull();
    }

    @Test
    void asyncAddMessageReturnsAccepted() throws Exception {
        mockMvc.perform(
                        post("/open/v1/memory/async/add-message")
                                .contentType(APPLICATION_JSON)
                                .content(validAddMessageJson()))
                .andExpect(status().isAccepted())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.data.operationId").exists())
                .andExpect(jsonPath("$.data.status").value("accepted"))
                .andExpect(jsonPath("$.data.mode").value("async"))
                .andExpect(jsonPath("$.code").doesNotExist());

        org.assertj.core.api.Assertions.assertThat(service.lastAddMessageRequest).isNotNull();
    }

    @Test
    void asyncCommitReturnsAccepted() throws Exception {
        mockMvc.perform(
                        post("/open/v1/memory/async/commit")
                                .contentType(APPLICATION_JSON)
                                .content(validCommitJson()))
                .andExpect(status().isAccepted())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.data.operationId").exists())
                .andExpect(jsonPath("$.data.status").value("accepted"))
                .andExpect(jsonPath("$.data.mode").value("async"))
                .andExpect(jsonPath("$.code").doesNotExist());

        org.assertj.core.api.Assertions.assertThat(service.lastCommitRequest).isNotNull();
    }

    @Test
    void extractSyncReturnsResponseOnSuccess() throws Exception {
        service.extractResponse =
                new ExtractMemoryResponse(
                        "SUCCESS",
                        List.of("rd-1"),
                        List.of(101L),
                        List.of(201L),
                        false,
                        123L,
                        null);

        mockMvc.perform(
                        post("/open/v1/memory/sync/extract")
                                .contentType(APPLICATION_JSON)
                                .content(validExtractJson()))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.timestamp").doesNotExist())
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.rawDataIds[0]").value("rd-1"))
                .andExpect(jsonPath("$.data.itemIds[0]").value(101))
                .andExpect(jsonPath("$.data.insightIds[0]").value(201))
                .andExpect(jsonPath("$.data.insightPending").value(false));

        org.assertj.core.api.Assertions.assertThat(service.lastExtractRequest).isNotNull();
    }

    @Test
    void extractSyncPreservesPartialSuccessAndInsightPending() throws Exception {
        service.extractResponse =
                new ExtractMemoryResponse(
                        "PARTIAL_SUCCESS",
                        List.of("rd-1"),
                        List.of(101L),
                        List.of(),
                        true,
                        234L,
                        "insight scheduling deferred");

        mockMvc.perform(
                        post("/open/v1/memory/sync/extract")
                                .contentType(APPLICATION_JSON)
                                .content(validExtractJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.data.status").value("PARTIAL_SUCCESS"))
                .andExpect(jsonPath("$.data.insightPending").value(true))
                .andExpect(jsonPath("$.data.errorMessage").value("insight scheduling deferred"));
    }

    @Test
    void extractSyncReturnsFailureEnvelopeOnFailedStatus() throws Exception {
        service.extractResponse =
                new ExtractMemoryResponse(
                        "FAILED", List.of(), List.of(), List.of(), false, 50L, "extraction failed");

        mockMvc.perform(
                        post("/open/v1/memory/sync/extract")
                                .contentType(APPLICATION_JSON)
                                .content(validExtractJson()))
                .andExpect(status().is5xxServerError())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.error.code").value("internal_error"))
                .andExpect(jsonPath("$.error.message").value("Memory extraction failed"))
                .andExpect(jsonPath("$.error.details.operation").value("extract"))
                .andExpect(jsonPath("$.error.details.reason").value("extraction failed"))
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    @Test
    void addMessageSyncReturnsSuccessWhenNoExtractionTriggered() throws Exception {
        service.addMessageResponse = new AddMessageResponse(false, null);

        mockMvc.perform(
                        post("/open/v1/memory/sync/add-message")
                                .contentType(APPLICATION_JSON)
                                .content(validAddMessageJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.data.triggered").value(false))
                .andExpect(jsonPath("$.data.result").doesNotExist());

        org.assertj.core.api.Assertions.assertThat(service.lastAddMessageRequest).isNotNull();
    }

    @Test
    void addMessageSyncReturnsFailureWhenTriggeredExtractionFailed() throws Exception {
        service.addMessageResponse =
                new AddMessageResponse(
                        true,
                        new ExtractMemoryResponse(
                                "FAILED",
                                List.of(),
                                List.of(),
                                List.of(),
                                false,
                                50L,
                                "boundary extraction failed"));

        mockMvc.perform(
                        post("/open/v1/memory/sync/add-message")
                                .contentType(APPLICATION_JSON)
                                .content(validAddMessageJson()))
                .andExpect(status().is5xxServerError())
                .andExpect(jsonPath("$.error.code").value("internal_error"))
                .andExpect(jsonPath("$.error.message").value("Memory extraction failed"))
                .andExpect(jsonPath("$.error.details.operation").value("add-message"))
                .andExpect(jsonPath("$.error.details.reason").value("boundary extraction failed"));
    }

    @Test
    void commitSyncReturnsExtractionResponse() throws Exception {
        service.commitResponse =
                new ExtractMemoryResponse(
                        "SUCCESS", List.of("rd-2"), List.of(102L), List.of(), false, 77L, null);

        mockMvc.perform(
                        post("/open/v1/memory/sync/commit")
                                .contentType(APPLICATION_JSON)
                                .content(validCommitJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.rawDataIds[0]").value("rd-2"));

        org.assertj.core.api.Assertions.assertThat(service.lastCommitRequest).isNotNull();
    }

    @Test
    void commitSyncReturnsFailureEnvelopeOnFailedStatus() throws Exception {
        service.commitResponse =
                new ExtractMemoryResponse(
                        "FAILED",
                        List.of(),
                        List.of(),
                        List.of(),
                        false,
                        50L,
                        "commit extraction failed");

        mockMvc.perform(
                        post("/open/v1/memory/sync/commit")
                                .contentType(APPLICATION_JSON)
                                .content(validCommitJson()))
                .andExpect(status().is5xxServerError())
                .andExpect(jsonPath("$.error.code").value("internal_error"))
                .andExpect(jsonPath("$.error.message").value("Memory extraction failed"))
                .andExpect(jsonPath("$.error.details.operation").value("commit"))
                .andExpect(jsonPath("$.error.details.reason").value("commit extraction failed"));
    }

    @Test
    void retrieveReturnsRankedMemoryPayload() throws Exception {
        mockMvc.perform(
                        post("/open/v1/memory/retrieve")
                                .contentType(APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "userId": "u1",
                                          "agentId": "a1",
                                          "query": "what do you know about me",
                                          "strategy": "SIMPLE"
                                        }
                                        """))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.timestamp").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].id").value("item-1"))
                .andExpect(jsonPath("$.data.insights[0].tier").value("LEAF"))
                .andExpect(jsonPath("$.data.rawData[0].rawDataId").value("rd-1"));
    }

    @Test
    void queryItemsReturnsStructuredMemoryItems() throws Exception {
        mockMvc.perform(
                        post("/open/v1/memory/items/query")
                                .contentType(APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "userId": "u1",
                                          "agentId": "a1",
                                          "scope": "AGENT",
                                          "categories": ["tool", "resolution"],
                                          "timeRange": {
                                            "field": "occurredAt",
                                            "from": "2026-05-01T00:00:00Z",
                                            "to": "2026-05-27T00:00:00Z"
                                          },
                                          "metadataFilter": {
                                            "all": [
                                              {"path": "projectSlug", "op": "eq", "value": "memind"}
                                            ]
                                          },
                                          "limit": 10
                                        }
                                        """))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].id").value("101"))
                .andExpect(jsonPath("$.data.items[0].category").value("tool"))
                .andExpect(jsonPath("$.data.items[0].metadata.projectSlug").value("memind"))
                .andExpect(jsonPath("$.data.nextCursor").doesNotExist());

        org.assertj.core.api.Assertions.assertThat(assetQueryService.lastItemsRequest).isNotNull();
    }

    @Test
    void queryRawDataOmitsSegmentUnlessIncluded() throws Exception {
        mockMvc.perform(
                        post("/open/v1/memory/raw-data/query")
                                .contentType(APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "userId": "u1",
                                          "agentId": "a1",
                                          "types": ["agent_timeline"],
                                          "sourceClients": ["claude-code"],
                                          "timeRange": {
                                            "field": "startTime",
                                            "from": "2026-05-01T00:00:00Z"
                                          },
                                          "include": {
                                            "segment": false,
                                            "metadata": true
                                          },
                                          "limit": 10
                                        }
                                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.data.rawData[0].id").value("rd-1"))
                .andExpect(jsonPath("$.data.rawData[0].type").value("agent_timeline"))
                .andExpect(jsonPath("$.data.rawData[0].metadata.projectSlug").value("memind"))
                .andExpect(jsonPath("$.data.rawData[0].segment").doesNotExist());

        org.assertj.core.api.Assertions.assertThat(assetQueryService.lastRawDataRequest)
                .isNotNull();
    }

    private static String validExtractJson() {
        return """
        {
          "userId": "u1",
          "agentId": "a1",
          "rawContent": {
            "type": "conversation",
            "messages": [
              {
                "role": "USER",
                "content": [{"type": "text", "text": "hello"}],
                "timestamp": "2026-03-31T10:00:00Z"
              }
            ]
          }
        }
        """;
    }

    private static String validAddMessageJson() {
        return """
        {
          "userId": "u1",
          "agentId": "a1",
          "message": {
            "role": "USER",
            "content": [{"type": "text", "text": "hello"}],
            "timestamp": "2026-03-31T10:00:00Z"
          }
        }
        """;
    }

    private static String validCommitJson() {
        return """
        {
          "userId": "u1",
          "agentId": "a1"
        }
        """;
    }

    private static final class StubOpenMemoryApplicationService
            extends OpenMemoryApplicationService {

        private ExtractMemoryRequest lastExtractRequest;
        private AddMessageRequest lastAddMessageRequest;
        private CommitMemoryRequest lastCommitRequest;
        private ExtractMemoryResponse extractResponse =
                new ExtractMemoryResponse(
                        "SUCCESS", List.of(), List.of(), List.of(), false, 1L, null);
        private AddMessageResponse addMessageResponse = new AddMessageResponse(false, null);
        private ExtractMemoryResponse commitResponse =
                new ExtractMemoryResponse(
                        "SUCCESS", List.of(), List.of(), List.of(), false, 1L, null);

        private StubOpenMemoryApplicationService() {
            super(null);
        }

        @Override
        public void extractAsync(ExtractMemoryRequest request) {
            this.lastExtractRequest = request;
        }

        @Override
        public void addMessageAsync(AddMessageRequest request) {
            this.lastAddMessageRequest = request;
        }

        @Override
        public void commitAsync(CommitMemoryRequest request) {
            this.lastCommitRequest = request;
        }

        @Override
        public ExtractMemoryResponse extract(ExtractMemoryRequest request) {
            this.lastExtractRequest = request;
            return extractResponse;
        }

        @Override
        public AddMessageResponse addMessage(AddMessageRequest request) {
            this.lastAddMessageRequest = request;
            return addMessageResponse;
        }

        @Override
        public ExtractMemoryResponse commit(CommitMemoryRequest request) {
            this.lastCommitRequest = request;
            return commitResponse;
        }

        @Override
        public RetrieveMemoryResponse retrieve(RetrieveMemoryRequest request) {
            return new RetrieveMemoryResponse(
                    "success",
                    List.of(
                            new RetrieveMemoryResponse.RetrievedItemView(
                                    "item-1",
                                    "loves coffee",
                                    0.82F,
                                    0.91,
                                    Instant.parse("2026-03-30T10:00:00Z"))),
                    List.of(
                            new RetrieveMemoryResponse.RetrievedInsightView(
                                    "insight-1", "prefers concise answers", "LEAF")),
                    List.of(
                            new RetrieveMemoryResponse.RetrievedRawDataView(
                                    "rd-1", "user mentioned coffee", 0.76, List.of("item-1"))),
                    List.of("user likes coffee"),
                    "SIMPLE",
                    request.query());
        }
    }

    private static final class StubOpenMemoryAssetQueryService extends OpenMemoryAssetQueryService {

        private com.openmemind.ai.memory.server.domain.memory.request.QueryMemoryItemsRequest
                lastItemsRequest;
        private com.openmemind.ai.memory.server.domain.memory.request.QueryMemoryRawDataRequest
                lastRawDataRequest;

        private StubOpenMemoryAssetQueryService() {
            super(null, null);
        }

        @Override
        public QueryMemoryItemsResponse queryItems(
                com.openmemind.ai.memory.server.domain.memory.request.QueryMemoryItemsRequest
                        request) {
            this.lastItemsRequest = request;
            return new QueryMemoryItemsResponse(
                    List.of(
                            new QueryMemoryItemsResponse.MemoryItemView(
                                    "101",
                                    "Run mvn -pl memind-server -am for server tests",
                                    "AGENT",
                                    "tool",
                                    "FACT",
                                    "rd-1",
                                    "agent_timeline",
                                    "claude-code",
                                    Instant.parse("2026-05-24T12:00:00Z"),
                                    Instant.parse("2026-05-24T12:01:00Z"),
                                    Instant.parse("2026-05-24T12:02:00Z"),
                                    Map.of("projectSlug", "memind"))),
                    null);
        }

        @Override
        public QueryMemoryRawDataResponse queryRawData(
                com.openmemind.ai.memory.server.domain.memory.request.QueryMemoryRawDataRequest
                        request) {
            this.lastRawDataRequest = request;
            return new QueryMemoryRawDataResponse(
                    List.of(
                            new QueryMemoryRawDataResponse.MemoryRawDataView(
                                    "rd-1",
                                    "agent_timeline",
                                    "claude-code",
                                    "fixed server API",
                                    Map.of("projectSlug", "memind"),
                                    null,
                                    Instant.parse("2026-05-24T12:00:00Z"),
                                    Instant.parse("2026-05-24T12:10:00Z"),
                                    Instant.parse("2026-05-24T12:11:00Z"))),
                    null);
        }
    }
}
