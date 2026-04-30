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

import com.openmemind.ai.memory.server.domain.buffer.view.ConversationBufferView;
import com.openmemind.ai.memory.server.domain.buffer.view.InsightBufferView;
import com.openmemind.ai.memory.server.domain.common.AdminUpdateResult;
import com.openmemind.ai.memory.server.domain.common.BatchDeleteResult;
import com.openmemind.ai.memory.server.mapper.buffer.AdminBufferQueryMapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class BufferManagementService {

    private final AdminBufferQueryMapper bufferQueryMapper;

    public BufferManagementService(AdminBufferQueryMapper bufferQueryMapper) {
        this.bufferQueryMapper = bufferQueryMapper;
    }

    public AdminUpdateResult markConversationsExtracted(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new AdminUpdateResult(0, List.of());
        }
        List<ConversationBufferView> rows = bufferQueryMapper.findConversations(ids);
        int updated = bufferQueryMapper.markConversationsExtracted(ids);
        return new AdminUpdateResult(updated, affectedConversationMemoryIds(rows));
    }

    public BatchDeleteResult deleteConversations(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new BatchDeleteResult(0, List.of());
        }
        List<ConversationBufferView> rows = bufferQueryMapper.findConversations(ids);
        int deleted = bufferQueryMapper.deleteConversations(ids);
        return new BatchDeleteResult(deleted, affectedConversationMemoryIds(rows));
    }

    public AdminUpdateResult updateInsightGroup(List<Integer> ids, String groupName) {
        if (ids == null || ids.isEmpty()) {
            return new AdminUpdateResult(0, List.of());
        }
        List<InsightBufferView> rows = bufferQueryMapper.findInsights(ids);
        int updated = bufferQueryMapper.updateInsightGroup(ids, groupName);
        return new AdminUpdateResult(updated, affectedInsightMemoryIds(rows));
    }

    public AdminUpdateResult updateInsightBuilt(List<Integer> ids, Boolean built) {
        if (ids == null || ids.isEmpty()) {
            return new AdminUpdateResult(0, List.of());
        }
        List<InsightBufferView> rows = bufferQueryMapper.findInsights(ids);
        int updated = bufferQueryMapper.updateInsightBuilt(ids, built);
        return new AdminUpdateResult(updated, affectedInsightMemoryIds(rows));
    }

    public BatchDeleteResult deleteInsightBuffers(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return new BatchDeleteResult(0, List.of());
        }
        List<InsightBufferView> rows = bufferQueryMapper.findInsights(ids);
        int deleted = bufferQueryMapper.physicalDeleteInsightBuffers(ids);
        return new BatchDeleteResult(deleted, affectedInsightMemoryIds(rows));
    }

    private static List<String> affectedConversationMemoryIds(List<ConversationBufferView> rows) {
        return rows.stream().map(ConversationBufferView::memoryId).distinct().toList();
    }

    private static List<String> affectedInsightMemoryIds(List<InsightBufferView> rows) {
        return rows.stream().map(InsightBufferView::memoryId).distinct().toList();
    }
}
