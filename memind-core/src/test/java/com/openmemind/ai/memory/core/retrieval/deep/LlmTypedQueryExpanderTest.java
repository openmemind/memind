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
package com.openmemind.ai.memory.core.retrieval.deep;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmemind.ai.memory.core.llm.ChatMessage;
import com.openmemind.ai.memory.core.llm.ChatMessages;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import com.openmemind.ai.memory.core.prompt.InMemoryPromptRegistry;
import com.openmemind.ai.memory.core.prompt.PromptType;
import com.openmemind.ai.memory.core.prompt.retrieval.TypedQueryExpandPrompts;
import com.openmemind.ai.memory.core.retrieval.deep.ExpandedQuery.QueryType;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

@DisplayName("LlmTypedQueryExpander Unit Test")
class LlmTypedQueryExpanderTest {

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {

        @Test
        @DisplayName("Should throw NullPointerException when structuredChatClient is null")
        void shouldThrowWhenStructuredChatClientNull() {
            assertThatThrownBy(() -> new LlmTypedQueryExpander(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("structuredChatClient must not be null");
        }
    }

    @Nested
    @DisplayName("expand method")
    class ExpandTests {

        @Test
        @DisplayName("Should parse and return a list of typed queries when LLM returns valid JSON")
        void shouldReturnTypedQueriesWhenLlmReturnsValidResponse() {
            var structuredLlmClient =
                    fakeStructuredLlmClientReturning(
                            List.of(
                                    new TypedQueryEntry("lex", "User prefers Java"),
                                    new TypedQueryEntry(
                                            "vec", "What programming languages does the user like"),
                                    new TypedQueryEntry(
                                            "hyde",
                                            "The programming language the user uses most often is"
                                                + " Java, often discussing the Spring framework")));
            var expander = new LlmTypedQueryExpander(structuredLlmClient);

            var query = "What programming language does the user like";
            var gaps = List.of("Missing programming preference information");

            StepVerifier.create(expander.expand(query, gaps, List.of(), List.of(), 3))
                    .assertNext(
                            queries -> {
                                assertThat(queries).hasSize(3);

                                assertThat(queries.get(0).queryType()).isEqualTo(QueryType.LEX);
                                assertThat(queries.get(0).text()).isEqualTo("User prefers Java");

                                assertThat(queries.get(1).queryType()).isEqualTo(QueryType.VEC);
                                assertThat(queries.get(1).text())
                                        .isEqualTo("What programming languages does the user like");

                                assertThat(queries.get(2).queryType()).isEqualTo(QueryType.HYDE);
                                assertThat(queries.get(2).text())
                                        .isEqualTo(
                                                "The programming language the user uses most often"
                                                        + " is Java, often discussing the Spring"
                                                        + " framework");
                            })
                    .verifyComplete();

            var prompt =
                    TypedQueryExpandPrompts.build(query, gaps, List.of(), List.of(), 3)
                            .render("English");
            assertThat(structuredLlmClient.lastMessages())
                    .isEqualTo(ChatMessages.systemUser(prompt.systemPrompt(), prompt.userPrompt()));
        }

        @Test
        @DisplayName("constructor with prompt registry should use override instruction")
        void constructorWithPromptRegistryUsesOverrideInstruction() {
            var structuredLlmClient =
                    fakeStructuredLlmClientReturning(
                            List.of(new TypedQueryEntry("lex", "User prefers Java")));
            var registry =
                    InMemoryPromptRegistry.builder()
                            .override(
                                    PromptType.TYPED_QUERY_EXPAND,
                                    "Custom typed query expansion instruction")
                            .build();
            var expander = new LlmTypedQueryExpander(structuredLlmClient, registry);

            StepVerifier.create(expander.expand("test", List.of("gap"), List.of(), List.of(), 1))
                    .assertNext(
                            queries -> {
                                assertThat(queries).hasSize(1);
                                assertThat(queries.getFirst().text())
                                        .isEqualTo("User prefers Java");
                            })
                    .verifyComplete();

            assertThat(structuredLlmClient.lastMessages().getFirst().content())
                    .contains("Custom typed query expansion instruction");
        }

        @Test
        @DisplayName("Type mapping: lex→LEX, vec→VEC, hyde→HYDE, unknown type→VEC")
        void shouldMapQueryTypesCorrectly() {
            var structuredLlmClient =
                    fakeStructuredLlmClientReturning(
                            List.of(
                                    new TypedQueryEntry("lex", "keyword query"),
                                    new TypedQueryEntry("vec", "semantic query"),
                                    new TypedQueryEntry("hyde", "hypothetical doc"),
                                    new TypedQueryEntry("unknown", "fallback query")));
            var expander = new LlmTypedQueryExpander(structuredLlmClient);

            StepVerifier.create(expander.expand("test", List.of(), List.of(), List.of(), 4))
                    .assertNext(
                            queries -> {
                                assertThat(queries).hasSize(4);
                                assertThat(queries.get(0).queryType()).isEqualTo(QueryType.LEX);
                                assertThat(queries.get(1).queryType()).isEqualTo(QueryType.VEC);
                                assertThat(queries.get(2).queryType()).isEqualTo(QueryType.HYDE);
                                assertThat(queries.get(3).queryType()).isEqualTo(QueryType.VEC);
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Type string case insensitive: LEX, Vec, HyDe are all recognizable")
        void shouldMapQueryTypesCaseInsensitively() {
            var structuredLlmClient =
                    fakeStructuredLlmClientReturning(
                            List.of(
                                    new TypedQueryEntry("LEX", "keyword query"),
                                    new TypedQueryEntry("Vec", "semantic query"),
                                    new TypedQueryEntry("HyDe", "hypothetical doc")));
            var expander = new LlmTypedQueryExpander(structuredLlmClient);

            StepVerifier.create(expander.expand("test", List.of(), List.of(), List.of(), 3))
                    .assertNext(
                            queries -> {
                                assertThat(queries).hasSize(3);
                                assertThat(queries.get(0).queryType()).isEqualTo(QueryType.LEX);
                                assertThat(queries.get(1).queryType()).isEqualTo(QueryType.VEC);
                                assertThat(queries.get(2).queryType()).isEqualTo(QueryType.HYDE);
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should return an empty list on error when LLM call fails")
        void shouldReturnEmptyListOnError() {
            var structuredLlmClient =
                    fakeStructuredLlmClientThrowing(new RuntimeException("LLM unavailable"));
            var expander = new LlmTypedQueryExpander(structuredLlmClient);

            StepVerifier.create(expander.expand("test", List.of("gap"), List.of(), List.of(), 3))
                    .assertNext(queries -> assertThat(queries).isEmpty())
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should return an empty list when LLM returns null")
        void shouldReturnEmptyListWhenLlmReturnsNull() {
            var structuredLlmClient = fakeStructuredLlmClientReturningNull();
            var expander = new LlmTypedQueryExpander(structuredLlmClient);

            StepVerifier.create(expander.expand("test", List.of(), List.of(), List.of(), 3))
                    .assertNext(queries -> assertThat(queries).isEmpty())
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should return an empty list when LLM returns an empty query list")
        void shouldReturnEmptyListWhenLlmReturnsEmptyQueries() {
            var structuredLlmClient = fakeStructuredLlmClientReturning(List.of());
            var expander = new LlmTypedQueryExpander(structuredLlmClient);

            StepVerifier.create(expander.expand("test", List.of(), List.of(), List.of(), 3))
                    .assertNext(queries -> assertThat(queries).isEmpty())
                    .verifyComplete();
        }

        @Test
        @DisplayName(
                "Should limit results to maxExpansions when LLM returns more than maxExpansions")
        void shouldLimitResultsToMaxExpansions() {
            var structuredLlmClient =
                    fakeStructuredLlmClientReturning(
                            List.of(
                                    new TypedQueryEntry("lex", "q1"),
                                    new TypedQueryEntry("vec", "q2"),
                                    new TypedQueryEntry("hyde", "q3"),
                                    new TypedQueryEntry("vec", "q4")));
            var expander = new LlmTypedQueryExpander(structuredLlmClient);

            StepVerifier.create(expander.expand("test", List.of("gap"), List.of(), List.of(), 2))
                    .assertNext(
                            queries -> {
                                assertThat(queries).hasSize(2);
                                assertThat(queries.getFirst().queryType()).isEqualTo(QueryType.LEX);
                                assertThat(queries.getLast().queryType()).isEqualTo(QueryType.VEC);
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should use generic expansion when gaps are empty (do not throw exception)")
        void shouldHandleEmptyGaps() {
            var structuredLlmClient =
                    fakeStructuredLlmClientReturning(
                            List.of(
                                    new TypedQueryEntry("lex", "generic lex"),
                                    new TypedQueryEntry("vec", "generic vec")));
            var expander = new LlmTypedQueryExpander(structuredLlmClient);

            StepVerifier.create(expander.expand("test", List.of(), List.of(), List.of(), 3))
                    .assertNext(
                            queries -> {
                                assertThat(queries).hasSize(2);
                                assertThat(queries.getFirst().text()).isEqualTo("generic lex");
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should default to VEC when type is null")
        void shouldDefaultToVecWhenTypeIsNull() {
            var structuredLlmClient =
                    fakeStructuredLlmClientReturning(
                            List.of(new TypedQueryEntry(null, "null type query")));
            var expander = new LlmTypedQueryExpander(structuredLlmClient);

            StepVerifier.create(expander.expand("test", List.of(), List.of(), List.of(), 3))
                    .assertNext(
                            queries -> {
                                assertThat(queries).hasSize(1);
                                assertThat(queries.getFirst().queryType()).isEqualTo(QueryType.VEC);
                                assertThat(queries.getFirst().text()).isEqualTo("null type query");
                            })
                    .verifyComplete();
        }
    }

    // -- Helper types and methods --

    /**
     * Simulate a single typed query returned by LLM
     */
    private record TypedQueryEntry(String type, String text) {}

    /**
     * Construct a fake StructuredChatClient and build TypedExpandResponse from entries.
     */
    @SuppressWarnings("unchecked")
    private CapturingStructuredChatClient fakeStructuredLlmClientReturning(
            List<TypedQueryEntry> entries) {
        var queries =
                entries.stream()
                        .map(
                                entry ->
                                        new LlmTypedQueryExpander.TypedQuery(
                                                entry.type(), entry.text()))
                        .toList();
        var response = new LlmTypedQueryExpander.TypedExpandResponse(queries);
        return new CapturingStructuredChatClient(response);
    }

    private CapturingStructuredChatClient fakeStructuredLlmClientReturningNull() {
        return new CapturingStructuredChatClient(null);
    }

    private CapturingStructuredChatClient fakeStructuredLlmClientThrowing(
            RuntimeException exception) {
        return new CapturingStructuredChatClient(exception);
    }

    private static final class CapturingStructuredChatClient implements StructuredChatClient {

        private final Object response;
        private final RuntimeException error;
        private List<ChatMessage> lastMessages = List.of();

        private CapturingStructuredChatClient(Object response) {
            this.response = response;
            this.error = null;
        }

        private CapturingStructuredChatClient(RuntimeException error) {
            this.response = null;
            this.error = error;
        }

        @Override
        public reactor.core.publisher.Mono<String> call(List<ChatMessage> messages) {
            lastMessages = List.copyOf(messages);
            return reactor.core.publisher.Mono.just("");
        }

        @Override
        public <T> reactor.core.publisher.Mono<T> call(
                List<ChatMessage> messages, Class<T> responseType) {
            lastMessages = List.copyOf(messages);
            if (error != null) {
                return reactor.core.publisher.Mono.error(error);
            }
            if (response == null) {
                return reactor.core.publisher.Mono.empty();
            }
            return reactor.core.publisher.Mono.fromSupplier(() -> responseType.cast(response));
        }

        private List<ChatMessage> lastMessages() {
            return lastMessages;
        }
    }
}
