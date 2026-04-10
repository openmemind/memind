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

import com.openmemind.ai.memory.core.data.MemoryId;
import java.net.URI;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
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
    public Mono<FetchedResource> fetch(
            MemoryId memoryId, String sourceUrl, String fileName, String mimeType) {
        Objects.requireNonNull(memoryId, "memoryId is required");
        Objects.requireNonNull(sourceUrl, "sourceUrl is required");

        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(URI.create(sourceUrl))
                        .header("Accept", "*/*")
                        .timeout(requestTimeout)
                        .GET()
                        .build();

        return Mono.fromFuture(
                        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()))
                .map(response -> toFetchedResource(sourceUrl, fileName, mimeType, response));
    }

    private FetchedResource toFetchedResource(
            String sourceUrl, String fileName, String mimeType, HttpResponse<byte[]> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException(
                    "Resource download failed: status="
                            + response.statusCode()
                            + ", sourceUrl="
                            + sourceUrl);
        }

        String resolvedFileName = resolveFileName(sourceUrl, fileName, response);
        String resolvedMimeType = resolveMimeType(resolvedFileName, mimeType, response);
        return new FetchedResource(sourceUrl, resolvedFileName, response.body(), resolvedMimeType);
    }

    private String resolveFileName(
            String sourceUrl, String requestedFileName, HttpResponse<byte[]> response) {
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
            String resolvedFileName, String requestedMimeType, HttpResponse<byte[]> response) {
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
