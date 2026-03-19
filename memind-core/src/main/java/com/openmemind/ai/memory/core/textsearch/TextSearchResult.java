package com.openmemind.ai.memory.core.textsearch;

/**
 * Text search result
 *
 * @param documentId Document ID
 * @param text       Matched text content
 * @param score      BM25 score
 */
public record TextSearchResult(String documentId, String text, double score) {}
