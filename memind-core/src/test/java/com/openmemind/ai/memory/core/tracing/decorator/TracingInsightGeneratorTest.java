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
package com.openmemind.ai.memory.core.tracing.decorator;

import static com.openmemind.ai.memory.core.tracing.MemoryAttributes.EXTRACTION_INSIGHT_ADD_COUNT;
import static com.openmemind.ai.memory.core.tracing.MemoryAttributes.EXTRACTION_INSIGHT_DELETE_COUNT;
import static com.openmemind.ai.memory.core.tracing.MemoryAttributes.EXTRACTION_INSIGHT_GROUP_NAME;
import static com.openmemind.ai.memory.core.tracing.MemoryAttributes.EXTRACTION_INSIGHT_LEAF_COUNT;
import static com.openmemind.ai.memory.core.tracing.MemoryAttributes.EXTRACTION_INSIGHT_TYPE;
import static com.openmemind.ai.memory.core.tracing.MemoryAttributes.EXTRACTION_INSIGHT_UPDATE_COUNT;
import static com.openmemind.ai.memory.core.tracing.MemorySpanNames.EXTRACTION_INSIGHT_GENERATE_BRANCH;
import static com.openmemind.ai.memory.core.tracing.MemorySpanNames.EXTRACTION_INSIGHT_GENERATE_LEAF;
import static com.openmemind.ai.memory.core.tracing.MemorySpanNames.EXTRACTION_INSIGHT_GENERATE_ROOT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.openmemind.ai.memory.core.data.InsightPoint;
import com.openmemind.ai.memory.core.data.MemoryInsight;
import com.openmemind.ai.memory.core.data.MemoryInsightType;
import com.openmemind.ai.memory.core.data.PointOperation;
import com.openmemind.ai.memory.core.extraction.insight.generator.InsightGenerator;
import com.openmemind.ai.memory.core.extraction.insight.generator.InsightPointGenerateResponse;
import com.openmemind.ai.memory.core.extraction.insight.generator.InsightPointOpsResponse;
import com.openmemind.ai.memory.core.support.RecordingMemoryObserver;
import com.openmemind.ai.memory.core.tracing.ObservationContext;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class TracingInsightGeneratorTest {

    @Test
    void generatePointsPublishesLeafSpanAndPropagatesResult() {
        var observer = new RecordingMemoryObserver();
        var delegate = mock(InsightGenerator.class);
        var response = new InsightPointGenerateResponse(List.of());
        when(delegate.generatePoints(any(), any(), any(), any(), anyInt(), any(), any()))
                .thenReturn(Mono.just(response));
        var insightType = insightType();

        var traced = new TracingInsightGenerator(delegate, observer);

        StepVerifier.create(
                        traced.generatePoints(
                                insightType, "group-a", List.of(), List.of(), 100, null, "zh-CN"))
                .expectNext(response)
                .verifyComplete();

        assertThat(observer.monoContexts()).hasSize(1);
        assertThat(observer.monoContexts().getFirst().spanName())
                .isEqualTo(EXTRACTION_INSIGHT_GENERATE_LEAF);
        assertThat(observer.monoContexts().getFirst().requestAttributes())
                .containsEntry(EXTRACTION_INSIGHT_TYPE, "PROFILE")
                .containsEntry(EXTRACTION_INSIGHT_GROUP_NAME, "group-a");
    }

    @Test
    void generateBranchSummaryPublishesBranchSpanAndPropagatesResult() {
        var observer = new RecordingMemoryObserver();
        var delegate = mock(InsightGenerator.class);
        var response = new InsightPointGenerateResponse(List.of());
        when(delegate.generateBranchSummary(any(), any(), any(), anyInt(), any()))
                .thenReturn(Mono.just(response));
        var insightType = insightType();

        var traced = new TracingInsightGenerator(delegate, observer);

        StepVerifier.create(
                        traced.generateBranchSummary(
                                insightType, List.of(), List.of(memoryInsight()), 100, "zh-CN"))
                .expectNext(response)
                .verifyComplete();

        assertThat(observer.monoContexts()).hasSize(1);
        assertThat(observer.monoContexts().getFirst().spanName())
                .isEqualTo(EXTRACTION_INSIGHT_GENERATE_BRANCH);
        assertThat(observer.monoContexts().getFirst().requestAttributes())
                .containsEntry(EXTRACTION_INSIGHT_TYPE, "PROFILE")
                .containsEntry(EXTRACTION_INSIGHT_LEAF_COUNT, 1);
    }

    @Test
    void generateRootSynthesisPublishesRootSpanAndPropagatesResult() {
        var observer = new RecordingMemoryObserver();
        var delegate = mock(InsightGenerator.class);
        var response = new InsightPointGenerateResponse(List.of());
        when(delegate.generateRootSynthesis(any(), any(), any(), anyInt(), any()))
                .thenReturn(Mono.just(response));
        var insightType = insightType();

        var traced = new TracingInsightGenerator(delegate, observer);

        StepVerifier.create(
                        traced.generateRootSynthesis(
                                insightType, List.of(), List.of(memoryInsight()), 100, "zh-CN"))
                .expectNext(response)
                .verifyComplete();

        assertThat(observer.monoContexts()).hasSize(1);
        assertThat(observer.monoContexts().getFirst().spanName())
                .isEqualTo(EXTRACTION_INSIGHT_GENERATE_ROOT);
        assertThat(observer.monoContexts().getFirst().requestAttributes())
                .containsEntry(EXTRACTION_INSIGHT_TYPE, "PROFILE")
                .containsEntry(EXTRACTION_INSIGHT_LEAF_COUNT, 1);
    }

    @Test
    void generatePointsPropagatesDelegateErrorsThroughObserver() {
        var observer = new RecordingMemoryObserver();
        var delegate = mock(InsightGenerator.class);
        when(delegate.generatePoints(any(), any(), any(), any(), anyInt(), any(), any()))
                .thenReturn(Mono.error(new IllegalStateException("boom")));
        var insightType = insightType();

        var traced = new TracingInsightGenerator(delegate, observer);

        StepVerifier.create(
                        traced.generatePoints(
                                insightType, "group-a", List.of(), List.of(), 100, null, "zh-CN"))
                .expectErrorMessage("boom")
                .verify();

        assertThat(observer.monoContexts()).hasSize(1);
    }

    @Test
    void generateLeafPointOpsPublishesOperationCounts() {
        var observer = new RecordingMemoryObserver();
        var delegate = mock(InsightGenerator.class);
        var response =
                new InsightPointOpsResponse(
                        List.of(
                                new PointOperation(
                                        PointOperation.OpType.ADD,
                                        null,
                                        new InsightPoint(
                                                "pt_add",
                                                InsightPoint.PointType.SUMMARY,
                                                "new",
                                                List.of("1", "2")),
                                        null),
                                new PointOperation(
                                        PointOperation.OpType.DELETE, "pt_delete", null, "drop")));
        when(delegate.generateLeafPointOps(any(), any(), any(), any(), anyInt(), any(), any()))
                .thenReturn(Mono.just(response));
        var insightType = insightType();

        var traced = new TracingInsightGenerator(delegate, observer);

        StepVerifier.create(
                        traced.generateLeafPointOps(
                                insightType, "group-a", List.of(), List.of(), 100, null, "zh-CN"))
                .expectNext(response)
                .verifyComplete();

        assertThat(extractResultAttributes(observer, response))
                .containsEntry(EXTRACTION_INSIGHT_ADD_COUNT, 1)
                .containsEntry(EXTRACTION_INSIGHT_UPDATE_COUNT, 0)
                .containsEntry(EXTRACTION_INSIGHT_DELETE_COUNT, 1);
    }

    @Test
    void generateBranchPointOpsPublishesOperationCounts() {
        var observer = new RecordingMemoryObserver();
        var delegate = mock(InsightGenerator.class);
        var response =
                new InsightPointOpsResponse(
                        List.of(
                                new PointOperation(
                                        PointOperation.OpType.UPDATE,
                                        "pt_update",
                                        new InsightPoint(
                                                "pt_update",
                                                InsightPoint.PointType.SUMMARY,
                                                "updated",
                                                List.of("1", "2")),
                                        null)));
        when(delegate.generateBranchPointOps(any(), any(), any(), anyInt(), any()))
                .thenReturn(Mono.just(response));
        var insightType = insightType();

        var traced = new TracingInsightGenerator(delegate, observer);

        StepVerifier.create(
                        traced.generateBranchPointOps(
                                insightType, List.of(), List.of(memoryInsight()), 100, "zh-CN"))
                .expectNext(response)
                .verifyComplete();

        assertThat(extractResultAttributes(observer, response))
                .containsEntry(EXTRACTION_INSIGHT_ADD_COUNT, 0)
                .containsEntry(EXTRACTION_INSIGHT_UPDATE_COUNT, 1)
                .containsEntry(EXTRACTION_INSIGHT_DELETE_COUNT, 0);
    }

    private static MemoryInsightType insightType() {
        return new MemoryInsightType(
                1L, "PROFILE", "desc", null, List.of(), 400, null, null, null, null, null, null);
    }

    private static MemoryInsight memoryInsight() {
        return new MemoryInsight(
                1L,
                "user1:agent1",
                "PROFILE",
                null,
                "group",
                List.of(),
                List.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                0);
    }

    @SuppressWarnings("unchecked")
    private static java.util.Map<String, Object> extractResultAttributes(
            RecordingMemoryObserver observer, InsightPointOpsResponse response) {
        var context =
                (ObservationContext<InsightPointOpsResponse>) observer.monoContexts().getFirst();
        return context.resultExtractor().extract(response);
    }
}
