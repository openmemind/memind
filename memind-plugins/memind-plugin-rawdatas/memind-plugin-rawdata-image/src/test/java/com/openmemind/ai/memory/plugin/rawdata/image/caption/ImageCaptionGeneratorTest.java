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

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.extraction.rawdata.segment.CharBoundary;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.Segment;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class ImageCaptionGeneratorTest {

    @Test
    void generateForSegmentsPreservesPrefilledCaption() {
        var generator = new ImageCaptionGenerator(80);
        var segment =
                new Segment(
                        "dashboard screenshot showing Total Revenue 30%",
                        "Revenue dashboard screenshot", new CharBoundary(0, 44), Map.of());

        StepVerifier.create(generator.generateForSegments(List.of(segment)))
                .assertNext(
                        segments ->
                                assertThat(segments)
                                        .singleElement()
                                        .extracting(Segment::caption)
                                        .isEqualTo("Revenue dashboard screenshot"))
                .verifyComplete();
    }

    @Test
    void generateForSegmentsWithLanguagePreservesPrefilledCaption() {
        var generator = new ImageCaptionGenerator(80);
        var segment =
                new Segment(
                        "dashboard screenshot showing Total Revenue 30%",
                        "Revenue dashboard screenshot", new CharBoundary(0, 44), Map.of());

        StepVerifier.create(generator.generateForSegments(List.of(segment), "zh-CN"))
                .assertNext(
                        segments ->
                                assertThat(segments)
                                        .singleElement()
                                        .extracting(Segment::caption)
                                        .isEqualTo("Revenue dashboard screenshot"))
                .verifyComplete();
    }

    @Test
    void generateForSegmentsTruncatesLongPrefilledCaption() {
        var generator = new ImageCaptionGenerator(12);
        var segment =
                new Segment("description", "123456789012345", new CharBoundary(0, 11), Map.of());

        StepVerifier.create(generator.generateForSegments(List.of(segment)))
                .assertNext(
                        segments ->
                                assertThat(segments)
                                        .singleElement()
                                        .extracting(Segment::caption)
                                        .isEqualTo("123456789..."))
                .verifyComplete();
    }
}
