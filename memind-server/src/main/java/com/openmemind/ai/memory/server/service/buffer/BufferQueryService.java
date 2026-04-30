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
package com.openmemind.ai.memory.server.service.buffer;

import com.openmemind.ai.memory.server.domain.buffer.query.ConversationBufferPageQuery;
import com.openmemind.ai.memory.server.domain.buffer.query.InsightBufferPageQuery;
import com.openmemind.ai.memory.server.domain.buffer.view.ConversationBufferView;
import com.openmemind.ai.memory.server.domain.buffer.view.InsightBufferGroupView;
import com.openmemind.ai.memory.server.domain.buffer.view.InsightBufferView;
import com.openmemind.ai.memory.server.domain.common.PageResponse;
import com.openmemind.ai.memory.server.mapper.buffer.AdminBufferQueryMapper;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;

@Service
public class BufferQueryService {

    private final AdminBufferQueryMapper bufferQueryMapper;

    public BufferQueryService(AdminBufferQueryMapper bufferQueryMapper) {
        this.bufferQueryMapper = bufferQueryMapper;
    }

    public PageResponse<ConversationBufferView> listConversations(
            ConversationBufferPageQuery query) {
        return bufferQueryMapper.pageConversations(query);
    }

    public ConversationBufferView getConversation(Long id) {
        return bufferQueryMapper
                .findConversation(id)
                .orElseThrow(
                        () -> new NoSuchElementException("Conversation buffer not found: " + id));
    }

    public PageResponse<InsightBufferView> listInsights(InsightBufferPageQuery query) {
        return bufferQueryMapper.pageInsights(query);
    }

    public List<InsightBufferGroupView> listInsightGroups(String memoryId, String insightTypeName) {
        return bufferQueryMapper.listInsightGroups(memoryId, insightTypeName);
    }
}
