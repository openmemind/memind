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
package com.openmemind.ai.memory.core.store.buffer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.openmemind.ai.memory.core.buffer.InsightBuffer;
import com.openmemind.ai.memory.core.buffer.MemoryBuffer;
import com.openmemind.ai.memory.core.buffer.PendingConversationBuffer;
import com.openmemind.ai.memory.core.buffer.RecentConversationBuffer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("MemoryBuffer")
class MemoryBufferTest {

    private interface SharedBuffer extends InsightBuffer, PendingConversationBuffer {
        @Override
        void close() throws Exception;
    }

    @Test
    @DisplayName("of returns the provided buffers")
    void ofReturnsProvidedBuffers() {
        InsightBuffer insightBuffer = mock(InsightBuffer.class);
        PendingConversationBuffer pendingConversationBuffer = mock(PendingConversationBuffer.class);
        RecentConversationBuffer recentConversationBuffer = mock(RecentConversationBuffer.class);

        MemoryBuffer memoryBuffer =
                MemoryBuffer.of(insightBuffer, pendingConversationBuffer, recentConversationBuffer);

        assertThat(memoryBuffer.insightBuffer()).isSameAs(insightBuffer);
        assertThat(memoryBuffer.pendingConversationBuffer()).isSameAs(pendingConversationBuffer);
        assertThat(memoryBuffer.recentConversationBuffer()).isSameAs(recentConversationBuffer);
    }

    @Test
    @DisplayName("close closes each unique buffer once")
    void closeClosesEachUniqueBufferOnce() throws Exception {
        SharedBuffer shared = mock(SharedBuffer.class);
        RecentConversationBuffer recentConversationBuffer = mock(RecentConversationBuffer.class);

        MemoryBuffer memoryBuffer = MemoryBuffer.of(shared, shared, recentConversationBuffer);

        memoryBuffer.close();

        verify(shared).close();
        verify(recentConversationBuffer).close();
    }
}
