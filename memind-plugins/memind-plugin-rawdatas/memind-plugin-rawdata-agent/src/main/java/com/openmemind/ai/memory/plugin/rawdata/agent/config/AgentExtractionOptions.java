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
package com.openmemind.ai.memory.plugin.rawdata.agent.config;

/**
 * Item extraction controls for agent episode segments.
 */
public record AgentExtractionOptions(
        boolean extractTool,
        boolean extractResolution,
        boolean extractPlaybook,
        boolean extractDirective,
        boolean extractOnEveryTool,
        int minEventsForExtraction,
        int minEventsForPlaybook,
        boolean requireSuccessForPlaybook) {

    public AgentExtractionOptions {
        minEventsForExtraction = Math.max(0, minEventsForExtraction);
        minEventsForPlaybook = Math.max(0, minEventsForPlaybook);
    }

    public static AgentExtractionOptions defaults() {
        return new AgentExtractionOptions(true, true, true, true, false, 3, 5, true);
    }
}
