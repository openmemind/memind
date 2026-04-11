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
package com.openmemind.ai.memory.core.extraction.rawdata.chunk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmemind.ai.memory.core.builder.ImageExtractionOptions;
import com.openmemind.ai.memory.core.extraction.rawdata.content.ImageContent;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.Segment;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ImageSegmentComposerTest {

    @Test
    void smallOcrCanBeMergedIntoCaptionSegmentWhenBelowConfiguredThreshold() {
        var defaults = ImageExtractionOptions.defaults();
        var options =
                new ImageExtractionOptions(
                        defaults.sourceLimit(), defaults.parsedLimit(), defaults.chunking(), 12);
        var content =
                new ImageContent("image/png", "Dashboard screenshot", "A B C", null, Map.of());

        assertThat(new ImageSegmentComposer().compose(content, options))
                .singleElement()
                .extracting(Segment::content)
                .isEqualTo("Dashboard screenshot\nA B C");
    }

    @Test
    void largeOcrMovesToDedicatedSegmentsWhenAboveConfiguredThreshold() {
        var defaults = ImageExtractionOptions.defaults();
        var options =
                new ImageExtractionOptions(
                        defaults.sourceLimit(), defaults.parsedLimit(), defaults.chunking(), 4);
        var content =
                new ImageContent(
                        "image/png", "Dashboard screenshot", "word ".repeat(20), null, Map.of());

        assertThat(new ImageSegmentComposer().compose(content, options)).hasSizeGreaterThan(1);
    }

    @Test
    void negativeOcrMergeThresholdIsRejected() {
        var defaults = ImageExtractionOptions.defaults();

        assertThatThrownBy(
                        () ->
                                new ImageExtractionOptions(
                                        defaults.sourceLimit(),
                                        defaults.parsedLimit(),
                                        defaults.chunking(),
                                        -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("captionOcrMergeMaxTokens");
    }
}
