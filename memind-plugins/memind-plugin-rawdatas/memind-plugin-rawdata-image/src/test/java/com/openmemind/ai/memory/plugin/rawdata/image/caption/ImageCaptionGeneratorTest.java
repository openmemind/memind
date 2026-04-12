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

import java.util.Map;
import org.junit.jupiter.api.Test;

class ImageCaptionGeneratorTest {

    @Test
    void mergedCaptionPrefersDescription() {
        var generator = new ImageCaptionGenerator();

        assertThat(
                        generator
                                .generate(
                                        "Dashboard screenshot\nQ1 revenue 20%",
                                        Map.of("segmentRole", "caption_ocr"))
                                .block())
                .isEqualTo("Dashboard screenshot");
    }

    @Test
    void ocrCaptionUsesStablePrefix() {
        var generator = new ImageCaptionGenerator();

        assertThat(generator.generate("Q1 revenue 20%", Map.of("segmentRole", "ocr")).block())
                .isEqualTo("Image text: Q1 revenue 20%");
    }

    @Test
    void descriptionCaptionTruncatesWhenTooLong() {
        var generator = new ImageCaptionGenerator(12);

        assertThat(generator.generate("123456789012345", Map.of()).block())
                .isEqualTo("123456789...");
    }
}
