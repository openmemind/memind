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
package com.openmemind.ai.memory.evaluation.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Evaluation framework configuration properties, bound to the evaluation.* prefix in application.yml
 *
 */
@ConfigurationProperties(prefix = "evaluation")
public class EvaluationProperties {

    private String outputDir = "results";
    private int fromConv = 0;
    private int toConv = -1;
    private String runName = "default";
    private String stages = "add,search,answer,evaluate";
    private boolean smoke = false;
    private int smokeMessages = 10;
    private int smokeQuestions = 3;
    private boolean cleanGroups = false;
    private DatasetProperties dataset = new DatasetProperties();
    private SystemProperties system = new SystemProperties();
    private ConcurrencyProperties concurrency = new ConcurrencyProperties();

    public String getOutputDir() {
        return outputDir;
    }

    public void setOutputDir(String outputDir) {
        this.outputDir = outputDir;
    }

    public int getFromConv() {
        return fromConv;
    }

    public void setFromConv(int fromConv) {
        this.fromConv = fromConv;
    }

    public int getToConv() {
        return toConv;
    }

    public void setToConv(int toConv) {
        this.toConv = toConv;
    }

    public String getRunName() {
        return runName;
    }

    public void setRunName(String runName) {
        this.runName = runName;
    }

    public String getStages() {
        return stages;
    }

    public void setStages(String stages) {
        this.stages = stages;
    }

    public boolean isSmoke() {
        return smoke;
    }

    public void setSmoke(boolean smoke) {
        this.smoke = smoke;
    }

    public int getSmokeMessages() {
        return smokeMessages;
    }

    public void setSmokeMessages(int smokeMessages) {
        this.smokeMessages = smokeMessages;
    }

    public int getSmokeQuestions() {
        return smokeQuestions;
    }

    public void setSmokeQuestions(int smokeQuestions) {
        this.smokeQuestions = smokeQuestions;
    }

    public boolean isCleanGroups() {
        return cleanGroups;
    }

    public void setCleanGroups(boolean cleanGroups) {
        this.cleanGroups = cleanGroups;
    }

    public DatasetProperties getDataset() {
        return dataset;
    }

    public void setDataset(DatasetProperties dataset) {
        this.dataset = dataset;
    }

    public SystemProperties getSystem() {
        return system;
    }

    public void setSystem(SystemProperties system) {
        this.system = system;
    }

    public ConcurrencyProperties getConcurrency() {
        return concurrency;
    }

    public void setConcurrency(ConcurrencyProperties concurrency) {
        this.concurrency = concurrency;
    }

    public static class ConcurrencyProperties {
        private int add = 1;
        private int search = 10;
        private int conv = 5;

        public int getAdd() {
            return add;
        }

        public void setAdd(int add) {
            this.add = add;
        }

        public int getSearch() {
            return search;
        }

        public void setSearch(int search) {
            this.search = search;
        }

        public int getConv() {
            return conv;
        }

        public void setConv(int conv) {
            this.conv = conv;
        }
    }

    public static class DatasetProperties {

        private String name = "locomo";
        private String path;
        private Integer maxContentLength;
        private List<String> filterCategories = new ArrayList<>();

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public Integer getMaxContentLength() {
            return maxContentLength;
        }

        public void setMaxContentLength(Integer maxContentLength) {
            this.maxContentLength = maxContentLength;
        }

        public List<String> getFilterCategories() {
            return filterCategories;
        }

        public void setFilterCategories(List<String> filterCategories) {
            this.filterCategories = filterCategories;
        }
    }

    public static class SystemProperties {

        private String adapter = "memind";
        private SearchProperties search = new SearchProperties();
        private AnswerProperties answer = new AnswerProperties();
        private LlmProperties llm = new LlmProperties();
        private MemindProperties memind = new MemindProperties();

        public String getAdapter() {
            return adapter;
        }

        public void setAdapter(String adapter) {
            this.adapter = adapter;
        }

        public SearchProperties getSearch() {
            return search;
        }

        public void setSearch(SearchProperties search) {
            this.search = search;
        }

        public AnswerProperties getAnswer() {
            return answer;
        }

        public void setAnswer(AnswerProperties answer) {
            this.answer = answer;
        }

        public LlmProperties getLlm() {
            return llm;
        }

        public void setLlm(LlmProperties llm) {
            this.llm = llm;
        }

        public MemindProperties getMemind() {
            return memind;
        }

        public void setMemind(MemindProperties memind) {
            this.memind = memind;
        }

        public static class SearchProperties {

            private int topK = 20;
            private boolean dualPerspective = false;

            public int getTopK() {
                return topK;
            }

            public void setTopK(int topK) {
                this.topK = topK;
            }

            public boolean isDualPerspective() {
                return dualPerspective;
            }

            public void setDualPerspective(boolean dualPerspective) {
                this.dualPerspective = dualPerspective;
            }
        }

        public static class MemindProperties {

            private String addMode = "context";
            private boolean enableInsight = false;
            private StorageProperties storage = new StorageProperties();
            private ExtractionProperties extraction = new ExtractionProperties();
            private RetrievalProperties retrieval = new RetrievalProperties();

            public String getAddMode() {
                return addMode;
            }

            public void setAddMode(String addMode) {
                this.addMode = addMode;
            }

            public boolean isEnableInsight() {
                return enableInsight;
            }

            public void setEnableInsight(boolean enableInsight) {
                this.enableInsight = enableInsight;
            }

            public StorageProperties getStorage() {
                return storage;
            }

            public void setStorage(StorageProperties storage) {
                this.storage = storage;
            }

            public ExtractionProperties getExtraction() {
                return extraction;
            }

            public void setExtraction(ExtractionProperties extraction) {
                this.extraction = extraction;
            }

            public RetrievalProperties getRetrieval() {
                return retrieval;
            }

            public void setRetrieval(RetrievalProperties retrieval) {
                this.retrieval = retrieval;
            }

            public static class StorageProperties {

                private SqliteProperties sqlite = new SqliteProperties();

                public SqliteProperties getSqlite() {
                    return sqlite;
                }

                public void setSqlite(SqliteProperties sqlite) {
                    this.sqlite = sqlite;
                }
            }

            public static class SqliteProperties {

                private String path = "./eval-data/memind-eval.db";

                public String getPath() {
                    return path;
                }

                public void setPath(String path) {
                    this.path = path;
                }
            }

            public static class ExtractionProperties {

                private BoundaryProperties boundary = new BoundaryProperties();

                public BoundaryProperties getBoundary() {
                    return boundary;
                }

                public void setBoundary(BoundaryProperties boundary) {
                    this.boundary = boundary;
                }
            }

            public static class BoundaryProperties {

                private int maxMessages = 30;
                private int maxTokens = 8_000;
                private int minMessagesForLlm = 5;

                public int getMaxMessages() {
                    return maxMessages;
                }

                public void setMaxMessages(int maxMessages) {
                    this.maxMessages = maxMessages;
                }

                public int getMaxTokens() {
                    return maxTokens;
                }

                public void setMaxTokens(int maxTokens) {
                    this.maxTokens = maxTokens;
                }

                public int getMinMessagesForLlm() {
                    return minMessagesForLlm;
                }

                public void setMinMessagesForLlm(int minMessagesForLlm) {
                    this.minMessagesForLlm = minMessagesForLlm;
                }
            }

            public static class RetrievalProperties {

                private Duration timeout = Duration.ofMinutes(5);
                private RerankProperties rerank = new RerankProperties();

                public Duration getTimeout() {
                    return timeout;
                }

                public void setTimeout(Duration timeout) {
                    this.timeout = timeout;
                }

                public RerankProperties getRerank() {
                    return rerank;
                }

                public void setRerank(RerankProperties rerank) {
                    this.rerank = rerank;
                }
            }

            public static class RerankProperties {

                private boolean enabled = true;
                private boolean blendWithRetrieval = true;
                private int topK = 20;
                private String baseUrl = "";
                private String apiKey = "";
                private String model = "jina-reranker-v3";

                public boolean isEnabled() {
                    return enabled;
                }

                public void setEnabled(boolean enabled) {
                    this.enabled = enabled;
                }

                public boolean isBlendWithRetrieval() {
                    return blendWithRetrieval;
                }

                public void setBlendWithRetrieval(boolean blendWithRetrieval) {
                    this.blendWithRetrieval = blendWithRetrieval;
                }

                public int getTopK() {
                    return topK;
                }

                public void setTopK(int topK) {
                    this.topK = topK;
                }

                public String getBaseUrl() {
                    return baseUrl;
                }

                public void setBaseUrl(String baseUrl) {
                    this.baseUrl = baseUrl;
                }

                public String getApiKey() {
                    return apiKey;
                }

                public void setApiKey(String apiKey) {
                    this.apiKey = apiKey;
                }

                public String getModel() {
                    return model;
                }

                public void setModel(String model) {
                    this.model = model;
                }
            }
        }

        public static class AnswerProperties {

            private int maxRetries = 3;

            public int getMaxRetries() {
                return maxRetries;
            }

            public void setMaxRetries(int maxRetries) {
                this.maxRetries = maxRetries;
            }
        }

        public static class LlmProperties {

            private String model = "gpt-4o-mini";
            private int numRuns = 3;

            public String getModel() {
                return model;
            }

            public void setModel(String model) {
                this.model = model;
            }

            public int getNumRuns() {
                return numRuns;
            }

            public void setNumRuns(int numRuns) {
                this.numRuns = numRuns;
            }
        }
    }
}
