package com.openmemind.ai.memory.core.extraction.rawdata.caption;

import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * Truncation-based summary generator
 *
 * <p>Take the first line, truncate if too long.
 *
 */
public class TruncateCaptionGenerator implements CaptionGenerator {

    private final int maxLength;

    public TruncateCaptionGenerator() {
        this(200);
    }

    public TruncateCaptionGenerator(int maxLength) {
        this.maxLength = maxLength;
    }

    @Override
    public Mono<String> generate(String content, Map<String, Object> metadata) {
        String firstLine = content.lines().findFirst().orElse("");
        String caption =
                firstLine.length() > maxLength
                        ? firstLine.substring(0, maxLength) + "..."
                        : firstLine;
        return Mono.just(caption);
    }
}
