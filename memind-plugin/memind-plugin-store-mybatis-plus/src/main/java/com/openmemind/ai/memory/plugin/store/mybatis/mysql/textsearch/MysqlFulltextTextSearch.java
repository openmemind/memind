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
package com.openmemind.ai.memory.plugin.store.mybatis.mysql.textsearch;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.textsearch.MemoryTextSearch;
import com.openmemind.ai.memory.core.textsearch.TextSearchResult;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Text search based on MySQL FULLTEXT ngram
 *
 * <p>Using raw content column + NATURAL LANGUAGE MODE (ngram character-level segmentation, naturally supports Chinese).
 * Score normalization: score = rawScore / (1 + rawScore) → [0, 1)
 *
 */
public class MysqlFulltextTextSearch implements MemoryTextSearch {

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

    private final JdbcTemplate jdbcTemplate;

    public MysqlFulltextTextSearch(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    @Override
    public Mono<List<TextSearchResult>> search(
            MemoryId memoryId, String query, int topK, SearchTarget target) {
        String memId = memoryId.toIdentifier();
        return Mono.fromCallable(() -> executeSearch(memId, query, topK, target))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private List<TextSearchResult> executeSearch(
            String memId, String query, int topK, SearchTarget target) {
        return switch (target) {
            case ITEM -> doSearch(ITEM_SEARCH_SQL, "content", memId, query, topK, true);
            case INSIGHT -> doSearch(INSIGHT_SEARCH_SQL, "content", memId, query, topK, true);
            case RAW_DATA ->
                    doSearch(RAW_DATA_SEARCH_SQL, "segment_content", memId, query, topK, false);
        };
    }

    private List<TextSearchResult> doSearch(
            String sql,
            String textColumn,
            String memId,
            String query,
            int topK,
            boolean bizIdIsLong) {
        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> {
                    String docId =
                            bizIdIsLong
                                    ? String.valueOf(rs.getLong("biz_id"))
                                    : rs.getString("biz_id");
                    String text = rs.getString(textColumn);
                    double rawScore = rs.getDouble("raw_score");
                    double normalizedScore = rawScore / (1.0 + rawScore);
                    return new TextSearchResult(docId, text, normalizedScore);
                },
                query,
                memId,
                query,
                topK);
    }
}
