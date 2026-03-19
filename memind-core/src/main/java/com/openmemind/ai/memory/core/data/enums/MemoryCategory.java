/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.openmemind.ai.memory.core.data.enums;

import com.openmemind.ai.memory.core.data.GroupingStrategy;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Memory classification enumeration
 *
 * <p>Defines 5 memory classifications for two scopes: USER (3) and AGENT (2).
 * Optimized with explicit rule-based definitions for small LLM (e.g., gpt-4o-mini) classification.
 */
public enum MemoryCategory {
    PROFILE(
            "profile",
            "Stable facts about who the user is as a person",
            """
            Core: Describes WHO the user IS as a person.
            Characteristics: Stable, long-term attributes that remain true across projects.
            Test: Is this still true if the user's current project ends?
            Includes: occupation, age, skills, identity, enduring personal/code style preferences.
            Note: "User is a Java developer" -> profile; "User is using Java 21 for this project" -> event.\
            """,
            MemoryScope.USER,
            new GroupingStrategy.LlmClassify()),

    BEHAVIOR(
            "behavior",
            "Recurring habits, routines, and repeated actions",
            """
            Core: Describes what the user HABITUALLY does.
            Characteristics: Repeated patterns with observable frequency, not one-time actions.
            Test: Does the user do this MULTIPLE times on a regular basis?
            Includes: daily routines, recurring work habits, regular exercise patterns.
            Note: Must have recurrence evidence. "Runs every morning" -> behavior; "Went running yesterday" -> event.\
            """,
            MemoryScope.USER,
            new GroupingStrategy.LlmClassify()),

    EVENT(
            "event",
            "Time-bound situations, actions, and occurrences",
            """
            Core: Describes what happened, is happening, or is planned.
            Characteristics: Time-bound situations — has a start/end, temporal anchor, or will become outdated.
            Test: Will this fact become outdated when the situation changes?
            Includes: ongoing projects, current team setup/tech stack, past decisions, planned migrations.
            Note: "User is building X" and "User's team uses Redis" are both events — current situations, not identity.\
            """,
            MemoryScope.USER,
            new GroupingStrategy.LlmClassify()),

    TOOL(
            "tool",
            "Usage experience and optimization for a single tool",
            """
            Core: Usage experience and optimization for a single tool.
            Characteristics: Best parameters, common failure modes, performance data per tool.
            Test: Does this describe how a specific tool was used or should be configured?\
            """,
            MemoryScope.AGENT,
            new GroupingStrategy.MetadataField("toolName")),

    PROCEDURAL(
            "procedural",
            "Reusable procedures, problem-solution pairs, and agent behavioral directives",
            """
            Core: Describes reusable operational knowledge for the agent.
            Characteristics: Step-by-step instructions, problem-solution pairs, or behavioral directives from the user.
            Test: Is this something the agent should remember for future tasks or interactions?
            Includes: configuration recipes, setup guides, step-by-step workflows, bug fixes with solutions, user directives on agent behavior.
            Note: "Enable X by setting Y=true" -> procedural; "Virtual threads caused pool exhaustion; solved by setting poolSize=10" -> procedural; "Don't add comments to my code" -> procedural; "User enabled X" -> event.\
            """,
            MemoryScope.AGENT,
            new GroupingStrategy.LlmClassify());

    private static final Map<String, MemoryCategory> BY_NAME =
            Arrays.stream(values())
                    .collect(
                            Collectors.toUnmodifiableMap(
                                    MemoryCategory::categoryName, Function.identity()));

    private final String name;
    private final String description;
    private final String promptDefinition;
    private final MemoryScope scope;
    private final GroupingStrategy groupingStrategy;

    MemoryCategory(
            String name,
            String description,
            String promptDefinition,
            MemoryScope scope,
            GroupingStrategy groupingStrategy) {
        this.name = name;
        this.description = description;
        this.promptDefinition = promptDefinition;
        this.scope = scope;
        this.groupingStrategy = groupingStrategy;
    }

    public String categoryName() {
        return name;
    }

    public String description() {
        return description;
    }

    /** Structured prompt definition (Core/Characteristics/Test/Includes/Note) with %s placeholder for insightTypes. */
    public String promptDefinition() {
        return promptDefinition;
    }

    public MemoryScope scope() {
        return scope;
    }

    public GroupingStrategy groupingStrategy() {
        return groupingStrategy;
    }

    public static Optional<MemoryCategory> byName(String name) {
        return Optional.ofNullable(BY_NAME.get(name));
    }

    public static Set<MemoryCategory> userCategories() {
        return EnumSet.of(PROFILE, BEHAVIOR, EVENT);
    }

    public static Set<MemoryCategory> agentCategories() {
        return EnumSet.of(TOOL, PROCEDURAL);
    }
}
