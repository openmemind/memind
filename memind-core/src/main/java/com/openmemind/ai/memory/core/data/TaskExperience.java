package com.openmemind.ai.memory.core.data;

import java.util.List;

/**
 * Task experience retrieval result
 *
 */
public record TaskExperience(
        List<String> contrastiveInsights,
        List<String> experienceInsights,
        List<String> strategies,
        List<String> antipatterns) {}
