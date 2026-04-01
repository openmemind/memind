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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmemind.ai.memory.server.domain.memory.request.AddMessageRequest;
import com.openmemind.ai.memory.server.domain.memory.request.CommitMemoryRequest;
import com.openmemind.ai.memory.server.domain.memory.request.ExtractMemoryRequest;
import com.openmemind.ai.memory.server.domain.memory.request.RetrieveMemoryRequest;
import com.openmemind.ai.memory.server.domain.memory.response.RetrieveMemoryResponse;
import com.openmemind.ai.memory.server.handler.ApiExceptionHandler;
import com.openmemind.ai.memory.server.service.memory.OpenMemoryApplicationService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class OpenMemoryControllerTest {

    private final StubOpenMemoryApplicationService service = new StubOpenMemoryApplicationService();
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        this.mockMvc =
                MockMvcBuilders.standaloneSetup(new OpenMemoryController(service))
                        .setControllerAdvice(new ApiExceptionHandler())
                        .setValidator(validator)
                        .build();
    }

    @Test
    void extractAcceptsConversationRawContent() throws Exception {
        MvcResult result =
                mockMvc.perform(
                                post("/open/v1/memory/extract")
                                        .contentType(APPLICATION_JSON)
                                        .content(
                                                """
                                                {
                                                  "userId": "u1",
                                                  "agentId": "a1",
                                                  "rawContent": {
                                                    "type": "conversation",
                                                    "messages": [
                                                      {
                                                        "role": "USER",
                                                        "content": [
                                                          {
                                                            "type": "text",
                                                            "text": "hello"
                                                          }
                                                        ],
                                                        "timestamp": "2026-03-31T10:00:00Z"
                                                      }
                                                    ]
                                                  }
                                                }
                                                """))
                        .andExpect(request().asyncStarted())
                        .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.data").doesNotExist());

        org.assertj.core.api.Assertions.assertThat(service.lastExtractRequest).isNotNull();
    }

    @Test
    void addMessageReturnsOkWithoutPayload() throws Exception {
        MvcResult result =
                mockMvc.perform(
                                post("/open/v1/memory/add-message")
                                        .contentType(APPLICATION_JSON)
                                        .content(
                                                """
                                                {
                                                  "userId": "u1",
                                                  "agentId": "a1",
                                                  "message": {
                                                    "role": "USER",
                                                    "content": [
                                                      {
                                                        "type": "text",
                                                        "text": "hello"
                                                      }
                                                    ],
                                                    "timestamp": "2026-03-31T10:00:00Z"
                                                  }
                                                }
                                                """))
                        .andExpect(request().asyncStarted())
                        .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.data").doesNotExist());

        org.assertj.core.api.Assertions.assertThat(service.lastAddMessageRequest).isNotNull();
    }

    @Test
    void commitReturnsOkWithoutPayload() throws Exception {
        MvcResult result =
                mockMvc.perform(
                                post("/open/v1/memory/commit")
                                        .contentType(APPLICATION_JSON)
                                        .content(
                                                """
                                                {
                                                  "userId": "u1",
                                                  "agentId": "a1"
                                                }
                                                """))
                        .andExpect(request().asyncStarted())
                        .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.data").doesNotExist());

        org.assertj.core.api.Assertions.assertThat(service.lastCommitRequest).isNotNull();
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
                .andExpect(jsonPath("$.code").value("success"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.data.items[0].id").value("item-1"))
                .andExpect(jsonPath("$.data.insights[0].tier").value("LEAF"))
                .andExpect(jsonPath("$.data.rawData[0].rawDataId").value("rd-1"));
    }

    private static final class StubOpenMemoryApplicationService
            extends OpenMemoryApplicationService {

        private ExtractMemoryRequest lastExtractRequest;
        private AddMessageRequest lastAddMessageRequest;
        private CommitMemoryRequest lastCommitRequest;

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
        public RetrieveMemoryResponse retrieve(RetrieveMemoryRequest request) {
            return new RetrieveMemoryResponse(
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
}
