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
package com.openmemind.ai.memory.plugin.rawdata.audio.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.enums.ContentGovernanceType;
import com.openmemind.ai.memory.core.extraction.ExtractionRequest;
import com.openmemind.ai.memory.core.extraction.source.DirectContentSource;
import com.openmemind.ai.memory.plugin.rawdata.audio.content.AudioContent;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AudioExtractionRequestsTest {

    @Test
    void audioFactoryDelegatesToGenericExtractionRequest() {
        var memoryId = DefaultMemoryId.of("user-1", "agent-1");
        var content = AudioContent.of("hello world");
        var request = AudioExtractionRequests.audio(memoryId, content);

        assertThat(request)
                .usingRecursiveComparison()
                .isEqualTo(ExtractionRequest.of(memoryId, content));
    }

    @Test
    void audioFactoryNormalizesMimeTypeAndSourceUri() {
        var content =
                new AudioContent(
                        "audio/mpeg", "hello world", List.of(), "file:///tmp/audio.mp3", Map.of());

        var request =
                AudioExtractionRequests.audio(DefaultMemoryId.of("user-1", "agent-1"), content);

        assertThat(request.source()).isInstanceOf(DirectContentSource.class);
        assertThat(request.content()).isSameAs(((DirectContentSource) request.source()).content());
        assertThat(request.content().contentType()).isEqualTo(AudioContent.TYPE);
        assertThat(request.metadata())
                .containsEntry("sourceKind", "DIRECT")
                .containsEntry("parserId", "direct")
                .containsEntry("contentProfile", "audio.transcript")
                .containsEntry("governanceType", ContentGovernanceType.AUDIO_TRANSCRIPT.name())
                .containsEntry("mimeType", "audio/mpeg")
                .containsEntry("sourceUri", "file:///tmp/audio.mp3");
    }
}
