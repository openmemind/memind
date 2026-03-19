package com.openmemind.ai.memory.autoconfigure;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * memind retrieval configuration properties
 *
 */
@ConfigurationProperties(prefix = "memind.retrieval")
public class MemoryRetrievalProperties {

    private Duration timeout = Duration.ofSeconds(120);
    private boolean enableCache = true;
    private Rerank rerank = new Rerank();
    private SimpleStrategy simple = new SimpleStrategy();
    private DeepStrategy deep = new DeepStrategy();
    private Scoring scoring = new Scoring();

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public boolean isEnableCache() {
        return enableCache;
    }

    public void setEnableCache(boolean enableCache) {
        this.enableCache = enableCache;
    }

    public Rerank getRerank() {
        return rerank;
    }

    public void setRerank(Rerank rerank) {
        this.rerank = rerank;
    }

    public SimpleStrategy getSimple() {
        return simple;
    }

    public void setSimple(SimpleStrategy simple) {
        this.simple = simple;
    }

    public DeepStrategy getDeep() {
        return deep;
    }

    public void setDeep(DeepStrategy deep) {
        this.deep = deep;
    }

    public Scoring getScoring() {
        return scoring;
    }

    public void setScoring(Scoring scoring) {
        this.scoring = scoring;
    }

    public static class Rerank {
        private boolean enabled = true;
        private boolean blendWithRetrieval = false;
        private int topK = 10;
        private String baseUrl = "";
        private String apiKey = "";
        // Note: LlmReranker.DEFAULT_MODEL="qwen3-reranker-8b", we override to jina-reranker-v3 here
        private String model = "jina-reranker-v3";
        private double top3Weight = 0.75;
        private double top10Weight = 0.60;
        private double otherWeight = 0.40;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isBlendWithRetrieval() {
            return blendWithRetrieval;
        }

        public void setBlendWithRetrieval(boolean v) {
            this.blendWithRetrieval = v;
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

        public double getTop3Weight() {
            return top3Weight;
        }

        public void setTop3Weight(double v) {
            this.top3Weight = v;
        }

        public double getTop10Weight() {
            return top10Weight;
        }

        public void setTop10Weight(double v) {
            this.top10Weight = v;
        }

        public double getOtherWeight() {
            return otherWeight;
        }

        public void setOtherWeight(double v) {
            this.otherWeight = v;
        }
    }

    public static class SimpleStrategy {
        private boolean enableKeywordSearch = true;

        public boolean isEnableKeywordSearch() {
            return enableKeywordSearch;
        }

        public void setEnableKeywordSearch(boolean v) {
            this.enableKeywordSearch = v;
        }
    }

    public static class DeepStrategy {
        private int tier2InitTopK = 50;
        private int bm25InitTopK = 50;
        private double minScore = 0.3;
        private QueryExpansion queryExpansion = new QueryExpansion();
        private Sufficiency sufficiency = new Sufficiency();

        public int getTier2InitTopK() {
            return tier2InitTopK;
        }

        public void setTier2InitTopK(int v) {
            this.tier2InitTopK = v;
        }

        public int getBm25InitTopK() {
            return bm25InitTopK;
        }

        public void setBm25InitTopK(int v) {
            this.bm25InitTopK = v;
        }

        public double getMinScore() {
            return minScore;
        }

        public void setMinScore(double v) {
            this.minScore = v;
        }

        public QueryExpansion getQueryExpansion() {
            return queryExpansion;
        }

        public void setQueryExpansion(QueryExpansion v) {
            this.queryExpansion = v;
        }

        public Sufficiency getSufficiency() {
            return sufficiency;
        }

        public void setSufficiency(Sufficiency v) {
            this.sufficiency = v;
        }

        public static class QueryExpansion {
            private int maxExpandedQueries = 3;
            private double originalWeight = 2.0;
            private double expandedWeight = 1.0;

            public int getMaxExpandedQueries() {
                return maxExpandedQueries;
            }

            public void setMaxExpandedQueries(int v) {
                this.maxExpandedQueries = v;
            }

            public double getOriginalWeight() {
                return originalWeight;
            }

            public void setOriginalWeight(double v) {
                this.originalWeight = v;
            }

            public double getExpandedWeight() {
                return expandedWeight;
            }

            public void setExpandedWeight(double v) {
                this.expandedWeight = v;
            }
        }

        public static class Sufficiency {
            private int itemTopK = 20;

            public int getItemTopK() {
                return itemTopK;
            }

            public void setItemTopK(int v) {
                this.itemTopK = v;
            }
        }
    }

    public static class Scoring {
        private Fusion fusion = new Fusion();
        private TimeDecay timeDecay = new TimeDecay();
        private Recency recency = new Recency();
        private PositionBonus positionBonus = new PositionBonus();
        private KeywordSearch keywordSearch = new KeywordSearch();
        private QueryWeight queryWeight = new QueryWeight();
        private int candidateMultiplier = 2;
        private int rerankCandidateLimit = 40;
        private int rawDataKeyInfoMaxLines = 5;
        private int insightLlmThreshold = 15;

        public Fusion getFusion() {
            return fusion;
        }

        public void setFusion(Fusion v) {
            this.fusion = v;
        }

        public TimeDecay getTimeDecay() {
            return timeDecay;
        }

        public void setTimeDecay(TimeDecay v) {
            this.timeDecay = v;
        }

        public Recency getRecency() {
            return recency;
        }

        public void setRecency(Recency v) {
            this.recency = v;
        }

        public PositionBonus getPositionBonus() {
            return positionBonus;
        }

        public void setPositionBonus(PositionBonus v) {
            this.positionBonus = v;
        }

        public KeywordSearch getKeywordSearch() {
            return keywordSearch;
        }

        public void setKeywordSearch(KeywordSearch v) {
            this.keywordSearch = v;
        }

        public QueryWeight getQueryWeight() {
            return queryWeight;
        }

        public void setQueryWeight(QueryWeight v) {
            this.queryWeight = v;
        }

        public int getCandidateMultiplier() {
            return candidateMultiplier;
        }

        public void setCandidateMultiplier(int v) {
            this.candidateMultiplier = v;
        }

        public int getRerankCandidateLimit() {
            return rerankCandidateLimit;
        }

        public void setRerankCandidateLimit(int v) {
            this.rerankCandidateLimit = v;
        }

        public int getRawDataKeyInfoMaxLines() {
            return rawDataKeyInfoMaxLines;
        }

        public void setRawDataKeyInfoMaxLines(int v) {
            this.rawDataKeyInfoMaxLines = v;
        }

        public int getInsightLlmThreshold() {
            return insightLlmThreshold;
        }

        public void setInsightLlmThreshold(int v) {
            this.insightLlmThreshold = v;
        }

        public static class Fusion {
            private int k = 60;
            private double vectorWeight = 1.5;
            private double keywordWeight = 1.0;

            public int getK() {
                return k;
            }

            public void setK(int k) {
                this.k = k;
            }

            public double getVectorWeight() {
                return vectorWeight;
            }

            public void setVectorWeight(double v) {
                this.vectorWeight = v;
            }

            public double getKeywordWeight() {
                return keywordWeight;
            }

            public void setKeywordWeight(double v) {
                this.keywordWeight = v;
            }
        }

        public static class TimeDecay {
            private double rate = 0.023;
            private double floor = 0.3;
            private double outOfRangePenalty = 0.5;

            public double getRate() {
                return rate;
            }

            public void setRate(double v) {
                this.rate = v;
            }

            public double getFloor() {
                return floor;
            }

            public void setFloor(double v) {
                this.floor = v;
            }

            public double getOutOfRangePenalty() {
                return outOfRangePenalty;
            }

            public void setOutOfRangePenalty(double v) {
                this.outOfRangePenalty = v;
            }
        }

        public static class Recency {
            private double rate = 0.0019;
            private double floor = 0.7;

            public double getRate() {
                return rate;
            }

            public void setRate(double v) {
                this.rate = v;
            }

            public double getFloor() {
                return floor;
            }

            public void setFloor(double v) {
                this.floor = v;
            }
        }

        public static class PositionBonus {
            private double top1 = 0.05;
            private double top3 = 0.02;

            public double getTop1() {
                return top1;
            }

            public void setTop1(double v) {
                this.top1 = v;
            }

            public double getTop3() {
                return top3;
            }

            public void setTop3(double v) {
                this.top3 = v;
            }
        }

        public static class KeywordSearch {
            private int probeTopK = 10;
            private double strongSignalMinScore = 0.85;
            private double strongSignalMinGap = 0.15;

            public int getProbeTopK() {
                return probeTopK;
            }

            public void setProbeTopK(int v) {
                this.probeTopK = v;
            }

            public double getStrongSignalMinScore() {
                return strongSignalMinScore;
            }

            public void setStrongSignalMinScore(double v) {
                this.strongSignalMinScore = v;
            }

            public double getStrongSignalMinGap() {
                return strongSignalMinGap;
            }

            public void setStrongSignalMinGap(double v) {
                this.strongSignalMinGap = v;
            }
        }

        public static class QueryWeight {
            private double originalWeight = 2.0;
            private double expandedWeight = 1.0;

            public double getOriginalWeight() {
                return originalWeight;
            }

            public void setOriginalWeight(double v) {
                this.originalWeight = v;
            }

            public double getExpandedWeight() {
                return expandedWeight;
            }

            public void setExpandedWeight(double v) {
                this.expandedWeight = v;
            }
        }
    }
}
