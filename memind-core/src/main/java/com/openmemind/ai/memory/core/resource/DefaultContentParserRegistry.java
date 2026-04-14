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

import com.openmemind.ai.memory.core.exception.AmbiguousContentParserException;
import com.openmemind.ai.memory.core.exception.UnsupportedContentSourceException;
import com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import reactor.core.publisher.Mono;

/**
 * Default in-memory {@link ContentParserRegistry} with deterministic validation and routing.
 */
public final class DefaultContentParserRegistry implements ContentParserRegistry {

    private static final Comparator<ContentParser> RESOLUTION_ORDER =
            Comparator.comparingInt(ContentParser::priority)
                    .reversed()
                    .thenComparing(ContentParser::parserId);

    private final List<ContentParser> parsers;
    private final List<ContentCapability> capabilities;

    public DefaultContentParserRegistry(List<ContentParser> parsers) {
        this.parsers =
                List.copyOf(
                        Objects.requireNonNull(parsers, "parsers").stream()
                                .filter(Objects::nonNull)
                                .toList());
        validate(this.parsers);
        this.capabilities =
                this.parsers.stream()
                        .sorted(RESOLUTION_ORDER)
                        .map(DefaultContentParserRegistry::capabilityOf)
                        .toList();
    }

    @Override
    public Mono<ParserResolution> resolve(SourceDescriptor source) {
        Objects.requireNonNull(source, "source is required");
        List<ContentParser> matches =
                parsers.stream()
                        .filter(parser -> parser.supports(source))
                        .sorted(RESOLUTION_ORDER)
                        .toList();
        if (matches.isEmpty()) {
            return Mono.error(
                    new UnsupportedContentSourceException("Unsupported source: " + source));
        }
        if (matches.size() > 1 && matches.get(0).priority() == matches.get(1).priority()) {
            return Mono.error(
                    new AmbiguousContentParserException(
                            "Ambiguous parsers for "
                                    + source
                                    + ": "
                                    + matches.get(0).parserId()
                                    + ", "
                                    + matches.get(1).parserId()));
        }
        ContentParser parser = matches.get(0);
        return Mono.just(new ParserResolution(parser, capabilityOf(parser)));
    }

    @Override
    public Mono<RawContent> parse(byte[] data, SourceDescriptor source) {
        return resolve(source).flatMap(resolution -> resolution.parser().parse(data, source));
    }

    @Override
    public List<ContentCapability> capabilities() {
        return capabilities;
    }

    private static void validate(List<ContentParser> parsers) {
        java.util.Set<String> parserIds = new java.util.LinkedHashSet<>();
        for (ContentParser parser : parsers) {
            String parserId = Objects.requireNonNull(parser.parserId(), "parser.parserId()");
            if (!parserIds.add(parserId)) {
                throw new IllegalStateException(
                        "Duplicate parserId across parsers is not allowed: " + parserId);
            }
            Objects.requireNonNull(parser.contentType(), "parser.contentType()");
            Objects.requireNonNull(parser.contentProfile(), "parser.contentProfile()");
            Objects.requireNonNull(parser.governanceType(), "parser.governanceType()");
            Objects.requireNonNull(parser.supportedMimeTypes(), "parser.supportedMimeTypes()");
            Objects.requireNonNull(parser.supportedExtensions(), "parser.supportedExtensions()");
        }
    }

    private static ContentCapability capabilityOf(ContentParser parser) {
        return new ContentCapability(
                parser.parserId(),
                parser.contentType(),
                parser.contentProfile(),
                parser.governanceType(),
                parser.supportedMimeTypes(),
                parser.supportedExtensions(),
                parser.priority());
    }
}
