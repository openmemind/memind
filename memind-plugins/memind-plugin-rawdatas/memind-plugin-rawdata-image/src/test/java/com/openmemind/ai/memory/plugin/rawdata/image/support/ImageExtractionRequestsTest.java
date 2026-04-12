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
package com.openmemind.ai.memory.plugin.rawdata.image.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.data.ContentTypes;
import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.enums.ContentGovernanceType;
import com.openmemind.ai.memory.core.extraction.ExtractionRequest;
import com.openmemind.ai.memory.plugin.rawdata.image.content.ImageContent;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ImageExtractionRequestsTest {

    @Test
    void imageFactoryDelegatesToGenericExtractionRequest() {
        var memoryId = DefaultMemoryId.of("user-1", "agent-1");
        var content = ImageContent.of("dashboard screenshot");
        var request = ImageExtractionRequests.image(memoryId, content);

        assertThat(request)
                .usingRecursiveComparison()
                .isEqualTo(ExtractionRequest.of(memoryId, content));
    }

    @Test
    void imageFactoryNormalizesMimeTypeAndSourceUri() {
        var content =
                new ImageContent(
                        "image/png",
                        "dashboard screenshot",
                        "total revenue 30%",
                        "file:///tmp/dashboard.png",
                        Map.of("width", 1280));

        var request =
                ImageExtractionRequests.image(DefaultMemoryId.of("user-1", "agent-1"), content);

        assertThat(request.contentType()).isEqualTo(ContentTypes.IMAGE);
        assertThat(request.metadata())
                .containsEntry("width", 1280)
                .containsEntry("sourceKind", "DIRECT")
                .containsEntry("parserId", "direct")
                .containsEntry("contentProfile", "image.caption-ocr")
                .containsEntry("governanceType", ContentGovernanceType.IMAGE_CAPTION_OCR.name())
                .containsEntry("mimeType", "image/png")
                .containsEntry("sourceUri", "file:///tmp/dashboard.png");
    }
}
