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
package com.openmemind.ai.memory.server.service.itemgraph;

import com.openmemind.ai.memory.server.domain.common.BatchDeleteResult;
import com.openmemind.ai.memory.server.domain.itemgraph.view.AdminGraphEntityDeleteResult;
import com.openmemind.ai.memory.server.domain.itemgraph.view.ItemGraphViews;
import com.openmemind.ai.memory.server.mapper.itemgraph.AdminItemGraphQueryMapper;
import com.openmemind.ai.memory.server.support.AdminMemoryScope;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ItemGraphManagementService {

    private final AdminItemGraphQueryMapper itemGraphQueryMapper;

    public ItemGraphManagementService(AdminItemGraphQueryMapper itemGraphQueryMapper) {
        this.itemGraphQueryMapper = itemGraphQueryMapper;
    }

    @Transactional
    public AdminGraphEntityDeleteResult deleteEntities(String memoryId, List<String> entityKeys) {
        AdminMemoryScope scope = AdminMemoryScope.fromMemoryId(memoryId);
        if (!scope.present()) {
            throw new IllegalArgumentException("memoryId is required");
        }
        if (entityKeys == null || entityKeys.isEmpty()) {
            return new AdminGraphEntityDeleteResult(0, List.of(), 0, 0, 0, 0);
        }
        long possiblyStaleLinks =
                itemGraphQueryMapper.countEntityOverlapItemLinks(scope.memoryId(), entityKeys);
        int deletedAliases =
                itemGraphQueryMapper.physicalDeleteScopedAliases(scope.memoryId(), entityKeys);
        int deletedMentions =
                itemGraphQueryMapper.physicalDeleteScopedMentions(scope.memoryId(), entityKeys);
        int deletedCooccurrences =
                itemGraphQueryMapper.physicalDeleteScopedCooccurrences(
                        scope.memoryId(), entityKeys);
        int deletedEntities =
                itemGraphQueryMapper.physicalDeleteScopedEntities(scope.memoryId(), entityKeys);
        int deletedRows = deletedEntities + deletedAliases + deletedMentions + deletedCooccurrences;
        return new AdminGraphEntityDeleteResult(
                deletedEntities,
                deletedRows > 0 ? List.of(scope.memoryId()) : List.of(),
                deletedAliases,
                deletedMentions,
                deletedCooccurrences,
                possiblyStaleLinks);
    }

    public BatchDeleteResult deleteAliases(List<Integer> ids) {
        List<ItemGraphViews.AliasView> rows = itemGraphQueryMapper.findAliases(ids);
        return new BatchDeleteResult(
                itemGraphQueryMapper.physicalDeleteAliases(ids),
                rows.stream().map(ItemGraphViews.AliasView::memoryId).distinct().toList());
    }

    public BatchDeleteResult deleteMentions(List<Integer> ids) {
        List<ItemGraphViews.MentionView> rows = itemGraphQueryMapper.findMentions(ids);
        return new BatchDeleteResult(
                itemGraphQueryMapper.physicalDeleteMentions(ids),
                rows.stream().map(ItemGraphViews.MentionView::memoryId).distinct().toList());
    }

    public BatchDeleteResult deleteItemLinks(List<Integer> ids) {
        List<ItemGraphViews.ItemLinkView> rows = itemGraphQueryMapper.findItemLinks(ids);
        return new BatchDeleteResult(
                itemGraphQueryMapper.physicalDeleteItemLinks(ids),
                rows.stream().map(ItemGraphViews.ItemLinkView::memoryId).distinct().toList());
    }

    public BatchDeleteResult deleteCooccurrences(List<Integer> ids) {
        List<ItemGraphViews.CooccurrenceView> rows = itemGraphQueryMapper.findCooccurrences(ids);
        return new BatchDeleteResult(
                itemGraphQueryMapper.physicalDeleteCooccurrences(ids),
                rows.stream().map(ItemGraphViews.CooccurrenceView::memoryId).distinct().toList());
    }
}
