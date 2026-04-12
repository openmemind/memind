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
package com.openmemind.ai.memory.core.extraction.rawdata.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmemind.ai.memory.core.data.enums.ContentGovernanceType;
import com.openmemind.ai.memory.core.extraction.BuiltinContentProfiles;
import com.openmemind.ai.memory.core.support.TestDocumentContent;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RawContentSpiTest {

    @Test
    void testDocumentContentExposesMetadataGovernanceProfileAndCopy() {
        var content =
                new TestDocumentContent(
                        "Guide",
                        "text/markdown",
                        "# title",
                        "file:///tmp/guide.md",
                        ContentGovernanceType.DOCUMENT_TEXT_LIKE,
                        BuiltinContentProfiles.DOCUMENT_MARKDOWN,
                        Map.of("author", "alice"));

        assertThat(content.contentMetadata()).containsEntry("author", "alice");
        assertThat(content.directGovernanceType())
                .isEqualTo(ContentGovernanceType.DOCUMENT_TEXT_LIKE);
        assertThat(content.directContentProfile())
                .isEqualTo(BuiltinContentProfiles.DOCUMENT_MARKDOWN);
        assertThat(content.withMetadata(Map.of("parserId", "direct")))
                .isInstanceOf(TestDocumentContent.class)
                .extracting(value -> ((TestDocumentContent) value).metadata().get("parserId"))
                .isEqualTo("direct");
    }

    void baseRawContentWithMetadataFailsFastWhenNotOverridden() {
        var content = ConversationContent.builder().addUserMessage("hello").build();

        assertThatThrownBy(() -> content.withMetadata(Map.of("x", 1)))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("ConversationContent");
    }
}
