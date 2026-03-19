package com.openmemind.ai.memory.plugin.store.mybatis.sqlite.textsearch;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.textsearch.MemoryTextSearch;
import com.openmemind.ai.memory.core.textsearch.TextSearchResult;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Text search based on SQLite FTS5 trigram
 *
 * <p>FTS5 rank column is BM25 score (negative, the closer to 0, the more relevant). Score normalization: score = -rank / (1 + (-rank)) → [0, 1)
 *
 */
public class SqliteFulltextTextSearch implements MemoryTextSearch {

    private static final String ITEM_SEARCH_SQL =
            """
            SELECT biz_id, content, rank
            FROM item_fts
            WHERE item_fts MATCH '%s'
              AND memory_id = ?
            ORDER BY rank
            LIMIT ?
            """;

    private static final String INSIGHT_SEARCH_SQL =
            """
            SELECT biz_id, content, rank
            FROM insight_fts
            WHERE insight_fts MATCH '%s'
              AND memory_id = ?
            ORDER BY rank
            LIMIT ?
            """;

    private static final String RAW_DATA_SEARCH_SQL =
            """
            SELECT biz_id, content, rank
            FROM raw_data_fts
            WHERE raw_data_fts MATCH '%s'
              AND memory_id = ?
            ORDER BY rank
            LIMIT ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public SqliteFulltextTextSearch(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    @Override
    public Mono<List<TextSearchResult>> search(
            MemoryId memoryId, String query, int topK, SearchTarget target) {
        if (query == null || query.isBlank()) {
            return Mono.just(List.of());
        }
        String memId = memoryId.toIdentifier();
        return Mono.fromCallable(() -> executeSearch(memId, query, topK, target))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private List<TextSearchResult> executeSearch(
            String memId, String query, int topK, SearchTarget target) {
        String sqlTemplate =
                switch (target) {
                    case ITEM -> ITEM_SEARCH_SQL;
                    case INSIGHT -> INSIGHT_SEARCH_SQL;
                    case RAW_DATA -> RAW_DATA_SEARCH_SQL;
                };
        // FTS5 phrase: escape FTS5 double quotes, escape SQL single quotes, wrap in double quotes
        String ftsPhrase = "\"" + query.replace("\"", "\"\"").replace("'", "''") + "\"";
        String sql = String.format(sqlTemplate, ftsPhrase);
        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> {
                    String docId = String.valueOf(rs.getLong("biz_id"));
                    String text = rs.getString("content");
                    double rank = rs.getDouble("rank"); // negative BM25 score
                    double normalizedScore = -rank / (1.0 + (-rank));
                    return new TextSearchResult(docId, text, normalizedScore);
                },
                memId,
                topK);
    }
}
