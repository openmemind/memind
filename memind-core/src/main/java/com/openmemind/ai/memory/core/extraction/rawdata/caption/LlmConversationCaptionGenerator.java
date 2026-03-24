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
package com.openmemind.ai.memory.core.extraction.rawdata.caption;

import com.openmemind.ai.memory.core.llm.ChatMessages;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import com.openmemind.ai.memory.core.prompt.extraction.rawdata.CaptionPrompts;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

/**
 * LLM summary generator.
 */
public class LlmConversationCaptionGenerator implements CaptionGenerator {

    private static final Logger log =
            LoggerFactory.getLogger(LlmConversationCaptionGenerator.class);

    private final StructuredChatClient structuredChatClient;

    public LlmConversationCaptionGenerator(StructuredChatClient structuredChatClient) {
        this.structuredChatClient =
                Objects.requireNonNull(
                        structuredChatClient, "structuredChatClient must not be null");
    }

    @Override
    public Mono<String> generate(String content, Map<String, Object> metadata) {
        return generate(content, metadata, null);
    }

    @Override
    public Mono<String> generate(String content, Map<String, Object> metadata, String language) {
        if (content == null || content.isBlank()) {
            return Mono.just("");
        }

        var result = CaptionPrompts.build(content, metadata).render(language);
        var messages = ChatMessages.systemUser(result.systemPrompt(), result.userPrompt());

        return structuredChatClient
                .call(messages, CaptionResponse.class)
                .map(LlmConversationCaptionGenerator::toCaption)
                .switchIfEmpty(Mono.just(""))
                .subscribeOn(Schedulers.boundedElastic())
                .retryWhen(
                        Retry.backoff(3, Duration.ofSeconds(2))
                                .maxBackoff(Duration.ofSeconds(15))
                                .doBeforeRetry(
                                        signal ->
                                                log.warn(
                                                        "Caption generation failed, retrying for"
                                                                + " the {} time: {}",
                                                        signal.totalRetries() + 1,
                                                        signal.failure().getMessage())))
                .onErrorResume(
                        e -> {
                            log.warn(
                                    "Caption generation ultimately failed, using empty caption: {}",
                                    e.getMessage());
                            return Mono.just("");
                        });
    }

    public static String toCaption(CaptionResponse response) {
        if (response == null) {
            return "";
        }
        var title = response.title() != null ? response.title().trim() : "";
        var content = response.content() != null ? response.content().trim() : "";
        if (title.isEmpty() && content.isEmpty()) {
            return "";
        }
        if (title.isEmpty()) {
            return content;
        }
        if (content.isEmpty()) {
            return title;
        }
        return title + "\n\n" + content;
    }

    public record CaptionResponse(String title, String content) {}
}
