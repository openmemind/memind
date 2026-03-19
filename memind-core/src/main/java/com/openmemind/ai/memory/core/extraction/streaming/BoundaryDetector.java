package com.openmemind.ai.memory.core.extraction.streaming;

import com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * Boundary Detector
 *
 * <p>Determines whether the current message buffer should be sealed, triggering memory extraction.
 *
 */
public interface BoundaryDetector {

    /**
     * Determines whether the current buffer should be sealed
     *
     * @param buffer Current message buffer
     * @param context Detection context (including the summary of the previous topic, time interval, etc.)
     * @return Boundary detection decision
     */
    Mono<BoundaryDecision> shouldSeal(List<Message> buffer, BoundaryDetectionContext context);
}
