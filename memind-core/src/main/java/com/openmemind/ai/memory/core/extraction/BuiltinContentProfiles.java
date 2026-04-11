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

import com.openmemind.ai.memory.core.data.enums.ContentGovernanceType;
import java.util.Map;
import java.util.Optional;

/**
 * Built-in content profile constants and their governance-family mapping.
 */
public final class BuiltinContentProfiles {

    public static final String DOCUMENT_MARKDOWN = "document.markdown";
    public static final String DOCUMENT_HTML = "document.html";
    public static final String DOCUMENT_TEXT = "document.text";
    public static final String DOCUMENT_BINARY = "document.binary";
    public static final String IMAGE_CAPTION_OCR = "image.caption-ocr";
    public static final String AUDIO_TRANSCRIPT = "audio.transcript";

    private static final Map<String, ContentGovernanceType> GOVERNANCE_TYPES =
            Map.of(
                    DOCUMENT_MARKDOWN, ContentGovernanceType.DOCUMENT_TEXT_LIKE,
                    DOCUMENT_HTML, ContentGovernanceType.DOCUMENT_TEXT_LIKE,
                    DOCUMENT_TEXT, ContentGovernanceType.DOCUMENT_TEXT_LIKE,
                    DOCUMENT_BINARY, ContentGovernanceType.DOCUMENT_BINARY,
                    IMAGE_CAPTION_OCR, ContentGovernanceType.IMAGE_CAPTION_OCR,
                    AUDIO_TRANSCRIPT, ContentGovernanceType.AUDIO_TRANSCRIPT);

    private BuiltinContentProfiles() {}

    public static Optional<ContentGovernanceType> governanceTypeOf(String contentProfile) {
        return Optional.ofNullable(GOVERNANCE_TYPES.get(contentProfile));
    }
}
