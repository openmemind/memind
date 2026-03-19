package com.openmemind.ai.memory.core.retrieval.deep;

/**
 * Extended query with type annotation
 *
 * <p>Each extended query carries {@link QueryType}, and downstream decides whether to route to BM25 or vector search based on the type.
 *
 * @param queryType Query type, determines the retrieval channel
 * @param text Query text
 */
public record ExpandedQuery(QueryType queryType, String text) {

    /**
     * Query type enumeration
     *
     * <p>Determines routing to different retrieval channels based on the nature of the query
     */
    public enum QueryType {
        /** Keyword query — only BM25 */
        LEX,
        /** Semantic rewrite query — only vector search */
        VEC,
        /** Hypothetical document query — only vector search */
        HYDE
    }
}
