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
package com.openmemind.ai.memory.plugin.store.mybatis.sqlite;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SQLite storage configuration properties.
 *
 * <pre>
 * memind:
 *   store:
 *     sqlite:
 *       path: ./memind.db   # File path; fill :memory: for in-memory mode (data loss on restart)
 * </pre>
 *
 */
@ConfigurationProperties("memind.store.sqlite")
public record MemorySqliteProperties(String path) {

    public MemorySqliteProperties {
        if (path == null || path.isBlank()) {
            path = "./memind.db";
        }
    }
}
