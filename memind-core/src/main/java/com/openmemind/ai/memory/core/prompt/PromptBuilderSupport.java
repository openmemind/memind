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
package com.openmemind.ai.memory.core.prompt;

import com.openmemind.ai.memory.core.data.MemoryInsightType;
import java.util.Objects;

/** Lightweight helpers for assembling prompt sections with consistent ordering. */
public final class PromptBuilderSupport {

    private PromptBuilderSupport() {}

    public static PromptTemplate.Builder coreSections(
            String name,
            String objective,
            String context,
            String workflow,
            String output,
            String examples) {
        return builder(
                name,
                section("objective", objective),
                section("context", context),
                section("workflow", workflow),
                section("output", output),
                section("examples", examples));
    }

    public static PromptTemplate.Builder builder(String name, PromptSection... sections) {
        var builder = PromptTemplate.builder(name);
        for (var section : sections) {
            builder.section(section.name(), section.content());
        }
        return builder;
    }

    public static PromptSection section(String name, String content) {
        return new PromptSection(name, content);
    }

    public static String descriptionOrName(MemoryInsightType insightType) {
        Objects.requireNonNull(insightType, "insightType");
        return insightType.description() != null ? insightType.description() : insightType.name();
    }

    public record PromptSection(String name, String content) {
        public PromptSection {
            Objects.requireNonNull(name, "name");
        }
    }
}
