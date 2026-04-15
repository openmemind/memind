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

import java.nio.file.Path;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

class SqliteBubbleTrackerStoreTest {

    @TempDir Path tempDir;

    private DataSource dataSource;
    private SqliteBubbleTrackerStore store;

    @BeforeEach
    void setUp() {
        var sqlite = new SQLiteDataSource();
        sqlite.setUrl("jdbc:sqlite:" + tempDir.resolve("bubble.db"));
        dataSource = sqlite;
        store = new SqliteBubbleTrackerStore(dataSource, true);
    }

    @Test
    void incrementAndGetPersistsAcrossInstancesForBranchAndRootKeys() {
        assertThat(store.incrementAndGet("memory-1::identity", 2)).isEqualTo(2);
        assertThat(store.incrementAndGet("memory-1::identity", 1)).isEqualTo(3);
        assertThat(store.incrementAndGet("memory-1::root::profile", 1)).isEqualTo(1);

        var reopened = new SqliteBubbleTrackerStore(dataSource, true);

        assertThat(reopened.getDirtyCount("memory-1::identity")).isEqualTo(3);
        assertThat(reopened.getDirtyCount("memory-1::root::profile")).isEqualTo(1);
        reopened.reset("memory-1::identity");
        reopened.reset("memory-1::root::profile");
        assertThat(reopened.getDirtyCount("memory-1::identity")).isZero();
        assertThat(reopened.getDirtyCount("memory-1::root::profile")).isZero();
    }
}
