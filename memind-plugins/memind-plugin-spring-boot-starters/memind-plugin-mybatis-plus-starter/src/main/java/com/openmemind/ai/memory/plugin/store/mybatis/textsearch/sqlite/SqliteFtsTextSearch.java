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
package com.openmemind.ai.memory.plugin.store.mybatis.textsearch.sqlite;

import com.openmemind.ai.memory.core.textsearch.TextSearchResult;
import com.openmemind.ai.memory.plugin.store.mybatis.textsearch.AbstractJdbcMemoryTextSearch;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

public class SqliteFtsTextSearch extends AbstractJdbcMemoryTextSearch {

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

    public SqliteFtsTextSearch(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }

    @Override
    protected List<TextSearchResult> executeSearch(
            String memoryId, String query, int topK, SearchTarget target) {
        String sqlTemplate =
                switch (target) {
                    case ITEM -> ITEM_SEARCH_SQL;
                    case INSIGHT -> INSIGHT_SEARCH_SQL;
                    case RAW_DATA -> RAW_DATA_SEARCH_SQL;
                };
        String ftsPhrase = "\"" + query.replace("\"", "\"\"").replace("'", "''") + "\"";
        String sql = sqlTemplate.formatted(ftsPhrase);
        return jdbcTemplate.query(
                sql,
                (resultSet, rowNum) -> {
                    String documentId = resultSet.getString("biz_id");
                    String text = resultSet.getString("content");
                    double rank = resultSet.getDouble("rank");
                    double normalizedScore = -rank / (1.0 + (-rank));
                    return new TextSearchResult(documentId, text, normalizedScore);
                },
                memoryId,
                topK);
    }
}
