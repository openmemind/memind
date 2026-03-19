package com.openmemind.ai.memory.core.extraction.insight.generator;

import com.openmemind.ai.memory.core.data.InsightPoint;
import com.openmemind.ai.memory.core.data.MemoryInsight;
import com.openmemind.ai.memory.core.data.MemoryInsightType;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.prompt.extraction.insight.BranchAggregationPrompts;
import com.openmemind.ai.memory.core.prompt.extraction.insight.InsightLeafPrompts;
import com.openmemind.ai.memory.core.prompt.extraction.insight.InteractionGuideSynthesisPrompts;
import com.openmemind.ai.memory.core.prompt.extraction.insight.RootSynthesisPrompts;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

/**
 * Insight generator based on ChatClient
 *
 * <p>Call LLM to generate/update Insight summary, using Spring AI structured output
 *
 */
public class LlmInsightGenerator implements InsightGenerator {

    private static final Logger log = LoggerFactory.getLogger(LlmInsightGenerator.class);

    private final ChatClient chatClient;

    public LlmInsightGenerator(ChatClient chatClient) {
        this.chatClient = Objects.requireNonNull(chatClient, "chatClient must not be null");
    }

    @Override
    public Mono<InsightPointGenerateResponse> generatePoints(
            MemoryInsightType insightType,
            String groupName,
            List<InsightPoint> existingPoints,
            List<MemoryItem> newItems,
            int targetTokens,
            String additionalContext,
            String language) {

        var template =
                InsightLeafPrompts.build(
                        insightType, groupName, existingPoints, newItems, targetTokens);
        var promptResult = template.render(language);
        var userPrompt =
                additionalContext != null && !additionalContext.isBlank()
                        ? promptResult.userPrompt()
                                + "\n\n<AdditionalContext>\n"
                                + additionalContext
                                + "\n</AdditionalContext>"
                        : promptResult.userPrompt();

        return Mono.fromCallable(
                        () -> {
                            var response =
                                    chatClient
                                            .prompt()
                                            .system(promptResult.systemPrompt())
                                            .user(userPrompt)
                                            .call()
                                            .entity(InsightPointGenerateResponse.class);
                            if (response == null || response.points().isEmpty()) {
                                return new InsightPointGenerateResponse(List.of());
                            }
                            return response;
                        })
                .subscribeOn(Schedulers.boundedElastic())
                .retryWhen(
                        Retry.backoff(3, Duration.ofSeconds(2))
                                .maxBackoff(Duration.ofSeconds(15))
                                .doBeforeRetry(
                                        signal ->
                                                log.warn(
                                                        "InsightPoint generation failed, retrying"
                                                            + " {} time [type={}, group={}]: {}",
                                                        signal.totalRetries() + 1,
                                                        insightType.name(),
                                                        groupName,
                                                        signal.failure().getMessage())));
    }

    @Override
    public Mono<InsightPointGenerateResponse> generateBranchSummary(
            MemoryInsightType insightType,
            List<InsightPoint> existingPoints,
            List<MemoryInsight> leafInsights,
            int targetTokens,
            String language) {

        var promptResult =
                BranchAggregationPrompts.build(
                                insightType, existingPoints, leafInsights, targetTokens)
                        .render(language);

        return Mono.fromCallable(
                        () -> {
                            var response =
                                    chatClient
                                            .prompt()
                                            .system(promptResult.systemPrompt())
                                            .user(promptResult.userPrompt())
                                            .call()
                                            .entity(InsightPointGenerateResponse.class);
                            if (response == null || response.points().isEmpty()) {
                                return new InsightPointGenerateResponse(List.of());
                            }
                            return response;
                        })
                .subscribeOn(Schedulers.boundedElastic())
                .retryWhen(
                        Retry.backoff(3, Duration.ofSeconds(2))
                                .maxBackoff(Duration.ofSeconds(15))
                                .doBeforeRetry(
                                        signal ->
                                                log.warn(
                                                        "BRANCH aggregation failed, retrying {}"
                                                                + " time [type={}]: {}",
                                                        signal.totalRetries() + 1,
                                                        insightType.name(),
                                                        signal.failure().getMessage())));
    }

    @Override
    public Mono<InsightPointGenerateResponse> generateRootSynthesis(
            MemoryInsightType rootInsightType,
            String existingSummary,
            List<MemoryInsight> branchInsights,
            int targetTokens,
            String language) {

        var template =
                switch (rootInsightType.name()) {
                    case "interaction" ->
                            InteractionGuideSynthesisPrompts.build(
                                    rootInsightType, existingSummary, branchInsights, targetTokens);
                    default ->
                            RootSynthesisPrompts.build(
                                    rootInsightType, existingSummary, branchInsights, targetTokens);
                };
        var promptResult = template.render(language);

        return Mono.fromCallable(
                        () -> {
                            var spec = chatClient.prompt();
                            if (promptResult.hasSystemPrompt()) {
                                spec = spec.system(promptResult.systemPrompt());
                            }
                            var response =
                                    spec.user(promptResult.userPrompt())
                                            .call()
                                            .entity(InsightPointGenerateResponse.class);
                            if (response == null || response.points().isEmpty()) {
                                return new InsightPointGenerateResponse(List.of());
                            }
                            return response;
                        })
                .subscribeOn(Schedulers.boundedElastic())
                .retryWhen(
                        Retry.backoff(3, Duration.ofSeconds(2))
                                .maxBackoff(Duration.ofSeconds(15))
                                .doBeforeRetry(
                                        signal ->
                                                log.warn(
                                                        "ROOT synthesis failed, retrying {} time:"
                                                                + " {}",
                                                        signal.totalRetries() + 1,
                                                        signal.failure().getMessage())));
    }
}
