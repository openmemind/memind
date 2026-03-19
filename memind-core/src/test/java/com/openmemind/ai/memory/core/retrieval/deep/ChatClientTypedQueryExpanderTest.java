package com.openmemind.ai.memory.core.retrieval.deep;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.openmemind.ai.memory.core.retrieval.deep.ExpandedQuery.QueryType;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChatClientTypedQueryExpander Unit Test")
class ChatClientTypedQueryExpanderTest {

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {

        @Test
        @DisplayName("Should throw NullPointerException when chatClient is null")
        void shouldThrowWhenChatClientNull() {
            assertThatThrownBy(() -> new ChatClientTypedQueryExpander(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("chatClient must not be null");
        }
    }

    @Nested
    @DisplayName("expand method")
    class ExpandTests {

        @Test
        @DisplayName("Should parse and return a list of typed queries when LLM returns valid JSON")
        void shouldReturnTypedQueriesWhenLlmReturnsValidResponse() {
            var chatClient =
                    mockChatClientReturning(
                            List.of(
                                    new TypedQueryEntry("lex", "User prefers Java"),
                                    new TypedQueryEntry(
                                            "vec", "What programming languages does the user like"),
                                    new TypedQueryEntry(
                                            "hyde",
                                            "The programming language the user uses most often is"
                                                + " Java, often discussing the Spring framework")));
            var expander = new ChatClientTypedQueryExpander(chatClient);

            StepVerifier.create(
                            expander.expand(
                                    "What programming language does the user like",
                                    List.of("Missing programming preference information"),
                                    List.of(),
                                    List.of(),
                                    3))
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
        }

        @Test
        @DisplayName("Type mapping: lex→LEX, vec→VEC, hyde→HYDE, unknown type→VEC")
        void shouldMapQueryTypesCorrectly() {
            var chatClient =
                    mockChatClientReturning(
                            List.of(
                                    new TypedQueryEntry("lex", "keyword query"),
                                    new TypedQueryEntry("vec", "semantic query"),
                                    new TypedQueryEntry("hyde", "hypothetical doc"),
                                    new TypedQueryEntry("unknown", "fallback query")));
            var expander = new ChatClientTypedQueryExpander(chatClient);

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
            var chatClient =
                    mockChatClientReturning(
                            List.of(
                                    new TypedQueryEntry("LEX", "keyword query"),
                                    new TypedQueryEntry("Vec", "semantic query"),
                                    new TypedQueryEntry("HyDe", "hypothetical doc")));
            var expander = new ChatClientTypedQueryExpander(chatClient);

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
            var chatClient = mockChatClientThrowing(new RuntimeException("LLM unavailable"));
            var expander = new ChatClientTypedQueryExpander(chatClient);

            StepVerifier.create(expander.expand("test", List.of("gap"), List.of(), List.of(), 3))
                    .assertNext(queries -> assertThat(queries).isEmpty())
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should return an empty list when LLM returns null")
        void shouldReturnEmptyListWhenLlmReturnsNull() {
            var chatClient = mockChatClientReturningNull();
            var expander = new ChatClientTypedQueryExpander(chatClient);

            StepVerifier.create(expander.expand("test", List.of(), List.of(), List.of(), 3))
                    .assertNext(queries -> assertThat(queries).isEmpty())
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should return an empty list when LLM returns an empty query list")
        void shouldReturnEmptyListWhenLlmReturnsEmptyQueries() {
            var chatClient = mockChatClientReturning(List.of());
            var expander = new ChatClientTypedQueryExpander(chatClient);

            StepVerifier.create(expander.expand("test", List.of(), List.of(), List.of(), 3))
                    .assertNext(queries -> assertThat(queries).isEmpty())
                    .verifyComplete();
        }

        @Test
        @DisplayName(
                "Should limit results to maxExpansions when LLM returns more than maxExpansions")
        void shouldLimitResultsToMaxExpansions() {
            var chatClient =
                    mockChatClientReturning(
                            List.of(
                                    new TypedQueryEntry("lex", "q1"),
                                    new TypedQueryEntry("vec", "q2"),
                                    new TypedQueryEntry("hyde", "q3"),
                                    new TypedQueryEntry("vec", "q4")));
            var expander = new ChatClientTypedQueryExpander(chatClient);

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
            var chatClient =
                    mockChatClientReturning(
                            List.of(
                                    new TypedQueryEntry("lex", "generic lex"),
                                    new TypedQueryEntry("vec", "generic vec")));
            var expander = new ChatClientTypedQueryExpander(chatClient);

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
            var chatClient =
                    mockChatClientReturning(List.of(new TypedQueryEntry(null, "null type query")));
            var expander = new ChatClientTypedQueryExpander(chatClient);

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
     * Simulate a complete response returned by LLM
     */
    private record TypedExpandResponseStub(List<TypedQueryEntry> queries) {}

    /**
     * Construct a mock ChatClient, create TypedExpandResponse (private record) via reflection,
     * and construct the internal TypedQuery list from the TypedQueryEntry list.
     */
    @SuppressWarnings("unchecked")
    private ChatClient mockChatClientReturning(List<TypedQueryEntry> entries) {
        var chatClient = mock(ChatClient.class);
        var requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        var callResponseSpec = mock(ChatClient.CallResponseSpec.class);

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.entity(any(Class.class)))
                .thenAnswer(
                        invocation -> {
                            Class<?> responseClass = invocation.getArgument(0);
                            // TypedQuery is a sibling private record in the enclosing class,
                            // not nested inside TypedExpandResponse
                            Class<?> enclosingClass = responseClass.getEnclosingClass();
                            Class<?> typedQueryClass = null;
                            if (enclosingClass != null) {
                                for (var declaredClass : enclosingClass.getDeclaredClasses()) {
                                    if (declaredClass.getSimpleName().equals("TypedQuery")) {
                                        typedQueryClass = declaredClass;
                                        break;
                                    }
                                }
                            }
                            if (typedQueryClass == null) {
                                throw new IllegalStateException(
                                        "TypedQuery class not found in enclosing class");
                            }

                            // Build TypedQuery instances via reflection
                            var typedQueryConstructor =
                                    typedQueryClass.getDeclaredConstructors()[0];
                            typedQueryConstructor.setAccessible(true);
                            var typedQueries =
                                    entries.stream()
                                            .map(
                                                    entry -> {
                                                        try {
                                                            return typedQueryConstructor
                                                                    .newInstance(
                                                                            entry.type(),
                                                                            entry.text());
                                                        } catch (Exception e) {
                                                            throw new RuntimeException(e);
                                                        }
                                                    })
                                            .toList();

                            // Build TypedExpandResponse via reflection
                            var responseConstructor = responseClass.getDeclaredConstructors()[0];
                            responseConstructor.setAccessible(true);
                            return responseConstructor.newInstance(typedQueries);
                        });

        return chatClient;
    }

    @SuppressWarnings("unchecked")
    private ChatClient mockChatClientReturningNull() {
        var chatClient = mock(ChatClient.class);
        var requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        var callResponseSpec = mock(ChatClient.CallResponseSpec.class);

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.entity(any(Class.class))).thenReturn(null);

        return chatClient;
    }

    @SuppressWarnings("unchecked")
    private ChatClient mockChatClientThrowing(RuntimeException exception) {
        var chatClient = mock(ChatClient.class);
        var requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        var callResponseSpec = mock(ChatClient.CallResponseSpec.class);

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.entity(any(Class.class))).thenThrow(exception);

        return chatClient;
    }
}
