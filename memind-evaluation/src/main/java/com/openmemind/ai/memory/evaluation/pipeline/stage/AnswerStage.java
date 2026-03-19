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

import com.openmemind.ai.memory.evaluation.adapter.MemoryAdapter;
import com.openmemind.ai.memory.evaluation.adapter.model.SearchResult;
import com.openmemind.ai.memory.evaluation.checkpoint.CheckpointStore;
import com.openmemind.ai.memory.evaluation.dataset.model.EvalDataset;
import com.openmemind.ai.memory.evaluation.dataset.model.QAPair;
import com.openmemind.ai.memory.evaluation.pipeline.PipelineConfig;
import com.openmemind.ai.memory.evaluation.pipeline.model.AnswerResult;
import com.openmemind.ai.memory.evaluation.progress.StageProgressBar;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

/**
 * ANSWER stage: Call adapter to answer questions based on retrieval results, supports multiple-choice option addition and batch checkpoint
 *
 */
@Component
public class AnswerStage {
    private static final Logger log = LoggerFactory.getLogger(AnswerStage.class);
    private static final int ANSWER_CONCURRENCY = 50;
    private static final int CHECKPOINT_BATCH = 400;

    private final CheckpointStore checkpointStore;

    public AnswerStage(CheckpointStore checkpointStore) {
        this.checkpointStore = checkpointStore;
    }

    public Mono<Void> run(
            EvalDataset dataset, MemoryAdapter adapter, Path runDir, PipelineConfig config) {
        Map<String, AnswerResult> answered =
                new ConcurrentHashMap<>(checkpointStore.loadAnswerCheckpoint(runDir));
        List<SearchResult> searchResults = checkpointStore.loadSearchResults(runDir);

        Map<String, QAPair> qaIndex =
                dataset.qaPairs().stream().collect(Collectors.toMap(QAPair::questionId, q -> q));

        List<SearchResult> pending =
                searchResults.stream()
                        .filter(sr -> !answered.containsKey(sr.questionId()))
                        .toList();

        log.info(
                "[Answer] total={} already={} pending={}",
                searchResults.size(),
                answered.size(),
                pending.size());

        StageProgressBar pb =
                StageProgressBar.create("Answer ", searchResults.size(), answered.size());
        AtomicInteger completed = new AtomicInteger(answered.size());

        return Flux.fromIterable(pending)
                .flatMap(
                        sr -> {
                            QAPair qa = qaIndex.get(sr.questionId());
                            if (qa == null) {
                                return Mono.empty();
                            }

                            String question =
                                    qa.isMultipleChoice()
                                            ? qa.question()
                                                    + "\n\nOPTIONS:\n"
                                                    + qa.allOptions()
                                                    + "\nIMPORTANT: This is a multiple-choice"
                                                    + " question. You MUST select the BEST option."
                                                    + " In your FINAL ANSWER, return ONLY the"
                                                    + " option letter like (a), (b), (c), or (d),"
                                                    + " nothing else."
                                            : qa.question();

                            return adapter.answer(question, sr.formattedContext(), qa)
                                    .retryWhen(
                                            Retry.backoff(3, Duration.ofSeconds(2))
                                                    .maxBackoff(Duration.ofSeconds(15)))
                                    .map(
                                            answer ->
                                                    new AnswerResult(
                                                            qa.questionId(),
                                                            qa.question(),
                                                            qa.goldenAnswer(),
                                                            answer,
                                                            qa.category(),
                                                            qa.conversationId(),
                                                            sr.formattedContext()))
                                    .doOnSuccess(
                                            ar -> {
                                                answered.put(ar.questionId(), ar);
                                                int count = completed.incrementAndGet();
                                                log.debug(
                                                        "[Answer] qa={} ({}/{})",
                                                        ar.questionId(),
                                                        count,
                                                        searchResults.size());
                                                pb.step();
                                            })
                                    .doOnError(
                                            e ->
                                                    log.warn(
                                                            "[Answer] qa={} failed: {}",
                                                            sr.questionId(),
                                                            e.getMessage()))
                                    .onErrorResume(e -> Mono.empty());
                        },
                        ANSWER_CONCURRENCY)
                .buffer(CHECKPOINT_BATCH)
                .doOnNext(
                        batch -> {
                            log.info(
                                    "[Answer] checkpoint: batch size={} total={}",
                                    batch.size(),
                                    answered.size());
                            checkpointStore.saveAnswerCheckpoint(runDir, answered);
                        })
                .then()
                .doOnSuccess(
                        v -> {
                            checkpointStore.saveAnswerResults(
                                    runDir, new ArrayList<>(answered.values()));
                            checkpointStore.deleteAnswerCheckpoint(runDir);
                            log.info("[Answer] completed: total={}", answered.size());
                        })
                .doFinally(signal -> pb.close());
    }
}
