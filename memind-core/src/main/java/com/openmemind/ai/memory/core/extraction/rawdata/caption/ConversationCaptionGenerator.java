package com.openmemind.ai.memory.core.extraction.rawdata.caption;

import com.openmemind.ai.memory.core.prompt.extraction.rawdata.CaptionPrompts;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

/**
 * LLM Summary Generator
 *
 * <p>Use LLM to generate Episode-style narrative memory for segmented content, for vector retrieval.
 *
 */
public class ConversationCaptionGenerator implements CaptionGenerator {

    private static final Logger log = LoggerFactory.getLogger(ConversationCaptionGenerator.class);

    private final ChatClient chatClient;

    public ConversationCaptionGenerator(ChatClient chatClient) {
        this.chatClient = Objects.requireNonNull(chatClient, "chatClient must not be null");
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

        return Mono.fromCallable(
                        () ->
                                chatClient
                                        .prompt()
                                        .system(result.systemPrompt())
                                        .user(result.userPrompt())
                                        .call()
                                        .entity(CaptionResponse.class))
                .map(ConversationCaptionGenerator::toCaption)
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
