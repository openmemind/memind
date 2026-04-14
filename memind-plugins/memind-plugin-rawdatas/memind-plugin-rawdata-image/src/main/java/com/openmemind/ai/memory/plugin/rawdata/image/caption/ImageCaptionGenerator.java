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
package com.openmemind.ai.memory.plugin.rawdata.image.caption;

import com.openmemind.ai.memory.core.extraction.rawdata.caption.CaptionGenerator;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.Segment;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Deterministic caption generator for parsed image segments.
 */
public final class ImageCaptionGenerator implements CaptionGenerator {

    private final int maxLength;

    public ImageCaptionGenerator() {
        this(200);
    }

    public ImageCaptionGenerator(int maxLength) {
        this.maxLength = maxLength;
    }

    @Override
    public Mono<List<Segment>> generateForSegments(List<Segment> segments) {
        return preserveOrGenerate(segments);
    }

    @Override
    public Mono<List<Segment>> generateForSegments(List<Segment> segments, String language) {
        return preserveOrGenerate(segments);
    }

    @Override
    public Mono<String> generate(String content, Map<String, Object> metadata) {
        return Mono.just(truncate(normalizeWhitespace(content)));
    }

    @Override
    public Mono<String> generate(String content, Map<String, Object> metadata, String language) {
        return generate(content, metadata);
    }

    private Mono<List<Segment>> preserveOrGenerate(List<Segment> segments) {
        if (segments.isEmpty()) {
            return Mono.just(List.of());
        }

        return Flux.fromIterable(segments)
                .map(
                        segment -> {
                            String candidate =
                                    segment.caption() != null && !segment.caption().isBlank()
                                            ? segment.caption()
                                            : segment.content();
                            return segment.withCaption(truncate(normalizeWhitespace(candidate)));
                        })
                .collectList();
    }

    private String normalizeWhitespace(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        return content.replaceAll("\\s+", " ").trim();
    }

    private String truncate(String content) {
        return truncate(content, maxLength);
    }

    private String truncate(String content, int limit) {
        if (limit <= 0 || content.isBlank()) {
            return "";
        }
        return content.length() <= limit ? content : content.substring(0, limit - 3) + "...";
    }
}
