package com.openmemind.ai.memory.evaluation.adapter.model;

/**
 * Memory addition result, including the number of new entries, the number of reinforced entries, and the number of insights
 *
 */
public record AddResult(int newItemCount, int reinforcedItemCount, int insightCount) {
    public static AddResult empty() {
        return new AddResult(0, 0, 0);
    }

    public AddResult merge(AddResult other) {
        return new AddResult(
                newItemCount + other.newItemCount,
                reinforcedItemCount + other.reinforcedItemCount,
                insightCount + other.insightCount);
    }
}
