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
package com.openmemind.ai.memory.plugin.store.mybatis.textsearch;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.textsearch.MemoryTextSearch;
import com.openmemind.ai.memory.core.textsearch.TextSearchResult;
import java.lang.reflect.Constructor;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class DialectMemoryTextSearchTest {

    private static final MemoryId MEMORY_ID = () -> "memory-1";

    @ParameterizedTest(name = "{0} returns empty results for blank queries")
    @MethodSource("implementations")
    void returnsEmptyResultsForBlankQueries(String className) throws Exception {
        TrackingJdbcTemplate jdbcTemplate = new TrackingJdbcTemplate();
        MemoryTextSearch textSearch = newTextSearch(className, jdbcTemplate);

        List<TextSearchResult> results =
                textSearch.search(MEMORY_ID, "   ", 5, MemoryTextSearch.SearchTarget.ITEM).block();

        assertThat(results).isEmpty();
        assertThat(jdbcTemplate.wasInvoked()).isFalse();
    }

    private static Stream<String> implementations() {
        return Stream.of(
                "com.openmemind.ai.memory.plugin.store.mybatis.textsearch.mysql.MysqlFulltextTextSearch",
                "com.openmemind.ai.memory.plugin.store.mybatis.textsearch.sqlite.SqliteFtsTextSearch",
                "com.openmemind.ai.memory.plugin.store.mybatis.textsearch.postgresql.PostgresqlTrigramTextSearch");
    }

    private MemoryTextSearch newTextSearch(String className, JdbcTemplate jdbcTemplate)
            throws Exception {
        Class<?> textSearchType = Class.forName(className);
        Constructor<?> constructor = textSearchType.getConstructor(JdbcTemplate.class);
        return (MemoryTextSearch) constructor.newInstance(jdbcTemplate);
    }

    private static final class TrackingJdbcTemplate extends JdbcTemplate {

        private boolean invoked;

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            invoked = true;
            throw new AssertionError("Blank queries should not hit the database");
        }

        boolean wasInvoked() {
            return invoked;
        }
    }
}
