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
package com.openmemind.ai.memory.core.store;

import com.openmemind.ai.memory.core.store.buffer.ConversationBuffer;
import com.openmemind.ai.memory.core.store.buffer.InMemoryConversationBuffer;
import com.openmemind.ai.memory.core.store.buffer.InMemoryInsightBuffer;
import com.openmemind.ai.memory.core.store.buffer.InsightBuffer;
import com.openmemind.ai.memory.core.store.insight.InMemoryInsightOperations;
import com.openmemind.ai.memory.core.store.insight.InsightOperations;
import com.openmemind.ai.memory.core.store.item.InMemoryItemOperations;
import com.openmemind.ai.memory.core.store.item.ItemOperations;
import com.openmemind.ai.memory.core.store.rawdata.InMemoryRawDataOperations;
import com.openmemind.ai.memory.core.store.rawdata.RawDataOperations;

/**
 * In-memory implementation of {@link MemoryStore}.
 */
public class InMemoryMemoryStore implements MemoryStore {

    private final RawDataOperations rawDataOperations = new InMemoryRawDataOperations();
    private final ItemOperations itemOperations = new InMemoryItemOperations();
    private final InsightOperations insightOperations = new InMemoryInsightOperations();
    private final InsightBuffer insightBuffer = new InMemoryInsightBuffer();
    private final ConversationBuffer conversationBuffer = new InMemoryConversationBuffer();

    @Override
    public RawDataOperations rawDataOperations() {
        return rawDataOperations;
    }

    @Override
    public ItemOperations itemOperations() {
        return itemOperations;
    }

    @Override
    public InsightOperations insightOperations() {
        return insightOperations;
    }

    @Override
    public InsightBuffer insightBufferStore() {
        return insightBuffer;
    }

    @Override
    public ConversationBuffer conversationBufferStore() {
        return conversationBuffer;
    }
}
