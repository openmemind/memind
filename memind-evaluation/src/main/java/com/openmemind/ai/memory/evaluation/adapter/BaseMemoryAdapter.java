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
package com.openmemind.ai.memory.evaluation.adapter;

/**
 * Abstract base class of MemoryAdapter, providing common utility methods
 *
 */
public abstract class BaseMemoryAdapter implements MemoryAdapter {

    /**
     * Build userId for the memory system based on conversation ID and speaker name
     *
     * @param convId      Conversation ID
     * @param speakerName Speaker name (spaces replaced with underscores)
     * @return userId, format: {convId}_{speakerName}
     */
    public static String buildUserId(String convId, String speakerName) {
        return convId + "_" + speakerName.replace(" ", "_");
    }
}
