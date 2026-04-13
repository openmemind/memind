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
package com.openmemind.ai.memory.plugin.rawdata.document.caption;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class DocumentCaptionFallbacksTest {

    @Test
    void fallbackPrefersHeadingThenBodyExcerpt() {
        assertThat(
                        DocumentCaptionFallbacks.build(
                                "Use exponential backoff for 429 and 503 retries.",
                                Map.of("headingTitle", "Retry policy"),
                                240))
                .isEqualTo("Retry policy: Use exponential backoff for 429 and 503 retries.");
    }

    @Test
    void fallbackUsesPageRangeWhenHeadingAndSectionAreMissing() {
        assertThat(
                        DocumentCaptionFallbacks.build(
                                "Flyway migrations run before the application starts.",
                                Map.of("pageStart", 3, "pageEnd", 4),
                                240))
                .startsWith("Pages 3-4: ");
    }

    @Test
    void fallbackTruncatesToConfiguredLength() {
        String caption =
                DocumentCaptionFallbacks.build(
                        "alpha beta gamma delta epsilon zeta eta theta iota kappa", Map.of(), 24);

        assertThat(caption).hasSizeLessThanOrEqualTo(24);
        assertThat(caption).endsWith("...");
    }
}
