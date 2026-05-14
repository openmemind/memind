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
package com.openmemind.ai.memory.server.mcp.request;

import com.openmemind.ai.memory.core.extraction.rawdata.content.ConversationContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message;
import com.openmemind.ai.memory.server.domain.memory.request.ExtractMemoryRequest;
import java.util.List;

public record MemindExtractTextToolRequest(
        String userId, String agentId, String text, String sourceClient) {

    public ExtractMemoryRequest toApplicationRequest() {
        return new ExtractMemoryRequest(
                requireText(userId, "userId"),
                requireText(agentId, "agentId"),
                new ConversationContent(List.of(Message.user(requireText(text, "text")))),
                normalizeSourceClient(sourceClient));
    }

    private static String normalizeSourceClient(String value) {
        if (value == null || value.isBlank()) {
            return "mcp";
        }
        return value.trim();
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
