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
package com.openmemind.ai.memory.plugin.jdbc.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class JdbcInternalArchitectureTest {

    @Test
    void internalJdbcImplementationDoesNotExposeAbstractBaseClasses() {
        List<String> presentClasses =
                abstractInternalClassNames().stream()
                        .filter(JdbcInternalArchitectureTest::classExists)
                        .toList();

        assertThat(presentClasses).isEmpty();
    }

    private static List<String> abstractInternalClassNames() {
        return List.of(
                "com.openmemind.ai.memory.plugin.jdbc.internal.buffer"
                        + ".AbstractJdbcConversationBuffer",
                "com.openmemind.ai.memory.plugin.jdbc.internal.buffer"
                        + ".AbstractJdbcConversationBufferAccessor",
                "com.openmemind.ai.memory.plugin.jdbc.internal.buffer"
                        + ".AbstractJdbcRecentConversationBuffer",
                "com.openmemind.ai.memory.plugin.jdbc.internal.graph.AbstractJdbcGraphOperations",
                "com.openmemind.ai.memory.plugin.jdbc.internal.graph"
                        + ".AbstractJdbcItemGraphCommitOperations",
                "com.openmemind.ai.memory.plugin.jdbc.internal.insight"
                        + ".AbstractJdbcBubbleTrackerStore",
                "com.openmemind.ai.memory.plugin.jdbc.internal.thread.AbstractJdbcThreadStore");
    }

    private static boolean classExists(String className) {
        String resourcePath = className.replace('.', '/') + ".class";
        return JdbcInternalArchitectureTest.class.getClassLoader().getResource(resourcePath)
                != null;
    }
}
