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
package com.openmemind.ai.memory.plugin.jdbc;

import com.openmemind.ai.memory.core.buffer.MemoryBuffer;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.textsearch.MemoryTextSearch;
import javax.sql.DataSource;

/**
 * JDBC plugin outputs ready to be plugged into {@code Memory.builder()}.
 */
public interface JdbcMemoryAccess extends AutoCloseable {

    MemoryStore store();

    MemoryBuffer buffer();

    MemoryTextSearch textSearch();

    DataSource dataSource();

    @Override
    default void close() throws Exception {}
}
