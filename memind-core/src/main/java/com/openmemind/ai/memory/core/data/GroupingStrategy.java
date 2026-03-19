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

/**
 * Insight grouping strategy
 *
 * <p>sealed interface, referenced by {@link com.openmemind.ai.memory.core.data.enums.MemoryCategory},
 * determines how items within the same category are grouped to construct Insight.
 *
 */
public sealed interface GroupingStrategy {

    /** LLM semantic classification grouping */
    record LlmClassify() implements GroupingStrategy {}

    /** Group directly by metadata field value, zero LLM overhead */
    record MetadataField(String fieldName) implements GroupingStrategy {}
}
