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
package com.openmemind.ai.memory.plugin.rawdata.image.processor;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.builder.ImageExtractionOptions;
import com.openmemind.ai.memory.core.data.ContentTypes;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.CharBoundary;
import com.openmemind.ai.memory.core.utils.TokenUtils;
import com.openmemind.ai.memory.plugin.rawdata.image.chunk.ImageSegmentComposer;
import com.openmemind.ai.memory.plugin.rawdata.image.content.ImageContent;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class ImageContentProcessorTest {

    @Test
    void shouldEmitSingleSegmentForImageText() {
        var processor =
                new ImageContentProcessor(
                        new ImageSegmentComposer(), ImageExtractionOptions.defaults());
        var content =
                new ImageContent(
                        "image/png",
                        "A screenshot of the dashboard",
                        "Total Revenue 30%",
                        "file:///tmp/dashboard.png",
                        Map.of("width", 1280, "height", 720));

        assertThat(processor.contentType()).isEqualTo(ContentTypes.IMAGE);

        StepVerifier.create(processor.chunk(content))
                .assertNext(
                        segments -> {
                            assertThat(segments).hasSize(1);
                            assertThat(segments.getFirst().content())
                                    .isEqualTo("A screenshot of the dashboard\nTotal Revenue 30%");
                            assertThat(segments.getFirst().boundary())
                                    .isEqualTo(
                                            new CharBoundary(
                                                    0,
                                                    "A screenshot of the dashboard\nTotal Revenue 30%"
                                                            .length()));
                            assertThat(segments.getFirst().metadata())
                                    .containsEntry("width", 1280)
                                    .containsEntry("height", 720)
                                    .doesNotContainKey("sourceUri");
                        })
                .verifyComplete();
    }

    @Test
    void imageProcessorKeepsMergedCaptionOcrWithinHardTokenBudget() {
        var options = ImageExtractionOptions.defaults();
        var processor = new ImageContentProcessor(new ImageSegmentComposer(), options);
        var caption = "word ".repeat(options.chunking().hardMaxTokens());
        var shortOcr = "word ".repeat(Math.max(1, options.chunking().targetTokens() / 4));
        var content =
                new ImageContent(
                        "image/png",
                        caption,
                        shortOcr,
                        null,
                        Map.of("contentProfile", "image.caption-ocr"));

        StepVerifier.create(processor.chunk(content))
                .assertNext(
                        segments ->
                                assertThat(segments)
                                        .allSatisfy(
                                                segment ->
                                                        assertThat(
                                                                        TokenUtils.countTokens(
                                                                                segment.content()))
                                                                .isLessThanOrEqualTo(
                                                                        options.chunking()
                                                                                .hardMaxTokens())))
                .verifyComplete();
    }

    @Test
    void imageProcessorCaptionPrefersDescriptionOverMergedOcr() {
        var processor =
                new ImageContentProcessor(
                        new ImageSegmentComposer(), ImageExtractionOptions.defaults());
        var content =
                new ImageContent(
                        "image/png",
                        "A screenshot of the dashboard",
                        "Total Revenue 30%",
                        null,
                        Map.of("contentProfile", "image.caption-ocr"));

        StepVerifier.create(
                        processor
                                .chunk(content)
                                .flatMap(processor.captionGenerator()::generateForSegments))
                .assertNext(
                        segments ->
                                assertThat(segments)
                                        .singleElement()
                                        .extracting(
                                                com.openmemind.ai.memory.core.extraction.rawdata
                                                                .segment.Segment
                                                        ::caption)
                                        .isEqualTo("A screenshot of the dashboard"))
                .verifyComplete();
    }

    @Test
    void imageProcessorCaptionFallsBackToOcrExcerptWhenDescriptionMissing() {
        var processor =
                new ImageContentProcessor(
                        new ImageSegmentComposer(), ImageExtractionOptions.defaults());
        var content =
                new ImageContent(
                        "image/png",
                        null,
                        "Total Revenue 30%\nARR 50%",
                        null,
                        Map.of("contentProfile", "image.ocr"));

        StepVerifier.create(
                        processor
                                .chunk(content)
                                .flatMap(processor.captionGenerator()::generateForSegments))
                .assertNext(
                        segments ->
                                assertThat(segments)
                                        .singleElement()
                                        .extracting(
                                                com.openmemind.ai.memory.core.extraction.rawdata
                                                                .segment.Segment
                                                        ::caption)
                                        .isEqualTo("Image text: Total Revenue 30% ARR 50%"))
                .verifyComplete();
    }
}
