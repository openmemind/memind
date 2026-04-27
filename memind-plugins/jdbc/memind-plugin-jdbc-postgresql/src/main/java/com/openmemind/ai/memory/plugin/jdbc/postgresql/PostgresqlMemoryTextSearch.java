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
package com.openmemind.ai.memory.plugin.jdbc.postgresql;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.textsearch.MemoryTextSearch;
import com.openmemind.ai.memory.core.textsearch.TextSearchResult;
import com.openmemind.ai.memory.plugin.jdbc.internal.jdbi.JdbiExecutor;
import com.openmemind.ai.memory.plugin.jdbc.internal.schema.TextSearchSchemaBootstrap;
import java.util.List;
import java.util.Objects;
import javax.sql.DataSource;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class PostgresqlMemoryTextSearch implements MemoryTextSearch {

    private static final String ITEM_SEARCH_SQL =
            """
            SELECT CAST(biz_id AS TEXT) AS document_id,
                   content AS document_text,
                   GREATEST(similarity(content, ?), word_similarity(?, content)) AS raw_score
            FROM memory_item
            WHERE memory_id = ?
              AND deleted = FALSE
              AND (content ILIKE ? ESCAPE '\\' OR content % ?)
            ORDER BY raw_score DESC, biz_id ASC
            LIMIT ?
            """;

    private static final String INSIGHT_SEARCH_SQL =
            """
            SELECT CAST(biz_id AS TEXT) AS document_id,
                   COALESCE(content, '') AS document_text,
                   GREATEST(
                       similarity(COALESCE(content, ''), ?),
                       word_similarity(?, COALESCE(content, ''))
                   ) AS raw_score
            FROM memory_insight
            WHERE memory_id = ?
              AND deleted = FALSE
              AND (COALESCE(content, '') ILIKE ? ESCAPE '\\' OR COALESCE(content, '') % ?)
            ORDER BY raw_score DESC, biz_id ASC
            LIMIT ?
            """;

    private static final String RAW_DATA_SEARCH_SQL =
            """
            SELECT biz_id AS document_id,
                   COALESCE(segment ->> 'content', '') AS document_text,
                   GREATEST(
                       similarity(COALESCE(segment ->> 'content', ''), ?),
                       word_similarity(?, COALESCE(segment ->> 'content', ''))
                   ) AS raw_score
            FROM memory_raw_data
            WHERE memory_id = ?
              AND deleted = FALSE
              AND (
                  COALESCE(segment ->> 'content', '') ILIKE ? ESCAPE '\\'
                  OR COALESCE(segment ->> 'content', '') % ?
              )
            ORDER BY raw_score DESC, biz_id ASC
            LIMIT ?
            """;

    private final DataSource dataSource;

    public PostgresqlMemoryTextSearch(DataSource dataSource) {
        this(dataSource, true);
    }

    public PostgresqlMemoryTextSearch(DataSource dataSource, boolean createIfNotExist) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        TextSearchSchemaBootstrap.ensurePostgresql(this.dataSource, createIfNotExist);
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
            case ITEM -> doSearch(ITEM_SEARCH_SQL, memoryId, query, topK);
            case INSIGHT -> doSearch(INSIGHT_SEARCH_SQL, memoryId, query, topK);
            case RAW_DATA -> doSearch(RAW_DATA_SEARCH_SQL, memoryId, query, topK);
        };
    }

    private List<TextSearchResult> doSearch(String sql, String memoryId, String query, int topK) {
        String likePattern = likePattern(query);
        return JdbiExecutor.queryList(
                dataSource,
                sql,
                resultSet ->
                        new TextSearchResult(
                                resultSet.getString("document_id"),
                                resultSet.getString("document_text"),
                                resultSet.getDouble("raw_score")),
                query,
                query,
                memoryId,
                likePattern,
                query,
                topK);
    }

    private String likePattern(String query) {
        return "%" + query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }
}
