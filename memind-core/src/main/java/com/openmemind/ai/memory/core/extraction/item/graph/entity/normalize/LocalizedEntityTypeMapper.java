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
package com.openmemind.ai.memory.core.extraction.item.graph.entity.normalize;

import com.openmemind.ai.memory.core.store.graph.GraphEntityType;
import java.util.Locale;
import java.util.Map;

/**
 * Default localized entity type mapper for the built-in English and Chinese packs.
 */
public final class LocalizedEntityTypeMapper implements EntityTypeMapper {

    private static final Map<String, GraphEntityType> TYPE_BY_LABEL =
            Map.ofEntries(
                    Map.entry("person", GraphEntityType.PERSON),
                    Map.entry("organization", GraphEntityType.ORGANIZATION),
                    Map.entry("place", GraphEntityType.PLACE),
                    Map.entry("object", GraphEntityType.OBJECT),
                    Map.entry("concept", GraphEntityType.CONCEPT),
                    Map.entry("special", GraphEntityType.SPECIAL),
                    Map.entry("人物", GraphEntityType.PERSON),
                    Map.entry("人", GraphEntityType.PERSON),
                    Map.entry("公司", GraphEntityType.ORGANIZATION),
                    Map.entry("组织", GraphEntityType.ORGANIZATION),
                    Map.entry("地点", GraphEntityType.PLACE),
                    Map.entry("地方", GraphEntityType.PLACE),
                    Map.entry("物品", GraphEntityType.OBJECT),
                    Map.entry("概念", GraphEntityType.CONCEPT),
                    Map.entry("特殊", GraphEntityType.SPECIAL));

    @Override
    public GraphEntityType map(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            return GraphEntityType.OTHER;
        }
        return TYPE_BY_LABEL.getOrDefault(
                rawType.strip().toLowerCase(Locale.ROOT), GraphEntityType.OTHER);
    }
}
