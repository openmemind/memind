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
package com.openmemind.ai.memory.plugin.jdbc.sqlite;

import com.openmemind.ai.memory.core.buffer.MemoryBuffer;
import com.openmemind.ai.memory.plugin.jdbc.HikariDataSourceFactory;
import com.openmemind.ai.memory.plugin.jdbc.JdbcConnectionProperties;
import com.openmemind.ai.memory.plugin.jdbc.JdbcMemoryAccess;
import com.openmemind.ai.memory.plugin.jdbc.JdbcPluginOptions;
import com.openmemind.ai.memory.plugin.jdbc.internal.DefaultJdbcMemoryAccess;
import com.openmemind.ai.memory.plugin.jdbc.internal.support.JsonCodec;
import com.zaxxer.hikari.HikariDataSource;
import java.util.List;
import java.util.Objects;
import javax.sql.DataSource;
import tools.jackson.databind.ObjectMapper;

public final class SqliteJdbcPlugin {

    private SqliteJdbcPlugin() {}

    public static JdbcMemoryAccess create(String path) {
        return create(
                JdbcConnectionProperties.sqlite("jdbc:sqlite:" + requireText(path, "path")),
                JdbcPluginOptions.defaults());
    }

    public static JdbcMemoryAccess create(JdbcConnectionProperties properties) {
        return create(properties, JdbcPluginOptions.defaults());
    }

    public static JdbcMemoryAccess create(
            JdbcConnectionProperties properties, JdbcPluginOptions options) {
        HikariDataSource dataSource =
                HikariDataSourceFactory.create(Objects.requireNonNull(properties, "properties"));
        return create(dataSource, Objects.requireNonNull(options, "options"));
    }

    public static JdbcMemoryAccess create(DataSource dataSource) {
        return create(dataSource, JdbcPluginOptions.externalDataSource());
    }

    public static JdbcMemoryAccess create(DataSource dataSource, JdbcPluginOptions options) {
        Objects.requireNonNull(dataSource, "dataSource");
        Objects.requireNonNull(options, "options");
        ObjectMapper objectMapper =
                options.objectMapper() != null
                        ? options.objectMapper()
                        : JsonCodec.createDefaultObjectMapper();
        var store =
                new SqliteMemoryStore(
                        dataSource,
                        options.resourceStore(),
                        objectMapper,
                        options.createIfNotExist());
        var insightBufferStore = new SqliteInsightBuffer(dataSource, options.createIfNotExist());
        var conversationBufferAccessor =
                new SqliteConversationBufferAccessor(dataSource, options.createIfNotExist());
        var bubbleTrackerStore =
                new SqliteBubbleTrackerStore(dataSource, options.createIfNotExist());
        var memoryBuffer =
                MemoryBuffer.of(
                        insightBufferStore,
                        new SqliteConversationBuffer(conversationBufferAccessor),
                        new SqliteRecentConversationBuffer(conversationBufferAccessor));
        return new DefaultJdbcMemoryAccess(
                store,
                memoryBuffer,
                new SqliteMemoryTextSearch(dataSource, options.createIfNotExist()),
                bubbleTrackerStore,
                dataSource,
                options.closeDataSourceOnClose()
                        ? List.of(
                                () -> {
                                    if (dataSource instanceof AutoCloseable ds) {
                                        ds.close();
                                    }
                                })
                        : List.of());
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
