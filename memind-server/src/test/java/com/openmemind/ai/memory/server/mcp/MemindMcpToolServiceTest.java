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
import com.openmemind.ai.memory.core.utils.JsonUtils;
import com.openmemind.ai.memory.server.domain.memory.request.AddMessageRequest;
import com.openmemind.ai.memory.server.domain.memory.request.CommitMemoryRequest;
import com.openmemind.ai.memory.server.domain.memory.request.ExtractMemoryRequest;
import com.openmemind.ai.memory.server.domain.memory.request.QueryMemoryItemsRequest;
import com.openmemind.ai.memory.server.domain.memory.request.QueryMemoryRawDataRequest;
import com.openmemind.ai.memory.server.domain.memory.request.RetrieveMemoryRequest;
import com.openmemind.ai.memory.server.domain.memory.response.AddMessageResponse;
import com.openmemind.ai.memory.server.domain.memory.response.ExtractMemoryResponse;
import com.openmemind.ai.memory.server.domain.memory.response.QueryMemoryItemsResponse;
import com.openmemind.ai.memory.server.domain.memory.response.QueryMemoryRawDataResponse;
import com.openmemind.ai.memory.server.domain.memory.response.RetrieveMemoryResponse;
import com.openmemind.ai.memory.server.mcp.compiler.MemindMcpContextCompiler;
import com.openmemind.ai.memory.server.mcp.config.MemindMcpToolProperties;
import com.openmemind.ai.memory.server.mcp.response.MemindItemSourcesResponse;
import com.openmemind.ai.memory.server.mcp.response.MemindItemsGetResponse;
import com.openmemind.ai.memory.server.mcp.response.MemindRawDataGetResponse;
import com.openmemind.ai.memory.server.service.memory.OpenMemoryApplicationService;
import com.openmemind.ai.memory.server.service.memory.OpenMemoryAssetQueryService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MemindMcpToolServiceTest {

    private final RecordingOpenMemoryApplicationService applicationService =
            new RecordingOpenMemoryApplicationService();
    private final RecordingOpenMemoryAssetQueryService assetQueryService =
            new RecordingOpenMemoryAssetQueryService();
    private final MemindMcpToolService toolService =
            new MemindMcpToolService(
                    applicationService,
                    assetQueryService,
                    new MemindMcpContextCompiler(),
                    new MemindMcpToolProperties(false, 1800, 6000, 50, 100, 50, 12000),
                    JsonUtils.mapper());

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
    void compileContextDelegatesToRetrieveHonorsMaxItemsAndIncludesSourcesByDefault() {
        applicationService.retrieveResponse =
                new RetrieveMemoryResponse(
                        "success",
                        List.of(
                                item("1", "Always run tests", "directive"),
                                item("2", "Second item", "tool")),
                        List.of(),
                        List.of(
                                new RetrieveMemoryResponse.RetrievedRawDataView(
                                        "rd-1",
                                        "Test turn",
                                        0.9,
                                        List.of("1"),
                                        "agent_timeline",
                                        "claude-code",
                                        Map.of(),
                                        Instant.parse("2026-05-01T00:00:00Z"),
                                        Instant.parse("2026-05-01T00:01:00Z"),
                                        Instant.parse("2026-05-01T00:02:00Z"))),
                        List.of(),
                        "SIMPLE",
                        "tests");

        var response =
                toolService.compileContext(
                        "user-1", "agent-1", " tests ", null, 1, 1200, null, null);

        assertThat(applicationService.lastRetrieveRequest.query()).isEqualTo("tests");
        assertThat(response.contextText())
                .contains("Always run tests")
                .doesNotContain("Second item");
        assertThat(response.sources())
                .singleElement()
                .satisfies(source -> assertThat(source.rawDataId()).isEqualTo("rd-1"));
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
    void extractRawDataUsesRawContentMapperAndRejectsNestedType() {
        applicationService.extractResponse =
                new ExtractMemoryResponse(
                        "SUCCESS", List.of("rd-1"), List.of(), List.of(), false, 1L, null);

        toolService.extractRawData(
                "user-1", "agent-1", "conversation", Map.of("messages", List.of()), null, null);

        assertThat(applicationService.lastExtractRequest.rawContent())
                .isInstanceOf(ConversationContent.class);
        assertThat(applicationService.lastExtractRequest.sourceClient()).isEqualTo("mcp");

        assertThatThrownBy(
                        () ->
                                toolService.extractRawData(
                                        "user-1",
                                        "agent-1",
                                        "conversation",
                                        Map.of("type", "tool_call"),
                                        null,
                                        null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("content must not contain type");
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

    @Test
    void itemsSearchDelegatesToAssetQueryServiceWithFilters() {
        toolService.itemsSearch(
                "user-1",
                "agent-1",
                "AGENT",
                List.of("tool"),
                List.of("claude-code"),
                List.of("agent_timeline"),
                null,
                null,
                10,
                null);

        QueryMemoryItemsRequest request = assetQueryService.lastItemsRequest;
        assertThat(request.userId()).isEqualTo("user-1");
        assertThat(request.agentId()).isEqualTo("agent-1");
        assertThat(request.scope()).isEqualTo("AGENT");
        assertThat(request.categories()).containsExactly("tool");
        assertThat(request.sourceClients()).containsExactly("claude-code");
        assertThat(request.rawDataTypes()).containsExactly("agent_timeline");
        assertThat(request.limit()).isEqualTo(10);
    }

    @Test
    void itemsGetParsesIdsAndDelegatesToScopedLookup() {
        toolService.itemsGet("user-1", "agent-1", List.of(" 101 ", "102"));

        assertThat(assetQueryService.lastItemIds).containsExactly(101L, 102L);
    }

    @Test
    void itemsSourcesDefaultsIncludeSegmentFalse() {
        toolService.itemsSources("user-1", "agent-1", List.of("101"), null);

        assertThat(assetQueryService.lastIncludeSegment).isFalse();
    }

    @Test
    void rawDataSearchDefaultsIncludeSegmentFalseAndMetadataTrue() {
        toolService.rawDataSearch(
                "user-1",
                "agent-1",
                List.of("agent_timeline"),
                List.of("codex"),
                null,
                null,
                null,
                null,
                null,
                null);

        QueryMemoryRawDataRequest request = assetQueryService.lastRawDataRequest;
        assertThat(request.effectiveInclude().includeSegment()).isFalse();
        assertThat(request.effectiveInclude().includeMetadata()).isTrue();
    }

    @Test
    void rawDataGetDefaultsIncludeSegmentFalse() {
        toolService.rawDataGet("user-1", "agent-1", List.of("rd-1"), null);

        assertThat(assetQueryService.lastRawDataIds).containsExactly("rd-1");
        assertThat(assetQueryService.lastIncludeSegment).isFalse();
    }

    @Test
    void recentMergesItemsAndRawDataByTime() {
        var response =
                toolService.recent("user-1", "agent-1", List.of("ITEM", "RAWDATA"), 2, null, null);

        assertThat(response.entries()).hasSize(2);
        assertThat(response.entries().get(0).kind()).isEqualTo("RAWDATA");
        assertThat(response.entries().get(1).kind()).isEqualTo("ITEM");
    }

    private static RetrieveMemoryResponse.RetrievedItemView item(
            String id, String text, String category) {
        return new RetrieveMemoryResponse.RetrievedItemView(
                id, text, 1.0f, 1.0, Instant.parse("2026-05-01T00:00:00Z"), category, Map.of());
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

    private static final class RecordingOpenMemoryAssetQueryService
            extends OpenMemoryAssetQueryService {

        private QueryMemoryItemsRequest lastItemsRequest;
        private QueryMemoryRawDataRequest lastRawDataRequest;
        private List<Long> lastItemIds;
        private List<String> lastRawDataIds;
        private boolean lastIncludeSegment;

        private RecordingOpenMemoryAssetQueryService() {
            super(null, null);
        }

        @Override
        public QueryMemoryItemsResponse queryItems(QueryMemoryItemsRequest request) {
            this.lastItemsRequest = request;
            return new QueryMemoryItemsResponse(
                    List.of(
                            new QueryMemoryItemsResponse.MemoryItemView(
                                    "101",
                                    "Run mvn test",
                                    "AGENT",
                                    "tool",
                                    "FACT",
                                    "rd-1",
                                    "agent_timeline",
                                    "claude-code",
                                    Instant.parse("2026-05-01T00:00:00Z"),
                                    Instant.parse("2026-05-01T00:01:00Z"),
                                    Instant.parse("2026-05-01T00:02:00Z"),
                                    Map.of())),
                    null);
        }

        @Override
        public QueryMemoryRawDataResponse queryRawData(QueryMemoryRawDataRequest request) {
            this.lastRawDataRequest = request;
            return new QueryMemoryRawDataResponse(
                    List.of(
                            new QueryMemoryRawDataResponse.MemoryRawDataView(
                                    "rd-1",
                                    "agent_timeline",
                                    "claude-code",
                                    "Fixed build",
                                    Map.of(),
                                    null,
                                    Instant.parse("2026-05-01T00:03:00Z"),
                                    Instant.parse("2026-05-01T00:04:00Z"),
                                    Instant.parse("2026-05-01T00:05:00Z"))),
                    null);
        }

        @Override
        public MemindItemsGetResponse getItemsByIds(
                String userId, String agentId, List<Long> itemIds) {
            this.lastItemIds = itemIds;
            return new MemindItemsGetResponse(List.of(), List.of());
        }

        @Override
        public MemindItemSourcesResponse getItemSourcesByItemIds(
                String userId,
                String agentId,
                List<Long> itemIds,
                boolean includeSegment,
                int maxSegmentChars) {
            this.lastItemIds = itemIds;
            this.lastIncludeSegment = includeSegment;
            return new MemindItemSourcesResponse(List.of(), List.of());
        }

        @Override
        public MemindRawDataGetResponse getRawDataByIds(
                String userId,
                String agentId,
                List<String> rawDataIds,
                boolean includeSegment,
                int maxSegmentChars) {
            this.lastRawDataIds = rawDataIds;
            this.lastIncludeSegment = includeSegment;
            return new MemindRawDataGetResponse(List.of(), List.of());
        }
    }
}
