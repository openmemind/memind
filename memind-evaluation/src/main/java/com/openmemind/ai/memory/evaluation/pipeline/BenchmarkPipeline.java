package com.openmemind.ai.memory.evaluation.pipeline;

import com.openmemind.ai.memory.evaluation.adapter.AdapterRegistry;
import com.openmemind.ai.memory.evaluation.adapter.MemoryAdapter;
import com.openmemind.ai.memory.evaluation.adapter.memind.MemindAdapter;
import com.openmemind.ai.memory.evaluation.checkpoint.CheckpointState;
import com.openmemind.ai.memory.evaluation.checkpoint.CheckpointStore;
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

    private final AdapterRegistry adapterRegistry;
    private final Map<String, DatasetLoader> loaders;
    private final CheckpointStore checkpointStore;
    private final ConverterRegistry converterRegistry;
    private final AddStage addStage;
    private final SearchStage searchStage;
    private final AnswerStage answerStage;
    private final EvaluateStage evaluateStage;
    private final ReportWriter reportWriter;

    public BenchmarkPipeline(
            AdapterRegistry adapterRegistry,
            List<DatasetLoader> loaders,
            CheckpointStore checkpointStore,
            ConverterRegistry converterRegistry,
            AddStage addStage,
            SearchStage searchStage,
            AnswerStage answerStage,
            EvaluateStage evaluateStage,
            ReportWriter reportWriter) {
        this.adapterRegistry = adapterRegistry;
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
        MemoryAdapter adapter = adapterRegistry.get(config.adapterName());

        // Pass the dualPerspective and addMode parameters from CLI to MemindAdapter
        if (adapter instanceof MemindAdapter m4j) {
            m4j.setDualPerspective(config.dualPerspective());
            m4j.setAddMode(config.addMode());
        }

        DatasetLoader loader = loaders.get(config.datasetName());
        if (loader == null)
            throw new IllegalArgumentException("Unknown dataset: " + config.datasetName());

        // Calculate runDir using config.outputDir() to make --output-dir CLI parameter effective
        Path runDir =
                config.outputDir()
                        .resolve(
                                config.datasetName()
                                        + "-"
                                        + config.adapterName()
                                        + "-"
                                        + config.runName());

        Path effectivePath =
                converterRegistry.convertIfNeeded(config.datasetName(), config.dataPath(), runDir);
        EvalDataset rawDataset = loader.load(effectivePath);

        // Category filtering: skip QAPair with category in filterCategories
        EvalDataset dataset = applyFilterCategories(rawDataset, config.filterCategories());
        CheckpointState checkpoint =
                checkpointStore.load(config.datasetName(), config.adapterName(), config.runName());

        log.info(
                "Starting benchmark: dataset={} adapter={} conversations={} qaPairs={}",
                config.datasetName(),
                config.adapterName(),
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
                                            reportWriter.write(
                                                    result,
                                                    runDir,
                                                    config.datasetName(),
                                                    config.adapterName(),
                                                    config.runName(),
                                                    elapsed,
                                                    config);
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
