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
package com.openmemind.ai.memory.core.data;

import com.openmemind.ai.memory.core.data.enums.InsightAnalysisMode;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import java.util.List;

/**
 * Built-in default insight types
 *
 * <p>InsightType is a thematic dimension across MemoryCategory, and an InsightType can aggregate items from multiple MemoryCategory.
 *
 */
public final class DefaultInsightTypes {

    private DefaultInsightTypes() {}

    public static final int DEFAULT_TARGET_TOKENS = 600;

    private static final List<String> CONVERSATION_ONLY = List.of(ContentTypes.CONVERSATION);

    // ── USER BRANCH ──────────────────────────────────────────────────────────

    public static MemoryInsightType identity() {
        return new MemoryInsightType(
                1L,
                "identity",
                "Who the user IS as a person: stable traits, professional background,"
                        + " education, skills, and self-description that remain true regardless"
                        + " of current projects. Groups might be: career_background,"
                        + " education, core_skills. NOT time-bound activities (→ experiences)"
                        + " or recurring habits (→ behavior)",
                null,
                List.of("profile"),
                DEFAULT_TARGET_TOKENS,
                null,
                null,
                null,
                InsightAnalysisMode.BRANCH,
                null,
                MemoryScope.USER,
                CONVERSATION_ONLY);
    }

    public static MemoryInsightType preferences() {
        return new MemoryInsightType(
                2L,
                "preferences",
                "What the user LIKES, DISLIKES, or VALUES: subjective opinions on tools,"
                        + " technologies, food, lifestyle, aesthetics, and communication style."
                        + " Groups might be: tech_preferences, food_taste, work_style."
                        + " NOT stable identity facts (→ identity) or recurring actions"
                        + " (→ behavior)",
                null,
                List.of("profile"),
                DEFAULT_TARGET_TOKENS,
                null,
                null,
                null,
                InsightAnalysisMode.BRANCH,
                null,
                MemoryScope.USER,
                CONVERSATION_ONLY);
    }

    public static MemoryInsightType relationships() {
        return new MemoryInsightType(
                3L,
                "relationships",
                "The user's social network: family members, friends, colleagues, mentors,"
                        + " and their roles or dynamics. Groups might be: family, work_team,"
                        + " social_circle. NOT the user's own traits (→ identity) or shared"
                        + " activities (→ experiences)",
                null,
                List.of("profile"),
                DEFAULT_TARGET_TOKENS,
                null,
                null,
                null,
                InsightAnalysisMode.BRANCH,
                null,
                MemoryScope.USER,
                CONVERSATION_ONLY);
    }

    public static MemoryInsightType experiences() {
        return new MemoryInsightType(
                4L,
                "experiences",
                "What is HAPPENING or HAS HAPPENED: time-bound activities, projects, goals,"
                        + " milestones, team context, and situational facts that may become"
                        + " outdated. Groups might be: current_projects, travel, learning."
                        + " NOT stable traits (→ identity) or recurring routines (→ behavior)",
                null,
                List.of("event"),
                DEFAULT_TARGET_TOKENS,
                null,
                null,
                null,
                InsightAnalysisMode.BRANCH,
                null,
                MemoryScope.USER,
                CONVERSATION_ONLY);
    }

    public static MemoryInsightType behavior() {
        return new MemoryInsightType(
                9L,
                "behavior",
                "What the user DOES REPEATEDLY: habits, routines, rituals, and recurring"
                        + " patterns with observable frequency (daily, weekly, always, usually)."
                        + " Groups might be: morning_routine, exercise_habits, work_rituals."
                        + " NOT one-time events (→ experiences) or subjective opinions"
                        + " (→ preferences)",
                null,
                List.of("behavior"),
                DEFAULT_TARGET_TOKENS,
                null,
                null,
                null,
                InsightAnalysisMode.BRANCH,
                null,
                MemoryScope.USER,
                CONVERSATION_ONLY);
    }

    // ── AGENT BRANCH ─────────────────────────────────────────────────────────

    public static MemoryInsightType directives() {
        return new MemoryInsightType(
                25L,
                "directives",
                "Durable agent instructions and collaboration boundaries. Group by stable"
                        + " rule domain or behavior boundary. NOT one-off commands,"
                        + " task-specific workflows, or user events.",
                null,
                List.of("directive"),
                DEFAULT_TARGET_TOKENS,
                null,
                null,
                null,
                InsightAnalysisMode.BRANCH,
                null,
                MemoryScope.AGENT,
                List.of(ContentTypes.CONVERSATION));
    }

    public static MemoryInsightType playbooks() {
        return new MemoryInsightType(
                26L,
                "playbooks",
                "Reusable task workflows and handling archetypes. Group by recurring task"
                        + " scenario, not by session title or one-off request wording.",
                null,
                List.of("playbook"),
                DEFAULT_TARGET_TOKENS,
                null,
                null,
                null,
                InsightAnalysisMode.BRANCH,
                null,
                MemoryScope.AGENT,
                List.of(ContentTypes.CONVERSATION));
    }

    public static MemoryInsightType resolutions() {
        return new MemoryInsightType(
                27L,
                "resolutions",
                "Resolved problem patterns with usable fixes or conclusions. Group by stable"
                        + " problem family or failure mode, not by specific response phrasing.",
                null,
                List.of("resolution"),
                DEFAULT_TARGET_TOKENS,
                null,
                null,
                null,
                InsightAnalysisMode.BRANCH,
                null,
                MemoryScope.AGENT,
                List.of(ContentTypes.CONVERSATION));
    }

    // ── ROOT ─────────────────────────────────────────────────────────────────

    public static MemoryInsightType profile() {
        return new MemoryInsightType(
                20L,
                "profile",
                "Comprehensive user portrait synthesized from all BRANCH dimensions"
                        + " (identity, preferences, relationships, experiences, behavior)"
                        + " using convergence, tension, trajectory, and causation analysis."
                        + " Reveals cross-dimension patterns invisible at the BRANCH level",
                null,
                List.of(),
                800,
                null,
                null,
                null,
                InsightAnalysisMode.ROOT,
                null,
                MemoryScope.USER,
                null);
    }

    public static MemoryInsightType interaction() {
        return new MemoryInsightType(
                21L,
                "interaction",
                "Prescriptive directives for the AI agent: communication style calibration"
                        + " (tone, verbosity, formality), domain-specific response strategies,"
                        + " and behavioral boundaries (do/don't). Synthesized from all BRANCH"
                        + " dimensions to guide how the agent should interact with this user",
                null,
                List.of(),
                600,
                null,
                null,
                null,
                InsightAnalysisMode.ROOT,
                null,
                MemoryScope.USER,
                null);
    }

    public static List<MemoryInsightType> all() {
        return List.of(
                identity(),
                preferences(),
                relationships(),
                experiences(),
                behavior(),
                directives(),
                playbooks(),
                resolutions(),
                profile(),
                interaction());
    }
}
