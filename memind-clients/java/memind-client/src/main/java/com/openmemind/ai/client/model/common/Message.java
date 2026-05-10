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
package com.openmemind.ai.client.model.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Message(
        Role role,
        List<ContentBlock> content,
        Instant timestamp,
        String userName,
        String sourceClient) {

    public static Message user(String text) {
        return new Message(Role.USER, List.of(new ContentBlock.TextBlock(text)), null, null, null);
    }

    public static Message user(String text, Instant timestamp) {
        return new Message(
                Role.USER, List.of(new ContentBlock.TextBlock(text)), timestamp, null, null);
    }

    public static Message assistant(String text) {
        return new Message(
                Role.ASSISTANT, List.of(new ContentBlock.TextBlock(text)), null, null, null);
    }

    public static Message assistant(String text, Instant timestamp) {
        return new Message(
                Role.ASSISTANT, List.of(new ContentBlock.TextBlock(text)), timestamp, null, null);
    }
}
