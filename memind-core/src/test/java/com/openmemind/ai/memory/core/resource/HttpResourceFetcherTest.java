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
import java.net.InetAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
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
                fetcher(new StubHttpClient(response))
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
    void openShouldRejectLoopbackAddressBeforeSendingRequest() {
        var client =
                new StubHttpClient(
                        okResponse(
                                URI.create("http://127.0.0.1/admin"),
                                "secret".getBytes(StandardCharsets.UTF_8),
                                Map.of("Content-Type", List.of("text/plain"))));

        assertThatThrownBy(() -> fetcher(client).open(request("http://127.0.0.1/admin")).block())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("denied");
        assertThat(client.sentRequests()).isEmpty();
    }

    @Test
    void openShouldRejectHostThatResolvesToPrivateAddress() throws Exception {
        var client =
                new StubHttpClient(
                        okResponse(
                                URI.create("https://documents.example/report.pdf"),
                                "secret".getBytes(StandardCharsets.UTF_8),
                                Map.of("Content-Type", List.of("application/pdf"))));

        assertThatThrownBy(
                        () ->
                                fetcher(client, host -> List.of(address("10.1.2.3")))
                                        .open(request("https://documents.example/report.pdf"))
                                        .block())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("denied");
        assertThat(client.sentRequests()).isEmpty();
    }

    @Test
    void openShouldRejectHostWhenAnyResolvedAddressIsPrivate() throws Exception {
        var client =
                new StubHttpClient(
                        okResponse(
                                URI.create("https://documents.example/report.pdf"),
                                "secret".getBytes(StandardCharsets.UTF_8),
                                Map.of("Content-Type", List.of("application/pdf"))));

        assertThatThrownBy(
                        () ->
                                fetcher(
                                                client,
                                                host ->
                                                        List.of(
                                                                address("93.184.216.34"),
                                                                address("192.168.1.10")))
                                        .open(request("https://documents.example/report.pdf"))
                                        .block())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("denied");
        assertThat(client.sentRequests()).isEmpty();
    }

    @Test
    void openShouldAllowPrivateNetworkWhenExplicitlyConfigured() throws Exception {
        var client =
                new StubHttpClient(
                        okResponse(
                                URI.create("https://documents.example/report.pdf"),
                                "payload".getBytes(StandardCharsets.UTF_8),
                                Map.of("Content-Type", List.of("application/pdf"))));
        var policy = HttpResourceFetchPolicy.builder().allowPrivateNetwork(true).build();

        var session =
                fetcher(client, policy, host -> List.of(address("10.1.2.3")))
                        .open(request("https://documents.example/report.pdf"))
                        .block();

        assertThat(session).isNotNull();
        assertThat(client.sentRequests())
                .extracting(request -> request.uri().toString())
                .containsExactly("https://documents.example/report.pdf");
    }

    @Test
    void openShouldRejectLinkLocalEvenWhenPrivateNetworkIsAllowed() throws Exception {
        var client =
                new StubHttpClient(
                        okResponse(
                                URI.create("https://metadata.example/latest"),
                                "secret".getBytes(StandardCharsets.UTF_8),
                                Map.of("Content-Type", List.of("text/plain"))));
        var policy = HttpResourceFetchPolicy.builder().allowPrivateNetwork(true).build();

        assertThatThrownBy(
                        () ->
                                fetcher(client, policy, host -> List.of(address("169.254.169.254")))
                                        .open(request("https://metadata.example/latest"))
                                        .block())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("denied");
        assertThat(client.sentRequests()).isEmpty();
    }

    @Test
    void openShouldRejectRedirectToBlockedAddress() {
        var client =
                new StubHttpClient(
                        new StubHttpResponse(
                                302,
                                URI.create("https://documents.example/redirect"),
                                new byte[0],
                                Map.of(
                                        "Location",
                                        List.of("http://169.254.169.254/latest/meta-data/"))),
                        okResponse(
                                URI.create("http://169.254.169.254/latest/meta-data/"),
                                "secret".getBytes(StandardCharsets.UTF_8),
                                Map.of("Content-Type", List.of("text/plain"))));

        assertThatThrownBy(
                        () ->
                                fetcher(client)
                                        .open(request("https://documents.example/redirect"))
                                        .block())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("denied");
        assertThat(client.sentRequests())
                .extracting(request -> request.uri().toString())
                .containsExactly("https://documents.example/redirect");
    }

    @Test
    void openShouldRejectHttpsToHttpRedirectDowngrade() {
        var client =
                new StubHttpClient(
                        new StubHttpResponse(
                                302,
                                URI.create("https://example.com/redirect"),
                                new byte[0],
                                Map.of(
                                        "Location",
                                        List.of("http://example.com/downloads/final.pdf"))),
                        okResponse(
                                URI.create("http://example.com/downloads/final.pdf"),
                                "payload".getBytes(StandardCharsets.UTF_8),
                                Map.of("Content-Type", List.of("application/pdf"))));

        assertThatThrownBy(
                        () -> fetcher(client).open(request("https://example.com/redirect")).block())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("https to http redirect");
        assertThat(client.sentRequests())
                .extracting(request -> request.uri().toString())
                .containsExactly("https://example.com/redirect");
    }

    @Test
    void openShouldInferFileNameFromRedirectTarget() {
        var client =
                new StubHttpClient(
                        new StubHttpResponse(
                                302,
                                URI.create("https://example.com/redirect"),
                                new byte[0],
                                Map.of(
                                        "Location",
                                        List.of("https://example.com/downloads/final.pdf"))),
                        okResponse(
                                URI.create("https://example.com/downloads/final.pdf"),
                                "payload".getBytes(StandardCharsets.UTF_8),
                                Map.of("Content-Type", List.of("application/pdf"))));

        var session = fetcher(client).open(request("https://example.com/redirect")).block();

        assertThat(session).isNotNull();
        assertThat(session.resolvedFileName()).isEqualTo("final.pdf");
        assertThat(session.finalUrl()).isEqualTo("https://example.com/downloads/final.pdf");
        assertThat(client.sentRequests())
                .extracting(request -> request.uri().toString())
                .containsExactly(
                        "https://example.com/redirect", "https://example.com/downloads/final.pdf");
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
                fetcher(new StubHttpClient(response))
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
                fetcher(new StubHttpClient(response))
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
    void constructorShouldRejectClientsThatFollowRedirectsAutomatically() {
        assertThatThrownBy(() -> new HttpResourceFetcher(new RedirectingStubHttpClient()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not follow redirects");
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
                                fetcher(new StubHttpClient(response))
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

    private static HttpResourceFetcher fetcher(StubHttpClient client) {
        return fetcher(client, HttpResourceFetchPolicy.secureDefaults(), publicResolver());
    }

    private static HttpResourceFetcher fetcher(
            StubHttpClient client, ResourceAddressResolver resolver) {
        return fetcher(client, HttpResourceFetchPolicy.secureDefaults(), resolver);
    }

    private static HttpResourceFetcher fetcher(
            StubHttpClient client,
            HttpResourceFetchPolicy policy,
            ResourceAddressResolver resolver) {
        return new HttpResourceFetcher(client, Duration.ofSeconds(30), policy, resolver);
    }

    private static ResourceAddressResolver publicResolver() {
        return host -> List.of(address(host.matches("[0-9.]+") ? host : "93.184.216.34"));
    }

    private static InetAddress address(String address) {
        try {
            return InetAddress.getByName(address);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid test address: " + address, ex);
        }
    }

    private static ResourceFetchRequest request(String sourceUrl) {
        return new ResourceFetchRequest(
                DefaultMemoryId.of("user-1", "agent-1"), sourceUrl, null, null);
    }

    private static StubHttpResponse okResponse(
            URI uri, byte[] body, Map<String, List<String>> headers) {
        return new StubHttpResponse(200, uri, body, headers);
    }

    private static class StubHttpClient extends HttpClient {

        private final Queue<HttpResponse<InputStream>> responses;
        private final List<HttpRequest> sentRequests = new ArrayList<>();

        @SafeVarargs
        private StubHttpClient(HttpResponse<InputStream>... responses) {
            this.responses = new ArrayDeque<>(List.of(responses));
        }

        private List<HttpRequest> sentRequests() {
            return List.copyOf(sentRequests);
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
            return Redirect.NEVER;
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
            sentRequests.add(request);
            HttpResponse<InputStream> response = responses.poll();
            if (response == null) {
                return CompletableFuture.failedFuture(
                        new AssertionError("No stubbed response for " + request.uri()));
            }
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

    private static final class RedirectingStubHttpClient extends StubHttpClient {

        private RedirectingStubHttpClient() {
            super();
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NORMAL;
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
