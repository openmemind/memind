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
package com.openmemind.ai.memory.server.service.item;

import com.openmemind.ai.memory.server.domain.common.PageResponse;
import com.openmemind.ai.memory.server.domain.item.query.ItemPageQuery;
import com.openmemind.ai.memory.server.domain.item.view.AdminItemView;
import com.openmemind.ai.memory.server.mapper.item.AdminItemQueryMapper;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;

@Service
public class ItemQueryService {

    private final AdminItemQueryMapper itemQueryMapper;

    public ItemQueryService(AdminItemQueryMapper itemQueryMapper) {
        this.itemQueryMapper = itemQueryMapper;
    }

    public PageResponse<AdminItemView> listItems(ItemPageQuery query) {
        return itemQueryMapper.page(query);
    }

    public AdminItemView getItem(Long itemId) {
        return itemQueryMapper
                .findByBizId(itemId)
                .orElseThrow(() -> new NoSuchElementException("Item not found: " + itemId));
    }
}
