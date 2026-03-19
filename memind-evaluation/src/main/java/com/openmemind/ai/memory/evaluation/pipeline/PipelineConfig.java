package com.openmemind.ai.memory.evaluation.pipeline;

import com.openmemind.ai.memory.evaluation.adapter.AddMode;
import java.nio.file.Path;
import java.util.List;

/**
 * Evaluation pipeline configuration, including dataset, adapter, stage selection, concurrency, and various switch parameters
 *
 */
public record PipelineConfig(
        String datasetName,
        String adapterName,
        Path dataPath,
        List<Stage> stages,
        boolean smoke,
        int smokeMessages,
        int smokeQuestions,
        int fromConv,
        int toConv, // -1 = all
        int topK,
        int addConcurrency,
        int searchConcurrency,
        int convConcurrency,
        Path outputDir,
        String runName,
        // Call adapter.clean() for each conversationId before the ADD stage
        boolean cleanGroups,
        // Category filter: skip QA in this list of categories, empty list = no filter
        List<String> filterCategories,
        // Dual perspective switch (passed to adapter)
        boolean dualPerspective,
        // LLM Judge independent run count
        int numRuns,
        // Memory extraction mode (passed to adapter)
        AddMode addMode,
        // LLM model name (for eval_results output)
        String model) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String datasetName, adapterName, runName = "default";
        private Path dataPath, outputDir = Path.of("results");
        private List<Stage> stages = List.of(Stage.ADD, Stage.SEARCH, Stage.ANSWER, Stage.EVALUATE);
        private boolean smoke = false;
        private int smokeMessages = 10, smokeQuestions = 3;
        private int fromConv = 0, toConv = -1, topK = 20;
        private int addConcurrency = 1, searchConcurrency = 5, convConcurrency = 10;
        private boolean cleanGroups = false;
        private List<String> filterCategories = List.of();
        private boolean dualPerspective = false;
        private int numRuns = 3;
        private AddMode addMode = AddMode.STREAMING;
        private String model = "gpt-4o-mini";

        public Builder datasetName(String datasetName) {
            this.datasetName = datasetName;
            return this;
        }

        public Builder adapterName(String adapterName) {
            this.adapterName = adapterName;
            return this;
        }

        public Builder dataPath(Path dataPath) {
            this.dataPath = dataPath;
            return this;
        }

        public Builder stages(List<Stage> stages) {
            this.stages = stages;
            return this;
        }

        public Builder smoke(boolean smoke) {
            this.smoke = smoke;
            return this;
        }

        public Builder smokeMessages(int smokeMessages) {
            this.smokeMessages = smokeMessages;
            return this;
        }

        public Builder smokeQuestions(int smokeQuestions) {
            this.smokeQuestions = smokeQuestions;
            return this;
        }

        public Builder fromConv(int fromConv) {
            this.fromConv = fromConv;
            return this;
        }

        public Builder toConv(int toConv) {
            this.toConv = toConv;
            return this;
        }

        public Builder topK(int topK) {
            this.topK = topK;
            return this;
        }

        public Builder addConcurrency(int addConcurrency) {
            this.addConcurrency = addConcurrency;
            return this;
        }

        public Builder searchConcurrency(int searchConcurrency) {
            this.searchConcurrency = searchConcurrency;
            return this;
        }

        public Builder convConcurrency(int convConcurrency) {
            this.convConcurrency = convConcurrency;
            return this;
        }

        public Builder outputDir(Path outputDir) {
            this.outputDir = outputDir;
            return this;
        }

        public Builder runName(String runName) {
            this.runName = runName;
            return this;
        }

        public Builder cleanGroups(boolean cleanGroups) {
            this.cleanGroups = cleanGroups;
            return this;
        }

        public Builder filterCategories(List<String> filterCategories) {
            this.filterCategories = filterCategories;
            return this;
        }

        public Builder dualPerspective(boolean dualPerspective) {
            this.dualPerspective = dualPerspective;
            return this;
        }

        public Builder numRuns(int numRuns) {
            this.numRuns = numRuns;
            return this;
        }

        public Builder addMode(AddMode addMode) {
            this.addMode = addMode;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public PipelineConfig build() {
            return new PipelineConfig(
                    datasetName,
                    adapterName,
                    dataPath,
                    stages,
                    smoke,
                    smokeMessages,
                    smokeQuestions,
                    fromConv,
                    toConv,
                    topK,
                    addConcurrency,
                    searchConcurrency,
                    convConcurrency,
                    outputDir,
                    runName,
                    cleanGroups,
                    filterCategories,
                    dualPerspective,
                    numRuns,
                    addMode,
                    model);
        }
    }
}
