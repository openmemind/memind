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
package com.openmemind.ai.memory.core.extraction;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.exception.SourceTooLargeException;
import com.openmemind.ai.memory.core.exception.UnsupportedContentSourceException;
import com.openmemind.ai.memory.core.extraction.rawdata.RawContentProcessor;
import com.openmemind.ai.memory.core.extraction.rawdata.RawContentProcessorRegistry;
import com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent;
import com.openmemind.ai.memory.core.extraction.source.DirectContentSource;
import com.openmemind.ai.memory.core.extraction.source.FileExtractionSource;
import com.openmemind.ai.memory.core.extraction.source.UrlExtractionSource;
import com.openmemind.ai.memory.core.plugin.RawDataIngestionPolicyRegistry;
import com.openmemind.ai.memory.core.resource.ContentCapability;
import com.openmemind.ai.memory.core.resource.ContentParserRegistry;
import com.openmemind.ai.memory.core.resource.FetchSession;
import com.openmemind.ai.memory.core.resource.FetchedResource;
import com.openmemind.ai.memory.core.resource.ResourceFetchRequest;
import com.openmemind.ai.memory.core.resource.ResourceFetcher;
import com.openmemind.ai.memory.core.resource.ResourceRef;
import com.openmemind.ai.memory.core.resource.ResourceStore;
import com.openmemind.ai.memory.core.resource.SourceDescriptor;
import com.openmemind.ai.memory.core.resource.SourceKind;
import com.openmemind.ai.memory.core.utils.HashUtils;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import reactor.core.publisher.Mono;

final class DefaultExtractionRequestResolver implements ExtractionRequestResolver {

    private final RawContentProcessorRegistry rawContentProcessorRegistry;
    private final ContentParserRegistry contentParserRegistry;
    private final ResourceStore resourceStore;
    private final ResourceFetcher resourceFetcher;
    private final RawDataIngestionPolicyRegistry ingestionPolicyRegistry;

    DefaultExtractionRequestResolver(
            RawContentProcessorRegistry rawContentProcessorRegistry,
            ContentParserRegistry contentParserRegistry,
            ResourceStore resourceStore,
            ResourceFetcher resourceFetcher,
            RawDataIngestionPolicyRegistry ingestionPolicyRegistry) {
        this.rawContentProcessorRegistry = rawContentProcessorRegistry;
        this.contentParserRegistry = contentParserRegistry;
        this.resourceStore = resourceStore;
        this.resourceFetcher = resourceFetcher;
        this.ingestionPolicyRegistry =
                Objects.requireNonNull(ingestionPolicyRegistry, "ingestionPolicyRegistry");
    }

    @Override
    public Mono<ResolvedExtractionRequest> resolve(ExtractionRequest request) {
        Objects.requireNonNull(request, "request is required");
        return switch (request.source()) {
            case DirectContentSource directSource ->
                    resolveDirectContentRequest(request, directSource);
            case FileExtractionSource fileSource ->
                    resolveFileExtractionRequest(request, fileSource);
            case UrlExtractionSource urlSource -> resolveUrlExtractionRequest(request, urlSource);
        };
    }

    private Mono<ResolvedExtractionRequest> resolveDirectContentRequest(
            ExtractionRequest request, DirectContentSource directSource) {
        RawContent content = directSource.content();
        if (content.directGovernanceType() == null) {
            return Mono.just(
                    resolvedRequest(
                            request.memoryId(),
                            content,
                            request.metadata(),
                            request.config(),
                            null));
        }
        RawContent normalizedContent = validateWithProcessor(content);
        return Mono.just(
                resolvedRequest(
                        request.memoryId(),
                        normalizedContent,
                        ExtractionRequest.normalizeMultimodalMetadata(normalizedContent),
                        request.config(),
                        null));
    }

    private Mono<ResolvedExtractionRequest> resolveFileExtractionRequest(
            ExtractionRequest request, FileExtractionSource fileSource) {
        ContentParserRegistry parserRegistry = requireContentParserRegistry("file");
        byte[] fileBytes = fileSource.data();
        String checksum = HashUtils.sha256(fileBytes);
        long sizeBytes = fileBytes.length;
        SourceDescriptor source =
                new SourceDescriptor(
                        SourceKind.FILE,
                        fileSource.fileName(),
                        fileSource.mimeType(),
                        sizeBytes,
                        null);
        return parserRegistry
                .resolve(source)
                .flatMap(
                        resolution -> {
                            validateKnownSourceSize(
                                    source, resolveSourceLimit(resolution.capability()));
                            return parserRegistry
                                    .parse(fileBytes, source)
                                    .switchIfEmpty(
                                            Mono.error(
                                                    new IllegalStateException(
                                                            "ContentParserRegistry returned no"
                                                                    + " content for file"
                                                                    + " extraction")))
                                    .flatMap(
                                            parsedContent -> {
                                                RawContent normalizedContent =
                                                        validateWithProcessor(
                                                                MultimodalMetadataNormalizer
                                                                        .normalizeParsedContent(
                                                                                parsedContent,
                                                                                request.metadata(),
                                                                                resolution
                                                                                        .capability()));
                                                if (resourceStore == null) {
                                                    return Mono.just(
                                                            buildResolvedParsedRequest(
                                                                    request.memoryId(),
                                                                    normalizedContent,
                                                                    request.metadata(),
                                                                    request.config(),
                                                                    fileSource.fileName(),
                                                                    fileSource.mimeType(),
                                                                    checksum,
                                                                    sizeBytes,
                                                                    null));
                                                }
                                                return resourceStore
                                                        .store(
                                                                request.memoryId(),
                                                                fileSource.fileName(),
                                                                fileBytes,
                                                                fileSource.mimeType(),
                                                                Map.of(
                                                                        "checksum",
                                                                        checksum,
                                                                        "sizeBytes",
                                                                        sizeBytes))
                                                        .map(
                                                                storedResource ->
                                                                        buildResolvedParsedRequest(
                                                                                request.memoryId(),
                                                                                normalizedContent,
                                                                                request.metadata(),
                                                                                request.config(),
                                                                                fileSource
                                                                                        .fileName(),
                                                                                fileSource
                                                                                        .mimeType(),
                                                                                checksum,
                                                                                sizeBytes,
                                                                                storedResource));
                                            });
                        });
    }

    private Mono<ResolvedExtractionRequest> resolveUrlExtractionRequest(
            ExtractionRequest request, UrlExtractionSource urlSource) {
        ContentParserRegistry parserRegistry = requireContentParserRegistry("URL");
        ResourceFetcher fetcher = requireResourceFetcher();
        SourceDescriptor provisionalSource =
                new SourceDescriptor(
                        SourceKind.URL,
                        urlSource.fileName(),
                        urlSource.mimeType(),
                        null,
                        urlSource.sourceUrl());

        return parserRegistry
                .resolve(provisionalSource)
                .then()
                .onErrorResume(UnsupportedContentSourceException.class, ignored -> Mono.empty())
                .then(
                        Mono.defer(
                                () ->
                                        fetcher.open(
                                                        new ResourceFetchRequest(
                                                                request.memoryId(),
                                                                urlSource.sourceUrl(),
                                                                urlSource.fileName(),
                                                                urlSource.mimeType()))
                                                .flatMap(
                                                        session ->
                                                                resolveFetchedUrlRequest(
                                                                        request, session))));
    }

    private Mono<ResolvedExtractionRequest> resolveFetchedUrlRequest(
            ExtractionRequest request, FetchSession session) {
        ContentParserRegistry parserRegistry = requireContentParserRegistry("URL");
        SourceDescriptor finalSource =
                new SourceDescriptor(
                        SourceKind.URL,
                        session.resolvedFileName(),
                        session.resolvedMimeType(),
                        session.declaredContentLength(),
                        session.finalUrl());

        return parserRegistry
                .resolve(finalSource)
                .flatMap(
                        finalResolution -> {
                            validateKnownSourceSize(
                                    finalSource, resolveSourceLimit(finalResolution.capability()));
                            return session.readBody(
                                            resolveSourceLimit(finalResolution.capability()))
                                    .flatMap(
                                            fetched -> {
                                                SourceDescriptor fetchedSource =
                                                        new SourceDescriptor(
                                                                SourceKind.URL,
                                                                fetched.fileName(),
                                                                fetched.mimeType(),
                                                                fetched.sizeBytes(),
                                                                fetched.finalUrl());
                                                return parserRegistry
                                                        .parse(fetched.data(), fetchedSource)
                                                        .switchIfEmpty(
                                                                Mono.error(
                                                                        new IllegalStateException(
                                                                                "ContentParserRegistry"
                                                                                    + " returned no"
                                                                                    + " content for"
                                                                                    + " URL extraction")))
                                                        .flatMap(
                                                                parsedContent -> {
                                                                    RawContent normalizedContent =
                                                                            validateWithProcessor(
                                                                                    MultimodalMetadataNormalizer
                                                                                            .normalizeParsedContent(
                                                                                                    parsedContent,
                                                                                                    request
                                                                                                            .metadata(),
                                                                                                    finalResolution
                                                                                                            .capability()));
                                                                    return persistFetchedUrlRequest(
                                                                            request.memoryId(),
                                                                            request.metadata(),
                                                                            request.config(),
                                                                            normalizedContent,
                                                                            fetched);
                                                                });
                                            });
                        });
    }

    private Mono<ResolvedExtractionRequest> persistFetchedUrlRequest(
            MemoryId memoryId,
            Map<String, Object> requestMetadata,
            ExtractionConfig config,
            RawContent parsedContent,
            FetchedResource fetchedResource) {
        byte[] fetchedBytes = fetchedResource.data();
        String checksum = HashUtils.sha256(fetchedBytes);
        long sizeBytes = fetchedResource.sizeBytes();
        Map<String, Object> transportMetadata = new LinkedHashMap<>(requestMetadata);
        transportMetadata.put("sourceUri", fetchedResource.finalUrl());
        transportMetadata.put("sourceKind", SourceKind.URL.name());
        if (resourceStore == null) {
            return Mono.just(
                    buildResolvedParsedRequest(
                            memoryId,
                            parsedContent,
                            transportMetadata,
                            config,
                            fetchedResource.fileName(),
                            fetchedResource.mimeType(),
                            checksum,
                            sizeBytes,
                            null));
        }
        return resourceStore
                .store(
                        memoryId,
                        fetchedResource.fileName(),
                        fetchedBytes,
                        fetchedResource.mimeType(),
                        Map.of("checksum", checksum, "sizeBytes", sizeBytes))
                .map(
                        storedResource ->
                                buildResolvedParsedRequest(
                                        memoryId,
                                        parsedContent,
                                        transportMetadata,
                                        config,
                                        fetchedResource.fileName(),
                                        fetchedResource.mimeType(),
                                        checksum,
                                        sizeBytes,
                                        storedResource));
    }

    private ResolvedExtractionRequest buildResolvedParsedRequest(
            MemoryId memoryId,
            RawContent parsedContent,
            Map<String, Object> requestMetadata,
            ExtractionConfig config,
            String fileName,
            String fallbackMimeType,
            String checksum,
            long sizeBytes,
            ResourceRef storedResource) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        if (requestMetadata != null && !requestMetadata.isEmpty()) {
            normalized.putAll(requestMetadata);
        }
        normalized.putAll(ExtractionRequest.normalizeMultimodalMetadata(parsedContent));
        if (requestMetadata != null && !requestMetadata.isEmpty()) {
            reapplyTransportContext(requestMetadata, normalized, "sourceKind");
            reapplyTransportContext(requestMetadata, normalized, "sourceUri");
        }
        normalized.putIfAbsent("sourceKind", SourceKind.FILE.name());

        String mimeType = resolveMimeType(parsedContent, fallbackMimeType, storedResource);
        if (mimeType != null) {
            normalized.put("mimeType", mimeType);
        }
        normalized.put("fileName", fileName);
        normalized.put("checksum", checksum);
        normalized.put("sizeBytes", sizeBytes);

        if (storedResource != null) {
            normalized.put("resourceId", storedResource.id());
            if (storedResource.storageUri() != null && !storedResource.storageUri().isBlank()) {
                normalized.put("storageUri", storedResource.storageUri());
            }
        } else {
            normalized.put(
                    "resourceId",
                    HashUtils.sampledSha256(
                            memoryId.toIdentifier() + "|" + fileName + "|" + checksum));
        }

        return resolvedRequest(memoryId, parsedContent, normalized, config, storedResource);
    }

    private long resolveSourceLimit(ContentCapability capability) {
        return ingestionPolicyRegistry.resolve(capability).sourceLimit().maxBytes();
    }

    private void validateKnownSourceSize(SourceDescriptor source, long maxBytes) {
        if (source.sizeBytes() != null && source.sizeBytes() > maxBytes) {
            throw new SourceTooLargeException(
                    "Source exceeds byte limit: source=%s size=%d max=%d"
                            .formatted(source, source.sizeBytes(), maxBytes));
        }
    }

    private String resolveMimeType(
            RawContent parsedContent, String fallbackMimeType, ResourceRef storedResource) {
        if (storedResource != null && storedResource.mimeType() != null) {
            return storedResource.mimeType();
        }
        Object value = ExtractionRequest.normalizeMultimodalMetadata(parsedContent).get("mimeType");
        if (value == null) {
            return fallbackMimeType;
        }
        String contentMimeType = value.toString();
        return contentMimeType.isBlank() ? fallbackMimeType : contentMimeType;
    }

    private void reapplyTransportContext(
            Map<String, Object> requestMetadata, Map<String, Object> normalized, String key) {
        Object value = requestMetadata.get(key);
        if (value != null) {
            normalized.put(key, value);
        }
    }

    private ContentParserRegistry requireContentParserRegistry(String sourceType) {
        if (contentParserRegistry == null) {
            throw new IllegalStateException(
                    "ContentParserRegistry is required for " + sourceType + " extraction requests");
        }
        return contentParserRegistry;
    }

    private ResourceFetcher requireResourceFetcher() {
        if (resourceFetcher == null) {
            throw new IllegalStateException(
                    "ResourceFetcher is required for URL extraction requests");
        }
        return resourceFetcher;
    }

    private ResolvedExtractionRequest resolvedRequest(
            MemoryId memoryId,
            RawContent content,
            Map<String, Object> metadata,
            ExtractionConfig config,
            ResourceRef cleanupRef) {
        return new ResolvedExtractionRequest(
                memoryId, content, content.contentType(), metadata, config, cleanupRef);
    }

    private <T extends RawContent> T validateWithProcessor(T content) {
        resolveRequiredProcessor(content).validateParsedContent(content);
        return content;
    }

    private <T extends RawContent> RawContentProcessor<T> resolveRequiredProcessor(T content) {
        if (rawContentProcessorRegistry == null) {
            throw new IllegalStateException(
                    "RawContentProcessorRegistry is required for raw content type: "
                            + content.contentType());
        }
        try {
            return rawContentProcessorRegistry.resolve(content);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "No processor registered for raw content type: " + content.contentType(), e);
        }
    }
}
