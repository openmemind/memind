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
package com.openmemind.ai.memory.core.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.exception.SourceTooLargeException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;

class HttpResourceFetcherTest {

    @Test
    void openShouldExposeHeadersBeforeReadingBody() {
        var response =
                new StubHttpResponse(
                        200,
                        URI.create("https://example.com/files/report.pdf"),
                        new byte[] {1, 2, 3},
                        Map.of("Content-Type", List.of("application/pdf")));

        var session =
                new HttpResourceFetcher(new StubHttpClient(response))
                        .open(
                                new ResourceFetchRequest(
                                        DefaultMemoryId.of("user-1", "agent-1"),
                                        "https://example.com/files/report.pdf",
                                        null,
                                        null))
                        .block();

        assertThat(session).isNotNull();
        assertThat(session.resolvedFileName()).isEqualTo("report.pdf");
        assertThat(session.resolvedMimeType()).isEqualTo("application/pdf");
        assertThat(session.declaredContentLength()).isNull();

        var fetched = session.readBody(1024L).block();
        assertThat(fetched).isNotNull();
        assertThat(fetched.fileName()).isEqualTo("report.pdf");
        assertThat(fetched.mimeType()).isEqualTo("application/pdf");
        assertThat(fetched.finalUrl()).isEqualTo("https://example.com/files/report.pdf");
        assertThat(fetched.sizeBytes()).isEqualTo(3L);
        assertThat(fetched.data()).containsExactly((byte) 1, (byte) 2, (byte) 3);
    }

    @Test
    void openShouldInferFileNameFromRedirectTarget() {
        var response =
                new StubHttpResponse(
                        200,
                        URI.create("https://example.com/downloads/final.pdf"),
                        "payload".getBytes(StandardCharsets.UTF_8),
                        Map.of("Content-Type", List.of("application/pdf")));

        var session =
                new HttpResourceFetcher(new StubHttpClient(response))
                        .open(
                                new ResourceFetchRequest(
                                        DefaultMemoryId.of("user-1", "agent-1"),
                                        "https://example.com/redirect",
                                        null,
                                        null))
                        .block();

        assertThat(session).isNotNull();
        assertThat(session.resolvedFileName()).isEqualTo("final.pdf");
        assertThat(session.finalUrl()).isEqualTo("https://example.com/downloads/final.pdf");
    }

    @Test
    void openShouldRespectRequestedFileNameAndMimeTypeOverrides() {
        var response =
                new StubHttpResponse(
                        200,
                        URI.create("https://example.com/files/raw"),
                        "hello".getBytes(StandardCharsets.UTF_8),
                        Map.of("Content-Type", List.of("text/plain")));

        var session =
                new HttpResourceFetcher(new StubHttpClient(response))
                        .open(
                                new ResourceFetchRequest(
                                        DefaultMemoryId.of("user-1", "agent-1"),
                                        "https://example.com/files/raw",
                                        "override.bin",
                                        "application/custom"))
                        .block();

        assertThat(session).isNotNull();
        assertThat(session.resolvedFileName()).isEqualTo("override.bin");
        assertThat(session.resolvedMimeType()).isEqualTo("application/custom");
    }

    @Test
    void readBodyShouldRejectWhenBytesExceedBudget() {
        var response =
                new StubHttpResponse(
                        200,
                        URI.create("https://example.com/report.pdf"),
                        new byte[] {1, 2, 3, 4},
                        Map.of(
                                "Content-Type", List.of("application/pdf"),
                                "Content-Length", List.of("4096")));

        var session =
                new HttpResourceFetcher(new StubHttpClient(response))
                        .open(
                                new ResourceFetchRequest(
                                        DefaultMemoryId.of("user-1", "agent-1"),
                                        "https://example.com/report.pdf",
                                        null,
                                        null))
                        .block();

        assertThat(session).isNotNull();
        assertThat(session.declaredContentLength()).isEqualTo(4096L);
        assertThatThrownBy(() -> session.readBody(1024L).block())
                .isInstanceOf(SourceTooLargeException.class);
    }

    @Test
    void openShouldFailWhenStatusIsNotSuccessful() {
        var response =
                new StubHttpResponse(
                        404,
                        URI.create("https://example.com/missing"),
                        "missing".getBytes(StandardCharsets.UTF_8),
                        Map.of("Content-Type", List.of("text/plain")));

        assertThatThrownBy(
                        () ->
                                new HttpResourceFetcher(new StubHttpClient(response))
                                        .open(
                                                new ResourceFetchRequest(
                                                        DefaultMemoryId.of("user-1", "agent-1"),
                                                        "https://example.com/missing",
                                                        null,
                                                        null))
                                        .block())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("status=404");
    }

    private static final class StubHttpClient extends HttpClient {

        private final HttpResponse<InputStream> response;

        private StubHttpClient(HttpResponse<InputStream> response) {
            this.response = response;
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.of(Duration.ofSeconds(10));
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NORMAL;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            return null;
        }

        @Override
        public SSLParameters sslParameters() {
            return new SSLParameters();
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }

        @Override
        public <T> HttpResponse<T> send(
                HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            throw new UnsupportedOperationException("send is not used in HttpResourceFetcher");
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            return CompletableFuture.completedFuture((HttpResponse<T>) response);
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            return sendAsync(request, responseBodyHandler);
        }
    }

    private static final class StubHttpResponse implements HttpResponse<InputStream> {

        private final int statusCode;
        private final URI uri;
        private final InputStream body;
        private final HttpHeaders headers;

        private StubHttpResponse(
                int statusCode, URI uri, byte[] body, Map<String, List<String>> headers) {
            this.statusCode = statusCode;
            this.uri = uri;
            this.body = new ByteArrayInputStream(body);
            this.headers = HttpHeaders.of(headers, (name, value) -> true);
        }

        @Override
        public int statusCode() {
            return statusCode;
        }

        @Override
        public HttpRequest request() {
            return HttpRequest.newBuilder(uri).GET().build();
        }

        @Override
        public Optional<HttpResponse<InputStream>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return headers;
        }

        @Override
        public InputStream body() {
            return body;
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return uri;
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }
}
