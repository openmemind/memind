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
package com.openmemind.ai.memory.evaluation.adapter.memind;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openmemind.ai.memory.core.Memory;
import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.extraction.ExtractionResult;
import com.openmemind.ai.memory.core.extraction.result.InsightResult;
import com.openmemind.ai.memory.core.extraction.result.MemoryItemResult;
import com.openmemind.ai.memory.core.extraction.result.RawDataResult;
import com.openmemind.ai.memory.core.llm.rerank.NoopReranker;
import com.openmemind.ai.memory.core.retrieval.RetrievalConfig;
import com.openmemind.ai.memory.core.retrieval.RetrievalRequest;
import com.openmemind.ai.memory.core.retrieval.RetrievalResult;
import com.openmemind.ai.memory.evaluation.adapter.model.AddRequest;
import com.openmemind.ai.memory.evaluation.adapter.model.SearchRequest;
import com.openmemind.ai.memory.evaluation.config.EvaluationProperties;
import com.openmemind.ai.memory.evaluation.dataset.model.EvalMessage;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Mono;

@DisplayName("MemindAdapter Test")
class MemindAdapterTest {

    @Test
    @DisplayName("streaming add uses Memory.addMessage and Memory.commit")
    void streamingAddUsesMemoryApi() {
        Memory memory = mock(Memory.class);
        ChatClient chatClient = mock(ChatClient.class);
        EvaluationProperties props = new EvaluationProperties();
        when(memory.addMessage(any(), any(), any())).thenReturn(Mono.empty());
        when(memory.commit(any(), any()))
                .thenReturn(
                        Mono.just(
                                ExtractionResult.success(
                                        DefaultMemoryId.of("speaker-a", "eval-agent"),
                                        RawDataResult.empty(),
                                        MemoryItemResult.empty(),
                                        InsightResult.empty(),
                                        Duration.ZERO)));

        MemindAdapter adapter =
                new MemindAdapter(
                        memory, chatClient, new NoopReranker(), RetrievalConfig.deep(), props);

        AddRequest request =
                new AddRequest(
                        "conv-1",
                        "speaker-a",
                        "speaker-b",
                        List.of(
                                new EvalMessage(
                                        "speaker-a",
                                        "Alice",
                                        "hi",
                                        Instant.parse("2026-03-30T10:15:30Z"),
                                        Map.of())));

        var result = adapter.add(request).block();

        assertThat(result).isNotNull();
        verify(memory, times(1)).addMessage(any(), any(), any());
        verify(memory, times(1)).commit(any(), any());
    }

    @Test
    @DisplayName("search uses Memory.retrieve with the configured RetrievalRequest")
    void searchUsesMemoryRetrieveRequest() {
        Memory memory = mock(Memory.class);
        ChatClient chatClient = mock(ChatClient.class);
        EvaluationProperties props = new EvaluationProperties();
        RetrievalResult retrieval = RetrievalResult.empty("deep", "what happened?");
        when(memory.retrieve(any(RetrievalRequest.class))).thenReturn(Mono.just(retrieval));

        MemindAdapter adapter =
                new MemindAdapter(
                        memory, chatClient, new NoopReranker(), RetrievalConfig.deep(), props);

        var result =
                adapter.search(
                                new SearchRequest(
                                        "conv-1", "speaker-a", "speaker-b", "what happened?", 20))
                        .block();

        assertThat(result).isNotNull();
        assertThat(result.retrievalResult()).isSameAs(retrieval);
        verify(memory, times(1)).retrieve(any(RetrievalRequest.class));
    }
}
