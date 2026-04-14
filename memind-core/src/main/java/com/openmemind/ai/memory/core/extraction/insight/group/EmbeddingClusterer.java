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
package com.openmemind.ai.memory.core.extraction.insight.group;

import com.openmemind.ai.memory.core.utils.VectorUtils;
import java.util.ArrayList;
import java.util.List;

/**
 * Running Centroid + Dual Gate (Semantic + Time) Greedy Clustering
 *
 * <p>Based on the EverMemOS idea: use Welford's online mean to update the centroid,
 * while using cosine similarity and time interval dual gating to decide whether to belong to an existing cluster.
 *
 */
public final class EmbeddingClusterer {

    private EmbeddingClusterer() {}

    /**
     * @param similarityThreshold Cosine similarity threshold [0.0, 1.0]
     * @param maxClusterSize      Maximum number of elements per cluster
     * @param maxTimeGapSeconds   Time interval limit (seconds), items exceeding this value do not belong to the same cluster, 0 = disable
     */
    public record ClusterConfig(
            double similarityThreshold, int maxClusterSize, long maxTimeGapSeconds) {

        public static ClusterConfig defaults() {
            return new ClusterConfig(0.65, 25, 7 * 24 * 3600);
        }
    }

    /**
     * Greedy clustering of items based on embedding similarity + time interval
     *
     * @param items      List of elements to be clustered
     * @param embeddings Corresponding embedding vectors, one-to-one with items
     * @param timestamps Corresponding timestamps (epochSecond), one-to-one with items, null means disable time gating
     * @param config     Clustering configuration
     * @return Clustering result
     */
    public static <T> List<List<T>> cluster(
            List<T> items,
            List<List<Float>> embeddings,
            List<Long> timestamps,
            ClusterConfig config) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        if (embeddings == null || items.size() != embeddings.size()) {
            throw new IllegalArgumentException(
                    "items and embeddings length do not match: "
                            + items.size()
                            + " vs "
                            + (embeddings == null ? "null" : embeddings.size()));
        }
        if (timestamps != null && timestamps.size() != items.size()) {
            throw new IllegalArgumentException(
                    "items and timestamps length do not match: "
                            + items.size()
                            + " vs "
                            + timestamps.size());
        }

        // Short-circuit: return a single cluster directly when items <= maxClusterSize
        if (items.size() <= config.maxClusterSize()) {
            return List.of(new ArrayList<>(items));
        }

        var clusters = new ArrayList<ClusterState<T>>();

        for (int i = 0; i < items.size(); i++) {
            var item = items.get(i);
            var embedding = embeddings.get(i);
            var timestamp = timestamps != null ? timestamps.get(i) : 0L;

            int bestCluster = -1;
            double bestSimilarity = -1;

            for (int c = 0; c < clusters.size(); c++) {
                var cluster = clusters.get(c);

                // Skip if full
                if (cluster.members.size() >= config.maxClusterSize()) {
                    continue;
                }

                // Time gating
                if (timestamps != null && config.maxTimeGapSeconds() > 0) {
                    long gap = Math.abs(timestamp - cluster.lastTimestamp);
                    if (gap > config.maxTimeGapSeconds()) {
                        continue;
                    }
                }

                // Semantic gating
                double sim = VectorUtils.cosineSimilarity(embedding, cluster.centroid);
                if (sim >= config.similarityThreshold() && sim > bestSimilarity) {
                    bestSimilarity = sim;
                    bestCluster = c;
                }
            }

            if (bestCluster >= 0) {
                var cluster = clusters.get(bestCluster);
                cluster.addMember(item, embedding, timestamp);
            } else {
                clusters.add(new ClusterState<>(item, embedding, timestamp));
            }
        }

        return clusters.stream().map(c -> (List<T>) c.members).toList();
    }

    /**
     * Runtime state of the cluster, maintaining running centroid
     */
    private static final class ClusterState<T> {

        final List<T> members = new ArrayList<>();
        List<Float> centroid;

        /** Accumulated sum vector of the centroid (for incremental updates) */
        List<Float> sum;

        int count;
        long lastTimestamp;

        ClusterState(T seed, List<Float> embedding, long timestamp) {
            members.add(seed);
            centroid = new ArrayList<>(embedding);
            sum = new ArrayList<>(embedding);
            count = 1;
            lastTimestamp = timestamp;
        }

        void addMember(T item, List<Float> embedding, long timestamp) {
            members.add(item);
            sum = VectorUtils.add(sum, embedding);
            count++;
            centroid = VectorUtils.divide(sum, count);
            lastTimestamp = Math.max(lastTimestamp, timestamp);
        }
    }
}
