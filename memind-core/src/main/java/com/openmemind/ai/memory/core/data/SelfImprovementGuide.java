package com.openmemind.ai.memory.core.data;

import java.util.List;

/**
 * Agent Self-Evolution Guide
 *
 */
public record SelfImprovementGuide(
        List<String> guidelines, List<String> topIssues, List<String> strengths) {}
