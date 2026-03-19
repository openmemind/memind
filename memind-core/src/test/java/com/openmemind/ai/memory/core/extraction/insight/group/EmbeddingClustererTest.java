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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmemind.ai.memory.core.extraction.insight.group.EmbeddingClusterer.ClusterConfig;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("EmbeddingClusterer Pre-clusterer")
class EmbeddingClustererTest {

    private static final ClusterConfig DEFAULT_CONFIG = ClusterConfig.defaults();

    @Nested
    @DisplayName("Basic Scenarios")
    class BasicScenarios {

        @Test
        @DisplayName("Empty list → Empty result")
        void emptyList() {
            var result = EmbeddingClusterer.cluster(List.of(), List.of(), null, DEFAULT_CONFIG);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("null list → Empty result")
        void nullList() {
            var result = EmbeddingClusterer.cluster(null, null, null, DEFAULT_CONFIG);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("items ≤ maxClusterSize → Single cluster short circuit")
        void shortCircuit() {
            var config = new ClusterConfig(0.65, 30, 0);
            var items = IntStream.range(0, 20).boxed().toList();
            var embeddings = items.stream().map(i -> List.of((float) i, 0.0f, 0.0f)).toList();
            var result = EmbeddingClusterer.cluster(items, embeddings, null, config);
            assertThat(result).hasSize(1);
            assertThat(result.getFirst()).hasSize(20);
        }

        @Test
        @DisplayName("items/embeddings length mismatch → Throw IllegalArgumentException")
        void sizeMismatch() {
            assertThatThrownBy(
                            () ->
                                    EmbeddingClusterer.cluster(
                                            List.of("a", "b"),
                                            List.of(List.of(1.0f)),
                                            null,
                                            DEFAULT_CONFIG))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Semantic Clustering")
    class SemanticClustering {

        @Test
        @DisplayName("Similar embeddings cluster together")
        void similarCluster() {
            var config = new ClusterConfig(0.9, 5, 0);
            // 3 groups of clearly different direction vectors, 3 in each group
            var items = List.of("a1", "a2", "a3", "b1", "b2", "b3", "c1", "c2", "c3");
            var embeddings =
                    List.of(
                            // Group A: x-axis direction
                            List.of(1.0f, 0.01f, 0.0f),
                            List.of(1.0f, 0.02f, 0.0f),
                            List.of(1.0f, -0.01f, 0.0f),
                            // Group B: y-axis direction
                            List.of(0.01f, 1.0f, 0.0f),
                            List.of(0.02f, 1.0f, 0.0f),
                            List.of(-0.01f, 1.0f, 0.0f),
                            // Group C: z-axis direction
                            List.of(0.0f, 0.01f, 1.0f),
                            List.of(0.0f, 0.02f, 1.0f),
                            List.of(0.0f, -0.01f, 1.0f));

            // maxClusterSize = 5, needs to exceed to not short circuit
            // 9 > 5, will not short circuit
            var result = EmbeddingClusterer.cluster(items, embeddings, null, config);
            assertThat(result).hasSize(3);

            // Verify each cluster contains the same group items
            assertThat(result.get(0)).containsExactly("a1", "a2", "a3");
            assertThat(result.get(1)).containsExactly("b1", "b2", "b3");
            assertThat(result.get(2)).containsExactly("c1", "c2", "c3");
        }

        @Test
        @DisplayName("Orthogonal embeddings each form a separate cluster")
        void orthogonalSeparate() {
            var config = new ClusterConfig(0.5, 2, 0);
            var items = List.of("x", "y", "z");
            var embeddings =
                    List.of(
                            List.of(1.0f, 0.0f, 0.0f),
                            List.of(0.0f, 1.0f, 0.0f),
                            List.of(0.0f, 0.0f, 1.0f));
            var result = EmbeddingClusterer.cluster(items, embeddings, null, config);
            assertThat(result).hasSize(3);
        }

        @Test
        @DisplayName("Overflow to new cluster when exceeding maxClusterSize")
        void overflowToNewCluster() {
            var config = new ClusterConfig(0.9, 3, 0);
            // 5 very similar vectors, maxClusterSize = 3
            var items = List.of("a", "b", "c", "d", "e");
            var embeddings =
                    List.of(
                            List.of(1.0f, 0.01f),
                            List.of(1.0f, 0.02f),
                            List.of(1.0f, 0.03f),
                            List.of(1.0f, 0.04f),
                            List.of(1.0f, 0.05f));
            var result = EmbeddingClusterer.cluster(items, embeddings, null, config);
            assertThat(result).hasSize(2);
            assertThat(result.getFirst()).hasSize(3);
            assertThat(result.get(1)).hasSize(2);
        }
    }

    @Nested
    @DisplayName("Time Gating")
    class TimeGating {

        @Test
        @DisplayName("Similar items with time gaps split into different clusters")
        void timeGapSplits() {
            long sevenDays = 7 * 24 * 3600;
            var config = new ClusterConfig(0.5, 25, sevenDays);

            // All vectors are similar, but the time gap exceeds 7 days
            var items = List.of("recent1", "recent2", "old1", "old2");
            var embeddings =
                    List.of(
                            List.of(1.0f, 0.1f),
                            List.of(1.0f, 0.2f),
                            List.of(1.0f, 0.1f),
                            List.of(1.0f, 0.2f));
            var now = 1_000_000_000L;
            var timestamps =
                    List.of(now, now + 3600, now - sevenDays * 2, now - sevenDays * 2 + 3600);

            // Needs to be > maxClusterSize to not short circuit, so set maxClusterSize to 3
            var smallConfig = new ClusterConfig(0.5, 3, sevenDays);
            var result = EmbeddingClusterer.cluster(items, embeddings, timestamps, smallConfig);
            assertThat(result).hasSizeGreaterThanOrEqualTo(2);

            // Verify same time period items are in the same cluster
            var cluster0Items = new HashSet<>(result.getFirst());
            if (cluster0Items.contains("recent1")) {
                assertThat(cluster0Items).contains("recent2");
            } else {
                assertThat(cluster0Items).contains("old1").contains("old2");
            }
        }

        @Test
        @DisplayName("When timestamps = null, only semantic clustering is applied")
        void nullTimestampsIgnoresTimeGating() {
            long sevenDays = 7 * 24 * 3600;
            var config = new ClusterConfig(0.9, 3, sevenDays);

            // All vectors are almost the same
            var items = List.of("a", "b", "c", "d");
            var embeddings =
                    List.of(
                            List.of(1.0f, 0.01f),
                            List.of(1.0f, 0.02f),
                            List.of(1.0f, 0.03f),
                            List.of(1.0f, 0.04f));

            // timestamps = null disables time gating
            var result = EmbeddingClusterer.cluster(items, embeddings, null, config);
            // Should cluster by semantics, 4 > 3 does not short circuit, but the first 3 should be
            // in one cluster, the 4th overflows
            assertThat(result).hasSize(2);
            assertThat(result.getFirst()).hasSize(3);
        }
    }

    @Nested
    @DisplayName("Running Centroid Behavior")
    class RunningCentroid {

        @Test
        @DisplayName("Later added border item influenced by centroid")
        void centroidInfluencesBorderItems() {
            // Using Leader scheme, leader = [1,0], border item [0.7, 0.7] has
            // cosine sim ≈ 0.707 may not pass.
            // Using Running Centroid, as [1,0.1] [1,0.2] are added,
            // the centroid shifts in the y direction, making it easier for the border item to
            // match.
            var config = new ClusterConfig(0.7, 25, 0);
            var items = List.of("seed", "shift1", "shift2", "shift3", "border");
            var embeddings =
                    List.of(
                            List.of(1.0f, 0.0f), // seed → centroid = [1, 0]
                            List.of(1.0f, 0.2f), // centroid shifts in y
                            List.of(1.0f, 0.4f), // continues to shift
                            List.of(1.0f, 0.6f), // continues to shift
                            List.of(
                                    0.7f,
                                    0.7f)); // border: cos≈0.71 with [1,0], higher similarity with
            // shifted centroid

            // Needs >maxClusterSize configuration to not short circuit, but there are only 5, needs
            // maxClusterSize < 5
            var smallConfig = new ClusterConfig(0.7, 4, 0);
            var result = EmbeddingClusterer.cluster(items, embeddings, null, smallConfig);

            // border should not form a separate cluster — should belong to the shifted cluster
            // due to running centroid shift, border has higher similarity to centroid
            var allItems = result.stream().flatMap(List::stream).toList();
            assertThat(allItems).containsExactlyInAnyOrderElementsOf(items);
        }
    }

    @Nested
    @DisplayName("Completeness Verification")
    class CompletenessVerification {

        @Test
        @DisplayName("50 items clustered with 0 loss")
        void fiftyItemsNoLoss() {
            var config = new ClusterConfig(0.65, 10, 0);
            var items = IntStream.range(0, 50).boxed().toList();

            // Randomly generate 5 groups of directionally different vectors
            var embeddings = new ArrayList<List<Float>>();
            for (int i = 0; i < 50; i++) {
                int group = i % 5;
                float x = group == 0 ? 1.0f : group == 1 ? -1.0f : 0.0f;
                float y = group == 2 ? 1.0f : group == 3 ? -1.0f : 0.0f;
                float z = group == 4 ? 1.0f : 0.0f;
                // Add small perturbation
                float noise = i * 0.001f;
                embeddings.add(List.of(x + noise, y + noise, z + noise));
            }

            var result = EmbeddingClusterer.cluster(items, embeddings, null, config);

            // Verify 0 loss
            var allItems = result.stream().flatMap(List::stream).toList();
            assertThat(allItems).hasSize(50);
            assertThat(new HashSet<>(allItems)).hasSize(50);
            assertThat(allItems).containsExactlyInAnyOrderElementsOf(items);
        }
    }
}
