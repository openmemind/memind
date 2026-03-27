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
 * <p>Defines 7 memory classifications for two scopes: USER (3) and AGENT (4).
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

    DIRECTIVE(
            "directive",
            "Durable agent instructions, boundaries, and collaboration rules",
            """
            Core: Describes a durable instruction about how the agent should behave in future interactions.
            Characteristics: Stable rule, boundary, or collaboration constraint that should be reused later.
            Test: Would this still be valid in a later conversation with the same user?
            Includes: planning requirements, response constraints, language handling rules, do/don't boundaries.
            Excludes: one-off control messages, transient execution commands, emotional support, and project facts.
            Note: "Show the plan before substantial edits" -> directive; "continue" -> do not extract.\
            """,
            MemoryScope.AGENT,
            new GroupingStrategy.LlmClassify()),

    PLAYBOOK(
            "playbook",
            "Reusable task workflows and handling patterns",
            """
            Core: Describes a reusable way of handling a class of tasks.
            Characteristics: Repeatable workflow, reusable workflow, scenario playbook, task archetype, or task-handling strategy.
            Test: Could the agent reuse this for a future task of the same kind?
            Includes: comparison workflows, prompt refinement sequences, analysis checklists.
            Excludes: one-off request titles, loose advice, and unresolved discussion fragments.\
            """,
            MemoryScope.AGENT,
            new GroupingStrategy.LlmClassify()),

    RESOLUTION(
            "resolution",
            "Resolved problem knowledge with usable fixes or conclusions",
            """
            Core: Describes a resolved problem pattern and the usable resolution.
            Characteristics: Stable failure mode plus fix, conclusion, or durable correction.
            Test: Does this include both the problem and a usable resolution?
            Includes: naming-quality fixes, taxonomy corrections, troubleshooting conclusions.
            Excludes: unresolved problems, bare actions without a named problem, and brainstorming without closure.\
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
        return EnumSet.of(TOOL, DIRECTIVE, PLAYBOOK, RESOLUTION);
    }
}
