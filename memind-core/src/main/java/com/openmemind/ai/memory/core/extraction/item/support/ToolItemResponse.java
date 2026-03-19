package com.openmemind.ai.memory.core.extraction.item.support;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * LLM response for tool usage insight extraction.
 *
 * @param content Tool usage insight text (2-4 sentences covering effective patterns, pitfalls, and
 *     recommendations)
 * @param score Binary quality score (1.0 = tool calls produced useful results, 0.0 = poor/failed
 *     results)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ToolItemResponse(String content, double score) {}
