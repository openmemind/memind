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

import com.openmemind.ai.memory.core.resource.ContentCapability;
import com.openmemind.ai.memory.core.support.TestDocumentContent;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MultimodalMetadataNormalizerTest {

    @Test
    void normalizeParsedShouldTreatContentProfileAsOpaqueMetadata() {
        var content =
                new TestDocumentContent(
                        "Report",
                        "application/pdf",
                        "body",
                        "direct://doc-1",
                        TestDocumentContent.GOVERNANCE_BINARY,
                        "document.binary",
                        Map.of(
                                "contentProfile",
                                "document.markdown",
                                "governanceType",
                                TestDocumentContent.GOVERNANCE_BINARY));
        var capability =
                new ContentCapability(
                        "document-test",
                        TestDocumentContent.TYPE,
                        "document.pdf.tika",
                        TestDocumentContent.GOVERNANCE_BINARY,
                        Set.of("application/pdf"),
                        Set.of(".pdf"),
                        0);

        var normalized =
                MultimodalMetadataNormalizer.normalizeParsed(content, Map.of(), capability);

        assertThat(normalized)
                .containsEntry("contentProfile", "document.markdown")
                .containsEntry("governanceType", TestDocumentContent.GOVERNANCE_BINARY)
                .containsEntry("parserId", "document-test");
    }
}
