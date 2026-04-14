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
package com.openmemind.ai.memory.plugin.rawdata.audio.caption;

import com.openmemind.ai.memory.core.extraction.rawdata.caption.CaptionGenerator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import reactor.core.publisher.Mono;

/**
 * Deterministic caption generator for parsed audio transcript segments.
 */
public final class AudioCaptionGenerator implements CaptionGenerator {

    private final int maxLength;

    public AudioCaptionGenerator() {
        this(200);
    }

    public AudioCaptionGenerator(int maxLength) {
        this.maxLength = maxLength;
    }

    @Override
    public Mono<String> generate(String content, Map<String, Object> metadata) {
        String excerpt = normalizeWhitespace(content);
        if (excerpt.isBlank()) {
            return Mono.just("");
        }

        String prefix = resolvePrefix(metadata);
        int available = Math.max(0, maxLength - prefix.length());
        return Mono.just(prefix + truncate(excerpt, available));
    }

    private String resolvePrefix(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "Transcript: ";
        }

        String speaker = asText(metadata.get("speaker"));
        if (speaker != null) {
            return speaker + ": ";
        }

        List<String> speakers = asStringList(metadata.get("speakers"));
        if (!speakers.isEmpty()) {
            return "Speakers (" + String.join(", ", speakers) + "): ";
        }
        return "Transcript: ";
    }

    private List<String> asStringList(Object value) {
        if (!(value instanceof Iterable<?> iterable)) {
            return List.of();
        }
        return java.util.stream.StreamSupport.stream(iterable.spliterator(), false)
                .map(this::asText)
                .filter(text -> text != null && !text.isBlank())
                .collect(Collectors.toList());
    }

    private String asText(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isBlank() ? null : text;
    }

    private String normalizeWhitespace(String content) {
        return content == null ? "" : content.replaceAll("\\s+", " ").trim();
    }

    private String truncate(String content, int limit) {
        if (limit <= 0 || content.isBlank()) {
            return "";
        }
        return content.length() <= limit ? content : content.substring(0, limit - 3) + "...";
    }
}
