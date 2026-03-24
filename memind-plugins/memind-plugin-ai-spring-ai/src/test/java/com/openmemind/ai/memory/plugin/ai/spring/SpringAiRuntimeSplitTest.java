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
package com.openmemind.ai.memory.plugin.ai.spring;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Spring AI runtime split")
class SpringAiRuntimeSplitTest {

    @Test
    @DisplayName("Runtime module should expose Spring AI implementations")
    void runtimeModuleShouldExposeSpringAiImplementations() {
        assertThatCode(
                        () ->
                                Class.forName(
                                        "com.openmemind.ai.memory.plugin.ai.spring.FileSimpleVectorStore"))
                .doesNotThrowAnyException();
        assertThatCode(
                        () ->
                                Class.forName(
                                        "com.openmemind.ai.memory.plugin.ai.spring.SpringAiMemoryVector"))
                .doesNotThrowAnyException();
        assertThatCode(
                        () ->
                                Class.forName(
                                        "com.openmemind.ai.memory.plugin.ai.spring.SpringAiStructuredChatClient"))
                .doesNotThrowAnyException();
    }
}
