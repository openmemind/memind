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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmemind.ai.memory.core.builder.ParsedContentLimitOptions;
import com.openmemind.ai.memory.core.builder.SourceLimitOptions;
import com.openmemind.ai.memory.core.exception.ParsedContentTooLargeException;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.CharBoundary;
import com.openmemind.ai.memory.plugin.rawdata.image.config.ImageExtractionOptions;
import com.openmemind.ai.memory.plugin.rawdata.image.content.ImageContent;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class ImageContentProcessorTest {

    @Test
    void imageProcessorEmitsSingleSegmentWithPrefilledCaption() {
        var processor = new ImageContentProcessor(ImageExtractionOptions.defaults());
        var content =
                new ImageContent(
                        "image/png",
                        "A screenshot of the dashboard showing Total Revenue 30%",
                        "Revenue dashboard screenshot",
                        "file:///tmp/dashboard.png",
                        Map.of("width", 1280, "height", 720));

        assertThat(processor.contentType()).isEqualTo(ImageContent.TYPE);

        StepVerifier.create(processor.chunk(content))
                .assertNext(
                        segments -> {
                            assertThat(segments).hasSize(1);
                            assertThat(segments.getFirst().content())
                                    .isEqualTo(
                                            "A screenshot of the dashboard showing Total Revenue"
                                                    + " 30%");
                            assertThat(segments.getFirst().caption())
                                    .isEqualTo("Revenue dashboard screenshot");
                            assertThat(segments.getFirst().boundary())
                                    .isEqualTo(
                                            new CharBoundary(
                                                    0,
                                                    "A screenshot of the dashboard showing Total Revenue 30%"
                                                            .length()));
                            assertThat(segments.getFirst().metadata())
                                    .containsEntry("width", 1280)
                                    .containsEntry("height", 720);
                        })
                .verifyComplete();
    }

    @Test
    void imageProcessorRejectsOversizedDescriptionsUsingParsedLimit() {
        var processor =
                new ImageContentProcessor(
                        new ImageExtractionOptions(
                                new SourceLimitOptions(1024),
                                new ParsedContentLimitOptions(5, null, null, null)));
        var content =
                new ImageContent(
                        "image/png",
                        "word word word word word word",
                        "summary",
                        null,
                        Map.of("contentProfile", "image.caption-ocr"));

        assertThatThrownBy(() -> processor.validateParsedContent(content))
                .isInstanceOf(ParsedContentTooLargeException.class)
                .hasMessageContaining("image.caption-ocr");
    }
}
