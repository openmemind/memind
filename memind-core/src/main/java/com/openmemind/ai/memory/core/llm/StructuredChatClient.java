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
package com.openmemind.ai.memory.core.llm;

import java.util.List;
import reactor.core.publisher.Mono;

/**
 * Framework-neutral LLM client for structured responses.
 */
public interface StructuredChatClient {

    /**
     * Call an LLM with a list of messages and return the raw response.
     *
     * @param messages message list
     * @return response text
     */
    Mono<String> call(List<ChatMessage> messages);

    /**
     * Call an LLM with a list of messages and map response to a target type.
     *
     * @param messages message list
     * @param responseType expected response type
     * @param <T> response type
     * @return structured response
     */
    <T> Mono<T> call(List<ChatMessage> messages, Class<T> responseType);
}
