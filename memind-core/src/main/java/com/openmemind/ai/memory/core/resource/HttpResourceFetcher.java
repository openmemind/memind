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

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
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
import java.util.concurrent.atomic.AtomicBoolean;

import com.openmemind.ai.memory.core.exception.SourceTooLargeException;
import reactor.core.publisher.Mono;

/**
 * Default {@link ResourceFetcher} backed by JDK {@link HttpClient}.
 */
public class HttpResourceFetcher implements ResourceFetcher {

    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final String DEFAULT_FILE_NAME = "download";
    private static final String DEFAULT_MIME_TYPE = "application/octet-stream";

    private final HttpClient httpClient;
    private final Duration requestTimeout;

    public HttpResourceFetcher() {
        this(
                HttpClient.newBuilder()
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .connectTimeout(DEFAULT_CONNECT_TIMEOUT)
                        .build(),
                DEFAULT_REQUEST_TIMEOUT);
    }

    public HttpResourceFetcher(HttpClient httpClient) {
        this(httpClient, DEFAULT_REQUEST_TIMEOUT);
    }

    public HttpResourceFetcher(HttpClient httpClient, Duration requestTimeout) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient is required");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout is required");
    }

    @Override
    public Mono<FetchSession> open(ResourceFetchRequest request) {
        Objects.requireNonNull(request, "request is required");

        HttpRequest httpRequest =
                HttpRequest.newBuilder()
                        .uri(URI.create(request.sourceUrl()))
                        .header("Accept", "*/*")
                        .timeout(requestTimeout)
                        .GET()
                        .build();

        return Mono.fromFuture(
                        httpClient.sendAsync(
                                httpRequest, HttpResponse.BodyHandlers.ofInputStream()))
                .map(response -> toFetchSession(request, response));
    }

    private FetchSession toFetchSession(
            ResourceFetchRequest request, HttpResponse<InputStream> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException(
                    "Resource download failed: status="
                            + response.statusCode()
                            + ", sourceUrl="
                            + request.sourceUrl());
        }

        String sourceUrl = request.sourceUrl();
        String finalUrl = response.uri() == null ? sourceUrl : response.uri().toString();
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
