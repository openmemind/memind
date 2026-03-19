package com.openmemind.ai.memory.core.retrieval;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.openmemind.ai.memory.core.data.enums.InsightTier;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Retrieval result (three-layer structure)
 *
 * @param items     Item original text scoring result list (sorted by finalScore in descending order)
 * @param insights  Insight result list (sorted by LLM, no scores)
 * @param rawData   RawData caption aggregation result list (sorted by finalScore in descending order)
 * @param evidences Tier1 key sentences extracted when sufficient; empty list when insufficient
 * @param strategy  Name of the strategy used
 * @param query     Actual query text used (may have been rewritten)
 */
public record RetrievalResult(
        List<ScoredResult> items,
        List<InsightResult> insights,
        List<RawDataResult> rawData,
        List<String> evidences,
        String strategy,
        String query) {

    /** RawData aggregation result */
    public record RawDataResult(
            String rawDataId, String caption, double maxScore, List<String> itemIds) {}

    /** Insight result (no scores, only ID, text, and tier) */
    public record InsightResult(String id, String text, InsightTier tier) {
        /** Backward compatibility: default null when no tier */
        public InsightResult(String id, String text) {
            this(id, text, null);
        }
    }

    /** Empty result */
    public static RetrievalResult empty(String strategy, String query) {
        return new RetrievalResult(List.of(), List.of(), List.of(), List.of(), strategy, query);
    }

    /** Is the result empty */
    @JsonIgnore
    public boolean isEmpty() {
        return (items == null || items.isEmpty())
                && (insights == null || insights.isEmpty())
                && (rawData == null || rawData.isEmpty());
    }

    /**
     * Returns a markdown-formatted string of the retrieval result, suitable for use as LLM prompt context.
     *
     * <p>Sections with no content are omitted. Item dates are formatted as [yyyy-MM-dd] when available.
     */
    @JsonIgnore
    public String formattedResult() {
        String insightSection =
                insights == null || insights.isEmpty()
                        ? ""
                        : "## Insights (aggregated knowledge patterns)\n"
                                + insights.stream()
                                        .map(i -> "- " + i.text())
                                        .collect(Collectors.joining("\n"))
                                + "\n\n";

        String itemSection =
                items == null || items.isEmpty()
                        ? ""
                        : "## Items (individual memory facts)\n"
                                + items.stream()
                                        .map(RetrievalResult::formatItemWithDate)
                                        .map(s -> "- " + s)
                                        .collect(Collectors.joining("\n"))
                                + "\n\n";

        List<RawDataResult> withCaption =
                rawData == null
                        ? List.of()
                        : rawData.stream()
                                .filter(rd -> rd.caption() != null && !rd.caption().isBlank())
                                .toList();
        String captionSection =
                withCaption.isEmpty()
                        ? ""
                        : "## Captions (raw conversation summaries)\n"
                                + withCaption.stream()
                                        .map(rd -> "- " + rd.caption())
                                        .collect(Collectors.joining("\n"))
                                + "\n\n";

        return (insightSection + itemSection + captionSection).trim();
    }

    private static String formatItemWithDate(ScoredResult item) {
        if (item.occurredAt() != null) {
            LocalDate date = item.occurredAt().atZone(ZoneOffset.UTC).toLocalDate();
            return "[" + date + "] " + item.text();
        }
        return item.text();
    }
}
