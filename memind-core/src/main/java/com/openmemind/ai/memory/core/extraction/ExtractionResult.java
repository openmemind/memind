package com.openmemind.ai.memory.core.extraction;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.extraction.result.InsightResult;
import com.openmemind.ai.memory.core.extraction.result.MemoryItemResult;
import com.openmemind.ai.memory.core.extraction.result.RawDataResult;
import java.time.Duration;

/**
 * Memory extraction result
 *
 * @param memoryId Memory identifier
 * @param rawDataResult RawData processing result
 * @param memoryItemResult MemoryItem extraction result
 * @param insightResult Insight generation result
 * @param status Extraction status
 * @param duration Total time taken
 * @param errorMessage Error message (when failed)
 * @param insightPending Whether Insight has been submitted for asynchronous scheduling but not yet completed
 */
public record ExtractionResult(
        MemoryId memoryId,
        RawDataResult rawDataResult,
        MemoryItemResult memoryItemResult,
        InsightResult insightResult,
        ExtractionStatus status,
        Duration duration,
        String errorMessage,
        boolean insightPending) {

    /**
     * Create success result
     */
    public static ExtractionResult success(
            MemoryId memoryId,
            RawDataResult rawDataResult,
            MemoryItemResult memoryItemResult,
            InsightResult insightResult,
            Duration duration) {
        return new ExtractionResult(
                memoryId,
                rawDataResult,
                memoryItemResult,
                insightResult,
                ExtractionStatus.SUCCESS,
                duration,
                null,
                false);
    }

    /**
     * Create success result (Insight asynchronous pending)
     */
    public static ExtractionResult successWithPendingInsight(
            MemoryId memoryId,
            RawDataResult rawDataResult,
            MemoryItemResult memoryItemResult,
            Duration duration) {
        return new ExtractionResult(
                memoryId,
                rawDataResult,
                memoryItemResult,
                InsightResult.empty(),
                ExtractionStatus.SUCCESS,
                duration,
                null,
                true);
    }

    /**
     * Create failure result
     */
    public static ExtractionResult failed(
            MemoryId memoryId, Duration duration, String errorMessage) {
        return new ExtractionResult(
                memoryId, null, null, null, ExtractionStatus.FAILED, duration, errorMessage, false);
    }

    /**
     * Create partial success result
     */
    public static ExtractionResult partialSuccess(
            MemoryId memoryId,
            RawDataResult rawDataResult,
            MemoryItemResult memoryItemResult,
            InsightResult insightResult,
            Duration duration,
            String errorMessage) {
        return new ExtractionResult(
                memoryId,
                rawDataResult,
                memoryItemResult,
                insightResult,
                ExtractionStatus.PARTIAL_SUCCESS,
                duration,
                errorMessage,
                false);
    }

    /**
     * Is success
     */
    public boolean isSuccess() {
        return status == ExtractionStatus.SUCCESS;
    }

    /**
     * Is failed
     */
    public boolean isFailed() {
        return status == ExtractionStatus.FAILED;
    }

    /**
     * Total number of memory items
     */
    public int totalMemoryItems() {
        return memoryItemResult != null ? memoryItemResult.newCount() : 0;
    }

    /**
     * Total number of insights
     */
    public int totalInsights() {
        return insightResult != null ? insightResult.totalCount() : 0;
    }
}
