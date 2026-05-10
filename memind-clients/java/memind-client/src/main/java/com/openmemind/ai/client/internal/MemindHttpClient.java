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
package com.openmemind.ai.client.internal;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.openmemind.ai.client.exception.MemindApiException;
import com.openmemind.ai.client.exception.MemindConnectionException;
import com.openmemind.ai.client.exception.MemindTimeoutException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MemindHttpClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(MemindHttpClient.class);
    private static final String USER_AGENT = "memind-java-client/0.2.0";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI baseUri;
    private final String apiToken;
    private final Duration readTimeout;

    public MemindHttpClient(
            String baseUrl, String apiToken, Duration connectTimeout, Duration readTimeout) {
        this.baseUri = normalizeBaseUri(baseUrl);
        this.apiToken = normalizeApiToken(apiToken);
        this.readTimeout = Objects.requireNonNull(readTimeout, "readTimeout");
        this.httpClient =
                HttpClient.newBuilder()
                        .connectTimeout(Objects.requireNonNull(connectTimeout, "connectTimeout"))
                        .build();
        this.objectMapper =
                new ObjectMapper()
                        .registerModule(new JavaTimeModule())
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                        .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    public <T> CompletableFuture<T> get(String path, TypeReference<ApiResult<T>> responseType) {
        HttpRequest request = newRequestBuilder(path).GET().build();
        log.debug("GET {}", request.uri());
        return sendAsync(request, responseType);
    }

    public <T> CompletableFuture<T> post(
            String path, Object body, TypeReference<ApiResult<T>> responseType) {
        try {
            byte[] bodyBytes = objectMapper.writeValueAsBytes(body);
            HttpRequest request =
                    newRequestBuilder(path)
                            .POST(HttpRequest.BodyPublishers.ofByteArray(bodyBytes))
                            .build();
            log.debug("POST {}", request.uri());
            return sendAsync(request, responseType);
        } catch (IOException e) {
            return CompletableFuture.failedFuture(
                    new MemindConnectionException("Failed to serialize request body", e));
        }
    }

    private HttpRequest.Builder newRequestBuilder(String path) {
        HttpRequest.Builder builder =
                HttpRequest.newBuilder()
                        .uri(resolvePath(path))
                        .timeout(readTimeout)
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .header("User-Agent", USER_AGENT);
        if (apiToken != null) {
            builder.header("Authorization", "Bearer " + apiToken);
        }
        return builder;
    }

    private <T> CompletableFuture<T> sendAsync(
            HttpRequest request, TypeReference<ApiResult<T>> responseType) {
        return httpClient
                .sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                .thenApply(response -> handleResponse(response, responseType))
                .exceptionallyCompose(ex -> CompletableFuture.failedFuture(mapFailure(request, ex)));
    }

    private RuntimeException mapFailure(HttpRequest request, Throwable ex) {
        Throwable cause = ex instanceof CompletionException ? ex.getCause() : ex;
        if (cause instanceof java.net.http.HttpTimeoutException) {
            return new MemindTimeoutException("Request timed out: " + request.uri(), cause);
        }
        if (cause instanceof ConnectException || cause instanceof IOException) {
            return new MemindConnectionException("Connection failed: " + request.uri(), cause);
        }
        if (cause instanceof MemindApiException apiException) {
            return apiException;
        }
        if (cause instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new MemindConnectionException("Unexpected error: " + cause.getMessage(), cause);
    }

    private <T> T handleResponse(
            HttpResponse<byte[]> response, TypeReference<ApiResult<T>> responseType) {
        int status = response.statusCode();
        byte[] body = response.body();
        log.debug("Response status: {}", status);

        try {
            ApiResult<T> result = objectMapper.readValue(body, responseType);
            if (status >= 200 && status < 300 && result.isSuccess()) {
                return result.data();
            }

            throw new MemindApiException(status, result.code(), result.message(), result.traceId());
        } catch (MemindApiException e) {
            throw e;
        } catch (IOException e) {
            throw new MemindApiException(
                    status, "parse_error", "Failed to parse response: " + new String(body), null);
        }
    }

    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    @Override
    public void close() {
        // JDK HttpClient does not require explicit close in Java 17.
    }

    private URI resolvePath(String path) {
        Objects.requireNonNull(path, "path");
        String base = baseUri.toString();
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        return URI.create(base + normalizedPath);
    }

    private static URI normalizeBaseUri(String baseUrl) {
        Objects.requireNonNull(baseUrl, "baseUrl");
        String normalized = baseUrl.trim();
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return URI.create(normalized);
    }

    private static String normalizeApiToken(String apiToken) {
        if (apiToken == null) {
            return null;
        }
        String normalized = apiToken.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
