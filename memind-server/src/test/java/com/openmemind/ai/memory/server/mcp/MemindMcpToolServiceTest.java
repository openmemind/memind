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
package com.openmemind.ai.memory.server.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmemind.ai.memory.core.extraction.rawdata.content.ConversationContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message;
import com.openmemind.ai.memory.core.retrieval.RetrievalConfig;
import com.openmemind.ai.memory.server.domain.memory.request.AddMessageRequest;
import com.openmemind.ai.memory.server.domain.memory.request.CommitMemoryRequest;
import com.openmemind.ai.memory.server.domain.memory.request.ExtractMemoryRequest;
import com.openmemind.ai.memory.server.domain.memory.request.RetrieveMemoryRequest;
import com.openmemind.ai.memory.server.domain.memory.response.AddMessageResponse;
import com.openmemind.ai.memory.server.domain.memory.response.ExtractMemoryResponse;
import com.openmemind.ai.memory.server.domain.memory.response.RetrieveMemoryResponse;
import com.openmemind.ai.memory.server.service.memory.OpenMemoryApplicationService;
import java.util.List;
import org.junit.jupiter.api.Test;

class MemindMcpToolServiceTest {

    private final RecordingOpenMemoryApplicationService applicationService =
            new RecordingOpenMemoryApplicationService();
    private final MemindMcpToolService toolService = new MemindMcpToolService(applicationService);

    @Test
    void retrieveDefaultsToSimpleStrategy() {
        applicationService.retrieveResponse =
                new RetrieveMemoryResponse(
                        "success", List.of(), List.of(), List.of(), List.of(), "SIMPLE", "coffee");

        var response = toolService.retrieve(" user-1 ", "agent-1", " coffee ", null, null);

        assertThat(response.strategy()).isEqualTo("SIMPLE");
        assertThat(applicationService.lastRetrieveRequest.userId()).isEqualTo("user-1");
        assertThat(applicationService.lastRetrieveRequest.agentId()).isEqualTo("agent-1");
        assertThat(applicationService.lastRetrieveRequest.query()).isEqualTo("coffee");
        assertThat(applicationService.lastRetrieveRequest.strategy())
                .isEqualTo(RetrievalConfig.Strategy.SIMPLE);
    }

    @Test
    void retrievePassesDeepStrategyAndTrace() {
        applicationService.retrieveResponse =
                new RetrieveMemoryResponse(
                        "success", List.of(), List.of(), List.of(), List.of(), "DEEP", "coffee");

        toolService.retrieve("user-1", "agent-1", "coffee", "deep", true);

        assertThat(applicationService.lastRetrieveRequest.strategy())
                .isEqualTo(RetrievalConfig.Strategy.DEEP);
        assertThat(applicationService.lastRetrieveRequest.trace()).isTrue();
    }

    @Test
    void retrieveRejectsInvalidStrategy() {
        assertThatThrownBy(() -> toolService.retrieve("user-1", "agent-1", "coffee", "wide", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("strategy must be one of SIMPLE, DEEP");
    }

    @Test
    void extractTextCreatesConversationContentAndDefaultsSourceClient() {
        applicationService.extractResponse =
                new ExtractMemoryResponse(
                        "SUCCESS", List.of("rd-1"), List.of(), List.of(), false, 1L, null);

        var response = toolService.extractText("user-1", "agent-1", " remember this ", null);

        assertThat(response.rawDataIds()).containsExactly("rd-1");
        ExtractMemoryRequest request = applicationService.lastExtractRequest;
        assertThat(request.sourceClient()).isEqualTo("mcp");
        assertThat(request.rawContent()).isInstanceOf(ConversationContent.class);
        var content = (ConversationContent) request.rawContent();
        assertThat(content.getMessages())
                .singleElement()
                .satisfies(
                        message -> {
                            assertThat(message.role()).isEqualTo(Message.Role.USER);
                            assertThat(message.textContent()).isEqualTo("remember this");
                        });
    }

    @Test
    void addMessageMapsUserRoleAndSourceClient() {
        applicationService.addMessageResponse = new AddMessageResponse(false, null);

        toolService.addMessage("user-1", "agent-1", "user", " hello ", null);

        AddMessageRequest request = applicationService.lastAddMessageRequest;
        assertThat(request.sourceClient()).isEqualTo("mcp");
        assertThat(request.message().role()).isEqualTo(Message.Role.USER);
        assertThat(request.message().textContent()).isEqualTo("hello");
        assertThat(request.message().sourceClient()).isEqualTo("mcp");
    }

    @Test
    void addMessageMapsAssistantRole() {
        toolService.addMessage("user-1", "agent-1", "assistant", " response ", "claude-code");

        AddMessageRequest request = applicationService.lastAddMessageRequest;
        assertThat(request.sourceClient()).isEqualTo("claude-code");
        assertThat(request.message().role()).isEqualTo(Message.Role.ASSISTANT);
        assertThat(request.message().textContent()).isEqualTo("response");
        assertThat(request.message().sourceClient()).isEqualTo("claude-code");
    }

    @Test
    void addMessageRejectsSystemRole() {
        assertThatThrownBy(
                        () -> toolService.addMessage("user-1", "agent-1", "system", "policy", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("role must be one of user, assistant");
    }

    @Test
    void commitDefaultsSourceClient() {
        applicationService.commitResponse =
                new ExtractMemoryResponse(
                        "SUCCESS", List.of("rd-1"), List.of(), List.of(), false, 1L, null);

        var response = toolService.commit("user-1", "agent-1", null);

        assertThat(response.rawDataIds()).containsExactly("rd-1");
        CommitMemoryRequest request = applicationService.lastCommitRequest;
        assertThat(request.sourceClient()).isEqualTo("mcp");
    }

    @Test
    void blankRequiredFieldsFailBeforeServiceInvocation() {
        assertThatThrownBy(() -> toolService.commit(" ", "agent-1", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("userId must not be blank");

        assertThat(applicationService.lastCommitRequest).isNull();
    }

    private static final class RecordingOpenMemoryApplicationService
            extends OpenMemoryApplicationService {

        private RetrieveMemoryRequest lastRetrieveRequest;
        private ExtractMemoryRequest lastExtractRequest;
        private AddMessageRequest lastAddMessageRequest;
        private CommitMemoryRequest lastCommitRequest;
        private RetrieveMemoryResponse retrieveResponse =
                new RetrieveMemoryResponse(
                        "success", List.of(), List.of(), List.of(), List.of(), "SIMPLE", "query");
        private ExtractMemoryResponse extractResponse =
                new ExtractMemoryResponse(
                        "SUCCESS", List.of(), List.of(), List.of(), false, 1L, null);
        private AddMessageResponse addMessageResponse = new AddMessageResponse(false, null);
        private ExtractMemoryResponse commitResponse =
                new ExtractMemoryResponse(
                        "SUCCESS", List.of(), List.of(), List.of(), false, 1L, null);

        private RecordingOpenMemoryApplicationService() {
            super(null);
        }

        @Override
        public RetrieveMemoryResponse retrieve(RetrieveMemoryRequest request) {
            this.lastRetrieveRequest = request;
            return retrieveResponse;
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
    }
}
