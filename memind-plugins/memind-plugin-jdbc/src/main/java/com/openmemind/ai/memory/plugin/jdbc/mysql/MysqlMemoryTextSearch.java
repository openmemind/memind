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
package com.openmemind.ai.memory.plugin.jdbc.mysql;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.textsearch.MemoryTextSearch;
import com.openmemind.ai.memory.core.textsearch.TextSearchResult;
import com.openmemind.ai.memory.plugin.jdbc.internal.schema.TextSearchSchemaBootstrap;
import com.openmemind.ai.memory.plugin.jdbc.internal.support.JdbcExecutor;
import java.util.List;
import java.util.Objects;
import javax.sql.DataSource;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class MysqlMemoryTextSearch implements MemoryTextSearch {

    private static final String ITEM_SEARCH_SQL =
            """
            SELECT biz_id, content,
                   MATCH(content) AGAINST(? IN NATURAL LANGUAGE MODE) AS raw_score
            FROM memory_item
            WHERE memory_id = ?
              AND deleted = 0
              AND MATCH(content) AGAINST(? IN NATURAL LANGUAGE MODE) > 0
            ORDER BY raw_score DESC
            LIMIT ?
            """;

    private static final String INSIGHT_SEARCH_SQL =
            """
            SELECT biz_id, content,
                   MATCH(content) AGAINST(? IN NATURAL LANGUAGE MODE) AS raw_score
            FROM memory_insight
            WHERE memory_id = ?
              AND deleted = 0
              AND MATCH(content) AGAINST(? IN NATURAL LANGUAGE MODE) > 0
            ORDER BY raw_score DESC
            LIMIT ?
            """;

    private static final String RAW_DATA_SEARCH_SQL =
            """
            SELECT biz_id, segment_content,
                   MATCH(segment_content) AGAINST(? IN NATURAL LANGUAGE MODE) AS raw_score
            FROM memory_raw_data
            WHERE memory_id = ?
              AND deleted = 0
              AND MATCH(segment_content) AGAINST(? IN NATURAL LANGUAGE MODE) > 0
            ORDER BY raw_score DESC
            LIMIT ?
            """;

    private final DataSource dataSource;

    public MysqlMemoryTextSearch(DataSource dataSource) {
        this(dataSource, true);
    }

    public MysqlMemoryTextSearch(DataSource dataSource, boolean createIfNotExist) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        TextSearchSchemaBootstrap.ensureMysql(this.dataSource, createIfNotExist);
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
        return switch (target) {
            case ITEM -> doSearch(ITEM_SEARCH_SQL, "content", memoryId, query, topK, true);
            case INSIGHT -> doSearch(INSIGHT_SEARCH_SQL, "content", memoryId, query, topK, true);
            case RAW_DATA ->
                    doSearch(RAW_DATA_SEARCH_SQL, "segment_content", memoryId, query, topK, false);
        };
    }

    private List<TextSearchResult> doSearch(
            String sql,
            String textColumn,
            String memoryId,
            String query,
            int topK,
            boolean bizIdIsLong) {
        return JdbcExecutor.queryList(
                dataSource,
                sql,
                resultSet -> {
                    String documentId =
                            bizIdIsLong
                                    ? String.valueOf(resultSet.getLong("biz_id"))
                                    : resultSet.getString("biz_id");
                    String text = resultSet.getString(textColumn);
                    double rawScore = resultSet.getDouble("raw_score");
                    double normalizedScore = rawScore / (1.0 + rawScore);
                    return new TextSearchResult(documentId, text, normalizedScore);
                },
                query,
                memoryId,
                query,
                topK);
    }
}
