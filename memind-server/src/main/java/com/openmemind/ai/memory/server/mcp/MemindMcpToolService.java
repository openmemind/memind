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

import com.openmemind.ai.memory.server.domain.memory.response.AddMessageResponse;
import com.openmemind.ai.memory.server.domain.memory.response.ExtractMemoryResponse;
import com.openmemind.ai.memory.server.domain.memory.response.RetrieveMemoryResponse;
import com.openmemind.ai.memory.server.mcp.request.MemindAddMessageToolRequest;
import com.openmemind.ai.memory.server.mcp.request.MemindCommitToolRequest;
import com.openmemind.ai.memory.server.mcp.request.MemindExtractTextToolRequest;
import com.openmemind.ai.memory.server.mcp.request.MemindRetrieveToolRequest;
import com.openmemind.ai.memory.server.service.memory.OpenMemoryApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

@Service
public class MemindMcpToolService {

    private static final Logger log = LoggerFactory.getLogger(MemindMcpToolService.class);

    private final OpenMemoryApplicationService memoryService;

    public MemindMcpToolService(OpenMemoryApplicationService memoryService) {
        this.memoryService = memoryService;
    }

    @McpTool(
            name = "memind_retrieve",
            title = "Retrieve Memind memory",
            description =
                    "Retrieve memory for a userId and agentId using a natural-language query. "
                            + "Use strategy SIMPLE for low-latency retrieval or DEEP for "
                            + "LLM-assisted retrieval.")
    public RetrieveMemoryResponse retrieve(
            @McpToolParam(description = "Memory user scope.") String userId,
            @McpToolParam(description = "Memory agent scope.") String agentId,
            @McpToolParam(description = "Natural-language retrieval query.") String query,
            @McpToolParam(
                            required = false,
                            description = "Retrieval strategy: SIMPLE or DEEP. Defaults to SIMPLE.")
                    String strategy,
            @McpToolParam(
                            required = false,
                            description = "Whether to include retrieval trace when supported.")
                    Boolean trace) {
        var request = new MemindRetrieveToolRequest(userId, agentId, query, strategy, trace);
        log.debug(
                "MCP tool memind_retrieve invoked for userId={} agentId={}",
                request.userId(),
                request.agentId());
        return memoryService.retrieve(request.toApplicationRequest());
    }

    @McpTool(
            name = "memind_extract_text",
            title = "Extract standalone text into Memind memory",
            description =
                    "Extract memory immediately from one standalone text payload, such as pasted "
                            + "notes, document excerpts, or summaries. For ongoing chat turns, "
                            + "use memind_add_message and memind_commit instead.")
    public ExtractMemoryResponse extractText(
            @McpToolParam(description = "Memory user scope.") String userId,
            @McpToolParam(description = "Memory agent scope.") String agentId,
            @McpToolParam(description = "Standalone text to extract into memory.") String text,
            @McpToolParam(
                            required = false,
                            description =
                                    "Source marker stored with the extraction request. Defaults "
                                            + "to mcp.")
                    String sourceClient) {
        var request = new MemindExtractTextToolRequest(userId, agentId, text, sourceClient);
        log.debug(
                "MCP tool memind_extract_text invoked for userId={} agentId={}",
                request.userId(),
                request.agentId());
        return memoryService.extract(request.toApplicationRequest());
    }

    @McpTool(
            name = "memind_add_message",
            title = "Add a conversation message to Memind memory",
            description =
                    "Add one user or assistant conversation message to Memind's buffered "
                            + "conversation flow. Use this for conversation-style memory, then "
                            + "call memind_commit to flush pending messages.")
    public AddMessageResponse addMessage(
            @McpToolParam(description = "Memory user scope.") String userId,
            @McpToolParam(description = "Memory agent scope.") String agentId,
            @McpToolParam(description = "Message role: user or assistant.") String role,
            @McpToolParam(description = "Plain text message body.") String content,
            @McpToolParam(
                            required = false,
                            description = "Source marker attached to the message. Defaults to mcp.")
                    String sourceClient) {
        var request = new MemindAddMessageToolRequest(userId, agentId, role, content, sourceClient);
        log.debug(
                "MCP tool memind_add_message invoked for userId={} agentId={}",
                request.userId(),
                request.agentId());
        return memoryService.addMessage(request.toApplicationRequest());
    }

    @McpTool(
            name = "memind_commit",
            title = "Commit pending Memind conversation memory",
            description =
                    "Flush pending conversation messages for a userId and agentId. Use this after "
                            + "memind_add_message when conversation turns should be committed to "
                            + "memory.")
    public ExtractMemoryResponse commit(
            @McpToolParam(description = "Memory user scope.") String userId,
            @McpToolParam(description = "Memory agent scope.") String agentId,
            @McpToolParam(
                            required = false,
                            description =
                                    "Source marker for the commit operation. Defaults to mcp.")
                    String sourceClient) {
        var request = new MemindCommitToolRequest(userId, agentId, sourceClient);
        log.debug(
                "MCP tool memind_commit invoked for userId={} agentId={}",
                request.userId(),
                request.agentId());
        return memoryService.commit(request.toApplicationRequest());
    }
}
