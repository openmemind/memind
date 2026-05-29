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

import com.openmemind.ai.memory.core.exception.ResourceFetchAccessDeniedException;
import com.openmemind.ai.memory.core.exception.SourceTooLargeException;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Default {@link ResourceFetcher} backed by JDK {@link HttpClient}.
 *
 * <p>The default policy is secure-by-default for untrusted URLs: it validates resolved addresses
 * before sending outbound requests and follows redirects manually so each redirect target is
 * revalidated.
 */
public class HttpResourceFetcher implements ResourceFetcher {

    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final String DEFAULT_FILE_NAME = "download";
    private static final String DEFAULT_MIME_TYPE = "application/octet-stream";

    private final HttpClient httpClient;
    private final Duration requestTimeout;
    private final HttpResourceFetchPolicy fetchPolicy;
    private final ResourceAddressResolver addressResolver;

    public HttpResourceFetcher() {
        this(defaultHttpClient(), DEFAULT_REQUEST_TIMEOUT);
    }

    public HttpResourceFetcher(HttpResourceFetchPolicy fetchPolicy) {
        this(defaultHttpClient(), DEFAULT_REQUEST_TIMEOUT, fetchPolicy);
    }

    public HttpResourceFetcher(HttpClient httpClient) {
        this(httpClient, DEFAULT_REQUEST_TIMEOUT);
    }

    public HttpResourceFetcher(HttpClient httpClient, HttpResourceFetchPolicy fetchPolicy) {
        this(httpClient, DEFAULT_REQUEST_TIMEOUT, fetchPolicy);
    }

    public HttpResourceFetcher(HttpClient httpClient, Duration requestTimeout) {
        this(httpClient, requestTimeout, HttpResourceFetchPolicy.secureDefaults());
    }

    public HttpResourceFetcher(
            HttpClient httpClient, Duration requestTimeout, HttpResourceFetchPolicy fetchPolicy) {
        this(httpClient, requestTimeout, fetchPolicy, ResourceAddressResolver.system());
    }

    HttpResourceFetcher(
            HttpClient httpClient,
            Duration requestTimeout,
            HttpResourceFetchPolicy fetchPolicy,
            ResourceAddressResolver addressResolver) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient is required");
        if (this.httpClient.followRedirects() != HttpClient.Redirect.NEVER) {
            throw new IllegalArgumentException(
                    "httpClient must not follow redirects automatically");
        }
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout is required");
        this.fetchPolicy = Objects.requireNonNull(fetchPolicy, "fetchPolicy is required");
        this.addressResolver =
                Objects.requireNonNull(addressResolver, "addressResolver is required");
    }

    @Override
    public Mono<FetchSession> open(ResourceFetchRequest request) {
        Objects.requireNonNull(request, "request is required");
        URI sourceUri = URI.create(request.sourceUrl());
        return sendWithRedirects(request, sourceUri, 0)
                .map(response -> toFetchSession(request, response.finalUri(), response.response()));
    }

    private Mono<ValidatedHttpResponse> sendWithRedirects(
            ResourceFetchRequest request, URI uri, int redirectCount) {
        return validateTarget(uri)
                .then(Mono.defer(() -> Mono.fromCompletionStage(send(uri))))
                .flatMap(
                        response -> {
                            URI responseUri = response.uri() == null ? uri : response.uri();
                            if (!isRedirect(response.statusCode())) {
                                return validateTarget(responseUri)
                                        .thenReturn(
                                                new ValidatedHttpResponse(responseUri, response))
                                        .doOnError(error -> closeBody(response));
                            }
                            if (redirectCount >= fetchPolicy.maxRedirects()) {
                                closeBody(response);
                                return Mono.error(
                                        new IllegalStateException(
                                                "Resource download failed: too many redirects,"
                                                        + " source="
                                                        + HttpResourceFetchPolicy.describe(
                                                                URI.create(request.sourceUrl()))));
                            }
                            URI nextUri;
                            try {
                                nextUri = resolveRedirectUri(responseUri, response);
                            } finally {
                                closeBody(response);
                            }
                            return sendWithRedirects(request, nextUri, redirectCount + 1);
                        });
    }

    private Mono<Void> validateTarget(URI uri) {
        return Mono.fromCallable(
                        () -> {
                            fetchPolicy.validateUri(uri);
                            List<InetAddress> addresses = addressResolver.resolve(uri.getHost());
                            fetchPolicy.validateResolvedAddresses(uri, addresses);
                            return true;
                        })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    private CompletionStage<HttpResponse<InputStream>> send(URI uri) {
        HttpRequest httpRequest =
                HttpRequest.newBuilder()
                        .uri(uri)
                        .header("Accept", "*/*")
                        .timeout(requestTimeout)
                        .GET()
                        .build();
        return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
    }

    private URI resolveRedirectUri(URI currentUri, HttpResponse<InputStream> response) {
        String location =
                response.headers()
                        .firstValue("Location")
                        .filter(value -> !value.isBlank())
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Resource download failed: redirect missing"
                                                        + " Location"));
        URI redirectUri = currentUri.resolve(location);
        if ("https".equalsIgnoreCase(currentUri.getScheme())
                && "http".equalsIgnoreCase(redirectUri.getScheme())) {
            throw new ResourceFetchAccessDeniedException(
                    "Resource fetch denied: https to http redirect is not allowed, target="
                            + HttpResourceFetchPolicy.describe(redirectUri));
        }
        return redirectUri;
    }

    private boolean isRedirect(int statusCode) {
        return statusCode == 301
                || statusCode == 302
                || statusCode == 303
                || statusCode == 307
                || statusCode == 308;
    }

    private record ValidatedHttpResponse(URI finalUri, HttpResponse<InputStream> response) {}

    private FetchSession toFetchSession(
            ResourceFetchRequest request, URI finalUri, HttpResponse<InputStream> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            closeBody(response);
            throw new IllegalStateException(
                    "Resource download failed: status="
                            + response.statusCode()
                            + ", source="
                            + HttpResourceFetchPolicy.describe(URI.create(request.sourceUrl())));
        }
        String sourceUrl = request.sourceUrl();
        String finalUrl = finalUri == null ? sourceUrl : finalUri.toString();
        String resolvedFileName = resolveFileName(sourceUrl, request.requestedFileName(), response);
        String resolvedMimeType =
                resolveMimeType(resolvedFileName, request.requestedMimeType(), response);
        Long declaredContentLength = resolveDeclaredContentLength(response);
        Map<String, List<String>> responseHeaders = Map.copyOf(response.headers().map());
        InputStream body = response.body();
        AtomicBoolean consumed = new AtomicBoolean(false);

        return new FetchSession() {
            @Override
            public String sourceUrl() {
                return sourceUrl;
            }

            @Override
            public String finalUrl() {
                return finalUrl;
            }

            @Override
            public int statusCode() {
                return response.statusCode();
            }

            @Override
            public Map<String, List<String>> responseHeaders() {
                return responseHeaders;
            }

            @Override
            public String resolvedFileName() {
                return resolvedFileName;
            }

            @Override
            public String resolvedMimeType() {
                return resolvedMimeType;
            }

            @Override
            public Long declaredContentLength() {
                return declaredContentLength;
            }

            @Override
            public Mono<FetchedResource> readBody(long maxBytes) {
                if (maxBytes <= 0) {
                    return Mono.error(new IllegalArgumentException("maxBytes must be positive"));
                }
                if (declaredContentLength != null && declaredContentLength > maxBytes) {
                    return Mono.error(
                            new SourceTooLargeException(
                                    "Source exceeds declared byte limit: sourceUrl=%s declared=%d max=%d"
                                            .formatted(
                                                    sourceUrl, declaredContentLength, maxBytes)));
                }
                if (!consumed.compareAndSet(false, true)) {
                    return Mono.error(
                            new IllegalStateException("FetchSession body already consumed"));
                }
                return Mono.fromCallable(
                        () -> {
                            byte[] data = readBodyBytes(body, maxBytes);
                            return new FetchedResource(
                                    sourceUrl,
                                    finalUrl,
                                    resolvedFileName,
                                    data,
                                    resolvedMimeType,
                                    data.length);
                        });
            }
        };
    }

    private static HttpClient defaultHttpClient() {
        return HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(DEFAULT_CONNECT_TIMEOUT)
                .build();
    }

    private void closeBody(HttpResponse<InputStream> response) {
        InputStream body = response.body();
        if (body == null) {
            return;
        }
        try {
            body.close();
        } catch (Exception ignored) {
            // Closing a discarded response body is best effort.
        }
    }

    private String resolveFileName(
            String sourceUrl, String requestedFileName, HttpResponse<?> response) {
        if (requestedFileName != null && !requestedFileName.isBlank()) {
            return requestedFileName;
        }

        String contentDispositionFileName =
                response.headers()
                        .firstValue("Content-Disposition")
                        .map(this::extractFileNameFromContentDisposition)
                        .orElse(null);
        if (contentDispositionFileName != null) {
            return contentDispositionFileName;
        }

        URI responseUri = response.uri();
        String path = responseUri != null ? responseUri.getPath() : URI.create(sourceUrl).getPath();
        if (path != null && !path.isBlank()) {
            String candidate = sanitizeFileName(path);
            if (candidate != null) {
                return candidate;
            }
        }
        return DEFAULT_FILE_NAME;
    }

    private String resolveMimeType(
            String resolvedFileName, String requestedMimeType, HttpResponse<?> response) {
        if (requestedMimeType != null && !requestedMimeType.isBlank()) {
            return requestedMimeType;
        }

        String responseMimeType =
                response.headers()
                        .firstValue("Content-Type")
                        .map(this::stripContentTypeParameters)
                        .orElse(null);
        if (responseMimeType != null && !responseMimeType.isBlank()) {
            return responseMimeType;
        }

        String guessedMimeType = URLConnection.guessContentTypeFromName(resolvedFileName);
        return guessedMimeType == null || guessedMimeType.isBlank()
                ? DEFAULT_MIME_TYPE
                : guessedMimeType;
    }

    private Long resolveDeclaredContentLength(HttpResponse<?> response) {
        return response.headers()
                .firstValue("Content-Length")
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(
                        value -> {
                            try {
                                return Long.parseLong(value);
                            } catch (NumberFormatException ignored) {
                                return null;
                            }
                        })
                .orElse(null);
    }

    private byte[] readBodyBytes(InputStream body, long maxBytes) throws Exception {
        try (body;
                var output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            long totalBytes = 0;
            int read;
            while ((read = body.read(buffer)) >= 0) {
                totalBytes += read;
                if (totalBytes > maxBytes) {
                    throw new SourceTooLargeException(
                            "Source exceeds byte limit while reading: max=%d".formatted(maxBytes));
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private String extractFileNameFromContentDisposition(String contentDisposition) {
        for (String part : contentDisposition.split(";")) {
            String trimmed = part.trim();
            if (trimmed.regionMatches(true, 0, "filename*=", 0, "filename*=".length())) {
                String encodedValue = trimmed.substring("filename*=".length());
                int charsetSeparator = encodedValue.indexOf("''");
                if (charsetSeparator >= 0) {
                    encodedValue = encodedValue.substring(charsetSeparator + 2);
                }
                return sanitizeFileName(
                        URLDecoder.decode(unquote(encodedValue), StandardCharsets.UTF_8));
            }
            if (trimmed.regionMatches(true, 0, "filename=", 0, "filename=".length())) {
                return sanitizeFileName(unquote(trimmed.substring("filename=".length())));
            }
        }
        return null;
    }

    private String stripContentTypeParameters(String contentType) {
        int separator = contentType.indexOf(';');
        return separator >= 0 ? contentType.substring(0, separator).trim() : contentType.trim();
    }

    private String sanitizeFileName(String candidate) {
        if (candidate == null) {
            return null;
        }
        String normalized = candidate.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        int slashIndex = Math.max(normalized.lastIndexOf('/'), normalized.lastIndexOf('\\'));
        String fileName = slashIndex >= 0 ? normalized.substring(slashIndex + 1) : normalized;
        return fileName.isBlank() ? null : fileName;
    }

    private String unquote(String value) {
        String normalized = value.trim();
        if (normalized.length() >= 2 && normalized.startsWith("\"") && normalized.endsWith("\"")) {
            return normalized.substring(1, normalized.length() - 1);
        }
        return normalized;
    }
}
