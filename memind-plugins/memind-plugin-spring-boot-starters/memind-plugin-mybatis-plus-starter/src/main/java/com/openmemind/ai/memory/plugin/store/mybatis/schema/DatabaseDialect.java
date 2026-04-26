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
    SQLITE("db/migration/sqlite/V1__init.sql"),
    MYSQL("db/migration/mysql/V1__init.sql"),
    POSTGRESQL("db/migration/postgresql/V1__init.sql");

    private final List<String> scriptPaths;

    DatabaseDialect(String... scriptPaths) {
        this.scriptPaths = List.of(scriptPaths);
    }

    public List<String> scriptPaths() {
        return scriptPaths;
    }
}
