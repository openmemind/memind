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
package com.openmemind.ai.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.openmemind.ai.client.exception.MemindClientException;
import com.openmemind.ai.client.internal.ApiResult;
import com.openmemind.ai.client.internal.MemindHttpClient;
import com.openmemind.ai.client.model.request.AddMessageRequest;
import com.openmemind.ai.client.model.request.CommitMemoryRequest;
import com.openmemind.ai.client.model.request.ExtractMemoryRequest;
import com.openmemind.ai.client.model.request.RetrieveMemoryRequest;
import com.openmemind.ai.client.model.response.HealthResponse;
import com.openmemind.ai.client.model.response.RetrieveMemoryResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;

public class MemindClient implements AutoCloseable {

    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(30);

    private final MemindHttpClient httpClient;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private MemindClient(Builder builder) {
        this.httpClient =
                new MemindHttpClient(
                        builder.baseUrl,
                        builder.apiToken,
                        builder.connectTimeout,
                        builder.readTimeout);
    }

    public void addMessage(AddMessageRequest request) {
        joinAndUnwrap(addMessageAsync(request));
    }

    public void extract(ExtractMemoryRequest request) {
        joinAndUnwrap(extractAsync(request));
    }

    public void commit(CommitMemoryRequest request) {
        joinAndUnwrap(commitAsync(request));
    }

    public RetrieveMemoryResponse retrieve(RetrieveMemoryRequest request) {
        return joinAndUnwrap(retrieveAsync(request));
    }

    public HealthResponse health() {
        return joinAndUnwrap(healthAsync());
    }

    public CompletableFuture<Void> addMessageAsync(AddMessageRequest request) {
        ensureOpen();
        return httpClient.post(
                "/open/v1/memory/add-message",
                Objects.requireNonNull(request, "request"),
                new TypeReference<ApiResult<Void>>() {});
    }

    public CompletableFuture<Void> extractAsync(ExtractMemoryRequest request) {
        ensureOpen();
        return httpClient.post(
                "/open/v1/memory/extract",
                Objects.requireNonNull(request, "request"),
                new TypeReference<ApiResult<Void>>() {});
    }

    public CompletableFuture<Void> commitAsync(CommitMemoryRequest request) {
        ensureOpen();
        return httpClient.post(
                "/open/v1/memory/commit",
                Objects.requireNonNull(request, "request"),
                new TypeReference<ApiResult<Void>>() {});
    }

    public CompletableFuture<RetrieveMemoryResponse> retrieveAsync(RetrieveMemoryRequest request) {
        ensureOpen();
        return httpClient.post(
                "/open/v1/memory/retrieve",
                Objects.requireNonNull(request, "request"),
                new TypeReference<ApiResult<RetrieveMemoryResponse>>() {});
    }

    public CompletableFuture<HealthResponse> healthAsync() {
        ensureOpen();
        return httpClient.get("/open/v1/health", new TypeReference<ApiResult<HealthResponse>>() {});
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            httpClient.close();
        }
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("MemindClient is closed");
        }
    }

    private <T> T joinAndUnwrap(CompletableFuture<T> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new MemindClientException(cause.getMessage(), cause);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String baseUrl;
        private String apiToken;
        private Duration connectTimeout = DEFAULT_CONNECT_TIMEOUT;
        private Duration readTimeout = DEFAULT_READ_TIMEOUT;

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder apiToken(String apiToken) {
            this.apiToken = apiToken;
            return this;
        }

        public Builder connectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }

        public Builder readTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout;
            return this;
        }

        public MemindClient build() {
            Objects.requireNonNull(baseUrl, "baseUrl");
            Objects.requireNonNull(connectTimeout, "connectTimeout");
            Objects.requireNonNull(readTimeout, "readTimeout");
            return new MemindClient(this);
        }
    }
}
