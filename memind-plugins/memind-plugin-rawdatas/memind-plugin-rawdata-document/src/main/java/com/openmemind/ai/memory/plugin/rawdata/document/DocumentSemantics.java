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
package com.openmemind.ai.memory.plugin.rawdata.document;

public final class DocumentSemantics {

    public static final String GOVERNANCE_TEXT_LIKE = "document.text-like";
    public static final String GOVERNANCE_BINARY = "document.binary";

    public static final String PROFILE_MARKDOWN = "document.markdown";
    public static final String PROFILE_HTML = "document.html";
    public static final String PROFILE_TEXT = "document.text";
    public static final String PROFILE_BINARY = "document.binary";

    private DocumentSemantics() {}

    public static String directGovernance(String mimeType, boolean hasStructuredSections) {
        if ("text/markdown".equals(mimeType)
                || "text/html".equals(mimeType)
                || "text/plain".equals(mimeType)
                || "text/csv".equals(mimeType)) {
            return GOVERNANCE_TEXT_LIKE;
        }
        if (mimeType != null && !mimeType.isBlank()) {
            return GOVERNANCE_BINARY;
        }
        return hasStructuredSections ? GOVERNANCE_BINARY : GOVERNANCE_TEXT_LIKE;
    }

    public static String directProfile(String mimeType, boolean hasStructuredSections) {
        if ("text/markdown".equals(mimeType)) {
            return PROFILE_MARKDOWN;
        }
        if ("text/html".equals(mimeType)) {
            return PROFILE_HTML;
        }
        if ("text/plain".equals(mimeType) || "text/csv".equals(mimeType)) {
            return PROFILE_TEXT;
        }
        if (mimeType != null && !mimeType.isBlank()) {
            return PROFILE_BINARY;
        }
        return hasStructuredSections ? PROFILE_BINARY : PROFILE_TEXT;
    }

    public static boolean isBinaryGovernance(String governanceType) {
        return GOVERNANCE_BINARY.equals(governanceType);
    }

    public static boolean isMarkdownProfile(String contentProfile) {
        return PROFILE_MARKDOWN.equals(contentProfile);
    }
}
