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
package com.openmemind.ai.memory.plugin.rawdata.audio.content;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.openmemind.ai.memory.core.data.ContentTypes;
import com.openmemind.ai.memory.core.data.enums.ContentGovernanceType;
import com.openmemind.ai.memory.core.extraction.BuiltinContentProfiles;
import com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent;
import com.openmemind.ai.memory.core.utils.HashUtils;
import com.openmemind.ai.memory.plugin.rawdata.audio.content.audio.TranscriptSegment;
import java.util.List;
import java.util.Map;

/**
 * Parsed audio raw content.
 */
public final class AudioContent extends RawContent {

    private final String mimeType;
    private final String transcript;
    private final List<TranscriptSegment> segments;
    private final String sourceUri;
    private final Map<String, Object> metadata;

    @JsonCreator
    public AudioContent(
            @JsonProperty("mimeType") String mimeType,
            @JsonProperty("transcript") String transcript,
            @JsonProperty("segments") List<TranscriptSegment> segments,
            @JsonProperty("sourceUri") String sourceUri,
            @JsonProperty("metadata") Map<String, Object> metadata) {
        this.mimeType = mimeType;
        this.transcript = transcript;
        this.segments = segments == null ? List.of() : List.copyOf(segments);
        this.sourceUri = sourceUri;
        this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static AudioContent of(String transcript) {
        return new AudioContent(null, transcript, List.of(), null, Map.of());
    }

    @Override
    public String contentType() {
        return ContentTypes.AUDIO;
    }

    @Override
    public String toContentString() {
        return transcript == null ? "" : transcript;
    }

    @Override
    public String getContentId() {
        return HashUtils.sampledSha256(toContentString());
    }

    @Override
    public Map<String, Object> contentMetadata() {
        return metadata;
    }

    @Override
    public RawContent withMetadata(Map<String, Object> metadata) {
        return new AudioContent(mimeType, transcript, segments, sourceUri, metadata);
    }

    @Override
    public ContentGovernanceType directGovernanceType() {
        return ContentGovernanceType.AUDIO_TRANSCRIPT;
    }

    @Override
    public String directContentProfile() {
        return BuiltinContentProfiles.AUDIO_TRANSCRIPT;
    }

    @Override
    @JsonProperty("mimeType")
    public String mimeType() {
        return mimeType;
    }

    @JsonProperty("transcript")
    public String transcript() {
        return transcript;
    }

    @JsonProperty("segments")
    public List<TranscriptSegment> segments() {
        return segments;
    }

    @Override
    @JsonProperty("sourceUri")
    public String sourceUri() {
        return sourceUri;
    }

    @JsonProperty("metadata")
    public Map<String, Object> metadata() {
        return metadata;
    }
}
