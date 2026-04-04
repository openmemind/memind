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
package com.openmemind.ai.memory.evaluation.pipeline.stage;

import com.openmemind.ai.memory.evaluation.adapter.memind.MemindAdapter;
import com.openmemind.ai.memory.evaluation.adapter.model.SearchRequest;
import com.openmemind.ai.memory.evaluation.adapter.model.SearchResult;
import com.openmemind.ai.memory.evaluation.checkpoint.CheckpointStore;
import com.openmemind.ai.memory.evaluation.dataset.model.EvalConversation;
import com.openmemind.ai.memory.evaluation.dataset.model.EvalDataset;
import com.openmemind.ai.memory.evaluation.dataset.model.QAPair;
import com.openmemind.ai.memory.evaluation.pipeline.PipelineConfig;
import com.openmemind.ai.memory.evaluation.pipeline.query.SearchQueryBuilderRegistry;
import com.openmemind.ai.memory.evaluation.progress.StageProgressBar;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * SEARCH stage: Initiate search requests for QA questions of each conversation, supporting conversation-level fine-grained checkpoint
 *
 */
@Component
public class SearchStage {
    private static final Logger log = LoggerFactory.getLogger(SearchStage.class);
    private final CheckpointStore checkpointStore;
    private final SearchQueryBuilderRegistry queryBuilderRegistry;

    public SearchStage(
            CheckpointStore checkpointStore, SearchQueryBuilderRegistry queryBuilderRegistry) {
        this.checkpointStore = checkpointStore;
        this.queryBuilderRegistry = queryBuilderRegistry;
    }

    /**
     * Execute search stage: Initiate search requests for QA questions of each conversation.
     *
     * <p>Supports fine-grained checkpoint: Completed conversations will be skipped, and after completing each conversation, write to search_checkpoint.json,
     * After all are completed, summarize and write to search_results.json and delete the checkpoint file.
     *
     * @return List of search results for all conversations
     */
    public Mono<List<SearchResult>> run(
            EvalDataset dataset, MemindAdapter adapter, Path runDir, PipelineConfig config) {

        // Load completed search checkpoint, key=convId, value=all search results for that
        // conversation
        Map<String, List<SearchResult>> done =
                new ConcurrentHashMap<>(checkpointStore.loadSearchCheckpoint(runDir));
        log.info("[Search] Restored {} conversations from checkpoint", done.size());

        Map<String, List<QAPair>> byConv = dataset.qaPairsByConversation();
        Map<String, EvalConversation> convIndex = dataset.conversationIndex();

        // Limit range by fromConv/toConv, consistent with AddStage
        List<String> convIds =
                dataset.conversations().stream().map(EvalConversation::conversationId).toList();
        int from = config.fromConv();
        int to = config.toConv() == -1 ? convIds.size() : Math.min(config.toConv(), convIds.size());
        var rangedConvIds = new java.util.HashSet<>(convIds.subList(from, to));

        // Per-QA progress (matching Python: total_questions / initial=processed_questions)
        long totalQAs =
                byConv.entrySet().stream()
                        .filter(e -> rangedConvIds.contains(e.getKey()))
                        .mapToLong(e -> applySmoke(e.getValue(), config).size())
                        .sum();
        long initialQAs =
                done.entrySet().stream()
                        .filter(e -> rangedConvIds.contains(e.getKey()))
                        .mapToLong(e -> e.getValue().size())
                        .sum();
        StageProgressBar pb = StageProgressBar.create("Search ", totalQAs, initialQAs);

        return Flux.fromIterable(byConv.entrySet())
                .filter(
                        e ->
                                rangedConvIds.contains(
                                        e.getKey())) // Only process conversations within range
                .filter(e -> !done.containsKey(e.getKey())) // Skip completed conversations
                .flatMap(
                        entry -> {
                            String convId = entry.getKey();
                            EvalConversation conv = convIndex.get(convId);
                            if (conv == null) {
                                return Mono.empty();
                            }

                            String speakerAId = MemindAdapter.buildUserId(convId, conv.speakerA());
                            String speakerBId = MemindAdapter.buildUserId(convId, conv.speakerB());
                            List<QAPair> qas = applySmoke(entry.getValue(), config);
                            log.info(
                                    "[Search] Processing conv={} ({} questions)",
                                    convId,
                                    qas.size());

                            return Flux.fromIterable(qas)
                                    .flatMap(
                                            qa -> {
                                                String query =
                                                        queryBuilderRegistry
                                                                .get(config.searchQueryMode())
                                                                .build(qa);
                                                SearchRequest req =
                                                        new SearchRequest(
                                                                convId,
                                                                speakerAId,
                                                                speakerBId,
                                                                query,
                                                                config.topK());
                                                return adapter.search(req)
                                                        .map(r -> r.withQuestionId(qa.questionId()))
                                                        .timeout(Duration.ofSeconds(300))
                                                        .retry(3)
                                                        .doOnError(
                                                                e ->
                                                                        log.warn(
                                                                                "[Search] qa={}"
                                                                                        + " failed:"
                                                                                        + " {}",
                                                                                qa.questionId(),
                                                                                e.getMessage()))
                                                        .onErrorResume(e -> Mono.empty())
                                                        .doFinally(signal -> pb.step()); // per QA,
                                                // always
                                            },
                                            config.searchConcurrency())
                                    .collectList()
                                    .doOnSuccess(
                                            results -> {
                                                log.info(
                                                        "[Search] conv={} questions={}",
                                                        convId,
                                                        results.size());
                                                done.put(convId, results);
                                                // Write checkpoint after completing each
                                                // conversation
                                                checkpointStore.saveSearchCheckpoint(runDir, done);
                                            });
                        },
                        config.convConcurrency())
                .then(
                        Mono.fromCallable(
                                () -> {
                                    // All conversations completed: only summarize results within
                                    // range, write search_results.json, delete fine-grained
                                    // checkpoint
                                    List<SearchResult> all =
                                            done.entrySet().stream()
                                                    .filter(e -> rangedConvIds.contains(e.getKey()))
                                                    .flatMap(e -> e.getValue().stream())
                                                    .toList();
                                    checkpointStore.saveSearchResults(runDir, all);
                                    checkpointStore.deleteSearchCheckpoint(runDir);
                                    log.info(
                                            "[Search] Completed, a total of {} question results"
                                                    + " written to search_results.json",
                                            all.size());
                                    return all;
                                }))
                .doFinally(signal -> pb.close());
    }

    private List<QAPair> applySmoke(List<QAPair> qas, PipelineConfig config) {
        if (!config.smoke()) {
            return qas;
        }
        return qas.subList(0, Math.min(config.smokeQuestions(), qas.size()));
    }
}
