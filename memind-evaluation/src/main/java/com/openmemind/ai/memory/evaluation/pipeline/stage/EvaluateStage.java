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

import com.openmemind.ai.memory.evaluation.dataset.model.EvalDataset;
import com.openmemind.ai.memory.evaluation.dataset.model.QAPair;
import com.openmemind.ai.memory.evaluation.evaluator.HybridEvaluator;
import com.openmemind.ai.memory.evaluation.pipeline.PipelineConfig;
import com.openmemind.ai.memory.evaluation.pipeline.model.AnswerResult;
import com.openmemind.ai.memory.evaluation.pipeline.model.EvaluationResult;
import com.openmemind.ai.memory.evaluation.progress.StageProgressBar;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * EVALUATE stage: Use HybridEvaluator to judge the correctness of each answer and summarize it into EvaluationResult
 *
 */
@Component
public class EvaluateStage {
    private static final Logger log = LoggerFactory.getLogger(EvaluateStage.class);
    private static final int EVALUATE_CONCURRENCY = 20;

    private final HybridEvaluator evaluator;

    public EvaluateStage(HybridEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    public Mono<EvaluationResult> run(
            EvalDataset dataset, List<AnswerResult> answerResults, PipelineConfig config) {
        Map<String, QAPair> qaIndex =
                dataset.qaPairs().stream().collect(Collectors.toMap(QAPair::questionId, q -> q));

        StageProgressBar pb = StageProgressBar.create("Eval   ", answerResults.size(), 0);

        return Flux.fromIterable(answerResults)
                .flatMap(
                        ar -> {
                            QAPair qa = qaIndex.get(ar.questionId());
                            if (qa == null) {
                                return Mono.empty();
                            }
                            return evaluator
                                    .evaluate(ar, qa)
                                    .doOnSuccess(j -> pb.step())
                                    .doOnError(
                                            e ->
                                                    log.warn(
                                                            "[Eval] qa={} failed: {}",
                                                            ar.questionId(),
                                                            e.getMessage()))
                                    .onErrorResume(e -> Mono.empty());
                        },
                        EVALUATE_CONCURRENCY)
                .collectList()
                .map(
                        judgments -> {
                            EvaluationResult result = EvaluationResult.from(judgments);
                            log.info(
                                    "[Eval] total={} correct={} accuracy={}%",
                                    result.totalQuestions(),
                                    result.correct(),
                                    String.format("%.2f", result.accuracy() * 100));
                            return result;
                        })
                .doFinally(signal -> pb.close());
    }
}
