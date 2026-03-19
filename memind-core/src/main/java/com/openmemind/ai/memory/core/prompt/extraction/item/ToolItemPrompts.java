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
package com.openmemind.ai.memory.core.prompt.extraction.item;

import com.openmemind.ai.memory.core.prompt.PromptTemplate;

/**
 * Tool usage insight extraction prompt builder.
 *
 * <p>Generates a tool usage insight from one or more tool call records for the same tool. When
 * historical item content is provided, the LLM incorporates existing knowledge and updates it with
 * new observations.
 *
 * <p>Returns a {@link PromptTemplate} so the caller can defer language injection to {@code
 * render(language)}.
 */
public final class ToolItemPrompts {

    private static final int MAX_IO_LENGTH = 2000;

    private static final String SYSTEM_PROMPT =
            """
            You are a tool execution analyst. Your task is to analyze tool call records for \
            a specific tool and produce a concise usage insight.

            This insight will be stored as a memory item for future retrieval. When an agent \
            needs to use this tool again, it can retrieve this insight to understand effective \
            parameter patterns, common pitfalls, and practical recommendations.

            Return ONLY a JSON object. No extra text, no markdown fences.

            # What to Extract

            ## From Input Parameters: Intent & Patterns
            - What is this tool typically used for?
            - What parameter patterns lead to good results? (specific filters, \
            appropriate scope, correct configuration values)
            - What parameter choices cause problems? (too broad, missing required fields, \
            oversized scope)

            ## From Output: Result Quality
            - Did the calls produce useful results?
            - For successful calls: what made them effective?
            - For failed/poor calls: what was the likely cause?
            - A technically successful call (status=success) may still produce useless \
            results (e.g., "No results found" for a reasonable query). Evaluate RESULT \
            QUALITY independently from execution status.

            ## From Statistics: Performance Context
            - Use the provided statistics (call count, success rate, avg duration) to \
            ground your analysis in data, not speculation.

            # Guidelines

            1. **Narrative, not log**: Write a coherent 2-4 sentence insight. The output \
            should read like practical advice, not a structured report.
            2. **Preserve specifics**: Keep exact parameter values, error messages, tool \
            names, counts, and durations. Do NOT generalize "searched for customer orders" \
            when the data says `customer_id: "C-20483", status: "pending"`.
            3. **Assess effectiveness**: Go beyond "tool X returned Y". State whether the \
            parameters were effective, what patterns work, and what to avoid. Stay grounded \
            in the actual data — no speculation.
            4. **Incremental update**: When historical insight is provided, incorporate it. \
            Confirm patterns that still hold, revise observations that new data contradicts, \
            and add newly discovered patterns. Do NOT simply append — produce a unified, \
            coherent insight.

            # Output Format

            Return valid JSON ONLY. No markdown fences, no surrounding text.

            {
              "content": "2-4 sentence tool usage insight",
              "score": 1.0
            }

            score: 1.0 if overall the tool calls produced useful results, 0.0 if mostly \
            poor/failed results.

            # Examples

            ## Good Example 1: Effective tool usage

            Input: 5 search_orders calls, 4 success, 1 timeout

            Output:
            {
              "content": "search_orders works best with narrow filters combining \
            customer_id + status + date_range within 7 days, consistently returning \
            focused results in ~230ms. Queries without customer_id or with date_range \
            exceeding 30 days risk timeouts (5000ms+). Always provide at least customer_id \
            as a required filter.",
              "score": 1.0
            }

            ## Good Example 2: Mostly failed calls

            Input: 3 web_search calls, all returned irrelevant results

            Output:
            {
              "content": "web_search with single-word queries ('programming', 'database', \
            'framework') consistently returned 50+ generic results with no specific \
            relevance, averaging 1200ms per call. Multi-word queries with qualifiers \
            (e.g., 'Python asyncio tutorial', 'PostgreSQL connection pooling guide') are \
            needed to produce targeted results.",
              "score": 0.0
            }

            ## Good Example 3: Incremental update with historical insight

            Historical insight: "search_orders works best with customer_id + status filters."

            New calls: 3 calls that used date_range parameter, 2 succeeded with short range, \
            1 timed out with 90-day range.

            Output:
            {
              "content": "search_orders works best with customer_id + status filters and \
            benefits from date_range within 7 days for focused results (~230ms). Date ranges \
            exceeding 30 days cause timeouts — a 90-day range query timed out at 5000ms. \
            For historical data, prefer paginated queries or split the date range into \
            smaller windows.",
              "score": 1.0
            }

            ## Bad Example: Too vague

            {
              "content": "search_orders was called several times with mixed results.",
              "score": 0.5
            }

            -> Wrong: Lost all specifics — which parameters? what worked? what failed? \
            Score must be binary (0.0 or 1.0), not 0.5.\
            """;

    private static final String USER_PROMPT_TEMPLATE =
            """
            # Tool: {{tool_name}}

            {{historical_section}}## Statistics

            {{statistics}}

            ## Tool Call Records

            {{records}}\
            """;

    private ToolItemPrompts() {}

    /**
     * Build tool item extraction prompt.
     *
     * @param toolName tool name
     * @param records formatted tool call records text
     * @param statistics formatted statistics text (callCount, successRate, avgDuration)
     * @param historicalContent historical item content for incremental update (null if first time)
     * @return prompt template; call {@code render(language)} to produce the final result
     */
    public static PromptTemplate build(
            String toolName, String records, String statistics, String historicalContent) {

        String historicalSection = "";
        if (historicalContent != null && !historicalContent.isBlank()) {
            historicalSection =
                    """
                    ## Historical Insight (update and incorporate)

                    %s

                    """
                            .formatted(historicalContent);
        }

        return PromptTemplate.builder("tool-item")
                .section("system", SYSTEM_PROMPT)
                .userPrompt(USER_PROMPT_TEMPLATE)
                .variable("tool_name", toolName != null ? toolName : "unknown")
                .variable("historical_section", historicalSection)
                .variable("statistics", statistics != null ? statistics : "(no statistics)")
                .variable("records", records != null ? records : "(no records)")
                .build();
    }

    /** Truncate overly long text */
    public static String truncate(String text, int maxLen) {
        if (text == null || text.isBlank()) {
            return "(empty)";
        }
        if (text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, maxLen) + "... (truncated)";
    }

    /** Max IO length for truncation */
    public static int maxIoLength() {
        return MAX_IO_LENGTH;
    }
}
