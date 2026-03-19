package com.openmemind.ai.memory.core.extraction.rawdata.caption;

import com.openmemind.ai.memory.core.extraction.rawdata.segment.Segment;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Summary generator
 *
 */
public interface CaptionGenerator {

    /**
     * Generate a summary for the content
     *
     * @param content Content
     * @param metadata Metadata
     * @return Summary
     */
    Mono<String> generate(String content, Map<String, Object> metadata);

    /**
     * Generate summaries in parallel for multiple segments
     *
     * @param segments List of segments
     * @return List of segments with summaries
     */
    default Mono<List<Segment>> generateForSegments(List<Segment> segments) {
        if (segments.isEmpty()) {
            return Mono.just(List.of());
        }

        return Flux.fromIterable(segments)
                .flatMap(
                        segment ->
                                generate(segment.content(), segment.metadata())
                                        .map(segment::withCaption))
                .collectList();
    }

    /**
     * Generate a summary for the content with language hint
     *
     * @param content Content
     * @param metadata Metadata
     * @param language Target language, can be null
     * @return Summary
     */
    default Mono<String> generate(String content, Map<String, Object> metadata, String language) {
        return generate(content, metadata);
    }

    /**
     * Generate summaries in parallel for multiple segments with language hint
     *
     * @param segments List of segments
     * @param language Target language, can be null
     * @return List of segments with summaries
     */
    default Mono<List<Segment>> generateForSegments(List<Segment> segments, String language) {
        if (segments.isEmpty()) {
            return Mono.just(List.of());
        }

        return Flux.fromIterable(segments)
                .flatMap(
                        segment ->
                                generate(segment.content(), segment.metadata(), language)
                                        .map(segment::withCaption))
                .collectList();
    }
}
