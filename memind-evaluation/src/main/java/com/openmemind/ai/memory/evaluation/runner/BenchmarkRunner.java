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
package com.openmemind.ai.memory.evaluation.runner;

import com.openmemind.ai.memory.evaluation.adapter.AddMode;
import com.openmemind.ai.memory.evaluation.config.ActiveDatasetProfileResolver;
import com.openmemind.ai.memory.evaluation.config.DatasetProfile;
import com.openmemind.ai.memory.evaluation.config.EvaluationProperties;
import com.openmemind.ai.memory.evaluation.pipeline.BenchmarkPipeline;
import com.openmemind.ai.memory.evaluation.pipeline.PipelineConfig;
import com.openmemind.ai.memory.evaluation.pipeline.Stage;
import com.openmemind.ai.memory.evaluation.pipeline.model.EvaluationResult;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Spring Boot ApplicationRunner entry: The evaluation pipeline runs directly after the application starts, without Spring Shell.
 * All parameters are passed in through application.yml or command line --evaluation.* overrides.
 *
 * <p>Example:
 *
 * <pre>
 * java -jar app.jar \
 *   --evaluation.dataset.path=data/locomo/locomo10.json \
 *   --evaluation.from-conv=0 \
 *   --evaluation.to-conv=1 \
 *   --evaluation.run-name=test-conv0
 * </pre>
 *
 */
/**
 * Enabled by {@code --evaluation.auto-run=true}, used for non-interactive automatic run mode (CI/CD).
 * This Runner is not needed in interactive Spring Shell mode.
 */
@Component
@ConditionalOnProperty(name = "evaluation.auto-run", havingValue = "true")
public class BenchmarkRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BenchmarkRunner.class);

    private final BenchmarkPipeline pipeline;
    private final EvaluationProperties props;
    private final ActiveDatasetProfileResolver datasetProfileResolver;
    private final String chatModel;

    public BenchmarkRunner(
            BenchmarkPipeline pipeline,
            EvaluationProperties props,
            ActiveDatasetProfileResolver datasetProfileResolver,
            @Value("${spring.ai.openai.chat.options.model:unknown}") String chatModel) {
        this.pipeline = pipeline;
        this.props = props;
        this.datasetProfileResolver = datasetProfileResolver;
        this.chatModel = chatModel;
    }

    @Override
    public void run(ApplicationArguments args) {
        DatasetProfile datasetProfile = datasetProfileResolver.resolve();
        if (datasetProfile.path() == null) {
            log.warn(
                    "Dataset path is not configured for active dataset {}, skipping automatic run.",
                    props.getActiveDataset());
            return;
        }

        List<Stage> stageList =
                Arrays.stream(props.getStages().split(","))
                        .map(String::trim)
                        .map(String::toUpperCase)
                        .map(Stage::valueOf)
                        .toList();

        PipelineConfig config =
                PipelineConfig.builder()
                        .datasetName(datasetProfile.name())
                        .sourceFormat(datasetProfile.sourceFormat())
                        .loaderFormat(datasetProfile.loaderFormat())
                        .dataPath(datasetProfile.path())
                        .stages(stageList)
                        .smoke(props.isSmoke())
                        .smokeMessages(props.getSmokeMessages())
                        .smokeQuestions(props.getSmokeQuestions())
                        .fromConv(props.getFromConv())
                        .toConv(props.getToConv())
                        .topK(props.getSystem().getSearch().getTopK())
                        .addConcurrency(props.getConcurrency().getAdd())
                        .searchConcurrency(props.getConcurrency().getSearch())
                        .convConcurrency(props.getConcurrency().getConv())
                        .answerConcurrency(props.getConcurrency().getAnswer())
                        .evaluateConcurrency(props.getConcurrency().getEvaluate())
                        .outputDir(Path.of(props.getOutputDir()))
                        .runName(props.getRunName())
                        .cleanGroups(props.isCleanGroups())
                        .filterCategories(datasetProfile.filterCategories())
                        .maxContentLength(datasetProfile.maxContentLength())
                        .searchQueryMode(datasetProfile.searchQueryMode())
                        .judgeStrategy(datasetProfile.judgeStrategy())
                        .dualPerspective(props.getSystem().getSearch().isDualPerspective())
                        .numRuns(props.getSystem().getLlm().getNumRuns())
                        .addMode(
                                AddMode.valueOf(
                                        props.getSystem().getMemind().getAddMode().toUpperCase()))
                        .model(chatModel)
                        .evalModel(props.getSystem().getLlm().getEvalModel())
                        .build();

        log.info(
                "Starting benchmark: dataset={} stages={} conv=[{},{})",
                datasetProfile.name(),
                props.getStages(),
                props.getFromConv(),
                props.getToConv());

        EvaluationResult result = pipeline.run(config).block();

        if (result != null) {
            System.out.printf(
                    "%nBenchmark complete: accuracy=%.2f%% (mean=%.2f%% ± std=%.2f%%)  (%d/%d)%n",
                    result.accuracy() * 100,
                    result.meanAccuracy() * 100,
                    result.stdAccuracy() * 100,
                    result.correct(),
                    result.totalQuestions());
            System.out.printf(
                    "Report saved to: %s/%s-%s/report.txt%n",
                    props.getOutputDir(), datasetProfile.name(), props.getRunName());
        }
    }
}
