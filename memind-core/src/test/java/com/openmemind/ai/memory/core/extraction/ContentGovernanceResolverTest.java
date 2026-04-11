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
package com.openmemind.ai.memory.core.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmemind.ai.memory.core.data.enums.ContentGovernanceType;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ContentGovernanceResolverTest {

    @Test
    void resolvesBuiltinProfilesToClosedGovernanceTypes() {
        assertThat(BuiltinContentProfiles.governanceTypeOf("document.markdown"))
                .hasValue(ContentGovernanceType.DOCUMENT_TEXT_LIKE);
        assertThat(BuiltinContentProfiles.governanceTypeOf("document.binary"))
                .hasValue(ContentGovernanceType.DOCUMENT_BINARY);
        assertThat(BuiltinContentProfiles.governanceTypeOf("image.caption-ocr"))
                .hasValue(ContentGovernanceType.IMAGE_CAPTION_OCR);
        assertThat(BuiltinContentProfiles.governanceTypeOf("audio.transcript"))
                .hasValue(ContentGovernanceType.AUDIO_TRANSCRIPT);
    }

    @Test
    void prefersExplicitGovernanceTypeWhenPresent() {
        assertThat(
                        ContentGovernanceResolver.resolveRequired(
                                Map.of(
                                        "contentProfile", "document.pdf.tika",
                                        "governanceType", "DOCUMENT_BINARY")))
                .isEqualTo(ContentGovernanceType.DOCUMENT_BINARY);
    }

    @Test
    void fallsBackToBuiltinProfileWhenGovernanceTypeIsMissing() {
        assertThat(
                        ContentGovernanceResolver.resolveRequired(
                                Map.of("contentProfile", "document.markdown")))
                .isEqualTo(ContentGovernanceType.DOCUMENT_TEXT_LIKE);
    }

    @Test
    void rejectsUnknownProfileWithoutGovernanceType() {
        assertThatThrownBy(
                        () ->
                                ContentGovernanceResolver.resolveRequired(
                                        Map.of("contentProfile", "document.pdf.tika")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("document.pdf.tika")
                .hasMessageContaining("governanceType");
    }
}
