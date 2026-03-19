package com.openmemind.ai.memory.core.prompt.extraction.item;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.data.MemoryInsightType;
import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MemoryItemUnifiedPromptsTest {

    @Test
    @DisplayName("Rendered prompt should contain Decision Logic and Category Definitions")
    void shouldContainCategoryContext() {
        var insightTypes =
                List.of(
                        createInsightType("profile", List.of("profile")),
                        createInsightType("experiences", List.of("event")));
        var template =
                MemoryItemUnifiedPrompts.build(
                        insightTypes,
                        "user: hello",
                        Instant.parse("2026-03-18T00:00:00Z"),
                        "Alice",
                        Set.of(MemoryCategory.PROFILE, MemoryCategory.EVENT));
        var result = template.render("English");
        assertThat(result.systemPrompt()).contains("## Decision Logic");
        assertThat(result.systemPrompt()).contains("## Category Definitions");
        assertThat(result.systemPrompt()).contains("**profile**");
        assertThat(result.systemPrompt()).contains("**event**");
        assertThat(result.systemPrompt()).contains("Alice");
        assertThat(result.systemPrompt()).contains("# Temporal Resolution");
    }

    private static MemoryInsightType createInsightType(String name, List<String> categories) {
        return new MemoryInsightType(
                null,
                null,
                name,
                null,
                null,
                categories,
                100,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }
}
