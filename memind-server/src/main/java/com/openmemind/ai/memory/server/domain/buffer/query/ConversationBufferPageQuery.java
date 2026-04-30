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
package com.openmemind.ai.memory.server.domain.buffer.query;

public record ConversationBufferPageQuery(
        int pageNo, int pageSize, String memoryId, String sessionId, String state) {

    public static ConversationBufferPageQuery of(
            int pageNo, int pageSize, String memoryId, String sessionId, String state) {
        return new ConversationBufferPageQuery(
                pageNo, pageSize, memoryId, sessionId, state == null ? "pending" : state);
    }
}
