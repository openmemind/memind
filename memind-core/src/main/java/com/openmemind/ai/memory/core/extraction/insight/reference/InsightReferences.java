package com.openmemind.ai.memory.core.extraction.insight.reference;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Insight reference parsing tool
 *
 * <p>Parse and manipulate the {@code [ref:ITEM_ID]} reference tags in the summary,
 * supporting tracing the source item of each summary information.
 *
 */
public final class InsightReferences {

    private InsightReferences() {}

    /** Regular expression for [ref:ITEM_ID] or [ref:id1,id2] */
    private static final Pattern REF_PATTERN = Pattern.compile("\\[ref:([a-zA-Z0-9_,\\-]+)]");

    /** Short ID length */
    private static final int SHORT_ID_LENGTH = 6;

    /**
     * Extract all referenced item IDs from the text
     *
     * @param text Text containing [ref:...] tags
     * @return List of all referenced item IDs (expanded from comma-separated multiple references)
     */
    public static List<String> extractReferences(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }

        var refs = new ArrayList<String>();
        var matcher = REF_PATTERN.matcher(text);
        while (matcher.find()) {
            var ids = matcher.group(1).split(",");
            for (var id : ids) {
                var trimmed = id.strip();
                if (!trimmed.isEmpty()) {
                    refs.add(trimmed);
                }
            }
        }
        return List.copyOf(refs);
    }

    /**
     * Remove all [ref:...] reference tags from the text (for clean display)
     *
     * @param text Text containing [ref:...] tags
     * @return Text after removing references
     */
    public static String stripReferences(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return REF_PATTERN.matcher(text).replaceAll("").strip();
    }

    /**
     * Build a 6-digit short ID for the item ID
     *
     * @param itemId Complete item ID
     * @return 6-digit short ID (take the first 6 digits after removing hyphens)
     */
    public static String buildShortRefId(String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return "";
        }
        var cleaned = itemId.replace("-", "");
        return cleaned.length() > SHORT_ID_LENGTH ? cleaned.substring(0, SHORT_ID_LENGTH) : cleaned;
    }
}
