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

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.buffer.MemoryBuffer;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.store.graph.NoOpGraphOperations;
import com.openmemind.ai.memory.core.store.graph.NoOpItemGraphCommitOperations;
import com.openmemind.ai.memory.core.store.thread.NoOpThreadEnrichmentInputStore;
import com.openmemind.ai.memory.core.store.thread.NoOpThreadProjectionStore;
import com.openmemind.ai.memory.plugin.jdbc.HikariDataSourceFactory;
import com.openmemind.ai.memory.plugin.jdbc.JdbcConnectionProperties;
import com.openmemind.ai.memory.plugin.jdbc.JdbcMemoryAccess;
import com.openmemind.ai.memory.plugin.jdbc.JdbcPluginOptions;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteJdbcPluginTest {

    @Test
    void createWithPathBuildsHikariBackedAccessAndOwnsThePool(@TempDir Path tempDir)
            throws Exception {
        JdbcMemoryAccess jdbc = SqliteJdbcPlugin.create(tempDir.resolve("memind.db").toString());

        assertThat(jdbc.dataSource()).isInstanceOf(HikariDataSource.class);
        HikariDataSource dataSource = (HikariDataSource) jdbc.dataSource();
        assertThat(dataSource.getJdbcUrl()).endsWith("memind.db");
        assertThat(dataSource.getDriverClassName()).isEqualTo("org.sqlite.JDBC");
        assertThat(dataSource.getMaximumPoolSize()).isEqualTo(1);
        assertThat(jdbc.store()).isInstanceOf(MemoryStore.class);
        assertThat(jdbc.buffer()).isInstanceOf(MemoryBuffer.class);
        assertThat(jdbc.store().rawDataOperations()).isInstanceOf(SqliteMemoryStore.class);
        assertThat(jdbc.store().graphOperations()).isNotInstanceOf(NoOpGraphOperations.class);
        assertThat(jdbc.store().graphOperationsCapabilities().supportsBoundedEntityKeyLookup())
                .isTrue();
        assertThat(jdbc.store().itemGraphCommitOperations())
                .isNotSameAs(NoOpItemGraphCommitOperations.INSTANCE);
        assertThat(jdbc.store().threadOperations()).isNotSameAs(NoOpThreadProjectionStore.INSTANCE);
        assertThat(jdbc.store().threadEnrichmentInputStore())
                .isNotSameAs(NoOpThreadEnrichmentInputStore.INSTANCE);
        assertThat(jdbc.buffer().insightBuffer()).isInstanceOf(SqliteInsightBuffer.class);
        assertThat(jdbc.buffer().pendingConversationBuffer())
                .isInstanceOf(SqliteConversationBuffer.class);
        assertThat(jdbc.buffer().recentConversationBuffer())
                .isInstanceOf(SqliteRecentConversationBuffer.class);
        assertThat(jdbc.textSearch()).isInstanceOf(SqliteMemoryTextSearch.class);
        assertThat(jdbc.bubbleTrackerStore()).isInstanceOf(SqliteBubbleTrackerStore.class);

        jdbc.close();

        assertThat(dataSource.isClosed()).isTrue();
    }

    @Test
    void createWithExternalHikariDoesNotClosePoolByDefault(@TempDir Path tempDir) throws Exception {
        HikariDataSource dataSource =
                HikariDataSourceFactory.create(
                        JdbcConnectionProperties.sqlite(
                                "jdbc:sqlite:" + tempDir.resolve("external.db")));

        JdbcMemoryAccess jdbc = SqliteJdbcPlugin.create(dataSource);
        jdbc.close();

        assertThat(dataSource.isClosed()).isFalse();
        dataSource.close();
    }

    @Test
    void createWithExternalHikariCanOwnPoolWhenRequested(@TempDir Path tempDir) throws Exception {
        HikariDataSource dataSource =
                HikariDataSourceFactory.create(
                        JdbcConnectionProperties.sqlite(
                                "jdbc:sqlite:" + tempDir.resolve("owned-external.db")));

        JdbcMemoryAccess jdbc =
                SqliteJdbcPlugin.create(
                        dataSource,
                        JdbcPluginOptions.externalDataSource().withCloseDataSourceOnClose(true));
        jdbc.close();

        assertThat(dataSource.isClosed()).isTrue();
    }
}
