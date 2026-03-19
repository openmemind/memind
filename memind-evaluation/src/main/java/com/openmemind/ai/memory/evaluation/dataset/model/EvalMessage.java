package com.openmemind.ai.memory.evaluation.dataset.model;

import java.time.Instant;
import java.util.Map;

/**
 * Evaluation message model, containing speaker information, message content, timestamp, and metadata
 *
 */
public record EvalMessage(
        String speakerId,
        String speakerName,
        String content,
        Instant timestamp,
        Map<String, Object> metadata) {}
