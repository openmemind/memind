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
package com.openmemind.ai.memory.core.builder;

import com.openmemind.ai.memory.core.extraction.insight.scheduler.InsightBuildConfig;
import java.util.Objects;

public record InsightExtractionOptions(
        boolean enabled, InsightBuildConfig build, InsightGraphAssistOptions graphAssist) {

    private static final InsightBuildConfig DEFAULT_BUILD = new InsightBuildConfig(3, 2, 8, 2);

    public InsightExtractionOptions {
        build = Objects.requireNonNull(build, "build");
        graphAssist = Objects.requireNonNull(graphAssist, "graphAssist");
    }

    public InsightExtractionOptions(boolean enabled, InsightBuildConfig build) {
        this(enabled, build, InsightGraphAssistOptions.defaults());
    }

    public static InsightExtractionOptions defaults() {
        return new InsightExtractionOptions(
                true, DEFAULT_BUILD, InsightGraphAssistOptions.defaults());
    }
}
