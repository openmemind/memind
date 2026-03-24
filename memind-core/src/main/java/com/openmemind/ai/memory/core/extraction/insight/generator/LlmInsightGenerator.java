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
package com.openmemind.ai.memory.core.extraction.insight.generator;

import com.openmemind.ai.memory.core.data.InsightPoint;
import com.openmemind.ai.memory.core.data.MemoryInsight;
import com.openmemind.ai.memory.core.data.MemoryInsightType;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.llm.ChatMessages;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import com.openmemind.ai.memory.core.prompt.extraction.insight.BranchAggregationPrompts;
import com.openmemind.ai.memory.core.prompt.extraction.insight.InsightLeafPrompts;
import com.openmemind.ai.memory.core.prompt.extraction.insight.InteractionGuideSynthesisPrompts;
import com.openmemind.ai.memory.core.prompt.extraction.insight.RootSynthesisPrompts;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

/**
 * Insight generator based on StructuredChatClient
 *
 * <p>Call LLM to generate/update insight summaries using the framework-neutral LLM SPI.
 *
 */
public class LlmInsightGenerator implements InsightGenerator {

    private static final Logger log = LoggerFactory.getLogger(LlmInsightGenerator.class);

    private final StructuredChatClient structuredChatClient;

    public LlmInsightGenerator(StructuredChatClient structuredChatClient) {
        this.structuredChatClient =
                Objects.requireNonNull(
                        structuredChatClient, "structuredChatClient must not be null");
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
        var messages = ChatMessages.systemUser(promptResult.systemPrompt(), userPrompt);

        return structuredChatClient
                .call(messages, InsightPointGenerateResponse.class)
                .map(this::emptyIfMissing)
                .switchIfEmpty(Mono.just(new InsightPointGenerateResponse(List.of())))
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
        var messages =
                ChatMessages.systemUser(promptResult.systemPrompt(), promptResult.userPrompt());

        return structuredChatClient
                .call(messages, InsightPointGenerateResponse.class)
                .map(this::emptyIfMissing)
                .switchIfEmpty(Mono.just(new InsightPointGenerateResponse(List.of())))
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
        var messages =
                ChatMessages.systemUser(promptResult.systemPrompt(), promptResult.userPrompt());

        return structuredChatClient
                .call(messages, InsightPointGenerateResponse.class)
                .map(this::emptyIfMissing)
                .switchIfEmpty(Mono.just(new InsightPointGenerateResponse(List.of())))
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

    private InsightPointGenerateResponse emptyIfMissing(InsightPointGenerateResponse response) {
        if (response == null || response.points() == null || response.points().isEmpty()) {
            return new InsightPointGenerateResponse(List.of());
        }
        return response;
    }
}
