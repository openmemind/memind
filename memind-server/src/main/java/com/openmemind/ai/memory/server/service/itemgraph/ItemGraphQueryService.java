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

import com.openmemind.ai.memory.server.domain.common.PageResponse;
import com.openmemind.ai.memory.server.domain.itemgraph.query.ItemGraphPageQueries;
import com.openmemind.ai.memory.server.domain.itemgraph.view.GraphEntityDetailView;
import com.openmemind.ai.memory.server.domain.itemgraph.view.ItemGraphViews;
import com.openmemind.ai.memory.server.mapper.itemgraph.AdminItemGraphQueryMapper;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;

@Service
public class ItemGraphQueryService {

    private final AdminItemGraphQueryMapper itemGraphQueryMapper;

    public ItemGraphQueryService(AdminItemGraphQueryMapper itemGraphQueryMapper) {
        this.itemGraphQueryMapper = itemGraphQueryMapper;
    }

    public ItemGraphViews.SummaryView summary(String memoryId) {
        return itemGraphQueryMapper.summary(memoryId);
    }

    public PageResponse<ItemGraphViews.EntityView> listEntities(
            ItemGraphPageQueries.EntityPageQuery query) {
        return itemGraphQueryMapper.pageEntities(query);
    }

    public GraphEntityDetailView getEntity(Integer id) {
        return itemGraphQueryMapper
                .findEntity(id)
                .orElseThrow(() -> new NoSuchElementException("Graph entity not found: " + id));
    }

    public PageResponse<ItemGraphViews.AliasView> listAliases(
            ItemGraphPageQueries.AliasPageQuery query) {
        return itemGraphQueryMapper.pageAliases(query);
    }

    public PageResponse<ItemGraphViews.MentionView> listMentions(
            ItemGraphPageQueries.MentionPageQuery query) {
        return itemGraphQueryMapper.pageMentions(query);
    }

    public PageResponse<ItemGraphViews.ItemLinkView> listItemLinks(
            ItemGraphPageQueries.ItemLinkPageQuery query) {
        return itemGraphQueryMapper.pageItemLinks(query);
    }

    public PageResponse<ItemGraphViews.CooccurrenceView> listCooccurrences(
            ItemGraphPageQueries.CooccurrencePageQuery query) {
        return itemGraphQueryMapper.pageCooccurrences(query);
    }

    public PageResponse<ItemGraphViews.BatchView> listBatches(
            ItemGraphPageQueries.BatchPageQuery query) {
        return itemGraphQueryMapper.pageBatches(query);
    }
}
