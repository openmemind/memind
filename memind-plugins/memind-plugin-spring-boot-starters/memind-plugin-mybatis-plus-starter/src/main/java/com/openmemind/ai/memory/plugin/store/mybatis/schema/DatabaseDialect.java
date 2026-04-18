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
package com.openmemind.ai.memory.plugin.store.mybatis.schema;

import java.util.List;

public enum DatabaseDialect {
    SQLITE(
            "db/migration/sqlite/V1__init_store.sql",
            "db/migration/sqlite/V2__init_text_search.sql",
            "db/migration/sqlite/V3__multimodal.sql",
            "db/migration/sqlite/V4__bubble_state.sql",
            "db/migration/sqlite/V5__item_temporal_fields.sql",
            "db/migration/sqlite/V6__graph_store.sql",
            "db/migration/sqlite/V7__memory_thread.sql"),
    MYSQL(
            "db/migration/mysql/V1__init_store.sql",
            "db/migration/mysql/V2__init_text_search.sql",
            "db/migration/mysql/V3__multimodal.sql",
            "db/migration/mysql/V4__bubble_state.sql",
            "db/migration/mysql/V5__item_temporal_fields.sql",
            "db/migration/mysql/V6__graph_store.sql",
            "db/migration/mysql/V7__memory_thread.sql"),
    POSTGRESQL(
            "db/migration/postgresql/V1__init_store.sql",
            "db/migration/postgresql/V2__init_text_search.sql",
            "db/migration/postgresql/V3__multimodal.sql",
            "db/migration/postgresql/V4__bubble_state.sql",
            "db/migration/postgresql/V5__item_temporal_fields.sql",
            "db/migration/postgresql/V6__graph_store.sql",
            "db/migration/postgresql/V7__memory_thread.sql");

    private final List<String> scriptPaths;

    DatabaseDialect(String... scriptPaths) {
        this.scriptPaths = List.of(scriptPaths);
    }

    public List<String> scriptPaths() {
        return scriptPaths;
    }
}
