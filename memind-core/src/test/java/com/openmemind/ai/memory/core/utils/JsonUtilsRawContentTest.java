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
package com.openmemind.ai.memory.core.utils;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.extraction.rawdata.content.ImageContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonUtilsRawContentTest {

    @Test
    void jsonUtilsCanRoundTripBuiltinRawContent() {
        RawContent content =
                new ImageContent(
                        "image/png",
                        "dashboard screenshot",
                        "Total Revenue 30%",
                        "file:///tmp/dashboard.png",
                        Map.of("width", 1280));

        String json = JsonUtils.toJson(content);
        RawContent decoded = JsonUtils.fromJson(json, RawContent.class);

        assertThat(decoded).isInstanceOf(ImageContent.class);
        assertThat((ImageContent) decoded)
                .extracting(
                        ImageContent::mimeType,
                        ImageContent::description,
                        ImageContent::ocrText,
                        ImageContent::sourceUri)
                .containsExactly(
                        "image/png",
                        "dashboard screenshot",
                        "Total Revenue 30%",
                        "file:///tmp/dashboard.png");
    }
}
