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

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.textsearch.MemoryTextSearch;
import com.openmemind.ai.memory.core.textsearch.TextSearchResult;
import com.openmemind.ai.memory.plugin.jdbc.internal.jdbi.JdbiFactory;
import com.openmemind.ai.memory.plugin.jdbc.internal.schema.TextSearchSchemaBootstrap;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import javax.sql.DataSource;
import org.jdbi.v3.core.Jdbi;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class SqliteMemoryTextSearch implements MemoryTextSearch {

    private static final String ITEM_SEARCH_SQL =
            """
            SELECT item_fts.biz_id, item_fts.content, item_fts.rank
            FROM item_fts
            JOIN memory_item ON memory_item.id = item_fts.rowid
            WHERE item_fts MATCH '%s'
              AND item_fts.memory_id = ?
              AND memory_item.deleted = 0
            ORDER BY rank
            LIMIT ?
            """;

    private static final String INSIGHT_SEARCH_SQL =
            """
            SELECT insight_fts.biz_id, insight_fts.content, insight_fts.rank
            FROM insight_fts
            JOIN memory_insight ON memory_insight.id = insight_fts.rowid
            WHERE insight_fts MATCH '%s'
              AND insight_fts.memory_id = ?
              AND memory_insight.deleted = 0
            ORDER BY rank
            LIMIT ?
            """;

    private static final String RAW_DATA_SEARCH_SQL =
            """
            SELECT raw_data_fts.biz_id, raw_data_fts.content, raw_data_fts.rank
            FROM raw_data_fts
            JOIN memory_raw_data ON memory_raw_data.id = raw_data_fts.rowid
            WHERE raw_data_fts MATCH '%s'
              AND raw_data_fts.memory_id = ?
              AND memory_raw_data.deleted = 0
            ORDER BY rank
            LIMIT ?
            """;

    private final Jdbi jdbi;

    public SqliteMemoryTextSearch(DataSource dataSource) {
        this(dataSource, true);
    }

    public SqliteMemoryTextSearch(DataSource dataSource, boolean createIfNotExist) {
        DataSource checkedDataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.jdbi = JdbiFactory.create(checkedDataSource);
        TextSearchSchemaBootstrap.ensureSqlite(checkedDataSource, createIfNotExist);
    }

    @Override
    public Mono<List<TextSearchResult>> search(
            MemoryId memoryId, String query, int topK, SearchTarget target) {
        if (query == null || query.isBlank()) {
            return Mono.just(List.of());
        }
        return Mono.fromCallable(() -> executeSearch(memoryId.toIdentifier(), query, topK, target))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private List<TextSearchResult> executeSearch(
            String memoryId, String query, int topK, SearchTarget target) {
        String sqlTemplate =
                switch (target) {
                    case ITEM -> ITEM_SEARCH_SQL;
                    case INSIGHT -> INSIGHT_SEARCH_SQL;
                    case RAW_DATA -> RAW_DATA_SEARCH_SQL;
                };
        String ftsPhrase = "\"" + query.replace("\"", "\"\"").replace("'", "''") + "\"";
        String sql = sqlTemplate.formatted(ftsPhrase);
        return queryList(
                sql,
                rs -> {
                    String documentId = rs.getString("biz_id");
                    String text = rs.getString("content");
                    double rank = rs.getDouble("rank");
                    double normalizedScore = -rank / (1.0 + (-rank));
                    return new TextSearchResult(documentId, text, normalizedScore);
                },
                memoryId,
                topK);
    }

    private <T> List<T> queryList(String sql, ResultSetMapper<T> mapper, Object... params) {
        return jdbi.withHandle(
                handle -> {
                    var query = handle.createQuery(sql);
                    for (int i = 0; i < params.length; i++) {
                        query.bind(i, params[i]);
                    }
                    return query.map((resultSet, context) -> mapper.map(resultSet)).list();
                });
    }

    @FunctionalInterface
    private interface ResultSetMapper<T> {
        T map(ResultSet resultSet) throws SQLException;
    }
}
