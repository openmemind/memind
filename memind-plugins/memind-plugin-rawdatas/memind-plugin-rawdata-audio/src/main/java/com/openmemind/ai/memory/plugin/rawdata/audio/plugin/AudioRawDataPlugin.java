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
package com.openmemind.ai.memory.plugin.rawdata.audio.plugin;

import com.openmemind.ai.memory.core.data.ContentTypes;
import com.openmemind.ai.memory.core.data.enums.ContentGovernanceType;
import com.openmemind.ai.memory.core.extraction.rawdata.RawContentProcessor;
import com.openmemind.ai.memory.core.extraction.rawdata.RawContentTypeRegistrar;
import com.openmemind.ai.memory.core.plugin.RawDataIngestionPolicy;
import com.openmemind.ai.memory.core.plugin.RawDataPlugin;
import com.openmemind.ai.memory.core.plugin.RawDataPluginContext;
import com.openmemind.ai.memory.plugin.rawdata.audio.chunk.TranscriptSegmentChunker;
import com.openmemind.ai.memory.plugin.rawdata.audio.config.AudioExtractionOptions;
import com.openmemind.ai.memory.plugin.rawdata.audio.processor.AudioContentProcessor;
import java.util.List;
import java.util.Set;

public final class AudioRawDataPlugin implements RawDataPlugin {

    private final AudioExtractionOptions options;

    public AudioRawDataPlugin() {
        this(AudioExtractionOptions.defaults());
    }

    public AudioRawDataPlugin(AudioExtractionOptions options) {
        this.options = options == null ? AudioExtractionOptions.defaults() : options;
    }

    @Override
    public String pluginId() {
        return "rawdata-audio";
    }

    @Override
    public List<RawContentProcessor<?>> processors(RawDataPluginContext context) {
        return List.of(new AudioContentProcessor(new TranscriptSegmentChunker(), options));
    }

    @Override
    public List<RawDataIngestionPolicy> ingestionPolicies() {
        return List.of(
                new RawDataIngestionPolicy(
                        ContentTypes.AUDIO,
                        Set.of(ContentGovernanceType.AUDIO_TRANSCRIPT),
                        options.sourceLimit()));
    }

    @Override
    public List<RawContentTypeRegistrar> typeRegistrars() {
        return List.of(new AudioRawContentTypeRegistrar());
    }
}
