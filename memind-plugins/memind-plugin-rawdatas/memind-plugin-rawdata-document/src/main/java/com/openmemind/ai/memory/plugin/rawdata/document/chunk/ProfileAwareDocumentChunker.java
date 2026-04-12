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
package com.openmemind.ai.memory.plugin.rawdata.document.chunk;

import com.openmemind.ai.memory.core.builder.TokenChunkingOptions;
import com.openmemind.ai.memory.core.data.enums.ContentGovernanceType;
import com.openmemind.ai.memory.core.extraction.BuiltinContentProfiles;
import com.openmemind.ai.memory.core.extraction.rawdata.chunk.TokenAwareSegmentAssembler;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.Segment;
import com.openmemind.ai.memory.plugin.rawdata.document.config.DocumentExtractionOptions;
import java.util.List;
import java.util.Objects;

/**
 * Chooses document chunking strategy by normalized content profile.
 */
public final class ProfileAwareDocumentChunker {

    private final TokenAwareSegmentAssembler tokenAwareSegmentAssembler;

    public ProfileAwareDocumentChunker() {
        this(new TokenAwareSegmentAssembler());
    }

    public ProfileAwareDocumentChunker(TokenAwareSegmentAssembler tokenAwareSegmentAssembler) {
        this.tokenAwareSegmentAssembler =
                Objects.requireNonNull(tokenAwareSegmentAssembler, "tokenAwareSegmentAssembler");
    }

    public List<Segment> chunk(
            String text,
            DocumentExtractionOptions options,
            ContentGovernanceType governanceType,
            String contentProfile) {
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(governanceType, "governanceType");
        String profile =
                contentProfile == null || contentProfile.isBlank()
                        ? BuiltinContentProfiles.DOCUMENT_TEXT
                        : contentProfile;
        TokenChunkingOptions chunkingOptions =
                switch (governanceType) {
                    case DOCUMENT_BINARY -> options.binaryChunking();
                    case DOCUMENT_TEXT_LIKE -> options.textLikeChunking();
                    default -> options.textLikeChunking();
                };
        List<Segment> candidates =
                switch (profile) {
                    case BuiltinContentProfiles.DOCUMENT_MARKDOWN ->
                            tokenAwareSegmentAssembler.markdownCandidates(text);
                    case BuiltinContentProfiles.DOCUMENT_BINARY,
                            BuiltinContentProfiles.DOCUMENT_HTML,
                            BuiltinContentProfiles.DOCUMENT_TEXT ->
                            tokenAwareSegmentAssembler.paragraphCandidates(text);
                    default -> tokenAwareSegmentAssembler.paragraphCandidates(text);
                };
        return tokenAwareSegmentAssembler.assemble(candidates, chunkingOptions);
    }
}
