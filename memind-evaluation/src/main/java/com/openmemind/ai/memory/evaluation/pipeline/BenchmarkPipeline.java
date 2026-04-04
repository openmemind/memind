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
package com.openmemind.ai.memory.evaluation.pipeline;

import com.openmemind.ai.memory.evaluation.adapter.memind.MemindAdapter;
import com.openmemind.ai.memory.evaluation.checkpoint.CheckpointState;
import com.openmemind.ai.memory.evaluation.checkpoint.CheckpointStore;
import com.openmemind.ai.memory.evaluation.dataset.DatasetLoadOptions;
import com.openmemind.ai.memory.evaluation.dataset.DatasetLoader;
import com.openmemind.ai.memory.evaluation.dataset.converter.ConverterRegistry;
import com.openmemind.ai.memory.evaluation.dataset.model.EvalDataset;
import com.openmemind.ai.memory.evaluation.dataset.model.QAPair;
import com.openmemind.ai.memory.evaluation.pipeline.model.EvaluationResult;
import com.openmemind.ai.memory.evaluation.pipeline.stage.AddStage;
import com.openmemind.ai.memory.evaluation.pipeline.stage.AnswerStage;
import com.openmemind.ai.memory.evaluation.pipeline.stage.EvaluateStage;
import com.openmemind.ai.memory.evaluation.pipeline.stage.SearchStage;
import com.openmemind.ai.memory.evaluation.report.ReportWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Benchmark main pipeline, executes in stage order ADD -> SEARCH -> ANSWER -> EVALUATE, supports resuming from checkpoints
 *
 */
@Component
public class BenchmarkPipeline {
    private static final Logger log = LoggerFactory.getLogger(BenchmarkPipeline.class);

    private final MemindAdapter memindAdapter;
    private final Map<String, DatasetLoader> loaders;
    private final CheckpointStore checkpointStore;
    private final ConverterRegistry converterRegistry;
    private final AddStage addStage;
    private final SearchStage searchStage;
    private final AnswerStage answerStage;
    private final EvaluateStage evaluateStage;
    private final ReportWriter reportWriter;

    public BenchmarkPipeline(
            MemindAdapter memindAdapter,
            List<DatasetLoader> loaders,
            CheckpointStore checkpointStore,
            ConverterRegistry converterRegistry,
            AddStage addStage,
            SearchStage searchStage,
            AnswerStage answerStage,
            EvaluateStage evaluateStage,
            ReportWriter reportWriter) {
        this.memindAdapter = memindAdapter;
        this.loaders =
                loaders.stream()
                        .collect(Collectors.toMap(DatasetLoader::datasetName, Function.identity()));
        this.checkpointStore = checkpointStore;
        this.converterRegistry = converterRegistry;
        this.addStage = addStage;
        this.searchStage = searchStage;
        this.answerStage = answerStage;
        this.evaluateStage = evaluateStage;
        this.reportWriter = reportWriter;
    }

    public Mono<EvaluationResult> run(PipelineConfig config) {
        long startMs = System.currentTimeMillis();
        MemindAdapter adapter = memindAdapter;
        adapter.setDualPerspective(config.dualPerspective());
        adapter.setAddMode(config.addMode());

        DatasetLoader loader = loaders.get(config.loaderFormat());
        if (loader == null) {
            throw new IllegalArgumentException("Unknown loader format: " + config.loaderFormat());
        }

        // Calculate runDir using config.outputDir() to make --output-dir CLI parameter effective
        Path runDir = config.outputDir().resolve(config.datasetName() + "-" + config.runName());

        Path effectivePath =
                converterRegistry.convertIfNeeded(config.sourceFormat(), config.dataPath(), runDir);
        EvalDataset rawDataset =
                loader.load(
                        effectivePath,
                        new DatasetLoadOptions(config.datasetName(), config.maxContentLength()));

        // Category filtering: skip QAPair with category in filterCategories
        EvalDataset dataset = applyFilterCategories(rawDataset, config.filterCategories());
        CheckpointState checkpoint = checkpointStore.load(config.datasetName(), config.runName());

        log.info(
                "Starting benchmark: dataset={} conversations={} qaPairs={}",
                config.datasetName(),
                dataset.conversations().size(),
                dataset.qaPairs().size());

        // This loop runs at assembly time (when run() is called), not at subscription time.
        // Each iteration appends a stage via pipeline = pipeline.then(...), chaining stages
        // sequentially. EVALUATE is handled separately below via the runEval flag.
        Mono<Void> pipeline = Mono.empty();
        for (Stage stage : config.stages()) {
            // Skip completed stage
            if (checkpointStore.isStageCompleted(runDir, stage)) {
                log.info("[Pipeline] Skipping completed stage: {}", stage);
                continue;
            }
            if (stage == Stage.EVALUATE) {
                // EVALUATE is not chained here; it is handled after the pipeline
                // via the runEval flag to produce an EvaluationResult return value.
                continue;
            }
            pipeline =
                    switch (stage) {
                        case ADD ->
                                pipeline.then(
                                        Mono.defer(
                                                        () ->
                                                                addStage.run(
                                                                        dataset, adapter,
                                                                        checkpoint, config))
                                                .doOnSuccess(
                                                        v ->
                                                                checkpointStore.markStageCompleted(
                                                                        runDir, Stage.ADD)));
                        case SEARCH ->
                                pipeline.then(
                                        Mono.defer(
                                                        () ->
                                                                searchStage.run(
                                                                        dataset, adapter, runDir,
                                                                        config))
                                                .doOnSuccess(
                                                        results ->
                                                                checkpointStore.markStageCompleted(
                                                                        runDir, Stage.SEARCH))
                                                .then());
                        case ANSWER ->
                                pipeline.then(
                                        Mono.defer(
                                                        () ->
                                                                answerStage.run(
                                                                        dataset, adapter, runDir,
                                                                        config))
                                                .doOnSuccess(
                                                        ignored ->
                                                                checkpointStore.markStageCompleted(
                                                                        runDir, Stage.ANSWER)));
                        default -> throw new IllegalStateException("Unexpected stage: " + stage);
                    };
        }

        boolean runEval = config.stages().contains(Stage.EVALUATE);
        return pipeline.then(
                runEval
                        ? Mono.defer(
                                        () ->
                                                evaluateStage.run(
                                                        dataset,
                                                        checkpointStore.loadAnswerResults(runDir),
                                                        config))
                                .doOnSuccess(
                                        result -> {
                                            long elapsed = System.currentTimeMillis() - startMs;
                                            try {
                                                reportWriter.writeSearchResults(
                                                        checkpointStore.loadSearchResults(runDir),
                                                        runDir);
                                                reportWriter.writeAnswerResults(
                                                        checkpointStore.loadAnswerResults(runDir),
                                                        runDir);
                                            } catch (IOException e) {
                                                log.warn(
                                                        "Failed to write intermediate results: {}",
                                                        e.getMessage());
                                            }
                                            reportWriter.write(result, runDir, elapsed, config);
                                        })
                        : Mono.empty());
    }

    /**
     * Filter QAPair based on filterCategories, an empty list means no filtering
     */
    EvalDataset applyFilterCategories(EvalDataset dataset, List<String> filterCategories) {
        if (filterCategories == null || filterCategories.isEmpty()) {
            return dataset;
        }
        List<QAPair> filtered =
                dataset.qaPairs().stream()
                        .filter(qa -> !filterCategories.contains(qa.category()))
                        .toList();
        return new EvalDataset(dataset.name(), dataset.conversations(), filtered);
    }
}
