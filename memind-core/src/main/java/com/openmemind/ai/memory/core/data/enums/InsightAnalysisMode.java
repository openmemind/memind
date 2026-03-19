package com.openmemind.ai.memory.core.data.enums;

/**
 * Insight Analysis Mode
 *
 * <p>Defines the pipeline types of InsightType:
 * <ul>
 *   <li>{@link #BRANCH} — Regular process: MemoryItem → LEAF → BRANCH
 *   <li>{@link #ROOT} — Comprehensive process: All BRANCH → ROOT
 * </ul>
 *
 */
public enum InsightAnalysisMode {

    /** Regular process: MemoryItem → LEAF → BRANCH */
    BRANCH,

    /** Comprehensive process: All BRANCH → ROOT */
    ROOT
}
