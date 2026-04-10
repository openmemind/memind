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

import com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import reactor.core.publisher.Mono;

/**
 * Default in-memory {@link ContentParserRegistry} with deterministic validation and routing.
 */
public final class DefaultContentParserRegistry implements ContentParserRegistry {

    private final List<ContentParser> parsers;
    private final Map<String, Set<String>> supportedMimeTypesByContentType;

    public DefaultContentParserRegistry(List<ContentParser> parsers) {
        this.parsers =
                Objects.requireNonNull(parsers, "parsers").stream()
                        .filter(Objects::nonNull)
                        .toList();
        validate(this.parsers);
        this.supportedMimeTypesByContentType = buildCapabilities(this.parsers);
    }

    @Override
    public Mono<RawContent> parse(byte[] data, String fileName, String mimeType) {
        List<ContentParser> matches =
                parsers.stream().filter(parser -> parser.supports(fileName, mimeType)).toList();
        if (matches.isEmpty()) {
            return Mono.error(
                    new UnsupportedContentSourceException(
                            "Unsupported source: fileName=" + fileName + ", mimeType=" + mimeType));
        }
        if (matches.size() > 1) {
            return Mono.error(
                    new AmbiguousContentParserException(
                            "Multiple parsers matched fileName="
                                    + fileName
                                    + ", mimeType="
                                    + mimeType
                                    + ": "
                                    + matches.stream()
                                            .map(ContentParser::contentType)
                                            .collect(Collectors.joining(", "))));
        }
        return matches.get(0).parse(data, fileName, mimeType);
    }

    @Override
    public Map<String, Set<String>> supportedMimeTypesByContentType() {
        return supportedMimeTypesByContentType;
    }

    private static void validate(List<ContentParser> parsers) {
        Set<String> contentTypes = new LinkedHashSet<>();
        Set<String> stableMimeTypes = new LinkedHashSet<>();
        for (ContentParser parser : parsers) {
            String contentType =
                    Objects.requireNonNull(parser.contentType(), "parser.contentType()");
            if (!contentTypes.add(contentType)) {
                throw new IllegalStateException(
                        "Only one parser per contentType is supported in this phase: "
                                + contentType);
            }

            Set<String> mimeTypes =
                    Objects.requireNonNull(
                            parser.supportedMimeTypes(), "parser.supportedMimeTypes()");
            for (String mimeType : mimeTypes) {
                String normalizedMimeType = Objects.requireNonNull(mimeType, "mimeType");
                if (!stableMimeTypes.add(normalizedMimeType)) {
                    throw new IllegalStateException(
                            "Duplicate stable mimeType across parsers: " + normalizedMimeType);
                }
            }
        }
    }

    private static Map<String, Set<String>> buildCapabilities(List<ContentParser> parsers) {
        Map<String, Set<String>> grouped = new LinkedHashMap<>();
        for (ContentParser parser : parsers) {
            grouped.computeIfAbsent(parser.contentType(), ignored -> new LinkedHashSet<>())
                    .addAll(parser.supportedMimeTypes());
        }

        Map<String, Set<String>> capabilities = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : grouped.entrySet()) {
            capabilities.put(
                    entry.getKey(),
                    Collections.unmodifiableSet(new LinkedHashSet<>(entry.getValue())));
        }
        return Collections.unmodifiableMap(capabilities);
    }
}
