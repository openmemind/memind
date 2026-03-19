package com.openmemind.ai.memory.core.data;

/**
 * Agent Memory Unified Retrieval Result
 *
 */
public record AgentMemoryContext(
        ToolGuidance toolGuidance,
        TaskExperience taskExperience,
        SelfImprovementGuide selfImprovement) {

    public String toPromptText() {
        var sb = new StringBuilder();

        if (toolGuidance != null && !toolGuidance.insights().isEmpty()) {
            sb.append("## Tool Usage Guidelines\n");
            toolGuidance.insights().forEach(i -> sb.append("- ").append(i).append("\n"));
        }

        if (taskExperience != null && !taskExperience.strategies().isEmpty()) {
            sb.append("\n## Relevant Task Experience\n");
            taskExperience.strategies().forEach(s -> sb.append("- ").append(s).append("\n"));
        }

        if (taskExperience != null && !taskExperience.antipatterns().isEmpty()) {
            sb.append("\n## Known Pitfalls\n");
            taskExperience
                    .antipatterns()
                    .forEach(a -> sb.append("- AVOID: ").append(a).append("\n"));
        }

        if (selfImprovement != null && !selfImprovement.guidelines().isEmpty()) {
            sb.append("\n## Self-Improvement Notes\n");
            sb.append("Based on user feedback, pay attention to:\n");
            selfImprovement.guidelines().forEach(g -> sb.append("- ").append(g).append("\n"));
        }

        return sb.toString();
    }
}
